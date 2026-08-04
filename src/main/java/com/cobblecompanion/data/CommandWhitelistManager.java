package com.cobblecompanion.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Nutzer-Vorgabe (Punkt 3 der CobbleDollars-Wunschliste): AdminOp kann einzelne Befehle für
 * Spieler im "beschnittenen" Creative (siehe CreativeRestrictionHandler.isRestricted) freischalten,
 * situationsbedingt an- und abschaltbar über eine einfache Liste. Muster pro Eintrag (Nutzer-
 * Vorgabe, wörtlich): endet der Eintrag auf ein Leerzeichen + "*" (z.B. "tag *"), ist JEDER Befehl
 * erlaubt, der mit "tag " beginnt (beliebige Argumente); ohne "*" (z.B. "tag add @p Test") ist NUR
 * genau dieser Befehl (exaktes Zeichenketten-Match) erlaubt. Einträge werden OHNE führenden "/"
 * gespeichert. Freigeschaltete Befehle laufen mit vollen Admin-Rechten (Level 4, Nutzer-Vorgabe) -
 * siehe CommandWhitelistHandler für die tatsächliche Durchsetzung per CommandEvent.
 */
public class CommandWhitelistManager {

    private static class Data {
        List<String> patterns = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_command_whitelist.json");
        load();
    }

    public static List<String> getPatterns() {
        return data.patterns;
    }

    /**
     * Für CommandWhitelistSuggestionHandler: die Menge der WURZEL-Literale (erstes Wort) aller
     * konfigurierten Muster, z.B. {"tag"} für das Muster "tag *". Der Sonderfall "*" (kompletter
     * Freifahrtschein) liefert bewusst KEINE Literale zurück - ein Freischalten JEDES Root-Knotens
     * im Befehlsbaum wäre unverhältnismäßig riskant für eine reine Tab-Vervollständigungs-Komfort-
     * funktion und wird deshalb hier nicht unterstützt (die Ausführung selbst bleibt über
     * CommandWhitelistHandler trotzdem funktionsfähig).
     */
    public static java.util.Set<String> getRootLiterals() {
        java.util.Set<String> roots = new java.util.HashSet<>();
        for (String pattern : data.patterns) {
            if (pattern.equals("*")) continue;
            int spaceIdx = pattern.indexOf(' ');
            String root = spaceIdx < 0 ? pattern : pattern.substring(0, spaceIdx);
            if (!root.isEmpty()) roots.add(root);
        }
        return roots;
    }

    /** true, wenn hinzugefügt (false bei Duplikat - schon vorhanden). */
    public static boolean addPattern(String pattern) {
        String normalized = normalize(pattern);
        if (normalized.isEmpty() || data.patterns.contains(normalized)) return false;
        data.patterns.add(normalized);
        save();
        return true;
    }

    public static boolean removePattern(String pattern) {
        boolean removed = data.patterns.remove(normalize(pattern));
        if (removed) save();
        return removed;
    }

    private static String normalize(String pattern) {
        String p = pattern.trim();
        if (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    /**
     * command: der volle eingegebene Befehlstext OHNE führenden "/" (siehe CommandWhitelistHandler).
     * Siehe Klassenkommentar für die genaue Wildcard-Semantik.
     */
    public static boolean isAllowed(String command) {
        for (String pattern : data.patterns) {
            if (pattern.endsWith(" *")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                if (command.equals(prefix) || command.startsWith(prefix + " ")) return true;
            } else if (pattern.equals("*")) {
                return true; // theoretisch möglich (kompletter Freifahrtschein), bewusst nicht gesondert verboten
            } else if (command.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
        if (data.patterns == null) data.patterns = new ArrayList<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
