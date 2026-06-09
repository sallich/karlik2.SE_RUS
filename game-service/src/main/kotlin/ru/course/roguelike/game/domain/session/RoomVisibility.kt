package ru.course.roguelike.game.domain.session

import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.ItemKind

/**
 * Видимость призов комнаты (issue #24): призы (предметы по E — оружие и ключи) скрыты,
 * пока комната не зачищена. Здесь же собираются метки призов для дверей.
 */
object RoomVisibility {
    /** Индекс комнаты, содержащей мировую точку, либо null (коридор/вне комнат). */
    fun roomIndexAt(session: GameSession, x: Float, y: Float): Int? {
        val index = session.rooms.indexOfFirst { it.containsWorld(x, y) }
        return if (index >= 0) index else null
    }

    /** Зачищена ли комната с данным индексом (нет состояния — считаем зачищенной). */
    fun isRoomCleared(session: GameSession, roomIndex: Int?): Boolean {
        roomIndex ?: return true
        return session.roomEngagements.getOrNull(roomIndex)?.cleared ?: true
    }

    /** Скрыт ли приз в точке: предмет-приз внутри ещё не зачищенной комнаты. */
    fun isPrizeHidden(session: GameSession, x: Float, y: Float): Boolean =
        !isRoomCleared(session, roomIndexAt(session, x, y))

    /** Виден ли предмет на локации: авто-подбор виден всегда, приз по E — только в зачищенной комнате. */
    fun isItemVisible(session: GameSession, item: ItemPickup): Boolean =
        !InventoryDefinitions.isManualPickup(item.kind) || !isPrizeHidden(session, item.x, item.y)

    /** Виден/доступен ли ключ (по E) — только в зачищенной комнате. */
    fun isKeyVisible(session: GameSession, key: KeyPickup): Boolean =
        !isPrizeHidden(session, key.x, key.y)

    /**
     * Метки призов для дверей всех незачищенных комнат: по одной метке на каждый
     * дверной проём с видом приоритетного приза комнаты (ключ > дробовик > пистолет).
     */
    fun doorMarkers(session: GameSession): List<DoorMarkerSnapshot> =
        session.roomEngagements.flatMap { markersFor(session, it) }

    private fun markersFor(session: GameSession, state: RoomEngagementState): List<DoorMarkerSnapshot> {
        if (state.cleared || state.doorways.isEmpty()) return emptyList()
        val room = session.rooms.getOrNull(state.roomIndex) ?: return emptyList()
        val prize = prizeOf(session, room) ?: return emptyList()
        return state.doorways.map { DoorMarkerSnapshot(x = it.x + 0.5f, y = it.y + 0.5f, kind = prize.kind) }
    }

    /** Приоритетный спрятанный приз комнаты для метки на двери. */
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

    /** Приз двери: [kind] == null означает ключ. */
    private sealed class Prize(val kind: ItemKind?) {
        data object KEY : Prize(null)
        class Weapon(kind: ItemKind) : Prize(kind)
    }
}
