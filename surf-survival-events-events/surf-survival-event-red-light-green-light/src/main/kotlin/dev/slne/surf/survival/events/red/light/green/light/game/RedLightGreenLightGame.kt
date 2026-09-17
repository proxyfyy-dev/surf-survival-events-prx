package dev.slne.surf.survival.events.red.light.green.light.game

import dev.slne.surf.survival.events.base.game.GameContext
import dev.slne.surf.survival.events.base.game.GameHandler
import dev.slne.surf.survival.events.base.game.GameKey
import dev.slne.surf.survival.events.base.game.GameOptions
import dev.slne.surf.survival.events.base.game.GameStopReason
import dev.slne.surf.survival.events.base.game.ParticipantRole
import dev.slne.surf.survival.events.base.game.PlayerRemoveReason
import dev.slne.surf.survival.events.base.service.GameService
import dev.slne.surf.survival.events.red.light.green.light.plugin
import dev.slne.surf.survival.events.red.light.green.light.scoreboard.addToRlglScoreboard
import dev.slne.surf.survival.events.red.light.green.light.scoreboard.removeFromRlglScoreboard
import dev.slne.surf.survival.events.red.light.green.light.service.RlglService
import org.bukkit.entity.Player
import java.util.UUID

class RedLightGreenLightGame : GameHandler {
    companion object {
        val KEY = GameKey.builder<RedLightGreenLightGame>()
            .key("red_light_green_light")
            .displayName("RED LIGHT, GREEN LIGHT")
            .skullTexture("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ")
            .build()
    }

    override val options: GameOptions
        get() = GameOptions()

    context(context: GameContext)
    override suspend fun onStarting() {
        RlglService.validateConfig()
    }

    context(context: GameContext)
    override suspend fun onStarted() {
        RlglService.onSessionStarted()

        context.onlineEventPlayers.forEach { it.addToRlglScoreboard() }

        plugin.logger.info(
            "Starting ${context.key.displayName} in ${context.eventWorldName}: " +
                    "players=${context.activePlayerCount}"
        )
    }

    context(context: GameContext)
    override suspend fun onRunningPlayerJoin(player: Player) {
        player.addToRlglScoreboard()
    }

    context(context: GameContext)
    override suspend fun onRunningReserveJoin(player: Player) {
        player.addToRlglScoreboard()
    }

    context(context: GameContext)
    override suspend fun onRunningSpectatorJoin(player: Player) {
        player.addToRlglScoreboard()
    }

    context(context: GameContext)
    override suspend fun onParticipantRemove(
        uuid: UUID,
        player: Player?,
        role: ParticipantRole,
        reason: PlayerRemoveReason
    ) {
        RlglService.forgetPlayer(uuid)

        if (role == ParticipantRole.PLAYER) {
            RlglService.onActivePlayerRemoved()
        }

        player?.removeFromRlglScoreboard()
    }

    context(context: GameContext)
    override suspend fun onStop(reason: GameStopReason) {
        RlglService.onSessionStopped()

        context.onlineEventPlayers.forEach { player ->
            player.removeFromRlglScoreboard()
            GameService.teleportToServerLobby(player)
        }

        plugin.logger.info("Red Light, Green Light stopped because of $reason")
    }
}