package dev.slne.surf.event.werewolf.util

import dev.slne.surf.api.core.messages.adventure.buildText
import net.kyori.adventure.text.Component
import org.gradle.internal.time.Time
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class GameState(
    val displayName: Component,
    val time: Duration
) {
    NIGHT(
        buildText {
            darkBlue("Nacht")
        },
        time = 120.seconds
    ),

    DAY(
        buildText {
            gold("Tag")
        },
        time = 120.seconds
    ),

    VOTE(
        buildText {
            yellow("Abstimmung")
        },
        time = 150.seconds
    ),

    MAYOR_VOTE(
        buildText {
            yellow("Bürgermeisterwahl")
        },
        time = 150.seconds
    );
}
