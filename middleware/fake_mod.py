"""
fake_mod.py — имитирует Java-мод для тестирования Middleware без Minecraft.

Использование:
    # Терминал 1 — запустить Middleware
    python main.py

    # Терминал 2 — запустить фейковый мод
    python fake_mod.py [сценарий]

Доступные сценарии (передать как аргумент):
    farm_ok       — ферма, все проверки пройдены   (по умолчанию)
    forge_tech_fail — кузница, техпроверка провалена
    free_build    — свободная постройка (/freebuild)
    plain_scan    — обычный /scan без валидации
"""

import asyncio
import json
import sys
import websockets

WS_URL = "ws://localhost:8765/ws"

# ── Сценарии ──────────────────────────────────────────────────────────────────

SCENARIOS: dict[str, dict] = {

    "farm_ok": {
        "description": "Ферма — все техпроверки пройдены, ждём VLM",
        "payload": {
            "type": "scan_result",
            "label": json.dumps({
                "building_type": "farm",
                "tech_passed": True,
                "player": "TestPlayer",
                "tech_checks": [
                    {"name": "perimeter",       "ok": True,  "message": "Периметр замкнут (Y=65)"},
                    {"name": "required_blocks", "ok": True,  "message": "Все обязательные блоки присутствуют"},
                ],
            }),
            "center": {"x": 0, "y": 64, "z": 0},
            "radius": 8,
            "blocks": [
                # Пол фермы
                *[{"x": x, "y": 64, "z": z, "id": "minecraft:farmland"}
                  for x in range(-3, 4) for z in range(-3, 4)],
                # Вода
                {"x": 0, "y": 64, "z": 0, "id": "minecraft:water"},
                # Посевы
                *[{"x": x, "y": 65, "z": z, "id": "minecraft:wheat"}
                  for x in range(-2, 3) for z in range(-2, 3)],
                # Ограждение
                *[{"x": x, "y": 65, "z": -4, "id": "minecraft:oak_fence"} for x in range(-4, 5)],
                *[{"x": x, "y": 65, "z":  4, "id": "minecraft:oak_fence"} for x in range(-4, 5)],
                *[{"x": -4, "y": 65, "z": z, "id": "minecraft:oak_fence"} for z in range(-3, 4)],
                *[{"x":  4, "y": 65, "z": z, "id": "minecraft:oak_fence"} for z in range(-3, 4)],
            ],
        },
    },

    "forge_tech_fail": {
        "description": "Кузница — техпроверка провалена (нет наковальни), VLM не запустится",
        "payload": {
            "type": "scan_result",
            "label": json.dumps({
                "building_type": "foundry",
                "tech_passed": False,
                "player": "TestPlayer",
                "tech_checks": [
                    {"name": "perimeter",       "ok": True,  "message": "Периметр замкнут"},
                    {"name": "required_blocks", "ok": False,
                     "message": "Не хватает обязательных блоков:\n  • «наковальня» — нужно 1, найдено 0"},
                    {"name": "su_generation",   "ok": False,
                     "message": "Недостаточно мощности. Генерация: ~0 SU | Потребление: ~256 SU"},
                ],
            }),
            "center": {"x": 50, "y": 64, "z": 50},
            "radius": 8,
            "blocks": [
                {"x": 50, "y": 64, "z": 50, "id": "minecraft:blast_furnace"},
                *[{"x": 50 + dx, "y": 64, "z": 50 + dz, "id": "minecraft:stone_bricks"}
                  for dx in range(-3, 4) for dz in (-3, 3)],
            ],
        },
    },

    "free_build": {
        "description": "Свободная постройка — VLM без чеклиста, возможен XP-бонус",
        "payload": {
            "type": "scan_result",
            "label": json.dumps({
                "building_type": "free",
                "tech_passed": True,
                "player": "TestPlayer",
                "tech_checks": [],
            }),
            "center": {"x": 200, "y": 64, "z": 200},
            "radius": 10,
            "blocks": [
                # Небольшая башня из камня со стеклом
                *[{"x": 200 + dx, "y": 64 + dy, "z": 200 + dz,
                   "id": "minecraft:stone_bricks" if dy < 6 else "minecraft:glass"}
                  for dx in range(-2, 3) for dz in range(-2, 3)
                  for dy in range(8)
                  if abs(dx) == 2 or abs(dz) == 2],
            ],
        },
    },

    "plain_scan": {
        "description": "Обычный /scan без валидации — должен вернуть scan_ack",
        "payload": {
            "type": "scan_result",
            "label": "my_area",
            "center": {"x": 0, "y": 64, "z": 0},
            "radius": 4,
            "blocks": [
                {"x": 0, "y": 64, "z": 0, "id": "minecraft:dirt"},
                {"x": 1, "y": 64, "z": 0, "id": "minecraft:grass_block"},
            ],
        },
    },
}

# ── Основной цикл ─────────────────────────────────────────────────────────────

async def run(scenario_name: str) -> None:
    scenario = SCENARIOS.get(scenario_name)
    if scenario is None:
        print(f"Неизвестный сценарий: '{scenario_name}'")
        print(f"Доступные: {', '.join(SCENARIOS)}")
        return

    print(f"\n{'='*60}")
    print(f"Сценарий: {scenario_name}")
    print(f"Описание: {scenario['description']}")
    print(f"{'='*60}\n")

    try:
        async with websockets.connect(WS_URL) as ws:
            print(f"✔ Подключён к Middleware ({WS_URL})\n")

            # Отправляем пакет
            payload = scenario["payload"]
            print("→ Отправляем:")
            print(json.dumps(payload, ensure_ascii=False, indent=2)[:600])
            print()
            await ws.send(json.dumps(payload))

            # Слушаем ответы (может прийти несколько: progress + result)
            print("← Ответы от Middleware:")
            timeout = 60  # ждём до 60 сек (VLM может думать)
            deadline = asyncio.get_event_loop().time() + timeout
            while True:
                remaining = deadline - asyncio.get_event_loop().time()
                if remaining <= 0:
                    print("(таймаут ожидания)")
                    break
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
                    data = json.loads(raw)
                    msg_type = data.get("type", "?")
                    print(f"\n[{msg_type}]")
                    print(json.dumps(data, ensure_ascii=False, indent=2))

                    # summary_lines — самое важное для игрока
                    if "summary_lines" in data:
                        print("\n── Текст игроку ──────────────────────")
                        for line in data["summary_lines"]:
                            # Убираем §-коды для читаемости в терминале
                            clean = ""
                            skip = False
                            for ch in line:
                                if ch == "§":
                                    skip = True
                                elif skip:
                                    skip = False
                                else:
                                    clean += ch
                            print(clean)
                        print("──────────────────────────────────────")

                    # После финального результата выходим
                    if msg_type in ("validate_result", "scan_ack"):
                        break
                except asyncio.TimeoutError:
                    print("(таймаут)")
                    break

    except ConnectionRefusedError:
        print(f"✘ Не удалось подключиться к {WS_URL}")
        print("  Убедись, что Middleware запущен: python main.py")


def main() -> None:
    scenario = sys.argv[1] if len(sys.argv) > 1 else "farm_ok"
    asyncio.run(run(scenario))


if __name__ == "__main__":
    main()
