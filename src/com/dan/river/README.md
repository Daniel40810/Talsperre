# com.dan.river – Flüsse mit Strömung

Das Paket beschreibt, wie ein Fluss fließt und wie seine Oberfläche aussieht: Strömung, Wellen, Schaum, Wasserfarbe und Treibgut. Es braucht nur Java 21 und hängt nicht vom Geyser-Projekt ab. Gezeichnet wird mit dem eigenen Renderer: Das Paket beantwortet für jeden Ort (und jedes Pixel), wie schnell und wohin das Wasser fließt, wie die Oberfläche geneigt ist und wie viel Schaum dort liegt. Unter `demo/` liegt ein kleiner CPU-Renderer als Beispiel.

Zum Herauskopieren reicht `src/com/dan/river/`. Nur `demo/RiverDemo` braucht zusätzlich `FStyle.jar` für die Oberfläche.

## Was es kann

- **Strömung ohne Simulation:** Die Geschwindigkeit ergibt sich nach Manning aus Tiefe und Gefälle. In der Mitte fließt das Wasser am schnellsten, zu den Ufern hin langsamer.
- **Kurven:** Außen ist das Wasser schneller und tiefer (Kolk), innen liegt die flache Kiesbank. An der Oberfläche drängt das Wasser nach außen.
- **Steine:** Das Wasser teilt sich um den Stein. Davor staut es sich zur Bugwelle, dahinter strömt es im Kehrwasser zurück, und im Nachlauf schäumt es. Steine knapp unter Wasser erzeugen nur eine stehende Welle.
- **Schnellen:** Wo das Gefälle steil und das Wasser flach ist, wird es unruhig und schäumt.
- **Abfluss:** Von Niedrigwasser bis Hochwasser. Mit dem Abfluss steigen Spiegel, Tiefe und Geschwindigkeit.
- **Oberfläche:** Wellen und Schaum treiben mit dem Wasser. Dafür sorgt die Flow-Map-Technik mit zwei Phasen, die nie verzerrt und nie springt. Dazu kommen Schlieren längs der Strömung, Riffel, kurze Wellen bei Unruhe, stehende Wellen an Steinen und Kräuselung durch Wind. Der Schaum sieht aus wie Spitze, mit Bahnen, Linien und Bläschen.
- **Wasserfarbe:** Rot wird zuerst geschluckt, darum wirkt tiefes Wasser grün bis blau. Trübes Wasser streut Licht zurück und verbirgt den Grund. Die Spiegelung folgt Fresnel, und am Grund liegt Kaustik.
- **Treibgut:** Schaumflocken, Blätter und Zweige ziehen mit, kreiseln im Kehrwasser und bleiben am Ufer hängen. Von außen lassen sich Stücke hineinwerfen, etwa Laub aus dem Wald-Paket (`LeafFall.Water`).

## Benutzung

```java
// Lauf aus Stützpunkten: x, z, Wasserspiegel, halbe Breite; unterteilt auf 2,5 m; Suche bis 12 m neben dem Ufer
RiverPath path = new RiverPath(new double[][]{{0, 0, 5, 6}, {30, 80, 4.6, 7}, {10, 160, 4.2, 6}}, 2.5, 12);
FlowField flow = new FlowField(path);
flow.add(new Rock(22, 70, 0.8, 0.3));        // Mitte, Radius, Höhe über dem Wasser
flow.discharge = 1.5f;                        // Abfluss relativ zum Mittel

WaterSurface surface = new WaterSurface();
WaterOptics optics = new WaterOptics();
FlowField.Flow f = new FlowField.Flow();
WaterSurface.Surf s = new WaterSurface.Surf();

// je Wasserpixel am Ort (x, z) zur Zeit t:
if (flow.sample(x, z, f) && f.wet) {
    surface.sample(x, z, t, f, lod, s);       // lod 0..1 dämpft feine Wellen in der Ferne
    // Normale: (-s.dhdx, 1, -s.dhdz) normiert; Schaum: s.foam; Tiefe: f.depth; Spiegel: f.level
    float F = WaterOptics.fresnel(cosView);
    optics.transmit(weg, rgb);                // was vom Grund durchkommt
}

// Treibgut um die Kamera
Drift drift = new Drift(2000);
drift.step(dt, flow, camX, camZ, 80);
int n = drift.quads(xyz, rgb);                // flache Vierecke auf dem Spiegel
```

`FlowField.Flow` liefert außerdem die Grundströmung ohne die Wirbel an den Steinen (`bx`, `bz`), die Unruhe (`turb`), das Kehrwasser (`eddy`) und die Querlage im Fluss (`u`, von −1 bis 1). Eine Abfrage von Strömung und Oberfläche zusammen dauert etwa 0,5 µs.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `RiverPath` | Mittellinie mit Spiegel und Breite, Richtung, Krümmung, Gefälle; Suchgitter und Suche um den letzten Treffer |
| `FlowField` | Strömung an jedem Ort: Profil, Kurve, Gefälle, Abfluss, Steine |
| `Rock` | Hindernis im Fluss |
| `WaterSurface` | bewegte Oberfläche und Schaum (Flow-Map) |
| `WaterOptics` | Durchlässigkeit, Streuung, Fresnel, Kaustik |
| `Drift` | Treibgut |
| `RNoise` | Rauschen mit Ableitungen |

## Demo und Selbsttest

- `com.dan.river.demo.RiverDemo [blick]` zeigt einen Bach mit Kurve, Kolk und einer Schnelle mit Steinen. Die Regler steuern Abfluss, Trübe, Wind und Sonne. „Strömung zeigen“ färbt das Wasser: blau langsam, gelb schnell, violett Kehrwasser.
- Ohne Fenster rechnet `RiverDemo --bild datei.png [blick] [abfluss%] [strömung 0/1] [breite] [höhe]` ein Standbild.
- `com.dan.river.demo.RiverCheck` prüft Lauf, Strömung, Steine, Oberfläche, Treibgut, Optik und Rechenzeit und endet mit „Alles in Ordnung.“.

## Grenzen

- Das ist keine Strömungssimulation. Die Regeln geben das typische Bild wieder, erfüllen aber nicht die Massenerhaltung. Stauseen, Wasserfälle und Überschwemmungen außerhalb des Bettes kann das Paket nicht.
- Steine sind Kreise, ihre Wirkungen addieren sich. Dicht an dicht verhalten sie sich nicht wie eine echte Blockschnelle.
- Die Oberfläche bekommt nur Normalen, die Geometrie bleibt flach. Echte Wellenberge über Steinen gibt es nicht.
- Der Beispiel-Renderer ist einfach: Die Uferlinie folgt dem 0,8-m-Raster und bekommt bei Niedrigwasser Zacken. Mit halber Auflösung schafft er auf vier Kernen etwa 10 Bilder/s.
