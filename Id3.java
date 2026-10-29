package com.example.custommusic;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

/** Минимальный читатель ID3v2 (v2.2/2.3/2.4) и ID3v1: название, исполнитель, альбом. */
public final class Id3 {
    private Id3() {}

    public static void read(Track t) {
        try {
            RandomAccessFile raf = new RandomAccessFile(t.file, "r");
            try {
                readV2(raf, t);
                if (t.artist.isEmpty() && t.album.isEmpty()) readV1(raf, t);
            } finally {
                raf.close();
            }
        } catch (Throwable ignored) {
        }
        // запасной вариант: "Исполнитель - Название" из имени файла
        if (t.artist.isEmpty()) {
            int i = t.title.indexOf(" - ");
            if (i > 0 && i < t.title.length() - 3) {
                t.artist = t.title.substring(0, i).trim();
                t.title = t.title.substring(i + 3).trim();
            }
        }
    }

    private static void readV2(RandomAccessFile raf, Track t) throws IOException {
        if (raf.length() < 10) return;
        byte[] h = new byte[10];
        raf.seek(0);
        raf.readFully(h);
        if (h[0] != 'I' || h[1] != 'D' || h[2] != '3') return;
        int ver = h[3] & 0xFF;
        if (ver < 2 || ver > 4) return;
        int flags = h[5] & 0xFF;
        long tagSize = syncsafe(h, 6);
        long pos = 10;
        long end = Math.min(10 + tagSize, raf.length());
        if (ver >= 3 && (flags & 0x40) != 0) { // расширенный заголовок
            byte[] e = new byte[4];
            raf.seek(pos);
            raf.readFully(e);
            pos += (ver == 4) ? syncsafe(e, 0) : (4 + be32(e, 0));
        }
        int idLen = ver == 2 ? 3 : 4;
        int hdrLen = ver == 2 ? 6 : 10;
        boolean gotTitle = false, gotArtist = false, gotAlbum = false;
        while (pos + hdrLen <= end) {
            byte[] fh = new byte[hdrLen];
            raf.seek(pos);
            raf.readFully(fh);
            if (fh[0] == 0) break; // паддинг
            String id = new String(fh, 0, idLen, Charset.forName("ISO-8859-1"));
            long size;
            if (ver == 2) size = ((fh[3] & 0xFF) << 16) | ((fh[4] & 0xFF) << 8) | (fh[5] & 0xFF);
            else if (ver == 3) size = be32(fh, 4);
            else size = syncsafe(fh, 4);
            long dataPos = pos + hdrLen;
            if (size <= 0 || dataPos + size > end) break;
            boolean wantTitle = id.equals("TIT2") || id.equals("TT2");
            boolean wantArtist = id.equals("TPE1") || id.equals("TP1");
            boolean wantAlbum = id.equals("TALB") || id.equals("TAL");
            if ((wantTitle || wantArtist || wantAlbum) && size < 4096) {
                byte[] data = new byte[(int) size];
                raf.seek(dataPos);
                raf.readFully(data);
                String s = decode(data);
                if (!s.isEmpty()) {
                    if (wantTitle) { t.title = s; gotTitle = true; }
                    else if (wantArtist) { t.artist = s; gotArtist = true; }
                    else { t.album = s; gotAlbum = true; }
                }
                if (gotTitle && gotArtist && gotAlbum) return;
            }
            pos = dataPos + size;
        }
    }

    private static void readV1(RandomAccessFile raf, Track t) throws IOException {
        if (raf.length() < 128) return;
        byte[] b = new byte[128];
        raf.seek(raf.length() - 128);
        raf.readFully(b);
        if (b[0] != 'T' || b[1] != 'A' || b[2] != 'G') return;
        String title = clean(b, 3, 30), artist = clean(b, 33, 30), album = clean(b, 63, 30);
        if (!title.isEmpty()) t.title = title;
        if (!artist.isEmpty()) t.artist = artist;
        if (!album.isEmpty()) t.album = album;
    }

    private static String clean(byte[] b, int off, int len) {
        byte[] d = new byte[len];
        System.arraycopy(b, off, d, 0, len);
        return decodeLegacy(d).trim();
    }

    private static String decode(byte[] d) {
        if (d.length < 2) return "";
        int enc = d[0] & 0xFF;
        byte[] body = new byte[d.length - 1];
        System.arraycopy(d, 1, body, 0, body.length);
        String s;
        switch (enc) {
            case 1: s = new String(body, Charset.forName("UTF-16")); break;
            case 2: s = new String(body, Charset.forName("UTF-16BE")); break;
            case 3: s = new String(body, Charset.forName("UTF-8")); break;
            default: s = decodeLegacy(body);
        }
        int nul = s.indexOf('\0');
        if (nul >= 0) s = s.substring(0, nul);
        return s.trim();
    }

    /** ASCII остаётся ASCII, всё остальное считаем windows-1251 (типично для русских тегов). */
    private static String decodeLegacy(byte[] b) {
        int n = b.length;
        while (n > 0 && b[n - 1] == 0) n--;
        boolean ascii = true;
        for (int i = 0; i < n; i++) if ((b[i] & 0x80) != 0) { ascii = false; break; }
        Charset cs = Charset.forName(ascii ? "US-ASCII" : "windows-1251");
        String s = new String(b, 0, n, cs);
        int nul = s.indexOf('\0');
        return nul >= 0 ? s.substring(0, nul) : s;
    }

    private static long syncsafe(byte[] b, int o) {
        return ((b[o] & 0x7FL) << 21) | ((b[o + 1] & 0x7FL) << 14) | ((b[o + 2] & 0x7FL) << 7) | (b[o + 3] & 0x7FL);
    }

    private static long be32(byte[] b, int o) {
        return ((b[o] & 0xFFL) << 24) | ((b[o + 1] & 0xFFL) << 16) | ((b[o + 2] & 0xFFL) << 8) | (b[o + 3] & 0xFFL);
    }
}
