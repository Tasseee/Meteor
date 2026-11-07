package dev.nightfall;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.nightfall.config.Config;
import dev.nightfall.ai.OfflineAgentCore;
import dev.nightfall.ai.AIBuilder;
import dev.nightfall.ai.OfflineAgentActionBridge;
import dev.nightfall.ai.OfflineAgentMemory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AutoTpa;
import dev.nightfall.modules.AutoText;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.modules.TrapperBot;
import dev.nightfall.modules.FakeBases;
import dev.nightfall.modules.InvSeeValue;
import dev.nightfall.modules.Lyrics;
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
    private static long offlineAgentSequence;
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

        // ---- Lyrics playback sidecar ----
        Lyrics lyrics = ModuleManager.get(Lyrics.class);
        root.then(literal("lyrics")
                .then(literal("status").executes(c -> say(c, "Lyrics " + (lyrics.isEnabled() ? "AN" : "AUS") + " | " + lyrics.status())))
                .then(literal("reconnect").executes(c -> {
                    lyrics.reconnect();
                    return say(c, "Lyrics-Verbindung wird neu aufgebaut.");
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

        root.then(agentCommand());

        d.register(root);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> agentCommand() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("agent");
        root.then(literal("status").executes(c -> offlineAgentStatus(c)));
        root.then(literal("actions").executes(c -> offlineAgentActions(c)));
        LiteralArgumentBuilder<FabricClientCommandSource> memory = literal("memory");
        memory.executes(c -> offlineAgentMemory(c, false));
        memory.then(literal("clear").executes(c -> offlineAgentMemory(c, true)));
        root.then(memory);
        root.then(literal("prepare").executes(c -> prepareOfflineAgent(c)));
        root.then(literal("help").executes(c -> say(c,
                "Offline-Agent: /nf agent memory [clear] · /nf agent gather <wood|block|ore> <item> <count> · craft <item> [count] · smelt <item> <count> · wood <bretter> · iron <erz> · shelter · build <starter|resume|vorlage|pause|continue|stop> · schematic <datei> · navigate <x> <y> <z> · mine <x> <y> <z> · attack · guard · sort · search [struktur]. "
                        + "Aktionen entdecken: /nf agent actions · weitere lokale Funktionen: /nf agent action <name> <json>. Jeder Auftrag zeigt vor Ausführung eine Vorschau. "
                        + "confirm bestätigt, reject lehnt ab, verify success|failed prüft das Ergebnis, cancel bricht ab.")));
        LiteralArgumentBuilder<FabricClientCommandSource> action = literal("action");
        var actionName = argument("name", StringArgumentType.word());
        actionName.executes(c -> submitRegistryAction(c, str(c, "name"), "{}"));
        actionName.then(argument("parameters", StringArgumentType.greedyString())
                .executes(c -> submitRegistryAction(c, str(c, "name"), str(c, "parameters"))));
        action.then(actionName);
        root.then(action);
        root.then(literal("confirm").executes(c -> confirmOfflineAgent(c)));
        root.then(literal("reject").executes(c -> rejectOfflineAgent(c)));
        root.then(literal("cancel").executes(c -> cancelOfflineAgent(c)));
        root.then(literal("resume_task").executes(c -> submitOfflineAgentGoal(c,
                "Setze einen lokal gespeicherten Sammel- oder Navigationsauftrag fort.",
                "resume_saved_task", Map.of())));
        root.then(literal("clear_task").executes(c -> submitOfflineAgentGoal(c,
                "Lösche den gespeicherten Sammel- oder Navigationsauftrag dieser Welt.",
                "clear_saved_task", Map.of())));

        LiteralArgumentBuilder<FabricClientCommandSource> verify = literal("verify");
        verify.then(literal("success").executes(c ->
                verifyOfflineAgent(c, true, "Erfolg vom Spieler bestätigt.")));
        LiteralArgumentBuilder<FabricClientCommandSource> failed = literal("failed");
        failed.executes(c -> verifyOfflineAgent(c, false, "Fehlschlag vom Spieler bestätigt."));
        failed.then(argument("reason", StringArgumentType.greedyString())
                .executes(c -> verifyOfflineAgent(c, false, str(c, "reason"))));
        verify.then(failed);
        root.then(verify);

        LiteralArgumentBuilder<FabricClientCommandSource> walk = literal("walk");
        walk.then(argument("ticks", IntegerArgumentType.integer(1, 100))
                .executes(c -> submitOfflineAgentGoal(c, "Gehe für eine kurze, begrenzte Zeit vorwärts.",
                        "walk_forward", Map.of("ticks", Integer.toString(IntegerArgumentType.getInteger(c, "ticks"))))));
        root.then(walk);

        LiteralArgumentBuilder<FabricClientCommandSource> navigate = literal("navigate");
        var nx = argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000));
        var ny = argument("y", IntegerArgumentType.integer(-2_048, 2_048));
        var nz = argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000));
        nz.executes(c -> submitOfflineAgentGoal(c, "Navigiere zum angegebenen Ziel.", "navigate_to", Map.of(
                "x", Integer.toString(IntegerArgumentType.getInteger(c, "x")),
                "y", Integer.toString(IntegerArgumentType.getInteger(c, "y")),
                "z", Integer.toString(IntegerArgumentType.getInteger(c, "z")))));
        ny.then(nz);
        nx.then(ny);
        navigate.then(nx);
        root.then(navigate);

        LiteralArgumentBuilder<FabricClientCommandSource> mine = literal("mine");
        var mx = argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000));
        var my = argument("y", IntegerArgumentType.integer(-2_048, 2_048));
        var mz = argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000));
        mz.executes(c -> submitOfflineAgentGoal(c, "Baue den angegebenen Block ab.", "mine_block", Map.of(
                "x", Integer.toString(IntegerArgumentType.getInteger(c, "x")),
                "y", Integer.toString(IntegerArgumentType.getInteger(c, "y")),
                "z", Integer.toString(IntegerArgumentType.getInteger(c, "z")))));
        my.then(mz);
        mx.then(my);
        mine.then(mx);
        root.then(mine);

        LiteralArgumentBuilder<FabricClientCommandSource> gather = literal("gather");
        var gatherKind = argument("kind", StringArgumentType.word());
        var gatherItem = argument("item", StringArgumentType.word());
        gatherItem.then(argument("count", IntegerArgumentType.integer(1, 256))
                .executes(c -> queueGatherGoal(c, str(c, "kind"), str(c, "item"),
                        IntegerArgumentType.getInteger(c, "count"))));
        gatherKind.then(gatherItem);
        gather.then(gatherKind);
        root.then(gather);

        LiteralArgumentBuilder<FabricClientCommandSource> craft = literal("craft");
        var craftItem = argument("item", StringArgumentType.word());
        craftItem.executes(c -> submitOfflineAgentGoal(c, "Stelle das gewünschte Item her.",
                "craft_item", Map.of("item", str(c, "item"), "count", "1")));
        craftItem.then(argument("count", IntegerArgumentType.integer(1, 256))
                .executes(c -> submitOfflineAgentGoal(c, "Stelle die gewünschte Anzahl her.",
                        "craft_item", Map.of("item", str(c, "item"),
                                "count", Integer.toString(IntegerArgumentType.getInteger(c, "count"))))));
        craft.then(craftItem);
        root.then(craft);

        LiteralArgumentBuilder<FabricClientCommandSource> smelt = literal("smelt");
        var smeltItem = argument("item", StringArgumentType.word());
        smeltItem.then(argument("count", IntegerArgumentType.integer(1, 16))
                .executes(c -> submitOfflineAgentGoal(c, "Schmelze die angegebene Menge im erreichbaren Ofen.",
                        "smelt_item", Map.of("item", str(c, "item"),
                                "count", Integer.toString(IntegerArgumentType.getInteger(c, "count"))))));
        smelt.then(smeltItem);
        root.then(smelt);

        LiteralArgumentBuilder<FabricClientCommandSource> eat = literal("eat");
        eat.executes(c -> submitOfflineAgentGoal(c, "Iss ein geeignetes Nahrungsmittel.", "eat_food", Map.of()));
        eat.then(argument("item", StringArgumentType.word())
                .executes(c -> submitOfflineAgentGoal(c, "Iss bevorzugt das angegebene Nahrungsmittel.",
                        "eat_food", Map.of("item", str(c, "item")))));
        root.then(eat);

        root.then(literal("sort").executes(c -> submitOfflineAgentGoal(c,
                "Sortiere das Inventar nach den lokalen Regeln.", "sort_inventory", Map.of())));

        LiteralArgumentBuilder<FabricClientCommandSource> search = literal("search");
        search.executes(c -> submitOfflineAgentGoal(c, "Erkunde die Umgebung nach Strukturhinweisen.",
                "search_structures", Map.of("structure", "any")));
        search.then(argument("structure", StringArgumentType.word())
                .executes(c -> submitOfflineAgentGoal(c, "Erkunde die Umgebung nach der angegebenen Struktur.",
                        "search_structures", Map.of("structure", str(c, "structure")))));
        root.then(search);

        root.then(literal("attack").executes(c -> submitOfflineAgentGoal(c,
                "Greife einen nahen feindlichen Mob an.", "attack_nearest_hostile", Map.of())));
        root.then(literal("guard").executes(c -> submitOfflineAgentGoal(c,
                "Verteidige den Spieler vor nahen feindlichen Mobs.", "guard_nearby_hostiles", Map.of())));

        LiteralArgumentBuilder<FabricClientCommandSource> build = literal("build");
        build.then(literal("starter").executes(c -> submitOfflineAgentGoal(c,
                "Baue das vorbereitete Starter-Schutzhaus.", "build_starter_shelter", Map.of())));
        build.then(argument("structure", StringArgumentType.word())
                .executes(c -> submitOfflineAgentGoal(c, "Bereite die angegebene lokale Bauvorlage vor.",
                        "build_structure", Map.of("structure", str(c, "structure")))));
        build.then(literal("resume").executes(c -> submitOfflineAgentGoal(c,
                "Setze einen lokal gespeicherten Bauplan fort.", "resume_saved_build", Map.of())));
        build.then(literal("pause").executes(c -> say(c, AIBuilder.pause())));
        build.then(literal("continue").executes(c -> say(c, AIBuilder.resume())));
        build.then(literal("stop").executes(c -> {
            try { return say(c, Nightfall.offlineAgentBridge().cancelBuilderAction()); }
            catch (RuntimeException failure) { return say(c, "Builder-Stopp fehlgeschlagen: " + safeAgentMessage(failure)); }
        }));
        root.then(build);

        LiteralArgumentBuilder<FabricClientCommandSource> schematic = literal("schematic");
        schematic.then(argument("file", StringArgumentType.word())
                .executes(c -> submitOfflineAgentGoal(c, "Bereite den ersten Abschnitt der lokalen Schematic vor.",
                        "build_schematic", Map.of("file", str(c, "file"),
                                "part_x", "0", "part_y", "0", "part_z", "0"))));
        root.then(schematic);

        LiteralArgumentBuilder<FabricClientCommandSource> wood = literal("wood");
        wood.then(argument("planks", IntegerArgumentType.integer(1, 256))
                .executes(c -> queueWoodWorkflow(c, IntegerArgumentType.getInteger(c, "planks"))));
        root.then(wood);

        LiteralArgumentBuilder<FabricClientCommandSource> iron = literal("iron");
        iron.then(argument("count", IntegerArgumentType.integer(1, 64))
                .executes(c -> queueIronWorkflow(c, IntegerArgumentType.getInteger(c, "count"))));
        root.then(iron);

        root.then(literal("shelter").executes(c -> queueShelterWorkflow(c)));
        return root;
    }

    private static int submitRegistryAction(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c,
            String actionName, String rawParameters) {
        try {
            if (!OfflineAgentActionBridge.supportsAction(actionName))
                throw new IllegalArgumentException("Diese Aktion ist nicht für das begrenzte Offline-Auftragsformat freigegeben oder ihre Parameter sind nicht unterstützt.");
            if (rawParameters == null || rawParameters.length() > 40_000)
                throw new IllegalArgumentException("Aktionsargumente dürfen höchstens 40.000 Zeichen lang sein.");
            JsonElement parsed = JsonParser.parseString(rawParameters);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Aktionen benötigen ein JSON-Objekt als Argumente.");
            JsonObject json = parsed.getAsJsonObject();
            if (json.size() > 16) throw new IllegalArgumentException("Zu viele Aktionsparameter.");
            Map<String, String> parameters = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || value.isJsonNull())
                    throw new IllegalArgumentException("Null-Werte sind für Offline-Aktionsparameter nicht erlaubt.");
                String encoded = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        ? value.getAsString() : value.toString();
                if (encoded.length() > 32_768)
                    throw new IllegalArgumentException("Ein Aktionsparameter überschreitet das 32-KiB-Limit.");
                parameters.put(entry.getKey(), encoded);
            }
            return submitOfflineAgentGoal(c, "Lokale Nightfall-Aktion: " + actionName,
                    actionName, parameters, 1);
        } catch (RuntimeException failure) {
            return say(c, "Lokale Aktion nicht eingereiht: " + safeAgentMessage(failure));
        }
    }

    private static int submitOfflineAgentGoal(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c,
            String description, String action, Map<String, String> parameters) {
        return submitOfflineAgentGoal(c, description, action, parameters, 2);
    }

    private static int submitOfflineAgentGoal(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c,
            String description, String action, Map<String, String> parameters, int maxAttempts) {
        try {
            Nightfall.offlineAgentBridge().ensureReady();
            String goalId = nextOfflineGoalId();
            OfflineAgentCore.Step step = new OfflineAgentCore.Step(
                    "action", action, parameters, List.of(), maxAttempts);
            Nightfall.offlineAgentCore().submit(new OfflineAgentCore.Goal(
                    goalId, description, 0, List.of(step)));
            Nightfall.offlineAgentBridge().prepareNext();
            return say(c, "Offline-Auftrag " + goalId + " eingereiht. Prüfe die Vorschau im Chat; "
                    + "bestätige mit /nf agent confirm. Status: /nf agent status.");
        } catch (RuntimeException failure) {
            return say(c, "Offline-Auftrag nicht eingereiht: " + safeAgentMessage(failure));
        }
    }

    private static int queueGatherGoal(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c,
            String kind, String item, int count) {
        String action = switch (kind.toLowerCase(java.util.Locale.ROOT)) {
            case "wood", "holz" -> "gather_wood";
            case "block", "blocks" -> "gather_block";
            case "ore", "erz" -> "gather_ore";
            default -> null;
        };
        if (action == null) return say(c, "Art muss wood, block oder ore sein.");
        return submitOfflineAgentGoal(c, "Sammle " + count + " × " + item + ".",
                action, Map.of("item", item, "count", Integer.toString(count)));
    }

    private static int queueWoodWorkflow(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c, int plankCount) {
        try {
            Nightfall.offlineAgentBridge().ensureReady();
            String goalId = nextOfflineGoalId();
            int logsRequired = Math.max(1, (plankCount + 3) / 4);
            int logsOwned = countInventoryItem("minecraft:oak_log");
            List<OfflineAgentCore.Step> steps = new java.util.ArrayList<>();
            String previousStep = null;
            if (logsOwned < logsRequired) {
                steps.add(new OfflineAgentCore.Step(
                        "gather_logs", "gather_wood",
                        Map.of("item", "minecraft:oak_log", "count", Integer.toString(logsRequired)),
                        List.of(), 2));
                previousStep = "gather_logs";
            }
            steps.add(new OfflineAgentCore.Step(
                    "craft_planks", "craft_item",
                    Map.of("item", "minecraft:oak_planks", "count", Integer.toString(plankCount)),
                    previousStep == null ? List.of() : List.of(previousStep), 2));
            Nightfall.offlineAgentCore().submit(new OfflineAgentCore.Goal(
                    goalId, "Beschaffe bei Bedarf Eichenstämme und stelle " + plankCount + " zusätzliche Eichenbretter her.",
                    5, steps));
            Nightfall.offlineAgentBridge().prepareNext();
            return say(c, "Holz-Workflow " + goalId + " eingereiht. Vorhandene Eichenstämme: " + logsOwned
                    + ", Zielbestand für die Rezeptmenge: " + logsRequired + ". "
                    + "Ausreichende Stämme werden nicht erneut gesammelt; Crafting stellt " + plankCount
                    + " zusätzliche Bretter her. Jeder Schritt hat eine eigene Vorschau.");
        } catch (RuntimeException failure) {
            return say(c, "Holz-Workflow nicht eingereiht: " + safeAgentMessage(failure));
        }
    }

    private static int queueShelterWorkflow(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c) {
        try {
            Nightfall.offlineAgentBridge().ensureReady();
            String goalId = nextOfflineGoalId();
            final int requiredPlanks = 54;
            int planksOwned = countInventoryItem("minecraft:oak_planks");
            int missingPlanks = Math.max(0, requiredPlanks - planksOwned);
            int logsRequired = (missingPlanks + 3) / 4;
            int logsOwned = countInventoryItem("minecraft:oak_log");
            List<OfflineAgentCore.Step> steps = new java.util.ArrayList<>();
            String previousStep = null;

            if (missingPlanks > 0) {
                if (logsOwned < logsRequired) {
                    steps.add(new OfflineAgentCore.Step(
                            "gather_logs", "gather_wood",
                            Map.of("item", "minecraft:oak_log", "count", Integer.toString(logsRequired)),
                            List.of(), 2));
                    previousStep = "gather_logs";
                }
                steps.add(new OfflineAgentCore.Step(
                        "craft_planks", "craft_item",
                        Map.of("item", "minecraft:oak_planks", "count", Integer.toString(missingPlanks)),
                        previousStep == null ? List.of() : List.of(previousStep), 2));
                previousStep = "craft_planks";
            }

            steps.add(new OfflineAgentCore.Step(
                    "build_shelter", "build_structure",
                    Map.of("structure", "starter_shelter", "material", "minecraft:oak_planks"),
                    previousStep == null ? List.of() : List.of(previousStep), 2));
            Nightfall.offlineAgentCore().submit(new OfflineAgentCore.Goal(
                    goalId, "Bereite 54 Eichenbretter vor und baue ein Starter-Schutzhaus.",
                    15, steps));
            Nightfall.offlineAgentBridge().prepareNext();
            return say(c, "Starter-Schutzhaus-Workflow " + goalId + " eingereiht. Bretter vorhanden: "
                    + planksOwned + "/54; Eichenstämme vorhanden: " + logsOwned + ". "
                    + "Nightfall sammelt und craftet nur die Fehlmenge und bereitet danach den Bau vor. "
                    + "Jeder Schritt bekommt eine eigene Vorschau.");
        } catch (RuntimeException failure) {
            return say(c, "Schutzhaus-Workflow nicht eingereiht: " + safeAgentMessage(failure));
        }
    }

    private static int queueIronWorkflow(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c, int count) {
        try {
            Nightfall.offlineAgentBridge().ensureReady();
            String goalId = nextOfflineGoalId();
            int rawIronOwned = countInventoryItem("minecraft:raw_iron");
            List<OfflineAgentCore.Step> steps = new java.util.ArrayList<>();
            String previousStep = null;
            if (rawIronOwned < count) {
                steps.add(new OfflineAgentCore.Step(
                        "gather", "gather_ore",
                        Map.of("item", "minecraft:raw_iron", "count", Integer.toString(count)),
                        List.of(), 2));
                previousStep = "gather";
            }
            int fuelRequired = (count + 7) / 8;
            int coalOwned = countInventoryItem("minecraft:coal");
            int charcoalOwned = countInventoryItem("minecraft:charcoal");
            if (Math.max(coalOwned, charcoalOwned) < fuelRequired) {
                String fuelStep = "gather_fuel";
                steps.add(new OfflineAgentCore.Step(
                        fuelStep, "gather_ore",
                        Map.of("item", "minecraft:coal", "count", Integer.toString(fuelRequired)),
                        previousStep == null ? List.of() : List.of(previousStep), 2));
                previousStep = fuelStep;
            }
            int remaining = count;
            int batch = 1;
            while (remaining > 0) {
                int batchCount = Math.min(16, remaining);
                String stepId = "smelt_" + batch;
                steps.add(new OfflineAgentCore.Step(
                        stepId, "smelt_item",
                        Map.of("item", "minecraft:raw_iron", "count", Integer.toString(batchCount)),
                        previousStep == null ? List.of() : List.of(previousStep), 2));
                previousStep = stepId;
                remaining -= batchCount;
                batch++;
            }
            Nightfall.offlineAgentCore().submit(new OfflineAgentCore.Goal(
                    goalId, "Beschaffe bei Bedarf und schmelze " + count + " rohes Eisen in Ofen-Chargen.",
                    10, steps));
            Nightfall.offlineAgentBridge().prepareNext();
            return say(c, "Eisen-Workflow " + goalId + " eingereiht. Vorhandenes Roheisen: " + rawIronOwned
                    + "; Kohle: " + coalOwned + "; Holzkohle: " + charcoalOwned
                    + ". Fehlendes Erz und bei Bedarf ein ausreichender Kohlevorrat werden zuerst gesammelt. "
                    + "Danach folgen " + ((count + 15) / 16) + " Schmelz-Charge(n) mit höchstens 16 Stück. "
                    + "Jede Charge braucht eine eigene Vorschau und wird erst nach bestätigtem Ergebnis fortgesetzt.");
        } catch (RuntimeException failure) {
            return say(c, "Workflow nicht eingereiht: " + safeAgentMessage(failure));
        }
    }

    private static int countInventoryItem(String itemId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return 0;
        var item = Registries.ITEM.get(id);
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            var stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }

    private static int prepareOfflineAgent(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c) {
        try {
            var prepared = Nightfall.offlineAgentBridge().prepareNext();
            if (prepared.isPresent()) return say(c, "Die nächste Offline-Agent-Vorschau wurde im Chat angezeigt.");
            String prompt = Nightfall.offlineAgentBridge().preparationPrompt();
            return say(c, prompt.isBlank()
                    ? "Aktuell ist kein weiterer Schritt zur Vorbereitung bereit."
                    : prompt);
        } catch (RuntimeException failure) {
            return say(c, "Vorbereitung fehlgeschlagen: " + safeAgentMessage(failure));
        }
    }

    private static int confirmOfflineAgent(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c) {
        var preview = Nightfall.offlineAgentBridge().pendingPreview();
        if (preview.isEmpty()) return say(c, "Es liegt keine Offline-Agent-Vorschau zur Bestätigung vor.");
        try {
            String result = Nightfall.offlineAgentBridge().confirm(preview.get());
            return say(c, result + " Lokale Controller-Abschlüsse prüft Nightfall automatisch. Nur externe Serveraktionen musst du nach ihrer Serverantwort mit /nf agent verify success oder /nf agent verify failed bestätigen. Status: /nf agent status.");
        } catch (RuntimeException failure) {
            return say(c, "Bestätigung fehlgeschlagen: " + safeAgentMessage(failure));
        }
    }

    private static int rejectOfflineAgent(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c) {
        var preview = Nightfall.offlineAgentBridge().pendingPreview();
        if (preview.isEmpty()) return say(c, "Es liegt keine Offline-Agent-Vorschau zum Ablehnen vor.");
        try {
            Nightfall.offlineAgentBridge().reject(preview.get());
            return say(c, "Vorschau abgelehnt. Diese Aktion wurde nicht ausgeführt.");
        } catch (RuntimeException failure) {
            return say(c, "Ablehnen fehlgeschlagen: " + safeAgentMessage(failure));
        }
    }

    private static int verifyOfflineAgent(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c,
            boolean success, String observation) {
        try {
            Nightfall.offlineAgentBridge().verifyLastAction(success, observation);
            Nightfall.offlineAgentBridge().prepareNext();
            return say(c, success
                    ? "Ergebnis als erfolgreich bestätigt. Abhängige Schritte können jetzt vorbereitet werden."
                    : "Fehlschlag eingetragen. Ein erneuter Versuch wird – falls noch erlaubt – mit neuer Vorschau vorbereitet.");
        } catch (RuntimeException failure) {
            return say(c, "Ergebnisprüfung nicht möglich: " + safeAgentMessage(failure));
        }
    }

    private static int cancelOfflineAgent(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c) {
        try {
            Nightfall.offlineAgentBridge().cancelAll();
            return say(c, "Alle aktiven Offline-Agent-Aufträge wurden abgebrochen.");
        } catch (RuntimeException failure) {
            return say(c, "Abbruch fehlgeschlagen: " + safeAgentMessage(failure));
        }
    }

    private static int offlineAgentActions(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c) {
        Map<String, Map<String, String>> actions = OfflineAgentActionBridge.supportedActions();
        if (actions.isEmpty()) return say(c, "Das lokale Aktionsregister enthält keine für den Offline-Agenten geeigneten Aktionen.");
        StringBuilder out = new StringBuilder("Lokale Offline-Aktionen (")
                .append(actions.size()).append("):");
        int shown = 0;
        for (Map.Entry<String, Map<String, String>> entry : actions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (shown++ >= 45) {
                out.append("\n… weitere Aktionen sind über /nf agent action <name> <json> verfügbar.");
                break;
            }
            out.append("\n").append(entry.getKey()).append(" {");
            boolean first = true;
            for (Map.Entry<String, String> parameter : entry.getValue().entrySet()) {
                if (!first) out.append(", ");
                first = false;
                out.append(parameter.getKey()).append(":").append(parameter.getValue());
            }
            out.append("}");
        }
        out.append("\nAufruf: /nf agent action <name> <json>. Vor jeder Weltänderung erscheint eine Vorschau.");
        return say(c, out.toString());
    }

    private static int offlineAgentMemory(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c,
            boolean clear) {
        if (clear) {
            boolean cleared = OfflineAgentMemory.clear();
            return say(c, cleared
                    ? "Das lokale Offline-Agent-Gedächtnis wurde gelöscht. Laufende Aufträge sind davon nicht betroffen."
                    : "Der Verlauf wurde im Arbeitsspeicher geleert, aber die Datei konnte nicht gelöscht werden. Prüfe config/nightfall/agent_memory.json.");
        }
        List<OfflineAgentMemory.Entry> entries = OfflineAgentMemory.recent(6);
        if (entries.isEmpty()) return say(c, "Das lokale Offline-Agent-Gedächtnis ist noch leer.");
        StringBuilder out = new StringBuilder("Lokales Offline-Agent-Gedächtnis (neueste Aufträge zuerst):");
        for (OfflineAgentMemory.Entry entry : entries) {
            out.append("\n").append(entry.status()).append(" · ")
                    .append(entry.description()).append(" · ")
                    .append(java.time.Instant.ofEpochMilli(entry.completedAtMillis()));
            int shownSteps = 0;
            for (OfflineAgentMemory.StepMemory step : entry.steps()) {
                if (shownSteps++ >= 6) {
                    out.append("\n  … weitere Schritte gespeichert");
                    break;
                }
                out.append("\n  ").append(step.action()).append(": ")
                        .append(step.status()).append(" · Versuche ").append(step.attempts());
            }
        }
        out.append("\nGespeichert lokal in config/nightfall/agent_memory.json. Löschen: /nf agent memory clear");
        return say(c, out.toString());
    }

    private static int offlineAgentStatus(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c) {
        OfflineAgentCore core = Nightfall.offlineAgentCore();
        var bridge = Nightfall.offlineAgentBridge();
        StringBuilder out = new StringBuilder("Offline-Agent:");
        if (bridge.pendingPreview().isPresent()) {
            var preview = bridge.pendingPreview().get();
            out.append("\nVorschau wartet auf Bestätigung: ").append(preview.goalId())
                    .append("/").append(preview.stepId());
        } else if (bridge.awaitingManualVerification()) {
            out.append("\nEine externe Serveraktion wartet auf deine Rückmeldung: /nf agent verify success|failed");
        } else if (bridge.controllerActionInProgress()) {
            out.append("\nLokale Minecraft-Aktion läuft; Nightfall prüft ihren Abschluss automatisch.");
        } else if (bridge.waitingForSetup()) {
            out.append("\nDer nächste Schritt wartet auf deine Vorbereitung: ").append(bridge.preparationPrompt());
        } else {
            out.append("\nKeine Vorschau und keine Ergebnisprüfung offen.");
        }
        int shown = 0;
        for (OfflineAgentCore.GoalView goal : core.snapshot()) {
            if (shown++ >= 8) {
                out.append("\nWeitere Aufträge in der Historie vorhanden.");
                break;
            }
            out.append("\n").append(goal.id()).append(" · ").append(goal.status())
                    .append(" · ").append(goal.description());
            for (OfflineAgentCore.StepView step : goal.steps()) {
                out.append("\n  ").append(step.id()).append(": ").append(step.action())
                        .append(" · ").append(step.status()).append(" · Versuche ")
                        .append(step.attempts()).append(step.detail().isBlank() ? "" : " · " + step.detail());
            }
        }
        if (!bridge.lastError().isBlank()) out.append("\nLetzter Fehler: ").append(bridge.lastError());
        return say(c, out.toString());
    }

    private static String nextOfflineGoalId() {
        return "manual-" + (++offlineAgentSequence);
    }

    private static String safeAgentMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() <= 240 ? message : message.substring(0, 237) + "...";
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
