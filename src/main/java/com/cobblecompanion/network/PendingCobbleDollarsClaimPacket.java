package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.PendingCobbleDollarsManager;
import com.cobblecompanion.data.TransactionLogManager;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Spieler klickt im Home-Tab auf "Abholen" für seinen ausstehenden Cobbledollars-
 * Betrag (siehe PendingCobbleDollarsManager.claim). Kein Payload nötig, der Server kennt den
 * ausstehenden Betrag selbst.
 */
public record PendingCobbleDollarsClaimPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PendingCobbleDollarsClaimPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "pending_cobbledollars_claim"));

    public static final StreamCodec<ByteBuf, PendingCobbleDollarsClaimPacket> CODEC =
        StreamCodec.unit(new PendingCobbleDollarsClaimPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PendingCobbleDollarsClaimPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            long amount = PendingCobbleDollarsManager.getPending(player.getUUID());
            PendingCobbleDollarsManager.ClaimResult result = PendingCobbleDollarsManager.claim(player);
            switch (result) {
                case GRANTED -> {
                    TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.OBSERVER_REWARD, String.valueOf(amount), null);
                    player.sendSystemMessage(Component.translatableWithFallback(
                        "cobblecompanion.msg.pending_cobbledollars_granted", "%s Cobbledollars credited.",
                        CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(amount))));
                }
                case CHARGED -> {
                    TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.OBSERVER_CHARGE, String.valueOf(-amount), null);
                    player.sendSystemMessage(Component.translatableWithFallback(
                        "cobblecompanion.msg.pending_cobbledollars_charged", "%s Cobbledollars deducted.",
                        CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(-amount))));
                }
                case INSUFFICIENT_FUNDS -> player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.pending_cobbledollars_insufficient",
                    "Not enough Cobbledollars to settle the outstanding %s.",
                    CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(-amount))));
                case NONE -> {}
            }
            PendingCobbleDollarsSyncPacket.sendTo(player);
        });
    }
}
