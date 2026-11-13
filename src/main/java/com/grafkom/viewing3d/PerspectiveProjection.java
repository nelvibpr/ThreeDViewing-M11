package com.grafkom.viewing3d;

import org.joml.Matrix4f;

/**
 * Proyeksi perspektif dengan Viewing Frustum yang dihitung eksplisit
 * (FOV_y, Aspect Ratio, Near, Far). Mendukung frustum asimetris (off-axis)
 * untuk stereo view.
 */
public class PerspectiveProjection {

    private float fovDegree;
    private float aspectRatio;
    private final float zNear;
    private final float zFar;

    public PerspectiveProjection(float fovDegree, float aspectRatio, float zNear, float zFar) {
        this.fovDegree = fovDegree;
        this.aspectRatio = aspectRatio;
        this.zNear = zNear;
        this.zFar = zFar;
    }

    /** Matriks perspektif simetris standar (sesuai modul). */
    public Matrix4f calculateProjectionMatrix() {
        float fovRad = (float) Math.toRadians(fovDegree);
        float f = 1.0f / (float) Math.tan(fovRad / 2.0f);

        Matrix4f proj = new Matrix4f();
        proj.zero();
        proj.m00(f / aspectRatio);
        proj.m11(f);
        proj.m22(-(zFar + zNear) / (zFar - zNear));
        proj.m32(-(2.0f * zFar * zNear) / (zFar - zNear));
        proj.m23(-1.0f);
        proj.m33(0.0f);
        return proj;
    }

    /**
     * Off-Axis (Asymmetric Frustum). Mata berada pada x = eyeOffset, bidang layar
     * (zero parallax) berjarak focalLength dari kamera. Tepi kiri/kanan frustum
     * pada near plane digeser sebesar eyeOffset * near / focalLength.
     */
    public Matrix4f calculateOffAxisMatrix(float eyeOffset, float focalLength) {
        float fovRad = (float) Math.toRadians(fovDegree);
        float top = zNear * (float) Math.tan(fovRad / 2.0f);
        float bottom = -top;
        float halfW = top * aspectRatio;
        float shift = eyeOffset * zNear / focalLength;
        return frustum(-halfW - shift, halfW - shift, bottom, top, zNear, zFar);
    }

    /** Matriks frustum umum (setara glFrustum), dihitung manual. */
    public static Matrix4f frustum(float l, float r, float b, float t, float n, float f) {
        Matrix4f m = new Matrix4f();
        m.zero();
        m.m00(2.0f * n / (r - l));
        m.m11(2.0f * n / (t - b));
        m.m20((r + l) / (r - l));
        m.m21((t + b) / (t - b));
        m.m22(-(f + n) / (f - n));
        m.m23(-1.0f);
        m.m32(-(2.0f * f * n) / (f - n));
        return m;
    }

    // Getter/Setter untuk pengujian interaktif FOV (Dolly Zoom)
    public void setFovDegree(float fovDegree) {
        this.fovDegree = Math.max(10.0f, Math.min(120.0f, fovDegree));
    }
    public float getFovDegree()                { return fovDegree; }
    public void setAspectRatio(float aspect)   { this.aspectRatio = aspect; }
    public float getAspectRatio()              { return aspectRatio; }
    public float getZNear()                    { return zNear; }
    public float getZFar()                     { return zFar; }
}
