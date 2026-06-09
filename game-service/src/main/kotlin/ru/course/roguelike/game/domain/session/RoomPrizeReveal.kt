package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.TileType

/**
 * При зачистке комнаты её приз (тот, что был отмечен на двери) перемещается в центр
 * комнаты и становится виден (issue #24). Ключ приоритетнее оружия — как и метка на двери.
 */
object RoomPrizeReveal {
    fun revealAtCenter(session: GameSession, roomIndex: Int) {
        val room = session.rooms.getOrNull(roomIndex) ?: return
        val cell = safeCenterCell(session.activeMap, room)
        val cx = cell.x + 0.5f
        val cy = cell.y + 0.5f

        val keyIdx = session.keyPickups.indexOfFirst { !it.collected && room.containsWorld(it.x, it.y) }
        if (keyIdx >= 0) {
            session.keyPickups[keyIdx] = session.keyPickups[keyIdx].copy(x = cx, y = cy)
            return
        }
        val itemIdx = session.itemPickups.indexOfFirst {
            !it.collected && InventoryDefinitions.isManualPickup(it.kind) && room.containsWorld(it.x, it.y)
        }
        if (itemIdx >= 0) {
            session.itemPickups[itemIdx] = session.itemPickups[itemIdx].copy(x = cx, y = cy)
        }
    }

    /** Центр комнаты, либо ближайшая к нему проходимая клетка пола (центр мог занять столб/лава). */
    private fun safeCenterCell(map: TileMap, room: Room): GridPos {
        val center = room.center
        if (map.get(center) == TileType.FLOOR) return center
        var best: GridPos? = null
        var bestDist = Int.MAX_VALUE
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                val pos = GridPos(x, y)
                if (map.get(pos) != TileType.FLOOR) continue
                val dist = (x - center.x) * (x - center.x) + (y - center.y) * (y - center.y)
                if (dist < bestDist) {
                    bestDist = dist
                    best = pos
                }
            }
        }
        return best ?: center
    }
}
