package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientCreativeDimensionRulesHelper;
import com.cobblecompanion.data.CreativeTimeManager;
import com.cobblecompanion.data.FriendsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client: aktueller Stand aller pro-Spieler Creative-Kauf-Berechtigungsregeln (siehe
 * CreativeTimeManager.playerPurchaseRules, kein Dimensionsbezug mehr) für die Settings-Anzeige
 * (Gamemodes-Kategorie, Liste am Ende, Nutzer-Vorgabe) - nur an AdminOp gesendet (Login + nach
 * jeder Änderung über den Befehl, siehe CobbleCompanionCommands). entries: bereits fertig
 * formatierte Anzeige-Zeilen ("Spielername (MODUS)"), rein lesend (Bearbeitung weiterhin nur per
 * Befehl).
 */
public record CreativeDimensionRulesSyncPacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreativeDimensionRulesSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "creative_dimension_rules_sync"));

    public static final StreamCodec<ByteBuf, CreativeDimensionRulesSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), CreativeDimensionRulesSyncPacket::entries,
        CreativeDimensionRulesSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        List<String> entries = new ArrayList<>();
        for (String uuidString : CreativeTimeManager.getAllPlayerPurchaseRuleUuids()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String name = FriendsManager.getKnownName(uuid);
            if (name == null) name = uuidString;
            entries.add(name + " (" + CreativeTimeManager.getPurchaseRule(uuid) + ")");
        }
        PacketDistributor.sendToPlayer(player, new CreativeDimensionRulesSyncPacket(entries));
    }

    public static void handle(CreativeDimensionRulesSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCreativeDimensionRulesHelper.setEntries(packet.entries()));
    }
}
