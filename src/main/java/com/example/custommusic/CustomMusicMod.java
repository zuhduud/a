package com.example.custommusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;

@Mod(modid = CustomMusicMod.MODID, name = "Custom Music Player", version = "1.0.0",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public class CustomMusicMod {
    public static final String MODID = "custommusic";
    public static KeyBinding openKey;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MusicManager.INSTANCE.init(new File(Minecraft.getMinecraft().mcDataDir, "custom_music"));
        openKey = new KeyBinding("key.custommusic.open", Keyboard.KEY_M, "key.categories.custommusic");
        ClientRegistry.registerKeyBinding(openKey);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (openKey.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiMusicPlayer());
        }
    }
}
