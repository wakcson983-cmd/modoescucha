package com.modoescucha;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = ModoEscucha.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    // ------------------------------------------------------------------ TICK / TECLA
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        ListenState.prevProgress = ListenState.progress;

        while (ClientModEvents.LISTEN_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                toggle(mc);
            }
        }

        // Si salimos del mundo con el modo activo, lo apagamos.
        if (mc.player == null || mc.level == null) {
            if (ListenState.active) {
                ListenState.active = false;
                applyGain(mc);
            }
            ListenState.progress = 0F;
            ListenState.prevProgress = 0F;
            return;
        }

        float step = 0.08F; // ~12 ticks (0.6 s) de animacion
        if (ListenState.active) {
            ListenState.progress = Math.min(1F, ListenState.progress + step);
        } else {
            ListenState.progress = Math.max(0F, ListenState.progress - step);
        }
    }

    private static void toggle(Minecraft mc) {
        ListenState.active = !ListenState.active;
        applyGain(mc);
        if (ListenState.active) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_LISTENING, 1.0F, 1.0F));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.6F, 0.7F));
        } else {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.7F));
        }
    }

    /** Sube la ganancia del oyente (todos los sonidos mas fuertes) o la restaura. */
    private static void applyGain(Minecraft mc) {
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        float gain = ListenState.active ? master * ListenState.LISTENER_GAIN : master;
        mc.getSoundManager().updateSourceVolume(SoundSource.MASTER, gain);
    }

    // ------------------------------------------------------------------ SONIDOS MAS FUERTES
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (!ListenState.active) return;
        SoundInstance sound = event.getSound();
        if (sound == null) return;
        if (sound.isRelative() || sound instanceof TickableSoundInstance) return; // UI, musica y sonidos con tick
        event.setSound(new LoudSound(sound, ListenState.SOUND_FACTOR));
    }

    // ------------------------------------------------------------------ RENDER
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        float pt = event.getPartialTick();
        float p = ListenState.smooth(pt);
        if (p <= 0.01F) return;

        drawGrayFilter(event.getProjectionMatrix(), p, pt);
        drawSilhouettes(event, mc, p, pt);
    }

    /** Filtro gris que aparece con animacion (con un pequeno destello al activarse). */
    private static void drawGrayFilter(Matrix4f projection, float p, float pt) {
        float flash = Mth.clamp(1F - p * 2.2F, 0F, 1F) * (ListenState.active ? 1F : 0F);
        int gray = (int) Mth.lerp(flash, 70F, 200F);
        int alpha = (int) (Mth.clamp(p * 0.55F + flash * 0.25F, 0F, 0.8F) * 255F);

        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(-1, -1, 0).color(gray, gray, gray + 4, alpha).endVertex();
        bb.vertex(1, -1, 0).color(gray, gray, gray + 4, alpha).endVertex();
        bb.vertex(1, 1, 0).color(gray, gray, gray + 4, alpha).endVertex();
        bb.vertex(-1, 1, 0).color(gray, gray, gray + 4, alpha).endVertex();
        BufferUploader.drawWithShader(bb.end());

        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** Dibuja en blanco los mobs hostiles/neutrales cercanos, a traves de paredes, con alpha segun distancia. */
    private static void drawSilhouettes(RenderLevelStageEvent event, Minecraft mc, float p, float pt) {
        Vec3 cam = event.getCamera().getPosition();
        double range = ListenState.RANGE;

        List<LivingEntity> targets = mc.level.getEntitiesOfClass(
                LivingEntity.class,
                mc.player.getBoundingBox().inflate(range + 1.0D),
                e -> e != mc.player
                        && e.isAlive()
                        && !e.isSpectator()
                        && (e instanceof Enemy || e instanceof NeutralMob)
                        && e.distanceToSqr(cam) <= range * range);
        if (targets.isEmpty()) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();

        dispatcher.setRenderShadow(false);

        float time = (mc.level.getGameTime() + pt) * 0.15F;
        float pulse = 0.9F + 0.1F * Mth.sin(time);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (LivingEntity e : targets) {
            double dist = Math.sqrt(e.distanceToSqr(cam));
            float near = (float) Mth.clamp(1.0D - dist / range, 0.0D, 1.0D);
            // Cerca = muy visible; lejos = casi invisible.
            float a = (0.12F + 0.88F * near) * p * pulse;
            int alpha = Mth.clamp((int) (a * 255F), 26, 255);

            MultiBufferSource wrapped = renderType -> {
                if (renderType.format() == DefaultVertexFormat.NEW_ENTITY) {
                    return new AlphaConsumer(buffers.getBuffer(GlowType.GLOW), alpha);
                }
                return buffers.getBuffer(renderType);
            };

            double x = Mth.lerp(pt, e.xOld, e.getX()) - cam.x;
            double y = Mth.lerp(pt, e.yOld, e.getY()) - cam.y;
            double z = Mth.lerp(pt, e.zOld, e.getZ()) - cam.z;
            float yaw = Mth.lerp(pt, e.yRotO, e.getYRot());

            dispatcher.render(e, x, y, z, yaw, pt, pose, wrapped, LightTexture.FULL_BRIGHT);
        }

        buffers.endBatch(GlowType.GLOW);
        dispatcher.setRenderShadow(true);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
