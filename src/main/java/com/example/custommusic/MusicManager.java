package com.example.custommusic;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;

import java.io.*;
import java.util.*;

public class MusicManager {
    public static final MusicManager INSTANCE = new MusicManager();

    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;

    private final List<Track> tracks = new ArrayList<Track>();
    private final Deque<Track> history = new ArrayDeque<Track>();
    private final Set<String> favorites = new HashSet<String>();
    private final Random random = new Random();
    private File dir;
    private File settingsFile;

    private volatile Track current;
    private int session = 0;
    private int failStreak = 0;
    private volatile boolean paused = false;
    private volatile boolean playing = false;
    private volatile float volume = 0.8f;
    private volatile boolean shuffle = false;
    private volatile int repeat = REPEAT_OFF;
    private volatile long startOffsetMs = 0;
    private volatile PausableDevice device;
    private volatile AdvancedPlayer player;
    private final Object pauseLock = new Object();

    // ---------- инициализация / настройки ----------

    public void init(File musicDir, File settings) {
        this.dir = musicDir;
        this.settingsFile = settings;
        if (!dir.exists()) dir.mkdirs();
        loadSettings();
        refresh();
    }

    private void loadSettings() {
        if (settingsFile == null || !settingsFile.exists()) return;
        Properties p = new Properties();
        try {
            InputStream in = new FileInputStream(settingsFile);
            try { p.load(new InputStreamReader(in, "UTF-8")); } finally { in.close(); }
            volume = clamp(Float.parseFloat(p.getProperty("volume", "0.8")));
            shuffle = Boolean.parseBoolean(p.getProperty("shuffle", "false"));
            repeat = Math.max(0, Math.min(2, Integer.parseInt(p.getProperty("repeat", "0"))));
            int n = Integer.parseInt(p.getProperty("fav.count", "0"));
            favorites.clear();
            for (int i = 0; i < n; i++) {
                String f = p.getProperty("fav." + i);
                if (f != null) favorites.add(f);
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void saveSettings() {
        if (settingsFile == null) return;
        Properties p = new Properties();
        p.setProperty("volume", Float.toString(volume));
        p.setProperty("shuffle", Boolean.toString(shuffle));
        p.setProperty("repeat", Integer.toString(repeat));
        p.setProperty("fav.count", Integer.toString(favorites.size()));
        int i = 0;
        for (String f : favorites) p.setProperty("fav." + (i++), f);
        try {
            File parent = settingsFile.getParentFile();
            if (parent != null) parent.mkdirs();
            OutputStream out = new FileOutputStream(settingsFile);
            try { p.store(new OutputStreamWriter(out, "UTF-8"), "Custom Music Player"); } finally { out.close(); }
        } catch (IOException ignored) {
        }
    }

    // ---------- библиотека ----------

    public File getDir() { return dir; }

    /** Копия списка: безопасно читать из потока отрисовки. */
    public synchronized List<Track> getTracks() { return new ArrayList<Track>(tracks); }

    public synchronized void refresh() {
        Map<String, Track> old = new HashMap<String, Track>();
        for (Track t : tracks) old.put(t.key(), t);
        tracks.clear();
        File[] files = dir == null ? null : dir.listFiles(new FilenameFilter() {
            public boolean accept(File d, String n) { return n.toLowerCase().endsWith(".mp3"); }
        });
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
            });
            for (File f : files) {
                Track t = old.get(f.getName());
                if (t == null) {
                    t = new Track(f);
                    Id3.read(t);
                }
                tracks.add(t);
            }
        }
        if (current != null && !tracks.contains(current)) {
            stop();
            current = null;
        }
        history.clear();
    }

    // ---------- состояние ----------

    public Track getCurrentTrack() { return current; }
    public boolean isPaused() { return paused; }
    public boolean isPlaying() { return playing; }
    public float getVolume() { return volume; }
    public boolean isShuffle() { return shuffle; }
    public int getRepeat() { return repeat; }

    public long getDurationMs() {
        Track t = current;
        return t == null ? -1 : t.durationMs;
    }

    public long getPositionMs() {
        PausableDevice d = device;
        long pos = startOffsetMs + (d == null ? 0 : d.getPosition());
        long dur = getDurationMs();
        return dur > 0 ? Math.min(pos, dur) : pos;
    }

    public void setVolume(float v) { volume = clamp(v); }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    public void toggleShuffle() { shuffle = !shuffle; saveSettings(); }
    public void cycleRepeat() { repeat = (repeat + 1) % 3; saveSettings(); }

    public synchronized boolean isFavorite(Track t) { return t != null && favorites.contains(t.key()); }

    public synchronized void toggleFavorite(Track t) {
        if (t == null) return;
        if (!favorites.remove(t.key())) favorites.add(t.key());
        saveSettings();
    }

    public synchronized int favoriteCount() {
        int n = 0;
        for (Track t : tracks) if (favorites.contains(t.key())) n++;
        return n;
    }

    // ---------- управление воспроизведением ----------

    public synchronized void play(Track t) { start(t, 0, false); }

    public synchronized void togglePause() {
        if (current == null) {
            if (!tracks.isEmpty()) play(tracks.get(0));
            return;
        }
        if (!playing) { start(current, 0, false); return; }
        paused = !paused;
        synchronized (pauseLock) { pauseLock.notifyAll(); }
    }

    public synchronized void next() {
        Track n = pickNext(false);
        if (n == null) return;
        if (current != null) pushHistory(current);
        start(n, 0, false);
    }

    public synchronized void prev() {
        if (current != null && playing && getPositionMs() > 3000) {
            start(current, 0, false);
            return;
        }
        Track p = history.pollFirst();
        if (p == null || !tracks.contains(p)) {
            if (tracks.isEmpty()) return;
            int idx = current == null ? 0 : tracks.indexOf(current);
            p = tracks.get((idx - 1 + tracks.size()) % tracks.size());
        }
        start(p, 0, false);
    }

    public synchronized void seekTo(long ms) {
        Track t = current;
        if (t == null) return;
        long dur = t.durationMs;
        if (dur <= 0) return;
        ms = Math.max(0, Math.min(ms, dur - 500));
        start(t, ms, paused && playing);
    }

    public void seekRelative(long deltaMs) { seekTo(getPositionMs() + deltaMs); }

    public synchronized void stop() {
        session++;
        playing = false;
        paused = false;
        AdvancedPlayer p = player;
        player = null;
        device = null;
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        if (p != null) {
            try { p.close(); } catch (Throwable ignored) {}
        }
    }

    private void pushHistory(Track t) {
        history.addFirst(t);
        while (history.size() > 50) history.pollLast();
    }

    private Track pickNext(boolean auto) {
        if (tracks.isEmpty()) return null;
        if (current == null) return tracks.get(0);
        int idx = tracks.indexOf(current);
        if (shuffle && tracks.size() > 1) {
            int n;
            do { n = random.nextInt(tracks.size()); } while (n == idx);
            return tracks.get(n);
        }
        int n = idx + 1;
        if (n >= tracks.size()) {
            if (auto && repeat == REPEAT_OFF) return null; // дошли до конца списка
            n = 0;
        }
        return tracks.get(n);
    }

    private synchronized void start(final Track t, final long startMs, final boolean startPaused) {
        stop();
        current = t;
        startOffsetMs = startMs;
        playing = true;
        paused = startPaused;
        final int mySession = ++session;
        final long startedAt = System.currentTimeMillis();
        Thread th = new Thread("CustomMusic-Player") {
            public void run() {
                boolean error = false;
                InputStream in = null;
                try {
                    ensureMeta(t);
                    in = new BufferedInputStream(new FileInputStream(t.file));
                    PausableDevice dev = new PausableDevice();
                    AdvancedPlayer p = new AdvancedPlayer(in, dev);
                    synchronized (MusicManager.this) {
                        if (session != mySession) return;
                        device = dev;
                        player = p;
                    }
                    int startFrame = t.msPerFrame > 0 ? (int) (startMs / t.msPerFrame) : 0;
                    p.play(startFrame, Integer.MAX_VALUE);
                } catch (Throwable e) {
                    error = true;
                } finally {
                    if (in != null) try { in.close(); } catch (IOException ignored) {}
                }
                synchronized (MusicManager.this) {
                    if (session != mySession) return;
                    playing = false;
                    paused = false;
                    boolean failed = error || System.currentTimeMillis() - startedAt < 400;
                    failStreak = failed ? failStreak + 1 : 0;
                    if (failStreak < 3) onTrackEnded();
                }
            }
        };
        th.setDaemon(true);
        th.start();
    }

    /** Вызывается под замком MusicManager, когда трек доиграл сам. */
    private void onTrackEnded() {
        Track cur = current;
        if (cur == null) return;
        if (repeat == REPEAT_ONE) { start(cur, 0, false); return; }
        Track n = pickNext(true);
        if (n == null) { startOffsetMs = 0; return; }
        pushHistory(cur);
        start(n, 0, false);
    }

    /** Считает длительность и длину кадра, если ещё не известны. */
    private static void ensureMeta(Track t) throws IOException, JavaLayerException {
        if (t.durationMs > 0 && t.msPerFrame > 0) return;
        InputStream in = new BufferedInputStream(new FileInputStream(t.file));
        try {
            Bitstream bs = new Bitstream(in);
            float total = 0f, first = 0f;
            Header h;
            while ((h = bs.readFrame()) != null) {
                float ms = h.ms_per_frame();
                if (first == 0f) first = ms;
                total += ms;
                bs.closeFrame();
            }
            if (first > 0f) t.msPerFrame = first;
            if (total > 0f) t.durationMs = (long) total;
        } finally {
            in.close();
        }
    }

    /** Аудиоустройство с паузой и громкостью (умножение сэмплов). */
    private class PausableDevice extends JavaSoundAudioDevice {
        @Override
        protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
            synchronized (pauseLock) {
                while (paused && playing) {
                    try { pauseLock.wait(100); } catch (InterruptedException e) { return; }
                }
            }
            float v = volume;
            float gain = v * v; // квадратичная кривая: на слух ровнее, чем линейная
            for (int i = offs; i < offs + len; i++) {
                samples[i] = (short) (samples[i] * gain);
            }
            super.writeImpl(samples, offs, len);
        }
    }
}
