package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientServerRulesHelper;
import com.cobblecompanion.data.ServerRulesManager;
import com.cobblecompanion.integrations.ModAvailability;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: aktuelle globale Server-Regeln + ob der empfangende Spieler sie
 * ändern darf (canEdit = OP), sowie welche optionalen Fremd-Mod-Integrationen der Server
 * unterstützt. Gesendet bei Login und nach jeder Regeländerung.
 */
public record ServerRulesSyncPacket(boolean forbidGifting, boolean allowTeleportToFriends, boolean canEdit,
        boolean cobbleDollarsAvailable, boolean createAvailable, boolean mobilePackagesAvailable,
        boolean rctAvailable, boolean earnFromNPC, boolean earnFromWildPokemon, double incomeMultiplier,
        boolean onlineRewardEnabled, int onlineRewardIntervalMinutes, long onlineRewardAmount)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerRulesSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "server_rules_sync"));

    // StreamCodec.composite unterstützt nur bis zu 6 Felder (Function6) - bei so vielen Feldern
    // wird hier deshalb manuell kodiert statt composite() zu verketten.
    public static final StreamCodec<ByteBuf, ServerRulesSyncPacket> CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeBoolean(packet.forbidGifting());
            buf.writeBoolean(packet.allowTeleportToFriends());
            buf.writeBoolean(packet.canEdit());
            buf.writeBoolean(packet.cobbleDollarsAvailable());
            buf.writeBoolean(packet.createAvailable());
            buf.writeBoolean(packet.mobilePackagesAvailable());
            buf.writeBoolean(packet.rctAvailable());
            buf.writeBoolean(packet.earnFromNPC());
            buf.writeBoolean(packet.earnFromWildPokemon());
            buf.writeDouble(packet.incomeMultiplier());
            buf.writeBoolean(packet.onlineRewardEnabled());
            buf.writeInt(packet.onlineRewardIntervalMinutes());
            buf.writeLong(packet.onlineRewardAmount());
        },
        buf -> new ServerRulesSyncPacket(
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readDouble(),
            buf.readBoolean(), buf.readInt(), buf.readLong()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server-seitig: schickt die aktuellen Regeln an einen Spieler (canEdit = OP-Level 2). */
    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ServerRulesSyncPacket(
            ServerRulesManager.isForbidGifting(),
            ServerRulesManager.isAllowTeleportToFriends(),
            com.cobblecompanion.data.AdminPermissionManager.isAdminOp(player.getUUID()),
            ModAvailability.isCobbleDollarsAvailable(),
            ModAvailability.isCreateAvailable(),
            ModAvailability.isMobilePackagesAvailable(),
            ModAvailability.isRctAvailable(),
            ServerRulesManager.isEarnCobbleDollarsFromNPC(),
            ServerRulesManager.isEarnCobbleDollarsFromWildPokemon(),
            ServerRulesManager.getCobbleDollarsIncomeMultiplier(),
            com.cobblecompanion.data.OnlineRewardManager.isEnabled(),
            com.cobblecompanion.data.OnlineRewardManager.getIntervalMinutes(),
            com.cobblecompanion.data.OnlineRewardManager.getAmount()));
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(ServerRulesSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientServerRulesHelper.apply(
            packet.forbidGifting(), packet.allowTeleportToFriends(), packet.canEdit(),
            packet.cobbleDollarsAvailable(), packet.createAvailable(),
            packet.mobilePackagesAvailable(), packet.rctAvailable(),
            packet.earnFromNPC(), packet.earnFromWildPokemon(), packet.incomeMultiplier(),
            packet.onlineRewardEnabled(), packet.onlineRewardIntervalMinutes(), packet.onlineRewardAmount()));
    }
}
