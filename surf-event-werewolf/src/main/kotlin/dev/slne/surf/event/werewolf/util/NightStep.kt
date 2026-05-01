package dev.slne.surf.event.werewolf.util

enum class NightStep(
    val activeRole: WerwolfRoles?,
) {
    AMOR(WerwolfRoles.AMOR),
    WEREWOLVES(WerwolfRoles.WERWOLF),
    SEER(WerwolfRoles.SEER),
    DOCTOR(WerwolfRoles.DOCTOR),
    WITCH(WerwolfRoles.WITCH),
    RESOLVE(null),
}
