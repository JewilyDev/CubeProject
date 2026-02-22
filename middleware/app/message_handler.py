"""
MessageHandler — диспетчер входящих сообщений от Minecraft.
Новые типы сообщений добавляются через декоратор @handler("type").

Изменение архитектуры (пайплайн S2C → Render → C2S):
  Ранее: scan_result содержал список блоков → Middleware рендерил через Pillow
  Теперь: scan_result содержит готовые PNG-изображения от Java-клиента
          (image_top_down, image_iso_ne, image_iso_sw в base64)
"""

import base64
import json
import logging
import os
import time
from pathlib import Path
from typing import Any, Awaitable, Callable

logger = logging.getLogger("immersiveciv.handler")


def _save_debug_images(top: str, ne: str, sw: str, label: str) -> None:
    """Сохраняет PNG на диск если задан RENDER_DEBUG_DIR. Для отладки рендеров."""
    debug_dir = os.getenv("RENDER_DEBUG_DIR")
    if not debug_dir or not (top and ne and sw):
        return
    try:
        out = Path(debug_dir)
        out.mkdir(parents=True, exist_ok=True)
        stamp = int(time.time())
        safe_label = label.replace("/", "_")[:32]
        for name, data in [("top_down", top), ("iso_ne", ne), ("iso_sw", sw)]:
            (out / f"{stamp}_{safe_label}_{name}.png").write_bytes(base64.b64decode(data))
        logger.info("DEBUG: рендеры сохранены в %s (%d_*.png)", debug_dir, stamp)
    except Exception as e:
        logger.warning("DEBUG: не удалось сохранить рендеры: %s", e)


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
    """
    Обрабатывает scan_result от сервера.

    Ожидаемые поля:
      label          — JSON-строка с метаданными от KubeJS (building_type, player, tech_checks…)
      image_top_down — PNG вид сверху    (base64, от Java-клиента)
      image_iso_ne   — PNG изометрия NE  (base64, от Java-клиента)
      image_iso_sw   — PNG изометрия SW  (base64, от Java-клиента)
    """
    from .aggregator import aggregate
    from .connection_manager import manager
    from .vlm.client import evaluate_building

    label_raw      = data.get("label", "unnamed")
    image_top_down = data.get("image_top_down", "")
    image_iso_ne   = data.get("image_iso_ne",   "")
    image_iso_sw   = data.get("image_iso_sw",   "")
    # Координаты региона — добавлены в ScanCommand.buildPayload() для BuildingRegistry
    pos1 = data.get("pos1")   # {"x": int, "y": int, "z": int} или None
    pos2 = data.get("pos2")   # {"x": int, "y": int, "z": int} или None

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
        player        = data.get("player", "unknown")
        tech_passed   = None
        tech_checks_raw = []

    has_images = bool(image_top_down and image_iso_ne and image_iso_sw)

    # ── DEBUG: сохраняем PNG на диск, если задан RENDER_DEBUG_DIR ─────────────
    # Запусти middleware с RENDER_DEBUG_DIR=./render_debug чтобы видеть рендеры
    _save_debug_images(image_top_down, image_iso_ne, image_iso_sw, building_type)
    # ── END DEBUG ─────────────────────────────────────────────────────────────

    logger.info(
        "[%s] scan_result: тип='%s', игрок='%s', has_images=%s, tech_passed=%s",
        connection_id, building_type, player, has_images, tech_passed,
    )

    # ── Обычный /scan без валидации ──────────────────────────────────────────
    if not is_validate:
        await manager.send_to(connection_id, {
            "type": "scan_ack",
            "label": label_raw,
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

    # ── VLM — передаём готовые изображения напрямую ──────────────────────────
    images = {
        "top_down": image_top_down,
        "iso_ne":   image_iso_ne,
        "iso_sw":   image_iso_sw,
    }
    vlm = await evaluate_building(building_type, images)

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

    # ГАРАНТИРУЕМ НАЛИЧИЕ ПОЛЯ TYPE ДЛЯ JAVA!
    report_data = report.to_dict()
    report_data["type"] = "validate_result"

    # Прокидываем pos1/pos2 → Java BuildingRegistry их использует для сохранения зоны
    if pos1 is not None:
        report_data["pos1"] = pos1
    if pos2 is not None:
        report_data["pos2"] = pos2

    await manager.send_to(connection_id, report_data)
