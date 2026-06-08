package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.inventory.InventoryWeapons
import ru.course.roguelike.game.domain.progression.ProgressionSystem
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.MobLootDropper
import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.InventoryItemType
object CombatSystem {
    fun tick(session: GameSession, deltaMs: Int, playerAttacking: Boolean): List<GameEvent> {
        if (session.playerHp <= 0) return emptyList()

        val events = mutableListOf<GameEvent>()
        val deltaSec = deltaMs / MILLIS_PER_SECOND
        val map = session.activeMap

        tickCooldowns(session, deltaMs)
        if (playerAttacking) {
            firePlayerProjectile(session)?.let { events.add(it) }
            events.addAll(InventoryWeapons.tryAutoReload(session))
        }
        CombatMobTick.tickMobs(session, map, deltaSec, events)
        session.mobs.filter { it.alive }.forEach { CombatMobMovement.clampMobOutOfWalls(map, it) }
        CombatMobMovement.separateMobs(session.mobs, map)
        tickProjectiles(session, map, deltaSec, events)
        removeDeadMobs(session, events)
        events.addAll(ProgressionSystem.checkLocationCompletion(session))
        return events
    }

    private fun tickCooldowns(session: GameSession, deltaMs: Int) {
        session.playerAttackCooldownMs = (session.playerAttackCooldownMs - deltaMs).coerceAtLeast(0)
        session.mobs.forEach { mob ->
            mob.attackCooldownMs = (mob.attackCooldownMs - deltaMs).coerceAtLeast(0)
        }
    }

    private fun tickProjectiles(
        session: GameSession,
        map: TileMap,
        deltaSec: Float,
        events: MutableList<GameEvent>,
    ) {
        val playerZ = EntityCollision.playerHitCenterZ(session.playerPose)
        session.projectiles.removeAll { projectile ->
            projectile.x += projectile.velocityX * deltaSec
            projectile.y += projectile.velocityY * deltaSec
            projectile.z += projectile.velocityZ * deltaSec
            val circle = EntityCollision.Circle(
                projectile.x,
                projectile.y,
                CombatConstants.PROJECTILE_RADIUS,
            )
            when {
                EntityCollision.overlapsWall(map, circle, worldZ = projectile.z) -> true
                projectile.fromPlayer -> handlePlayerProjectileHit(session, projectile, events)
                EntityCollision.spheresOverlap3D(
                    projectile.x,
                    projectile.y,
                    projectile.z,
                    CombatConstants.PROJECTILE_RADIUS,
                    session.playerPose.x,
                    session.playerPose.y,
                    playerZ,
                    CombatConstants.MOB_HIT_HALF_HEIGHT,
                ) -> {
                    damagePlayer(session, projectile.damage, events)
                    true
                }
                else -> false
            }
        }
    }

    private fun handlePlayerProjectileHit(
        session: GameSession,
        projectile: ProjectileEntity,
        events: MutableList<GameEvent>,
    ): Boolean {
        val hitMob = session.mobs.firstOrNull { mob ->
            mob.alive &&
                EntityCollision.spheresOverlap3D(
                    projectile.x,
                    projectile.y,
                    projectile.z,
                    CombatConstants.PROJECTILE_RADIUS,
                    mob.x,
                    mob.y,
                    mob.hitCenterZ(),
                    CombatConstants.MOB_HIT_HALF_HEIGHT,
                )
        } ?: return false

        val before = hitMob.hp
        hitMob.hp = (before - session.playerAttackDamage).coerceAtLeast(0)
        val dealt = before - hitMob.hp
        events.add(
            if (hitMob.hp <= 0) {
                onMobKilled(session, hitMob, events)
                GameEvent.MobKilled(hitMob.id)
            } else {
                GameEvent.MobDamaged(hitMob.id, dealt, hitMob.hp)
            },
        )
        return true
    }

    private fun firePlayerProjectile(session: GameSession): GameEvent? {
        val weaponType = InventoryWeapons.equippedWeaponType(session) ?: InventoryItemType.PISTOL
        val ammoCost = InventoryDefinitions.ammoCost(weaponType)
        if (session.playerAttackCooldownMs > 0) return null
        if (session.playerAmmo < ammoCost) return null

        val totalDamage = session.playerAttackDamage
        val pelletCount = InventoryDefinitions.pelletCount(weaponType)
        val spread = InventoryDefinitions.spreadRadians(weaponType)
        val pelletDmg = InventoryDefinitions.pelletDamage(weaponType, totalDamage)
        repeat(pelletCount) { index ->
            val offset = if (pelletCount <= 1) {
                0f
            } else {
                spread * ((index.toFloat() / (pelletCount - 1)) - 0.5f) * 2f
            }
            session.projectiles.add(
                ProjectileEntity.fromPlayer(
                    session.playerPose,
                    session.allocateEntityId(),
                    pelletDmg,
                    yawOffset = offset,
                ),
            )
        }
        session.playerAttackCooldownMs = InventoryDefinitions.attackCooldownMs(weaponType)
        session.playerAmmo -= ammoCost
        session.equippedWeaponItemId?.let { session.weaponLoadedAmmo[it] = session.playerAmmo }
        return GameEvent.AmmoChanged(-ammoCost, session.playerAmmo)
    }

    private fun damagePlayer(session: GameSession, amount: Int, events: MutableList<GameEvent>) {
        val before = session.playerHp
        session.playerHp = (before - amount).coerceAtLeast(0)
        events.add(GameEvent.PlayerDamaged(before - session.playerHp, session.playerHp))
    }

    private fun removeDeadMobs(session: GameSession, events: MutableList<GameEvent>) {
        val dead = session.mobs.filter { !it.alive }
        if (dead.isEmpty()) return
        dead.forEach { mob ->
            if (events.none { it is GameEvent.MobKilled && it.mobId == mob.id }) {
                onMobKilled(session, mob, events)
                events.add(GameEvent.MobKilled(mob.id))
            }
        }
        session.mobs.removeAll { !it.alive }
    }

    private fun onMobKilled(session: GameSession, mob: MobEntity, events: MutableList<GameEvent>) {
        events.addAll(ProgressionSystem.awardMobKill(session, mob.kind))
        MobLootDropper.dropFrom(session, mob)?.let { events.add(it) }
    }

    private const val MILLIS_PER_SECOND = 1000f
}
