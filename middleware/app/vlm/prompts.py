"""
Промпт-шаблоны для VLM-оценки зданий.
Каждый шаблон описывает, что именно GPT-4o должен оценивать визуально,
в дополнение к уже пройденным техническим проверкам KubeJS.
"""

# Системный промпт — общий для всех зданий
SYSTEM_PROMPT = """You are an expert evaluator of buildings in a Minecraft city-builder mod called "Immersive Civilisation".
You receive an isometric view of a player's building and assess its visual quality.

Rules:
- KubeJS has already verified technical requirements (block counts, perimeter closure, SU generation).
  Your job is ONLY to evaluate aesthetic and structural quality.
- Be constructive and specific. Players use this feedback to improve.
- Respond ONLY with valid JSON — no markdown, no extra text.
- Score range: 0–100. Passing threshold is 60.

Response format:
{
  "score": <integer 0-100>,
  "passed": <bool>,
  "style_rating": "<poor|fair|good|excellent>",
  "comments": "<2-4 sentences in the player's language: Russian>",
  "improvements": ["<specific suggestion 1>", "<specific suggestion 2>"]
}"""

# Шаблоны по типам зданий
# Ключи совпадают с типами из required_blocks_check.js
BUILDING_PROMPTS: dict[str, str] = {

    # ── Мастер продовольствия ────────────────────────────────────────────────
    "well": """Evaluate this WELL building.
Visual criteria:
- Central water source, clearly visible
- Stone or brick walls forming a ring or square around the water (at least 3 blocks high)
- Optional: wooden roof structure or bucket hanging above
- Surrounding area should not be cluttered with random blocks
Deduct points for: exposed dirt under the water, no surrounding wall, floating blocks.""",

    "farm": """Evaluate this FARM building.
Visual criteria:
- Organised rows of farmland with crops (not random patches)
- Clear water channel visible (not buried)
- Fencing or low walls enclosing the field
- Optional: small barn or tool shed nearby
Deduct points for: crops mixed randomly, no water visible, no enclosure.""",

    "barn": """Evaluate this BARN / GRANARY building.
Visual criteria:
- Rectangular wooden structure with a clear roof (slabs or stairs)
- Door(s) present and accessible
- Interior feels spacious (at least 4x4 floor area)
- Optional: hay bales, barrels, ladders inside
Deduct points for: open ceiling, single-layer wall with no depth, no door.""",

    "bakery": """Evaluate this BAKERY building.
Visual criteria:
- Enclosed stone or brick structure
- Chimneys or furnaces visible (smoke effect if present)
- Workspace feel: counters (slabs), shelves (item frames)
- Optional: small window openings, signage (signs)
Deduct points for: furnaces floating outside, no roof, no counter space.""",

    "tavern": """Evaluate this TAVERN building.
Visual criteria:
- Welcoming entrance (door, torches/lanterns flanking it)
- Interior has a bar counter (fence + slab combination)
- Seating area with tables (fence + pressure plate) and chairs (stairs)
- Beds visible upstairs or in a separate room
- Warm lighting (candles, lanterns)
Deduct points for: no counter, no seating, dark interior, no entrance lighting.""",

    "greenhouse": """Evaluate this GREENHOUSE building.
Visual criteria:
- Predominantly glass walls and/or roof (at least 60% of the structure)
- Plants or farmland clearly visible inside through the glass
- Structured rows, not random dirt placement
- Optional: watering channels visible inside
Deduct points for: opaque walls blocking the view, no plants visible, incomplete glass roof.""",

    # ── Мастер инженерии ─────────────────────────────────────────────────────
    "windmill": """Evaluate this WINDMILL building.
Visual criteria:
- Tall vertical structure (at least 8 blocks high)
- Sail blades extending from a central shaft (wool or wooden planks in cross pattern)
- Sails should be symmetrical and proportional
- Base structure (stone or wood) anchoring the tower
Deduct points for: asymmetric sails, sails below roof level, no visible shaft/axis.""",

    "foundry": """Evaluate this FOUNDRY / FORGE building.
Visual criteria:
- Solid stone or brick construction (industrial feel)
- Blast furnace or Create blaze burner clearly visible
- Anvil in an accessible location
- Chimneys or venting structures (half-slabs on top)
- Optional: gear or shaft mechanism visible (Create)
Deduct points for: no chimney, anvil behind walls with no access, wood-dominated structure.""",

    "press": """Evaluate this MECHANICAL PRESS building.
Visual criteria:
- Create mechanical press clearly positioned and accessible
- Belt/conveyor system leading to and from the press
- Enclosed workshop structure with a roof
- Power source (shaft, cogwheel) visible and connected
Deduct points for: press floating with no structure around it, no belt, disconnected power source.""",

    "assembly": """Evaluate this ASSEMBLY LINE building.
Visual criteria:
- Long rectangular structure accommodating the belt length
- Multiple deployers positioned above the belt
- Clear input and output ends of the line
- Roof over the production area
Deduct points for: deployers not aligned with belt, no clear flow direction, open sides.""",

    "depot": """Evaluate this DEPOT / WAREHOUSE building.
Visual criteria:
- Large rectangular storage building
- Multiple chests or barrels organised in rows
- Hopper network visible (feeding chests)
- Rail tracks leading into or out of the depot
- Loading dock feel (open bay or large door)
Deduct points for: single chest in open field, no rail connection, no organisation.""",

    # ── Мастер обороны ───────────────────────────────────────────────────────
    "watchtower": """Evaluate this WATCHTOWER building.
Visual criteria:
- Tall narrow structure (at least 12 blocks high for Tier 1)
- Battlements or merlons at the top (slabs or walls with gaps)
- Ladder or staircase inside for access
- Arrow slits (single-block gaps in walls) optional but appreciated
- Solid stone or deepslate construction
Deduct points for: wooden tower, no battlements, no internal access, shorter than 8 blocks.""",

    "barracks": """Evaluate this BARRACKS building.
Visual criteria:
- Military aesthetic: stone or dark oak construction
- Multiple beds arranged in rows
- Weapon racks (fence posts with items) or armour stands
- Clear entrance with iron door preferred
- Optional: training yard adjacent
Deduct points for: beds scattered randomly, no weapon storage, no military feel.""",

    "armory": """Evaluate this ARMOURY building.
Visual criteria:
- Secure feel: iron bars, stone brick walls
- Smithing table and grindstone clearly positioned
- Storage for weapons/armour (chests, item frames on walls)
- Organised layout (not cluttered)
Deduct points for: no smithing station, open walls with no security, random chest placement.""",

    "outpost": """Evaluate this OUTPOST building.
Visual criteria:
- Compact defensive structure (walls with battlements)
- Firing positions (elevated platform or archer slits)
- Torch or lantern lighting for night visibility
- Optional: flagpole (fence post + banner) marking territory
Deduct points for: no elevation advantage, no lighting, no defensive walls.""",

    # ── Мастер магии ─────────────────────────────────────────────────────────
    "library": """Evaluate this LIBRARY building.
Visual criteria:
- Bookshelves covering walls (library aesthetic)
- Enchanting table as centrepiece
- Warm, dim lighting (candles preferred over torches)
- Organised aisles or reading area
- Optional: second floor with more shelves accessible via ladder
Deduct points for: bookshelves in random positions, harsh torch lighting, no reading space.""",

    "alchemylab": """Evaluate this ALCHEMY LAB building.
Visual criteria:
- Multiple brewing stands as focal points
- Cauldron(s) visible
- Mystical lighting (soul lanterns, candles)
- Cluttered-but-organised feel (item frames with ingredients)
- Optional: shelving with potion storage
Deduct points for: brewing stands in open air, no ambient lighting, sterile empty room.""",

    "mage_tower": """Evaluate this MAGE TOWER building.
Visual criteria:
- Tall narrow tower (at least 16 blocks), obsidian or deepslate base
- Coloured glass windows (purple, blue, cyan)
- Enchanting table visible through windows or at the top
- Magical feel: crying obsidian accents, amethyst blocks if available
- Spiral staircase or ladder inside
Deduct points for: short tower, no coloured glass, no magical materials, no enchanting table visible.""",

    "observatory": """Evaluate this OBSERVATORY building.
Visual criteria:
- Dome or curved roof structure (glass dominant)
- Central telescope mechanism (tall iron/brass column pointing up)
- Open sky access (roof can rotate or open — aesthetic only)
- Stone base structure elevating the dome
Deduct points for: opaque roof, no central instrument, dome not round/curved.""",

    "archive": """Evaluate this ARCHIVE building.
Visual criteria:
- Dense bookshelf arrangement covering all walls
- Lectern as central reading station
- Organised chest storage for documents/maps
- Quiet, scholarly atmosphere (candles, no harsh lighting)
- Optional: secret room or locked chest area
Deduct points for: sparse bookshelves, no lectern, torch lighting only, empty floor space.""",

    # ── Мэр ──────────────────────────────────────────────────────────────────
    "town_hall": """Evaluate this TOWN HALL building.
Visual criteria:
- Impressive, symmetrical facade (the largest building in the settlement)
- Polished deepslate or stone brick construction (high tier feel)
- Banner or flag prominently displayed on the front
- Lectern or throne area inside as the seat of government
- Grand entrance (double door or arched opening)
Deduct points for: asymmetric design, no flag/banner, smaller than surrounding buildings, plain facade.""",

    "bank": """Evaluate this BANK building.
Visual criteria:
- Secure, imposing stone structure
- Iron bars on windows or a vault-like interior
- Organised chest storage (vault room feel)
- Counter area (smooth stone slab bar)
- Optional: weighing scales (item frame + gold)
Deduct points for: wooden construction, no security features, no counter, random chests.""",

    "market": """Evaluate this MARKET SQUARE building.
Visual criteria:
- Open-air or partially covered structure (not fully enclosed)
- Multiple stall counters (fence + slab) in an organised grid
- Cartography table as the trade registry
- Awnings or canopies over stalls (coloured wool or slabs)
- Optional: central open plaza with lanterns
Deduct points for: single counter, no awnings, stalls not aligned, fully enclosed like a house.""",
}


def get_prompt(building_type: str) -> tuple[str, str]:
    """
    Возвращает (system_prompt, user_prompt) для указанного типа здания.
    Если тип неизвестен — возвращает общий промпт.
    """
    user_prompt = BUILDING_PROMPTS.get(building_type)
    if user_prompt is None:
        user_prompt = f"""Evaluate this {building_type.upper().replace('_', ' ')} building.
Visual criteria:
- The building should look intentional and functional, not like random block placement
- It should have a clear entrance, roof, and walls
- Interior should match the building's purpose
- Lighting should be appropriate
Score based on how well the structure communicates its purpose visually."""
    return SYSTEM_PROMPT, user_prompt
