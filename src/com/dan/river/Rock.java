package com.dan.river;

/**
 * Ein Stein im Fluss (oder ein Brückenpfeiler, ein Baumstamm von oben gesehen als Kreis): Mitte, Radius
 * am Wasserspiegel und wie weit er herausragt (negativ: knapp unter Wasser, dann bildet sich nur eine
 * stehende Welle darüber).
 */
public final class Rock {
    public final float x, z, r, top;

    public Rock(double x, double z, double r, double top) {
        this.x = (float) x; this.z = (float) z; this.r = (float) r; this.top = (float) top;
    }
}
