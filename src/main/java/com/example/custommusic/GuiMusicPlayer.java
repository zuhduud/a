package com.example.custommusic;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class GuiMusicPlayer extends GuiScreen {
    private static final int PER_PAGE = 6;
    private static int page = 0;
    private final MusicManager m = MusicManager.INSTANCE;

    @Override
    public void initGui() {
        buttonList.clear();
        List<File> tracks = m.getTracks();
        int cx = width / 2;
        int top = 40;

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= tracks.size()) break;
            String name = tracks.get(idx).getName();
            if (name.length() > 36) name = name.substring(0, 33) + "...";
            if (idx == m.getCurrent() && m.isPlaying()) name = (m.isPaused() ? "|| " : "> ") + name;
            buttonList.add(new GuiButton(i, cx - 100, top + i * 22, 200, 20, name));
        }

        buttonList.add(new GuiButton(10, cx - 100, top + 6 * 22 + 2, 20, 20, "<"));
        buttonList.add(new GuiButton(11, cx + 80, top + 6 * 22 + 2, 20, 20, ">"));

        int y = top + 6 * 22 + 30;
        buttonList.add(new GuiButton(20, cx - 100, y, 40, 20, "|<"));
        buttonList.add(new GuiButton(21, cx - 58, y, 70, 20, m.isPlaying() && !m.isPaused() ? "Пауза" : "Играть"));
        buttonList.add(new GuiButton(22, cx + 14, y, 40, 20, "Стоп"));
        buttonList.add(new GuiButton(23, cx + 60, y, 40, 20, ">|"));

        y += 24;
        buttonList.add(new GuiButton(30, cx - 100, y, 20, 20, "-"));
        buttonList.add(new GuiButton(31, cx + 80, y, 20, 20, "+"));

        y += 24;
        buttonList.add(new GuiButton(40, cx - 100, y, 98, 20, "Открыть папку"));
        buttonList.add(new GuiButton(41, cx + 2, y, 98, 20, "Обновить"));
        buttonList.add(new GuiButton(50, cx - 100, y + 24, 200, 20, "Закрыть"));
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        int id = b.id;
        int pages = Math.max(1, (m.getTracks().size() + PER_PAGE - 1) / PER_PAGE);
        if (id >= 0 && id < PER_PAGE) m.play(page * PER_PAGE + id);
        else if (id == 10) page = Math.max(0, page - 1);
        else if (id == 11) page = Math.min(pages - 1, page + 1);
        else if (id == 20) m.prev();
        else if (id == 21) m.togglePause();
        else if (id == 22) m.stop();
        else if (id == 23) m.next();
        else if (id == 30) m.setVolume(m.getVolume() - 0.1f);
        else if (id == 31) m.setVolume(m.getVolume() + 0.1f);
        else if (id == 40) openFolder();
        else if (id == 41) { m.refresh(); page = 0; }
        else if (id == 50) { mc.displayGuiScreen(null); return; }
        initGui();
    }

    private void openFolder() {
        try {
            File dir = m.getDir();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer", dir.getAbsolutePath()).start();
            } else {
                Desktop.getDesktop().open(dir);
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // музыка и игра не встают на паузу
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int pages = Math.max(1, (m.getTracks().size() + PER_PAGE - 1) / PER_PAGE);
        drawCenteredString(fontRenderer, "Свой музыкальный плеер", cx, 15, 0xFFFFFF);
        if (m.getTracks().isEmpty()) {
            drawCenteredString(fontRenderer, "Положи .mp3 в папку custom_music и нажми \"Обновить\"", cx, 60, 0xFFAA00);
        }
        int top = 40;
        drawCenteredString(fontRenderer, (page + 1) + " / " + pages, cx, top + 6 * 22 + 8, 0xAAAAAA);
        int y = top + 6 * 22 + 30 + 24;
        drawCenteredString(fontRenderer, "Громкость: " + Math.round(m.getVolume() * 100) + "%", cx, y + 6, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
