package dev.nightfall;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.nightfall.config.Config;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.modules.AutoText;
import dev.nightfall.modules.AutoTpa;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.modules.FriendManager;
import dev.nightfall.modules.KillAura;
import dev.nightfall.modules.StaffWatch;
import dev.nightfall.modules.TrapperBot;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Storage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.class_2561;
import net.minecraft.class_310;

public final class Commands {
   private Commands() {
   }

   private static int say(CommandContext<FabricClientCommandSource> c, String s) {
      ((FabricClientCommandSource)c.getSource()).sendFeedback(class_2561.method_43470("[Nightfall] " + s));
      return 1;
   }

   private static String str(CommandContext<FabricClientCommandSource> c, String n) {
      return StringArgumentType.getString(c, n);
   }

   public static void register(CommandDispatcher<FabricClientCommandSource> d) {
      LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("nf");
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("wp")
                  .then(
                     ClientCommandManager.literal("add")
                        .then(
                           ((RequiredArgumentBuilder)ClientCommandManager.argument("name", StringArgumentType.word())
                                 .executes(c -> addWp(c, str(c, "name"), "Default")))
                              .then(
                                 ClientCommandManager.argument("category", StringArgumentType.word())
                                    .executes(c -> addWp(c, str(c, "name"), str(c, "category")))
                              )
                        )
                  ))
               .then(
                  ClientCommandManager.literal("del")
                     .then(
                        ClientCommandManager.argument("name", StringArgumentType.word())
                           .executes(c -> say(c, WaypointManager.remove(str(c, "name")) ? "Waypoint geloescht" : "Nicht gefunden"))
                     )
               ))
            .then(
               ClientCommandManager.literal("list")
                  .executes(
                     c -> {
                        String dim = Storage.dim();
                        int n = 0;

                        for (Waypoint w : WaypointManager.all()) {
                           say(
                              c,
                              "["
                                 + w.category
                                 + "] "
                                 + w.name
                                 + " "
                                 + w.x
                                 + " "
                                 + w.y
                                 + " "
                                 + w.z
                                 + " ("
                                 + w.dim
                                 + ")"
                                 + (w.dim.equals(dim) ? "" : " - andere Dimension")
                           );
                           n++;
                        }

                        return say(c, n + " Waypoints");
                     }
                  )
            )
      );
      AutoTpa tpa = ModuleManager.get(AutoTpa.class);
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("tpa").then(listCmd("wl", tpa.whitelist, "Whitelist")))
               .then(listCmd("bl", tpa.blacklist, "Blacklist")))
            .then(ClientCommandManager.literal("list").executes(c -> {
               say(c, "Whitelist: " + String.join(", ", tpa.whitelist.get()));
               return say(c, "Blacklist: " + String.join(", ", tpa.blacklist.get()));
            }))
      );
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("friend")
                  .then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                     String name = str(c, "name");
                     boolean added = ModuleManager.get(FriendManager.class).addFriend(name);
                     if (added) {
                        ModuleManager.get(KillAura.class).friends.add(name);
                     }

                     Config.save();
                     return say(c, added ? "Freund hinzugefuegt: " + name : "Ungueltiger Name oder bereits vorhanden");
                  }))))
               .then(ClientCommandManager.literal("del").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                  String name = str(c, "name");
                  boolean removed = ModuleManager.get(FriendManager.class).removeFriend(name);
                  ModuleManager.get(KillAura.class).friends.remove(name);
                  Config.save();
                  return say(c, removed ? "Freund entfernt: " + name : "Freund nicht gefunden");
               }))))
            .then(ClientCommandManager.literal("list").executes(c -> {
               Set<String> friends = ModuleManager.get(FriendManager.class).friends.get();

               for (String name : friends) {
                  say(c, "• " + name);
               }

               return say(c, friends.size() + " Freund(e)");
            }))
      );
      TrapperBot trapper = ModuleManager.get(TrapperBot.class);
      root.then(
         ((LiteralArgumentBuilder)ClientCommandManager.literal("trapper")
               .then(
                  ClientCommandManager.literal("status")
                     .executes(c -> say(c, "TrapperBot: " + (trapper.isEnabled() ? "AN" : "AUS") + " | " + trapper.status()))
               ))
            .then(ClientCommandManager.literal("bl").then(listCmd("x", trapper.blacklist, "Trapper-Blacklist")))
      );
      AutoText autoText = ModuleManager.get(AutoText.class);
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("autotext")
                  .then(
                     ClientCommandManager.literal("add")
                        .then(
                           ClientCommandManager.argument("message", StringArgumentType.greedyString())
                              .executes(
                                 c -> {
                                    String msg = str(c, "message");
                                    return say(
                                       c,
                                       autoText.addMessage(msg)
                                          ? "AutoText-Nachricht hinzugefuegt (" + autoText.messageList().size() + ")"
                                          : "Ungueltige oder bereits vorhandene Nachricht (max. 256 Zeichen)"
                                    );
                                 }
                              )
                        )
                  ))
               .then(ClientCommandManager.literal("del").then(ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes(c -> {
                  String msg = str(c, "message");
                  return say(c, autoText.removeMessage(msg) ? "AutoText-Nachricht entfernt" : "Nachricht nicht gefunden");
               }))))
            .then(ClientCommandManager.literal("list").executes(c -> {
               List<String> lines = autoText.messageList();

               for (int i = 0; i < lines.size(); i++) {
                  say(c, i + 1 + ". " + lines.get(i));
               }

               return say(c, lines.size() + " Nachricht(en); Intervall in der ClickGUI einstellen (mind. 5 Sekunden)");
            }))
      );
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("profile")
                  .then(
                     ClientCommandManager.literal("save")
                        .then(
                           ClientCommandManager.argument("name", StringArgumentType.word())
                              .executes(c -> say(c, Config.saveProfile(str(c, "name")) ? "Profil gespeichert" : "Fehler beim Speichern"))
                        )
                  ))
               .then(
                  ClientCommandManager.literal("load")
                     .then(
                        ClientCommandManager.argument("name", StringArgumentType.word())
                           .executes(c -> say(c, Config.loadProfile(str(c, "name")) ? "Profil geladen" : "Profil nicht gefunden"))
                     )
               ))
            .then(
               ClientCommandManager.literal("list")
                  .executes(
                     c -> {
                        Path dir = Storage.dir().resolve("profiles");

                        try {
                           Files.createDirectories(dir);

                           int var9;
                           try (Stream<Path> stream = Files.list(dir)) {
                              List<String> names = stream.filter(x$0 -> Files.isRegularFile(x$0))
                                 .map(path -> path.getFileName().toString())
                                 .filter(namex -> namex.endsWith(".json"))
                                 .sorted()
                                 .toList();

                              for (String name : names) {
                                 say(c, name.substring(0, name.length() - 5));
                              }

                              var9 = say(c, names.size() + " Profil(e)");
                           }

                           return var9;
                        } catch (IOException var8) {
                           return say(c, "Profilordner konnte nicht gelesen werden");
                        }
                     }
                  )
            )
      );
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("fake")
                  .then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                     class_310 mc = class_310.method_1551();
                     if (mc.field_1724 == null) {
                        return 0;
                     } else {
                        WaypointManager.add(
                           str(c, "name"), "FakeBase", mc.field_1724.method_31477(), mc.field_1724.method_31478(), mc.field_1724.method_31479()
                        );
                        return say(c, "Lokale TEST/DECOY-Markierung gesetzt: " + str(c, "name"));
                     }
                  }))))
               .then(ClientCommandManager.literal("del").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                  String name = str(c, "name");
                  Waypoint found = null;

                  for (Waypoint w : WaypointManager.all()) {
                     if ("FakeBase".equalsIgnoreCase(w.category) && w.name.equalsIgnoreCase(name)) {
                        found = w;
                        break;
                     }
                  }

                  if (found == null) {
                     return say(c, "Fake-Markierung nicht gefunden");
                  } else {
                     WaypointManager.remove(found);
                     return say(c, "Fake-Markierung entfernt: " + name);
                  }
               }))))
            .then(ClientCommandManager.literal("list").executes(c -> {
               int n = 0;

               for (Waypoint w : WaypointManager.all()) {
                  if ("FakeBase".equalsIgnoreCase(w.category)) {
                     say(c, "[TEST/DECOY] " + w.name + " " + w.x + " " + w.y + " " + w.z);
                     n++;
                  }
               }

               return say(c, n + " lokale Fake-Markierung(en)");
            }))
      );
      StaffWatch staff = ModuleManager.get(StaffWatch.class);
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("staff")
                  .then(
                     ClientCommandManager.literal("add")
                        .then(
                           ClientCommandManager.argument("name", StringArgumentType.word())
                              .executes(c -> say(c, staff.staffNames.add(str(c, "name")) ? "StaffWatch-Name hinzugefügt" : "Name bereits eingetragen"))
                        )
                  ))
               .then(
                  ClientCommandManager.literal("del")
                     .then(
                        ClientCommandManager.argument("name", StringArgumentType.word())
                           .executes(c -> say(c, staff.staffNames.remove(str(c, "name")) ? "StaffWatch-Name entfernt" : "Name nicht gefunden"))
                     )
               ))
            .then(ClientCommandManager.literal("list").executes(c -> say(c, "Beobachtete Namen: " + String.join(", ", staff.staffNames.get()))))
      );
      root.then(ClientCommandManager.literal("keysounds").then(ClientCommandManager.argument("state", StringArgumentType.word()).executes(c -> {
         String state = str(c, "state");
         if (!state.equalsIgnoreCase("on") && !state.equalsIgnoreCase("off")) {
            return say(c, "Verwendung: /nf keysounds on|off");
         } else {
            Config.keySounds = state.equalsIgnoreCase("on");
            Config.save();
            return say(c, "Tastatur-Klicksounds: " + (Config.keySounds ? "AN" : "AUS"));
         }
      })));
      BaseFinder finder = ModuleManager.get(BaseFinder.class);
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("scan")
                     .then(ClientCommandManager.literal("pause").executes(c -> {
                        finder.setPaused(true);
                        return say(c, "BaseFinder pausiert. Bereits gesammelte Ergebnisse bleiben erhalten.");
                     })))
                  .then(ClientCommandManager.literal("resume").executes(c -> {
                     finder.setPaused(false);
                     return say(c, "BaseFinder fortgesetzt.");
                  })))
               .then(
                  ClientCommandManager.literal("status")
                     .executes(
                        c -> say(
                           c,
                           "BaseFinder "
                              + (finder.isEnabled() ? "AN" : "AUS")
                              + (finder.isPaused() ? " (PAUSIERT)" : "")
                              + " | Queue "
                              + finder.queueSize()
                              + " | gescannt "
                              + finder.scannedSinceRefill()
                              + "/"
                              + finder.candidatesLastRefill()
                              + " | Ergebnisse "
                              + finder.resultCount()
                              + " | letztes Budget "
                              + String.format(Locale.ROOT, "%.2f ms", finder.lastScanMs())
                        )
                     )
               ))
            .then(ClientCommandManager.literal("clear").executes(c -> {
               finder.clearMarks();
               return say(c, "Markierungen dieses Servers/Dimension geloescht");
            }))
      );
      d.register(root);
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> listCmd(String lit, ListSetting list, String label) {
      return (LiteralArgumentBuilder<FabricClientCommandSource>)((LiteralArgumentBuilder)ClientCommandManager.literal(lit)
            .then(
               ClientCommandManager.literal("add")
                  .then(
                     ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(c -> say(c, list.add(str(c, "name")) ? label + ": hinzugefuegt" : "Schon vorhanden"))
                  )
            ))
         .then(
            ClientCommandManager.literal("del")
               .then(
                  ClientCommandManager.argument("name", StringArgumentType.word())
                     .executes(c -> say(c, list.remove(str(c, "name")) ? label + ": entfernt" : "Nicht vorhanden"))
               )
         );
   }

   private static int addWp(CommandContext<FabricClientCommandSource> c, String name, String cat) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null) {
         return 0;
      } else {
         WaypointManager.add(name, cat, mc.field_1724.method_31477(), mc.field_1724.method_31478(), mc.field_1724.method_31479());
         return say(c, "Waypoint '" + name + "' gesetzt");
      }
   }
}
