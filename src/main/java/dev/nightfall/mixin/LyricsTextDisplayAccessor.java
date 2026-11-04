package dev.nightfall.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessors for the local-only floating lyric text display.
 * The setText/background/opacity invokers follow the 1.21.11 TextDisplayEntity
 * access pattern used by the MIT-licensed LyricSync client (MusicShow).
 */
@Mixin(DisplayEntity.TextDisplayEntity.class)
public interface LyricsTextDisplayAccessor {
    @Invoker("setText")
    void nightfall$setText(Text text);

    @Invoker("setBackground")
    void nightfall$setBackground(int background);

    @Invoker("setTextOpacity")
    void nightfall$setTextOpacity(byte opacity);

    @Invoker("setDisplayFlags")
    void nightfall$setFlags(byte flags);
}
