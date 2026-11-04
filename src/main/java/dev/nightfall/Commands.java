package dev.nightfall;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.nightfall.config.Config;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AutoTpa;
import dev.nightfall.modules.AutoText;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.modules.TrapperBot;
import dev.nightfall.modules.FakeBases;
import dev.nightfall.modules.InvSeeValue;
import dev.nightfall.modules.StaffWatch;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Storage;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** /nf wp | /nf tpa | /nf profile | /nf scan */
public final class Commands {
    private Commands() {}

    private static int say(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c, String s) {
        c.getSource().sendFeedback(Text.literal("[Nightfall] " + s));
        return 1;
    }

    private static String str(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c, String n) {
        return StringArgumentType.getString(c, n);
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("nf");

        // ---- Waypoints ----
        root.then(literal("wp")
                .then(literal("add")
                        .then(argument("name", StringArgumentType.word())
                                .executes(c -> addWp(c, str(c, "name"), "Default"))
                                .then(argument("category", StringArgumentType.word())
                                        .executes(c -> addWp(c, str(c, "name"), str(c, "category"))))))
                .then(literal("del")
                        .then(argument("name", StringArgumentType.word())
                                .executes(c -> say(c, WaypointManager.remove(str(c, "name")) ? "Waypoint geloescht" : "Nicht gefunden"))))
                .then(literal("list").executes(c -> {
                    String dim = Storage.dim();
                    int n = 0;
                    for (Waypoint w : WaypointManager.all()) {
                        say(c, "[" + w.category + "] " + w.name + " " + w.x + " " + w.y + " " + w.z + " (" + w.dim + ")" + (w.dim.equals(dim) ? "" : " - andere Dimension"));
                        n++;
                    }
                    return say(c, n + " Waypoints");
                })));

        // ---- AutoTPA Listen ----
        AutoTpa tpa = ModuleManager.get(AutoTpa.class);
        root.then(literal("tpa")
                .then(listCmd("wl", tpa.whitelist, "Whitelist"))
                .then(listCmd("bl", tpa.blacklist, "Blacklist"))
                .then(literal("list").executes(c -> {
                    say(c, "Whitelist: " + String.join(", ", tpa.whitelist.get()));
                    return say(c, "Blacklist: " + String.join(", ", tpa.blacklist.get()));
                })));

        // ---- Freunde (KillAura) ----
        root.then(literal("friend")
                .then(listCmd("x", ModuleManager.get(dev.nightfall.modules.KillAura.class).friends, "Freunde")));

        // ---- TrapperBot ----
        TrapperBot trapper = ModuleManager.get(TrapperBot.class);
        root.then(literal("trapper")
                .then(literal("status").executes(c -> say(c, "TrapperBot: " + (trapper.isEnabled() ? "AN" : "AUS") + " | " + trapper.status())))
                .then(literal("bl").then(listCmd("x", trapper.blacklist, "Trapper-Blacklist"))));

        // ---- AutoText: user-authored messages only ----
        AutoText autoText = ModuleManager.get(AutoText.class);
        root.then(literal("autotext")
                .then(literal("add").then(argument("message", StringArgumentType.greedyString()).executes(c -> {
                    String msg = str(c, "message");
                    return say(c, autoText.addMessage(msg) ? "AutoText-Nachricht hinzugefuegt (" + autoText.messageList().size() + ")" : "Ungueltige oder bereits vorhandene Nachricht (max. 256 Zeichen)");
                })))
                .then(literal("del").then(argument("message", StringArgumentType.greedyString()).executes(c -> {
                    String msg = str(c, "message");
                    return say(c, autoText.removeMessage(msg) ? "AutoText-Nachricht entfernt" : "Nachricht nicht gefunden");
                })))
                .then(literal("list").executes(c -> {
                    List<String> lines = autoText.messageList();
                    for (int i = 0; i < lines.size(); i++) say(c, (i + 1) + ". " + lines.get(i));
                    return say(c, lines.size() + " Nachricht(en); Intervall in der ClickGUI einstellen (mind. 5 Sekunden)");
                })));

        // FakeStats values are configured directly in the FakeStats module settings.
        // Only free-form text (AutoText messages and display name) remains command-based.

        // ---- Profile ----
        root.then(literal("profile")
                .then(literal("save").then(argument("name", StringArgumentType.word())
                        .executes(c -> say(c, Config.saveProfile(str(c, "name")) ? "Profil gespeichert" : "Fehler beim Speichern"))))
                .then(literal("load").then(argument("name", StringArgumentType.word())
                        .executes(c -> say(c, Config.loadProfile(str(c, "name")) ? "Profil geladen" : "Profil nicht gefunden"))))
                .then(literal("list").executes(c -> {
                    java.nio.file.Path dir = Storage.dir().resolve("profiles");
                    try {
                        java.nio.file.Files.createDirectories(dir);
                        try (var stream = java.nio.file.Files.list(dir)) {
                            var names = stream.filter(java.nio.file.Files::isRegularFile)
                                    .map(path -> path.getFileName().toString())
                                    .filter(name -> name.endsWith(".json"))
                                    .sorted().toList();
                            for (String name : names) say(c, name.substring(0, name.length() - 5));
                            return say(c, names.size() + " Profil(e)");
                        }
                    } catch (java.io.IOException e) { return say(c, "Profilordner konnte nicht gelesen werden"); }
                })));

        // ---- Local-only fake-base test/decoy markers ----
        root.then(literal("fake")
                .then(literal("add").then(argument("name", StringArgumentType.word()).executes(c -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null) return 0;
                    String name = str(c, "name");
                    WaypointManager.add(name, FakeBases.CATEGORY, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
                    var chunk = mc.world.getChunkManager().getWorldChunk(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4);
                    String learned = FakeBases.learn(name, chunk);
                    return say(c, "Lokale DECOY-Markierung gesetzt: " + name + " | " + learned);
                })))
                .then(literal("del").then(argument("name", StringArgumentType.word()).executes(c -> {
                    String name = str(c, "name");
                    Waypoint found = null;
                    for (Waypoint w : WaypointManager.all()) if (FakeBases.CATEGORY.equalsIgnoreCase(w.category) && w.name.equalsIgnoreCase(name)) { found = w; break; }
                    if (found == null) return say(c, "Fake-Markierung nicht gefunden");
                    WaypointManager.remove(found);
                    return say(c, "Fake-Markierung entfernt: " + name);
                })))
                .then(literal("list").executes(c -> {
                    int n = 0;
                    for (Waypoint w : WaypointManager.all()) if (FakeBases.CATEGORY.equalsIgnoreCase(w.category)) { say(c, "[DECOY] " + w.name + " " + w.x + " " + w.y + " " + w.z); n++; }
                    return say(c, n + " lokale Fake-Markierung(en)");
                }))
                .then(literal("learn").then(argument("name", StringArgumentType.word()).executes(c -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null || mc.world == null) return 0;
                    var chunk = mc.world.getChunkManager().getWorldChunk(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4);
                    return say(c, FakeBases.learn(str(c, "name"), chunk));
                })))
                .then(literal("templates").executes(c -> {
                    List<String> names = FakeBases.templateNames();
                    for (String name : names) say(c, "[AUTO-FAKE] " + name);
                    return say(c, names.size() + " gelernte FakeBase-Template(s); 90/180/270-Grad-Rotationen werden mitgeprueft");
                }))
                .then(literal("unlearn").then(argument("name", StringArgumentType.word()).executes(c ->
                        say(c, FakeBases.forgetTemplate(str(c, "name")) ? "FakeBase-Template entfernt" : "Template nicht gefunden")))));

        // ---- InvSee value HUD: prices are configured locally by item ID ----
        root.then(literal("invvalue")
                .then(literal("hand").then(argument("price", DoubleArgumentType.doubleArg(0)).executes(c -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null || mc.player.getMainHandStack().isEmpty()) return say(c, "Halte zuerst ein Item in der Haupthand.");
                    String id = InvSeeValue.itemId(mc.player.getMainHandStack());
                    InvSeeValue.setPrice(id, DoubleArgumentType.getDouble(c, "price"));
                    return say(c, id + " = " + DoubleArgumentType.getDouble(c, "price") + " (lokal gespeichert)");
                })))
                .then(literal("set").then(argument("item", StringArgumentType.word())
                        .then(argument("price", DoubleArgumentType.doubleArg(0)).executes(c -> {
                            String raw = str(c, "item");
                            Identifier id = Identifier.tryParse(raw);
                            if (id == null || !Registries.ITEM.containsId(id)) return say(c, "Unbekannte Item-ID: " + raw);
                            String canonical = Registries.ITEM.getId(Registries.ITEM.get(id)).toString();
                            double price = DoubleArgumentType.getDouble(c, "price");
                            InvSeeValue.setPrice(canonical, price);
                            return say(c, canonical + " = " + price + " (lokal gespeichert)");
                        }))))
                .then(literal("remove").then(argument("item", StringArgumentType.word()).executes(c -> {
                    Identifier id = Identifier.tryParse(str(c, "item"));
                    String canonical = id == null ? str(c, "item") : id.toString();
                    return say(c, InvSeeValue.removePrice(canonical) ? canonical + " entfernt" : "Kein Preis fuer " + canonical);
                })))
                .then(literal("list").executes(c -> {
                    var entries = new java.util.TreeMap<>(InvSeeValue.prices());
                    for (var entry : entries.entrySet()) say(c, entry.getKey() + " = " + entry.getValue());
                    return say(c, entries.size() + " Itempreis(e) gespeichert");
                })));

        // ---- Staff list: visible client data only, no confirmed vanish claims ----
        StaffWatch staff = ModuleManager.get(StaffWatch.class);
        root.then(literal("staff")
                .then(literal("add").then(argument("name", StringArgumentType.word()).executes(c ->
                        say(c, staff.staffNames.add(str(c, "name")) ? "StaffWatch-Name hinzugefügt" : "Name bereits eingetragen"))))
                .then(literal("del").then(argument("name", StringArgumentType.word()).executes(c ->
                        say(c, staff.staffNames.remove(str(c, "name")) ? "StaffWatch-Name entfernt" : "Name nicht gefunden"))))
                .then(literal("list").executes(c -> say(c, "Beobachtete Namen: " + String.join(", ", staff.staffNames.get())))));

        // ---- Soft keyboard click feedback (only while Nightfall UI/editor is open) ----
        root.then(literal("keysounds")
                .then(argument("state", StringArgumentType.word()).executes(c -> {
                    String state = str(c, "state");
                    if (!state.equalsIgnoreCase("on") && !state.equalsIgnoreCase("off"))
                        return say(c, "Verwendung: /nf keysounds on|off");
                    Config.keySounds = state.equalsIgnoreCase("on");
                    Config.save();
                    return say(c, "Tastatur-Klicksounds: " + (Config.keySounds ? "AN" : "AUS"));
                })));

        // ---- BaseFinder Pro controls: pause/resume/status ----
        BaseFinder finder = ModuleManager.get(BaseFinder.class);
        root.then(literal("scan")
                .then(literal("pause").executes(c -> {
                    finder.setPaused(true);
                    return say(c, "BaseFinder pausiert. Bereits gesammelte Ergebnisse bleiben erhalten.");
                }))
                .then(literal("resume").executes(c -> {
                    finder.setPaused(false);
                    return say(c, "BaseFinder fortgesetzt.");
                }))
                .then(literal("status").executes(c -> say(c,
                        "BaseFinder " + (finder.isEnabled() ? "AN" : "AUS")
                                + (finder.isPaused() ? " (PAUSIERT)" : "")
                                + " | Queue " + finder.queueSize()
                                + " | gescannt " + finder.scannedSinceRefill() + "/" + finder.candidatesLastRefill()
                                + " | Ergebnisse " + finder.resultCount()
                                + " | letztes Budget " + String.format(java.util.Locale.ROOT, "%.2f ms", finder.lastScanMs()))))
                .then(literal("clear").executes(c -> {
                    finder.clearMarks();
                    return say(c, "Markierungen dieses Servers/Dimension geloescht");
                })));

        d.register(root);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> listCmd(String lit, ListSetting list, String label) {
        return literal(lit)
                .then(literal("add").then(argument("name", StringArgumentType.word())
                        .executes(c -> say(c, list.add(str(c, "name")) ? label + ": hinzugefuegt" : "Schon vorhanden"))))
                .then(literal("del").then(argument("name", StringArgumentType.word())
                        .executes(c -> say(c, list.remove(str(c, "name")) ? label + ": entfernt" : "Nicht vorhanden"))));
    }

    private static int addWp(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c, String name, String cat) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        WaypointManager.add(name, cat, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        return say(c, "Waypoint '" + name + "' gesetzt");
    }
}
