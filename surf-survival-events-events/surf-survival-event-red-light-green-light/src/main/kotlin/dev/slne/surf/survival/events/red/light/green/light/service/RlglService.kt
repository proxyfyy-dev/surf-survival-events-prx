package dev.slne.surf.survival.events.red.light.green.light.service

import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import dev.slne.surf.survival.events.base.game.GameContext
import dev.slne.surf.survival.events.base.game.ParticipantRole
import dev.slne.surf.survival.events.base.service.GameService
import dev.slne.surf.survival.events.base.util.GamePosition
import dev.slne.surf.survival.events.red.light.green.light.config.RlglConfig
import dev.slne.surf.survival.events.red.light.green.light.messaging.RlglMessenger
import dev.slne.surf.survival.events.red.light.green.light.plugin
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

object RlglService {

    private val UNSET_POSITION = GamePosition(0.0, 0.0, 0.0)

    private val finishedPlayers = ObjectLinkedOpenHashSet<UUID>()

    @Volatile
    private var openRoundId: String? = null

    @Volatile
    private var roundOpener: UUID? = null

    private val roundPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    private val originalPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    private val previousGameModes = ConcurrentHashMap<UUID, GameMode>()

    @Volatile
    private var state = RlglState.LOBBY

    @Volatile
    private var lightJob: Job? = null

    @Volatile
    private var redLightStartedAt: Long = 0L

    fun getState(): RlglState = state

    fun validateConfig() {
        val config = RlglConfig.getConfig()

        check(config.start.volume > 0.0) {
            "Start-Zone ist nicht konfiguriert (/rlgl area start)."
        }
        check(config.finish.volume > 0.0) {
            "Ziel-Zone ist nicht konfiguriert (/rlgl area finish)."
        }
        check(config.startSpawn != UNSET_POSITION) {
            "Start-Spawn ist nicht konfiguriert (/rlgl set-start-spawn)."
        }
        check(config.spectatorSpawn != UNSET_POSITION) {
            "Spectator-Spawn ist nicht konfiguriert (/rlgl set-spectator-spawn)."
        }
    }

    context(context: GameContext)
    fun onSessionStarted() {
        stopLightCycle()
        state = RlglState.LOBBY
        synchronized(finishedPlayers) { finishedPlayers.clear() }
        openRoundId = null
        roundOpener = null
        roundPlayers.clear()
        originalPlayers.clear()

        context.onlineEventPlayers.forEach { teleportToSpectatorSpawn(it.uniqueId) }
    }

    fun onSessionStopped() {
        stopLightCycle()
        state = RlglState.LOBBY
        openRoundId = null
        roundOpener = null
        roundPlayers.clear()
        originalPlayers.clear()
        restoreAllGameModes()
    }

    fun openRound(sender: Player, id: String?): OpenRoundResult {
        if (state != RlglState.LOBBY) return OpenRoundResult.RoundInProgress
        if (openRoundId != null) return OpenRoundResult.AlreadyOpen

        val roundId = id ?: generateRoundId()
        openRoundId = roundId
        roundOpener = sender.uniqueId
        roundPlayers.clear()

        invitePlayersNearby(sender, roundId)

        return OpenRoundResult.Opened(roundId)
    }

    fun joinRound(player: Player, id: String?): JoinRoundResult {
        val currentRoundId = openRoundId ?: return JoinRoundResult.NO_ROUND_OPEN
        if (id != null && id != currentRoundId) return JoinRoundResult.WRONG_ID
        if (!roundPlayers.add(player.uniqueId)) return JoinRoundResult.ALREADY_JOINED

        notifyOpenerOfJoin(player)

        return JoinRoundResult.JOINED
    }

    private fun notifyOpenerOfJoin(player: Player) {
        val opener = roundOpener?.takeIf { it != player.uniqueId }?.let(Bukkit::getPlayer) ?: return
        RlglMessenger.notifyOpenerOfJoin(opener, player, roundPlayers.size)
    }

    fun currentRoundMembers(): Set<UUID> {
        openRoundId?.let { return roundPlayers.toSet() }
        return originalPlayers.toSet()
    }

    fun aliveCount(): Int = originalPlayers.count { GameService.isPlayer(it) }

    fun totalRoundSize(): Int = originalPlayers.size

    fun isRoundMember(uuid: UUID): Boolean = uuid in originalPlayers

    fun isActiveInRound(uuid: UUID): Boolean = uuid in originalPlayers && GameService.isPlayer(uuid)

    fun topFinishers(limit: Int): List<UUID> {
        return synchronized(finishedPlayers) { finishedPlayers.take(limit) }
    }

    fun placementOf(uuid: UUID): Int? {
        return synchronized(finishedPlayers) {
            val index = finishedPlayers.indexOf(uuid)
            index.takeIf { it >= 0 }?.plus(1)
        }
    }

    fun forgetPlayer(uuid: UUID) {
        roundPlayers.remove(uuid)
        originalPlayers.remove(uuid)
    }

    context(context: GameContext)
    fun removePlayer(remover: Player, target: Player, id: String?): RemovePlayerResult {
        openRoundId?.let { currentRoundId ->
            if (id != null && id != currentRoundId) return RemovePlayerResult.WRONG_ID
            if (!roundPlayers.remove(target.uniqueId)) return RemovePlayerResult.NOT_IN_ROUND

            RlglMessenger.notifyRemovedFromOpenRound(target, remover)

            return RemovePlayerResult.REMOVED
        }

        if (state == RlglState.GREEN || state == RlglState.RED) {
            if (target.uniqueId !in originalPlayers) return RemovePlayerResult.NOT_IN_ROUND

            kickFromRunningRound(target)
            return RemovePlayerResult.REMOVED
        }

        return RemovePlayerResult.NO_ROUND_OPEN
    }

    fun onActivePlayerRemoved() {
        checkRoundEnd()
    }

    fun stopRound(): Boolean {
        if (state == RlglState.LOBBY && openRoundId == null) return false

        stopLightCycle()
        state = RlglState.LOBBY
        openRoundId = null
        roundOpener = null
        roundPlayers.clear()
        originalPlayers.clear()
        synchronized(finishedPlayers) { finishedPlayers.clear() }
        restoreAllGameModes()

        return true
    }

    context(context: GameContext)
    fun startRound(id: String?): StartRoundResult {
        val currentRoundId = openRoundId ?: return StartRoundResult.NO_ROUND_OPEN
        if (id != null && id != currentRoundId) return StartRoundResult.WRONG_ID
        if (state != RlglState.LOBBY) return StartRoundResult.ALREADY_RUNNING
        if (roundPlayers.isEmpty()) return StartRoundResult.NO_PLAYERS_JOINED

        openRoundId = null
        roundOpener = null
        originalPlayers.clear()
        originalPlayers.addAll(roundPlayers)
        roundPlayers.clear()

        originalPlayers.forEach { teleportToStart(it) }

        lightJob = plugin.launch {
            state = RlglState.GREEN
            while (isActive) {
                RlglMessenger.broadcastPhase(originalPlayers.mapNotNull(Bukkit::getPlayer), state)
                delay(randomPhaseDuration(state).seconds)
                state = if (state == RlglState.GREEN) RlglState.RED else RlglState.GREEN
                if (state == RlglState.RED) {
                    redLightStartedAt = System.currentTimeMillis()
                }
            }
        }
        return StartRoundResult.STARTED
    }

    private fun generateRoundId(): String = UUID.randomUUID().toString().substring(0, 6)

    private fun invitePlayersNearby(sender: Player, roundId: String) {
        val radius = RlglConfig.getConfig().gameplay.inviteRadius

        sender.getNearbyEntities(radius, radius, radius)
            .filterIsInstance<Player>()
            .forEach { invitee -> RlglMessenger.sendRoundInvite(invitee, sender, roundId) }
    }

    context(context: GameContext)
    fun handleMovement(player: Player) {
        val uuid = player.uniqueId
        if (uuid !in originalPlayers) return
        if (!GameService.isPlayer(uuid)) return
        if (state != RlglState.GREEN && state != RlglState.RED) return

        if (isInsideFinish(player.location)) {
            finish(player)
            return
        }

        if (state == RlglState.RED && !isWithinRedLightGrace() && !isInsideStart(player.location)) {
            eliminate(player)
        }
    }

    private fun isWithinRedLightGrace(): Boolean {
        val graceMillis = RlglConfig.getConfig().gameplay.redLightGraceMillis
        return System.currentTimeMillis() - redLightStartedAt < graceMillis
    }

    context(context: GameContext)
    private fun isInsideFinish(location: Location): Boolean {
        if (context.eventWorld != location.world) return false
        return RlglConfig.getConfig().finish.containsColumn(location)
    }

    context(context: GameContext)
    private fun isInsideStart(location: Location): Boolean {
        if (context.eventWorld != location.world) return false
        return RlglConfig.getConfig().start.containsColumn(location)
    }

    private fun BoundingBox.containsColumn(location: Location): Boolean {
        return location.x >= minX && location.x < maxX + 1.0 &&
                location.z >= minZ && location.z < maxZ + 1.0
    }

    context(context: GameContext)
    private fun eliminate(player: Player) {
        GameService.setParticipantRole(player.uniqueId, ParticipantRole.SPECTATOR)
        teleportToSpectatorSpawn(player.uniqueId)

        previousGameModes.putIfAbsent(player.uniqueId, player.gameMode)
        player.gameMode = GameMode.SPECTATOR

        RlglMessenger.playEliminationEffects(player)
        RlglMessenger.broadcastElimination(originalPlayers.mapNotNull(Bukkit::getPlayer), player)

        checkRoundEnd()
    }

    context(context: GameContext)
    private fun kickFromRunningRound(player: Player) {
        val uuid = player.uniqueId

        GameService.setParticipantRole(uuid, ParticipantRole.SPECTATOR)
        teleportToSpectatorSpawn(uuid)

        previousGameModes.putIfAbsent(uuid, player.gameMode)
        player.gameMode = GameMode.SPECTATOR

        RlglMessenger.broadcastKick(originalPlayers.mapNotNull(Bukkit::getPlayer), player)

        checkRoundEnd()
    }

    context(context: GameContext)
    private fun finish(player: Player) {
        val uuid = player.uniqueId
        val placement = synchronized(finishedPlayers) {
            finishedPlayers.add(uuid)
            finishedPlayers.size
        }
        GameService.setParticipantRole(uuid, ParticipantRole.SPECTATOR)
        teleportToSpectatorSpawn(uuid)

        previousGameModes.putIfAbsent(uuid, player.gameMode)
        player.gameMode = GameMode.SPECTATOR

        RlglMessenger.playFinishSound(player)
        RlglMessenger.broadcastFinish(originalPlayers.mapNotNull(Bukkit::getPlayer), player, placement)

        checkRoundEnd()
    }

    context(context: GameContext)
    private fun teleportToStart(uuid: UUID) {
        val player = Bukkit.getPlayer(uuid) ?: return
        val location = RlglConfig.getConfig().startSpawn.toLocation()

        plugin.launch {
            player.teleportAsync(location).await()
        }
    }

    context(context: GameContext)
    private fun teleportToSpectatorSpawn(uuid: UUID) {
        val player = Bukkit.getPlayer(uuid) ?: return
        val location = RlglConfig.getConfig().spectatorSpawn.toLocation()

        plugin.launch {
            player.teleportAsync(location).await()
        }
    }

    private fun restoreAllGameModes() {
        val uuids = previousGameModes.keys.toList()
        uuids.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            plugin.launch(plugin.entityDispatcher(player)) { applyPendingGameModeRestore(player) }
        }
    }

    fun applyPendingGameModeRestore(player: Player) {
        val previousGameMode = previousGameModes.remove(player.uniqueId) ?: return

        if (player.gameMode == GameMode.SPECTATOR) {
            player.gameMode = previousGameMode
        }
    }

    private fun checkRoundEnd() {
        if (state != RlglState.GREEN && state != RlglState.RED) return
        if (originalPlayers.any { GameService.isPlayer(it) }) return

        stopLightCycle()
        state = RlglState.FINISHED

        val winners = synchronized(finishedPlayers) { finishedPlayers.toList() }
        RlglMessenger.broadcastResults(originalPlayers.mapNotNull(Bukkit::getPlayer), winners)
    }

    private fun stopLightCycle() {
        lightJob?.cancel()
        lightJob = null
    }

    private fun randomPhaseDuration(state: RlglState): Long {
        val gameplay = RlglConfig.getConfig().gameplay
        return when (state) {
            RlglState.GREEN -> Random.nextLong(
                gameplay.minGreenLightSeconds.toLong(),
                gameplay.maxGreenLightSeconds + 1L
            )

            RlglState.RED -> Random.nextLong(
                gameplay.minRedLightSeconds.toLong(),
                gameplay.maxRedLightSeconds + 1L
            )

            else -> 3L
        }
    }

}