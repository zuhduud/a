package com.example.custommusic;

import java.io.File;

/** Один mp3-файл из папки custom_music вместе с тегами. */
public class Track {
    public final File file;
    public String title;
    public String artist = "";
    public String album = "";
    /** Длительность в мс, -1 пока не определена (определяется при первом запуске трека). */
    public volatile long durationMs = -1;
    /** Длительность одного mp3-кадра в мс (нужна для перемотки). */
    public volatile float msPerFrame = 0f;

    public Track(File file) {
        this.file = file;
        String n = file.getName();
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        this.title = n;
    }

    public String key() { return file.getName(); }

    public String searchText() {
        return (title + " " + artist + " " + album + " " + file.getName()).toLowerCase();
    }
}
