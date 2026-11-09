package dev.nightfall.modules;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
/** Hides only the empty first-person main-hand model; interaction remains unchanged. */
public final class GhostHand extends Module {
    public GhostHand(){super("GhostHand","Blendet die leere First-Person-Haupthand aus (nur Optik).",Category.VISUALS);}
}
