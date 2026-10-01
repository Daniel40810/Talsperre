# Talsperre

Die Edertalsperre als animierte 3D-Szene in reinem Java 21 mit eigenem Software-Renderer. Dampf, Wasser und Lichteinfall, dazu ein Kamera-Rundumblick. Oracle 21c (Service PDBORCL, Schema DEMO, Präfix TAL_) ist seit Phase 10 optional dabei: Die App startet und läuft auch ohne Datenbank.

Stand: Phase 10 (Bedienfeld und Datenbank). Datenbank: Steckbrief der Edertalsperre mit Spatial-Geometrien (SRID 8307), Pegelreihen (Beispielwerte, keine Messwerte), Blickpunkte, Kamerafahrten mit Einstellungen und Posen, Voreinstellungen des Bedienfelds. Im Bedienfeld gibt es den Abschnitt Datenbank (Verbinden auf Knopfdruck, Voreinstellung laden, speichern, löschen, eigene Blickpunkte merken, Fenster mit Steckbrief, Pegelreihe und Fahrten) und den Regler Kraftwerksdampf. Davor Phase 9 (Zugaben: Mauerschnitt, Bauzeitraffer 1908–14, Niedrigwasser, Mineral-Lupe, Bresche 1943 mit Flutwelle, Ton) und Phase 8 (Wald, Straße, Leben).

## Starten

- In NetBeans: Projekt öffnen, Run. Hauptklasse `com.dan.talsperre.TalsperreApp`.
- Selbsttest ohne Fenster: `com.dan.talsperre.tools.Check` endet mit „Alles in Ordnung.“ (prüft auch die SQL-Skripte, ohne Datenbank).
- Standbild ohne Fenster: `com.dan.talsperre.tools.StillRender datei.png [blick 0..16] [uhrzeit] [tag] [breite] [höhe] [nebel] [ex,ey,ez,tx,ty,tz]`. Wasser: -Dtal.level=Pegel -Dtal.gate=0..1 -Dtal.wind=0..1 -Dtal.plant=0..2 (Kraftwerksdampf). Zugaben: -Dtal.mode=0|1|2 (Normal, Schnitt, Bresche), -Dtal.scene=build|breach|section -Dtal.sim=Sekunden.

Bedienung: Maus links dreht, rechts oder Umschalt verschiebt, das Rad zoomt, Doppelklick setzt den Drehpunkt, W A S D Q E bewegen ihn.

## Datenbank einrichten

Voraussetzung: Oracle 21c läuft, Dienst PDBORCL, Benutzer DEMO (Passwort de), `lib/ojdbc11.jar` ist im Projekt eingebunden. Adresse und Zugang stehen in `db/TalDb.java` (`jdbc:oracle:thin:@//localhost:1521/PDBORCL`) und lassen sich mit `-Dtal.db.url`, `-Dtal.db.user`, `-Dtal.db.pass` überschreiben.

1. In NetBeans `com.dan.talsperre.db.TalSetup` ausführen (Run File). Das legt die zehn TAL_-Tabellen, Stammdaten, Spatial-Metadaten und -Indizes an, füllt Blickpunkte und Fahrten aus dem Modell und führt den Durchstich aus. Er endet mit „Durchstich bestanden“ oder nennt die fehlgeschlagene Prüfung.
2. Argumente für `TalSetup`: `anlegen` (Standard, bricht ab, wenn TAL_-Tabellen existieren), `neu` (entfernt alles und legt neu an, eigene Voreinstellungen gehen verloren), `entfernen`, `pruefen` (nur der Durchstich).
3. In der App: Abschnitt Datenbank, „Verbinden und prüfen“.

Skripte: `db/tal_setup.sql` und `db/tal_drop.sql` (Anweisungen durch eine Zeile mit `/` getrennt, auch in SQL Developer ausführbar, dann ohne die Java-Füllung der Blickpunkte und Fahrten). Koordinaten und Umrisse sind aus dem Modell einfach nach WGS84 umgerechnet (Näherungen, keine Vermessung).

## Aufbau

```
src/com/dan/talsperre/
  TalsperreApp.java   Einstieg, FFrame
  core/               Renderer, Gelände, Materialien, Schatten
  effects/            Himmel, Sonne und Mond, Sterne, Klima, Wetter, Teilchen
  camera/             Kamerasteuerung, Pfade, Blickpunkte
  world/              Valley baut die Szene: Tal, Mauer (Dam), Seefläche, Wald (Woods), Straßen (Roads), Leben (Life), Flutband (Flood), Ruinen (Ruins)
  extras/             Zugaben: Ablauf (Extras) und Mineral-Lupe (Lupe)
  audio/              Soundscape: synthetischer Ton (javax.sound)
  water/              WaterWorks: Pegel, Ablässe, Überläufe, Strahlen, Kraftwerksdampf
  db/                 TalDb (Zugriff), TalSetup (Einrichtung und Durchstich), tal_setup.sql, tal_drop.sql
  ui/                 Szene, Bedienfeld, Abschnitt Datenbank (DbSection) und Fenster (DbDialog)
  tools/              Check, StillRender, LupeSheet
src/com/dan/forest/   Wald-Paket
src/com/dan/ground/   Boden-Paket
src/com/dan/river/    Fluss-Paket
src/com/dan/road/     Wege-Paket
```

Koordinaten: Meter, x nach Osten, y nach oben, z nach Süden, Ursprung Mitte der Mauerkrone, y = 0 ist das Stauziel (244,97 m ü. NHN). Der See liegt im Süden, die Eder fließt nach Norden ab.

Orte und Zeiten: Edertalsperre 51,186° N, 9,062° O, Mitteleuropäische Zeit (Sommerzeit 29. März bis 25. Oktober 2026).
