package com.dan.forest;

import java.util.List;

/**
 * Wie eine Baumart wächst und aussieht: Höhe, Stamm, Äste je Ordnung, Form der Krone, Blätter oder
 * Nadeln, Farben durchs Jahr und wie biegsam sie im Wind ist. Die Werte sind öffentlich und dürfen
 * angepasst werden; die fertigen Arten ({@link #ALL}) sind nach Wuchsbeschreibungen der Forstkunde
 * eingestellt und genähert, nicht vermessen.
 * <p>
 * Farben sind linear (nicht sRGB), 0..1.
 */
public final class Species {
    /**
     * Form der Krone: wie lang die Äste erster Ordnung je nach Höhe in der Krone sind. SPIRE ist der
     * schmale Turm der Hochlagentannen (fast gleich breit, oben spitz), UMBRELLA der flache Schirm alter
     * Kiefern und Tannen (unten kurz, oben breit).
     */
    public enum Crown { CONICAL, NARROW, OVOID, ROUND, SPREADING, SPIRE, UMBRELLA }

    /** Blattform: breites Blatt, rundes (Espe), gelapptes (Eiche), kleines (Birke), Nadelbüschel. */
    public enum Leaf { OVAL, ROUND, LOBED, SMALL, NEEDLES }

    public final String name, latin;
    public boolean conifer, deciduous;
    /** Höhe von bis (m) eines ausgewachsenen Baums. */
    public float heightMin, heightMax;
    /** Stammdurchmesser am Fuß je Meter Höhe. */
    public float trunkRatio = 0.02f;
    /** Bis zu welchem Anteil der Höhe der Stamm als eigener Leittrieb geht (1 = bis zur Spitze; Eiche teilt sich früher). */
    public float trunkEnd = 1f;
    /** Ab welchem Anteil der Höhe Äste ansetzen (darunter ist der Stamm kahl). */
    public float crownBase = 0.35f;
    public Crown crown = Crown.OVOID;
    /** Stamm: Neigung und Krümmung (Rauschen), Anzahl der Glieder. */
    public float trunkWobble = 0.04f;
    public int trunkSegments = 10;
    /** Ordnungen der Äste (1 = nur Äste am Stamm, 3 = Äste, Zweige, Triebe). */
    public int levels = 3;
    /** Je Ordnung 1..levels: Anzahl Kinder je Eltern, Abgangswinkel (Grad), Streuung, Längenverhältnis, Ansatz ab (0..1). */
    public float[] count = {0, 24, 7, 5}, angle = {0, 70, 45, 40}, angleVar = {0, 12, 15, 15}, lengthRatio = {0, 0.45f, 0.45f, 0.5f},
            start = {0, 0, 0.2f, 0.25f};
    /** Je Ordnung: Richtung nach oben (+) oder hängend (−) je Meter Länge. */
    public float[] tropism = {0, 0.02f, 0.0f, 0.05f};
    /** Je Ordnung: Dicke des Kindes zum Elternteil an der Ansatzstelle. */
    public float[] radiusRatio = {0, 0.55f, 0.5f, 0.5f};
    /** Quirle (Nadelbäume): so viele Äste auf einer Höhe; 0 = schraubig im Goldenen Winkel. */
    public int whorl = 0;
    /** Ab welcher Ordnung Blätter wachsen, und wie viele je Meter Zweig. */
    public int leafLevel = 3;
    public float leafDensity = 14f;
    public Leaf leaf = Leaf.OVAL;
    /** Blattlänge (m) und Breite im Verhältnis; Nadelbäume: Länge eines Nadelbüschels entlang des Zweigs. */
    public float leafSize = 0.09f, leafAspect = 0.6f;
    /**
     * Laubbäume: Durchmesser eines Blattbüschels (m). Ein echter Baum hat Hunderttausende Blätter; so
     * viele Dreiecke zeichnet kein Software-Renderer. Darum steht jede Blattstelle für ein Büschel aus
     * vielen Blättern, gezeichnet als flacher Fächer mit gezacktem Rand (Blattspitzen), wie die
     * Blattkarten in Spiele-Engines, nur als Geometrie statt als Textur. Die Zacken folgen der
     * Blattform ({@link #leaf}).
     */
    public float cluster = 0.45f;
    /** Wie biegsam im Wind: Stamm, Äste, Blattflattern (Espe zittert besonders). */
    public float flexTrunk = 1f, flexBranch = 1f, flutter = 1f;
    /** Rinde (linear). */
    public float[] bark = {0.09f, 0.07f, 0.055f};
    /** Rinde hell mit dunklen Querbändern (Birke) oder glatt grünlich (Espe): Anteil. */
    public float barkMarks = 0;
    public float[] barkMark = {0.02f, 0.02f, 0.02f};
    /** Rinde im oberen Stamm (Waldkiefer: orange „Spiegelrinde“) ab diesem Anteil der Höhe; null = wie unten. */
    public float[] barkTop;
    public float barkTopFrom = 0.5f;
    /**
     * Zapfen: wie viele je Ast erster Ordnung (0 = keine), Länge (m), Farbe; aufrecht (Tannen, Zirbe) oder
     * hängend (Fichten); nur im oberen Teil der Krone (Anteil, 1 = überall).
     */
    public float cones;
    public float coneSize = 0.06f, coneZone = 0.5f;
    public float[] coneColor = {0.22f, 0.13f, 0.07f};
    public boolean coneUpright;
    /** Nadelbäume: Farbe der Maitriebe (die neuen, hellen Spitzen im Frühsommer); null = keine. */
    public float[] shoots;
    /** Blattfarben (linear): Austrieb, Sommer, Herbst (einige Töne), Nadeln das ganze Jahr = Sommer. */
    public float[] spring = {0.12f, 0.30f, 0.04f}, summer = {0.05f, 0.16f, 0.03f};
    public float[][] autumn = {{0.35f, 0.20f, 0.02f}};
    /** Laubjahr (Tag im Jahr, Nordhalbkugel): Austrieb, Beginn der Färbung, volle Färbung, kahl. */
    public int leafOut = 120, colorStart = 255, colorFull = 280, bare = 305;

    public Species(String name, String latin) { this.name = name; this.latin = latin; }

    // ------------------------------------------------------------ die Arten

    /** Drehkiefer (Pinus contorta latifolia): schlank und gerade, Krone im oberen Drittel, kurze Äste; Hauptbaum in Yellowstone. */
    public static Species lodgepolePine() {
        Species s = new Species("Drehkiefer", "Pinus contorta");
        s.conifer = true; s.heightMin = 14; s.heightMax = 26; s.trunkRatio = 0.014f; s.crownBase = 0.55f; s.crown = Species.Crown.NARROW;
        s.trunkWobble = 0.02f; s.trunkSegments = 12; s.levels = 2; s.whorl = 4;
        s.count = new float[]{0, 110, 5}; s.angle = new float[]{0, 82, 40}; s.angleVar = new float[]{0, 10, 15};
        s.lengthRatio = new float[]{0, 0.12f, 0.45f}; s.start = new float[]{0, 0, 0.15f};
        s.tropism = new float[]{0, -0.04f, 0.1f}; s.radiusRatio = new float[]{0, 0.35f, 0.5f};
        s.leafLevel = 1; s.leafDensity = 6; s.leaf = Leaf.NEEDLES; s.leafSize = 0.4f;
        s.flexTrunk = 0.6f; s.flexBranch = 0.7f; s.flutter = 0.3f;
        s.bark = new float[]{0.10f, 0.075f, 0.055f};
        s.summer = new float[]{0.035f, 0.085f, 0.03f}; s.spring = s.summer; s.shoots = new float[]{0.11f, 0.22f, 0.08f};
        s.leafOut = 160;
        s.cones = 1.5f; s.coneSize = 0.045f; s.coneColor = new float[]{0.20f, 0.14f, 0.08f}; s.coneZone = 0.6f;
        return s;
    }

    /** Espe (Populus tremuloides): schlank, weißgrüne Rinde, runde Blätter an flachen Stielen, die im leisesten Wind zittern; im Herbst goldgelb. */
    public static Species aspen() {
        Species s = new Species("Espe", "Populus tremuloides");
        s.deciduous = true; s.heightMin = 10; s.heightMax = 20; s.trunkRatio = 0.016f; s.crownBase = 0.5f; s.crown = Crown.OVOID;
        s.trunkWobble = 0.05f; s.levels = 3;
        s.count = new float[]{0, 16, 6, 4}; s.angle = new float[]{0, 45, 45, 40}; s.lengthRatio = new float[]{0, 0.32f, 0.45f, 0.45f};
        s.tropism = new float[]{0, 0.05f, 0.02f, 0.02f};
        s.leafLevel = 2; s.leafDensity = 14; s.leaf = Leaf.ROUND; s.leafSize = 0.06f; s.leafAspect = 0.95f; s.cluster = 0.45f;
        s.flexTrunk = 1.2f; s.flexBranch = 1.3f; s.flutter = 3.0f;
        s.bark = new float[]{0.55f, 0.58f, 0.50f}; s.barkMarks = 0.25f; s.barkMark = new float[]{0.05f, 0.05f, 0.04f};
        s.spring = new float[]{0.18f, 0.38f, 0.06f}; s.summer = new float[]{0.08f, 0.22f, 0.04f};
        s.autumn = new float[][]{{0.75f, 0.52f, 0.02f}, {0.80f, 0.45f, 0.01f}, {0.62f, 0.50f, 0.03f}};
        s.leafOut = 150; s.colorStart = 250; s.colorFull = 268; s.bare = 290;
        return s;
    }

    /** Douglasie (Pseudotsuga menziesii): kegelförmig, dicht, die unteren Äste hängen. */
    public static Species douglasFir() {
        Species s = new Species("Douglasie", "Pseudotsuga menziesii");
        s.conifer = true; s.heightMin = 20; s.heightMax = 40; s.trunkRatio = 0.022f; s.crownBase = 0.2f; s.crown = Crown.CONICAL;
        s.trunkWobble = 0.015f; s.trunkSegments = 14; s.levels = 2; s.whorl = 5;
        s.count = new float[]{0, 150, 7}; s.angle = new float[]{0, 80, 45}; s.lengthRatio = new float[]{0, 0.17f, 0.4f};
        s.start = new float[]{0, 0, 0.1f}; s.tropism = new float[]{0, -0.06f, 0.05f}; s.radiusRatio = new float[]{0, 0.3f, 0.5f};
        s.leafLevel = 1; s.leafDensity = 5; s.leaf = Leaf.NEEDLES; s.leafSize = 0.45f;
        s.flexTrunk = 0.5f; s.flexBranch = 0.8f; s.flutter = 0.3f;
        s.bark = new float[]{0.12f, 0.07f, 0.045f};
        s.summer = new float[]{0.03f, 0.075f, 0.035f}; s.spring = s.summer; s.shoots = new float[]{0.11f, 0.23f, 0.09f};
        s.leafOut = 150;
        s.cones = 2; s.coneSize = 0.08f; s.coneColor = new float[]{0.23f, 0.14f, 0.08f}; s.coneZone = 0.5f;
        return s;
    }

    /** Stieleiche (Quercus robur): breite, knorrige Krone, der Stamm teilt sich früh; Blätter gelappt, im Herbst braun. */
    public static Species oak() {
        Species s = new Species("Eiche", "Quercus robur");
        s.deciduous = true; s.heightMin = 18; s.heightMax = 30; s.trunkRatio = 0.035f; s.trunkEnd = 0.55f; s.crownBase = 0.3f;
        s.crown = Crown.SPREADING; s.trunkWobble = 0.08f; s.levels = 3;
        s.count = new float[]{0, 9, 7, 5}; s.angle = new float[]{0, 50, 50, 45}; s.angleVar = new float[]{0, 18, 22, 20};
        s.lengthRatio = new float[]{0, 0.45f, 0.5f, 0.45f}; s.start = new float[]{0, 0.1f, 0.2f, 0.25f};
        s.tropism = new float[]{0, 0.01f, -0.02f, 0.03f}; s.radiusRatio = new float[]{0, 0.6f, 0.5f, 0.5f};
        s.leafLevel = 3; s.leafDensity = 15; s.leaf = Leaf.LOBED; s.leafSize = 0.11f; s.leafAspect = 0.55f; s.cluster = 0.8f;
        s.flexTrunk = 0.4f; s.flexBranch = 0.7f; s.flutter = 0.8f;
        s.bark = new float[]{0.08f, 0.07f, 0.06f};
        s.spring = new float[]{0.14f, 0.30f, 0.05f}; s.summer = new float[]{0.045f, 0.13f, 0.025f};
        s.autumn = new float[][]{{0.25f, 0.13f, 0.03f}, {0.32f, 0.20f, 0.04f}, {0.20f, 0.14f, 0.05f}};
        s.leafOut = 125; s.colorStart = 275; s.colorFull = 300; s.bare = 330;
        return s;
    }

    /** Rotbuche (Fagus sylvatica): dichte, eiförmige Krone, glatte graue Rinde; im Herbst kupferrot. */
    public static Species beech() {
        Species s = new Species("Buche", "Fagus sylvatica");
        s.deciduous = true; s.heightMin = 20; s.heightMax = 35; s.trunkRatio = 0.022f; s.trunkEnd = 0.85f; s.crownBase = 0.35f;
        s.crown = Crown.OVOID; s.trunkWobble = 0.03f; s.levels = 3;
        s.count = new float[]{0, 14, 8, 5}; s.angle = new float[]{0, 50, 45, 40}; s.lengthRatio = new float[]{0, 0.38f, 0.5f, 0.45f};
        s.tropism = new float[]{0, 0.02f, -0.01f, 0.0f};
        s.leafLevel = 3; s.leafDensity = 15; s.leaf = Leaf.OVAL; s.leafSize = 0.08f; s.leafAspect = 0.6f; s.cluster = 0.7f;
        s.flexTrunk = 0.5f; s.flexBranch = 0.8f; s.flutter = 0.8f;
        s.bark = new float[]{0.20f, 0.20f, 0.19f};
        s.spring = new float[]{0.20f, 0.42f, 0.06f}; s.summer = new float[]{0.05f, 0.15f, 0.03f};
        s.autumn = new float[][]{{0.45f, 0.18f, 0.03f}, {0.55f, 0.28f, 0.04f}, {0.40f, 0.12f, 0.02f}};
        s.leafOut = 120; s.colorStart = 280; s.colorFull = 298; s.bare = 320;
        return s;
    }

    /** Hängebirke (Betula pendula): schlank, weiße Rinde mit schwarzen Rissen, dünne hängende Zweige, kleine Blätter; im Herbst gelb. */
    public static Species birch() {
        Species s = new Species("Birke", "Betula pendula");
        s.deciduous = true; s.heightMin = 12; s.heightMax = 25; s.trunkRatio = 0.013f; s.crownBase = 0.4f; s.crown = Crown.OVOID;
        s.trunkWobble = 0.05f; s.levels = 3;
        s.count = new float[]{0, 16, 7, 6}; s.angle = new float[]{0, 40, 50, 30}; s.lengthRatio = new float[]{0, 0.38f, 0.55f, 0.6f};
        s.tropism = new float[]{0, 0.03f, -0.10f, -0.35f}; s.radiusRatio = new float[]{0, 0.5f, 0.45f, 0.45f};
        s.leafLevel = 3; s.leafDensity = 15; s.leaf = Leaf.SMALL; s.leafSize = 0.05f; s.leafAspect = 0.7f; s.cluster = 0.5f;
        s.flexTrunk = 1.0f; s.flexBranch = 1.3f; s.flutter = 1.6f;
        s.bark = new float[]{0.62f, 0.62f, 0.58f}; s.barkMarks = 0.4f; s.barkMark = new float[]{0.02f, 0.02f, 0.02f};
        s.spring = new float[]{0.20f, 0.45f, 0.07f}; s.summer = new float[]{0.07f, 0.20f, 0.035f};
        s.autumn = new float[][]{{0.70f, 0.55f, 0.04f}, {0.60f, 0.48f, 0.05f}};
        s.leafOut = 115; s.colorStart = 265; s.colorFull = 285; s.bare = 310;
        return s;
    }

    /** Gemeine Fichte (Picea abies): spitzer Kegel, dichte Quirle, bei älteren Bäumen hängen die Zweige an den Ästen („Kammfichte“). */
    public static Species spruce() {
        Species s = new Species("Fichte", "Picea abies");
        s.conifer = true; s.heightMin = 20; s.heightMax = 40; s.trunkRatio = 0.02f; s.crownBase = 0.12f; s.crown = Crown.CONICAL;
        s.trunkWobble = 0.012f; s.trunkSegments = 14; s.levels = 2; s.whorl = 5;
        s.count = new float[]{0, 170, 7}; s.angle = new float[]{0, 85, 70}; s.lengthRatio = new float[]{0, 0.15f, 0.35f};
        s.start = new float[]{0, 0, 0.1f}; s.tropism = new float[]{0, -0.02f, -0.3f}; s.radiusRatio = new float[]{0, 0.3f, 0.45f};
        s.leafLevel = 1; s.leafDensity = 5; s.leaf = Leaf.NEEDLES; s.leafSize = 0.42f;
        s.flexTrunk = 0.5f; s.flexBranch = 0.9f; s.flutter = 0.3f;
        s.bark = new float[]{0.11f, 0.07f, 0.05f};
        s.summer = new float[]{0.02f, 0.06f, 0.03f}; s.spring = s.summer; s.shoots = new float[]{0.11f, 0.24f, 0.08f};
        s.leafOut = 135;
        s.cones = 2.5f; s.coneSize = 0.12f; s.coneColor = new float[]{0.24f, 0.13f, 0.07f}; s.coneZone = 0.35f;
        return s;
    }

    // ------------------------------------------------------------ weitere Nadelbäume

    /** Engelmann-Fichte (Picea engelmannii): schmal kegelförmig, dicht, blaugrün; hängende Zapfen; in feuchten Lagen Yellowstones. */
    public static Species engelmannSpruce() {
        Species s = new Species("Engelmann-Fichte", "Picea engelmannii");
        s.conifer = true; s.heightMin = 20; s.heightMax = 35; s.trunkRatio = 0.019f; s.crownBase = 0.06f; s.crown = Crown.CONICAL;
        s.trunkWobble = 0.012f; s.trunkSegments = 14; s.levels = 2; s.whorl = 5;
        s.count = new float[]{0, 175, 7}; s.angle = new float[]{0, 86, 70}; s.lengthRatio = new float[]{0, 0.12f, 0.35f};
        s.start = new float[]{0, 0, 0.1f}; s.tropism = new float[]{0, -0.035f, -0.35f}; s.radiusRatio = new float[]{0, 0.3f, 0.45f};
        s.leafLevel = 1; s.leafDensity = 5.5f; s.leaf = Leaf.NEEDLES; s.leafSize = 0.36f;
        s.flexTrunk = 0.55f; s.flexBranch = 0.9f; s.flutter = 0.3f;
        s.bark = new float[]{0.14f, 0.09f, 0.07f};
        s.summer = new float[]{0.025f, 0.058f, 0.048f}; s.spring = s.summer; s.shoots = new float[]{0.10f, 0.22f, 0.13f};
        s.leafOut = 160;
        s.cones = 2.5f; s.coneSize = 0.055f; s.coneColor = new float[]{0.24f, 0.13f, 0.07f}; s.coneZone = 0.45f;
        return s;
    }

    /** Felsengebirgs-Tanne (Abies lasiocarpa): schmaler, spitzer Turm bis zum Boden beastet, dunkel; aufrechte violette Zapfen. */
    public static Species subalpineFir() {
        Species s = new Species("Felsengebirgs-Tanne", "Abies lasiocarpa");
        s.conifer = true; s.heightMin = 14; s.heightMax = 24; s.trunkRatio = 0.017f; s.crownBase = 0.02f; s.crown = Crown.SPIRE;
        s.trunkWobble = 0.01f; s.trunkSegments = 14; s.levels = 2; s.whorl = 5;
        s.count = new float[]{0, 190, 8}; s.angle = new float[]{0, 88, 60}; s.lengthRatio = new float[]{0, 0.085f, 0.4f};
        s.start = new float[]{0, 0, 0.1f}; s.tropism = new float[]{0, -0.015f, 0.05f}; s.radiusRatio = new float[]{0, 0.3f, 0.45f};
        s.leafLevel = 1; s.leafDensity = 6; s.leaf = Leaf.NEEDLES; s.leafSize = 0.32f;
        s.flexTrunk = 0.5f; s.flexBranch = 0.8f; s.flutter = 0.25f;
        s.bark = new float[]{0.20f, 0.20f, 0.19f};
        s.summer = new float[]{0.018f, 0.048f, 0.030f}; s.spring = s.summer; s.shoots = new float[]{0.09f, 0.20f, 0.10f};
        s.leafOut = 165;
        s.cones = 2; s.coneSize = 0.08f; s.coneColor = new float[]{0.14f, 0.06f, 0.13f}; s.coneUpright = true; s.coneZone = 0.2f;
        return s;
    }

    /** Weißstämmige Kiefer (Pinus albicaulis): niedrig, rundlich, oft mehrstämmig, helle Rinde; an der Waldgrenze. */
    public static Species whitebarkPine() {
        Species s = new Species("Weißstämmige Kiefer", "Pinus albicaulis");
        s.conifer = true; s.heightMin = 10; s.heightMax = 17; s.trunkRatio = 0.035f; s.trunkEnd = 0.7f; s.crownBase = 0.3f; s.crown = Crown.ROUND;
        s.trunkWobble = 0.07f; s.trunkSegments = 9; s.levels = 2; s.whorl = 0;
        s.count = new float[]{0, 34, 8}; s.angle = new float[]{0, 50, 45}; s.angleVar = new float[]{0, 18, 20};
        s.lengthRatio = new float[]{0, 0.38f, 0.4f}; s.start = new float[]{0, 0.1f, 0.2f};
        s.tropism = new float[]{0, 0.05f, 0.12f}; s.radiusRatio = new float[]{0, 0.55f, 0.5f};
        s.leafLevel = 1; s.leafDensity = 6; s.leaf = Leaf.NEEDLES; s.leafSize = 0.3f;
        s.flexTrunk = 0.3f; s.flexBranch = 0.6f; s.flutter = 0.3f;
        s.bark = new float[]{0.30f, 0.30f, 0.28f};
        s.summer = new float[]{0.05f, 0.10f, 0.035f}; s.spring = s.summer; s.shoots = new float[]{0.12f, 0.22f, 0.08f};
        s.leafOut = 165;
        s.cones = 1.2f; s.coneSize = 0.07f; s.coneColor = new float[]{0.16f, 0.09f, 0.10f}; s.coneUpright = true; s.coneZone = 0.4f;
        return s;
    }

    /** Weißtanne (Abies alba): mächtig, lange kegelförmig, alt mit flacher Spitze („Storchennest“), silbergraue Rinde, aufrechte Zapfen. */
    public static Species silverFir() {
        Species s = new Species("Weißtanne", "Abies alba");
        s.conifer = true; s.heightMin = 28; s.heightMax = 45; s.trunkRatio = 0.023f; s.crownBase = 0.22f; s.crown = Crown.CONICAL;
        s.trunkWobble = 0.01f; s.trunkSegments = 14; s.levels = 2; s.whorl = 5;
        s.count = new float[]{0, 160, 8}; s.angle = new float[]{0, 84, 55}; s.lengthRatio = new float[]{0, 0.15f, 0.4f};
        s.start = new float[]{0, 0, 0.1f}; s.tropism = new float[]{0, -0.01f, 0.08f}; s.radiusRatio = new float[]{0, 0.3f, 0.45f};
        s.leafLevel = 1; s.leafDensity = 5.5f; s.leaf = Leaf.NEEDLES; s.leafSize = 0.4f;
        s.flexTrunk = 0.45f; s.flexBranch = 0.8f; s.flutter = 0.25f;
        s.bark = new float[]{0.26f, 0.26f, 0.25f};
        s.summer = new float[]{0.02f, 0.065f, 0.03f}; s.spring = s.summer; s.shoots = new float[]{0.10f, 0.24f, 0.08f};
        s.leafOut = 140;
        s.cones = 1.5f; s.coneSize = 0.12f; s.coneColor = new float[]{0.20f, 0.14f, 0.07f}; s.coneUpright = true; s.coneZone = 0.15f;
        return s;
    }

    /** Waldkiefer (Pinus sylvestris): hoch astfrei, oben orange „Spiegelrinde“, schirmförmige Krone, blaugrüne Nadeln. */
    public static Species scotsPine() {
        Species s = new Species("Waldkiefer", "Pinus sylvestris");
        s.conifer = true; s.heightMin = 20; s.heightMax = 35; s.trunkRatio = 0.018f; s.crownBase = 0.62f; s.crown = Crown.UMBRELLA;
        s.trunkWobble = 0.035f; s.trunkSegments = 12; s.levels = 2; s.whorl = 4;
        s.count = new float[]{0, 48, 7}; s.angle = new float[]{0, 62, 45}; s.angleVar = new float[]{0, 14, 18};
        s.lengthRatio = new float[]{0, 0.24f, 0.45f}; s.start = new float[]{0, 0, 0.15f};
        s.tropism = new float[]{0, 0.045f, 0.12f}; s.radiusRatio = new float[]{0, 0.4f, 0.5f};
        s.leafLevel = 1; s.leafDensity = 6; s.leaf = Leaf.NEEDLES; s.leafSize = 0.36f;
        s.flexTrunk = 0.55f; s.flexBranch = 0.7f; s.flutter = 0.3f;
        s.bark = new float[]{0.13f, 0.09f, 0.07f}; s.barkTop = new float[]{0.48f, 0.21f, 0.08f}; s.barkTopFrom = 0.45f;
        s.summer = new float[]{0.03f, 0.068f, 0.048f}; s.spring = s.summer; s.shoots = new float[]{0.11f, 0.20f, 0.10f};
        s.leafOut = 130;
        s.cones = 1.5f; s.coneSize = 0.045f; s.coneColor = new float[]{0.22f, 0.18f, 0.13f}; s.coneZone = 0.7f;
        return s;
    }

    /** Europäische Lärche (Larix decidua): lichter Kegel, hängende Zweige, weiche hellgrüne Nadeln, im Herbst goldgelb, im Winter kahl. */
    public static Species larch() {
        Species s = new Species("Europäische Lärche", "Larix decidua");
        s.conifer = true; s.deciduous = true; s.heightMin = 25; s.heightMax = 40; s.trunkRatio = 0.018f; s.crownBase = 0.25f; s.crown = Crown.CONICAL;
        s.trunkWobble = 0.03f; s.trunkSegments = 13; s.levels = 2; s.whorl = 0;
        s.count = new float[]{0, 70, 9}; s.angle = new float[]{0, 75, 55}; s.angleVar = new float[]{0, 12, 18};
        s.lengthRatio = new float[]{0, 0.2f, 0.45f}; s.start = new float[]{0, 0, 0.1f};
        s.tropism = new float[]{0, 0.02f, -0.45f}; s.radiusRatio = new float[]{0, 0.35f, 0.45f};
        s.leafLevel = 1; s.leafDensity = 5; s.leaf = Leaf.NEEDLES; s.leafSize = 0.3f;
        s.flexTrunk = 0.6f; s.flexBranch = 1.1f; s.flutter = 0.6f;
        s.bark = new float[]{0.17f, 0.09f, 0.06f};
        s.spring = new float[]{0.22f, 0.42f, 0.10f}; s.summer = new float[]{0.09f, 0.20f, 0.06f};
        s.autumn = new float[][]{{0.72f, 0.46f, 0.05f}, {0.64f, 0.38f, 0.04f}, {0.78f, 0.55f, 0.08f}};
        s.leafOut = 115; s.colorStart = 280; s.colorFull = 296; s.bare = 318;
        s.cones = 3; s.coneSize = 0.03f; s.coneColor = new float[]{0.20f, 0.12f, 0.07f}; s.coneUpright = true; s.coneZone = 0.9f;
        return s;
    }

    /** Zirbe (Pinus cembra): dicht, säulenförmig bis breit, aufstrebende Äste; aufrechte blauviolette Zapfen; an der Waldgrenze der Alpen. */
    public static Species stonePine() {
        Species s = new Species("Zirbe", "Pinus cembra");
        s.conifer = true; s.heightMin = 10; s.heightMax = 22; s.trunkRatio = 0.035f; s.crownBase = 0.08f; s.crown = Crown.OVOID;
        s.trunkWobble = 0.04f; s.trunkSegments = 12; s.levels = 2; s.whorl = 5;
        s.count = new float[]{0, 120, 8}; s.angle = new float[]{0, 65, 45}; s.lengthRatio = new float[]{0, 0.2f, 0.4f};
        s.start = new float[]{0, 0, 0.15f}; s.tropism = new float[]{0, 0.06f, 0.1f}; s.radiusRatio = new float[]{0, 0.35f, 0.5f};
        s.leafLevel = 1; s.leafDensity = 6.5f; s.leaf = Leaf.NEEDLES; s.leafSize = 0.36f;
        s.flexTrunk = 0.35f; s.flexBranch = 0.6f; s.flutter = 0.25f;
        s.bark = new float[]{0.16f, 0.14f, 0.12f};
        s.summer = new float[]{0.028f, 0.072f, 0.045f}; s.spring = s.summer; s.shoots = new float[]{0.10f, 0.20f, 0.10f};
        s.leafOut = 165;
        s.cones = 1.2f; s.coneSize = 0.07f; s.coneColor = new float[]{0.14f, 0.10f, 0.20f}; s.coneUpright = true; s.coneZone = 0.25f;
        return s;
    }

    /** Alle fertigen Arten: erst die drei aus Yellowstone, dann Eiche, Buche, Birke, Fichte, dann die weiteren Nadelbäume. */
    public static final List<java.util.function.Supplier<Species>> ALL = List.of(Species::lodgepolePine, Species::aspen, Species::douglasFir,
            Species::oak, Species::beech, Species::birch, Species::spruce,
            Species::engelmannSpruce, Species::subalpineFir, Species::whitebarkPine,
            Species::silverFir, Species::scotsPine, Species::larch, Species::stonePine);

    /** Die Art mit diesem deutschen oder lateinischen Namen (oder null). */
    public static Species byName(String n) {
        for (java.util.function.Supplier<Species> f : ALL) {
            Species s = f.get();
            if (s.name.equalsIgnoreCase(n) || s.latin.equalsIgnoreCase(n)) return s;
        }
        return null;
    }

    @Override public String toString() { return name + " (" + latin + ")"; }
}
