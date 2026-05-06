package dev.slne.surf.event.werewolf.util

enum class NightStep(
    val activeRole: WerwolfRoles?,
) {
    AMOR(WerwolfRoles.AMOR),
    WEREWOLVES(WerwolfRoles.WERWOLF),
    GIRL(WerwolfRoles.GIRL),
    SEER(WerwolfRoles.SEER),
    DOCTOR(WerwolfRoles.DOCTOR),
    WITCH(WerwolfRoles.WITCH),
    SERIAL_KILLER(WerwolfRoles.SERIAL_KILLER),
    RESOLVE(null),
}
