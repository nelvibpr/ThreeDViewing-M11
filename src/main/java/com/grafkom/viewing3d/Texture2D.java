package com.grafkom.viewing3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBImage.*;

/**
 * Texture 2D: memuat PNG/JPG dengan STBImage ke GPU.
 * Sumber gambar: (1) file di disk, (2) resource classpath, (3) fallback papan catur prosedural.
 */
public class Texture2D {

    private final int textureID;

    public Texture2D(String source) {
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        // Wrap + filtering default (Bilinear + Mipmap)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        setMipmapped(true);

        byte[] bytes = readBytes(source);
        boolean ok = false;
        if (bytes != null) ok = loadFromBytes(bytes);
        if (!ok) {
            System.err.println("Tekstur gagal dimuat (" + source + "), memakai papan catur prosedural.");
            uploadCheckerboard();
        }
    }

    /** Baca byte dari file disk; jika tidak ada, coba resource classpath. */
    private static byte[] readBytes(String source) {
        try {
            Path p = Path.of(source);
            if (Files.isRegularFile(p)) return Files.readAllBytes(p);
        } catch (Exception ignored) { /* bukan path file valid -> coba classpath */ }
        try (InputStream in = Texture2D.class.getResourceAsStream(source.startsWith("/") ? source : "/" + source)) {
            if (in != null) return in.readAllBytes();
        } catch (IOException ignored) { }
        return null;
    }

    private boolean loadFromBytes(byte[] bytes) {
        stbi_set_flip_vertically_on_load(true);   // origin gambar di kiri-atas -> V=0 di bawah
        ByteBuffer raw = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            raw.put(bytes).flip();
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer data = stbi_load_from_memory(raw, width, height, channels, 4);
            if (data == null) return false;
            upload(data, width.get(0), height.get(0));
            stbi_image_free(data);
            return true;
        } finally {
            MemoryUtil.memFree(raw);
        }
    }

    private void uploadCheckerboard() {
        int size = 256, cell = 32;
        ByteBuffer data = MemoryUtil.memAlloc(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean light = ((x / cell) + (y / cell)) % 2 == 0;
                byte c = (byte) (light ? 220 : 60);
                data.put(c).put(c).put(c).put((byte) 255);
            }
        }
        data.flip();
        upload(data, size, size);
        MemoryUtil.memFree(data);
    }

    private void upload(ByteBuffer data, int w, int h) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glGenerateMipmap(GL_TEXTURE_2D);
    }

    /** true = Bilinear + Mipmap (trilinear); false = Nearest Neighbor. */
    public void setMipmapped(boolean mipmapped) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mipmapped ? GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mipmapped ? GL_LINEAR : GL_NEAREST);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
    }
}
