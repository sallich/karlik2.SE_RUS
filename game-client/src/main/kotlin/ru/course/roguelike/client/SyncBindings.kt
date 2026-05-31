package ru.course.roguelike.client

import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.model.PlayerPose

data class SyncBindings(
    val poseAccessor: () -> PlayerPose?,
    val poseMutator: (PlayerPose?) -> Unit,
    val authoritativeMutator: (PlayerPose?) -> Unit,
    val vitalsMutator: (Int, Int) -> Unit = { _, _ -> },
    val combatMutator: (List<MobSnapshot>, List<ProjectileSnapshot>) -> Unit = { _, _ -> },
)
