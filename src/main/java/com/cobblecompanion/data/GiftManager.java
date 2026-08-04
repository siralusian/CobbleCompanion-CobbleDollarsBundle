package com.cobblecompanion.data;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pokemon-Verschenken: rein serverseitig, kein Gson-Persistenz nötig (siehe createOffer/accept -
 * ein offenes Angebot ist bewusst flüchtig; geht es beim Serverneustart verloren, ist einfach
 * nichts passiert - das Pokemon blieb beim Absender). Beide Spieler müssen zum Zeitpunkt des
 * Angebots UND zum Zeitpunkt des Annehmens online sein (keine Offline-Storage-Mutation, um
 * Datenverlust-Risiken zu vermeiden).
 */
public class GiftManager {

    /**
     * speciesId/level/aspects/nickname/caughtBallId werden zum Zeitpunkt des Angebots
     * eingefroren (nicht live vom Pokemon-Objekt gelesen), damit das GUI beim Empfänger auch
     * dann noch etwas Sinnvolles anzeigen kann, wenn der Sender inzwischen offline ist -
     * caughtBallId kann null sein (kein Fangball bekannt, z.B. geschlüpft/per Command erhalten).
     */
    public record PendingGift(UUID fromUuid, String fromName, UUID pokemonUuid, ResourceLocation speciesId,
                               int level, String aspects, String nickname, ResourceLocation caughtBallId,
                               String natureId, String abilityId) {}

    public enum GiftResult {
        OK, RULE_FORBIDDEN, SELF, NOT_FRIENDS, TARGET_OFFLINE, SENDER_OFFLINE,
        POKEMON_NOT_FOUND, LAST_POKEMON, RECEIVER_FULL, NO_PENDING_GIFT
    }

    private static final Map<UUID, List<PendingGift>> pendingByReceiver = new HashMap<>();

    /** Schritt 1 (Absender): Angebot erstellen. Prüft schon hier alles außer der finalen Annahme. */
    public static GiftResult createOffer(ServerPlayer from, UUID pokemonUuid, UUID toUuid) {
        if (ServerRulesManager.isForbidGifting()) return GiftResult.RULE_FORBIDDEN;
        if (from.getUUID().equals(toUuid)) return GiftResult.SELF;
        if (!FriendsManager.areFriends(from.getUUID(), toUuid)) return GiftResult.NOT_FRIENDS;

        MinecraftServer server = from.getServer();
        if (server == null || server.getPlayerList().getPlayer(toUuid) == null) return GiftResult.TARGET_OFFLINE;

        Pokemon pokemon = findPokemon(from, pokemonUuid);
        if (pokemon == null) return GiftResult.POKEMON_NOT_FOUND;
        if (PlayerDataHelper.getAllPokemon(from).size() <= 1) return GiftResult.LAST_POKEMON;

        String nickname = pokemon.getNickname() != null ? pokemon.getNickname().getString() : "";
        ResourceLocation caughtBallId = pokemon.getCaughtBall() != null ? pokemon.getCaughtBall().getName() : null;
        // Rohe IDs (nicht der u.U. server-lokalisierte getDisplayName()) - der Client übersetzt
        // "cobblemon.nature.<id>"/"cobblemon.ability.<id>" selbst über I18n mit seiner eigenen Sprache.
        String natureId = "";
        try {
            natureId = pokemon.getNature().getName().getPath();
        } catch (Exception ignored) {}
        String abilityId = "";
        try {
            abilityId = pokemon.getAbility().getName();
        } catch (Exception ignored) {}
        pendingByReceiver.computeIfAbsent(toUuid, k -> new ArrayList<>())
            .add(new PendingGift(from.getUUID(), from.getName().getString(), pokemonUuid,
                pokemon.getSpecies().getResourceIdentifier(), pokemon.getLevel(),
                String.join(",", pokemon.getAspects()), nickname, caughtBallId, natureId, abilityId));
        return GiftResult.OK;
    }

    /** Schritt 2b (Empfänger): Angebot ablehnen - kein Transfer, Pokemon bleibt beim Absender. */
    public static void decline(UUID receiverUuid, PendingGift gift) {
        removePending(receiverUuid, gift);
    }

    public static List<PendingGift> getPendingFor(UUID receiverUuid) {
        return pendingByReceiver.getOrDefault(receiverUuid, Collections.emptyList());
    }

    public static PendingGift findPendingFromName(UUID receiverUuid, String fromName) {
        for (PendingGift g : getPendingFor(receiverUuid)) {
            if (g.fromName().equalsIgnoreCase(fromName)) return g;
        }
        return null;
    }

    public static PendingGift findPendingFromUuid(UUID receiverUuid, UUID fromUuid) {
        for (PendingGift g : getPendingFor(receiverUuid)) {
            if (g.fromUuid().equals(fromUuid)) return g;
        }
        return null;
    }

    public static void removePending(UUID receiverUuid, PendingGift gift) {
        List<PendingGift> list = pendingByReceiver.get(receiverUuid);
        if (list != null) list.remove(gift);
    }

    /** Schritt 2 (Empfänger): Angebot annehmen - führt den eigentlichen Transfer aus. */
    public static GiftResult accept(ServerPlayer receiver, PendingGift gift) {
        removePending(receiver.getUUID(), gift);
        if (ServerRulesManager.isForbidGifting()) return GiftResult.RULE_FORBIDDEN;

        MinecraftServer server = receiver.getServer();
        ServerPlayer from = server != null ? server.getPlayerList().getPlayer(gift.fromUuid()) : null;
        if (from == null) return GiftResult.SENDER_OFFLINE;

        Pokemon pokemon = findPokemon(from, gift.pokemonUuid());
        if (pokemon == null) return GiftResult.POKEMON_NOT_FOUND;

        PlayerPartyStore fromParty = Cobblemon.INSTANCE.getStorage().getParty(from);
        PCStore fromPC = Cobblemon.INSTANCE.getStorage().getPC(from);
        boolean removed = fromParty.remove(pokemon);
        if (!removed) removed = fromPC.remove(pokemon);
        if (!removed) return GiftResult.POKEMON_NOT_FOUND;

        PlayerPartyStore toParty = Cobblemon.INSTANCE.getStorage().getParty(receiver);
        PCStore toPC = Cobblemon.INSTANCE.getStorage().getPC(receiver);
        PartyPosition partyPos = toParty.getFirstAvailablePosition();
        if (partyPos != null) {
            toParty.set(partyPos, pokemon);
            return GiftResult.OK;
        }
        PCPosition pcPos = toPC.getFirstAvailablePosition();
        if (pcPos != null) {
            toPC.set(pcPos, pokemon);
            return GiftResult.OK;
        }

        // Empfänger hat nirgends Platz - Pokemon zurück zum Absender statt es zu verlieren.
        PartyPosition backPos = fromParty.getFirstAvailablePosition();
        if (backPos != null) fromParty.set(backPos, pokemon);
        else {
            PCPosition backPcPos = fromPC.getFirstAvailablePosition();
            if (backPcPos != null) fromPC.set(backPcPos, pokemon);
        }
        return GiftResult.RECEIVER_FULL;
    }

    private static Pokemon findPokemon(ServerPlayer player, UUID pokemonUuid) {
        for (Pokemon p : PlayerDataHelper.getAllPokemon(player)) {
            if (p.getUuid().equals(pokemonUuid)) return p;
        }
        return null;
    }
}
