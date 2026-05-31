package dev.slne.surf.survival.events.werewolf.domain.roleActions

import dev.slne.surf.survival.events.werewolf.util.NightAction
import dev.slne.surf.survival.events.werewolf.util.WerewolfPlayer
import java.util.*

object SerialKillerActions {
    fun isValid(
        action: NightAction.SerialKillerKill,
        players: Map<UUID, WerewolfPlayer>,
    ): Boolean = players[action.target]?.isAlive == true &&
            action.actor != action.target

    fun resolveTarget(actions: List<NightAction>) = actions
        .filterIsInstance<NightAction.SerialKillerKill>()
        .lastOrNull()
        ?.target
}
