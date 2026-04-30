package dev.slne.surf.event.werewolf.domain

import dev.slne.surf.api.core.messages.Colors
import dev.slne.surf.api.core.messages.adventure.buildText
import dev.slne.surf.event.werewolf.domain.roleActions.AmorActions
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
    private fun nightResolver() = NightResolver(service.players, roundState.dayNumber)

    val currentPhase: GameState
        get() = roundState.phase

    val phaseRemainingSeconds: Duration
        get() = roundState.phaseRemainingSeconds

    fun startGameEngine(): PhaseAdvanceResult {
        roundState = GameRoundState(
            phase = GameState.DAY,
            dayNumber = 1,
            nightActions = mutableListOf(),
            votes = mutableMapOf(),
            phaseRemainingSeconds = GameState.DAY.time,
        )

        return PhaseAdvanceResult(nextPhase = roundState.phase)
    }

    fun tick(): PhaseAdvanceResult? {
        if (roundState.phaseRemainingSeconds <= 1.seconds) {
            roundState = roundState.copy(phaseRemainingSeconds = 0.seconds)
            return advancePhase()
        }

        roundState = roundState.copy(
            phaseRemainingSeconds = roundState.phaseRemainingSeconds - 1.seconds,
        )

        println(phaseRemainingSeconds)
        println(roundState.phase)

        return null
    }

    fun advancePhase(): PhaseAdvanceResult {
        return when (roundState.phase) {
            GameState.MAYOR_VOTE -> advanceMayorVotePhase()
            GameState.DAY -> advanceDayPhase()
            GameState.VOTE -> advanceVotePhase()
            GameState.NIGHT -> advanceNightPhase()
        }
    }

    private fun advanceMayorVotePhase(): PhaseAdvanceResult {
        val standings = calculateMayorVoteStandings()
        val electedMayor = resolveMayorVote()
        roundState = roundState.copy(mayorPlayer = electedMayor)

        messenger.announceVotings(roundState.phase, standings, electedMayor)
        beginNightPhase()

        return PhaseAdvanceResult(
            nextPhase = roundState.phase,
            electedMayor = electedMayor,
            voteStandings = standings
        )
    }

    private fun advanceDayPhase(): PhaseAdvanceResult {
        if (isFirstDayWithoutMayor()) {
            beginMayorVoting()
        } else {
            beginVotePhase()
        }

        return PhaseAdvanceResult(nextPhase = roundState.phase)
    }

    private fun advanceVotePhase(): PhaseAdvanceResult {
        val standings = calculateVoteStandings()
        val votedOutPlayer = resolveVote()

        messenger.announceVotings(
            state = roundState.phase,
            standings = standings,
            eliminatedPlayers = listOfNotNull(votedOutPlayer)
        )

        val winner = checkWinCondition()
        if (winner != null) {
            return PhaseAdvanceResult(
                nextPhase = roundState.phase,
                winner = winner,
                eliminatedPlayers = listOfNotNull(votedOutPlayer),
                voteStandings = standings
            )
        }

        beginNightPhase()
        return PhaseAdvanceResult(
            nextPhase = roundState.phase,
            eliminatedPlayers = listOfNotNull(votedOutPlayer),
            voteStandings = standings
        )
    }

    private fun advanceNightPhase(): PhaseAdvanceResult {
        val nightResolution = resolveNight()
        val winner = checkWinCondition()

        if (winner != null) {
            return PhaseAdvanceResult(
                nextPhase = roundState.phase,
                winner = winner,
                eliminatedPlayers = nightResolution.eliminatedPlayers
            )
        }

        beginDayPhase(increaseDayNumber = true)
        return PhaseAdvanceResult(
            nextPhase = roundState.phase,
            eliminatedPlayers = nightResolution.eliminatedPlayers
        )
    }

    private fun isFirstDayWithoutMayor(): Boolean {
        return roundState.dayNumber == 1 && roundState.mayorPlayer == null
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
            protectedPlayer = null,
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
            protectedPlayer = null,
            werewolfTarget = null,
            nightActions = mutableListOf()
        )

        service.setGameState(GameState.DAY)
    }

    fun submitMayorVote(voter: UUID, target: UUID): Boolean {
        return submitVote(
            expectedPhase = GameState.MAYOR_VOTE,
            votes = roundState.mayorVotes,
            voter = voter,
            target = target
        )
    }

    fun resolveMayorVote(): UUID? {
        if (roundState.phase != GameState.MAYOR_VOTE) return null
        return calculateMayorVoteStandings().firstOrNull()?.target
    }

    fun submitVote(voter: UUID, target: UUID): Boolean {
        return submitVote(
            expectedPhase = GameState.VOTE,
            votes = roundState.votes,
            voter = voter,
            target = target
        )
    }

    fun resolveVote(): UUID? {
        if (roundState.phase != GameState.VOTE) return null
        val killed = calculateVoteStandings().firstOrNull()?.target ?: return null
        service.executePlayer(killed)
        return killed
    }

    private fun submitVote(
        expectedPhase: GameState,
        votes: MutableMap<UUID, UUID>,
        voter: UUID,
        target: UUID,
    ): Boolean {
        if (roundState.phase != expectedPhase) return false
        if (service.players[voter]?.isAlive != true) return false
        if (service.players[target]?.isAlive != true) return false

        votes[voter] = target
        return true
    }

    private fun calculateVoteStandings(
        expectedPhase: GameState,
        votes: Map<UUID, UUID>,
        weightSelector: (WerewolfPlayer) -> Int,
    ): List<VoteStanding> {
        if (roundState.phase != expectedPhase) return emptyList()
        if (votes.isEmpty()) return emptyList()

        val counts = mutableMapOf<UUID, Int>()

        for ((voter, target) in votes) {
            val voterPlayer = service.players[voter] ?: continue
            if (!voterPlayer.isAlive) continue
            if (service.players[target]?.isAlive != true) continue

            counts[target] = (counts[target] ?: 0) + weightSelector(voterPlayer)
        }

        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<UUID, Int>> { it.value }
                    .thenBy { service.players[it.key]?.name ?: "~" }
            )
            .map { VoteStanding(it.key, it.value) }
    }

    private fun calculateMayorVoteStandings(): List<VoteStanding> {
        return calculateVoteStandings(
            expectedPhase = GameState.MAYOR_VOTE,
            votes = roundState.mayorVotes
        ) { 1 }
    }

    private fun calculateVoteStandings(): List<VoteStanding> {
        return calculateVoteStandings(
            expectedPhase = GameState.VOTE,
            votes = roundState.votes
        ) { voterPlayer ->
            if (voterPlayer.role == WerwolfRoles.MAYOR) 2 else 1
        }
    }

    fun submitNightAction(action: NightAction): Boolean {
        if (roundState.phase != GameState.NIGHT) return false
        val actor = service.players[action.actor] ?: return false
        if (!actor.isAlive) return false
        if (!nightResolver().isValid(action, actor.role)) return false

        replaceNightAction(action)

        return true
    }

    fun resolveNight(): NightResolutionResult {
        if (roundState.phase != GameState.NIGHT) return NightResolutionResult()

        val resolution = nightResolver().resolve(roundState.nightActions)
        AmorActions.apply(service.players, resolution.lovers)
        announceLovers(resolution.lovers)
        resolution.eliminatedPlayers.forEach(service::executePlayer)

        roundState = roundState.copy(
            protectedPlayer = resolution.protectedPlayer,
            werewolfTarget = resolution.werewolfTarget,
            nightActions = mutableListOf()
        )

        return resolution
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
        if (hasAliveLoverPair(alivePlayers)) return GameOutcome.LoversWin

        val aliveWerewolves = alivePlayers.count { it.role == WerwolfRoles.WERWOLF }
        val aliveVillagers = alivePlayers.count { it.role != WerwolfRoles.WERWOLF }

        if (aliveWerewolves == 0) return GameOutcome.VillagersWin
        if (aliveWerewolves >= aliveVillagers) return GameOutcome.WerewolvesWin

        return null
    }

    private fun hasAliveLoverPair(alivePlayers: List<WerewolfPlayer>): Boolean {
        if (alivePlayers.size != 2) return false

        val firstPlayer = alivePlayers[0]
        val secondPlayer = alivePlayers[1]

        return firstPlayer.inLoveWith == secondPlayer.uuid &&
                secondPlayer.inLoveWith == firstPlayer.uuid
    }

    private fun replaceNightAction(newAction: NightAction) {
        roundState.nightActions.removeAll { existingAction ->
            isSameNightActionSlot(existingAction, newAction)
        }
        roundState.nightActions.add(newAction)
    }

    private fun isSameNightActionSlot(existingAction: NightAction, newAction: NightAction): Boolean {
        return existingAction.actor == newAction.actor &&
                existingAction::class == newAction::class
    }

    private fun announceLovers(lovers: Pair<UUID, UUID>?) {
        if (lovers == null) return

        val (firstId, secondId) = lovers
        val firstName = service.players[firstId]?.name ?: "Unbekannt"
        val secondName = service.players[secondId]?.name ?: "Unbekannt"

        messenger.announceToPlayer(firstId) {
            appendSuccessPrefix()
            success("Du bist nun ein Liebespaar mit")
            appendSpace()
            variableValue(secondName)
            appendSpace()
            success(".")
            appendNewInfoPrefixedLine()
            info("Wenn einer von euch stirbt, stirbt der andere auch.")
        }

        messenger.announceToPlayer(secondId) {
            appendSuccessPrefix()
            success("Du bist nun ein Liebespaar mit")
            appendSpace()
            variableValue(firstName)
            appendSpace()
            success(".")
            appendNewInfoPrefixedLine()
            info("Wenn einer von euch stirbt, stirbt der andere auch.")
        }
    }

    private fun createClickable() = buildText {
        text("HIER", Colors.VARIABLE_VALUE, TextDecoration.UNDERLINED)
        hoverEvent(HoverEvent.showText(buildText { info("Klicke hier, um den Command in den Chat einzufügen!") }))
        clickEvent(ClickEvent.suggestCommand("/werewolf vote "))
    }
}
