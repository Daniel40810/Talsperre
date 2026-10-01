# com.dan.road – Pfad, Feldweg, Straße, Autobahn

Das Paket legt Wege in ein Gelände und belebt sie. Es gibt vier Arten: Pfade, Feldwege, Straßen und Autobahnen. Das Paket plant die Trasse mit Steigung, Querneigung und Brücken und formt dafür das Gelände. Es baut die Fahrbahn mit Markierung und Ausstattung und lässt darauf Verkehr fahren. Das Wetter macht die Fahrbahn nass oder verschneit sie, nachts leuchten die Scheinwerfer. Es braucht nur Java 21 und hängt nicht vom Geyser-Projekt ab.

Das Paket liefert je Bild Dreiecke mit Farbe, Normale, Glanz und Art, zeichnen muss der eigene Renderer. Unter `demo/` liegt ein kleiner CPU-Renderer als Beispiel.

Zum Herauskopieren reicht `src/com/dan/road/`. Nur `demo/RoadDemo` braucht zusätzlich `FStyle.jar` für die Oberfläche.

## Was es kann

- **Wegarten:** `WayType.path()`, `track()`, `road()` und `motorway()` sind die vier Voreinstellungen:
  - **Pfad:** festgetretene Erde, mit schwankender Breite und Lage.
  - **Feldweg:** zwei Fahrspuren aus Kies, dazwischen Gras.
  - **Landstraße:** zwei Fahrstreifen mit gelber Doppellinie (auf Geraden gestrichelt), Leitpfosten, Schneestangen und Schildern.
  - **Autobahn:** je Richtung zwei Fahrstreifen und ein Seitenstreifen, Mittelstreifen mit Schutzplanke, europäische Markierung.

  Alle Werte sind öffentlich und lassen sich ändern: Querschnitt, Belag, Alter, Farben, Steigung und Tempolimit.
- **Trasse:** Die Achse läuft als Catmull-Rom-Kurve durch Stützpunkte und wird alle 2 m abgetastet. Offene und geschlossene Wege (Rundkurse) sind möglich.
  - Das Höhenprofil folgt dem Boden, geglättet und mit begrenzter Steigung.
  - Über Wasser hält es die lichte Höhe ein. Hoch über Wasser oder einer Senke wird der Weg zur Brücke, mit Pfeilern, Brüstung und Geländer. Brücken von Pfaden und Feldwegen sind aus Holz.
  - In Kurven neigt sich die Fahrbahn nach innen. Aus Krümmung und Querneigung folgt die Geschwindigkeit, mit der man die Kurve fährt.
- **Gelände:** `Network.shape` ebnet den Boden unter der Fahrbahn. Daneben entstehen Berme, Damm und Einschnitt, weiter außen bleibt der Boden, wie er ist. Unter Brücken bleibt das Tal frei. `Network.locate` sagt für jeden Punkt, welcher Weg dort liegt, wie weit quer zur Achse und wie hoch die Fahrbahn ist. Damit lassen sich zum Beispiel Bäume und Gras fernhalten.
- **Fahrbahn:** Die Fahrbahn wird in Stücken von 32 m gebaut, in drei Detailstufen (0,5 m, 2 m und 8 m längs), und zwar erst dann, wenn sie gebraucht wird.
  - Alter Asphalt hat Flicken, Risse und polierte Radspuren. Feldweg und Pfad haben Spurrinnen.
  - Eine Schürze am Rand deckt Stufen im groben Geländegitter.
  - Dazu kommen Markierung, Leitpfosten mit Rückstrahlern, Schutzplanken, Schilder und Schneestangen.
- **Verkehr:** Autos, SUVs, Pick-ups, Wohnmobile, Busse, Lastwagen, Motorräder, Radfahrer, Traktoren und Wanderer sind unterwegs.
  - Die Fahrzeuge folgen einander nach dem Intelligent Driver Model und bremsen rechtzeitig vor Kurven.
  - Auf der Autobahn wechseln sie nach MOBIL die Spur, mit Blinker. Lastwagen fahren rechts.
  - Autos überholen Radfahrer mit Abstand. Kommt Gegenverkehr, fahren sie hinterher.
  - Auf Feldweg und Pfad weichen sich Entgegenkommende aus.
  - Hindernisse auf der Fahrbahn (`Traffic.obstacles`, etwa eine Bisonherde) halten den Verkehr an. Das erste Fahrzeug schaltet die Warnblinker ein.
  - Die Fahrzeuge nicken beim Bremsen und neigen sich in Kurven, auf dem Feldweg federn sie. Räder drehen sich, Wanderer gehen mit schwingenden Beinen, Radfahrer treten.
- **Wetter:** Im Regen wird die Fahrbahn in wenigen Minuten nass und glänzt, und Pfützen laufen in den Mulden voll. Danach trocknet sie langsam, in den Radspuren zuerst. Bei Regen zittern die Pfützen.
  - Bei Schnee sind Straßen geräumt, mit festgefahrenem Schnee, Matsch in den Radspuren und Schneewällen am Rand. Feldwege und Pfade liegen unter Schnee.
  - Hinter Fahrzeugen steigt auf trockenem Kies und trockener Erde Staub auf. Auf nasser Fahrbahn gibt es Gischt, im Schnee aufgewirbelten Schnee.
- **Licht:** In der Dämmerung, nachts und bei starkem Regen fahren alle mit Licht.
  - Scheinwerfer, Rück-, Brems- und Blinklichter leuchten selbst (`Batch.LAMP`) und haben Lichthöfe (`Batch.GLOW`).
  - `Roads.illuminate` liefert das Licht der Scheinwerfer auf einer Fläche.
  - Rückstrahler an den Leitpfosten leuchten auf, wenn ein Scheinwerfer sie trifft und man nahe hinter dem Licht steht.

## Benutzung

```java
Ground ground = new Ground() {
    public float height(double x, double z) { return terrain.height(x, z); }
    public float water(double x, double z)  { return river.levelAt(x, z); }   // NaN: kein Wasser
};
Network net = new Network();
net.add(new Way(WayType.road(), false, 0, 0, 400, 120, 900, 80));      // x, z, x, z, …
net.add(new Way(WayType.path(), true, /* Rundweg */ 50, 50, 90, 70, 70, 110));
net.build(ground);                                // Trasse, Profil, Brücken, Suchgitter

Ground shaped = net.shaped(ground);               // geformtes Gelände (Damm, Einschnitt)
Roads roads = new Roads(net, shaped, 42);         // Geometrie und Verkehr
roads.weather.rain = 0.6f; roads.weather.dark = 0.8f;

// je Bild:
roads.traffic.obstacles(new float[]{x, z, 1.8f}, 1);   // x, z, Radius je Hindernis
roads.update(t, dt, camX, camY, camZ);
Batch b = roads.batch;          // b.nv Ecken (xyz, nrm, rgb, gloss, fade, kind), b.nt Dreiecke (tri)
roads.illuminate(x, y, z, nx, ny, nz, rgb);       // Scheinwerferlicht auf eine Fläche addieren
```

Die Arten in `Batch.kind` sind:

- `SOLID`: fest, nur von vorn beleuchtet.
- `THIN`: dünn, von beiden Seiten beleuchtet.
- `LAMP`: leuchtet selbst, die Farbe ist die Strahlung.
- `GLOW`: wird zum Bild addiert und verdeckt nichts.

`gloss` ist der Glanz (0 bis 1), mit dem der Zeichner Himmel und Licht spiegelt. `fade` mischt eine Ecke mit dem, was schon im Bild steht, etwa bei Staub oder am Rand eines Pfades.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `WayType` | Art des Weges: Querschnitt, Belag, Markierung, Ausstattung, Trassierung, Farben |
| `Way` | Achse, Höhenprofil, Querneigung, Brücken, Kurvengeschwindigkeit, Punkte auf der Fahrbahn |
| `Network` | alle Wege, Suchgitter, Abfragen, Formung des Geländes |
| `Deck` | Geometrie eines Stücks: Fahrbahn, Schürze, Markierung, Ausstattung, Brücken, Schneewälle |
| `Traffic`, `Mover`, `Vehicle` | Fahrstreifen, Fahrverhalten, Spurwechsel, Hindernisse; Verkehrsteilnehmer und ihre Formen |
| `Weather` | Regen, Schnee, Dunkelheit; Nässe, Radspuren, Pfützen mit Trägheit |
| `Dust` | Staub, Gischt und Schnee hinter den Fahrzeugen |
| `Roads` | alles zusammen: Stücke um die Kamera, Wetter auf der Fahrbahn, Fahrzeuge, Lichter, Staub |
| `Batch`, `Ground`, `WNoise` | Ergebnis je Bild, Boden und Wasser, Rauschen |

## Demo und Selbsttest

- `com.dan.road.demo.RoadDemo` zeigt eine Hügellandschaft mit Bach. Darin liegen eine Autobahn, eine Landstraße mit zwei Brücken, ein Feldweg und ein Pfad über den Hügel, alle als Rundkurse mit Verkehr.
  - Die Blicke gehen über die Landschaft, auf jeden Weg, und man kann mit einem Auto mitfahren.
  - Die Regler steuern Tageszeit, Regen, Schnee, Zeitraffer, Wind und Verkehrsdichte. „Bisons auf der Straße“ stellt eine Herde auf die Fahrbahn.
- Ohne Fenster rechnet `RoadDemo --bild datei.png [blick 0..5] [regen 0..1] [nacht 0..1] [schnee 0..1] [breite] [höhe] [herde]` ein Standbild.
- `com.dan.road.demo.RoadCheck` prüft Folgendes und endet mit „Alles in Ordnung.“:
  - Steigung, Brücken, lichte Höhe und Querneigung
  - Abfragen und die Formung des Geländes
  - die Detailstufen
  - den Verkehr: keine Auffahrunfälle, Kurvengeschwindigkeit, Spurwechsel, Lastwagen rechts, Stau vor Hindernissen und das Weiterfahren danach
  - Nässe und Abtrocknen, Scheinwerfer, Staub, Gischt und Rechenzeit

## Grenzen

- Kreuzungen und Einmündungen gibt es nicht. Wege, die sich schneiden, liegen übereinander.
- Die Fahrzeuge sind einfache Körper aus Prismen und Quadern, ohne Innenraum und ohne Spiegelung im Lack außer dem Himmel.
- Die Farben liegen je Ecke. Feine Muster wie Risse sieht man nur in der nächsten Detailstufe.
- Tunnel gibt es nicht, tiefe Einschnitte bleiben Einschnitte.
- Das Fahrverhalten ist ein Modell (IDM, MOBIL) und keine Fahrphysik. Unfälle gibt es nicht.
- Das Paket selbst braucht je Bild etwa 3 bis 8 ms: Stücke zusammensetzen, Wetter, Verkehr, Staub.
