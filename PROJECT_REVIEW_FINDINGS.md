# Projekt-Review Findings

Stand: 2026-04-22

Gepruefter Stand:
- Kompiliert mit `./gradlew.bat :surf-event-werewolf:compileKotlin`
- Es gibt aktuell kein `src/test`, also keine automatisierten Tests fuer die Spiellogik

## Findings

### 1. Kritisch: Phasenlaufzeiten werden beim Phasenwechsel nicht neu gesetzt, dadurch ueberspringt der Cycle nach der ersten Phase den restlichen Ablauf

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:116`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:126`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:136`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:147`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:35`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/GameState.kt:17`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/GameState.kt:24`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/GameState.kt:31`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/GameState.kt:38`

Beschreibung:
- `tick()` zaehlt die Restzeit bis `0.seconds` herunter.
- Beim Wechsel in `beginMayorVoting()`, `beginVotePhase()`, `beginNightPhase()` und `beginDayPhase()` wird die Restzeit aber immer wieder auf `roundState.phaseRemainingSeconds` gesetzt statt auf die Zielzeit der neuen Phase.
- Nach dem ersten Ablauf startet die naechste Phase damit bereits mit `0.seconds` und wird im naechsten Tick sofort wieder weitergeschaltet.

Auswirkung:
- Der eigentliche Game-Cycle `DAY -> VOTE -> NIGHT -> DAY` laeuft fachlich nicht.
- Spieler haben effektiv keine Zeit fuer Aktionen oder Abstimmungen.

Empfohlene Behebung:
- Jede `begin...Phase()`-Methode muss explizit `GameState.<PHASE>.time` setzen.
- `startGameEngine()` sollte dieselbe Logik benutzen statt eine feste `150.seconds`-Zahl zu hardcoden.

### 2. Hoch: Die Siegbedingungen sind fuer Spiele mit `SERIAL_KILLER` und Liebespaar aktuell falsch

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:230`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/WerewolfRoleSelection.kt:80`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/GameOutcome.kt:3`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/WerewolfPlayer.kt:9`

Beschreibung:
- `checkWinCondition()` unterscheidet nur zwischen `WERWOLF` und `nicht WERWOLF`.
- Ab 15 Spielern wird aber `SERIAL_KILLER` aktiv vergeben.
- Gleichzeitig existieren bereits Modelle fuer `LoversWin` und `SerialKillerWin`, werden aber nirgends verwendet.

Auswirkung:
- Ein ueberlebender Serienmoerder kann nie korrekt gewinnen.
- Das Liebespaar kann ebenfalls nie korrekt ausgewertet werden.
- Spiele mit diesen Rollen liefern falsche Gewinner.

Empfohlene Behebung:
- `checkWinCondition()` auf echte Fraktionen aufteilen.
- `GameOutcome` statt roher `String`-Rueckgaben verwenden.
- Serienmoerder und Liebespaar gesondert behandeln.

### 3. Hoch: Die Buergermeisterwahl ist fachlich wirkungslos und aktuell sogar unerreichbar

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:49`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:51`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:116`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/domain/WerewolfGameEngine.kt:205`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/GameRoundState.kt:16`

Beschreibung:
- `resolveMayorVote()` ermittelt zwar einen Spieler und speichert ihn in `mayorPlayer`.
- In `resolveVote()` zaehlt aber weiterhin nur die Rolle `WerwolfRoles.MAYOR` doppelt, nicht der gewaehlte `mayorPlayer`.
- Zusaetzlich wird `beginMayorVoting()` im restlichen Projekt nirgends aufgerufen, die Phase ist also aktuell gar nicht Teil des Spielablaufs.

Auswirkung:
- Die Wahl hat keinen Einfluss auf das Spiel.
- Der Code vermittelt eine Mechanik, die faktisch nicht existiert.
- Gleichzeitig ist unklar, ob `MAYOR` eine Rolle oder ein gewaehltes Amt sein soll.

Empfohlene Behebung:
- Entweder `mayorPlayer` als einzige Quelle fuer das doppelte Stimmgewicht nutzen.
- Oder die Wahl komplett entfernen, solange `MAYOR` noch eine feste Rolle ist.
- Den Game-Cycle klar entscheiden und die Phase entweder wirklich starten oder komplett streichen.

### 4. Hoch: Das Spiel kann mit ungueltigen Spielerzahlen starten und erzeugt dann inkonsistente Rollenverteilungen

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt:117`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/WerewolfRoleSelection.kt:15`

Beschreibung:
- `WerewolfService.start()` erlaubt aktuell testweise schon `minPlayers = 1`.
- `WerewolfRoleSelection.assignRoles()` verwendet fuer alle Spielerzahlen unter 8 trotzdem die 8er-Rollenliste.
- Dadurch entstehen bei 1 bis 7 Spielern beliebige Teilmengen einer fuer 8 Spieler gedachten Verteilung.

Auswirkung:
- Spiele koennen ohne Werwolf starten.
- Spiele koennen mit voellig unbalancierten oder nicht spielbaren Rollen starten.
- Die Siegbedingungen werden dadurch noch fehleranfaelliger.

Empfohlene Behebung:
- Wieder eine fachlich gueltige Mindestspielerzahl erzwingen.
- Oder fuer kleinere Spielerzahlen eigene Role-Sets definieren.

### 5. Mittel: `join()` traegt Spieler vor der Existenzpruefung in den Spielzustand ein

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt:92`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt:96`

Beschreibung:
- Der Spieler wird sofort in `players` eingetragen.
- Erst danach wird versucht, den Bukkit-Player aufgeloest und dem Scoreboard hinzuzufuegen.
- Falls das Aufloesen fehlschlaegt, wird `WerewolfJoinResult.Error` zurueckgegeben, der interne Eintrag bleibt aber bestehen.

Auswirkung:
- Interner Spielzustand und echter Online-Zustand koennen auseinanderlaufen.
- Nachfolgende Rollenvergabe, Alive-Count und Broadcasts arbeiten dann auf fehlerhaften Daten.

Empfohlene Behebung:
- Erst `toBukkitPlayer()` erfolgreich aufloesen.
- Danach `players` mutieren und Nebeneffekte ausfuehren.

### 6. Mittel: `openGame` legt Spiele an, obwohl die Vorbedingungen fuer eine nutzbare Lobby nicht erfuellt sind

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/commands/subcommands/WerewolfOpengameCommand.kt:27`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/commands/subcommands/WerewolfOpengameCommand.kt:39`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfGameManager.kt:15`

Beschreibung:
- Das Spiel wird direkt mit `createGame(...)` angelegt.
- Erst danach wird geprueft, ob ueberhaupt Spieler in der Naehe sind.
- Wenn keine Spieler da sind, wird zwar eine Fehlermeldung gesendet, die leere Lobby bleibt aber bestehen.

Auswirkung:
- Verwaiste Spiele bleiben im Manager registriert.
- Der Leader wird an ein Spiel gebunden, obwohl das Oeffnen fachlich gescheitert ist.

Empfohlene Behebung:
- Erst potentielle Teilnehmer suchen.
- Das Spiel nur dann anlegen, wenn die Vorbedingungen erfuellt sind.

### 7. Mittel: Stop/Cleanup behandeln den Leader inkonsistent

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt:294`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt:297`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt:304`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfGameManager.kt:15`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfGameManager.kt:28`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfGameManager.kt:30`

Beschreibung:
- In `stop()` wird `_leader` vor dem Broadcast auf `null` gesetzt.
- `announceToAll()` erreicht den Leader damit nicht mehr, falls er nicht selbst Teilnehmer ist.
- In `WerewolfGameManager.removeGame()` werden nur `game.players.keys` aus `playerToGame` entfernt, der beim Erstellen separat eingetragene Leader aber nicht.

Auswirkung:
- Der Erzaehler/Leader bekommt Stop- und Endmeldungen nicht verlaesslich.
- Im Manager bleibt ein stale Mapping fuer den Leader zurueck.

Empfohlene Behebung:
- Erst broadcasten, dann den Leader loeschen.
- Beim `removeGame()` auch `game.leader` aus `playerToGame` entfernen.

### 8. Mittel: Voicechat ist aktuell deaktiviert, trotzdem bleibt die Funktionalitaet im Spielservice aktiv und kann Spieler sogar stummschalten

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/PaperMain.kt:17`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/voicechat/PrivateAudioHandler.kt:25`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/voicechat/PrivateAudioHandler.kt:35`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/voicechat/PrivateAudioHandler.kt:53`
- `surf-event-werewolf/build.gradle.kts:27`

Beschreibung:
- Die Registrierung des Voicechat-Plugins ist in `PaperMain` komplett auskommentiert.
- Gleichzeitig bleibt `VoiceChat` in Gradle als erforderliche Server-Abhaengigkeit deklariert.
- `PrivateAudioHandler` traegt geheime Spieler auch dann ein, wenn gar kein Channel erstellt werden konnte, und `onMicrophone()` cancelt danach trotzdem deren Pakete.

Auswirkung:
- Die Voicechat-Mechanik ist aktuell faktisch deaktiviert.
- Im Fehlerfall koennen geheime Spieler stumm werden.
- Der Service kann weiterhin Erfolgsmeldungen fuer einen privaten Sprachkanal senden, obwohl keiner existiert.

Empfohlene Behebung:
- Entweder Voicechat sauber wieder anbinden.
- Oder die Abhaengigkeit und alle Laufzeitpfade voruebergehend konsequent deaktivieren.
- In `PrivateAudioHandler` nur dann `secretPlayers` setzen und `event.cancel()` nutzen, wenn der Channel wirklich steht.

### 9. Mittel: `start()` wirft bei erneutem Startversuch eine Exception statt sauber mit dem Result-Typ zu antworten

Betroffene Stelle:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt:110`

Beschreibung:
- Der Rest der API arbeitet mit `WerewolfStartResult`.
- Beim Fall `werewolfTask != null` wird stattdessen mit `error(...)` hart abgebrochen.

Auswirkung:
- Der Command-Flow ist in diesem Sonderfall nicht konsistent modelliert.
- Statt einer normalen Fehlermeldung kann eine ungeplante Exception in den Aufrufer laufen.

Empfohlene Behebung:
- Auch diesen Fall ueber `WerewolfStartResult` abbilden.

## Offene Fragen und Annahmen

- Ich bewerte `SERIAL_KILLER` und Liebespaar als echte, spaeter spielbare Mechaniken, weil die Rollen bereits vergeben werden und `GameOutcome` diese Faelle schon modelliert.
- Ich habe die Voicechat-Probleme als echte Findings aufgenommen, obwohl die Integration momentan teilweise auskommentiert ist, weil Build-Konfiguration und Service-Code die Funktion weiterhin als vorhanden behandeln.
- Die Buergermeisterwahl wirkt aktuell wie ein halbfertig eingefuehrter Mechanismus. Falls `MAYOR` doch nur eine feste Rolle sein soll, sollte die Wahl komplett entfernt werden.

## Verifikation

- Erfolgreich ausgefuehrt: `./gradlew.bat :surf-event-werewolf:compileKotlin`
- Nicht moeglich zu verifizieren: Laufzeitverhalten im Paper-Server, weil keine integrierten Tests oder Server-Szenarien im Projekt vorhanden sind.
