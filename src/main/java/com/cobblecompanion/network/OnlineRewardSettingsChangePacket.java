package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.OnlineRewardManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: ein AdminOp ändert die "Online-Belohnung"-Einstellungen (Settings > Server) -
 * schickt immer den kompletten gewünschten Zustand, gleiches Muster wie
 * CreateStockTickerPricesUpdatePacket.
 */
public record OnlineRewardSettingsChangePacket(boolean enabled, int intervalMinutes, long amount)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OnlineRewardSettingsChangePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "online_reward_settings_change"));

    public static final StreamCodec<ByteBuf, OnlineRewardSettingsChangePacket> CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeBoolean(packet.enabled());
            buf.writeInt(packet.intervalMinutes());
            buf.writeLong(packet.amount());
        },
        buf -> new OnlineRewardSettingsChangePacket(buf.readBoolean(), buf.readInt(), buf.readLong()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OnlineRewardSettingsChangePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
            OnlineRewardManager.setSettings(packet.enabled(), packet.intervalMinutes(), packet.amount());
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                ServerRulesSyncPacket.sendTo(p);
            }
        });
    }
}
