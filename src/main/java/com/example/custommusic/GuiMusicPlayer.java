package com.example.custommusic;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.awt.Desktop;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Плеер в тёмно-фиолетовом стиле: список треков, поиск, прогресс и громкость с перетаскиванием. */
public class GuiMusicPlayer extends GuiScreen {
    // палитра
    private static final int C_PANEL = 0xF0161420;
    private static final int C_PANEL2 = 0xFF1D1A2B;
    private static final int C_BTN = 0xFF2A2740;
    private static final int C_BTN_HOVER = 0xFF39345A;
    private static final int C_ACCENT = 0xFF9B6BFF;
    private static final int C_ACCENT_DARK = 0xFF6D45C9;
    private static final int C_TEXT = 0xFFF2EFFF;
    private static final int C_MUTED = 0xFF8A86A3;
    private static final int C_TRACK_BG = 0xFF2E2A47;

    private static final int ROW_H = 20;
    private static final int DRAG_NONE = 0, DRAG_PROGRESS = 1, DRAG_VOLUME = 2, DRAG_SCROLL = 3;

    private final MusicManager mm = MusicManager.INSTANCE;

    private GuiTextField searchField;
    private final List<Track> filtered = new ArrayList<Track>();
    private int totalCount, favCount;
    private int tab = 0; // 0 - все, 1 - любимые
    private int scroll = 0;
    private int drag = DRAG_NONE;
    private float dragFrac = 0f;
    private String status = "";
    private long statusUntil = 0;

    // раскладка
    private int px, py, pw, ph;
    private int leftX, leftW, rightX, rightW;
    private int listTop, listBottom, rows;
    private int progY, ctrlY, modeY, volSliderX, volSliderW;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        pw = Math.min(width - 12, 440);
        ph = Math.min(height - 12, 240);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        leftX = px + 8;
        leftW = Math.max(110, Math.min(150, pw * 36 / 100));
        rightX = leftX + leftW + 10;
        rightW = px + pw - 8 - rightX;

        listTop = py + 70;
        listBottom = py + ph - 8;
        rows = Math.max(1, (listBottom - listTop) / ROW_H);

        progY = py + ph - 62;
        modeY = progY + 8;
        ctrlY = py + ph - 34;

        searchField = new GuiTextField(0, fontRenderer, leftX + 5, py + 35, leftW - 10, 12);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setMaxStringLength(40);
        searchField.setTextColor(C_TEXT);

        reloadLibrary();
        scrollToCurrent();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        mm.saveSettings();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
    }

    // ---------- данные ----------

    private void reloadLibrary() {
        totalCount = mm.getTracks().size();
        applyFilter();
    }

    private void applyFilter() {
        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        filtered.clear();
        favCount = 0;
        for (Track t : mm.getTracks()) {
            boolean fav = mm.isFavorite(t);
            if (fav) favCount++;
            if (tab == 1 && !fav) continue;
            if (!q.isEmpty() && !t.searchText().contains(q)) continue;
            filtered.add(t);
        }
        clampScroll();
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - rows)));
    }

    private void scrollToCurrent() {
        Track cur = mm.getCurrentTrack();
        int i = filtered.indexOf(cur);
        if (i >= 0) {
            scroll = Math.max(0, i - rows / 2);
            clampScroll();
        }
    }

    private void setStatus(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    // ---------- отрисовка ----------

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();

        // панель
        fillRound(px, py, px + pw, py + ph, C_PANEL);
        drawRect(px + 1, py, px + pw - 1, py + 2, C_ACCENT);
        drawRect(px + 8, py + 26, px + pw - 8, py + 27, 0xFF2A2740);

        drawHeader(mx, my);
        drawLeft(mx, my);
        drawRight(mx, my);
    }

    private void drawHeader(int mx, int my) {
        fontRenderer.drawStringWithShadow("Custom Music", leftX, py + 9, C_ACCENT);
        int cx = leftX + fontRenderer.getStringWidth("Custom Music") + 8;
        fontRenderer.drawString(totalCount + " " + plural(totalCount), cx, py + 9, C_MUTED);

        int bx = px + pw - 8;
        int wFolder = fontRenderer.getStringWidth("Папка") + 12;
        int wRefresh = fontRenderer.getStringWidth("Обновить") + 12;
        bx -= wFolder;
        button(bx, py + 6, wFolder, 14, "Папка", mx, my, false);
        int folderX = bx;
        bx -= wRefresh + 4;
        button(bx, py + 6, wRefresh, 14, "Обновить", mx, my, false);
        int refreshX = bx;

        if (System.currentTimeMillis() < statusUntil) {
            int maxW = refreshX - 8 - (cx + 60);
            if (maxW > 20) {
                String s = fontRenderer.trimStringToWidth(status, maxW);
                fontRenderer.drawString(s, refreshX - 6 - fontRenderer.getStringWidth(s), py + 9, C_MUTED);
            }
        }
        // запоминаем для клика
        hFolderX = folderX; hFolderW = wFolder; hRefreshX = refreshX; hRefreshW = wRefresh;
    }

    private int hFolderX, hFolderW, hRefreshX, hRefreshW;

    private void drawLeft(int mx, int my) {
        // поиск
        fillRound(leftX, py + 31, leftX + leftW, py + 49, C_PANEL2);
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            fontRenderer.drawString("Поиск треков...", leftX + 5, py + 35, C_MUTED);
        }
        searchField.drawTextBox();

        // вкладки
        int tw = (leftW - 4) / 2;
        button(leftX, py + 52, tw, 14, "Все " + totalCount, mx, my, tab == 0);
        button(leftX + tw + 4, py + 52, tw, 14, "Любимые " + favCount, mx, my, tab == 1);

        // список
        fillRound(leftX, listTop - 2, leftX + leftW, listBottom, C_PANEL2);
        if (filtered.isEmpty()) {
            String m1, m2;
            if (totalCount == 0) { m1 = "Папка пуста"; m2 = "Положи mp3 в custom_music"; }
            else if (tab == 1 && favCount == 0) { m1 = "Нет любимых"; m2 = "ПКМ по треку или сердце"; }
            else { m1 = "Ничего не найдено"; m2 = ""; }
            drawCentered(m1, leftX + leftW / 2, listTop + 14, C_MUTED);
            if (!m2.isEmpty()) drawCentered(fontRenderer.trimStringToWidth(m2, leftW - 8), leftX + leftW / 2, listTop + 26, C_MUTED);
            return;
        }
        Track cur = mm.getCurrentTrack();
        boolean sbar = filtered.size() > rows;
        int textW = leftW - 16 - (sbar ? 5 : 0);
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            if (i >= filtered.size()) break;
            Track t = filtered.get(i);
            int ry = listTop + r * ROW_H;
            boolean hover = mx >= leftX && mx < leftX + leftW - (sbar ? 5 : 0) && my >= ry && my < ry + ROW_H;
            boolean isCur = t == cur;
            if (isCur) fillRound(leftX + 1, ry, leftX + leftW - 1 - (sbar ? 5 : 0), ry + ROW_H - 1, 0x669B6BFF);
            else if (hover) fillRound(leftX + 1, ry, leftX + leftW - 1 - (sbar ? 5 : 0), ry + ROW_H - 1, 0x33FFFFFF);
            if (isCur) {
                if (mm.isPlaying() && !mm.isPaused()) triRight(leftX + 4, ry + 6, 8, C_TEXT);
                else { drawRect(leftX + 4, ry + 6, leftX + 6, ry + 14, C_TEXT); drawRect(leftX + 8, ry + 6, leftX + 10, ry + 14, C_TEXT); }
            }
            fontRenderer.drawString(fontRenderer.trimStringToWidth(t.title, textW), leftX + 14, ry + 2, isCur ? C_TEXT : 0xFFDAD6EE);
            String sub = t.artist.isEmpty() ? "Неизвестный исполнитель" : t.artist;
            fontRenderer.drawString(fontRenderer.trimStringToWidth(sub, textW), leftX + 14, ry + 11, isCur ? 0xFFD2C4FF : C_MUTED);
        }
        if (sbar) {
            int sx = leftX + leftW - 5, trackH = listBottom - listTop;
            drawRect(sx, listTop, sx + 3, listBottom - 2, C_TRACK_BG);
            int thumbH = Math.max(12, trackH * rows / filtered.size());
            int maxScroll = filtered.size() - rows;
            int thumbY = listTop + (maxScroll == 0 ? 0 : (trackH - 2 - thumbH) * scroll / maxScroll);
            drawRect(sx, thumbY, sx + 3, thumbY + thumbH, drag == DRAG_SCROLL ? C_ACCENT : C_ACCENT_DARK);
        }
    }

    private void drawRight(int mx, int my) {
        Track cur = mm.getCurrentTrack();

        // обложка-заглушка
        int cs = 52, cy = py + 33;
        int coverColor = 0xFF2A2740;
        String letter = "";
        if (cur != null) {
            float hue = (Math.abs(cur.title.hashCode()) % 360) / 360f;
            coverColor = 0xFF000000 | Color.HSBtoRGB(hue, 0.45f, 0.55f);
            letter = cur.title.isEmpty() ? "?" : cur.title.substring(0, 1).toUpperCase();
        }
        fillRound(rightX, cy, rightX + cs, cy + cs, coverColor);
        GlStateManager.pushMatrix();
        GlStateManager.translate(rightX + cs / 2f, cy + cs / 2f - 8f, 0f);
        GlStateManager.scale(2f, 2f, 1f);
        if (!letter.isEmpty()) {
            int lw = fontRenderer.getStringWidth(letter);
            fontRenderer.drawStringWithShadow(letter, -lw / 2f, 0f, C_TEXT);
        }
        GlStateManager.popMatrix();

        // название
        int tx = rightX + cs + 8;
        int tw = rightX + rightW - tx - 14;
        if (cur != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(tx, cy + 2, 0f);
            GlStateManager.scale(1.5f, 1.5f, 1f);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(cur.title, (int) (tw / 1.5f)), 0, 0, C_ACCENT);
            GlStateManager.popMatrix();
            fontRenderer.drawString(fontRenderer.trimStringToWidth(cur.artist.isEmpty() ? "Неизвестный исполнитель" : cur.artist, tw), tx, cy + 20, C_TEXT);
            if (!cur.album.isEmpty()) fontRenderer.drawString(fontRenderer.trimStringToWidth(cur.album, tw), tx, cy + 32, C_MUTED);
            drawHeart(rightX + rightW - 10, cy + 2, mm.isFavorite(cur));
        } else {
            fontRenderer.drawStringWithShadow("Ничего не играет", tx, cy + 4, C_MUTED);
            fontRenderer.drawString("Выбери трек слева", tx, cy + 20, C_MUTED);
        }

        // эквалайзер
        int eqTop = py + 94, eqBottom = progY - 8;
        if (eqBottom - eqTop >= 14) {
            fillRound(rightX, eqTop, rightX + rightW, eqBottom, C_PANEL2);
            drawEqualizer(rightX + 6, eqTop + 4, rightW - 12, eqBottom - eqTop - 8);
        }

        // прогресс
        long dur = mm.getDurationMs();
        long pos = mm.getPositionMs();
        float frac = dur > 0 ? Math.min(1f, (float) pos / dur) : 0f;
        if (drag == DRAG_PROGRESS) frac = dragFrac;
        boolean progHover = cur != null && dur > 0 && inProgress(mx, my);
        slider(rightX, progY, rightW, frac, progHover || drag == DRAG_PROGRESS, cur != null && dur > 0);
        long shown = drag == DRAG_PROGRESS ? (long) (dragFrac * dur) : pos;
        fontRenderer.drawString(cur == null ? "0:00" : fmt(shown), rightX, progY + 10, C_MUTED);
        String d = dur > 0 ? fmt(dur) : "--:--";
        fontRenderer.drawString(d, rightX + rightW - fontRenderer.getStringWidth(d), progY + 10, C_MUTED);

        // режимы
        String rep = mm.getRepeat() == MusicManager.REPEAT_OFF ? "Повтор: выкл" : mm.getRepeat() == MusicManager.REPEAT_ALL ? "Повтор: все" : "Повтор: трек";
        int wS = fontRenderer.getStringWidth("Случайно") + 12;
        int wR = fontRenderer.getStringWidth("Повтор: трек") + 12;
        int mxs = rightX + rightW / 2 - (wS + 4 + wR) / 2;
        button(mxs, modeY, wS, 13, "Случайно", mx, my, mm.isShuffle());
        button(mxs + wS + 4, modeY, wR, 13, rep, mx, my, mm.getRepeat() != MusicManager.REPEAT_OFF);

        // кнопки управления
        int x = rightX;
        button(x, ctrlY + 3, 28, 16, "-10", mx, my, false);
        x += 28 + 6;
        boolean hp = hit(mx, my, x, ctrlY, 22, 22);
        fillRound(x, ctrlY, x + 22, ctrlY + 22, hp ? C_BTN_HOVER : C_BTN);
        drawRect(x + 7, ctrlY + 6, x + 9, ctrlY + 16, C_TEXT);
        triLeft(x + 9, ctrlY + 6, 10, C_TEXT);
        x += 22 + 6;
        boolean hPlay = hit(mx, my, x, ctrlY - 2, 26, 26);
        fillRound(x, ctrlY - 2, x + 26, ctrlY + 24, hPlay ? 0xFFB48BFF : C_ACCENT);
        boolean showPause = mm.isPlaying() && !mm.isPaused();
        if (showPause) {
            drawRect(x + 8, ctrlY + 5, x + 11, ctrlY + 17, 0xFF140F24);
            drawRect(x + 15, ctrlY + 5, x + 18, ctrlY + 17, 0xFF140F24);
        } else {
            triRight(x + 10, ctrlY + 5, 12, 0xFF140F24);
        }
        x += 26 + 6;
        boolean hn = hit(mx, my, x, ctrlY, 22, 22);
        fillRound(x, ctrlY, x + 22, ctrlY + 22, hn ? C_BTN_HOVER : C_BTN);
        triRight(x + 6, ctrlY + 6, 10, C_TEXT);
        drawRect(x + 14, ctrlY + 6, x + 16, ctrlY + 16, C_TEXT);
        x += 22 + 6;
        button(x, ctrlY + 3, 28, 16, "+10", mx, my, false);
        x += 28 + 12;

        // громкость
        drawSpeaker(x, ctrlY + 6);
        String pct = Math.round(mm.getVolume() * 100) + "%";
        int pctW = fontRenderer.getStringWidth("100%");
        volSliderX = x + 14;
        volSliderW = Math.max(30, rightX + rightW - pctW - 6 - volSliderX);
        slider(volSliderX, ctrlY + 10, volSliderW, mm.getVolume(), drag == DRAG_VOLUME || inVolume(mx, my), true);
        fontRenderer.drawString(pct, rightX + rightW - fontRenderer.getStringWidth(pct), ctrlY + 7, C_MUTED);
    }

    // ---------- примитивы отрисовки ----------

    private void fillRound(int x1, int y1, int x2, int y2, int color) {
        drawRect(x1 + 1, y1, x2 - 1, y2, color);
        drawRect(x1, y1 + 1, x2, y2 - 1, color);
    }

    private void button(int x, int y, int w, int h, String label, int mx, int my, boolean active) {
        boolean hover = hit(mx, my, x, y, w, h);
        fillRound(x, y, x + w, y + h, active ? C_ACCENT_DARK : (hover ? C_BTN_HOVER : C_BTN));
        int lw = fontRenderer.getStringWidth(label);
        fontRenderer.drawString(label, x + (w - lw) / 2, y + (h - 8) / 2, active ? C_TEXT : 0xFFD8D4EE);
    }

    private void slider(int x, int y, int w, float frac, boolean hot, boolean enabled) {
        frac = Math.max(0f, Math.min(1f, frac));
        drawRect(x, y, x + w, y + 4, C_TRACK_BG);
        int fw = (int) (w * frac);
        if (fw > 0) drawRect(x, y, x + fw, y + 4, enabled ? C_ACCENT : C_MUTED);
        if (enabled) {
            int kx = x + fw;
            int ks = hot ? 4 : 3;
            drawRect(kx - ks, y + 2 - ks, kx + ks, y + 2 + ks, C_TEXT);
        }
    }

    private void drawEqualizer(int x, int y, int w, int h) {
        int barW = 4, gap = 2;
        int n = Math.max(1, (w + gap) / (barW + gap));
        boolean live = mm.getCurrentTrack() != null && mm.isPlaying() && !mm.isPaused();
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < n; i++) {
            float amp;
            if (live) {
                double a = Math.sin(t * (2.0 + (i % 5) * 0.9) + i * 1.7) * 0.5 + 0.5;
                double b = Math.sin(t * 3.3 + i * 0.6) * 0.5 + 0.5;
                amp = (float) (0.12 + 0.88 * (a * 0.6 + b * 0.4));
            } else {
                amp = 0.06f;
            }
            int bh = Math.max(2, (int) (h * amp));
            float k = n <= 1 ? 0f : (float) i / (n - 1);
            int col = lerp(0xFF7C5CFF, 0xFFD084FF, k);
            int bx = x + i * (barW + gap);
            drawRect(bx, y + h - bh, bx + barW, y + h, col);
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void triRight(int x, int y, int h, int color) {
        int w = Math.max(2, h * 4 / 5);
        for (int c = 0; c < w; c++) {
            int ch = Math.max(1, h - (int) (c * (float) h / w));
            int y0 = y + (h - ch) / 2;
            drawRect(x + c, y0, x + c + 1, y0 + ch, color);
        }
    }

    private void triLeft(int x, int y, int h, int color) {
        int w = Math.max(2, h * 4 / 5);
        for (int c = 0; c < w; c++) {
            int ch = Math.max(1, h - (int) (c * (float) h / w));
            int y0 = y + (h - ch) / 2;
            drawRect(x + w - 1 - c, y0, x + w - c, y0 + ch, color);
        }
    }

    private void drawHeart(int x, int y, boolean on) {
        String[] p = {"0110110", "1111111", "1111111", "0111110", "0011100", "0001000"};
        int col = on ? 0xFFFF5C8A : C_MUTED;
        for (int r = 0; r < p.length; r++)
            for (int c = 0; c < p[r].length(); c++)
                if (p[r].charAt(c) == '1') {
                    if (!on && !(r == 0 || r == 5 || c == 0 || c == 6 || (r == 1 && c == 3) || (r == 3 && (c == 1 || c == 5)) || (r == 4 && (c == 2 || c == 4)) || (r == 2 && (c == 0 || c == 6)))) continue;
                    drawRect(x + c * 1, y + r, x + c + 1, y + r + 1, col);
                }
    }

    private void drawSpeaker(int x, int y) {
        drawRect(x, y + 2, x + 3, y + 6, C_MUTED);
        for (int i = 0; i < 4; i++) drawRect(x + 3 + i, y + 2 - i, x + 4 + i, y + 6 + i, C_MUTED);
    }

    private void drawCentered(String s, int cx, int y, int color) {
        fontRenderer.drawString(s, cx - fontRenderer.getStringWidth(s) / 2, y, color);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static String plural(int n) {
        int a = n % 100, b = n % 10;
        if (a >= 11 && a <= 14) return "треков";
        if (b == 1) return "трек";
        if (b >= 2 && b <= 4) return "трека";
        return "треков";
    }

    // ---------- ввод ----------

    private boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean inProgress(int mx, int my) { return hit(mx, my, rightX - 4, progY - 5, rightW + 8, 14); }
    private boolean inVolume(int mx, int my) { return hit(mx, my, volSliderX - 4, ctrlY + 4, volSliderW + 8, 14); }
    private boolean inList(int mx, int my) { return hit(mx, my, leftX, listTop, leftW, listBottom - listTop); }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        searchField.mouseClicked(mx, my, btn);

        // правая кнопка по треку - в любимые
        if (btn == 1) {
            Track t = trackAt(mx, my);
            if (t != null) { mm.toggleFavorite(t); applyFilter(); }
            return;
        }
        if (btn != 0) return;

        // шапка
        if (hit(mx, my, hFolderX, py + 6, hFolderW, 14)) { openFolder(); return; }
        if (hit(mx, my, hRefreshX, py + 6, hRefreshW, 14)) {
            mm.refresh();
            reloadLibrary();
            setStatus("Список обновлён");
            return;
        }

        // вкладки
        int tw = (leftW - 4) / 2;
        if (hit(mx, my, leftX, py + 52, tw, 14)) { tab = 0; scroll = 0; applyFilter(); return; }
        if (hit(mx, my, leftX + tw + 4, py + 52, tw, 14)) { tab = 1; scroll = 0; applyFilter(); return; }

        // список
        if (filtered.size() > rows && mx >= leftX + leftW - 8 && mx < leftX + leftW && my >= listTop && my < listBottom) {
            drag = DRAG_SCROLL;
            dragScroll(my);
            return;
        }
        Track t = trackAt(mx, my);
        if (t != null) { mm.play(t); return; }

        // прогресс
        Track cur = mm.getCurrentTrack();
        if (cur != null && mm.getDurationMs() > 0 && inProgress(mx, my)) {
            drag = DRAG_PROGRESS;
            dragFrac = clamp01((mx - rightX) / (float) rightW);
            return;
        }

        // громкость
        if (inVolume(mx, my)) {
            drag = DRAG_VOLUME;
            mm.setVolume(clamp01((mx - volSliderX) / (float) volSliderW));
            return;
        }

        // режимы
        int wS = fontRenderer.getStringWidth("Случайно") + 12;
        int wR = fontRenderer.getStringWidth("Повтор: трек") + 12;
        int mxs = rightX + rightW / 2 - (wS + 4 + wR) / 2;
        if (hit(mx, my, mxs, modeY, wS, 13)) { mm.toggleShuffle(); return; }
        if (hit(mx, my, mxs + wS + 4, modeY, wR, 13)) { mm.cycleRepeat(); return; }

        // сердце
        if (cur != null && hit(mx, my, rightX + rightW - 14, py + 31, 14, 12)) {
            mm.toggleFavorite(cur);
            applyFilter();
            return;
        }

        // управление
        int x = rightX;
        if (hit(mx, my, x, ctrlY + 3, 28, 16)) { mm.seekRelative(-10000); return; }
        x += 34;
        if (hit(mx, my, x, ctrlY, 22, 22)) { mm.prev(); return; }
        x += 28;
        if (hit(mx, my, x, ctrlY - 2, 26, 26)) { mm.togglePause(); return; }
        x += 32;
        if (hit(mx, my, x, ctrlY, 22, 22)) { mm.next(); return; }
        x += 28;
        if (hit(mx, my, x, ctrlY + 3, 28, 16)) { mm.seekRelative(10000); return; }
    }

    private Track trackAt(int mx, int my) {
        if (!inList(mx, my)) return null;
        boolean sbar = filtered.size() > rows;
        if (sbar && mx >= leftX + leftW - 8) return null;
        int i = scroll + (my - listTop) / ROW_H;
        return (i >= 0 && i < filtered.size()) ? filtered.get(i) : null;
    }

    private void dragScroll(int my) {
        int maxScroll = filtered.size() - rows;
        if (maxScroll <= 0) return;
        float f = clamp01((my - listTop) / (float) (listBottom - listTop));
        scroll = Math.round(f * maxScroll);
        clampScroll();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (drag == DRAG_PROGRESS) dragFrac = clamp01((mx - rightX) / (float) rightW);
        else if (drag == DRAG_VOLUME) mm.setVolume(clamp01((mx - volSliderX) / (float) volSliderW));
        else if (drag == DRAG_SCROLL) dragScroll(my);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (drag == DRAG_PROGRESS) {
            long dur = mm.getDurationMs();
            if (dur > 0) mm.seekTo((long) (dragFrac * dur));
        } else if (drag == DRAG_VOLUME) {
            mm.saveSettings();
        }
        drag = DRAG_NONE;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int dir = wheel > 0 ? -1 : 1;
        if (inList(mx, my)) {
            scroll += dir * 2;
            clampScroll();
        } else if (inVolume(mx, my) || hit(mx, my, rightX, ctrlY, rightW, 22)) {
            mm.setVolume(mm.getVolume() - dir * 0.05f);
            mm.saveSettings();
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        if (searchField.isFocused()) {
            if (key == Keyboard.KEY_RETURN) {
                searchField.setFocused(false);
                if (!filtered.isEmpty()) mm.play(filtered.get(0));
                return;
            }
            String before = searchField.getText();
            searchField.textboxKeyTyped(c, key);
            if (!before.equals(searchField.getText())) { scroll = 0; applyFilter(); }
            return;
        }
        if (CustomMusicMod.openKey != null && key == CustomMusicMod.openKey.getKeyCode()) { mc.displayGuiScreen(null); return; }
        if (key == Keyboard.KEY_SPACE) mm.togglePause();
        else if (key == Keyboard.KEY_LEFT) mm.seekRelative(-10000);
        else if (key == Keyboard.KEY_RIGHT) mm.seekRelative(10000);
        else if (key == Keyboard.KEY_UP) { mm.setVolume(mm.getVolume() + 0.05f); mm.saveSettings(); }
        else if (key == Keyboard.KEY_DOWN) { mm.setVolume(mm.getVolume() - 0.05f); mm.saveSettings(); }
    }

    private void openFolder() {
        final java.io.File dir = mm.getDir();
        setStatus("Папка: " + dir.getAbsolutePath());
        Thread th = new Thread("CustomMusic-OpenFolder") {
            public void run() {
                try {
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir);
                } catch (Throwable ignored) {
                }
            }
        };
        th.setDaemon(true);
        th.start();
    }
}
