package dev.slne.surf.survival.events.red.light.green.light.command

import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.addPlayerCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.areaCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.debugCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.joinCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.listCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.openRoundCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.removePlayerCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.setSpectatorSpawnCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.setStartSpawnCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.startCommand
import dev.slne.surf.survival.events.red.light.green.light.command.subcommand.stopCommand

fun rlglCommand() = commandAPICommand("rlgl") {
    areaCommand()
    openRoundCommand()
    joinCommand()
    addPlayerCommand()
    removePlayerCommand()
    listCommand()
    startCommand()
    stopCommand()
    setStartSpawnCommand()
    setSpectatorSpawnCommand()
    debugCommand()
}

