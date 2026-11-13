package com.grafkom.viewing3d;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/**
 * Mesh segitiga untuk Visual Realism. Dari data posisi + UV + indeks, kelas ini membangun DUA
 * buffer GPU dengan layout interleaved (pos3, normal3, uv2):
 *   - FLAT   : tiap segitiga punya 1 normal face (non-indexed, vertex tidak dibagi)
 *   - SMOOTH : normal vertex = rata-rata normal semua face yang berbagi posisi vertex itu
 *              N_vertex = normalize(sum N_face_i)  (vertex digabung berdasarkan POSISI,
 *              sehingga jahitan UV tidak menimbulkan garis patah)
 */
public class RealismMesh {

    private record PosKey(long x, long y, long z) { }

    private final String name;
    private final int vaoFlat, vboFlat, vaoSmooth, vboSmooth;
    private final int vertexCount;

    public RealismMesh(String name, float[] positions, float[] uvs, int[] indices) {
        this.name = name;

        int triTotal = indices.length / 3;
        float[][] faceNormal = new float[triTotal][];
        Map<PosKey, float[]> accum = new HashMap<>();

        // Pass 1: normal face + akumulasi ke vertex (digabung per posisi)
        int valid = 0;
        for (int t = 0; t < triTotal; t++) {
            int ia = indices[3 * t], ib = indices[3 * t + 1], ic = indices[3 * t + 2];
            float[] n = faceNormalOf(positions, ia, ib, ic);
            if (n == null) continue;                  // segitiga degenerate (mis. di kutub bola)
            faceNormal[t] = n;
            valid++;
            for (int idx : new int[] { ia, ib, ic }) {
                float[] acc = accum.computeIfAbsent(key(positions, idx), k -> new float[3]);
                acc[0] += n[0]; acc[1] += n[1]; acc[2] += n[2];
            }
        }
        for (float[] acc : accum.values()) normalize(acc);

        // Pass 2: susun buffer interleaved
        vertexCount = valid * 3;
        float[] flat = new float[vertexCount * 8];
        float[] smooth = new float[vertexCount * 8];
        int o = 0;
        for (int t = 0; t < triTotal; t++) {
            if (faceNormal[t] == null) continue;
            for (int k = 0; k < 3; k++) {
                int idx = indices[3 * t + k];
                float[] fn = faceNormal[t];
                float[] sn = accum.get(key(positions, idx));
                put(flat, o, positions, uvs, idx, fn);
                put(smooth, o, positions, uvs, idx, sn);
                o += 8;
            }
        }

        vaoFlat = glGenVertexArrays();   vboFlat = glGenBuffers();
        vaoSmooth = glGenVertexArrays(); vboSmooth = glGenBuffers();
        upload(vaoFlat, vboFlat, flat);
        upload(vaoSmooth, vboSmooth, smooth);
    }

    private static PosKey key(float[] p, int i) {
        return new PosKey(Math.round(p[3 * i] * 10000f), Math.round(p[3 * i + 1] * 10000f),
                          Math.round(p[3 * i + 2] * 10000f));
    }

    private static float[] faceNormalOf(float[] p, int a, int b, int c) {
        float e1x = p[3 * b] - p[3 * a], e1y = p[3 * b + 1] - p[3 * a + 1], e1z = p[3 * b + 2] - p[3 * a + 2];
        float e2x = p[3 * c] - p[3 * a], e2y = p[3 * c + 1] - p[3 * a + 1], e2z = p[3 * c + 2] - p[3 * a + 2];
        float[] n = { e1y * e2z - e1z * e2y, e1z * e2x - e1x * e2z, e1x * e2y - e1y * e2x };
        float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (len < 1e-9f) return null;
        n[0] /= len; n[1] /= len; n[2] /= len;
        return n;
    }

    private static void normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > 1e-9f) { v[0] /= len; v[1] /= len; v[2] /= len; }
    }

    private static void put(float[] dst, int o, float[] pos, float[] uv, int idx, float[] n) {
        dst[o]     = pos[3 * idx];     dst[o + 1] = pos[3 * idx + 1]; dst[o + 2] = pos[3 * idx + 2];
        dst[o + 3] = n[0];             dst[o + 4] = n[1];             dst[o + 5] = n[2];
        dst[o + 6] = uv[2 * idx];      dst[o + 7] = uv[2 * idx + 1];
    }

    private static void upload(int vao, int vbo, float[] data) {
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        int stride = 8 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);                    // aPos
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);      // aNormal
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);      // aTexCoords
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    public void draw(boolean smoothShading) {
        glBindVertexArray(smoothShading ? vaoSmooth : vaoFlat);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public String getName() { return name; }
    public int getTriangleCount() { return vertexCount / 3; }

    public void destroy() {
        glDeleteBuffers(vboFlat);   glDeleteVertexArrays(vaoFlat);
        glDeleteBuffers(vboSmooth); glDeleteVertexArrays(vaoSmooth);
    }
}
