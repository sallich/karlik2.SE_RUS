package ru.course.roguelike.client

import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.HotbarSnapshot
import ru.course.roguelike.shared.dto.InventorySnapshot
import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.dto.RoomClearTimerSnapshot
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose

data class SyncBindings(
    val poseAccessor: () -> PlayerPose?,
    val poseMutator: (PlayerPose?) -> Unit,
    val authoritativeMutator: (PlayerPose?) -> Unit,
    val vitalsMutator: (
        hp: Int,
        maxHp: Int,
        level: Int,
        experience: Int,
        experienceToNextLevel: Int,
        ammo: Int,
        maxAmmo: Int,
        equippedWeaponName: String?,
        equippedWeaponType: String?,
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    val inventoryMutator: (InventorySnapshot?, HotbarSnapshot?) -> Unit = { _, _ -> },
    val combatMutator: (List<MobSnapshot>, List<ProjectileSnapshot>) -> Unit = { _, _ -> },
    val progressMutator: (String, Int, Int, List<KeySnapshot>, List<ItemSnapshot>, GridPos?) -> Unit =
        { _, _, _, _, _, _ -> },
    val agentMutator: (PlayerPose?) -> Unit = {},
    val verticalMutator: (verticalVelocity: Float) -> Unit = {},
    val elevatorPhaseMutator: (elevatorPhase: String) -> Unit = {},
    val roomTimerMutator: (RoomClearTimerSnapshot?, Long) -> Unit = { _, _ -> },
    /** Карта, метки дверей и ярус — меняются при запирании комнат (issue #24). */
    val worldMutator: (GameSnapshot) -> Unit = {},
)
