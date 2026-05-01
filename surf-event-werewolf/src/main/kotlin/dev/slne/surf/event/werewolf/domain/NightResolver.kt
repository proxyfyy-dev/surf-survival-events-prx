package dev.slne.surf.event.werewolf.domain

import dev.slne.surf.event.werewolf.domain.roleActions.AmorActions
import dev.slne.surf.event.werewolf.domain.roleActions.DoctorActions
import dev.slne.surf.event.werewolf.domain.roleActions.SeerActions
import dev.slne.surf.event.werewolf.domain.roleActions.WitchActions
import dev.slne.surf.event.werewolf.util.NightAction
import dev.slne.surf.event.werewolf.util.NightResolutionResult
import dev.slne.surf.event.werewolf.util.WerewolfPlayer
import dev.slne.surf.event.werewolf.util.WerwolfRoles
import java.util.*

internal class NightResolver(
    private val players: Map<UUID, WerewolfPlayer>,
    private val dayNumber: Int,
    private val werewolfTarget: UUID?,
) {

    fun isValid(action: NightAction, actorRole: WerwolfRoles): Boolean {
        return when (action) {
            is NightAction.WerewolfKill -> actorRole == WerwolfRoles.WERWOLF &&
                    isValidLivingTarget(action.target) &&
                    action.actor != action.target

            is NightAction.SeerInspect -> actorRole == WerwolfRoles.SEER &&
                    SeerActions.isValid(action, players)

            is NightAction.DoctorProtect -> actorRole == WerwolfRoles.DOCTOR &&
                    DoctorActions.isValid(action, players, werewolfTarget)

            is NightAction.WitchHeal,
            is NightAction.WitchPoison -> actorRole == WerwolfRoles.WITCH &&
                    WitchActions.isValid(action, players, werewolfTarget, dayNumber)

            is NightAction.AmorLink -> actorRole == WerwolfRoles.AMOR &&
                    AmorActions.isValid(action, players, dayNumber)

            is NightAction.PriestWater -> actorRole == WerwolfRoles.PRIEST &&
                    isValidLivingTarget(action.target) &&
                    action.actor != action.target

            is NightAction.SerialKillerKill -> actorRole == WerwolfRoles.SERIAL_KILLER &&
                    isValidLivingTarget(action.target) &&
                    action.actor != action.target
        }
    }

    fun resolve(actions: List<NightAction>): NightResolutionResult {
        val lovers = AmorActions.resolve(actions)
        val resolvedWerewolfTarget = resolveWerewolfTarget(actions)
        val doctorProtectedPlayer = DoctorActions.resolveTarget(actions)
        val witchHealTarget = WitchActions.resolveHealTarget(actions)
        val witchPoisonTarget = WitchActions.resolvePoisonTarget(actions)
        val eliminatedPlayers = linkedSetOf<UUID>()

        if (resolvedWerewolfTarget != null &&
            resolvedWerewolfTarget != doctorProtectedPlayer &&
            resolvedWerewolfTarget != witchHealTarget
        ) {
            eliminatedPlayers.add(resolvedWerewolfTarget)
        }

        if (witchPoisonTarget != null) {
            eliminatedPlayers.add(witchPoisonTarget)
        }

        return NightResolutionResult(
            eliminatedPlayers = eliminatedPlayers.toList(),
            werewolfTarget = resolvedWerewolfTarget,
            protectedPlayer = witchHealTarget ?: doctorProtectedPlayer,
            lovers = lovers
        )
    }

    private fun isValidLivingTarget(target: UUID): Boolean {
        return players[target]?.isAlive == true
    }

    fun resolveWerewolfTarget(actions: List<NightAction>): UUID? {
        val werewolfActions = actions.filterIsInstance<NightAction.WerewolfKill>()
        if (werewolfActions.isEmpty()) return null

        val counts = werewolfActions.groupingBy { it.target }.eachCount()
        val winner = counts.maxByOrNull { it.value } ?: return null

        return if (winner.value > werewolfActions.size / 2) {
            winner.key
        } else {
            werewolfActions.random().target
        }
    }
}
