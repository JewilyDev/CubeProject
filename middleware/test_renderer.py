"""
test_renderer.py — проверяет изометрический рендерер без Middleware и без API.

Генерирует PNG-файлы для нескольких тестовых построек и открывает их.

Использование:
    cd middleware
    python test_renderer.py
"""

import os
import sys
from app.vlm.renderer import render_all_views, blocks_to_hash

# ── Тестовые постройки ────────────────────────────────────────────────────────

CASES = {
    "farm": [
        *[{"x": x, "y": 0, "z": z, "id": "minecraft:farmland"}
          for x in range(7) for z in range(7)],
        {"x": 3, "y": 0, "z": 3, "id": "minecraft:water"},
        *[{"x": x, "y": 1, "z": z, "id": "minecraft:wheat"}
          for x in range(1, 6) for z in range(1, 6)],
        *[{"x": x, "y": 1, "z": 0, "id": "minecraft:oak_fence"} for x in range(7)],
        *[{"x": x, "y": 1, "z": 6, "id": "minecraft:oak_fence"} for x in range(7)],
        *[{"x": 0, "y": 1, "z": z, "id": "minecraft:oak_fence"} for z in range(1, 6)],
        *[{"x": 6, "y": 1, "z": z, "id": "minecraft:oak_fence"} for z in range(1, 6)],
    ],

    "watchtower": [
        # Квадратное основание
        *[{"x": x, "y": y, "z": z, "id": "minecraft:stone_bricks"}
          for x in range(5) for z in range(5) for y in range(14)
          if x in (0, 4) or z in (0, 4)],
        # Зубцы
        *[{"x": x, "y": 14, "z": z, "id": "minecraft:stone_brick_slab"}
          for x in (0, 2, 4) for z in range(5)],
        *[{"x": x, "y": 14, "z": z, "id": "minecraft:stone_brick_slab"}
          for z in (0, 2, 4) for x in range(1, 4)],
        # Лестница внутри
        *[{"x": 1, "y": y, "z": 1, "id": "minecraft:ladder"} for y in range(14)],
    ],

    "foundry": [
        # Стены
        *[{"x": x, "y": y, "z": z, "id": "minecraft:stone_bricks"}
          for x in range(7) for z in range(7) for y in range(5)
          if x in (0, 6) or z in (0, 6)],
        # Крыша
        *[{"x": x, "y": 5, "z": z, "id": "minecraft:stone_brick_slab"}
          for x in range(7) for z in range(7)],
        # Оборудование
        {"x": 2, "y": 1, "z": 2, "id": "minecraft:blast_furnace"},
        {"x": 3, "y": 1, "z": 2, "id": "minecraft:blast_furnace"},
        {"x": 4, "y": 1, "z": 4, "id": "minecraft:anvil"},
        {"x": 2, "y": 1, "z": 4, "id": "minecraft:chest"},
        # Дымоход
        *[{"x": 3, "y": y, "z": 3, "id": "minecraft:stone_bricks"} for y in range(5, 9)],
    ],

    "greenhouse": [
        # Стеклянные стены
        *[{"x": x, "y": y, "z": z, "id": "minecraft:glass"}
          for x in range(8) for z in range(8) for y in range(1, 5)
          if x in (0, 7) or z in (0, 7)],
        # Стеклянная крыша
        *[{"x": x, "y": 5, "z": z, "id": "minecraft:glass"}
          for x in range(8) for z in range(8)],
        # Пол с фармлэндом
        *[{"x": x, "y": 0, "z": z, "id": "minecraft:farmland"}
          for x in range(1, 7) for z in range(1, 7)],
        # Вода посередине
        {"x": 3, "y": 0, "z": 3, "id": "minecraft:water"},
        {"x": 4, "y": 0, "z": 3, "id": "minecraft:water"},
        # Растения
        *[{"x": x, "y": 1, "z": z, "id": "minecraft:wheat"}
          for x in range(1, 7) for z in range(1, 7) if not (3 <= x <= 4 and z == 3)],
    ],
}

# ── Запуск ────────────────────────────────────────────────────────────────────

def main() -> None:
    out_dir = os.path.join(os.path.dirname(__file__), "test_renders")
    os.makedirs(out_dir, exist_ok=True)

    VIEW_NAMES = ["top_down", "iso_ne", "iso_sw"]
    generated = []
    for name, blocks in CASES.items():
        print(f"Рендерим «{name}»… ({len(blocks)} блоков)", end=" ", flush=True)
        views = render_all_views(blocks, max_size=512)
        h = blocks_to_hash(blocks)
        for view_name, png in zip(VIEW_NAMES, views):
            path = os.path.join(out_dir, f"{name}_{view_name}.png")
            with open(path, "wb") as f:
                f.write(png)
            generated.append(path)
        print(f"OK  (hash: {h[:12]}…) → {name}_top_down/iso_ne/iso_sw.png")

    print(f"\nГотово. Файлы в: {out_dir}")

    # Открыть в системном просмотрщике (Windows)
    if sys.platform == "win32":
        answer = input("Открыть файлы? (y/n): ").strip().lower()
        if answer == "y":
            for path in generated:
                os.startfile(path)


if __name__ == "__main__":
    main()
