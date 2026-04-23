package dev.slne.surf.event.werewolf.commands

import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.event.werewolf.commands.subcommands.startWerewolfCommand
import dev.slne.surf.event.werewolf.commands.subcommands.openGameWerewolfCommand
import dev.slne.surf.event.werewolf.commands.subcommands.stopWerewolfCommand
import dev.slne.surf.event.werewolf.commands.subcommands.joinWerewolfCommand
import dev.slne.surf.event.werewolf.commands.subcommands.killWerewolfCommand
import dev.slne.surf.event.werewolf.commands.subcommands.voteWerewolfCommand

fun werewolfCommand() = commandAPICommand("werewolf"){

    subcommand(openGameWerewolfCommand())
    subcommand(startWerewolfCommand())
    subcommand(stopWerewolfCommand())
    subcommand(joinWerewolfCommand())
    subcommand(voteWerewolfCommand())
    subcommand(killWerewolfCommand())

    playerExecutor { player, arguments ->

    }
}
