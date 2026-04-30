package dev.slne.surf.event.werewolf.messaging

import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.api.core.messages.builder.SurfComponentBuilder
import dev.slne.surf.event.werewolf.service.WerewolfService
import dev.slne.surf.event.werewolf.util.GameState
import dev.slne.surf.event.werewolf.util.VoteStanding
import dev.slne.surf.event.werewolf.util.WerwolfRoles
import dev.slne.surf.event.werewolf.util.toBukkitPlayer
import java.util.*

class WerewolfMessenger(private val service: WerewolfService) {

    fun announceToPlayer(playerId: UUID, content: SurfComponentBuilder.() -> Unit) {
        playerId.toBukkitPlayer()?.sendText(content)
    }

    fun announceToAll(content: SurfComponentBuilder.() -> Unit) {
        service.players.keys.forEach { uuid ->
            uuid.toBukkitPlayer()?.sendText(content)
        }

        service.leader?.toBukkitPlayer()?.sendText(content)
    }

    fun announceToLeader(content: SurfComponentBuilder.() -> Unit) {
        service.leader?.toBukkitPlayer()?.sendText(content)
    }

    fun announceToRole(
        role: WerwolfRoles,
        onlyAlive: Boolean = true,
        content: SurfComponentBuilder.() -> Unit
    ) {
        service.players.values
            .filter { it.role == role }
            .filter { !onlyAlive || it.isAlive }
            .forEach { player ->
                player.uuid.toBukkitPlayer()?.sendText(content)
            }
    }

    fun announceToAlive(content: SurfComponentBuilder.() -> Unit) {
        service.getAlivePlayers()
            .forEach { player ->
                player.uuid.toBukkitPlayer()?.sendText(content)
            }
    }

    fun announceVotings(
        state: GameState,
        standings: List<VoteStanding>,
        chosenPlayer: UUID? = null,
        eliminatedPlayers: List<UUID> = emptyList(),
        content: SurfComponentBuilder.() -> Unit = {}
    ) {
        val type = when (state) {
            GameState.MAYOR_VOTE -> "Bürgermeisterwahl"
            GameState.VOTE -> "Dorfabstimmung"
            else -> "Abstimmung"
        }

        val podium = standings.take(3)

        announceToAlive {
            appendSuccessPrefix()
            success("Die Ergebnisse der")
            appendSpace()
            variableValue(type)
            appendSpace()
            success("sind da!")

            if (podium.isEmpty()) {
                appendNewInfoPrefixedLine()
                info("Es wurde keine gültige Stimme abgegeben.")
            } else {
                appendNewInfoPrefixedLine()
                info("Das Podium im Überblick:")

                podium.forEachIndexed { index, standing ->
                    appendNewInfoPrefixedLine()
                    variableValue("${index + 1}. Platz")
                    appendSpace()
                    spacer("-")
                    appendSpace()
                    variableValue(playerName(standing.target))
                    appendSpace()
                    spacer("(")
                    variableValue(standing.votes)
                    appendSpace()
                    info(if (standing.votes == 1) "Stimme" else "Stimmen")
                    spacer(")")
                }

                if (standings.size > podium.size) {
                    appendNewInfoPrefixedLine()
                    info("Weitere Kandidaten:")
                    appendSpace()
                    variableValue(standings.size - podium.size)
                }
            }

            when (state) {
                GameState.MAYOR_VOTE -> {
                    appendNewInfoPrefixedLine()
                    if (chosenPlayer != null) {
                        success("Neuer Bürgermeister:")
                        appendSpace()
                        variableValue(playerName(chosenPlayer))
                    } else {
                        info("Es konnte kein Bürgermeister bestimmt werden.")
                    }
                }

                GameState.VOTE -> {
                    appendNewInfoPrefixedLine()
                    val eliminatedPlayer = eliminatedPlayers.firstOrNull()
                    if (eliminatedPlayer != null) {
                        error("Aus dem Dorf ausgeschieden:")
                        appendSpace()
                        variableValue(playerName(eliminatedPlayer))
                    } else {
                        info("Niemand wurde aus dem Dorf entfernt.")
                    }
                }

                else -> Unit
            }

            content()
        }
    }

    private fun playerName(uuid: UUID): String =
        service.players[uuid]?.name ?: uuid.toBukkitPlayer()?.name ?: "Unbekannt"
}
