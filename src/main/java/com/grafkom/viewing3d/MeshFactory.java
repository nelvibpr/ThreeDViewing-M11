package com.grafkom.viewing3d;

/** Pembangkit mesh prosedural (semua segitiga CCW menghadap keluar, lengkap dengan UV). */
public final class MeshFactory {

    private MeshFactory() { }

    /** Bola. stacks x slices dibuat rendah sengaja agar beda Flat vs Smooth terlihat jelas. */
    public static RealismMesh createSphere(String name, float r, int stacks, int slices) {
        int nv = (stacks + 1) * (slices + 1);
        float[] pos = new float[nv * 3];
        float[] uv = new float[nv * 2];
        for (int i = 0; i <= stacks; i++) {
            double theta = Math.PI * i / stacks;
            for (int j = 0; j <= slices; j++) {
                double phi = 2.0 * Math.PI * j / slices;
                int k = i * (slices + 1) + j;
                pos[3 * k]     = (float) (r * Math.sin(theta) * Math.cos(phi));
                pos[3 * k + 1] = (float) (r * Math.cos(theta));
                pos[3 * k + 2] = (float) (r * Math.sin(theta) * Math.sin(phi));
                uv[2 * k]      = 2.0f * j / slices;
                uv[2 * k + 1]  = 1.0f - (float) i / stacks;
            }
        }
        return new RealismMesh(name, pos, uv, gridIndices(stacks, slices));
    }

    /** Torus (donat) sumbu Y: R = jari-jari cincin, r = jari-jari tabung. */
    public static RealismMesh createTorus(String name, float R, float r, int ringSeg, int tubeSeg) {
        int nv = (ringSeg + 1) * (tubeSeg + 1);
        float[] pos = new float[nv * 3];
        float[] uv = new float[nv * 2];
        for (int i = 0; i <= ringSeg; i++) {
            double phi = 2.0 * Math.PI * i / ringSeg;
            for (int j = 0; j <= tubeSeg; j++) {
                double psi = 2.0 * Math.PI * j / tubeSeg;
                int k = i * (tubeSeg + 1) + j;
                pos[3 * k]     = (float) ((R + r * Math.cos(psi)) * Math.cos(phi));
                pos[3 * k + 1] = (float) (r * Math.sin(psi));
                pos[3 * k + 2] = (float) ((R + r * Math.cos(psi)) * Math.sin(phi));
                uv[2 * k]      = 3.0f * i / ringSeg;
                uv[2 * k + 1]  = (float) j / tubeSeg;
            }
        }
        return new RealismMesh(name, pos, uv, gridIndices(ringSeg, tubeSeg));
    }

    /** Indeks grid (rows x cols) dengan winding (a,d,b),(d,c,b) -> CCW menghadap luar. */
    private static int[] gridIndices(int rows, int cols) {
        int[] idx = new int[rows * cols * 6];
        int p = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int a = i * (cols + 1) + j;
                int b = (i + 1) * (cols + 1) + j;
                int c = b + 1;
                int d = a + 1;
                idx[p++] = a; idx[p++] = d; idx[p++] = b;
                idx[p++] = d; idx[p++] = c; idx[p++] = b;
            }
        }
        return idx;
    }

    /** Kubus dengan sisi 2*s. Normal smooth pada sudut = rata-rata 3 face (tampak membulat). */
    public static RealismMesh createCube(String name, float s) {
        float[][] v = {
            {-s, -s,  s}, { s, -s,  s}, { s,  s,  s}, {-s,  s,  s},
            {-s, -s, -s}, { s, -s, -s}, { s,  s, -s}, {-s,  s, -s}
        };
        int[][] quads = {
            {0, 1, 2, 3}, {5, 4, 7, 6}, {1, 5, 6, 2},
            {4, 0, 3, 7}, {3, 2, 6, 7}, {4, 5, 1, 0}
        };
        float[][] quv = { {0, 0}, {1, 0}, {1, 1}, {0, 1} };

        float[] pos = new float[24 * 3];
        float[] uv = new float[24 * 2];
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            for (int k = 0; k < 4; k++) {
                int vi = f * 4 + k;
                float[] p = v[quads[f][k]];
                pos[3 * vi] = p[0]; pos[3 * vi + 1] = p[1]; pos[3 * vi + 2] = p[2];
                uv[2 * vi] = quv[k][0]; uv[2 * vi + 1] = quv[k][1];
            }
            int b = f * 4, o = f * 6;
            idx[o] = b;     idx[o + 1] = b + 1; idx[o + 2] = b + 2;
            idx[o + 3] = b; idx[o + 4] = b + 2; idx[o + 5] = b + 3;
        }
        return new RealismMesh(name, pos, uv, idx);
    }

    /** Bidang lantai horizontal pada ketinggian y, UV di-tile tiling x tiling kali. */
    public static RealismMesh createGround(String name, float half, float y, float tiling) {
        float[] pos = {
            -half, y,  half,   half, y,  half,   half, y, -half,   -half, y, -half
        };
        float[] uv = { 0, 0,   tiling, 0,   tiling, tiling,   0, tiling };
        int[] idx = { 0, 1, 2,   0, 2, 3 };   // CCW dilihat dari atas -> normal +Y
        return new RealismMesh(name, pos, uv, idx);
    }
}
