package com.grafkom.viewing3d;

import org.joml.Matrix4f;

/**
 * Pengelola taksonomi proyeksi:
 *   Paralel    : Ortografis, Isometrik (aksonometrik), Oblik Cavalier, Oblik Cabinet
 *   Perspektif : 1, 2, dan 3 titik hilang
 * Semua matriks dihitung manual (tanpa glOrtho/gluPerspective, yang tidak ada di LWJGL).
 */
public class ProjectionTaxonomyManager {

    public enum ProjectionType {
        //            label                       perspektif  azimuth  elevasi   k     sudut
        ORTHOGRAPHIC     ("Ortografis",                false,   30.0f,  20.0f,  0.0f,  0.0f),
        ISOMETRIC        ("Isometrik (Aksonometrik)",  false,   45.0f,  35.264f,0.0f,  0.0f),
        OBLIQUE_CAVALIER ("Oblik Cavalier (k=1)",      false,    0.0f,   0.0f,  1.0f, 45.0f),
        OBLIQUE_CABINET  ("Oblik Cabinet (k=0.5)",     false,    0.0f,   0.0f,  0.5f, 45.0f),
        PERSPECTIVE_1POINT("Perspektif 1 Titik Hilang",true,     0.0f,   0.0f,  0.0f,  0.0f),
        PERSPECTIVE_2POINT("Perspektif 2 Titik Hilang",true,    45.0f,   0.0f,  0.0f,  0.0f),
        PERSPECTIVE_3POINT("Perspektif 3 Titik Hilang",true,    45.0f,  30.0f,  0.0f,  0.0f);

        private final String label;
        private final boolean perspective;
        private final float presetAzimuth, presetElevation;
        private final float obliqueK, obliqueAngleDeg;

        ProjectionType(String label, boolean perspective, float az, float el, float k, float angle) {
            this.label = label;
            this.perspective = perspective;
            this.presetAzimuth = az;
            this.presetElevation = el;
            this.obliqueK = k;
            this.obliqueAngleDeg = angle;
        }

        public String getLabel()           { return label; }
        public boolean isPerspective()     { return perspective; }
        public float getPresetAzimuth()    { return presetAzimuth; }
        public float getPresetElevation()  { return presetElevation; }
    }

    private ProjectionTaxonomyManager() { }

    /**
     * Membangun matriks proyeksi untuk satu mata.
     *
     * @param cameraRadius jarak kamera ke target; juga dipakai sebagai focal length
     *                     (bidang zero parallax) dan penentu ukuran view volume paralel
     * @param eyeOffset    pergeseran mata (0 = tanpa stereo)
     */
    public static Matrix4f buildProjection(ProjectionType type, PerspectiveProjection persp,
                                           float cameraRadius, float eyeOffset) {
        if (type.isPerspective()) {
            return (eyeOffset == 0.0f)
                    ? persp.calculateProjectionMatrix()
                    : persp.calculateOffAxisMatrix(eyeOffset, cameraRadius);
        }

        // --- Proyeksi paralel: ortho * shear ---
        float halfH = cameraRadius * (float) Math.tan(Math.toRadians(persp.getFovDegree()) / 2.0);
        float halfW = halfH * persp.getAspectRatio();
        Matrix4f ortho = orthographic(-halfW, halfW, -halfH, halfH, persp.getZNear(), persp.getZFar());

        // Shear: x' = x + a*z, y' = y + b*z
        //  - oblik  : a = -k cos(alpha), b = -k sin(alpha)  (sumbu z "miring")
        //  - stereo : a -= eyeOffset / focal  (parallax mata pada proyeksi paralel)
        float alpha = (float) Math.toRadians(type.obliqueAngleDeg);
        float a = -type.obliqueK * (float) Math.cos(alpha) - eyeOffset / cameraRadius;
        float b = -type.obliqueK * (float) Math.sin(alpha);

        Matrix4f shear = new Matrix4f();
        shear.m20(a).m21(b);
        return ortho.mul(shear);
    }

    /** Matriks proyeksi ortografis (setara glOrtho), dihitung manual. */
    public static Matrix4f orthographic(float l, float r, float b, float t, float n, float f) {
        Matrix4f m = new Matrix4f();
        m.m00(2.0f / (r - l));
        m.m11(2.0f / (t - b));
        m.m22(-2.0f / (f - n));
        m.m30(-(r + l) / (r - l));
        m.m31(-(t + b) / (t - b));
        m.m32(-(f + n) / (f - n));
        m.m33(1.0f);
        return m;
    }
}
