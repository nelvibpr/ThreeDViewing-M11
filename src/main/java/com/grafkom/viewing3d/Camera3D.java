package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Kamera sintetis 3D (Synthetic Camera Model) dengan navigasi orbital.
 * Parameter: Eye (E), Target/At, dan Up. Matriks LookAt dihitung MANUAL
 * dari basis ortonormal (u, v, n), bukan memakai Matrix4f.lookAt().
 */
public class Camera3D {

    private static final float MIN_RADIUS = 2.0f;
    private static final float MAX_RADIUS = 30.0f;
    private static final float MAX_ELEVATION = 89.0f; // hindari Up sejajar n (gimbal flip)

    private final Vector3f eye;
    private final Vector3f target;
    private final Vector3f up;

    // koordinat sferis kamera orbital terhadap target
    private float radius;
    private float azimuthDeg;
    private float elevationDeg;

    // pose awal untuk reset
    private final float initRadius, initAzimuth, initElevation;

    public Camera3D(Vector3f eye, Vector3f target, Vector3f up) {
        this.eye = new Vector3f(eye);
        this.target = new Vector3f(target);
        this.up = new Vector3f(up);

        Vector3f d = new Vector3f(eye).sub(target);
        this.radius = d.length();
        this.elevationDeg = (float) Math.toDegrees(Math.asin(d.y / radius));
        this.azimuthDeg = (float) Math.toDegrees(Math.atan2(d.x, d.z));

        this.initRadius = radius;
        this.initAzimuth = azimuthDeg;
        this.initElevation = elevationDeg;
    }

    /** Matriks LookAt (View Matrix) tanpa pergeseran mata. */
    public Matrix4f calculateLookAtMatrix() {
        return calculateLookAtMatrix(0.0f);
    }

    /**
     * Matriks LookAt manual. lateralOffset menggeser posisi kamera sepanjang
     * vektor kanan u (arah pandang tetap sejajar) -> dipakai untuk mata kiri (-)
     * dan mata kanan (+) pada stereo view.
     */
    public Matrix4f calculateLookAtMatrix(float lateralOffset) {
        Vector3f n = new Vector3f();
        eye.sub(target, n).normalize();      // View Plane Normal
        Vector3f u = new Vector3f();
        up.cross(n, u).normalize();          // Right Vector
        Vector3f v = new Vector3f();
        n.cross(u, v);                       // True Up Vector

        Vector3f e = new Vector3f(eye).fma(lateralOffset, u);

        Matrix4f m = new Matrix4f();         // JOML: m<kolom><baris>
        m.m00(u.x).m10(u.y).m20(u.z).m30(-u.dot(e));
        m.m01(v.x).m11(v.y).m21(v.z).m31(-v.dot(e));
        m.m02(n.x).m12(n.y).m22(n.z).m32(-n.dot(e));
        m.m03(0.0f).m13(0.0f).m23(0.0f).m33(1.0f);
        return m;
    }

    /** Update koordinat sferis untuk kamera orbital (mengelilingi target). */
    public void updateOrbitalPosition(float radius, float azimuthDeg, float elevationDeg) {
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        this.azimuthDeg = azimuthDeg;
        this.elevationDeg = Math.max(-MAX_ELEVATION, Math.min(MAX_ELEVATION, elevationDeg));

        float azRad = (float) Math.toRadians(this.azimuthDeg);
        float elRad = (float) Math.toRadians(this.elevationDeg);
        eye.x = target.x + this.radius * (float) (Math.cos(elRad) * Math.sin(azRad));
        eye.y = target.y + this.radius * (float) Math.sin(elRad);
        eye.z = target.z + this.radius * (float) (Math.cos(elRad) * Math.cos(azRad));
    }

    public void orbit(float deltaAzimuth, float deltaElevation) {
        updateOrbitalPosition(radius, azimuthDeg + deltaAzimuth, elevationDeg + deltaElevation);
    }

    public void zoom(float deltaRadius) {
        updateOrbitalPosition(radius + deltaRadius, azimuthDeg, elevationDeg);
    }

    public void setPose(float azimuthDeg, float elevationDeg) {
        updateOrbitalPosition(radius, azimuthDeg, elevationDeg);
    }

    public void reset() {
        updateOrbitalPosition(initRadius, initAzimuth, initElevation);
    }

    public Vector3f getEye()   { return new Vector3f(eye); }
    public float getRadius()   { return radius; }
}
