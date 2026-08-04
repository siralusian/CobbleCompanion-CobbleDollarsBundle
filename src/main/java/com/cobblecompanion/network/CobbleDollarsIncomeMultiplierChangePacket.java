package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.ServerRulesManager;
import com.cobblecompanion.integrations.ModAvailability;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: ein AdminOp ändert den Cobbledollars-Einnahmen-Multiplikator (Settings > Server).
 * Passt nicht in ServerRuleChangePacket (int-Regel + boolean), da hier ein double nötig ist -
 * eigenes Packet nach demselben Broadcast-an-alle-Muster.
 */
public record CobbleDollarsIncomeMultiplierChangePacket(double multiplier) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CobbleDollarsIncomeMultiplierChangePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "cobbledollars_income_multiplier_change"));

    public static final StreamCodec<ByteBuf, CobbleDollarsIncomeMultiplierChangePacket> CODEC =
        ByteBufCodecs.DOUBLE.map(CobbleDollarsIncomeMultiplierChangePacket::new,
            CobbleDollarsIncomeMultiplierChangePacket::multiplier);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CobbleDollarsIncomeMultiplierChangePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
            if (!ModAvailability.isCobbleDollarsAvailable()) return;
            ServerRulesManager.setCobbleDollarsIncomeMultiplier(packet.multiplier());
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                ServerRulesSyncPacket.sendTo(p);
            }
        });
    }
}
