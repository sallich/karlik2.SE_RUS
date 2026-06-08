package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.ai.MobDecisionContext
import ru.course.roguelike.game.domain.ai.MobIntent
import ru.course.roguelike.game.domain.ai.distanceTo
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap

internal object CombatMobTick {
    private data class MobTickContext(
        val session: GameSession,
        val map: TileMap,
        val playerX: Float,
        val playerY: Float,
        val playerZ: Float,
        val deltaSec: Float,
        val events: MutableList<GameEvent>,
    )

    fun tickMobs(
        session: GameSession,
        map: TileMap,
        deltaSec: Float,
        events: MutableList<GameEvent>,
    ) {
        val context = MobTickContext(
            session = session,
            map = map,
            playerX = session.playerPose.x,
            playerY = session.playerPose.y,
            playerZ = EntityCollision.playerHitCenterZ(session.playerPose),
            deltaSec = deltaSec,
            events = events,
        )

        for (mob in session.mobs) {
            tickSingleMob(context, mob)
        }
    }

    private fun tickSingleMob(context: MobTickContext, mob: MobEntity) {
        if (!mob.alive) return
        if (tickReinforcement(mob, context.map, context.playerX, context.playerY, context.deltaSec)) return
        if (!canEngageMob(mob, context.playerX, context.playerY)) return
        executeMobIntent(context, mob)
    }

    private fun tickReinforcement(
        mob: MobEntity,
        map: TileMap,
        playerX: Float,
        playerY: Float,
        deltaSec: Float,
    ): Boolean {
        val reinforceTarget = mob.reinforceTarget ?: return false
        val playerInTarget = reinforceTarget.containsWorld(playerX, playerY)
        val mobInTarget = reinforceTarget.containsWorld(mob.x, mob.y)
        if (!playerInTarget && !mobInTarget) {
            CombatMobMovement.moveToward(
                map,
                mob,
                reinforceTarget.center.x + 0.5f,
                reinforceTarget.center.y + 0.5f,
                mob.moveSpeed,
                deltaSec,
            )
            return true
        }
        return !playerInTarget
    }

    private fun canEngageMob(mob: MobEntity, playerX: Float, playerY: Float): Boolean {
        val reinforceTarget = mob.reinforceTarget
        return mob.aggroRoom.containsWorld(playerX, playerY) ||
            (reinforceTarget != null && reinforceTarget.containsWorld(playerX, playerY))
    }

    private fun executeMobIntent(context: MobTickContext, mob: MobEntity) {
        val distance = mob.distanceTo(context.playerX, context.playerY)
        val decisionContext = MobDecisionContext(
            mob = mob,
            playerX = context.playerX,
            playerY = context.playerY,
            distanceToPlayer = distance,
            playerHp = context.session.playerHp,
        )
        when (mob.behavior.decide(decisionContext)) {
            MobIntent.Idle -> Unit
            MobIntent.ChasePlayer -> CombatMobMovement.moveToward(
                context.map,
                mob,
                context.playerX,
                context.playerY,
                mob.moveSpeed,
                context.deltaSec,
            )
            MobIntent.KitePlayer -> CombatMobMovement.moveToward(
                context.map,
                mob,
                context.playerX,
                context.playerY,
                -mob.moveSpeed,
                context.deltaSec,
            )
            MobIntent.StrafePlayer -> CombatMobMovement.strafeAround(
                context.map,
                mob,
                context.playerX,
                context.playerY,
                mob.moveSpeed,
                context.deltaSec,
            )
            MobIntent.AttackPlayer -> attackPlayerIfInRange(context.session, mob, distance, context.events)
            MobIntent.ShootPlayer -> shootPlayerIfReady(
                context.session,
                mob,
                context.playerX,
                context.playerY,
                context.playerZ,
            )
        }
    }

    private fun attackPlayerIfInRange(
        session: GameSession,
        mob: MobEntity,
        distance: Float,
        events: MutableList<GameEvent>,
    ) {
        if (distance <= mob.attackRange && mob.attackCooldownMs <= 0) {
            val before = session.playerHp
            session.playerHp = (before - mob.attackDamage).coerceAtLeast(0)
            events.add(GameEvent.PlayerDamaged(before - session.playerHp, session.playerHp))
            mob.attackCooldownMs = mob.attackCooldownTotalMs
        }
    }

    private fun shootPlayerIfReady(
        session: GameSession,
        mob: MobEntity,
        playerX: Float,
        playerY: Float,
        playerZ: Float,
    ) {
        if (mob.attackCooldownMs > 0) return
        session.projectiles.add(
            ProjectileEntity.fromMob(
                mob,
                playerX,
                playerY,
                playerZ,
                session.allocateEntityId(),
            ),
        )
        mob.attackCooldownMs = mob.attackCooldownTotalMs
    }
}
