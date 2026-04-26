package dev.slne.surf.event.werewolf.domain

import dev.slne.surf.api.core.messages.Colors
import dev.slne.surf.api.core.messages.adventure.buildText
import dev.slne.surf.event.werewolf.messaging.WerewolfMessenger
import dev.slne.surf.event.werewolf.service.WerewolfService
import dev.slne.surf.event.werewolf.util.*
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class WerewolfGameEngine(
    private val service: WerewolfService
) {
    private var roundState = GameRoundState.initial()

    private val messenger = WerewolfMessenger(service)

    private var werewolfTargetList = mutableListOf<UUID>()

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

            println(phaseRemainingSeconds)
            println(roundState.phase)

            return null
        }

        roundState = roundState.copy(phaseRemainingSeconds = 0.seconds)
        return advancePhase()
    }

    fun advancePhase(): PhaseAdvanceResult {
        return when (roundState.phase) {
            GameState.MAYOR_VOTE -> {
                val standings = calculateMayorVoteStandings()
                val electedMayor = resolveMayorVote()
                roundState = roundState.copy(mayorPlayer = electedMayor)

                messenger.announceVotings(roundState.phase, standings, electedMayor)

                beginNightPhase()

                PhaseAdvanceResult(
                    nextPhase = roundState.phase,
                    electedMayor = electedMayor,
                    voteStandings = standings
                )
            }

            GameState.DAY -> {
                if (roundState.dayNumber == 1 && roundState.mayorPlayer == null) {
                    beginMayorVoting()
                } else {
                    beginVotePhase()
                }

                PhaseAdvanceResult(
                    nextPhase = roundState.phase,
                )
            }

            GameState.VOTE -> {
                val standings = calculateVoteStandings()
                val votedOutPlayer = resolveVote()

                messenger.announceVotings(
                    state = roundState.phase,
                    standings = standings,
                    eliminatedPlayers = listOfNotNull(votedOutPlayer)
                )

                val winner = checkWinCondition()
                if (winner != null) {
                    PhaseAdvanceResult(
                        nextPhase = roundState.phase,
                        winner = winner,
                        eliminatedPlayers = listOfNotNull(votedOutPlayer),
                        voteStandings = standings
                    )
                } else {
                    beginNightPhase()
                    PhaseAdvanceResult(
                        nextPhase = roundState.phase,
                        eliminatedPlayers = listOfNotNull(votedOutPlayer),
                        voteStandings = standings
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

        service.announceToAlive {
            appendInfoPrefix()
            info("Die Bürgermeisterwahl hat begonnen.")
            appendNewInfoPrefixedLine()
            info("Die Stimme des Bürgermeisters zahlt doppelt so viel.")
            appendNewInfoPrefixedLine()
            append(createClickable())
        }

        service.setGameState(GameState.MAYOR_VOTE)
    }

    fun beginVotePhase() {
        roundState = roundState.copy(
            phase = GameState.VOTE,
            phaseRemainingSeconds = GameState.VOTE.time,
            votes = mutableMapOf()
        )

        service.announceToAlive {
            appendInfoPrefix()
            info("Das Dorf hat eine Abstimmung gestartet!")
            appendSpace()
            info("Wähle jemanden, der ein Werewolf sein konnte, oder enthalte dich!")
        }

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
        return calculateMayorVoteStandings().firstOrNull()?.target
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
        val killed = calculateVoteStandings().firstOrNull()?.target ?: return null
        service.executePlayer(killed)
        return killed
    }

    private fun calculateMayorVoteStandings(): List<VoteStanding> {
        if (roundState.phase != GameState.MAYOR_VOTE) return emptyList()
        if (roundState.mayorVotes.isEmpty()) return emptyList()

        val counts = mutableMapOf<UUID, Int>()

        for ((voter, target) in roundState.mayorVotes) {
            val voterPlayer = service.players[voter] ?: continue
            if (!voterPlayer.isAlive) continue
            if (service.players[target]?.isAlive != true) continue

            counts[target] = (counts[target] ?: 0) + 1
        }

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<UUID, Int>> { it.value }.thenBy { service.players[it.key]?.name ?: "~" })
            .map { VoteStanding(it.key, it.value) }
    }

    private fun calculateVoteStandings(): List<VoteStanding> {
        if (roundState.phase != GameState.VOTE) return emptyList()
        if (roundState.votes.isEmpty()) return emptyList()

        val counts = mutableMapOf<UUID, Int>()

        for ((voter, target) in roundState.votes) {
            val voterPlayer = service.players[voter] ?: continue
            if (!voterPlayer.isAlive) continue
            if (service.players[target]?.isAlive != true) continue

            val weight = if (voterPlayer.role == WerwolfRoles.MAYOR) 2 else 1
            counts[target] = (counts[target] ?: 0) + weight
        }

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<UUID, Int>> { it.value }
                .thenBy { service.players[it.key]?.name ?: "~" })
            .map { VoteStanding(it.key, it.value) }
    }

    fun submitWerewolfTarget(target: UUID): Boolean {
        if (roundState.phase != GameState.NIGHT) return false
        if (service.players[target]?.isAlive != true) return false

        werewolfTargetList.add(target)
        return true
    }

    fun resolveNight(): UUID? {
        if (roundState.phase != GameState.NIGHT) return null
        if (werewolfTargetList.isEmpty()) return null

        val counts = werewolfTargetList.groupingBy { it }.eachCount()
        val winner = counts.maxByOrNull { it.value } ?: return null

        val target = if (winner.value > werewolfTargetList.size / 2) {
            winner.key
        } else {
            werewolfTargetList.random()
        }

        service.executePlayer(target)
        werewolfTargetList.clear()

        roundState = roundState.copy(werewolfTarget = target)

        return target
    }

    fun getWerewolfTargetFromLineOfSight(player: Player): UUID? {
        if (roundState.phase != GameState.NIGHT) return null
        if (service.getPlayerRole(player.uniqueId) != WerwolfRoles.WERWOLF) return null
        if (service.players[player.uniqueId]?.isAlive != true) return null

        val targetPlayer = player.getTargetEntity(50, true) as? Player ?: return null
        val targetId = targetPlayer.uniqueId

        if (service.players[targetId]?.isAlive != true) return null

        return targetId
    }

    fun checkWinCondition(): GameOutcome? {
        val alivePlayers = service.players.values.filter { it.isAlive }
        val aliveWerewolves = alivePlayers.count { it.role == WerwolfRoles.WERWOLF }
        val aliveVillagers = alivePlayers.count { it.role != WerwolfRoles.WERWOLF }

        if (aliveWerewolves == 0) return GameOutcome.VillagersWin
        if (aliveWerewolves >= aliveVillagers) return GameOutcome.WerewolvesWin

        return null
    }

    private fun createClickable() = buildText {
        text("HIER", Colors.VARIABLE_VALUE, TextDecoration.UNDERLINED)
        hoverEvent(HoverEvent.showText(buildText { info("Klicke hier, um den Command in den Chat einzufügen!") }))
        clickEvent(ClickEvent.suggestCommand("/werewolf vote "))
    }
}
