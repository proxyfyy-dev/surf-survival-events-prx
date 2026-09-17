package dev.slne.surf.survival.events.red.light.green.light.service

enum class RlglState {
    /** Round has not been started yet, players may move freely. */
    LOBBY,

    /** Players may move. */
    GREEN,

    /** Any movement gets a player eliminated. */
    RED,

    /** Round is over, no more eliminations/finishes are processed. */
    FINISHED
}