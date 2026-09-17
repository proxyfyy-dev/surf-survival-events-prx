package dev.slne.surf.survival.events.red.light.green.light.command.subcommand

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.survival.events.red.light.green.light.config.RlglConfig
import dev.slne.surf.survival.events.red.light.green.light.utils.PermissionList

fun CommandAPICommand.debugCommand() = subcommand("debug") {
    withPermission(PermissionList.COMMAND_COMMUNITY_MANAGER)

    subcommand("reload") {
        anyExecutor { sender, _ ->
            RlglConfig.reloadFromFile()

            sender.sendText {
                appendSuccessPrefix()
                success("Die Red Light, Green Light Konfiguration wurde neu geladen.")
            }
        }
    }
}