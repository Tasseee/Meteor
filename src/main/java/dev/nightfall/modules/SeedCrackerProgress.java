package dev.nightfall.modules;
public final class SeedCrackerProgress {
    private static volatile float progress;
    private SeedCrackerProgress() {}
    public static synchronized void reset(){progress=0f;}
    public static synchronized void reset(float initial){progress=clamp(initial);}
    public static synchronized void addPercent(float value){progress=clamp(progress+value);}
    public static synchronized void complete(){progress=100f;}
    public static float getSearchProgress(){return progress;}
    private static float clamp(float v){return Float.isFinite(v)?Math.max(0f,Math.min(100f,v)):0f;}
}
