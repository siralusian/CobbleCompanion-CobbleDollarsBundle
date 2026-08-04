package com.cobblecompanion.cobbledollars;

import com.cobblecompanion.api.CompanionExtensions;
import com.cobblecompanion.client.screens.CompanionScreen;
import com.cobblecompanion.cobbledollars.client.WalletTabExtension;
import com.cobblecompanion.cobbledollars.network.CobbleDollarsBalanceRequestPacket;
import com.cobblecompanion.cobbledollars.network.CobbleDollarsBalanceSyncPacket;
import com.cobblecompanion.cobbledollars.network.CobbleDollarsTransferPacket;
import com.cobblecompanion.cobbledollars.network.CreativeTimeGameModeSwitchPacket;
import com.cobblecompanion.cobbledollars.network.CreativeTimePurchasePacket;
import com.cobblecompanion.cobbledollars.network.TransactionLogRequestPacket;
import com.cobblecompanion.cobbledollars.network.TransactionLogSyncPacket;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * CobbleCompanion: CobbleDollars - liefert den Wallet-Tab (Überweisungen, Transaktions-Log,
 * Creative-Zeitkauf) für CobbleCompanion: Basis nach. Registriert eigene Netzwerk-Pakete unter
 * einem eigenen Namespace (siehe onRegisterPayloads) und meldet {@link WalletTabExtension} bei
 * Basis' com.cobblecompanion.api.CompanionExtensions an - ist dieses Jar nicht installiert, läuft
 * dieser Konstruktor nie, und Basis zeigt für TAB_WALLET seinen bestehenden "nicht
 * installiert"-Platzhalter.
 */
@Mod(CobbleCompanionDollars.MOD_ID)
public class CobbleCompanionDollars {

    public static final String MOD_ID = "cobblecompanion_cobbledollars";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CobbleCompanionDollars(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onRegisterPayloads);
        CompanionExtensions.registerTab(CompanionScreen.TAB_WALLET, new WalletTabExtension());
        LOGGER.info("CobbleCompanion: CobbleDollars loading...");
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.optional()
            .playToServer(
                CobbleDollarsTransferPacket.TYPE,
                CobbleDollarsTransferPacket.CODEC,
                CobbleDollarsTransferPacket::handle)
            .playToServer(
                CobbleDollarsBalanceRequestPacket.TYPE,
                CobbleDollarsBalanceRequestPacket.CODEC,
                CobbleDollarsBalanceRequestPacket::handle)
            .playToClient(
                CobbleDollarsBalanceSyncPacket.TYPE,
                CobbleDollarsBalanceSyncPacket.CODEC,
                CobbleDollarsBalanceSyncPacket::handle)
            .playToServer(
                TransactionLogRequestPacket.TYPE,
                TransactionLogRequestPacket.CODEC,
                TransactionLogRequestPacket::handle)
            .playToClient(
                TransactionLogSyncPacket.TYPE,
                TransactionLogSyncPacket.CODEC,
                TransactionLogSyncPacket::handle)
            .playToServer(
                CreativeTimePurchasePacket.TYPE,
                CreativeTimePurchasePacket.CODEC,
                CreativeTimePurchasePacket::handle)
            .playToServer(
                CreativeTimeGameModeSwitchPacket.TYPE,
                CreativeTimeGameModeSwitchPacket.CODEC,
                CreativeTimeGameModeSwitchPacket::handle);
    }
}
