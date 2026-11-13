package com.grafkom.viewing3d;

import com.grafkom.viewing3d.ProjectionTaxonomyManager.ProjectionType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 3D Visual Realism Suite (Minggu 11) - perluasan proyek ThreeDViewing (Minggu 10).
 * Memakai ulang Camera3D, PerspectiveProjection, ProjectionTaxonomyManager, dan
 * StereoAnaglyphRenderer; pipeline diganti ke Modern OpenGL 3.3 core + GLSL.
 *
 * Kontrol:
 *   Mouse kiri + geser : orbit        Scroll : zoom        [ ] : FOV
 *   B : Phong <-> Blinn-Phong         F : Flat <-> Smooth shading
 *   Z : Z-Buffer on/off               C : Back-Face Culling on/off
 *   T : filter tekstur Nearest <-> Bilinear+Mipmap       X : tekstur on/off
 *   H : bayangan on/off               V : shadow bias on/off (tampilkan Shadow Acne)
 *   M : ganti model (bola/torus/kubus) L : animasi cahaya on/off
 *   Panah kiri/kanan : putar cahaya   Panah atas/bawah : tinggi cahaya
 *   G : VSync on/off (matikan saat mengukur FPS)
 *   1..7 : jenis proyeksi (Minggu 10) SPASI : stereo anaglyph    R : reset    ESC : keluar
 *
 * Argumen opsional: path gambar tekstur sendiri (PNG/JPG), mis.  C:\gambar\kayu.jpg
 */
public class VisualRealismSuite {

    private static final float LIGHT_RADIUS = 5.0f;
    private static final float GROUND_Y = -1.5f;

    private final String textureSource;

    private long window;
    private int fbWidth = 1280, fbHeight = 720;

    private Camera3D camera;
    private PerspectiveProjection perspective;
    private final StereoAnaglyphRenderer stereo = new StereoAnaglyphRenderer();
    private ProjectionType projectionType = ProjectionType.PERSPECTIVE_3POINT;
    private boolean stereoEnabled = false;

    private ShaderProgram sceneShader, depthShader;
    private ShadowMapFBO shadowFbo;
    private Texture2D texture;
    private RealismMesh[] models;
    private RealismMesh ground, marker;
    private int modelIndex = 0;

    // Toggle fitur (indikator 1-5)
    private boolean blinnPhong = true;
    private boolean smoothShading = true;
    private boolean depthTest = true;
    private boolean backFaceCulling = true;
    private boolean mipmapped = true;
    private boolean useTexture = true;
    private boolean shadows = true;
    private boolean shadowBias = true;
    private boolean lightAnimate = true;
    private boolean vsync = true;

    private float lightAngle = 45.0f;   // derajat
    private float lightHeight = 5.5f;

    private boolean dragging = false;
    private double lastX, lastY, lastFrame, fpsTime;
    private int frames, fps;

    public VisualRealismSuite(String textureSource) {
        this.textureSource = textureSource;
    }

    public static void main(String[] args) {
        new VisualRealismSuite(args.length > 0 ? args[0] : "/textures/brick.png").run();
    }

    private void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }

    // ------------------------------------------------------------------ init
    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Gagal menginisialisasi GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);        // Modern OpenGL 3.3 core
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(fbWidth, fbHeight, "3D Visual Realism Suite", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Gagal membuat window (butuh OpenGL 3.3)");

        glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            fbWidth = width;
            fbHeight = Math.max(1, height);
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) dragging = (action == GLFW_PRESS);
        });
        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (dragging) camera.orbit((float) (lastX - x) * 0.4f, (float) (y - lastY) * 0.4f);
            lastX = x;
            lastY = y;
        });
        glfwSetScrollCallback(window, (w, xoff, yoff) -> camera.zoom((float) -yoff * 0.8f));
        glfwSetKeyCallback(window, this::onKey);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        int[] fw = new int[1], fh = new int[1];
        glfwGetFramebufferSize(window, fw, fh);
        fbWidth = fw[0];
        fbHeight = Math.max(1, fh[0]);

        // Objek aplikasi: kamera & proyeksi dari Minggu 10 (zNear 0.5 -> presisi depth lebih baik)
        camera = new Camera3D(new Vector3f(6f, 5f, 8f), new Vector3f(0f, 0f, 0f), new Vector3f(0f, 1f, 0f));
        perspective = new PerspectiveProjection(45.0f, (float) fbWidth / fbHeight, 0.5f, 100.0f);

        sceneShader = new ShaderProgram("/shaders/phong.vert", "/shaders/phong.frag");
        depthShader = new ShaderProgram("/shaders/depth.vert", "/shaders/depth.frag");
        shadowFbo = new ShadowMapFBO();
        texture = new Texture2D(textureSource);

        models = new RealismMesh[] {
            MeshFactory.createSphere("Bola", 1.0f, 12, 24),
            MeshFactory.createTorus("Torus", 1.0f, 0.4f, 24, 12),
            MeshFactory.createCube("Kubus", 0.9f)
        };
        ground = MeshFactory.createGround("Lantai", 8.0f, GROUND_Y, 4.0f);
        marker = MeshFactory.createSphere("Marker", 1.0f, 8, 12);

        lastFrame = fpsTime = glfwGetTime();
        updateTitle();
    }

    private void onKey(long w, int key, int scancode, int action, int mods) {
        if (action == GLFW_RELEASE) return;
        boolean press = (action == GLFW_PRESS);

        if (press && key >= GLFW_KEY_1 && key < GLFW_KEY_1 + ProjectionType.values().length) {
            projectionType = ProjectionType.values()[key - GLFW_KEY_1];
            camera.setPose(projectionType.getPresetAzimuth(), projectionType.getPresetElevation());
        } else {
            switch (key) {
                case GLFW_KEY_ESCAPE: if (press) glfwSetWindowShouldClose(w, true); break;
                case GLFW_KEY_B: if (press) blinnPhong = !blinnPhong; break;
                case GLFW_KEY_F: if (press) smoothShading = !smoothShading; break;
                case GLFW_KEY_Z: if (press) depthTest = !depthTest; break;
                case GLFW_KEY_C: if (press) backFaceCulling = !backFaceCulling; break;
                case GLFW_KEY_T:
                    if (press) { mipmapped = !mipmapped; texture.setMipmapped(mipmapped); }
                    break;
                case GLFW_KEY_X: if (press) useTexture = !useTexture; break;
                case GLFW_KEY_H: if (press) shadows = !shadows; break;
                case GLFW_KEY_V: if (press) shadowBias = !shadowBias; break;
                case GLFW_KEY_M: if (press) modelIndex = (modelIndex + 1) % models.length; break;
                case GLFW_KEY_L: if (press) lightAnimate = !lightAnimate; break;
                case GLFW_KEY_G:
                    if (press) { vsync = !vsync; glfwSwapInterval(vsync ? 1 : 0); }
                    break;
                case GLFW_KEY_R: if (press) camera.reset(); break;
                case GLFW_KEY_SPACE: if (press) stereoEnabled = !stereoEnabled; break;
                case GLFW_KEY_LEFT:  lightAngle -= 5.0f; break;
                case GLFW_KEY_RIGHT: lightAngle += 5.0f; break;
                case GLFW_KEY_UP:    lightHeight = Math.min(9.0f, lightHeight + 0.3f); break;
                case GLFW_KEY_DOWN:  lightHeight = Math.max(2.0f, lightHeight - 0.3f); break;
                case GLFW_KEY_LEFT_BRACKET:  perspective.setFovDegree(perspective.getFovDegree() - 2.0f); break;
                case GLFW_KEY_RIGHT_BRACKET: perspective.setFovDegree(perspective.getFovDegree() + 2.0f); break;
                default: break;
            }
        }
        updateTitle();
    }

    // ------------------------------------------------------------------ loop
    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) (now - lastFrame);
            lastFrame = now;
            if (lightAnimate) lightAngle += 25.0f * dt;

            frames++;
            if (now - fpsTime >= 0.5) {
                fps = (int) Math.round(frames / (now - fpsTime));
                frames = 0;
                fpsTime = now;
                updateTitle();
            }

            render();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private Vector3f lightPosition() {
        double a = Math.toRadians(lightAngle);
        return new Vector3f(LIGHT_RADIUS * (float) Math.cos(a), lightHeight, LIGHT_RADIUS * (float) Math.sin(a));
    }

    /** Light space = proyeksi ortografik (directional light) * view dari posisi cahaya. */
    private Matrix4f lightSpaceMatrix() {
        Matrix4f lightProj = ProjectionTaxonomyManager.orthographic(-7f, 7f, -7f, 7f, 1.0f, 20.0f);
        Matrix4f lightView = new Matrix4f().lookAt(lightPosition(), new Vector3f(0f, 0f, 0f), new Vector3f(0f, 1f, 0f));
        return lightProj.mul(lightView);
    }

    // ---------------------------------------------------------------- render
    private void render() {
        perspective.setAspectRatio((float) fbWidth / fbHeight);
        Matrix4f lightSpace = lightSpaceMatrix();

        if (shadows) renderShadowPass(lightSpace);     // PASS 1: depth dari sudut cahaya

        // PASS 2: render normal dari sudut kamera
        glViewport(0, 0, fbWidth, fbHeight);
        glClearColor(0.12f, 0.14f, 0.18f, 1.0f);
        HSRManager.setupDepthTest(depthTest);
        HSRManager.setupBackFaceCulling(backFaceCulling);

        if (stereoEnabled) {
            stereo.renderAnaglyph(offset -> renderScene(offset, lightSpace));
        } else {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderScene(0.0f, lightSpace);
        }
    }

    private void renderShadowPass(Matrix4f lightSpace) {
        shadowFbo.bindForWriting();
        HSRManager.setupDepthTest(true);               // shadow map selalu butuh depth test
        HSRManager.setupBackFaceCulling(backFaceCulling);

        depthShader.use();
        depthShader.setMat4("lightSpaceMatrix", lightSpace);
        depthShader.setMat4("model", new Matrix4f());
        models[modelIndex].draw(true);
        ground.draw(true);

        shadowFbo.unbind(fbWidth, fbHeight);
    }

    private void renderScene(float eyeOffset, Matrix4f lightSpace) {
        Matrix4f projection = ProjectionTaxonomyManager.buildProjection(
                projectionType, perspective, camera.getRadius(), eyeOffset);
        Matrix4f view = camera.calculateLookAtMatrix(eyeOffset);
        Matrix4f identity = new Matrix4f();
        Vector3f lightPos = lightPosition();

        sceneShader.use();
        sceneShader.setMat4("projection", projection);
        sceneShader.setMat4("view", view);
        sceneShader.setMat4("lightSpaceMatrix", lightSpace);
        sceneShader.setVec3("lightPos", lightPos);
        sceneShader.setVec3("viewPos", camera.getEye());
        sceneShader.setVec3("lightColor", 1.0f, 1.0f, 1.0f);
        sceneShader.setBool("useBlinnPhong", blinnPhong);
        sceneShader.setFloat("shininess", 32.0f);
        sceneShader.setBool("useTexture", useTexture);
        sceneShader.setBool("useShadow", shadows);
        sceneShader.setBool("useBias", shadowBias);
        sceneShader.setBool("grayscale", stereoEnabled);   // anaglyph: hindari kanal warna hilang
        sceneShader.setBool("unlit", false);
        sceneShader.setInt("textureSampler", 0);
        sceneShader.setInt("shadowMap", 1);

        texture.bind(0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, shadowFbo.getDepthTextureID());
        glActiveTexture(GL_TEXTURE0);

        // Objek utama
        sceneShader.setMat4("model", identity);
        sceneShader.setVec3("objectColor", 0.85f, 0.35f, 0.25f);
        sceneShader.setFloat("specularStrength", 1.0f);
        models[modelIndex].draw(smoothShading);

        // Lantai (digambar setelah objek: bila Z-Buffer dimatikan, lantai menimpa objek)
        sceneShader.setVec3("objectColor", 0.45f, 0.55f, 0.45f);
        sceneShader.setFloat("specularStrength", 0.6f);
        ground.draw(true);

        // Penanda posisi cahaya
        sceneShader.setBool("unlit", true);
        sceneShader.setVec3("objectColor", 1.0f, 0.95f, 0.4f);
        sceneShader.setMat4("model", new Matrix4f().translate(lightPos).scale(0.18f));
        marker.draw(true);
    }

    private void updateTitle() {
        glfwSetWindowTitle(window, String.format(
                "VRS | FPS %d (VSync %s) | %s | %s | Z-Buf %s | Cull %s | Tex %s/%s | Shadow %s (bias %s) | Proj [%d] | %s%s",
                fps,
                vsync ? "ON" : "OFF",
                blinnPhong ? "Blinn-Phong" : "Phong",
                smoothShading ? "Smooth" : "Flat",
                depthTest ? "ON" : "OFF",
                backFaceCulling ? "ON" : "OFF",
                useTexture ? "ON" : "OFF",
                mipmapped ? "Bilinear+Mipmap" : "Nearest",
                shadows ? "ON" : "OFF",
                shadowBias ? "ON" : "OFF",
                projectionType.ordinal() + 1,
                models[modelIndex].getName(),
                stereoEnabled ? " | Stereo" : ""));
    }
}