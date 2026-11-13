package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL11.*;

/**
 * Rendering stereoskopik Anaglyph Red-Cyan dengan Color Masking.
 * Pass 1: mata kiri -> kanal merah saja. Pass 2: mata kanan -> kanal hijau+biru (cyan).
 * Pemanggil (callback) bertanggung jawab memasang matriks off-axis sesuai eyeOffset.
 */
public class StereoAnaglyphRenderer {

    /** Callback render satu mata; eyeOffset negatif = mata kiri, positif = mata kanan. */
    @FunctionalInterface
    public interface EyeCallback {
        void render(float eyeOffset);
    }

    private float ipd = 0.30f; // Interpupillary Distance (satuan dunia)

    public void renderAnaglyph(EyeCallback callback) {
        try {
            // Bersihkan SEMUA kanal dulu, agar tidak ada sisa frame sebelumnya
            glColorMask(true, true, true, true);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // PASS 1: mata kiri -> hanya kanal merah
            glColorMask(true, false, false, true);
            callback.render(-ipd / 2.0f);

            // PASS 2: mata kanan -> kanal hijau + biru (cyan)
            glColorMask(false, true, true, true);
            glClear(GL_DEPTH_BUFFER_BIT);   // depth dibersihkan, color buffer TIDAK
            callback.render(+ipd / 2.0f);
        } finally {
            glColorMask(true, true, true, true); // kembalikan ke kondisi normal
        }
    }

    public float getIpd() { return ipd; }
    public void setIpd(float ipd) { this.ipd = Math.max(0.0f, Math.min(1.5f, ipd)); }
}
