package com.grafkom.viewing3d;

import com.grafkom.viewing3d.ProjectionTaxonomyManager.ProjectionType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 3D Viewing Interactive Visualizer (Main Class).
 *
 * Kontrol:
 *   Mouse kiri + geser : orbit kamera        Scroll : zoom
 *   1..7               : pilih proyeksi (kamera disetel ke pose kanonik proyeksi itu)
 *   SPASI              : stereo Anaglyph Red-Cyan on/off
 *   Panah atas/bawah   : FOV +/-             Panah kiri/kanan : IPD -/+
 *   M                  : ganti model (kubus / rumah)
 *   R                  : reset kamera        ESC : keluar
 */
public class ThreeDViewing {

    private long window;
    private int fbWidth = 1024, fbHeight = 720;

    private Camera3D camera;
    private PerspectiveProjection perspective;
    private final StereoAnaglyphRenderer stereo = new StereoAnaglyphRenderer();
    private ProjectionType projectionType = ProjectionType.PERSPECTIVE_3POINT;
    private boolean stereoEnabled = false;

    private Mesh3D[] models;
    private int modelIndex = 0;

    private boolean dragging = false;
    private double lastX, lastY;

    public static void main(String[] args) {
        new ThreeDViewing().run();
    }

    private void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Gagal menginisialisasi GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(fbWidth, fbHeight, "3D Viewing", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Gagal membuat window GLFW");

        // --- Callback input ---
        glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            fbWidth = width;
            fbHeight = Math.max(1, height);
        });

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) dragging = (action == GLFW_PRESS);
        });

        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (dragging) {
                float dx = (float) (x - lastX);
                float dy = (float) (y - lastY);
                camera.orbit(-dx * 0.4f, dy * 0.4f);
            }
            lastX = x;
            lastY = y;
        });

        glfwSetScrollCallback(window, (w, xoff, yoff) -> camera.zoom((float) -yoff * 0.6f));

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE) return;
            boolean press = (action == GLFW_PRESS);

            if (press && key >= GLFW_KEY_1 && key < GLFW_KEY_1 + ProjectionType.values().length) {
                selectProjection(ProjectionType.values()[key - GLFW_KEY_1]);
            } else {
                switch (key) {
                    case GLFW_KEY_ESCAPE: if (press) glfwSetWindowShouldClose(w, true); break;
                    case GLFW_KEY_SPACE:  if (press) stereoEnabled = !stereoEnabled; break;
                    case GLFW_KEY_M:      if (press) modelIndex = (modelIndex + 1) % models.length; break;
                    case GLFW_KEY_R:      if (press) camera.reset(); break;
                    case GLFW_KEY_UP:     perspective.setFovDegree(perspective.getFovDegree() + 2.0f); break;
                    case GLFW_KEY_DOWN:   perspective.setFovDegree(perspective.getFovDegree() - 2.0f); break;
                    case GLFW_KEY_RIGHT:  stereo.setIpd(stereo.getIpd() + 0.02f); break;
                    case GLFW_KEY_LEFT:   stereo.setIpd(stereo.getIpd() - 0.02f); break;
                    default: break;
                }
            }
            updateTitle();
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        int[] fbW = new int[1], fbH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);
        fbWidth = fbW[0];
        fbHeight = Math.max(1, fbH[0]);

        // --- Objek aplikasi ---
        camera = new Camera3D(new Vector3f(4f, 3f, 6f), new Vector3f(0f, 0f, 0f), new Vector3f(0f, 1f, 0f));
        perspective = new PerspectiveProjection(45.0f, (float) fbWidth / fbHeight, 0.1f, 100.0f);
        models = new Mesh3D[] { Mesh3D.createColorCube(1.0f), Mesh3D.createHouse() };

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        updateTitle();
    }

    private void selectProjection(ProjectionType type) {
        projectionType = type;
        camera.setPose(type.getPresetAzimuth(), type.getPresetElevation());
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            render();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void render() {
        glViewport(0, 0, fbWidth, fbHeight);
        perspective.setAspectRatio((float) fbWidth / fbHeight);
        glClearColor(0.07f, 0.07f, 0.09f, 1.0f);

        if (stereoEnabled) {
            stereo.renderAnaglyph(this::renderEye);
        } else {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderEye(0.0f);
        }
    }

    /** Render satu pandangan; eyeOffset = 0 untuk mode biasa (non-stereo). */
    private void renderEye(float eyeOffset) {
        Matrix4f projection = ProjectionTaxonomyManager.buildProjection(
                projectionType, perspective, camera.getRadius(), eyeOffset);
        Matrix4f view = camera.calculateLookAtMatrix(eyeOffset);

        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(projection.get(new float[16]));
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrixf(view.get(new float[16]));

        drawGrid();
        models[modelIndex].draw(stereoEnabled); // anaglyph -> grayscale
    }

    private void drawGrid() {
        glColor3f(0.30f, 0.32f, 0.38f);
        glBegin(GL_LINES);
        for (int i = -6; i <= 6; i++) {
            glVertex3f(i, -1.002f, -6);  glVertex3f(i, -1.002f, 6);
            glVertex3f(-6, -1.002f, i);  glVertex3f(6, -1.002f, i);
        }
        glEnd();
    }

    private void updateTitle() {
        glfwSetWindowTitle(window, String.format(
                "3D Viewing | [%d] %s | Stereo: %s (IPD %.2f) | FOV %.0f deg | Model: %s",
                projectionType.ordinal() + 1, projectionType.getLabel(),
                stereoEnabled ? "ON" : "OFF", stereo.getIpd(),
                perspective.getFovDegree(), models[modelIndex].getName()));
    }
}
