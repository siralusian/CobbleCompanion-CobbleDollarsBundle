package com.cobblecompanion.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Eigenständige Berechtigungsstufe für den Professor-Tab, komplett unabhängig von Minecrafts
 * Vanilla-OP-Status (bewusste Design-Entscheidung: ein Vanilla-OP hat NICHT automatisch Zugriff).
 * Persistiert als Gson-JSON im Weltordner, gleiches Muster wie {@link ServerRulesManager}.
 *
 * AdminOp schließt Op mit ein (siehe isOp()). Der Server-Besitzer muss sich einmalig über die
 * Server-Konsole selbst AdminOp geben ("/companion adminop &lt;Name&gt;" aus der Konsole
 * ausgeführt braucht keine Berechtigung - siehe CobbleCompanionCommands), danach kann er weitere
 * Spieler per Op/AdminOp berechtigen.
 */
public class AdminPermissionManager {

    private static class Data {
        Set<String> op = new HashSet<>();
        Set<String> adminOp = new HashSet<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("cobblecompanion_admin.json");
        load();
    }

    public static boolean isAdminOp(UUID uuid) {
        return data.adminOp.contains(uuid.toString());
    }

    /** true bei Op ODER AdminOp - AdminOp schließt alle Op-Rechte mit ein. */
    public static boolean isOp(UUID uuid) {
        return data.op.contains(uuid.toString()) || isAdminOp(uuid);
    }

    /** Für die Konsole (kein Spieler) und für Spieler mit AdminOp: darf andere Spieler berechtigen. */
    public static boolean canGrant(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null || isAdminOp(player.getUUID());
    }

    public static void grantOp(UUID uuid) {
        data.op.add(uuid.toString());
        save();
    }

    public static void grantAdminOp(UUID uuid) {
        data.adminOp.add(uuid.toString());
        save();
    }

    public static void revokeOp(UUID uuid) {
        data.op.remove(uuid.toString());
        data.adminOp.remove(uuid.toString());
        save();
    }

    public static void revokeAdminOp(UUID uuid) {
        data.adminOp.remove(uuid.toString());
        save();
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
        if (data.op == null) data.op = new HashSet<>();
        if (data.adminOp == null) data.adminOp = new HashSet<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
