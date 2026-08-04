package com.cobblecompanion.client.data;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Hält die zuletzt vom Server empfangene Spielerliste + die aktuelle Auswahl für den Professor-Tab. */
public class ClientProfessorHelper {

    public static class PlayerItem {
        public final UUID uuid;
        public final String name;
        public final boolean online;

        public PlayerItem(UUID uuid, String name, boolean online) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
        }
    }

    private static List<PlayerItem> players = new ArrayList<>();
    private static UUID selectedUuid = null;

    public static void setPlayers(List<String> raw) {
        List<PlayerItem> parsed = new ArrayList<>();
        for (String line : raw) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3) continue;
            try {
                UUID uuid = UUID.fromString(parts[0]);
                String name = parts[1];
                boolean online = Boolean.parseBoolean(parts[2]);
                parsed.add(new PlayerItem(uuid, name, online));
            } catch (Exception ignored) {}
        }
        players = parsed;
    }

    public static List<PlayerItem> getPlayers() {
        return players;
    }

    public static void select(UUID uuid) {
        selectedUuid = uuid;
    }

    public static PlayerItem getSelected() {
        if (selectedUuid == null) return null;
        for (PlayerItem p : players) {
            if (p.uuid.equals(selectedUuid)) return p;
        }
        return null;
    }

    // ===== PC-Ansicht (Cobblemons PCGUI, ClientPC/ClientParty direkt konstruiert) =====
    private static String pcTargetName = "";
    private static List<String> pcBoxNames = new ArrayList<>();
    private static List<CompoundTag> pcEntries = new ArrayList<>();
    private static List<CompoundTag> pcPartyEntries = new ArrayList<>();
    private static int pcDataVersion = 0;

    public static void setPCData(String targetName, List<String> boxNames, List<CompoundTag> pcEntries, List<CompoundTag> partyEntries) {
        pcTargetName = targetName;
        pcBoxNames = boxNames;
        ClientProfessorHelper.pcEntries = pcEntries;
        pcPartyEntries = partyEntries;
        pcDataVersion++;
    }

    public static String getPCTargetName() {
        return pcTargetName;
    }

    public static List<String> getPCBoxNames() {
        return pcBoxNames;
    }

    public static List<CompoundTag> getPCEntries() {
        return pcEntries;
    }

    public static List<CompoundTag> getPCPartyEntries() {
        return pcPartyEntries;
    }

    public static int getPCDataVersion() {
        return pcDataVersion;
    }

    // ===== Pokédex-Ansicht (Cobblemons PokedexGUI, clientPokedexData-Singleton temporär getauscht) =====
    private static String pokedexTargetName = "";
    private static CompoundTag pokedexTag = null;
    private static int pokedexDataVersion = 0;

    public static void setPokedexData(String targetName, CompoundTag tag) {
        pokedexTargetName = targetName;
        pokedexTag = tag;
        pokedexDataVersion++;
    }

    public static String getPokedexTargetName() {
        return pokedexTargetName;
    }

    public static CompoundTag getPokedexTag() {
        return pokedexTag;
    }

    public static int getPokedexDataVersion() {
        return pokedexDataVersion;
    }

    // ===== Living-Dex-Ansicht (dieselbe PokedexGUI wie oben + Blatt-Icon-Overlays anhand der
    // aktuell besessenen Spezies des Zielspielers, siehe ClientLivingDexHelper-Swap in
    // CompanionScreen.buildProfessorLivingDexScreen()) =====
    private static String livingDexTargetName = "";
    private static CompoundTag livingDexPokedexTag = null;
    private static List<String> livingDexSpecies = new ArrayList<>();
    private static int livingDexDataVersion = 0;

    public static void setLivingDexData(String targetName, CompoundTag tag, List<String> species) {
        livingDexTargetName = targetName;
        livingDexPokedexTag = tag;
        livingDexSpecies = species;
        livingDexDataVersion++;
    }

    public static String getLivingDexTargetName() {
        return livingDexTargetName;
    }

    public static CompoundTag getLivingDexPokedexTag() {
        return livingDexPokedexTag;
    }

    public static List<String> getLivingDexSpecies() {
        return livingDexSpecies;
    }

    public static int getLivingDexDataVersion() {
        return livingDexDataVersion;
    }

    // ===== Entwickeln-Auswahl (Optionen kommen vom Server, siehe AdminEvolveOptionsRequestPacket
    // - das clientseitig via loadFromNBT() rekonstruierte Pokemon liefert bei getEvolutions()
    // unzuverlässig leere Ergebnisse, deshalb wird die ECHTE, serverseitige Pokemon-Instanz gefragt) =====
    private static List<String> evolveOptions = new ArrayList<>();
    private static int evolveOptionsVersion = 0;

    /** Rohzeilen im Format "toSpeciesId|toAspects". */
    public static void setEvolveOptions(List<String> options) {
        evolveOptions = options;
        evolveOptionsVersion++;
    }

    public static List<String> getEvolveOptions() {
        return evolveOptions;
    }

    public static int getEvolveOptionsVersion() {
        return evolveOptionsVersion;
    }

    // ===== Zurückentwickeln-Auswahl (Vorentwicklung kommt vom Server, siehe AdminDeEvolveOptionsRequestPacket) =====
    private static String deEvolveOption = "";
    private static int deEvolveOptionVersion = 0;

    public static void setDeEvolveOption(String toSpeciesId) {
        deEvolveOption = toSpeciesId;
        deEvolveOptionVersion++;
    }

    public static String getDeEvolveOption() {
        return deEvolveOption;
    }

    public static int getDeEvolveOptionVersion() {
        return deEvolveOptionVersion;
    }

    // ===== RCT-Trainerpfad-Liste (nur wenn ModAvailability.isRctAvailable(), siehe
    // ProfessorRctListRequestPacket/-ResponsePacket) =====
    public static class RctSeriesItem {
        public final String id;
        public final String title;
        public final boolean completed;

        public RctSeriesItem(String id, String title, boolean completed) {
            this.id = id;
            this.title = title;
            this.completed = completed;
        }
    }

    private static UUID rctTargetUuid = null;
    private static boolean rctTargetOnline = true;
    private static List<RctSeriesItem> rctSeries = new ArrayList<>();
    private static int rctDataVersion = 0;

    public static void setRctData(UUID targetUuid, boolean targetOnline, List<String> raw) {
        rctTargetUuid = targetUuid;
        rctTargetOnline = targetOnline;
        List<RctSeriesItem> parsed = new ArrayList<>();
        for (String line : raw) {
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) continue;
            parsed.add(new RctSeriesItem(parts[0], parts[1], Boolean.parseBoolean(parts[2])));
        }
        rctSeries = parsed;
        rctDataVersion++;
    }

    public static UUID getRctTargetUuid() {
        return rctTargetUuid;
    }

    public static boolean isRctTargetOnline() {
        return rctTargetOnline;
    }

    public static List<RctSeriesItem> getRctSeries() {
        return rctSeries;
    }

    public static int getRctDataVersion() {
        return rctDataVersion;
    }
}
