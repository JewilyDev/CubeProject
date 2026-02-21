"""
Рендерер блок-дампов через Pillow.

Генерирует три PNG для передачи в GPT-4o:
  1. top-down    — вид сверху (план постройки)
  2. iso_ne      — изометрия с северо-востока
  3. iso_sw      — изометрия с юго-запада

Цвет блока:
  Приоритет 1 — поле map_color из Java (MapColor.col, int 0xRRGGBB).
                Это точный цвет с Minecraft-карт, 100% совпадение с игрой.
  Приоритет 2 — ключевые слова в id блока (fallback для Create и модов).
  Приоритет 3 — серый по умолчанию.
"""

from __future__ import annotations

import base64
import hashlib
import io
from typing import NamedTuple

from PIL import Image, ImageDraw

# ── Размеры ───────────────────────────────────────────────────────────────────

ISO_W  = 32   # ширина верхней грани ромба (пикс)
ISO_H  = 16   # высота верхней грани ромба (половина)
ISO_D  = 10   # высота боковой грани на один блок Y
TOP_SZ = 8    # размер одной клетки в top-down виде (пикс)

# Minecraft применяет три шейдинг-множителя к base map_color:
# грань: top × 1.0, right × 0.86, left × 0.71
_SHADE_TOP   = 1.00
_SHADE_RIGHT = 0.86
_SHADE_LEFT  = 0.71
_SHADE_DARK  = 0.53   # тень при AO (ambient occlusion, необязательно)

# ── Fallback-палитра по ключевым словам в id ──────────────────────────────────
# Формат: (r, g, b) — base цвет, шейдинг применяется автоматически

_KEYWORD_COLORS: list[tuple[str, tuple[int, int, int]]] = [
    # Цвета шерсти / стекла / бетона / терракоты
    ("white",       (255, 255, 255)),
    ("orange",      (216, 127,  51)),
    ("magenta",     (178,  76, 216)),
    ("light_blue",  (102, 153, 216)),
    ("yellow",      (229, 229,  51)),
    ("lime",        (127, 204,  25)),
    ("pink",        (242, 127, 165)),
    ("gray",        ( 76,  76,  76)),
    ("light_gray",  (153, 153, 153)),
    ("cyan",        ( 76, 127, 153)),
    ("purple",      (127,  63, 178)),
    ("blue",        ( 51,  76, 178)),
    ("brown",       (102,  76,  51)),
    ("green",       (102, 127,  51)),
    ("red",         (153,  51,  51)),
    ("black",       ( 25,  25,  25)),
    # Дерево
    ("oak",         (197, 154,  85)),
    ("spruce",      (114,  84,  48)),
    ("birch",       (216, 203, 154)),
    ("jungle",      (160, 115,  80)),
    ("acacia",      (168,  90,  50)),
    ("dark_oak",    ( 66,  43,  20)),
    ("mangrove",    (120,  40,  30)),
    ("cherry",      (226, 179, 191)),
    ("bamboo",      (196, 182,  70)),
    ("crimson",     (149,  29,  55)),
    ("warped",      ( 58, 142, 140)),
    # Камень
    ("deepslate",   ( 72,  72,  78)),
    ("blackstone",  ( 43,  35,  44)),
    ("basalt",      ( 92,  90,  94)),
    ("andesite",    (136, 136, 136)),
    ("diorite",     (255, 255, 255)),
    ("granite",     (162,  85,  57)),
    ("sandstone",   (216, 201, 148)),
    ("prismarine",  ( 99, 164, 157)),
    ("obsidian",    ( 23,  16,  35)),
    ("netherrack",  (100,  31,  30)),
    ("nether_brick",( 72,  18,  18)),
    ("end_stone",   (219, 222, 159)),
    ("quartz",      (235, 229, 222)),
    # Металлы
    ("iron",        (220, 220, 220)),
    ("gold",        (250, 210,  60)),
    ("diamond",     (100, 230, 225)),
    ("emerald",     ( 17, 174,  55)),
    ("copper",      (182, 113,  76)),
    ("amethyst",    (154,  94, 196)),
    # Create мод
    ("brass",       (215, 175,  80)),
    ("andesite_alloy", (136, 136, 136)),
    ("zinc",        (200, 210, 215)),
    ("cast_iron",   ( 80,  80,  90)),
    ("steam",       (200, 210, 220)),
    # Утилиты / функционал
    ("glass",       (190, 225, 255)),
    ("water",       ( 64, 140, 210)),
    ("lava",        (255, 100,   0)),
    ("fire",        (255, 150,  30)),
    ("ice",         (145, 185, 230)),
    ("snow",        (255, 255, 255)),
    ("sand",        (219, 207, 163)),
    ("gravel",      (130, 120, 115)),
    ("dirt",        (143, 106,  67)),
    ("grass",       (100, 170,  70)),
    ("farmland",    (103,  75,  44)),
    ("leaves",      ( 55, 150,  60)),
    ("log",         (110,  80,  40)),
    ("wood",        (155, 125,  65)),
    ("planks",      (190, 150,  85)),
    ("stone",       (125, 125, 125)),
    ("cobblestone", (117, 117, 117)),
    ("brick",       (180,  97,  80)),
    ("wool",        (200, 200, 200)),
    ("concrete",    (180, 180, 180)),
    ("terracotta",  (162, 100,  75)),
    ("coral",       (255,  80, 120)),
    ("sponge",      (195, 195, 100)),
    ("clay",        (162, 166, 182)),
    ("glowstone",   (245, 225, 170)),
    ("shroomlight", (240, 160,  90)),
    ("sea_lantern", (165, 205, 200)),
    ("lantern",     (220, 180, 100)),
    ("torch",       (230, 190, 100)),
    ("chest",       (185, 140,  70)),
    ("furnace",     (110, 110, 110)),
    ("anvil",       (100, 100, 100)),
    ("bookshelf",   (185, 140,  70)),
    ("enchanting",  (180,  30,  30)),
    ("brewing",     (100,  90, 120)),
    ("cauldron",    (100, 105, 110)),
    ("fence",       (185, 148,  82)),
    ("door",        (185, 148,  82)),
    ("gate",        (185, 148,  82)),
    ("slab",        (125, 125, 125)),
    ("stairs",      (125, 125, 125)),
    ("wall",        (125, 125, 125)),
]

_DEFAULT_BASE = (160, 160, 160)


# ── Определение цвета блока ───────────────────────────────────────────────────

def _base_color(block: dict) -> tuple[int, int, int]:
    """
    Возвращает base RGB для блока.
    Использует map_color если есть, иначе keyword-fallback.
    """
    mc = block.get("map_color")
    if mc and mc != 0:
        r = (mc >> 16) & 0xFF
        g = (mc >>  8) & 0xFF
        b =  mc        & 0xFF
        return (r, g, b)

    block_id = block.get("id", "")
    name = block_id.split(":")[-1] if ":" in block_id else block_id
    for keyword, color in _KEYWORD_COLORS:
        if keyword in name:
            return color

    return _DEFAULT_BASE


def _shade(base: tuple[int, int, int], mult: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, int(c * mult))) for c in base)


# ── TOP-DOWN рендер ───────────────────────────────────────────────────────────

def render_top_down(blocks: list[dict], max_size: int = 512) -> bytes:
    """
    Вид сверху. Каждая клетка X×Z — цвет самого верхнего блока в этой колонке.
    """
    if not blocks:
        return _empty_png()

    min_x = min(b["x"] for b in blocks)
    min_z = min(b["z"] for b in blocks)
    norm  = [{"x": b["x"] - min_x, "z": b["z"] - min_z,
              "y": b["y"], "id": b.get("id", ""), "map_color": b.get("map_color", 0)}
             for b in blocks]

    max_x = max(b["x"] for b in norm)
    max_z = max(b["z"] for b in norm)

    # Для каждой (x, z) — берём блок с максимальным Y
    top_block: dict[tuple[int,int], dict] = {}
    for b in norm:
        key = (b["x"], b["z"])
        if key not in top_block or b["y"] > top_block[key]["y"]:
            top_block[key] = b

    w = (max_x + 1) * TOP_SZ
    h = (max_z + 1) * TOP_SZ
    img  = Image.new("RGB", (w, h), (20, 20, 20))
    draw = ImageDraw.Draw(img)

    for (x, z), b in top_block.items():
        base  = _base_color(b)
        color = _shade(base, _SHADE_TOP)
        px, pz = x * TOP_SZ, z * TOP_SZ
        draw.rectangle([px, pz, px + TOP_SZ - 1, pz + TOP_SZ - 1], fill=color)
        # Тонкая сетка
        draw.rectangle([px, pz, px + TOP_SZ - 1, pz + TOP_SZ - 1],
                        outline=(0, 0, 0), width=1)

    return _scale_and_encode(img, max_size)


# ── ИЗОМЕТРИЯ — общая логика ──────────────────────────────────────────────────

def _draw_iso_block(draw: ImageDraw.ImageDraw, sx: int, sy: int,
                    base: tuple[int, int, int], mirror: bool = False) -> None:
    hw = ISO_W // 2
    hh = ISO_H // 2
    d  = ISO_D

    top_color   = _shade(base, _SHADE_TOP)
    right_color = _shade(base, _SHADE_RIGHT)
    left_color  = _shade(base, _SHADE_LEFT)

    if mirror:
        right_color, left_color = left_color, right_color

    # Верхняя грань
    top_pts = [(sx, sy - hh), (sx + hw, sy), (sx, sy + hh), (sx - hw, sy)]
    draw.polygon(top_pts, fill=top_color)

    # Правая боковая
    draw.polygon([(sx, sy + hh), (sx + hw, sy),
                  (sx + hw, sy + d), (sx, sy + hh + d)], fill=right_color)

    # Левая боковая
    draw.polygon([(sx - hw, sy), (sx, sy + hh),
                  (sx, sy + hh + d), (sx - hw, sy + d)], fill=left_color)

    # Тонкий контур
    draw.line(top_pts + [top_pts[0]], fill=(0, 0, 0, 60), width=1)


def _render_iso(blocks: list[dict], mirror: bool, max_size: int) -> bytes:
    if not blocks:
        return _empty_png()

    min_x = min(b["x"] for b in blocks)
    min_y = min(b["y"] for b in blocks)
    min_z = min(b["z"] for b in blocks)
    norm  = [{"x": b["x"] - min_x, "y": b["y"] - min_y, "z": b["z"] - min_z,
              "id": b.get("id", ""), "map_color": b.get("map_color", 0)}
             for b in blocks]

    max_x = max(b["x"] for b in norm)
    max_y = max(b["y"] for b in norm)
    max_z = max(b["z"] for b in norm)

    hw = ISO_W // 2
    hh = ISO_H // 2

    def project(x: int, y: int, z: int) -> tuple[int, int]:
        if mirror:
            sx = (z - x) * hw
        else:
            sx = (x - z) * hw
        sy = (x + z) * hh - y * ISO_D
        return sx, sy

    corners = [(x, y, z) for x in (0, max_x) for y in (0, max_y) for z in (0, max_z)]
    pts = [project(x, y, z) for x, y, z in corners]
    xs  = [p[0] for p in pts]
    ys  = [p[1] for p in pts]

    pad     = 24
    offset_x = -min(xs) + pad
    offset_y = -min(ys) + pad
    w = max(xs) - min(xs) + ISO_W + pad * 2
    h = max(ys) - min(ys) + ISO_H + ISO_D + pad * 2

    img  = Image.new("RGB", (w, h), (20, 20, 20))
    draw = ImageDraw.Draw(img, "RGBA")

    # Painter's algorithm:
    # NE (mirror=False): дальние = малый x+z → рисуем первыми
    # SW (mirror=True):  дальние = большой x+z → рисуем первыми
    if mirror:
        sorted_blocks = sorted(norm, key=lambda b: (-(b["x"] + b["z"]), b["y"]))
    else:
        sorted_blocks = sorted(norm, key=lambda b: (b["x"] + b["z"], b["y"]))

    for b in sorted_blocks:
        sx, sy = project(b["x"], b["y"], b["z"])
        sx += offset_x
        sy += offset_y
        _draw_iso_block(draw, sx, sy, _base_color(b), mirror=mirror)

    return _scale_and_encode(img, max_size)


def render_iso_ne(blocks: list[dict], max_size: int = 512) -> bytes:
    """Изометрия с северо-востока (классический вид)."""
    return _render_iso(blocks, mirror=False, max_size=max_size)


def render_iso_sw(blocks: list[dict], max_size: int = 512) -> bytes:
    """Изометрия с юго-запада (зеркальный вид)."""
    return _render_iso(blocks, mirror=True, max_size=max_size)


# ── Публичный API ─────────────────────────────────────────────────────────────

def render_all_views(blocks: list[dict], max_size: int = 512) -> list[bytes]:
    """
    Возвращает список из трёх PNG: [top_down, iso_ne, iso_sw].
    Используется в vlm/client.py для отправки всех трёх в GPT-4o.
    """
    return [
        render_top_down(blocks, max_size),
        render_iso_ne(blocks, max_size),
        render_iso_sw(blocks, max_size),
    ]


def blocks_to_hash(blocks: list[dict]) -> str:
    """SHA-256 хеш блок-дампа для кеширования."""
    canonical = sorted((b["x"], b["y"], b["z"], b.get("id", "")) for b in blocks)
    return hashlib.sha256(str(canonical).encode()).hexdigest()


def png_to_base64(png_bytes: bytes) -> str:
    return base64.b64encode(png_bytes).decode()


# ── Вспомогательные ───────────────────────────────────────────────────────────

def _empty_png() -> bytes:
    img = Image.new("RGB", (64, 64), (40, 40, 40))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _scale_and_encode(img: Image.Image, max_size: int) -> bytes:
    w, h = img.size
    if max(w, h) > max_size:
        ratio = max_size / max(w, h)
        img   = img.resize((int(w * ratio), int(h * ratio)), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    return buf.getvalue()
