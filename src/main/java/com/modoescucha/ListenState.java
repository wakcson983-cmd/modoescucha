package com.modoescucha;

import net.minecraft.util.Mth;

/** Estado del Modo Escucha (solo cliente). */
public final class ListenState {
    public static final double RANGE = 15.0D;
    public static final float SOUND_FACTOR = 2.5F;
    public static final float LISTENER_GAIN = 2.0F;

    public static boolean active = false;
    public static float progress = 0F;
    public static float prevProgress = 0F;

    private ListenState() {}

    /** Progreso suavizado (0..1) para la animacion. */
    public static float smooth(float partialTick) {
        float p = Mth.lerp(partialTick, prevProgress, progress);
        return p * p * (3F - 2F * p);
    }
}
