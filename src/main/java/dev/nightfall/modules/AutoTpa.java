package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_2561;

public final class AutoTpa extends Module {
   private static final Pattern TPA = Pattern.compile(
      "(?i)(?:^|\\s)([A-Za-z0-9_]{3,16})\\s+(?:has\\s+)?(?:requested|asked|wants|would like|is requesting)\\s+(?:to\\s+)?teleport(?:ation)?\\s+to\\s+you"
   );
   private static final Pattern TPA_HERE = Pattern.compile(
      "(?i)(?:^|\\s)([A-Za-z0-9_]{3,16})\\s+(?:has\\s+)?(?:requested|asked|wants|would like|is requesting)\\s+(?:that\\s+)?you\\s+teleport\\s+(?:to\\s+)?(?:them|him|her|their location|there)"
   );
   private static final String[] HERE_HINTS = new String[]{
      "tpahere", "you to teleport", "you teleport to", "teleport you to", "teleport to them", "teleport to him", "teleport to her"
   };
   private final NumberSetting delay = this.add(new NumberSetting("Verzoegerung (Ticks)", 3.0, 0.0, 200.0, 1.0));
   private final ModeSetting filter = this.add(new ModeSetting("Filter", "Aus", "Aus", "Whitelist", "Blacklist"));
   private final BoolSetting withName = this.add(new BoolSetting("tpaccept mit Name", false));
   private final BoolSetting acceptHere = this.add(new BoolSetting("TPAHere annehmen", false));
   private final BoolSetting info = this.add(new BoolSetting("Chat-Info", true));
   public final ListSetting whitelist = this.add(new ListSetting("Whitelist"));
   public final ListSetting blacklist = this.add(new ListSetting("Blacklist"));
   private final List<AutoTpa.Pending> pending = new ArrayList<>();

   public AutoTpa() {
      super("AutoTPA", "Nimmt TPA-Anfragen an; TPAHere ist separat abschaltbar.", Category.MISC);
   }

   @Override
   protected void onDisable() {
      this.pending.clear();
   }

   @Override
   public void onWorldLeave() {
      this.pending.clear();
   }

   public int pendingCount() {
      return this.pending.size();
   }

   public void onMessage(String raw) {
      if (this.isEnabled() && mc.field_1724 != null && raw != null) {
         String lower = raw.toLowerCase(Locale.ROOT);
         Matcher matcher = TPA.matcher(raw);
         boolean here = false;
         if (!matcher.find()) {
            if (!this.acceptHere.get()) {
               return;
            }

            boolean hinted = false;

            for (String hint : HERE_HINTS) {
               if (lower.contains(hint)) {
                  hinted = true;
                  break;
               }
            }

            if (!hinted) {
               return;
            }

            matcher = TPA_HERE.matcher(raw);
            if (!matcher.find()) {
               return;
            }

            here = true;
         }

         String name = matcher.group(1);
         if (!name.equalsIgnoreCase(mc.field_1724.method_5477().getString())) {
            String var11 = this.filter.get();
            switch (var11) {
               case "Whitelist":
                  if (!this.whitelist.has(name)) {
                     return;
                  }
                  break;
               case "Blacklist":
                  if (this.blacklist.has(name)) {
                     return;
                  }
            }

            for (AutoTpa.Pending item : this.pending) {
               if (item.name.equalsIgnoreCase(name)) {
                  return;
               }
            }

            AutoTpa.Pending itemx = new AutoTpa.Pending();
            itemx.name = name;
            itemx.ticks = this.delay.getInt();
            itemx.here = here;
            this.pending.add(itemx);
         }
      }
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1724.field_3944 != null) {
         for (int i = this.pending.size() - 1; i >= 0; i--) {
            AutoTpa.Pending item = this.pending.get(i);
            if (item.ticks-- <= 0) {
               this.pending.remove(i);
               boolean named = this.withName.get() || item.here;
               mc.field_1724.field_3944.method_45730(named ? "tpaccept " + item.name : "tpaccept");
               if (this.info.get()) {
                  mc.field_1724
                     .method_7353(class_2561.method_43470("[Nightfall] TPA" + (item.here ? "Here" : "") + " von " + item.name + " angenommen"), false);
               }
            }
         }
      }
   }

   private static final class Pending {
      String name;
      int ticks;
      boolean here;
   }
}
