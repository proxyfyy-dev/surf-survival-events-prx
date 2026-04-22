package dev.slne.surf.event.werewolf.util

import java.util.UUID
import kotlin.collections.mutableListOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class GameRoundState(
    val phase: GameState,
    var phaseRemainingSeconds: Duration,
    val dayNumber: Int,
    val nightActions: MutableList<NightAction>,
    val votes: MutableMap<UUID, UUID>,
    val protectedPlayer: UUID? = null,
    val werewolfTarget: UUID? = null,
    val mayorPlayer: UUID? = null,
    val mayorVotes: MutableMap<UUID, UUID> = mutableMapOf(),
) {
    companion object {
        fun initial() = GameRoundState(
            phase = GameState.DAY,
            phaseRemainingSeconds = GameState.DAY.time,
            dayNumber = 1,
            nightActions = mutableListOf(),
            votes = mutableMapOf()
        )
    }
}