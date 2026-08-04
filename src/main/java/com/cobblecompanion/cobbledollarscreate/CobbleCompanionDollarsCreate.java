package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantPayoutManager;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.cobblecompanion.cobbledollarscreate.data.CreateStockTickerPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.MerchantOfferManager;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverConfigSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverConfigUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.CreateStockTickerPricesSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.CreateStockTickerPricesUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.KnownPlayersSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.LinkedMerchantActionPacket;
import com.cobblecompanion.cobbledollarscreate.network.LinkedMerchantsSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.MerchantOfferRequestPacket;
import com.cobblecompanion.cobbledollarscreate.network.MerchantOfferStockSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.MerchantOfferSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.MerchantOfferUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.SaleRecipientEntityUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.SaleRecipientNetworkUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.SaleRecipientSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.StockTickerBuyPricesSyncPacket;
import com.cobblecompanion.integrations.ModAvailability;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * CobbleCompanion: CobbleDollars/Create - verbindet Create (Lagerticker, Schlauer Beobachter) mit
 * CobbleDollars. Registriert eigene Netzwerk-Pakete unter einem eigenen Namespace und initialisiert
 * die aus Basis hierher verschobenen Manager (siehe onServerStarting) - spiegelt den
 * Registrierungsumfang, den diese Klassen früher in CobbleCompanion: Basis hatten.
 */
@Mod(CobbleCompanionDollarsCreate.MOD_ID)
public class CobbleCompanionDollarsCreate {

    public static final String MOD_ID = "cobblecompanion_cobbledollars_create";
    public static final Logger LOGGER = LogUtils.getLogger();

    private boolean createHandlersRegistered = false;
    private boolean mobileStockTickerHandlerRegistered = false;
    private boolean cobbleMerchantLinkHandlerRegistered = false;
    private boolean merchantShopPeriodicSyncHandlerRegistered = false;

    public CobbleCompanionDollarsCreate(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onRegisterPayloads);
        modEventBus.addListener(this::clientSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        LOGGER.info("CobbleCompanion: CobbleDollars/Create loading...");
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.optional()
            .playToClient(
                CreateStockTickerPricesSyncPacket.TYPE,
                CreateStockTickerPricesSyncPacket.CODEC,
                CreateStockTickerPricesSyncPacket::handle)
            .playToServer(
                CreateStockTickerPricesUpdatePacket.TYPE,
                CreateStockTickerPricesUpdatePacket.CODEC,
                CreateStockTickerPricesUpdatePacket::handle)
            .playToClient(
                LinkedMerchantsSyncPacket.TYPE,
                LinkedMerchantsSyncPacket.CODEC,
                LinkedMerchantsSyncPacket::handle)
            .playToServer(
                LinkedMerchantActionPacket.TYPE,
                LinkedMerchantActionPacket.CODEC,
                LinkedMerchantActionPacket::handle)
            .playToClient(
                SaleRecipientSyncPacket.TYPE,
                SaleRecipientSyncPacket.CODEC,
                SaleRecipientSyncPacket::handle)
            .playToClient(
                KnownPlayersSyncPacket.TYPE,
                KnownPlayersSyncPacket.CODEC,
                KnownPlayersSyncPacket::handle)
            .playToServer(
                SaleRecipientNetworkUpdatePacket.TYPE,
                SaleRecipientNetworkUpdatePacket.CODEC,
                SaleRecipientNetworkUpdatePacket::handle)
            .playToServer(
                SaleRecipientEntityUpdatePacket.TYPE,
                SaleRecipientEntityUpdatePacket.CODEC,
                SaleRecipientEntityUpdatePacket::handle)
            .playToClient(
                ContentObserverConfigSyncPacket.TYPE,
                ContentObserverConfigSyncPacket.CODEC,
                ContentObserverConfigSyncPacket::handle)
            .playToServer(
                ContentObserverConfigUpdatePacket.TYPE,
                ContentObserverConfigUpdatePacket.CODEC,
                ContentObserverConfigUpdatePacket::handle)
            .playToClient(
                StockTickerBuyPricesSyncPacket.TYPE,
                StockTickerBuyPricesSyncPacket.CODEC,
                StockTickerBuyPricesSyncPacket::handle)
            .playToClient(
                MerchantOfferStockSyncPacket.TYPE,
                MerchantOfferStockSyncPacket.CODEC,
                MerchantOfferStockSyncPacket::handle)
            .playToServer(
                MerchantOfferRequestPacket.TYPE,
                MerchantOfferRequestPacket.CODEC,
                MerchantOfferRequestPacket::handle)
            .playToClient(
                MerchantOfferSyncPacket.TYPE,
                MerchantOfferSyncPacket.CODEC,
                MerchantOfferSyncPacket::handle)
            .playToServer(
                MerchantOfferUpdatePacket.TYPE,
                MerchantOfferUpdatePacket.CODEC,
                MerchantOfferUpdatePacket::handle);
    }

    /**
     * Feuert NUR clientseitig (auch im Singleplayer-Client, dort zusätzlich zu den server-seitigen
     * onServerStarting-Registrierungen). StockTickerPriceOverlay/MobileStockTickerPriceOverlay
     * importieren Create-Typen direkt und dürfen deshalb NIEMALS geladen werden, wenn Create nicht
     * installiert ist - deshalb hier manuelle Registrierung mit ModList-Gate statt @EventBusSubscriber-
     * Auto-Scan (das würde die Klasse unconditional beim Moddurchlauf laden und ohne Create mit
     * NoClassDefFoundError abstürzen).
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("create")) {
            NeoForge.EVENT_BUS.register(new com.cobblecompanion.cobbledollarscreate.client.StockTickerPriceOverlay());
        }
        if (ModList.get().isLoaded("create") && ModList.get().isLoaded("create_mobile_packages")) {
            NeoForge.EVENT_BUS.register(new com.cobblecompanion.cobbledollarscreate.mobilepackages.client.MobileStockTickerPriceOverlay());
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        // Basis' eigenes onServerStarting ruft ModAvailability.refresh() ebenfalls auf - die
        // Reihenfolge, in der NeoForge ServerStartingEvent an verschiedene Mods verteilt, ist aber
        // nicht garantiert. Erneuter (billiger, idempotenter) Aufruf hier stellt sicher, dass die
        // untenstehenden isCreateAvailable()/isCobbleDollarsAvailable()-Prüfungen nie auf einem
        // noch nicht initialisierten Stand laufen, unabhängig von der Aufrufreihenfolge.
        ModAvailability.refresh();

        CreateStockTickerPriceManager.init(event.getServer());
        ContentObserverConfigManager.init(event.getServer());
        CentralItemPriceManager.init(event.getServer());
        CobbleMerchantSellManager.init(event.getServer());
        CobbleMerchantPayoutManager.init(event.getServer());
        MerchantOfferManager.init(event.getServer());

        // Einmalige ×10-Skalierung der zentralen Item-Preisliste + Schlauer-Beobachter-Beträge
        // (Ein-Nachkommastellen-Anzeige, siehe integrations.cobbledollars.CobbleDollarsScale in
        // Basis) - nur relevant, wenn CobbleDollars installiert ist, muss NACH den obigen
        // Manager-.init()-Aufrufen laufen.
        if (ModAvailability.isCobbleDollarsAvailable()) {
            CobbleDollarsScaleMigrationCreate.runIfNeeded(event.getServer());
        }

        // Vollständigen Item-Katalog + aktuelle zentrale Preisliste einmalig in CobbleDollars'
        // eigene globale Bank-Config spiegeln (siehe CobbleDollarsBankBridge-Klassenkommentar) -
        // sonst bleibt der client-seitige "Verkaufen"-Button bei frisch gestartetem Server für
        // alles deaktiviert, bis irgendwer die Preisliste einmal (neu) speichert.
        if (ModAvailability.isCobbleDollarsAvailable()) {
            CobbleDollarsBankBridge.syncGlobalBank(CentralItemPriceManager.getUnionSellPrices());
        }

        // Erst NACH dem obigen ModAvailability-Check registrieren - diese Klassen importieren
        // Create-Typen direkt, dürfen also nur berührt/geladen werden, wenn Create wirklich
        // installiert ist.
        if (ModAvailability.isCreateAvailable() && !createHandlersRegistered) {
            NeoForge.EVENT_BUS.register(new CreateStockTickerInteractionHandler());
            NeoForge.EVENT_BUS.register(new ContentObserverInteractionHandler());
            NeoForge.EVENT_BUS.register(new AdminProtectedBlockHandler());
            NeoForge.EVENT_BUS.register(new StockTickerOrderScreenPriceHandler());
            createHandlersRegistered = true;
        }
        // Preisschild auch für den mobilen Lagerticker (siehe MobileStockTickerPriceHandler) -
        // eigener Gate, da create_mobile_packages ein separates optionales Mod ist.
        if (ModAvailability.isCreateAvailable() && ModAvailability.isMobilePackagesAvailable()
                && !mobileStockTickerHandlerRegistered) {
            NeoForge.EVENT_BUS.register(new com.cobblecompanion.cobbledollarscreate.mobilepackages.MobileStockTickerPriceHandler());
            mobileStockTickerHandlerRegistered = true;
        }
        if (ModAvailability.isCobbleDollarsAvailable() && !cobbleMerchantLinkHandlerRegistered) {
            NeoForge.EVENT_BUS.register(new CobbleMerchantLinkInteractionHandler());
            cobbleMerchantLinkHandlerRegistered = true;
        }
        // Nutzer-Vorgabe: Bestandsanzeige im CobbleMerchant-Kaufinterface aktualisiert sich alle
        // ~2s von selbst (siehe MerchantShopPeriodicSyncHandler) - braucht BEIDE Mods.
        if (ModAvailability.isCobbleDollarsAvailable() && ModAvailability.isCreateAvailable()
                && !merchantShopPeriodicSyncHandlerRegistered) {
            NeoForge.EVENT_BUS.register(new MerchantShopPeriodicSyncHandler());
            merchantShopPeriodicSyncHandlerRegistered = true;
        }
    }
}
