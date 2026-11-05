package com.modoescucha;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** Envuelve un sonido y multiplica su volumen (tambien amplia su alcance). */
public class LoudSound implements SoundInstance {
    private final SoundInstance d;
    private final float factor;

    public LoudSound(SoundInstance delegate, float factor) {
        this.d = delegate;
        this.factor = factor;
    }

    @Override public ResourceLocation getLocation() { return d.getLocation(); }
    @Override public WeighedSoundEvents resolve(SoundManager manager) { return d.resolve(manager); }
    @Override public Sound getSound() { return d.getSound(); }
    @Override public SoundSource getSource() { return d.getSource(); }
    @Override public boolean isLooping() { return d.isLooping(); }
    @Override public boolean isRelative() { return d.isRelative(); }
    @Override public int getDelay() { return d.getDelay(); }
    @Override public float getVolume() { return d.getVolume() * factor; }
    @Override public float getPitch() { return d.getPitch(); }
    @Override public double getX() { return d.getX(); }
    @Override public double getY() { return d.getY(); }
    @Override public double getZ() { return d.getZ(); }
    @Override public Attenuation getAttenuation() { return d.getAttenuation(); }
}
