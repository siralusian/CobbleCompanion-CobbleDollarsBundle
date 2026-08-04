package com.cobblecompanion.cobbledollars.network;

import com.cobblecompanion.cobbledollars.CobbleCompanionDollars;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.CreativeTimeManager;
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
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.math.BigInteger;

/**
 * Client -> Server: Wallet-Tab "Creative kaufen"-Button (Ja/Nein-bestätigt im CompanionScreen aus
 * CobbleCompanion: Basis). Preis/Restzeit/Freischaltung verwaltet weiterhin
 * com.cobblecompanion.data.CreativeTimeManager in Basis (dort auch von der Settings-Tab-
 * Preiseditor-Anzeige und /companion gamemode genutzt) - diese Erweiterung liefert nur den
 * Kauf-Trigger nach.
 */
public record CreativeTimePurchasePacket(int minutes) implements CustomPacketPayload {

    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 600; // 10 Stunden pro Kauf, gegen Tippfehler/Versehen

    public static final CustomPacketPayload.Type<CreativeTimePurchasePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollars.MOD_ID, "creative_time_purchase"));

    public static final StreamCodec<ByteBuf, CreativeTimePurchasePacket> CODEC =
        ByteBufCodecs.VAR_INT.map(CreativeTimePurchasePacket::new, CreativeTimePurchasePacket::minutes);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreativeTimePurchasePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ModAvailability.isCobbleDollarsAvailable()) return;
            if (!CreativeTimeManager.isPurchaseEnabled() && !AdminPermissionManager.isAdminOp(player.getUUID())) {
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.creative_purchase_disabled", "Buying Creative time is currently disabled."));
                return;
            }
            // Nutzer-Vorgabe: Kauf pro Spieler per Whitelist/Blacklist einschränkbar (siehe
            // CreativeTimeManager) - AdminOps sind wie beim purchaseEnabled-Flag ausgenommen.
            if (!CreativeTimeManager.isPurchaseAllowedForPlayer(player.getUUID())
                    && !AdminPermissionManager.isAdminOp(player.getUUID())) {
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.creative_purchase_player_blocked", "You are not allowed to buy Creative time."));
                return;
            }

            int minutes = packet.minutes();
            if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.creative_invalid_minutes", "Invalid duration."));
                return;
            }

            BigInteger cost = BigInteger.valueOf(CreativeTimeManager.getPricePerMinute()).multiply(BigInteger.valueOf(minutes));
            // Nutzer-Fund: bei Preis 0 wollte CobbleDollarsBridge.charge() 0 Cobbledollars abbuchen,
            // was dort als ungültiger Betrag (amount.signum() <= 0) abgelehnt wird - Kauf schlug
            // dadurch fehl, obwohl Preis 0 laut Admin-Vorgabe "kostenlos, aber kaufbar" bedeuten soll.
            // Ein positiver Preis läuft weiterhin über die normale Abbuchung.
            if (cost.signum() > 0 && !CobbleDollarsBridge.charge(player, cost)) {
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.creative_insufficient", "Not enough Cobbledollars (%s needed).", CobbleDollarsScale.formatRaw(cost)));
                return;
            }

            CreativeTimeManager.purchase(player, minutes);
            TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.CREATIVE_PAID, cost.toString(), null);
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.creative_purchased", "Creative mode active for %s more minutes.",
                String.valueOf(minutes)));
            PacketDistributor.sendToPlayer(player, new com.cobblecompanion.network.CreativeTimeStatusSyncPacket(
                CreativeTimeManager.getPricePerMinute(),
                CreativeTimeManager.getRemainingSeconds(player.getUUID()),
                AdminPermissionManager.isAdminOp(player.getUUID()),
                CreativeTimeManager.isPurchaseEnabled()));
        });
    }
}
