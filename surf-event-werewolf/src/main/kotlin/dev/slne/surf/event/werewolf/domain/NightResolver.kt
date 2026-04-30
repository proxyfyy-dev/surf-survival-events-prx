package dev.slne.surf.event.werewolf.domain

import dev.slne.surf.event.werewolf.domain.roleActions.AmorActions
import dev.slne.surf.event.werewolf.util.NightAction
import dev.slne.surf.event.werewolf.util.NightResolutionResult
import dev.slne.surf.event.werewolf.util.WerewolfPlayer
import dev.slne.surf.event.werewolf.util.WerwolfRoles
import java.util.*

internal class NightResolver(
    private val players: Map<UUID, WerewolfPlayer>,
    private val dayNumber: Int,
) {

    fun isValid(action: NightAction, actorRole: WerwolfRoles): Boolean {
        return when (action) {
            is NightAction.WerewolfKill -> actorRole == WerwolfRoles.WERWOLF &&
                    isValidLivingTarget(action.target) &&
                    action.actor != action.target

            is NightAction.SeerInspect -> actorRole == WerwolfRoles.SEER &&
                    isValidLivingTarget(action.target)

            is NightAction.DoctorProtect -> actorRole == WerwolfRoles.DOCTOR &&
                    isValidLivingTarget(action.target)

            is NightAction.WitchHeal -> actorRole == WerwolfRoles.WITCH &&
                    isValidLivingTarget(action.target)

            is NightAction.WitchPoison -> actorRole == WerwolfRoles.WITCH &&
                    isValidLivingTarget(action.target) &&
                    action.actor != action.target

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
        val werewolfTarget = resolveWerewolfTarget(
            actions.filterIsInstance<NightAction.WerewolfKill>()
        )
        val protectedPlayer = actions
            .filterIsInstance<NightAction.DoctorProtect>()
            .lastOrNull()
            ?.target
        val eliminatedPlayers = linkedSetOf<UUID>()

        if (werewolfTarget != null && werewolfTarget != protectedPlayer) {
            eliminatedPlayers.add(werewolfTarget)
        }

        return NightResolutionResult(
            eliminatedPlayers = eliminatedPlayers.toList(),
            werewolfTarget = werewolfTarget,
            protectedPlayer = protectedPlayer,
            lovers = lovers
        )
    }

    private fun isValidLivingTarget(target: UUID): Boolean {
        return players[target]?.isAlive == true
    }

    private fun resolveWerewolfTarget(actions: List<NightAction.WerewolfKill>): UUID? {
        if (actions.isEmpty()) return null

        val counts = actions.groupingBy { it.target }.eachCount()
        val winner = counts.maxByOrNull { it.value } ?: return null

        return if (winner.value > actions.size / 2) {
            winner.key
        } else {
            actions.random().target
        }
    }
}
