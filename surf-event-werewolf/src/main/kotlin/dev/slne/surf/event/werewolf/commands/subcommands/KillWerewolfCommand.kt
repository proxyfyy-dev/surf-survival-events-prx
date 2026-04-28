package dev.slne.surf.event.werewolf.commands.subcommands

import dev.jorel.commandapi.arguments.EntitySelectorArgument
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.api.core.messages.adventure.uuid
import dev.slne.surf.event.werewolf.service.WerewolfGameManager
import dev.slne.surf.event.werewolf.util.GameState
import dev.slne.surf.event.werewolf.util.WerwolfRoles
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player

fun killWerewolfCommand() = subcommand("kill") {
//    withAliases("eat")
    withArguments(EntitySelectorArgument.OnePlayer("player"))

    playerExecutor { commandSender, arguments ->
        val targetPlayer = arguments.get("player") as Player
        val service = WerewolfGameManager.getGameForPlayer(commandSender.uuid())

        if (service == null) {
            commandSender.sendText {
                appendErrorPrefix()
                error("Du bist aktuell in keinem Werwolf-Spiel.")
            }
            return@playerExecutor
        }

        if (service.isPhaseTransitioning) {
            commandSender.sendText {
                appendErrorPrefix()
                error("Der Phasenwechsel laeuft gerade noch. Warte einen kurzen Moment.")
            }
            return@playerExecutor
        }

        val currentPhase = service.engine.currentPhase
        if (currentPhase != GameState.NIGHT) {
            commandSender.sendText {
                appendErrorPrefix()
                error("Du kannst diesen Befehl nur wahrend der Nacht benutzen!")
            }
            return@playerExecutor
        }

        val playerRole = service.getPlayerRole(commandSender.uuid())
        if (playerRole != WerwolfRoles.WERWOLF) {
            commandSender.sendText {
                appendErrorPrefix()
                error("Du kannst niemanden töten oder essen!")
            }
        }

        if (targetPlayer == commandSender) {
            commandSender.sendText {
                appendErrorPrefix()
                error("Du kannst dich nicht selber essen :)")
            }
            return@playerExecutor
        }

        service.engine.submitWerewolfTarget(targetPlayer.uuid())

        commandSender.sendText {
            appendSuccessPrefix()
            success("Du hast den Dorfbewohner")
            appendSpace()
            variableValue(targetPlayer.name, TextDecoration.BOLD)
            appendSpace()
            success("als dein Opfer auserwählt!")
        }

        service.announceToRole(
            WerwolfRoles.WERWOLF,
            true,
            content = {
                appendInfoPrefix()
                info("")
            }
        )
    }
}
