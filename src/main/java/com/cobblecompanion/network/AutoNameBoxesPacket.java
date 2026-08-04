package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.LivingDexPlusLayoutHelper;
import com.cobblecompanion.data.LivingDexPlusRegistry;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.net.messages.client.storage.pc.RenamePCBoxPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client -> Server: "Boxen automatisch benennen"-Button (Settings-Tab, Kategorie PC). Benennt die
 * eigenen PC-Boxen des sendenden Spielers nach dem Sortierhilfe-Schema (siehe PCSortHelper).
 *
 * WICHTIG (Root-Cause des ursprünglichen Bugs): Cobblemons eigenes RequestRenamePCBoxPacket wurde
 * hier bewusst NICHT wiederverwendet - dessen Server-Handler holt die PCStore über
 * PCLinkManager.getPC(player), was NULL liefert (und den Client sogar zwangsweise per
 * ClosePCPacket "schließt"), sobald der Spieler sein PC-Fenster gerade NICHT offen hat. Der
 * "Automatisch benennen"-Button im Settings-Tab läuft aber typischerweise OHNE offenes PC-Fenster.
 * Deshalb hier wie bei den restlichen AdminOp-Packets die UUID-basierte Storage-API
 * (PlayerDataHelper.getPCStoreByUuid) direkt genutzt - funktioniert unabhängig davon, ob das PC
 * gerade "verlinkt" ist. Nach der Umbenennung wird Cobblemons eigenes (client-gebundenes)
 * RenamePCBoxPacket manuell nachgeschickt, damit der lokale ClientPC-Cache (z.B. für ein evtl.
 * gerade geöffnetes PC-Fenster oder den ToDo-Tab) die neuen Namen ohne Neustart übernimmt.
 *
 * pcSortMode/ldpCategories: im Living-Dex+-Modus (2) werden Boxnamen aus dem
 * LivingDexPlusLayoutHelper-Katalog abgeleitet (Kategorie-Kürzel + Nummernbereich), siehe
 * ldpBoxName().
 *
 * BUGFIX (Nutzer-Report: Pokédex-Modus beschriftet wie Living Dex, Living-Dex-Modus zählt bis
 * 1140 statt bei 1025 zu stoppen): Pokédex-Modus (0) und Living-Dex-Modus (1) nutzten bisher
 * GAR KEINE echten Arten-/Familien-Daten, sondern reine Box-Index-Arithmetik ("#" + idx*30+1 +
 * "-" + idx*30+30) für JEDE existierende PC-Box, unabhängig davon, ob dort überhaupt noch Inhalt
 * hin gehört - Pokédex-Modus bekam dadurch nie die Familien-Kollabierung (siehe
 * EvolutionFamilyHelper) und beide Modi liefen einfach bis zur letzten konfigurierten Box durch.
 * legacyBoxName()/buildLegacyFlatList() nutzen jetzt denselben LivingDexPlusRegistry-Katalog wie
 * Living Dex+ (Kategorie 0 bzw. 1), zeigen echte (bei Pokédex: Familien-Anker-)Dex-Nummern und
 * brechen wie ldpBoxName() sauber ab, sobald der Katalog erschöpft ist.
 */
public record AutoNameBoxesPacket(int startBox, int pcSortMode, List<Integer> ldpCategories) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AutoNameBoxesPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "auto_name_boxes"));

    public static final StreamCodec<ByteBuf, AutoNameBoxesPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, AutoNameBoxesPacket::startBox,
        ByteBufCodecs.VAR_INT, AutoNameBoxesPacket::pcSortMode,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), AutoNameBoxesPacket::ldpCategories,
        AutoNameBoxesPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AutoNameBoxesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            PCStore pc = PlayerDataHelper.getPCStoreByUuid(player.getUUID(), server.registryAccess());
            if (pc == null) return;

            int boxCount = pc.getBoxes().size();
            if (boxCount < 2) return;
            int startBox = Math.min(Math.max(1, packet.startBox()), boxCount);

            boolean livingDexPlus = packet.pcSortMode() == 2;
            List<LivingDexPlusRegistry.Entry> flat = livingDexPlus
                ? LivingDexPlusLayoutHelper.buildFlatList(packet.ldpCategories())
                : buildLegacyFlatList(packet.pcSortMode() == 0 ? 0 : 1);
            // Nutzer-Vorgabe (deutsche Spieler sollen "Karpador" statt "Magikarp" sehen): PC-
            // Boxnamen sind reiner Klartext (keine Komponente/Übersetzungsschlüssel, wird nie pro
            // Betrachter neu aufgelöst) - deshalb hier EINMALIG anhand der Client-Sprache DES
            // anfragenden Spielers aufgelöst, nicht erst beim Anzeigen.
            boolean german = isGermanClient(player);

            // Defensive Absicherung: eine einzelne fehlerhafte Box darf nicht mehr den kompletten
            // Durchlauf abbrechen (siehe positionWithinCategory()-Bugfix oben, Root Cause des
            // "Beschriftung bricht mitten drin ab"-Reports) - jede Box einzeln try/catch, damit
            // ein unerwarteter Fehler bestenfalls EINE Box überspringt statt alle folgenden.
            for (int box = 1; box <= boxCount; box++) {
                try {
                    String name;
                    if (box == boxCount) {
                        name = "Doppelte";
                    } else if (startBox > 1 && box == 1) {
                        name = "Neu";
                    } else if (box >= startBox) {
                        int idx = box - startBox;
                        name = livingDexPlus ? ldpBoxName(flat, idx, german) : legacyBoxName(flat, idx);
                    } else {
                        continue; // Boxen zwischen "Neu" und der Startbox bleiben unangetastet.
                    }
                    PCBox pcBox = pc.getBoxes().get(box - 1);
                    if (name == null) {
                        // Nutzer-Wunsch: Box wird durch die aktuelle Auswahl nicht mehr gebraucht
                        // (Katalog an dieser Stelle erschöpft) - Name NUR zurücksetzen (leer, damit
                        // Cobblemons Original-Name wieder erscheint), wenn der aktuelle Name selbst
                        // von einem früheren Auto-Beschriften-Lauf stammt. Ein vom Spieler von Hand
                        // vergebener Name bleibt unangetastet.
                        if (isAutoGeneratedName(pcBox.getName())) {
                            pcBox.setName("");
                            new RenamePCBoxPacket(pc.getUuid(), box - 1, "").sendToPlayer(player);
                        }
                        continue;
                    }
                    pcBox.setName(name);
                    new RenamePCBoxPacket(pc.getUuid(), box - 1, name).sendToPlayer(player);
                } catch (Exception e) {
                    CobbleCompanion.LOGGER.error("[CC] Fehler beim Benennen von PC-Box " + box, e);
                }
            }
        });
    }

    /**
     * Boxname für eine Living-Dex+-Box (0-basierter Offset ab der Startbox). Nutzer-Vorgabe: für
     * Regionalformen (Kategorie 3/4) und die Kosmetisch-Unterkategorien-Whitelist (Kategorie 5/6,
     * siehe LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST) gibt es KEIN Kürzel und KEINE
     * "1-X"-Nummerierung mehr, nur noch den (bei Kosmetisch: sprachabhängigen) Klarnamen - da es
     * für diese Gruppen ohnehin praktisch immer nur eine einzige Box gibt. Alle anderen Kategorien
     * (Pokédex/Living Dex/Shiny-Varianten sowie die gemeinsame "Kosmetische Formen"-Sammelbox für
     * alle übrigen Arten) behalten das bisherige Kürzel+Nummernbereich-Schema, da dort viele
     * verschiedene Arten in einer langen, unnummeriert unübersichtlichen Kette lägen.
     */
    private static String ldpBoxName(List<LivingDexPlusRegistry.Entry> flat, int boxIdx, boolean german) {
        int start = boxIdx * 30;
        int rawEnd = Math.min(start + 30, flat.size()) - 1;
        // null = Katalog an dieser Stelle erschöpft, Box wird nicht (mehr) gebraucht (siehe
        // Aufrufer: setzt den Namen nur zurück, statt einen Platzhalter-Text zu vergeben).
        if (start >= flat.size() || rawEnd < start || flat.get(start) == null) return null;

        LivingDexPlusRegistry.Entry startEntry = flat.get(start);
        int startCat = startEntry.categoryId();

        if (startCat == 3 || startCat == 4) {
            String name = startCat == 4 ? startEntry.formName() + " Shiny" : startEntry.formName();
            return withGroupNumberIfNeeded(flat, boxIdx, name, plainGroupKey(startEntry));
        }
        if ((startCat == 5 || startCat == 6) && LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST.contains(startEntry.speciesName().toLowerCase())) {
            String base = LivingDexPlusRegistry.cosmeticSpeciesDisplayName(startEntry.speciesName(), german);
            String name = startCat == 6 ? base + " Shiny" : base;
            return withGroupNumberIfNeeded(flat, boxIdx, name, plainGroupKey(startEntry));
        }

        // Box-Trennungs-Padding (siehe LivingDexPlusLayoutHelper) füllt das Boxende ggf. mit
        // null - für die Anzeige den letzten ECHTEN Eintrag in dieser Box suchen.
        int end = rawEnd;
        while (end > start && flat.get(end) == null) end--;

        String startGroup = boxGroupKey(startEntry);
        String endGroup = boxGroupKey(flat.get(end));
        String startAbbr = boxGroupAbbreviation(startEntry);

        // Positionsnummer INNERHALB der Kategorie (1-basiert) für Start/Ende dieser Box.
        int startPosInCat = positionWithinCategory(flat, start);
        if (startGroup.equals(endGroup)) {
            int endPosInCat = positionWithinCategory(flat, end);
            return startAbbr + " " + startPosInCat + "-" + endPosInCat;
        }
        String endAbbr = boxGroupAbbreviation(flat.get(end));
        int endPosInCat = positionWithinCategory(flat, end);
        return startAbbr + " " + startPosInCat + "+ / " + endAbbr + " 1-" + endPosInCat;
    }

    /** Gruppenschlüssel für die (weiterhin nummerierten) Basis-Kategorien 0/1/2/7 sowie die generische Kosmetisch-Sammelbox. */
    private static String boxGroupKey(LivingDexPlusRegistry.Entry e) {
        if (e.categoryId() == 5 || e.categoryId() == 6) return e.categoryId() + ":" + e.speciesName().toLowerCase();
        return String.valueOf(e.categoryId());
    }

    /**
     * Gruppenschlüssel für Regionen (Kategorie 3/4, Formname=Region) bzw. Kosmetisch-Whitelist
     * (Kategorie 5/6) - Karpador+Garados teilen sich denselben Schlüssel (siehe
     * LivingDexPlusRegistry.MAGIKARP_GYARADOS_BOX_GROUP), da ihre Boxen ja zusammengelegt sind.
     */
    private static String plainGroupKey(LivingDexPlusRegistry.Entry e) {
        if (e.categoryId() == 3 || e.categoryId() == 4) return e.categoryId() + ":" + e.formName().toLowerCase();
        String species = e.speciesName().toLowerCase();
        if (LivingDexPlusRegistry.MAGIKARP_GYARADOS_BOX_GROUP.contains(species)) species = "magikarp_gyarados_group";
        return e.categoryId() + ":" + species;
    }

    /**
     * Nutzer-Vorgabe: braucht eine Regionalformen-/Kosmetisch-Whitelist-Gruppe MEHR als eine Box
     * (z.B. Karpador/Garados mit 64 Formen oder Hokumil mit 63 Kombinationen -> je 3 Boxen), werden
     * die Boxen fortlaufend nummeriert ("Hokumil 1", "Hokumil 2", "Hokumil 3") - bei nur einer Box
     * (die meisten Regionen/Arten) bleibt es beim reinen Namen ohne Zahl.
     */
    private static String withGroupNumberIfNeeded(List<LivingDexPlusRegistry.Entry> flat, int boxIdx, String name, String groupKey) {
        int pos = 1;
        for (int i = boxIdx - 1; i >= 0; i--) {
            int s = i * 30;
            if (s >= flat.size() || flat.get(s) == null || !plainGroupKey(flat.get(s)).equals(groupKey)) break;
            pos++;
        }
        int nextStart = (boxIdx + 1) * 30;
        boolean hasNext = nextStart < flat.size() && flat.get(nextStart) != null && plainGroupKey(flat.get(nextStart)).equals(groupKey);
        if (pos == 1 && !hasNext) return name;
        return name + " " + pos;
    }

    private static String boxGroupAbbreviation(LivingDexPlusRegistry.Entry e) {
        return LivingDexPlusLayoutHelper.categoryAbbreviation(e.categoryId());
    }

    private static int positionWithinCategory(List<LivingDexPlusRegistry.Entry> flat, int index) {
        String group = boxGroupKey(flat.get(index));
        int pos = 1;
        // BUGFIX (Nutzer-Report: Beschriftung "bricht ab", sobald eine Kategorie zu Ende ist):
        // direkt VOR dem ersten Eintrag jeder neuen Kategorie liegt jetzt das Box-Trennungs-
        // Padding (null) der vorherigen Kategorie - flat.get(i) auf null pruefen, BEVOR
        // boxGroupKey() aufgerufen wird, sonst NullPointerException, die den kompletten
        // Umbenennungs-Durchlauf (eine einzige enqueueWork-Lambda) mitten drin abbricht und alle
        // nachfolgenden Boxen unbenannt laesst.
        for (int i = index - 1; i >= 0 && flat.get(i) != null && boxGroupKey(flat.get(i)).equals(group); i--) pos++;
        return pos;
    }

    /** Client-Sprache DES anfragenden Spielers - siehe ldpBoxName()-Doc-Kommentar. */
    private static boolean isGermanClient(ServerPlayer player) {
        try {
            String lang = player.clientInformation().language();
            return lang != null && lang.toLowerCase().startsWith("de");
        } catch (Exception e) {
            return false;
        }
    }

    /** Sortierte Einzel-Kategorie-Liste (0=Pokédex/Familien-Anker, 1=Living Dex) aus dem geteilten Katalog. */
    private static List<LivingDexPlusRegistry.Entry> buildLegacyFlatList(int categoryId) {
        List<LivingDexPlusRegistry.Entry> list = new ArrayList<>();
        for (LivingDexPlusRegistry.Entry e : LivingDexPlusRegistry.getAll()) {
            if (e.categoryId() == categoryId) list.add(e);
        }
        list.sort(Comparator.comparingInt(LivingDexPlusRegistry.Entry::dexNumber));
        return list;
    }

    /**
     * Boxname für Pokédex-/Living-Dex-Modus (0-basierter Offset ab Startbox): echte (bei
     * Pokédex: familien-kollabierte) Dex-Nummern-Spanne statt reiner Box-Index-Arithmetik -
     * bricht wie ldpBoxName() ab, sobald der Katalog erschöpft ist.
     */
    private static String legacyBoxName(List<LivingDexPlusRegistry.Entry> flat, int boxIdx) {
        int start = boxIdx * 30;
        int end = Math.min(start + 30, flat.size()) - 1;
        // null = Katalog an dieser Stelle erschöpft, Box wird nicht (mehr) gebraucht.
        if (start >= flat.size() || end < start) return null;
        return "#" + flat.get(start).dexNumber() + "-" + flat.get(end).dexNumber();
    }

    // Erkennt Box-Namen, die von EINEM früheren Auto-Beschriften-Lauf stammen (egal welcher
    // Modus) - deckt alle bisher/aktuell erzeugbaren Formate ab: legacyBoxName ("#123-456"),
    // ldpBoxName ("PD 1-30" bzw. bei Kategorie-Übergang "PD 15+ / LD 1-15"), sowie "Neu"/
    // "Doppelte". BUGFIX (Nutzer-Report: alte "LD+ 34"-Namen aus einem VORHERIGEN Lauf, vor dem
    // null-Rückgabe-Fix oben, wurden nie zurückgesetzt): das alte Fallback-Format hatte ein
    // Pluszeichen ("LD+ N"), das hier ursprünglich fehlte - "\\+?" ergänzt. Nur bei einem Treffer
    // wird eine nicht mehr benötigte Box zurückgesetzt (leerer Name -> Cobblemons Original-Name
    // kommt wieder zum Vorschein) - ein vom Spieler von Hand vergebener Name matcht dieses Muster
    // praktisch nie und bleibt unangetastet.
    private static final java.util.regex.Pattern AUTO_NAME_PATTERN = java.util.regex.Pattern.compile(
        "^(Neu|Doppelte|#\\d+-\\d+|(PD|LD|SH-LD|REG(SH)?(-[A-Z]{1,3})?|COS(SH)?(-[A-Z]{1,3})?|SH-PD)\\+? \\d+(-\\d+)?"
            + "(\\+ / (PD|LD|SH-LD|REG(SH)?(-[A-Z]{1,3})?|COS(SH)?(-[A-Z]{1,3})?|SH-PD) \\d+-\\d+)?)$");

    /**
     * Die Klarnamen-Boxtitel aus ldpBoxName() für Regionen/Kosmetisch-Whitelist (siehe dort,
     * inkl. der "Name N"-Nummerierung bei mehrboxigen Gruppen wie Hokumil/Karpador-Garados) -
     * werden hier separat als Set aufgebaut, da ein starres Regex-Muster für "irgendein Region-
     * oder Artname, optional +Shiny, optional nummeriert, in DE oder EN" unübersichtlich wäre.
     * Obergrenze 20 Boxen pro Gruppe ist großzügig bemessen (größte Gruppe aktuell: Hokumil mit 3).
     */
    private static final int AUTO_NAME_MAX_GROUP_BOXES = 20;
    private static final java.util.Set<String> AUTO_NAME_PLAIN_NAMES = buildAutoNamePlainNames();

    private static java.util.Set<String> buildAutoNamePlainNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        List<String> baseNames = new ArrayList<>();
        for (String region : LivingDexPlusRegistry.REGIONS) {
            baseNames.add(region);
            baseNames.add(region + " Shiny");
        }
        for (String species : LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST) {
            for (boolean german : new boolean[]{true, false}) {
                String name = LivingDexPlusRegistry.cosmeticSpeciesDisplayName(species, german);
                baseNames.add(name);
                baseNames.add(name + " Shiny");
            }
        }
        for (String base : baseNames) {
            names.add(base);
            for (int i = 1; i <= AUTO_NAME_MAX_GROUP_BOXES; i++) names.add(base + " " + i);
        }
        return names;
    }

    private static boolean isAutoGeneratedName(String name) {
        if (name == null) return false;
        return AUTO_NAME_PATTERN.matcher(name).matches() || AUTO_NAME_PLAIN_NAMES.contains(name);
    }
}
