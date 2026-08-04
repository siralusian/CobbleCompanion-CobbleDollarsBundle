package com.cobblecompanion.cobbledollars.network;

import com.cobblecompanion.cobbledollars.CobbleCompanionDollars;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.data.TransactionLogManager;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsBridge;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Client -> Server: Wallet-Tab "Senden"-Button (Ja/Nein-bestätigt im CompanionScreen). Empfänger
 * wird per Namen aufgelöst (jeder bekannte Spieler, nicht nur Freunde - siehe
 * FriendsManager.resolvePlayerName, dasselbe Verfahren wie bei Freundschaftsanfragen). Empfänger
 * darf offline sein - CobbleDollarsBridge.transfer() schreibt dann direkt ins persistierte
 * Offline-Konto. Rückmeldung läuft wie beim Freunde-System über eine Chat-Systemnachricht
 * (kein eigenes Toast-System in diesem Mod vorhanden).
 */
public record CobbleDollarsTransferPacket(String targetName, String amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CobbleDollarsTransferPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollars.MOD_ID, "cobbledollars_transfer"));

    public static final StreamCodec<ByteBuf, CobbleDollarsTransferPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, CobbleDollarsTransferPacket::targetName,
        ByteBufCodecs.STRING_UTF8, CobbleDollarsTransferPacket::amount,
        CobbleDollarsTransferPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CobbleDollarsTransferPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            if (!ModAvailability.isCobbleDollarsAvailable()) return;
            MinecraftServer server = sender.getServer();
            if (server == null) return;

            BigInteger amount = CobbleDollarsScale.parseToRaw(packet.amount().trim());
            if (amount == null) {
                sendMsg(sender, "cobblecompanion.msg.wallet_invalid_amount", "Invalid amount.");
                return;
            }

            UUID targetUuid = FriendsManager.resolvePlayerName(server, packet.targetName());
            if (targetUuid == null) {
                sendMsg(sender, "cobblecompanion.msg.wallet_player_not_found", "Player %s not found.", packet.targetName());
                return;
            }
            if (targetUuid.equals(sender.getUUID())) {
                sendMsg(sender, "cobblecompanion.msg.wallet_self", "You cannot transfer to yourself.");
                return;
            }

            ServerPlayer targetOnline = server.getPlayerList().getPlayer(targetUuid);
            CobbleDollarsBridge.TransferResult result =
                CobbleDollarsBridge.transfer(sender, targetUuid, targetOnline, server, amount);

            switch (result) {
                case OK -> {
                    String targetName = targetOnline != null ? targetOnline.getName().getString()
                        : FriendsManager.getKnownName(targetUuid);
                    sendMsg(sender, "cobblecompanion.msg.wallet_sent", "%s Cobbledollars sent to %s.",
                        CobbleDollarsScale.formatRaw(amount), targetName);
                    TransactionLogManager.addEntry(sender.getUUID(), TransactionLogManager.TRANSFER_SENT, amount.toString(), targetName);
                    PacketDistributor.sendToPlayer(sender, new CobbleDollarsBalanceSyncPacket(
                        CobbleDollarsBridge.getBalance(sender).toString()));
                    TransactionLogManager.addEntry(targetUuid, TransactionLogManager.TRANSFER_RECEIVED, amount.toString(), sender.getName().getString());
                    if (targetOnline != null) {
                        sendMsg(targetOnline, "cobblecompanion.msg.wallet_received", "You received %s Cobbledollars from %s.",
                            CobbleDollarsScale.formatRaw(amount), sender.getName().getString());
                        PacketDistributor.sendToPlayer(targetOnline, new CobbleDollarsBalanceSyncPacket(
                            CobbleDollarsBridge.getBalance(targetOnline).toString()));
                    }
                }
                case INSUFFICIENT_FUNDS -> sendMsg(sender, "cobblecompanion.msg.wallet_insufficient", "Not enough Cobbledollars.");
                case INVALID_AMOUNT -> sendMsg(sender, "cobblecompanion.msg.wallet_invalid_amount", "Invalid amount.");
            }
        });
    }

    private static void sendMsg(ServerPlayer player, String key, String fallback, String... args) {
        player.sendSystemMessage(Component.translatableWithFallback(key, fallback, (Object[]) args));
    }
}
