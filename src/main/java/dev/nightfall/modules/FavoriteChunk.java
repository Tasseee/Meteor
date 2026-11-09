package dev.nightfall.modules;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.scanner.ChunkFavorites;
import net.minecraft.text.Text;
public final class FavoriteChunk extends Module {
    private boolean pending;
    public FavoriteChunk(){super("FavoriteChunk","Markiert den aktuellen Chunk als Favorit (Hotkey-Aktion).",Category.WORLD);}
    @Override protected void onEnable(){pending=true;}
    @Override protected void onDisable(){pending=false;}
    @Override public void onTick(){
        if(!pending||mc.player==null||mc.world==null)return;pending=false;
        int cx=mc.player.getBlockX()>>4,cz=mc.player.getBlockZ()>>4;boolean added=ChunkFavorites.toggle(cx,cz);
        mc.player.sendMessage(Text.literal("[Nightfall] Chunk "+cx+", "+cz+(added?" zu Favoriten hinzugefuegt.":" aus Favoriten entfernt.")),false);
        setEnabled(false);
    }
}
