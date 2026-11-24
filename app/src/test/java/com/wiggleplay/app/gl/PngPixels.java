package com.wiggleplay.app.gl;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads the pixels of a non-interlaced 8-bit RGB or RGBA PNG with plain Java (the unit tests compile against the Android
 * stubs, which have no ImageIO). Only what the palette check needs.
 */
final class PngPixels {

    final int width, height;
    private final int[] rgb;

    PngPixels(File f) throws IOException {
        byte[] all;
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            all = new byte[(int) f.length()];
            in.readFully(all);
        }
        int pos = 8, w = 0, h = 0, channels = 0;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        while (pos < all.length) {
            int len = ((all[pos] & 255) << 24) | ((all[pos + 1] & 255) << 16) | ((all[pos + 2] & 255) << 8) | (all[pos + 3] & 255);
            String type = new String(all, pos + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int d = pos + 8;
            if (type.equals("IHDR")) {
                w = ((all[d] & 255) << 24) | ((all[d + 1] & 255) << 16) | ((all[d + 2] & 255) << 8) | (all[d + 3] & 255);
                h = ((all[d + 4] & 255) << 24) | ((all[d + 5] & 255) << 16) | ((all[d + 6] & 255) << 8) | (all[d + 7] & 255);
                if (all[d + 8] != 8 || all[d + 12] != 0) throw new IOException("need 8-bit, non-interlaced");
                channels = all[d + 9] == 6 ? 4 : all[d + 9] == 2 ? 3 : 0;
                if (channels == 0) throw new IOException("need RGB or RGBA");
            } else if (type.equals("IDAT")) {
                idat.write(all, d, len);
            }
            pos = d + len + 4;
        }
        width = w;
        height = h;
        byte[] raw = new byte[h * (1 + w * channels)];
        Inflater inf = new Inflater();
        inf.setInput(idat.toByteArray());
        try {
            int got = 0;
            while (got < raw.length && !inf.finished()) got += inf.inflate(raw, got, raw.length - got);
        } catch (DataFormatException e) {
            throw new IOException(e);
        } finally {
            inf.end();
        }
        rgb = new int[w * h];
        int stride = w * channels;
        byte[] prev = new byte[stride], cur = new byte[stride];
        for (int y = 0; y < h; y++) {
            int o = y * (1 + stride);
            int ft = raw[o] & 255;
            for (int i = 0; i < stride; i++) {
                int a = i >= channels ? cur[i - channels] & 255 : 0, b = prev[i] & 255, c = i >= channels ? prev[i - channels] & 255 : 0;
                int v = raw[o + 1 + i] & 255;
                switch (ft) {
                    case 1: v += a; break;
                    case 2: v += b; break;
                    case 3: v += (a + b) / 2; break;
                    case 4: {
                        int pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c);
                        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
                        break;
                    }
                    default: break;
                }
                cur[i] = (byte) v;
            }
            for (int x = 0; x < w; x++) {
                rgb[y * w + x] = ((cur[x * channels] & 255) << 16) | ((cur[x * channels + 1] & 255) << 8) | (cur[x * channels + 2] & 255);
            }
            byte[] t = prev; prev = cur; cur = t;
        }
    }

    /** @return the pixel as 0xRRGGBB */
    int rgb(int x, int y) {
        return rgb[y * width + x];
    }
}
