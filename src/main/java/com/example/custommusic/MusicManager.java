package com.example.custommusic;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;

import java.io.*;
import java.util.*;

public class MusicManager {
    public static final MusicManager INSTANCE = new MusicManager();

    private final List<File> tracks = new ArrayList<File>();
    private File dir;
    private int current = -1;
    private int session = 0;
    private volatile boolean paused = false;
    private volatile boolean playing = false;
    private volatile float volume = 0.8f;
    private volatile AdvancedPlayer player;
    private final Object pauseLock = new Object();

    public void init(File musicDir) {
        this.dir = musicDir;
        if (!dir.exists()) dir.mkdirs();
        refresh();
    }

    public File getDir() { return dir; }
    public List<File> getTracks() { return tracks; }
    public int getCurrent() { return current; }
    public boolean isPaused() { return paused; }
    public boolean isPlaying() { return playing; }
    public float getVolume() { return volume; }

    public synchronized void refresh() {
        tracks.clear();
        File[] files = dir.listFiles(new FilenameFilter() {
            public boolean accept(File d, String n) { return n.toLowerCase().endsWith(".mp3"); }
        });
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
            });
            tracks.addAll(Arrays.asList(files));
        }
        if (current >= tracks.size()) current = -1;
    }

    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    public synchronized void play(int index) {
        if (index < 0 || index >= tracks.size()) return;
        stop();
        current = index;
        paused = false;
        playing = true;
        final int mySession = ++session;
        final File file = tracks.get(index);

        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    InputStream in = new BufferedInputStream(new FileInputStream(file));
                    try {
                        AdvancedPlayer p = new AdvancedPlayer(in, new PausableDevice());
                        player = p;
                        p.play();
                    } finally {
                        in.close();
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                synchronized (MusicManager.this) {
                    if (session == mySession) {
                        playing = false;
                        next(); // трек доиграл сам -> следующий
                    }
                }
            }
        }, "CustomMusic-Player");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        session++;
        playing = false;
        paused = false;
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        AdvancedPlayer p = player;
        player = null;
        if (p != null) p.close();
    }

    public synchronized void togglePause() {
        if (!playing) {
            if (current >= 0) play(current);
            else if (!tracks.isEmpty()) play(0);
            return;
        }
        paused = !paused;
        if (!paused) synchronized (pauseLock) { pauseLock.notifyAll(); }
    }

    public synchronized void next() {
        if (tracks.isEmpty()) return;
        play((current + 1) % tracks.size());
    }

    public synchronized void prev() {
        if (tracks.isEmpty()) return;
        play((current - 1 + tracks.size()) % tracks.size());
    }

    private class PausableDevice extends JavaSoundAudioDevice {
        @Override
        protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
            synchronized (pauseLock) {
                while (paused && playing) {
                    try { pauseLock.wait(100); } catch (InterruptedException e) { return; }
                }
            }
            super.writeImpl(samples, offs, len);
            try {
                float v = Math.max(volume, 0.0001f);
                float db = (float) (20.0 * Math.log10(v));
                setLineGain(Math.max(db, -70f));
            } catch (Throwable ignored) { }
        }
    }
}
