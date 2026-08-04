package com.cobblecompanion;

import com.cobblecompanion.commands.CobbleCompanionCommands;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.data.PlayerActivityTracker;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblecompanion.data.ServerRulesManager;
import com.cobblecompanion.network.EvolvePokemonRequestPacket;
import com.cobblecompanion.network.FriendActionPacket;
import com.cobblecompanion.network.FriendsListRequestPacket;
import com.cobblecompanion.network.FriendsSyncPacket;
import com.cobblecompanion.network.LivingDexPacket;
import com.cobblecompanion.network.LivingDexRequestPacket;
import com.cobblecompanion.network.ServerRuleChangePacket;
import com.cobblecompanion.network.ServerRulesSyncPacket;
import com.cobblecompanion.network.TeleportPreferencePacket;
import com.cobblecompanion.network.CtrlKeyStatePacket;
import com.cobblecompanion.network.TeleportToFriendPacket;
import com.cobblecompanion.network.GiftOfferPacket;
import com.cobblecompanion.network.GiftAcceptPacket;
import com.cobblecompanion.network.GiftDeclinePacket;
import com.cobblecompanion.network.GiftPendingSyncPacket;
import com.cobblecompanion.network.MyPartyRequestPacket;
import com.cobblecompanion.network.MyPartyResponsePacket;
import com.cobblecompanion.network.AdminPermissionSyncPacket;
import com.cobblecompanion.network.ProfessorPlayerListRequestPacket;
import com.cobblecompanion.network.ProfessorPlayerListResponsePacket;
import com.cobblecompanion.network.ProfessorPCRequestPacket;
import com.cobblecompanion.network.ProfessorPCResponsePacket;
import com.cobblecompanion.network.ProfessorPokedexRequestPacket;
import com.cobblecompanion.network.ProfessorPokedexResponsePacket;
import com.cobblecompanion.network.ProfessorLivingDexRequestPacket;
import com.cobblecompanion.network.ProfessorLivingDexResponsePacket;
import com.cobblecompanion.network.AdminEditPokemonPacket;
import com.cobblecompanion.network.AdminReleasePokemonPacket;
import com.cobblecompanion.network.AdminGiftPokemonPacket;
import com.cobblecompanion.network.AutoNameBoxesPacket;
import com.cobblecompanion.network.AdminResetPlayerPacket;
import com.cobblecompanion.network.AdminForceEvolvePokemonPacket;
import com.cobblecompanion.network.AdminDeEvolvePokemonPacket;
import com.cobblecompanion.network.AdminEvolveOptionsRequestPacket;
import com.cobblecompanion.network.AdminEvolveOptionsResponsePacket;
import com.cobblecompanion.network.AdminMovePokemonPacket;
import com.cobblecompanion.network.AdminDeEvolveOptionsRequestPacket;
import com.cobblecompanion.network.AdminDeEvolveOptionsResponsePacket;
import com.cobblecompanion.network.EvolutionChainRequestPacket;
import com.cobblecompanion.network.EvolutionChainResponsePacket;
import com.cobblecompanion.network.FamilySlotRequestPacket;
import com.cobblecompanion.network.FamilySlotResponsePacket;
import com.cobblecompanion.network.LivingDexPlusEnumerationRequestPacket;
import com.cobblecompanion.network.LivingDexPlusEnumerationResponsePacket;
import com.cobblecompanion.network.PcBoxCountChangePacket;
import com.cobblecompanion.network.PcBoxCountSyncPacket;
import com.cobblecompanion.network.ProfessorRctListRequestPacket;
import com.cobblecompanion.network.ProfessorRctListResponsePacket;
import com.cobblecompanion.network.AdminResetRctPacket;
import com.cobblecompanion.network.CreativeTimeStatusRequestPacket;
import com.cobblecompanion.network.CreativeTimeStatusSyncPacket;
import com.cobblecompanion.network.CreativeTimePriceChangePacket;
import com.cobblecompanion.network.CreativePurchaseEnabledChangePacket;
import com.cobblecompanion.network.CobbleDollarsIncomeMultiplierChangePacket;
import com.cobblecompanion.network.OnlineRewardSettingsChangePacket;
import com.cobblecompanion.network.HomeSummaryRequestPacket;
import com.cobblecompanion.network.HomeSummaryResponsePacket;
import com.cobblecompanion.network.HomeSlotCheckResponsePacket;
import com.cobblecompanion.network.TeamBuilderRequestPacket;
import com.cobblecompanion.network.TeamBuilderResponsePacket;
import com.cobblecompanion.network.TodoRequestPacket;
import com.cobblecompanion.network.TodoResponsePacket;
import com.cobblecompanion.network.DexCompletionRequestPacket;
import com.cobblecompanion.network.DexCompletionResponsePacket;
import com.cobblecompanion.network.DexCompletionSearchRequestPacket;
import com.cobblecompanion.network.DexCompletionSearchResponsePacket;
import com.cobblecompanion.network.TypeRequestPacket;
import com.cobblecompanion.network.TypeResponsePacket;
import com.cobblecompanion.network.MyDuplicatesRequestPacket;
import com.cobblecompanion.network.MyDuplicatesResponsePacket;
import com.cobblecompanion.network.WhoNeedsQueryPacket;
import com.cobblecompanion.network.WhoNeedsResultPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(CobbleCompanion.MOD_ID)
public class CobbleCompanion {

    public static final String MOD_ID = "cobblecompanion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CobbleCompanion(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.register(new com.cobblecompanion.data.CreativeRestrictionHandler());
        NeoForge.EVENT_BUS.register(new com.cobblecompanion.data.CommandWhitelistHandler());
        NeoForge.EVENT_BUS.register(new com.cobblecompanion.data.GamemodeInventorySyncHandler());
        LOGGER.info("CobbleCompanion loading...");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("CobbleCompanion common setup complete.");
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.optional()
            .playToClient(
                LivingDexPacket.TYPE,
                LivingDexPacket.CODEC,
                LivingDexPacket::handle)
            .playToServer(
                LivingDexRequestPacket.TYPE,
                LivingDexRequestPacket.CODEC,
                LivingDexRequestPacket::handle)
            .playToClient(
                TodoResponsePacket.TYPE,
                TodoResponsePacket.CODEC,
                TodoResponsePacket::handle)
            .playToServer(
                TodoRequestPacket.TYPE,
                TodoRequestPacket.CODEC,
                TodoRequestPacket::handle)
            .playToClient(
                DexCompletionResponsePacket.TYPE,
                DexCompletionResponsePacket.CODEC,
                DexCompletionResponsePacket::handle)
            .playToServer(
                DexCompletionRequestPacket.TYPE,
                DexCompletionRequestPacket.CODEC,
                DexCompletionRequestPacket::handle)
            .playToClient(
                DexCompletionSearchResponsePacket.TYPE,
                DexCompletionSearchResponsePacket.CODEC,
                DexCompletionSearchResponsePacket::handle)
            .playToServer(
                DexCompletionSearchRequestPacket.TYPE,
                DexCompletionSearchRequestPacket.CODEC,
                DexCompletionSearchRequestPacket::handle)
            .playToClient(
                TypeResponsePacket.TYPE,
                TypeResponsePacket.CODEC,
                TypeResponsePacket::handle)
            .playToServer(
                TypeRequestPacket.TYPE,
                TypeRequestPacket.CODEC,
                TypeRequestPacket::handle)
            .playToClient(
                MyDuplicatesResponsePacket.TYPE,
                MyDuplicatesResponsePacket.CODEC,
                MyDuplicatesResponsePacket::handle)
            .playToServer(
                MyDuplicatesRequestPacket.TYPE,
                MyDuplicatesRequestPacket.CODEC,
                MyDuplicatesRequestPacket::handle)
            .playToClient(
                WhoNeedsResultPacket.TYPE,
                WhoNeedsResultPacket.CODEC,
                WhoNeedsResultPacket::handle)
            .playToServer(
                WhoNeedsQueryPacket.TYPE,
                WhoNeedsQueryPacket.CODEC,
                WhoNeedsQueryPacket::handle)
            .playToServer(
                EvolvePokemonRequestPacket.TYPE,
                EvolvePokemonRequestPacket.CODEC,
                EvolvePokemonRequestPacket::handle)
            // Friends-System
            .playToClient(
                FriendsSyncPacket.TYPE,
                FriendsSyncPacket.CODEC,
                FriendsSyncPacket::handle)
            .playToServer(
                FriendsListRequestPacket.TYPE,
                FriendsListRequestPacket.CODEC,
                FriendsListRequestPacket::handle)
            .playToServer(
                FriendActionPacket.TYPE,
                FriendActionPacket.CODEC,
                FriendActionPacket::handle)
            // Server-Regeln
            .playToClient(
                ServerRulesSyncPacket.TYPE,
                ServerRulesSyncPacket.CODEC,
                ServerRulesSyncPacket::handle)
            .playToServer(
                ServerRuleChangePacket.TYPE,
                ServerRuleChangePacket.CODEC,
                ServerRuleChangePacket::handle)
            // Teleport-System
            .playToServer(
                TeleportPreferencePacket.TYPE,
                TeleportPreferencePacket.CODEC,
                TeleportPreferencePacket::handle)
            .playToServer(
                CtrlKeyStatePacket.TYPE,
                CtrlKeyStatePacket.CODEC,
                CtrlKeyStatePacket::handle)
            .playToServer(
                com.cobblecompanion.network.AltKeyStatePacket.TYPE,
                com.cobblecompanion.network.AltKeyStatePacket.CODEC,
                com.cobblecompanion.network.AltKeyStatePacket::handle)
            .playToServer(
                TeleportToFriendPacket.TYPE,
                TeleportToFriendPacket.CODEC,
                TeleportToFriendPacket::handle)
            // Gifting-System
            .playToServer(
                GiftOfferPacket.TYPE,
                GiftOfferPacket.CODEC,
                GiftOfferPacket::handle)
            .playToServer(
                GiftAcceptPacket.TYPE,
                GiftAcceptPacket.CODEC,
                GiftAcceptPacket::handle)
            .playToServer(
                GiftDeclinePacket.TYPE,
                GiftDeclinePacket.CODEC,
                GiftDeclinePacket::handle)
            .playToClient(
                GiftPendingSyncPacket.TYPE,
                GiftPendingSyncPacket.CODEC,
                GiftPendingSyncPacket::handle)
            .playToServer(
                MyPartyRequestPacket.TYPE,
                MyPartyRequestPacket.CODEC,
                MyPartyRequestPacket::handle)
            .playToClient(
                MyPartyResponsePacket.TYPE,
                MyPartyResponsePacket.CODEC,
                MyPartyResponsePacket::handle)
            // Professor-Tab (Op/AdminOp)
            .playToClient(
                AdminPermissionSyncPacket.TYPE,
                AdminPermissionSyncPacket.CODEC,
                AdminPermissionSyncPacket::handle)
            .playToServer(
                ProfessorPlayerListRequestPacket.TYPE,
                ProfessorPlayerListRequestPacket.CODEC,
                ProfessorPlayerListRequestPacket::handle)
            .playToClient(
                ProfessorPlayerListResponsePacket.TYPE,
                ProfessorPlayerListResponsePacket.CODEC,
                ProfessorPlayerListResponsePacket::handle)
            .playToServer(
                ProfessorPCRequestPacket.TYPE,
                ProfessorPCRequestPacket.CODEC,
                ProfessorPCRequestPacket::handle)
            .playToClient(
                ProfessorPCResponsePacket.TYPE,
                ProfessorPCResponsePacket.CODEC,
                ProfessorPCResponsePacket::handle)
            .playToServer(
                ProfessorPokedexRequestPacket.TYPE,
                ProfessorPokedexRequestPacket.CODEC,
                ProfessorPokedexRequestPacket::handle)
            .playToClient(
                ProfessorPokedexResponsePacket.TYPE,
                ProfessorPokedexResponsePacket.CODEC,
                ProfessorPokedexResponsePacket::handle)
            .playToServer(
                ProfessorLivingDexRequestPacket.TYPE,
                ProfessorLivingDexRequestPacket.CODEC,
                ProfessorLivingDexRequestPacket::handle)
            .playToClient(
                ProfessorLivingDexResponsePacket.TYPE,
                ProfessorLivingDexResponsePacket.CODEC,
                ProfessorLivingDexResponsePacket::handle)
            .playToServer(
                AdminEditPokemonPacket.TYPE,
                AdminEditPokemonPacket.CODEC,
                AdminEditPokemonPacket::handle)
            .playToServer(
                AdminReleasePokemonPacket.TYPE,
                AdminReleasePokemonPacket.CODEC,
                AdminReleasePokemonPacket::handle)
            .playToServer(
                AdminGiftPokemonPacket.TYPE,
                AdminGiftPokemonPacket.CODEC,
                AdminGiftPokemonPacket::handle)
            .playToServer(
                AutoNameBoxesPacket.TYPE,
                AutoNameBoxesPacket.CODEC,
                AutoNameBoxesPacket::handle)
            .playToServer(
                AdminResetPlayerPacket.TYPE,
                AdminResetPlayerPacket.CODEC,
                AdminResetPlayerPacket::handle)
            .playToServer(
                AdminForceEvolvePokemonPacket.TYPE,
                AdminForceEvolvePokemonPacket.CODEC,
                AdminForceEvolvePokemonPacket::handle)
            .playToServer(
                AdminDeEvolvePokemonPacket.TYPE,
                AdminDeEvolvePokemonPacket.CODEC,
                AdminDeEvolvePokemonPacket::handle)
            .playToServer(
                AdminEvolveOptionsRequestPacket.TYPE,
                AdminEvolveOptionsRequestPacket.CODEC,
                AdminEvolveOptionsRequestPacket::handle)
            .playToClient(
                AdminEvolveOptionsResponsePacket.TYPE,
                AdminEvolveOptionsResponsePacket.CODEC,
                AdminEvolveOptionsResponsePacket::handle)
            .playToServer(
                AdminMovePokemonPacket.TYPE,
                AdminMovePokemonPacket.CODEC,
                AdminMovePokemonPacket::handle)
            .playToServer(
                AdminDeEvolveOptionsRequestPacket.TYPE,
                AdminDeEvolveOptionsRequestPacket.CODEC,
                AdminDeEvolveOptionsRequestPacket::handle)
            .playToClient(
                AdminDeEvolveOptionsResponsePacket.TYPE,
                AdminDeEvolveOptionsResponsePacket.CODEC,
                AdminDeEvolveOptionsResponsePacket::handle)
            .playToServer(
                EvolutionChainRequestPacket.TYPE,
                EvolutionChainRequestPacket.CODEC,
                EvolutionChainRequestPacket::handle)
            .playToClient(
                EvolutionChainResponsePacket.TYPE,
                EvolutionChainResponsePacket.CODEC,
                EvolutionChainResponsePacket::handle)
            .playToServer(
                HomeSummaryRequestPacket.TYPE,
                HomeSummaryRequestPacket.CODEC,
                HomeSummaryRequestPacket::handle)
            .playToClient(
                HomeSummaryResponsePacket.TYPE,
                HomeSummaryResponsePacket.CODEC,
                HomeSummaryResponsePacket::handle)
            .playToServer(
                TeamBuilderRequestPacket.TYPE,
                TeamBuilderRequestPacket.CODEC,
                TeamBuilderRequestPacket::handle)
            .playToClient(
                TeamBuilderResponsePacket.TYPE,
                TeamBuilderResponsePacket.CODEC,
                TeamBuilderResponsePacket::handle)
            .playToClient(
                HomeSlotCheckResponsePacket.TYPE,
                HomeSlotCheckResponsePacket.CODEC,
                HomeSlotCheckResponsePacket::handle)
            .playToServer(
                FamilySlotRequestPacket.TYPE,
                FamilySlotRequestPacket.CODEC,
                FamilySlotRequestPacket::handle)
            .playToClient(
                FamilySlotResponsePacket.TYPE,
                FamilySlotResponsePacket.CODEC,
                FamilySlotResponsePacket::handle)
            .playToServer(
                LivingDexPlusEnumerationRequestPacket.TYPE,
                LivingDexPlusEnumerationRequestPacket.CODEC,
                LivingDexPlusEnumerationRequestPacket::handle)
            .playToClient(
                LivingDexPlusEnumerationResponsePacket.TYPE,
                LivingDexPlusEnumerationResponsePacket.CODEC,
                LivingDexPlusEnumerationResponsePacket::handle)
            .playToServer(
                PcBoxCountChangePacket.TYPE,
                PcBoxCountChangePacket.CODEC,
                PcBoxCountChangePacket::handle)
            .playToClient(
                PcBoxCountSyncPacket.TYPE,
                PcBoxCountSyncPacket.CODEC,
                PcBoxCountSyncPacket::handle)
            // RCT-Integration (siehe com.cobblecompanion.integrations, nur relevant wenn
            // ModAvailability.isRctAvailable())
            .playToServer(
                ProfessorRctListRequestPacket.TYPE,
                ProfessorRctListRequestPacket.CODEC,
                ProfessorRctListRequestPacket::handle)
            .playToClient(
                ProfessorRctListResponsePacket.TYPE,
                ProfessorRctListResponsePacket.CODEC,
                ProfessorRctListResponsePacket::handle)
            .playToServer(
                AdminResetRctPacket.TYPE,
                AdminResetRctPacket.CODEC,
                AdminResetRctPacket::handle)
            // Wallet-Tab (Überweisungen/Kontostand/Transaktions-Log) wandert komplett in
            // CobbleCompanion: CobbleDollars - siehe dortige CobbleCompanionDollars.onRegisterPayloads.
            // Create-Lagerticker-Bezahlsystem UND Schlauer-Beobachter-Konfiguration wandern komplett
            // in CobbleCompanion: CobbleDollars/Create - siehe dortige
            // CobbleCompanionDollarsCreate.onRegisterPayloads. Pokémon-Bauplan-System wandert
            // komplett in CobbleCompanion: CobblemonWorker - siehe dortige
            // CobbleCompanionWorker.onRegisterPayloads.
            // Creative-Modus-Zeitkauf (rein vanilla, nur die Bezahlung braucht Cobbledollars)
            .playToServer(
                CreativeTimeStatusRequestPacket.TYPE,
                CreativeTimeStatusRequestPacket.CODEC,
                CreativeTimeStatusRequestPacket::handle)
            .playToClient(
                CreativeTimeStatusSyncPacket.TYPE,
                CreativeTimeStatusSyncPacket.CODEC,
                CreativeTimeStatusSyncPacket::handle)
            // Kauf-Trigger + Gamemode-Umschalt-Button (Wallet-Tab) wandern in CobbleCompanion:
            // CobbleDollars - siehe dortige CobbleCompanionDollars.onRegisterPayloads. Preis-Editor
            // (Settings-Tab) und der zugrundeliegende Status bleiben hier in Basis.
            .playToServer(
                CreativeTimePriceChangePacket.TYPE,
                CreativeTimePriceChangePacket.CODEC,
                CreativeTimePriceChangePacket::handle)
            .playToServer(
                CreativePurchaseEnabledChangePacket.TYPE,
                CreativePurchaseEnabledChangePacket.CODEC,
                CreativePurchaseEnabledChangePacket::handle)
            // Dimension-Gamemode-Regeln (Settings > Gamemodes Listeneditor, siehe DimensionGamemodeManager)
            .playToClient(
                com.cobblecompanion.network.DimensionGamemodeSyncPacket.TYPE,
                com.cobblecompanion.network.DimensionGamemodeSyncPacket.CODEC,
                com.cobblecompanion.network.DimensionGamemodeSyncPacket::handle)
            .playToServer(
                com.cobblecompanion.network.DimensionGamemodeSetPacket.TYPE,
                com.cobblecompanion.network.DimensionGamemodeSetPacket.CODEC,
                com.cobblecompanion.network.DimensionGamemodeSetPacket::handle)
            // Nutzer-Vorgabe: rein lesende Listen-Anzeigen in den Settings (Gamemodes-Kategorie
            // bzw. Server-Kategorie) - Bearbeitung bleibt bei den jeweiligen /companion admin-Befehlen.
            .playToClient(
                com.cobblecompanion.network.CreativeDimensionRulesSyncPacket.TYPE,
                com.cobblecompanion.network.CreativeDimensionRulesSyncPacket.CODEC,
                com.cobblecompanion.network.CreativeDimensionRulesSyncPacket::handle)
            .playToClient(
                com.cobblecompanion.network.OnlineBonusRulesSyncPacket.TYPE,
                com.cobblecompanion.network.OnlineBonusRulesSyncPacket.CODEC,
                com.cobblecompanion.network.OnlineBonusRulesSyncPacket::handle)
            .playToServer(
                com.cobblecompanion.network.DimensionGamemodeRemovePacket.TYPE,
                com.cobblecompanion.network.DimensionGamemodeRemovePacket.CODEC,
                com.cobblecompanion.network.DimensionGamemodeRemovePacket::handle)
            .playToServer(
                CobbleDollarsIncomeMultiplierChangePacket.TYPE,
                CobbleDollarsIncomeMultiplierChangePacket.CODEC,
                CobbleDollarsIncomeMultiplierChangePacket::handle)
            .playToServer(
                OnlineRewardSettingsChangePacket.TYPE,
                OnlineRewardSettingsChangePacket.CODEC,
                OnlineRewardSettingsChangePacket::handle)
            // Gamemode-Inventar-Trennung (Ersatz für InvSync-Datapack, siehe GamemodeInventorySyncManager)
            .playToClient(
                com.cobblecompanion.network.GamemodeInventorySyncStatusPacket.TYPE,
                com.cobblecompanion.network.GamemodeInventorySyncStatusPacket.CODEC,
                com.cobblecompanion.network.GamemodeInventorySyncStatusPacket::handle)
            .playToServer(
                com.cobblecompanion.network.GamemodeInventorySyncTogglePacket.TYPE,
                com.cobblecompanion.network.GamemodeInventorySyncTogglePacket.CODEC,
                com.cobblecompanion.network.GamemodeInventorySyncTogglePacket::handle)
            .playToClient(
                com.cobblecompanion.network.GamemodeInventoryReclaimSyncPacket.TYPE,
                com.cobblecompanion.network.GamemodeInventoryReclaimSyncPacket.CODEC,
                com.cobblecompanion.network.GamemodeInventoryReclaimSyncPacket::handle)
            .playToServer(
                com.cobblecompanion.network.GamemodeInventoryReclaimClaimPacket.TYPE,
                com.cobblecompanion.network.GamemodeInventoryReclaimClaimPacket.CODEC,
                com.cobblecompanion.network.GamemodeInventoryReclaimClaimPacket::handle)
            // Preisschild im Lagerticker-Bestellmenü UND Netzwerk-Lagerbestand pro Angebot im
            // CobbleMerchant-Shop-Fenster wandern komplett in CobbleCompanion: CobbleDollars/Create -
            // siehe dortige CobbleCompanionDollarsCreate.onRegisterPayloads.
            // Nutzer-Vorgabe: gemeinsame Online-Belohnung/Schlauer-Beobachter-Meldung (siehe
            // OnlineRewardManager.tick, AutomaticIncomeReportPacket).
            .playToClient(
                com.cobblecompanion.network.AutomaticIncomeReportPacket.TYPE,
                com.cobblecompanion.network.AutomaticIncomeReportPacket.CODEC,
                com.cobblecompanion.network.AutomaticIncomeReportPacket::handle)
            // Bugfix Offline-Auszahlung Schlauer Beobachter (siehe PendingCobbleDollarsManager /
            // Home-Tab-Abhol-Badge).
            .playToClient(
                com.cobblecompanion.network.PendingCobbleDollarsSyncPacket.TYPE,
                com.cobblecompanion.network.PendingCobbleDollarsSyncPacket.CODEC,
                com.cobblecompanion.network.PendingCobbleDollarsSyncPacket::handle)
            .playToServer(
                com.cobblecompanion.network.PendingCobbleDollarsClaimPacket.TYPE,
                com.cobblecompanion.network.PendingCobbleDollarsClaimPacket.CODEC,
                com.cobblecompanion.network.PendingCobbleDollarsClaimPacket::handle);
        // Netzwerk-Lagerbestand pro Verkaufsslot im CustomNPCs-Trader-GUI wandert komplett in
        // CobbleCompanion: CobbleDollars/CustomNPCs - siehe dortige
        // CobbleCompanionDollarsCustomNPCs.onRegisterPayloads.
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CobbleCompanionCommands.register(event.getDispatcher());
    }

    private int creativeTimeTickCounter = 0;

    /** Prüft einmal pro Sekunde (20 Ticks) bei allen Online-Spielern, ob ihre gekaufte Creative-
     * Zeit abgelaufen ist (siehe CreativeTimeManager.checkExpiry). */
    private void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (++creativeTimeTickCounter < 20) return;
        creativeTimeTickCounter = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            try {
                com.cobblecompanion.data.CreativeTimeManager.checkExpiry(player);
            } catch (Throwable t) {
                LOGGER.error("[CC] CreativeTimeManager.checkExpiry failed for " + player.getName().getString(), t);
            }
            try {
                // Treibt seit dem Umbau auch die gemeinsame Schlauer-Beobachter-Meldung an (siehe
                // OnlineRewardManager.tick-Kommentar) - ContentObserverRewardManager hat deshalb
                // keinen eigenen Tick mehr.
                com.cobblecompanion.data.OnlineRewardManager.tick(player);
            } catch (Throwable t) {
                LOGGER.error("[CC] OnlineRewardManager.tick failed for " + player.getName().getString(), t);
            }
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        PlayerActivityTracker.init(event.getServer());
        FriendsManager.init(event.getServer());
        ServerRulesManager.init(event.getServer());
        AdminPermissionManager.init(event.getServer());
        com.cobblecompanion.data.CreativeTimeManager.init(event.getServer());
        com.cobblecompanion.data.DimensionGamemodeManager.init(event.getServer());
        com.cobblecompanion.data.CommandWhitelistManager.init(event.getServer());
        com.cobblecompanion.data.OnlineRewardManager.init(event.getServer());
        com.cobblecompanion.data.TransactionLogManager.init(event.getServer());
        com.cobblecompanion.data.GamemodeInventorySyncManager.init(event.getServer());
        com.cobblecompanion.data.PendingCobbleDollarsManager.init(event.getServer());
        com.cobblecompanion.data.InvSyncMigration.runIfNeeded(event.getServer());
        com.cobblecompanion.integrations.ModAvailability.refresh();
        LOGGER.info("[CC] Integrationen verfügbar: cobbledollars={}, create={}, create_mobile_packages={}, rctmod={}, curios={}, farm_and_charm={}",
            com.cobblecompanion.integrations.ModAvailability.isCobbleDollarsAvailable(),
            com.cobblecompanion.integrations.ModAvailability.isCreateAvailable(),
            com.cobblecompanion.integrations.ModAvailability.isMobilePackagesAvailable(),
            com.cobblecompanion.integrations.ModAvailability.isRctAvailable(),
            com.cobblecompanion.integrations.ModAvailability.isCuriosAvailable(),
            com.cobblecompanion.integrations.ModAvailability.isFarmAndCharmAvailable());

        // Einmalige ×10-Skalierung aller Cobbledollars-Beträge (Ein-Nachkommastellen-Anzeige,
        // siehe integrations.cobbledollars.CobbleDollarsScale) - nur relevant, wenn CobbleDollars
        // installiert ist, muss NACH allen betroffenen Manager-.init()-Aufrufen oben laufen.
        if (com.cobblecompanion.integrations.ModAvailability.isCobbleDollarsAvailable()) {
            com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScaleMigration.runIfNeeded(event.getServer());
        }

        // Create-Lagerticker-Bezahlsystem, Schlauer-Beobachter-Konfiguration UND das globale
        // Bank-Config-Spiegeln (früher hier: CobbleDollarsBankBridge.syncGlobalBank) wandern
        // komplett in CobbleCompanion: CobbleDollars/Create - siehe dortige
        // CobbleCompanionDollarsCreate.onServerStarting.

        // CustomNPCs-Trader-Verknüpfung/Preisschild UND der CobbleMerchant-Modus wandern komplett
        // in CobbleCompanion: CobbleDollars/CustomNPCs - siehe dortige
        // CobbleCompanionDollarsCustomNPCs.onServerStarting.

        // Pokémon-Bauplan-System (Weidenblock + Create-Schematic) wandert komplett in
        // CobbleCompanion: CobblemonWorker - siehe dortige CobbleCompanionWorker.onServerStarting.
    }

    /**
     * Feuert NACH ServerStartingEvent (und damit nach allen Manager-Inits inkl.
     * CommandWhitelistManager.init) - siehe CommandWhitelistSuggestionHandler-Klassenkommentar für
     * den genauen Timing-Grund.
     */
    private void onServerStarted(ServerStartedEvent event) {
        com.cobblecompanion.data.CommandWhitelistSuggestionHandler.refresh(event.getServer());
    }

    /**
     * PlayerLoggedInEvent feuert innerhalb von PlayerList.placeNewPlayer() - wirft hier
     * irgendetwas eine unbehandelte Exception, fängt Vanilla das pauschal ab und disconnected
     * den Spieler mit "Invalid player data" (kein aussagekräftiger Fehler im Client sichtbar!).
     * Deshalb jeder Schritt einzeln try/catch-abgesichert: ein Fehler in einem Teil (z.B. beim
     * Aufbau der Freundesdaten für einen speziellen Spielerzustand) darf niemals den Login
     * eines Spielers verhindern, egal ob der Client unsere Mod installiert hat oder nicht.
     */
    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        try {
            PlayerActivityTracker.recordLogin(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] PlayerActivityTracker.recordLogin failed for " + player.getName().getString(), t);
        }
        try {
            FriendsManager.recordLogin(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] FriendsManager.recordLogin failed for " + player.getName().getString(), t);
        }
        try {
            // Falls die gekaufte Creative-Zeit während der Abwesenheit abgelaufen ist - der
            // periodische Server-Tick-Check greift sonst erst nach dem nächsten Tick-Intervall.
            com.cobblecompanion.data.CreativeTimeManager.checkExpiry(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] CreativeTimeManager.checkExpiry failed for " + player.getName().getString(), t);
        }
        try {
            // Spieler kann direkt IN eine geregelte Dimension einloggen (kein
            // PlayerChangedDimensionEvent beim Login) - deshalb hier zusätzlich prüfen.
            com.cobblecompanion.data.DimensionGamemodeManager.onDimensionEnter(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] DimensionGamemodeManager.onDimensionEnter failed for " + player.getName().getString(), t);
        }
        try {
            sendLivingDexData(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] sendLivingDexData failed for " + player.getName().getString(), t);
        }
        // Freundesdaten + Server-Regeln an den (evtl. Mod-)Client senden. Vanilla-/Mod-lose
        // Clients ignorieren diese optionalen Pakete einfach (registrar.optional()) - ABER nur,
        // wenn wir vorher selbst prüfen, ob die Verbindung den jeweiligen Kanal überhaupt
        // negoziiert hat (siehe hasClientChannel-Kommentar: Nutzer-Fund im Live-Log, "may not be
        // sent to the client" für genau diese Pakete bei einem Spieler ohne unsere Mod).
        try {
            if (hasClientChannel(player, FriendsSyncPacket.TYPE)) FriendsSyncPacket.buildAndSend(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] FriendsSyncPacket.buildAndSend failed for " + player.getName().getString(), t);
        }
        try {
            if (hasClientChannel(player, ServerRulesSyncPacket.TYPE)) ServerRulesSyncPacket.sendTo(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] ServerRulesSyncPacket.sendTo failed for " + player.getName().getString(), t);
        }
        try {
            if (hasClientChannel(player, PcBoxCountSyncPacket.TYPE)) PcBoxCountSyncPacket.sendTo(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] PcBoxCountSyncPacket.sendTo failed for " + player.getName().getString(), t);
        }
        try {
            if (hasClientChannel(player, GiftPendingSyncPacket.TYPE)) GiftPendingSyncPacket.buildAndSend(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] GiftPendingSyncPacket.buildAndSend failed for " + player.getName().getString(), t);
        }
        try {
            // Jeder Spieler (nicht nur AdminOp) kann eine Abhol-Warteschlange haben, siehe
            // GamemodeInventorySyncManager.
            if (hasClientChannel(player, com.cobblecompanion.network.GamemodeInventoryReclaimSyncPacket.TYPE)) {
                com.cobblecompanion.network.GamemodeInventoryReclaimSyncPacket.sendTo(player);
            }
        } catch (Throwable t) {
            LOGGER.error("[CC] GamemodeInventoryReclaimSyncPacket.sendTo failed for " + player.getName().getString(), t);
        }
        try {
            if (hasClientChannel(player, AdminPermissionSyncPacket.TYPE)) AdminPermissionSyncPacket.sendTo(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] AdminPermissionSyncPacket.sendTo failed for " + player.getName().getString(), t);
        }
        try {
            // Bugfix Offline-Auszahlung Schlauer Beobachter: jeder Spieler kann einen ausstehenden
            // Cobbledollars-Betrag haben, siehe PendingCobbleDollarsManager.
            if (hasClientChannel(player, com.cobblecompanion.network.PendingCobbleDollarsSyncPacket.TYPE)) {
                com.cobblecompanion.network.PendingCobbleDollarsSyncPacket.sendTo(player);
            }
        } catch (Throwable t) {
            LOGGER.error("[CC] PendingCobbleDollarsSyncPacket.sendTo failed for " + player.getName().getString(), t);
        }
        try {
            // Nur AdminOp braucht die Dimension-Gamemode-Liste (Settings > Gamemodes Listeneditor).
            if (AdminPermissionManager.isAdminOp(player.getUUID())) {
                if (hasClientChannel(player, com.cobblecompanion.network.DimensionGamemodeSyncPacket.TYPE)) {
                    com.cobblecompanion.network.DimensionGamemodeSyncPacket.sendTo(player);
                }
                if (hasClientChannel(player, com.cobblecompanion.network.CreativeDimensionRulesSyncPacket.TYPE)) {
                    com.cobblecompanion.network.CreativeDimensionRulesSyncPacket.sendTo(player);
                }
                if (hasClientChannel(player, com.cobblecompanion.network.OnlineBonusRulesSyncPacket.TYPE)) {
                    com.cobblecompanion.network.OnlineBonusRulesSyncPacket.sendTo(player);
                }
                if (hasClientChannel(player, com.cobblecompanion.network.GamemodeInventorySyncStatusPacket.TYPE)) {
                    com.cobblecompanion.network.GamemodeInventorySyncStatusPacket.sendTo(player);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[CC] DimensionGamemodeSyncPacket.sendTo failed for " + player.getName().getString(), t);
        }
        try {
            com.cobblecompanion.data.OnlineRewardManager.onPlayerLogin(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] OnlineRewardManager.onPlayerLogin failed for " + player.getName().getString(), t);
        }
    }

    /**
     * Nutzer-Fund (Live-Log): "UnsupportedOperationException: Payload cobblecompanion:... may not
     * be sent to the client!" für mehrere unserer Login-Sync-Pakete bei einem Spieler ohne
     * (vollständig funktionierende) Client-Mod - trotz registrar.optional() bei der Registrierung.
     * Das .optional()-Flag allein reicht laut NeoForge-Quellcode (NetworkRegistry.checkPacket/
     * hasChannel) NICHT aus, um einen Sende-Versuch an eine Verbindung zu erlauben, die diesen
     * konkreten Kanal nicht negoziiert hat - der try/catch fängt die Exception zwar serverseitig
     * ab (kein Crash), verhindert die Exception selbst aber nicht. Deshalb hier zusätzlich vorher
     * prüfen, ob der jeweilige Kanal für DIESEN Spieler überhaupt registriert ist.
     */
    private static boolean hasClientChannel(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
        return net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(
            player.connection.getConnection(), net.minecraft.network.ConnectionProtocol.PLAY, type.id());
    }

    /** Nutzer-Vorgabe: Dimensionsregeln (siehe DimensionGamemodeManager) sofort bei jedem Wechsel prüfen, nicht erst beim nächsten Login. */
    private void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            com.cobblecompanion.data.DimensionGamemodeManager.onDimensionEnter(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] DimensionGamemodeManager.onDimensionEnter failed for " + player.getName().getString(), t);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            com.cobblecompanion.data.OnlineRewardManager.onPlayerLogout(player);
        } catch (Throwable t) {
            LOGGER.error("[CC] OnlineRewardManager.onPlayerLogout failed for " + player.getName().getString(), t);
        }
        com.cobblecompanion.data.ClientKeyStateManager.clear(player.getUUID());
    }

    public static void sendLivingDexData(ServerPlayer player) {
        try {
            List<String> species = new ArrayList<>();
            for (Pokemon p : PlayerDataHelper.getAllPokemon(player)) {
                String name = p.getSpecies().getName().toLowerCase();
                if (!species.contains(name)) species.add(name);
            }
            PacketDistributor.sendToPlayer(player, new LivingDexPacket(species));
        } catch (Exception e) {
            LOGGER.error("[CC] Failed to send living dex data", e);
        }
    }
}