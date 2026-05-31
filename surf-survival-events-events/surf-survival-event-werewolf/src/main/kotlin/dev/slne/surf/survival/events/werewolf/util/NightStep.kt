package dev.slne.surf.survival.events.werewolf.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class NightStep(
    val activeRole: WerwolfRoles?,
    val time: Duration,
) {
    AMOR(WerwolfRoles.AMOR, 30.seconds),
    WEREWOLVES(WerwolfRoles.WERWOLF, 60.seconds),
    GIRL(WerwolfRoles.GIRL, 20.seconds),
    SEER(WerwolfRoles.SEER, 30.seconds),
    DOCTOR(WerwolfRoles.DOCTOR, 30.seconds),
    WITCH(WerwolfRoles.WITCH, 30.seconds),
    SERIAL_KILLER(WerwolfRoles.SERIAL_KILLER, 60.seconds),
    RESOLVE(null, 1.seconds),
}
