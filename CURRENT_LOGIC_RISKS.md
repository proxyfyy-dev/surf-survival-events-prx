# Current Logic Risks

Stand: 2026-04-21

Dieses Dokument sammelt die aktuell sichtbaren Logik- und Zustandsrisiken im Werewolf-Plugin, damit sie spaeter gezielt behoben werden koennen.

## 1. `start()` wirft vor der eigentlichen Statuspruefung

Betroffene Stelle:
`surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt`

Relevante Logik:
- `if (werewolfTask != null) error("Werewolf task is already running!")`
- danach erst die Rueckgabe `WerewolfStartResult.NotInLobbyPhase`

Risiko:
- Ein doppelter Startversuch endet nicht kontrolliert mit einem erwarteten Ergebnisobjekt.
- Stattdessen wird eine Exception geworfen und der normale Command-Flow umgangen.

Auswirkung:
- Der Command-Aufrufer bekommt potentiell kein sauberes Fehler-Feedback.
- Die Kontrolle ueber erwartete Spielzustaende ist uneinheitlich.

Empfohlene Richtung:
- Nicht mit `error(...)` abbrechen.
- Den Zustand rein ueber `WerewolfStartResult` modellieren.

## 2. `join()` erzeugt Zustand vor erfolgreicher Spielerpruefung

Betroffene Stelle:
`surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt`

Relevante Logik:
- `players[uuid] = WerewolfPlayer(uuid)`
- danach erst `uuid.toBukkitPlayer()?.addToWerewolfScoreboard() ?: return WerewolfJoinResult.Error(...)`

Risiko:
- Der Spieler wird zuerst in die interne Spielerliste eingetragen.
- Wenn der Bukkit-Player danach nicht gefunden wird, bleibt der Eintrag trotzdem bestehen.

Auswirkung:
- Inkonsistenter Zustand zwischen interner Spielverwaltung und echter Server-Session.
- Folgefehler bei Scoreboard, Rollenvergabe, Alive-Count oder Broadcasts sind moeglich.

Empfohlene Richtung:
- Zuerst den Player aufloesen.
- Erst danach `players` mutieren und Nebenwirkungen wie Scoreboard registrieren.

## 3. Spielzeit wird beim Neustart nicht zurueckgesetzt

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt`

Relevante Logik:
- `_werewolfTime += 1.seconds`
- in `stop()` kein Reset von `_werewolfTime`
- in `openLobby()` kein Reset von `_werewolfTime`

Risiko:
- Die angezeigte Spielzeit kann ueber mehrere Runden hinweg weiterlaufen.

Auswirkung:
- Falsche Anzeige im Scoreboard.
- Unsauberer Lifecycle fuer mehrere Spiele mit demselben Service-Objekt.

Empfohlene Richtung:
- `_werewolfTime` beim Oeffnen einer Lobby oder spaetestens in `stop()` auf `0.seconds` setzen.

## 4. Leader-Mapping wird beim Entfernen des Spiels nicht vollstaendig bereinigt

Betroffene Stelle:
`surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfGameManager.kt`

Relevante Logik:
- beim Erzeugen: `playerToGame[leaderUuid] = gameId`
- beim Entfernen: nur `game.players.keys.forEach { playerToGame.remove(it) }`

Risiko:
- Der Leader ist nicht Teil von `game.players`, wird aber separat in `playerToGame` eingetragen.
- Beim `removeGame()` bleibt sein Mapping daher erhalten.

Auswirkung:
- `getGameForPlayer(leaderUuid)` kann auf einen nicht mehr gueltigen Eintrag zeigen.
- Folgefehler bei Scoreboard, Dialogen oder spaeteren Spielbeitritten sind moeglich.

Empfohlene Richtung:
- Beim Entfernen auch `game.leader` aus `playerToGame` loeschen.

## 5. `openGame` erstellt ein Spiel, bevor ueberhaupt Teilnehmer existieren

Betroffene Stelle:
`surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/commands/subcommands/WerewolfOpengameCommand.kt`

Relevante Logik:
- zuerst `WerewolfGameManager.createGame(gameId, player.uniqueId)`
- danach erst Pruefung auf `potentialParticipants.isEmpty()`

Risiko:
- Es wird ein Spiel angelegt, obwohl danach festgestellt wird, dass niemand eingeladen werden kann.

Auswirkung:
- Leere oder verwaiste Lobbys koennen bestehen bleiben.
- Der Leader ist bereits an ein Spiel gebunden, obwohl das Oeffnen fachlich gescheitert ist.

Empfohlene Richtung:
- Erst potentielle Teilnehmer ermitteln.
- Das Spiel nur anlegen, wenn die Vorbedingungen erfuellt sind.

## 6. Voice-Privatchannel kann Spieler effektiv stummschalten

Betroffene Stelle:
`surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/voicechat/PrivateAudioHandler.kt`

Relevante Logik:
- Channel-Erstellung haengt an `players.first()` und dessen Voicechat-Verbindung
- geheime Spieler werden trotzdem in `secretPlayers` eingetragen
- in `onMicrophone()` wird `event.cancel()` bereits vor gesichertem Send-Pfad ausgefuehrt

Risiko:
- Wenn fuer den ersten Spieler keine Voicechat-Verbindung existiert, wird kein Channel gebaut.
- Die Events geheimer Spieler werden trotzdem abgefangen und abgebrochen.

Auswirkung:
- Werwoelfe oder andere geheime Sprecher koennen komplett stumm werden.
- Das ist besonders problematisch, weil der Fehler zur Laufzeit nicht offensichtlich sein muss.

Empfohlene Richtung:
- Channel nur dann aktivieren, wenn eine funktionierende Connection existiert.
- `secretPlayers` erst nach erfolgreicher Channel-Initialisierung setzen oder bei Fehlschlag nicht canceln.

## 7. Der aktuelle Spielzustand wird nie wirklich weiterentwickelt

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/GameState.kt`

Relevante Beobachtung:
- `GameState` kennt `DAY`, `NIGHT`, `VOTE`
- beim Start wird `_state = GameState.DAY` gesetzt
- danach gibt es im sichtbaren Code keinen echten Zustandswechsel

Risiko:
- Das Spiel wirkt gestartet, laeuft fachlich aber nicht durch echte Phasen.

Auswirkung:
- Nacht, Abstimmung, Faehigkeiten und Siegbedingungen koennen nicht korrekt aufbauen.
- Vorhandene Felder wie `votes`, `chosenVictim` oder `inLoveWith` bleiben aktuell weitgehend ungenutzt.

Empfohlene Richtung:
- Eine explizite Phasen- bzw. Rundensteuerung einfuehren.
- Regeln, Timings und Aktionen pro Phase zentral modellieren.

## 8. Viele Rolleneigenschaften sind vorbereitet, aber noch nicht fachlich verdrahtet

Betroffene Stellen:
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/WerewolfPlayer.kt`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/util/WerwolfRoles.kt`
- `surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/service/WerewolfService.kt`

Relevante Beobachtung:
- Es gibt Felder fuer `votes`, `chosenVictim`, `inLoveWith`
- Es gibt viele Rollen mit Beschreibungen und Sonderregeln
- Im aktuellen Service-Code fehlen aber die meisten dazugehoerigen Mechaniken

Risiko:
- Das Datenmodell suggeriert bereits funktionale Spielfeatures, die faktisch noch nicht umgesetzt sind.

Auswirkung:
- Spaetere Erweiterungen koennen inkonsistent werden, wenn das Modell frueher waechst als die Spiellogik.
- Bei Tests oder manueller Nutzung entsteht leicht ein falscher Eindruck ueber den Funktionsumfang.

Empfohlene Richtung:
- Entweder ungenutzte Felder vorerst reduzieren oder die fehlenden Regeln schrittweise sauber anschliessen.

## Optionaler Zusatz: aktueller Nicht-Logik-Blocker

Betroffene Stelle:
`surf-event-werewolf/src/main/kotlin/dev/slne/surf/event/werewolf/PaperMain.kt`

Beobachtung:
- Der aktuelle Build scheitert an `voicechatService.voicechatServerApi`.
- In der aufgeloesten Voicechat-API 2.5.0 existiert dieses API-Member auf `BukkitVoicechatService` nicht.

Warum hier erwaehnt:
- Das ist kein reines Logikrisiko, blockiert aber jede weitere saubere Iteration und Verifikation.
