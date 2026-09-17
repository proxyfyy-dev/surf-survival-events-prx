package dev.slne.surf.survival.events.red.light.green.light.command.subcommand

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.entitySelectorArgumentOnePlayer
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.stringArgument
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.survival.events.base.service.GameService
import dev.slne.surf.survival.events.red.light.green.light.game.RedLightGreenLightGame
import dev.slne.surf.survival.events.red.light.green.light.service.RemovePlayerResult
import dev.slne.surf.survival.events.red.light.green.light.service.RlglService
import dev.slne.surf.survival.events.red.light.green.light.utils.PermissionList
import org.bukkit.entity.Player

fun CommandAPICommand.removePlayerCommand() = subcommand("removePlayer") {
    withPermission(PermissionList.COMMAND_COMMUNITY_MANAGER)
    entitySelectorArgumentOnePlayer("target")
    stringArgument("id", optional = true)

    playerExecutor { player, arguments ->
        if (!GameService.isActiveGame(RedLightGreenLightGame.KEY)) {
            player.sendText {
                appendErrorPrefix()
                error("Red Light, Green Light ist nicht aktiv.")
            }
            return@playerExecutor
        }

        val target: Player? by arguments
        val id: String? by arguments

        val resolvedTarget = target ?: run {
            player.sendText {
                appendErrorPrefix()
                error("Spieler nicht gefunden oder nicht online.")
            }
            return@playerExecutor
        }

        val result = GameService.withGameContext(RedLightGreenLightGame.KEY) {
            RlglService.removePlayer(player, resolvedTarget, id)
        }

        when (result) {
            RemovePlayerResult.REMOVED -> {
                player.sendText {
                    appendSuccessPrefix()
                    variableValue(resolvedTarget.name)
                    appendSpace()
                    success("wurde aus der Runde entfernt.")
                }
            }

            RemovePlayerResult.NOT_IN_ROUND -> {
                player.sendText {
                    appendErrorPrefix()
                    variableValue(resolvedTarget.name)
                    appendSpace()
                    error("ist nicht in der Runde.")
                }
            }

            RemovePlayerResult.NO_ROUND_OPEN -> {
                player.sendText {
                    appendErrorPrefix()
                    error("Es ist aktuell keine Runde offen oder aktiv.")
                }
            }

            RemovePlayerResult.WRONG_ID -> {
                player.sendText {
                    appendErrorPrefix()
                    error("Es ist keine Runde mit dieser ID offen.")
                }
            }
        }
    }
}