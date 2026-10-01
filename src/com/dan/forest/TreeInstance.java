package com.dan.forest;

/** Ein Baum im Wald: Art, Variante, Ort, Drehung, Größe, Alter; dazu der Zustand für Wind und Laubfall. */
public final class TreeInstance {
    public final Species species;
    /** Index der Art im Wald und Variante (welches der vorgerechneten Modelle). */
    public final int speciesIndex, variant;
    public double x, y, z, yaw, scale;
    /** Alter 0..1 (junge Bäume sind kleiner). */
    public final float age;
    /** Detailstufe im letzten Bild (0..3) und der Animator, solange der Baum nah ist. */
    public int lod = 3;
    TreeAnimator anim;
    /** Schon gefallener Anteil (steigt nur; Böen reißen mehr ab). */
    float dropped = -1;
    float[] leafScale;

    TreeInstance(Species species, int speciesIndex, int variant, double x, double y, double z, double yaw, double scale, float age) {
        this.species = species; this.speciesIndex = speciesIndex; this.variant = variant;
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.scale = scale; this.age = age;
    }

    /** Bewegte Lagen und Normalen (null, wenn der Baum ruht, dann gilt das Netz der Stufe). */
    public float[] animatedPositions() { return anim == null ? null : anim.pos; }
    public float[] animatedNormals() { return anim == null ? null : anim.nrm; }
}
