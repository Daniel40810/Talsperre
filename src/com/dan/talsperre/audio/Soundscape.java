package com.dan.talsperre.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.Random;

/**
 * Der Ton der Talsperre, rein synthetisch (javax.sound, 22,05 kHz, 16 Bit, mono): Rauschen der Ablässe und Überläufe nach
 * Wassermenge und Abstand zum Tosbecken, Wind, Vogelrufe am Tag, Grillen in der Nacht, Schiffshorn, Autos, in der Szene
 * von 1943 Motoren, Detonationen und das Rauschen der Flut. Läuft in einem Hintergrundfaden; ohne Tonausgabe bleibt es still.
 */
public final class Soundscape {
    public static final int RATE = 22050;

    // Zustand, den die Ansicht je Bild setzt
    public volatile boolean enabled = true;
    public volatile double volume = 0.6;
    /** Wassermenge 0..1, Abstand der Kamera zum Tosbecken (m), Wind 0..1, Nacht 0..1. */
    public volatile double flow, dist = 100, wind = 0.3, night;
    public volatile boolean birds = true, traffic = true, ships = true;
    /** Bomber (Nähe 0..1), Flut 0..1, Detonation 0..1 (klingt ab). */
    public volatile double bomber, flood, blast;
    private volatile int hornRequest;

    private final Random rnd = new Random(7);
    // Filter- und Oszillatorzustände
    private double lp1, lp2, lpW, lpB, lpC, hp1, hpPrev, phWind, phEng, tremolo, crick, crickEnv;
    private double chirpT = 1, chirpF, chirpA, chirpPh, nextChirp = 1;
    private double hornT = -1, hornPh;
    private double carT = -1, nextCar = 12, carPan;
    private double nextHorn = 60;
    private double blastEnv, lpBlast;
    private double clock;

    private SourceDataLine line;
    private Thread thread;
    private volatile boolean running;

    /** Schiffshorn jetzt. */
    public void horn() { hornRequest++; }
    private int hornSeen;

    public void start() {
        if (thread != null) return;
        running = true;
        thread = new Thread(this::run, "Talsperre-Ton");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        SourceDataLine l = line;
        if (l != null) { try { l.stop(); l.close(); } catch (RuntimeException ignored) { } }
    }

    private void run() {
        try {
            AudioFormat f = new AudioFormat(RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(f);
            line.open(f, RATE / 4 * 2);
            line.start();
        } catch (Exception | Error ex) {
            line = null;
            return;
        }
        int n = RATE / 20;
        byte[] buf = new byte[2 * n];
        float[] mix = new float[n];
        while (running) {
            if (!enabled) { sleep(60); continue; }
            render(mix, n);
            for (int i = 0; i < n; i++) {
                int v = (int) Math.max(-32767, Math.min(32767, Math.round(mix[i] * 32767)));
                buf[2 * i] = (byte) v; buf[2 * i + 1] = (byte) (v >> 8);
            }
            line.write(buf, 0, buf.length);
        }
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    /** Rechnet n Abtastwerte in out (−1..1); öffentlich für den Selbsttest. */
    public synchronized void render(float[] out, int n) {
        double vol = volume;
        double att = 1.0 / (1.0 + Math.max(0, dist - 40) / 90.0);
        double fl = flow, w = wind, nt = night, bm = bomber, fd = flood;
        if (hornRequest != hornSeen) { hornSeen = hornRequest; hornT = 0; }
        for (int i = 0; i < n; i++) {
            clock += 1.0 / RATE;
            double white = rnd.nextDouble() * 2 - 1;
            // Wasser: dunkles Rauschen plus Rauschen der Gischt, mit langsamem Schwellen
            lp1 += (white - lp1) * 0.045;
            lp2 += (white - lp2) * 0.22;
            double swell = 0.85 + 0.15 * Math.sin(clock * 1.7) * Math.sin(clock * 0.43);
            double water = (lp1 * 3.2 + (lp2 - lp1) * 1.1) * fl * att * swell * 0.55;
            // Wind: tiefes Rauschen mit Böen
            lpW += (white - lpW) * 0.012;
            double gust = 0.5 + 0.5 * Math.sin(clock * 0.37 + 1.3 * Math.sin(clock * 0.11));
            double wd = lpW * 5.0 * w * (0.3 + 0.7 * gust) * 0.28;
            // Flut und Bruch: breites, tiefes Tosen
            lpB += (white - lpB) * 0.02;
            double roar = (lpB * 4.5 + (lp2 - lp1) * 0.8) * fd * 0.8 * (0.9 + 0.1 * Math.sin(clock * 5.3));
            // Motoren der Bomber: vier leicht verstimmte Dreieckschwingungen um 85 Hz mit Schweben
            double eng = 0;
            if (bm > 0.01) {
                phEng += 2 * Math.PI * 82 / RATE;
                double trem = 0.75 + 0.25 * Math.sin(clock * 2 * Math.PI * 11.3);
                for (int k = 0; k < 4; k++) {
                    double ph = phEng * (1 + 0.012 * k) + k * 1.9;
                    eng += (Math.sin(ph) + 0.5 * Math.sin(2 * ph) + 0.25 * Math.sin(3 * ph));
                }
                eng *= 0.045 * bm * trem;
            }
            // Detonation: tiefer Schlag, klingt ab
            double bl = 0;
            if (blast > 0.02) blastEnv = Math.max(blastEnv, blast);
            if (blastEnv > 0.001) {
                lpBlast += (white - lpBlast) * 0.03;
                bl = lpBlast * 6.0 * blastEnv;
                blastEnv *= 0.99985;
            }
            // Vögel (Tag)
            double bird = 0;
            if (birds && nt < 0.5 && fl < 0.6) {
                nextChirp -= 1.0 / RATE;
                if (nextChirp <= 0) { nextChirp = 0.4 + rnd.nextDouble() * 3.5; chirpT = 0; chirpF = 2400 + rnd.nextDouble() * 2200; chirpA = 0.5 + rnd.nextDouble() * 0.5; }
                if (chirpT >= 0 && chirpT < 0.09) {
                    double env = Math.sin(Math.PI * chirpT / 0.09);
                    chirpPh += 2 * Math.PI * (chirpF + 900 * chirpT / 0.09) / RATE;
                    bird = Math.sin(chirpPh) * env * chirpA * 0.045 * (1 - nt);
                    chirpT += 1.0 / RATE;
                }
            }
            // Grillen (Nacht)
            double cr = 0;
            if (nt > 0.4 && fl < 0.8) {
                crick += 2 * Math.PI * 4300 / RATE;
                double gate = (Math.sin(clock * 2 * Math.PI * 38) > 0.2 ? 1 : 0) * (Math.sin(clock * 2 * Math.PI * 2.3) > -0.2 ? 1 : 0);
                crickEnv += (gate - crickEnv) * 0.01;
                cr = Math.sin(crick) * crickEnv * 0.018 * nt;
            }
            // Schiffshorn: zwei Töne, tief
            double horn = 0;
            if (hornT >= 0) {
                hornT += 1.0 / RATE;
                double env = Math.min(1, hornT / 0.12) * Math.max(0, Math.min(1, (2.4 - hornT) / 0.3));
                hornPh += 2 * Math.PI * 128 / RATE;
                horn = (Math.sin(hornPh) + 0.7 * Math.sin(hornPh * 1.5) + 0.3 * Math.sin(hornPh * 2)) * env * 0.12;
                if (hornT > 2.4) hornT = -1;
            } else if (ships && nt < 0.7) {
                nextHorn -= 1.0 / RATE;
                if (nextHorn <= 0) { nextHorn = 80 + rnd.nextDouble() * 120; hornT = 0; }
            }
            // Autos: Rauschband, das anschwillt und wieder abnimmt
            double car = 0;
            if (traffic) {
                nextCar -= 1.0 / RATE;
                if (nextCar <= 0 && carT < 0) { carT = 0; nextCar = 18 + rnd.nextDouble() * 30; }
                if (carT >= 0) {
                    carT += 1.0 / RATE;
                    double env = Math.sin(Math.PI * Math.min(1, carT / 5.0));
                    lpC += (white - lpC) * 0.08;
                    car = lpC * 2.2 * env * env * 0.05;
                    if (carT > 5) carT = -1;
                }
            }
            double s = water + wd + roar + eng + bl + bird + cr + horn + car;
            s = Math.tanh(s * 1.4) * vol;
            out[i] = (float) s;
        }
    }

    /** Für den Selbsttest: setzt die Abklingzeit der Detonation zurück. */
    public void resetEnvelope() { blastEnv = 0; }
}
