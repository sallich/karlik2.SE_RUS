package ru.course.roguelike.game.domain.session

import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.TileType

/**
 * Видимость призов комнаты (issue #24): призы скрыты, пока комната не зачищена.
 * [doorMarkers] описывают ещё стоящие на карте [TileType.ROOM_DOOR] для отрисовки приза на стене.
 */
object RoomVisibility {
    fun roomIndexAt(session: GameSession, x: Float, y: Float): Int? {
        val index = session.rooms.indexOfFirst { it.containsWorld(x, y) }
        return if (index >= 0) index else null
    }

    fun isRoomCleared(session: GameSession, roomIndex: Int?): Boolean {
        roomIndex ?: return true
        return session.roomEngagements.getOrNull(roomIndex)?.cleared ?: true
    }

    fun isPrizeHidden(session: GameSession, x: Float, y: Float): Boolean =
        !isRoomCleared(session, roomIndexAt(session, x, y))

    fun isItemVisible(session: GameSession, item: ItemPickup): Boolean =
        !InventoryDefinitions.isManualPickup(item.kind) || !isPrizeHidden(session, item.x, item.y)

    fun isKeyVisible(session: GameSession, key: KeyPickup): Boolean =
        !isPrizeHidden(session, key.x, key.y)

    fun doorMarkers(session: GameSession): List<DoorMarkerSnapshot> =
        session.roomEngagements.flatMap { markersFor(session, it) }

    private fun markersFor(session: GameSession, state: RoomEngagementState): List<DoorMarkerSnapshot> {
        if (state.cleared || state.entered || state.doorways.isEmpty()) return emptyList()
        val room = session.rooms.getOrNull(state.roomIndex) ?: return emptyList()
        val prize = prizeOf(session, room)
        val mobRoom = prize == null && RoomEngagementSystem.hasLivingMobs(session, state.roomIndex)
        if (prize == null && !mobRoom) return emptyList()
        return state.doorways
            .filter { session.map.get(it) == TileType.ROOM_DOOR }
            .map {
                DoorMarkerSnapshot(
                    x = it.x + 0.5f,
                    y = it.y + 0.5f,
                    kind = prize?.kind,
                    prizeIsKey = prize is Prize.KEY,
                    mobRoom = mobRoom,
                )
            }
    }

    private fun prizeOf(
        session: GameSession,
        room: ru.course.roguelike.game.domain.level.Room,
    ): Prize? {
        val hasKey = session.keyPickups.any { !it.collected && room.containsWorld(it.x, it.y) }
        if (hasKey) return Prize.KEY
        val weapon = session.itemPickups
            .filter { !it.collected && InventoryDefinitions.isManualPickup(it.kind) && room.containsWorld(it.x, it.y) }
            .map { it.kind }
            .minByOrNull { weaponRank(it) }
            ?: return null
        return Prize.Weapon(weapon)
    }

    private fun weaponRank(kind: ItemKind): Int = when (kind) {
        ItemKind.WEAPON_SHOTGUN -> 0
        ItemKind.WEAPON_PISTOL -> 1
        else -> 2
    }

    private sealed class Prize(val kind: ItemKind?) {
        data object KEY : Prize(null)
        class Weapon(kind: ItemKind) : Prize(kind)
    }
}
