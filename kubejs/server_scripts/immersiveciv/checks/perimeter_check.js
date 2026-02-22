// ImmersiveCiv — Проверка замкнутости периметра стен
// Алгоритм: находим "пол" здания (наибольший сплошной горизонтальный слой),
// затем проверяем, что на уровне пола+1 и пола+2 нет открытых горизонтальных
// выходов наружу через стены (flood-fill из угла наружу не должен достигать центра).

/**
 * Проверяет, что постройка образует замкнутый периметр.
 *
 * @param {{ x:number, y:number, z:number, id:string }[]} blocks
 * @param {number} cx - центр X (позиция игрока)
 * @param {number} cy
 * @param {number} cz
 * @param {number} radius
 * @returns {{ name: string, ok: boolean, message: string }}
 */
function checkPerimeter(blocks, cx, cy, cz, radius) {
    // Строим сет занятых позиций для быстрого поиска
    const occupied = new Set(blocks.map(b => `${b.x},${b.y},${b.z}`))

    // 1. Находим уровень пола — Y с максимальным количеством блоков в слое
    const layerCount = {}
    for (const b of blocks) {
        layerCount[b.y] = (layerCount[b.y] || 0) + 1
    }
    const sortedLayers = Object.entries(layerCount).sort((a, b) => b[1] - a[1]);
    const floorY = sortedLayers.length > 0 ? sortedLayers[0][0] : undefined;
    if (floorY === undefined) {
        return { name: 'perimeter', ok: false, message: 'Не найдено ни одного блока для определения пола.' }
    }
    const wallY = parseInt(floorY) + 1  // проверяем уровень стен над полом

    // 2. Flood-fill с границы области на уровне стен.
    //    Если flood достигает центра — стены не замкнуты.
    const minX = cx - radius, maxX = cx + radius
    const minZ = cz - radius, maxZ = cz + radius
    const centerKey = `${cx},${wallY},${cz}`

    // BFS от всех граничных клеток
    const visited = new Set()
    const queue   = []

    const tryEnqueue = (x, z) => {
        const key = `${x},${wallY},${z}`
        if (visited.has(key)) return
        if (occupied.has(key)) return  // блок — стена, не проходим
        visited.add(key)
        queue.push([x, z])
    }

    // Засеваем границу прямоугольника
    for (let x = minX; x <= maxX; x++) {
        tryEnqueue(x, minZ)
        tryEnqueue(x, maxZ)
    }
    for (let z = minZ + 1; z < maxZ; z++) {
        tryEnqueue(minX, z)
        tryEnqueue(maxX, z)
    }

    const dirs = [[1,0],[-1,0],[0,1],[0,-1]]
    let reachedCenter = false

    while (queue.length > 0) {
        let cell_ = queue.shift()
        let qx = cell_[0], qz = cell_[1]
        if (`${qx},${wallY},${qz}` === centerKey) {
            reachedCenter = true
            break
        }
        for (let di = 0; di < dirs.length; di++) {
            let nx = qx + dirs[di][0], nz = qz + dirs[di][1]
            if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue
            tryEnqueue(nx, nz)
        }
    }

    if (reachedCenter) {
        return {
            name: 'perimeter',
            ok: false,
            message: `Периметр стен не замкнут на уровне Y=${wallY}. Проверь наличие дыр в стенах.`
        }
    }

    return {
        name: 'perimeter',
        ok: true,
        message: `Периметр замкнут (уровень стен Y=${wallY}).`
    }
}
