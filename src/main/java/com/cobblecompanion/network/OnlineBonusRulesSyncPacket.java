package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientOnlineBonusRulesHelper;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.data.OnlineRewardManager;
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
import java.util.Map;
import java.util.UUID;

/**
 * Server -> Client: aktueller Stand aller pro-Spieler Online-Belohnung-Boni (siehe
 * OnlineRewardManager.setBonusAmount) für die Settings-Anzeige (Server-Kategorie, unter
 * "Online-Belohnung: Betrag", Nutzer-Vorgabe) - nur an AdminOp gesendet, rein lesend (Bearbeitung
 * weiterhin nur per Befehl /companion admin onlinebonus).
 */
public record OnlineBonusRulesSyncPacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OnlineBonusRulesSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "online_bonus_rules_sync"));

    public static final StreamCodec<ByteBuf, OnlineBonusRulesSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OnlineBonusRulesSyncPacket::entries,
        OnlineBonusRulesSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Long> bonus : OnlineRewardManager.getAllBonuses().entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(bonus.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            String name = FriendsManager.getKnownName(uuid);
            if (name == null) name = bonus.getKey();
            String sign = bonus.getValue() >= 0 ? "+" : "";
            entries.add(name + ": " + sign + bonus.getValue());
        }
        PacketDistributor.sendToPlayer(player, new OnlineBonusRulesSyncPacket(entries));
    }

    public static void handle(OnlineBonusRulesSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientOnlineBonusRulesHelper.setEntries(packet.entries()));
    }
}
