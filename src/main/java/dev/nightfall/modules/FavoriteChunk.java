package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.scanner.ChunkFavorites;
import net.minecraft.class_2561;

public final class FavoriteChunk extends Module {
   private boolean pending;

   public FavoriteChunk() {
      super("FavoriteChunk", "Markiert den aktuellen Chunk als Favorit (Hotkey-Aktion).", Category.WORLD);
   }

   @Override
   protected void onEnable() {
      this.pending = true;
   }

   @Override
   protected void onDisable() {
      this.pending = false;
   }

   @Override
   public void onTick() {
      if (this.pending && mc.field_1724 != null && mc.field_1687 != null) {
         this.pending = false;
         int cx = mc.field_1724.method_31477() >> 4;
         int cz = mc.field_1724.method_31479() >> 4;
         boolean added = ChunkFavorites.toggle(cx, cz);
         mc.field_1724
            .method_7353(
               class_2561.method_43470("[Nightfall] Chunk " + cx + ", " + cz + (added ? " zu Favoriten hinzugefuegt." : " aus Favoriten entfernt.")), false
            );
         this.setEnabled(false);
      }
   }
}
