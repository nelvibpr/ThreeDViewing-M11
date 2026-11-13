package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL11.*;

/** Model 3D sederhana (daftar vertex + face poligon + warna per face). */
public class Mesh3D {

    private final String name;
    private final float[][] vertices;   // [i] = {x, y, z}
    private final int[][] faces;        // indeks vertex per face (cembung)
    private final float[][] faceColors; // [f] = {r, g, b}

    public Mesh3D(String name, float[][] vertices, int[][] faces, float[][] faceColors) {
        this.name = name;
        this.vertices = vertices;
        this.faces = faces;
        this.faceColors = faceColors;
    }

    public String getName() { return name; }

    /** Gambar model. grayscale = true dipakai pada mode anaglyph agar kedalaman tidak hilang. */
    public void draw(boolean grayscale) {
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.0f, 1.0f);
        for (int f = 0; f < faces.length; f++) {
            float[] c = faceColors[f];
            if (grayscale) {
                float g = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2];
                g = 0.25f + 0.75f * g;
                glColor3f(g, g, g);
            } else {
                glColor3f(c[0], c[1], c[2]);
            }
            glBegin(GL_POLYGON);
            for (int idx : faces[f]) {
                float[] p = vertices[idx];
                glVertex3f(p[0], p[1], p[2]);
            }
            glEnd();
        }
        glDisable(GL_POLYGON_OFFSET_FILL);

        // Garis tepi
        glColor3f(0.95f, 0.95f, 0.95f);
        glLineWidth(2.0f);
        for (int[] face : faces) {
            glBegin(GL_LINE_LOOP);
            for (int idx : face) {
                float[] p = vertices[idx];
                glVertex3f(p[0], p[1], p[2]);
            }
            glEnd();
        }
        glLineWidth(1.0f);
    }

    /** Kubus berwarna, pusat di (0,0,0), panjang sisi = 2 * s. */
    public static Mesh3D createColorCube(float s) {
        float[][] v = {
            {-s, -s,  s}, { s, -s,  s}, { s,  s,  s}, {-s,  s,  s},  // 0..3 (z = +s)
            {-s, -s, -s}, { s, -s, -s}, { s,  s, -s}, {-s,  s, -s}   // 4..7 (z = -s)
        };
        int[][] f = {
            {0, 1, 2, 3},  // depan  (+z)
            {5, 4, 7, 6},  // belakang (-z)
            {1, 5, 6, 2},  // kanan  (+x)
            {4, 0, 3, 7},  // kiri   (-x)
            {3, 2, 6, 7},  // atas   (+y)
            {4, 5, 1, 0}   // bawah  (-y)
        };
        float[][] c = {
            {0.95f, 0.25f, 0.25f},  // merah
            {0.20f, 0.80f, 0.90f},  // cyan
            {0.25f, 0.85f, 0.35f},  // hijau
            {0.85f, 0.30f, 0.85f},  // magenta
            {0.95f, 0.85f, 0.25f},  // kuning
            {0.30f, 0.45f, 0.95f}   // biru
        };
        return new Mesh3D("Color Cube", v, f, c);
    }

    /** Rumah sederhana: badan balok, atap pelana, dan pintu. */
    public static Mesh3D createHouse() {
        float[][] v = {
            {-1.2f, -1.0f,  1.0f}, { 1.2f, -1.0f,  1.0f}, { 1.2f,  0.4f,  1.0f}, {-1.2f,  0.4f,  1.0f}, // 0..3 depan
            {-1.2f, -1.0f, -1.0f}, { 1.2f, -1.0f, -1.0f}, { 1.2f,  0.4f, -1.0f}, {-1.2f,  0.4f, -1.0f}, // 4..7 belakang
            { 0.0f,  1.4f,  1.0f}, { 0.0f,  1.4f, -1.0f},                                                 // 8,9 puncak atap
            {-0.3f, -1.0f,  1.01f}, { 0.3f, -1.0f,  1.01f}, { 0.3f, -0.1f,  1.01f}, {-0.3f, -0.1f,  1.01f} // 10..13 pintu
        };
        int[][] f = {
            {0, 1, 2, 3},   // dinding depan
            {5, 4, 7, 6},   // dinding belakang
            {1, 5, 6, 2},   // dinding kanan
            {4, 0, 3, 7},   // dinding kiri
            {4, 5, 1, 0},   // alas
            {3, 2, 8},      // gable depan
            {6, 7, 9},      // gable belakang
            {2, 6, 9, 8},   // atap kanan
            {7, 3, 8, 9},   // atap kiri
            {10, 11, 12, 13}// pintu
        };
        float[][] c = {
            {0.95f, 0.85f, 0.65f}, {0.90f, 0.80f, 0.60f},
            {0.85f, 0.75f, 0.55f}, {0.85f, 0.75f, 0.55f},
            {0.50f, 0.40f, 0.30f},
            {0.90f, 0.80f, 0.60f}, {0.85f, 0.75f, 0.55f},
            {0.80f, 0.25f, 0.20f}, {0.70f, 0.20f, 0.18f},
            {0.45f, 0.28f, 0.15f}
        };
        return new Mesh3D("Rumah 3D", v, f, c);
    }
}
