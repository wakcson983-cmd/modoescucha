package com.modoescucha;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModoEscucha.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    public static final KeyMapping LISTEN_KEY = new KeyMapping(
            "key.modoescucha.listen",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.modoescucha");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(LISTEN_KEY);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("listen_hint", (gui, graphics, partialTick, width, height) -> {
            float p = ListenState.smooth(partialTick);
            if (p <= 0.02F) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui) return;
            int a = (int) (p * 200F);
            if (a < 5) return;
            int color = (a << 24) | 0xFFFFFF;
            Component text = Component.translatable("hud.modoescucha.listening");
            graphics.drawCenteredString(mc.font, text, width / 2, height / 2 + 26, color);
        });
    }
}
