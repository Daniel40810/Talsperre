# com.dan.ground – Gras, Blumen, Steine und Erde

Das Paket erzeugt die Bodendecke um einen Betrachter: Gräser in Horsten, Blumen, Steine, Kiesel und offene Erde. Sie bewegen sich im Wind, wechseln mit der Jahreszeit und lassen sich niedertreten. Es braucht nur Java 21 und hängt nicht vom Geyser-Projekt ab. Das Paket liefert je Bild bewegte Dreiecke mit Farbe und Normale, zeichnen muss der eigene Renderer. Unter `demo/` liegt ein kleiner CPU-Renderer als Beispiel.

Zum Herauskopieren reicht `src/com/dan/ground/`. Nur `demo/MeadowDemo` braucht zusätzlich `FStyle.jar` für die Oberfläche.

## Was es kann

- **Arten:**
  - Yellowstone: Idaho-Schwingel, Ährenweizengras, Segge, Silberlupine, Indian Paintbrush, Balsamwurzel, Glockenblume
  - Mitteleuropa: Wiesen-Rispengras, Margerite, Klatschmohn, Kornblume, Löwenzahn
  - dazu Kiesel, Steine und offene Erde

  Alle Werte sind öffentlich und lassen sich ändern.
- **Biome:** `Biome.yellowstone()`, `europe()` und `gravelBar()` mischen die Arten mit Dichten je Quadratmeter. Jede Art zieht ihre Dichte aus einer von vier Schichten: Gras, Blumen, Steine, Erde. Manche Arten mögen es feucht, etwa Seggen, andere trocken, etwa die Balsamwurzel.
- **Standort:** Über die Schnittstelle `Site` sagt man, wie hoch der Boden ist und wie dicht jede Schicht dort ist. So legt man Pfade, Blumeninseln, Geröllfelder, Ufer oder heiße Stellen ohne Bewuchs fest.
- **Wind:** Böen laufen als Wellen über die Wiese. Die Halme biegen sich nach Höhe und Steifheit und zittern, die Blüten nicken, die Wurzeln bleiben am Ort.
- **Niedertreten:** Gehende (`pushers`) drücken das Gras zur Seite. Es richtet sich in einigen Sekunden wieder auf, dadurch entstehen sichtbare Spuren.
- **Jahreszeit:** Die Gräser werden im Frühling grün, im Hochsommer golden und im Winter fahl. Jede Blume blüht in ihrem eigenen Zeitfenster, die Blüte wächst auf und fällt danach zusammen. Unter Schnee liegen Gras und Blumen, Steine tragen eine Haube.
- **Streuung:** Kacheln von 8 m werden erst erzeugt, wenn sie gebraucht werden, und zwar aus einem Startwert, also immer gleich. In der Ferne wird ausgedünnt, zum Ausgleich werden die Halme dort breiter. Am Rand der Reichweite verblasst alles in den Boden dahinter (`Batch.fade`).
- **Grasnarbe:** `Meadow.carpet(x, z, out)` liefert die Bodenfarbe zwischen den Pflanzen, passend zur Jahreszeit. Damit wirken Lücken nicht wie nackter Boden.

## Benutzung

```java
Site site = new Site() {
    public float height(double x, double z) { return terrain.height(x, z); }
    public void cover(double x, double z, float[] o) {   // Gras, Blumen, Steine, Erde, Feuchte (je 0..1)
        o[0] = 0.8f; o[1] = 0.3f; o[2] = 0.1f; o[3] = 0; o[4] = 0.5f;
    }
};
Meadow meadow = new Meadow(Biome.yellowstone(), site);
meadow.radius = 30;
meadow.wind.speed = 6; meadow.wind.direction = 45;
meadow.setSeason(185, 0);                       // Tag im Jahr, Schnee 0..1

// je Bild:
meadow.pushers = new float[]{px, pz, 0.5f, 1};  // x, z, Radius, Stärke je Gehendem
meadow.pusherCount = 1;
meadow.update(t, camX, camZ);
Batch b = meadow.batch;                         // b.nv Ecken (xyz, nrm, rgb, solid, fade), b.nt Dreiecke (tri)
```

Halme und Blüten sind dünn (`solid = 0`) und werden von beiden Seiten beleuchtet, Steine und Erde nur von vorn (`solid = 1`). `fade` mischt die Farbe einer Ecke mit dem, was schon im Bild steht.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Plant` | Art mit Form, Farben, Blütezeit; Voreinstellungen |
| `Biome` | Arten, Dichten, Schichten, Feuchte |
| `Site` | Höhe und Bedeckung (Schnittstelle), Beispiel `Site.MEADOW` |
| `Breeze` | Wind mit wandernden Böen |
| `Meadow` | Kacheln, Formen (Horste, Blumen, Steine, Erde), Jahreszeit, Ausdünnung, Bewegung, Niedertreten |
| `Batch` | Ergebnis je Bild |
| `GNoise` | Rauschen |

## Demo und Selbsttest

- `com.dan.ground.demo.MeadowDemo` zeigt einen Hang mit Trampelpfad, Blumeninseln und Geröll. Ein Spaziergänger geht den Pfad entlang. Die Regler steuern Boden, Tag, Schnee, Wind, Böen, Richtung und Reichweite.
- Ohne Fenster rechnet `MeadowDemo --bild datei.png [biom 0..2] [tag] [wind] [blick 0..2] [breite] [höhe]` ein Standbild.
- `com.dan.ground.demo.GroundCheck` prüft Streuung, Bedeckung, Ausdünnung, Wind, Böen, Niedertreten, Jahreszeit, Schnee und Rechenzeit und endet mit „Alles in Ordnung.“.

## Grenzen

- Die Pflanzen sind einfache Formen: Halme aus drei Dreiecken, Blüten als Fächer oder Ähre. Einzelne Blätter an Stängeln und Samenstände gibt es nicht.
- Schatten werfen die Pflanzen nicht selbst. Der Renderer entscheidet, ob sie im Schatten liegen.
- Die Farben und Blütezeiten sind nach Beschreibungen angenähert, nicht vermessen.
- Die Rechenzeit hängt vor allem vom Zeichnen ab. Das Paket selbst braucht für eine Wiese von 30 m Reichweite etwa 10 bis 20 ms je Bild auf vier Kernen, bei 400 000 bis 600 000 Dreiecken.
