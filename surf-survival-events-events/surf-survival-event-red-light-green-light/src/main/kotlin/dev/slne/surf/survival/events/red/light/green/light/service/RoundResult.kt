package dev.slne.surf.survival.events.red.light.green.light.service

sealed interface OpenRoundResult {
    data class Opened(val id: String) : OpenRoundResult
    data object AlreadyOpen : OpenRoundResult
    data object RoundInProgress : OpenRoundResult
}

enum class JoinRoundResult {
    NO_ROUND_OPEN,
    WRONG_ID,
    ALREADY_JOINED,
    JOINED
}

enum class StartRoundResult {
    NO_ROUND_OPEN,
    WRONG_ID,
    NO_PLAYERS_JOINED,
    ALREADY_RUNNING,
    STARTED
}

enum class RemovePlayerResult {
    NO_ROUND_OPEN,
    WRONG_ID,
    NOT_IN_ROUND,
    REMOVED
}