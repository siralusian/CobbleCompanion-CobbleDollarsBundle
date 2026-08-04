package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientSettingsHelper;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: gemeinsame periodische Meldung für Online-Belohnung UND Schlauer-Beobachter-
 * Einnahmen/Ausgaben (Nutzer-Vorgabe: "Nachrichten-Intervall der Schlauen Beobachter an den
 * Online-Belohnungs-Timer anpassen" + "sämtliches automatisches Einkommen in der Chatnachricht
 * ... zusammenfassen") - ausgelöst von OnlineRewardManager.tick() sobald dessen Intervall für
 * einen Spieler abläuft, holt sich dafür die aufgelaufenen Beträge per
 * ContentObserverRewardManager.pullPending(). Absichtlich reine Rohzahlen statt einer fertigen
 * Chat-Komponente - die eigentliche Text-/Sichtbarkeitsentscheidung (siehe
 * ClientSettingsHelper.isHideObserverMessages) passiert komplett client-seitig, damit das
 * Ausblenden-Setting rein lokal bleiben kann (kein Server-Sync nötig).
 */
public record AutomaticIncomeReportPacket(long onlineReward, long observerIncome, long observerExpense)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AutomaticIncomeReportPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "automatic_income_report"));

    public static final StreamCodec<ByteBuf, AutomaticIncomeReportPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, AutomaticIncomeReportPacket::onlineReward,
        ByteBufCodecs.VAR_LONG, AutomaticIncomeReportPacket::observerIncome,
        ByteBufCodecs.VAR_LONG, AutomaticIncomeReportPacket::observerExpense,
        AutomaticIncomeReportPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AutomaticIncomeReportPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            boolean hideObserverLines = ClientSettingsHelper.isHideObserverMessages();
            boolean showObserverLines = !hideObserverLines && (packet.observerIncome() > 0 || packet.observerExpense() > 0);
            if (packet.onlineReward() <= 0 && !showObserverLines) return;

            if (packet.onlineReward() > 0) {
                player.displayClientMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.automatic_income_online", "Online Reward: %s$",
                    CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(packet.onlineReward()))), false);
            }
            if (showObserverLines) {
                player.displayClientMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.automatic_income_income", "Automatic Income: %s$",
                    CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(packet.observerIncome()))), false);
                player.displayClientMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.automatic_income_expense", "Automatic Expenses: %s$",
                    CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(packet.observerExpense()))), false);
            }
        });
    }
}
