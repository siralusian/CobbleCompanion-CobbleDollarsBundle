package com.cobblecompanion.cobbledollarscustomnpcs;

import com.cobblecompanion.cobbledollarscustomnpcs.data.CustomNpcMerchantShopManager;
import com.cobblecompanion.cobbledollarscustomnpcs.network.CustomNpcTraderStockSyncPacket;
import com.cobblecompanion.data.CustomNpcTraderLinkManager;
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
 * CobbleCompanion: CobbleDollars/CustomNPCs - verbindet CustomNPCs-Unofficial-NeoForge (Trader-
 * Rolle, "CobbleMerchant-Modus") mit CobbleDollars. Create ist eine optionale Zusatz-Abhängigkeit
 * (Lagerticker-Verknüpfung für Trader-NPCs) - der CobbleMerchant-Modus funktioniert auch ohne.
 */
@Mod(CobbleCompanionDollarsCustomNPCs.MOD_ID)
public class CobbleCompanionDollarsCustomNPCs {

    public static final String MOD_ID = "cobblecompanion_cobbledollars_customnpcs";
    public static final Logger LOGGER = LogUtils.getLogger();

    private boolean customNpcTraderLinkHandlerRegistered = false;
    private boolean customNpcTraderStockHandlerRegistered = false;
    private boolean customNpcMerchantHandlerRegistered = false;

    public CobbleCompanionDollarsCustomNPCs(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onRegisterPayloads);
        modEventBus.addListener(this::clientSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        LOGGER.info("CobbleCompanion: CobbleDollars/CustomNPCs loading...");
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.optional()
            .playToClient(
                CustomNpcTraderStockSyncPacket.TYPE,
                CustomNpcTraderStockSyncPacket.CODEC,
                CustomNpcTraderStockSyncPacket::handle);
    }

    /**
     * Feuert NUR clientseitig (auch im Singleplayer-Client, dort zusätzlich zu den server-seitigen
     * onServerStarting-Registrierungen). CustomNpcTraderStockOverlay importiert Create-/CustomNPCs-
     * Typen direkt und darf deshalb NIEMALS geladen werden, wenn die jeweiligen Mods nicht
     * installiert sind - deshalb hier manuelle Registrierung mit ModList-Gate statt
     * @EventBusSubscriber-Auto-Scan.
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("create") && ModList.get().isLoaded("customnpcs")) {
            NeoForge.EVENT_BUS.register(new com.cobblecompanion.cobbledollarscustomnpcs.client.CustomNpcTraderStockOverlay());
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        // Basis' eigenes onServerStarting ruft ModAvailability.refresh() ebenfalls auf - die
        // Reihenfolge, in der NeoForge ServerStartingEvent an verschiedene Mods verteilt, ist aber
        // nicht garantiert. Erneuter (billiger, idempotenter) Aufruf hier stellt sicher, dass die
        // untenstehenden isCustomNpcsAvailable()/isCobbleDollarsAvailable()/isCreateAvailable()-
        // Prüfungen nie auf einem noch nicht initialisierten Stand laufen.
        ModAvailability.refresh();

        // CustomNPCs-Trader <-> Lagerticker-Verknüpfung (siehe CustomNpcTraderLinkManager, bleibt
        // in Basis - auch von CC:CobbleDollars/Create gebraucht).
        if (ModAvailability.isCustomNpcsAvailable() && !customNpcTraderLinkHandlerRegistered) {
            CustomNpcTraderLinkManager.init(event.getServer());
            NeoForge.EVENT_BUS.register(new CustomNpcTraderLinkInteractionHandler());
            customNpcTraderLinkHandlerRegistered = true;
        }
        // Preisschild-Anzeige im CustomNPCs-Trader-GUI (siehe CustomNpcTraderStockSyncHandler) -
        // braucht BEIDE Mods (Create für den Lagerticker-Bestand, CustomNPCs für das GUI).
        if (ModAvailability.isCreateAvailable() && ModAvailability.isCustomNpcsAvailable()
                && !customNpcTraderStockHandlerRegistered) {
            NeoForge.EVENT_BUS.register(new CustomNpcTraderStockSyncHandler());
            customNpcTraderStockHandlerRegistered = true;
        }
        // CustomNPC "wie ein CobbleMerchant"-Modus (siehe CustomNpcMerchantShopManager /
        // mixin.EntityNPCInterfaceMerchantMixin) - braucht BEIDE Mods, unabhängig von Create.
        if (ModAvailability.isCustomNpcsAvailable() && ModAvailability.isCobbleDollarsAvailable()
                && !customNpcMerchantHandlerRegistered) {
            CustomNpcMerchantShopManager.init(event.getServer());
            NeoForge.EVENT_BUS.register(new CustomNpcCobbleMerchantInteractionHandler());
            customNpcMerchantHandlerRegistered = true;
        }
    }
}
