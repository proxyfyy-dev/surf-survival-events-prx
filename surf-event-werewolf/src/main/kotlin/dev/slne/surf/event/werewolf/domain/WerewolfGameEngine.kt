package dev.slne.surf.event.werewolf.domain

import dev.slne.surf.event.werewolf.service.WerewolfService
import dev.slne.surf.event.werewolf.util.GameOutcome
import dev.slne.surf.event.werewolf.util.GameRoundState
import dev.slne.surf.event.werewolf.util.GameState
import dev.slne.surf.event.werewolf.util.PhaseAdvanceResult
import dev.slne.surf.event.werewolf.util.WerwolfRoles
import java.util.UUID
import kotlin.collections.iterator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class WerewolfGameEngine(
    private val service: WerewolfService
) {
    private var roundState = GameRoundState.initial()

    val currentPhase: GameState
        get() = roundState.phase

    val phaseRemainingSeconds: Duration
        get() = roundState.phaseRemainingSeconds

    fun startGameEngine(): PhaseAdvanceResult {
        roundState = GameRoundState(phase = GameState.DAY,
            dayNumber = 1,
            nightActions = mutableListOf(),
            votes = mutableMapOf(),
            phaseRemainingSeconds = GameState.DAY.time,
        )

        return PhaseAdvanceResult(nextPhase = roundState.phase)
    }

    fun tick(): PhaseAdvanceResult? {
        if (roundState.phaseRemainingSeconds > 1.seconds) {
            roundState = roundState.copy(
                phaseRemainingSeconds = roundState.phaseRemainingSeconds - 1.seconds,
            )
            return null
        }

        roundState = roundState.copy(phaseRemainingSeconds = 0.seconds)
        return advancePhase()
    }

    fun advancePhase(): PhaseAdvanceResult {
        return when (roundState.phase) {
            GameState.MAYOR_VOTE -> {
                val electedMayor = resolveMayorVote()
                roundState = roundState.copy(mayorPlayer = electedMayor)

                val winner = checkWinCondition()
                if (winner != null) {
                    PhaseAdvanceResult(
                        nextPhase = roundState.phase,
                        winner = winner,
                        electedMayor = electedMayor
                    )
                } else {
                    beginDayPhase(increaseDayNumber = false)
                    PhaseAdvanceResult(
                        nextPhase = roundState.phase,
                        electedMayor = electedMayor
                    )
                }
            }

            GameState.DAY -> {
                beginVotePhase()
                PhaseAdvanceResult(
                    nextPhase = roundState.phase
                )
            }

            GameState.VOTE -> {
                val votedOutPlayer = resolveVote()

                val winner = checkWinCondition()
                if (winner != null) {
                    PhaseAdvanceResult(
                        nextPhase = roundState.phase,
                        winner = winner,
                        eliminatedPlayers = listOfNotNull(votedOutPlayer)
                    )
                } else {
                    beginNightPhase()
                    PhaseAdvanceResult(
                        nextPhase = roundState.phase,
                        eliminatedPlayers = listOfNotNull(votedOutPlayer)
                    )
                }
            }

            GameState.NIGHT -> {
                val killedAtNight = resolveNight()

                val winner = checkWinCondition()
                if (winner != null) {
                    PhaseAdvanceResult(
                        nextPhase = roundState.phase,
                        winner = winner,
                        eliminatedPlayers = listOfNotNull(killedAtNight)
                    )
                } else {
                    beginDayPhase(increaseDayNumber = true)
                    PhaseAdvanceResult(
                        nextPhase = roundState.phase,
                        eliminatedPlayers = listOfNotNull(killedAtNight)
                    )
                }
            }
        }
    }

    fun beginMayorVoting() {
        roundState = roundState.copy(
            phase = GameState.MAYOR_VOTE,
            phaseRemainingSeconds = GameState.MAYOR_VOTE.time,
            mayorVotes = mutableMapOf()
        )

        service.setGameState(GameState.MAYOR_VOTE)
    }

    fun beginVotePhase() {
        roundState = roundState.copy(
            phase = GameState.VOTE,
            phaseRemainingSeconds = GameState.VOTE.time,
            votes = mutableMapOf()
        )

        service.setGameState(GameState.VOTE)
    }

    fun beginNightPhase() {
        roundState = roundState.copy(
            phase = GameState.NIGHT,
            phaseRemainingSeconds = GameState.NIGHT.time,
            werewolfTarget = null,
            nightActions = mutableListOf()
        )

        service.setGameState(GameState.NIGHT)
    }

    fun beginDayPhase(increaseDayNumber: Boolean = true) {
        roundState = roundState.copy(
            phase = GameState.DAY,
            phaseRemainingSeconds = GameState.DAY.time,
            dayNumber = if (increaseDayNumber) roundState.dayNumber + 1 else roundState.dayNumber,
            votes = mutableMapOf(),
            werewolfTarget = null,
            nightActions = mutableListOf()
        )

        service.setGameState(GameState.DAY)
    }

    fun submitMayorVote(voter: UUID, target: UUID): Boolean {
        if (roundState.phase != GameState.MAYOR_VOTE) return false
        if (service.players[voter]?.isAlive != true) return false
        if (service.players[target]?.isAlive != true) return false

        roundState.mayorVotes[voter] = target
        return true
    }

    fun resolveMayorVote(): UUID? {
        if (roundState.phase != GameState.MAYOR_VOTE) return null
        if (roundState.mayorVotes.isEmpty()) return null

        val counts = mutableMapOf<UUID?, Int>()

        for ((voter, target) in roundState.mayorVotes) {
            val voterPlayer = service.players[voter] ?: continue
            if (!voterPlayer.isAlive) continue

            counts[target] = (counts[target] ?: 0) + 1
        }

        val chosenOne = counts.maxByOrNull { it.value }?.key ?: return null
        return chosenOne
    }

    fun submitVote(voter: UUID, target: UUID): Boolean {
        if (roundState.phase != GameState.VOTE) return false
        if (service.players[voter]?.isAlive != true) return false
        if (service.players[target]?.isAlive != true) return false

        roundState.votes[voter] = target
        return true
    }

    fun resolveVote(): UUID? {
        if (roundState.phase != GameState.VOTE) return null
        if (roundState.votes.isEmpty()) return null

        val counts = mutableMapOf<UUID, Int>()

        for ((voter, target) in roundState.votes) {
            val voterPlayer = service.players[voter] ?: continue
            if (!voterPlayer.isAlive) continue

            val weight = if (voterPlayer.role == WerwolfRoles.MAYOR) 2 else 1
            counts[target] = (counts[target] ?: 0) + weight
        }

        val killed = counts.maxByOrNull { it.value }?.key ?: return null
        service.players[killed]?.isAlive = false
        return killed
    }

    fun submitWerewolfTarget(target: UUID): Boolean {
        if (roundState.phase != GameState.NIGHT) return false
        if (service.players[target]?.isAlive != true) return false

        roundState = roundState.copy(werewolfTarget = target)
        return true
    }

    fun resolveNight(): UUID? {
        if (roundState.phase != GameState.NIGHT) return null

        val target = roundState.werewolfTarget ?: return null
        service.players[target]?.isAlive = false
        return target
    }

    fun checkWinCondition(): GameOutcome? {
        val alivePlayers = service.players.values.filter { it.isAlive }
        val aliveWerewolves = alivePlayers.count { it.role == WerwolfRoles.WERWOLF }
        val aliveVillagers = alivePlayers.count { it.role != WerwolfRoles.WERWOLF }

        if (aliveWerewolves == 0) return GameOutcome.VillagersWin
        if (aliveWerewolves >= aliveVillagers) return GameOutcome.WerewolvesWin

        return null
    }
}