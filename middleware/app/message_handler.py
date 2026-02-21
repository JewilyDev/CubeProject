"""
MessageHandler — диспетчер входящих сообщений от Minecraft.
Новые типы сообщений добавляются через декоратор @handler("type").
"""

import json
import logging
from typing import Any, Awaitable, Callable

logger = logging.getLogger("immersiveciv.handler")

# Реестр обработчиков: тип сообщения → coroutine
_HANDLERS: dict[str, Callable[[str, dict], Awaitable[None]]] = {}


def handler(msg_type: str):
    """Декоратор для регистрации обработчика конкретного типа сообщения."""
    def decorator(fn: Callable[[str, dict], Awaitable[None]]):
        _HANDLERS[msg_type] = fn
        return fn
    return decorator


async def dispatch(connection_id: str, raw: str) -> None:
    """Парсит JSON и вызывает нужный обработчик."""
    try:
        data: dict[str, Any] = json.loads(raw)
    except json.JSONDecodeError:
        logger.warning("[%s] Получено не-JSON сообщение: %s", connection_id, raw[:200])
        return

    msg_type = data.get("type", "unknown")
    fn = _HANDLERS.get(msg_type)
    if fn is None:
        logger.warning("[%s] Неизвестный тип сообщения: %s", connection_id, msg_type)
        return

    try:
        await fn(connection_id, data)
    except Exception as e:
        logger.error("[%s] Ошибка обработчика '%s': %s", connection_id, msg_type, e, exc_info=True)


# ── Регистрируем обработчики ─────────────────────────────────────────────────

@handler("scan_result")
async def handle_scan_result(connection_id: str, data: dict) -> None:
    from .aggregator import aggregate
    from .connection_manager import manager
    from .vlm.client import evaluate_building

    label_raw = data.get("label", "unnamed")
    blocks    = data.get("blocks", [])
    center    = data.get("center", {})
    radius    = data.get("radius", 0)

    # Пробуем распарсить label как JSON-метаданные от KubeJS validate_trigger
    try:
        meta: dict       = json.loads(label_raw)
        building_type    = meta.get("building_type", "unknown")
        tech_passed      = meta.get("tech_passed", False)
        player           = meta.get("player", "unknown")
        tech_checks_raw  = meta.get("tech_checks", [])   # список {name, ok, message}
        is_validate      = True
    except (json.JSONDecodeError, TypeError):
        is_validate   = False
        building_type = label_raw
        player        = "unknown"
        tech_passed   = None
        tech_checks_raw = []

    logger.info(
        "[%s] scan_result: тип='%s', игрок='%s', центр=%s, радиус=%d, блоков=%d, tech_passed=%s",
        connection_id, building_type, player, center, radius, len(blocks), tech_passed,
    )

    # ── Обычный /scan без валидации ──────────────────────────────────────────
    if not is_validate:
        await manager.send_to(connection_id, {
            "type": "scan_ack",
            "label": label_raw,
            "block_count": len(blocks),
            "status": "received",
        })
        return

    # ── Свободная постройка или здание с провалом техпроверок ────────────────
    is_free = building_type == "free"

    if not tech_passed and not is_free:
        # KubeJS провален — немедленно отдаём результат через агрегатор
        report = aggregate(
            building_type=building_type,
            player=player,
            tech_passed=False,
            tech_checks_raw=tech_checks_raw,
            vlm=None,
        )
        await manager.send_to(connection_id, report.to_dict())
        return

    # ── Уведомляем об ожидании VLM ───────────────────────────────────────────
    await manager.send_to(connection_id, {
        "type": "validate_progress",
        "building_type": building_type,
        "player": player,
        "message": (
            "Запускаем визуальную оценку…"
            if is_free
            else "Технические проверки пройдены. Запускаем визуальную оценку…"
        ),
    })

    # ── VLM ──────────────────────────────────────────────────────────────────
    vlm = await evaluate_building(building_type, blocks)

    # ── Агрегатор собирает итоговый отчёт ────────────────────────────────────
    report = aggregate(
        building_type=building_type,
        player=player,
        tech_passed=True,
        tech_checks_raw=tech_checks_raw,
        vlm=vlm,
    )

    logger.info(
        "[%s] Итог «%s»: overall=%s, tech=%s, vlm_score=%s",
        connection_id, building_type,
        report.overall_passed, report.tech_passed, report.vlm_score,
    )

    await manager.send_to(connection_id, report.to_dict())
