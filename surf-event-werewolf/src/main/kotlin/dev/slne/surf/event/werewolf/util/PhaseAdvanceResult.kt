package dev.slne.surf.event.werewolf.util

import java.util.UUID

data class PhaseAdvanceResult(
    val nextPhase: GameState,
    val winner: GameOutcome? = null,
    val electedMayor: UUID? = null,
    val eliminatedPlayers: List<UUID> = emptyList()
)