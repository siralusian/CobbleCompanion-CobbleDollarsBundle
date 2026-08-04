package com.cobblecompanion.cobbledollarscustomnpcs.mixin.create;

import com.cobblecompanion.data.CustomNpcTraderLinkManager;
import com.cobblecompanion.integrations.create.CreateNetworkStockHelper;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import noppes.npcs.NoppesUtilPlayer;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Nutzer-Vorgabe: CustomNPCs-Trader-NPCs (RoleTrader) sollen Items nur verkaufen können, wenn der
 * NPC mit einem Lagerticker verknüpft ist (siehe CustomNpcTraderLinkInteractionHandler) UND die
 * benötigte Menge tatsächlich im Netzwerk dieses Tickers vorhanden ist - die Items werden dann ECHT
 * aus dem Netzwerk entfernt (nicht nur ein Preis-Check), damit der NPC sie dem Spieler direkt geben
 * kann. Die Bezahl-Items werden NICHT vernichtet (wie es CustomNPCs standardmäßig tut), sondern wie
 * beim CobbleMerchant in eine zugewiesene Kiste umgeleitet (siehe CustomNpcTraderLinkManager).
 *
 * Ansatzpunkt: ContainerNPCTrader.clicked(int slotId, int button, ClickType, Player) - per javap-
 * Analyse verifiziert. slotId entspricht 1:1 dem Index in role.inventorySold/inventoryCurrency
 * (siehe Konstruktor-Bytecode: addSlot(new Slot(inventorySold, i, ...)) für i in 0..18). Der Inject
 * sitzt genau vor dem ERSTEN NoppesUtilPlayer.consumeItem()-Aufruf - an diesem Punkt hat CustomNPCs
 * bereits ALLE eigenen Prüfungen durchlaufen (Klick-Typ/Button, Item vorhanden, canGivePlayer,
 * canBuy VOR und NACH dem TraderEvent-Rollenskript) - ein Abbruch hier verhindert zuverlässig
 * sowohl die Bezahlung als auch die Item-Übergabe, ohne CustomNPCs' eigene Validierung zu
 * duplizieren.
 *
 * Echtes Entnehmen aus dem Netzwerk: Create liefert Pakete normalerweise asynchron über Förderbänder
 * (PackagerBlockEntity + Conveyor), das lässt sich für einen Sofort-Kauf nicht 1:1 nachbilden. Diese
 * Klasse nutzt stattdessen den ohnehin bereits synchronen Entnahme-Weg, den JEDER Packager selbst
 * verwendet (targetInventory.extract(), siehe InvManipulationBehaviour) - alle Packager desselben
 * Netzwerks (LogisticallyLinkedBehaviour.getAllPresent(freqId)) werden nacheinander um die
 * benötigte Menge erleichtert. Reicht die tatsächliche Entnahme (Race Condition ggü. der zuvor
 * geprüften InventorySummary) nicht aus, wird bereits Entnommenes sofort zurückgebucht und der
 * Handel abgebrochen - kein Dupe-/Verlust-Risiko.
 */
@Mixin(ContainerNPCTrader.class)
public abstract class ContainerNPCTraderMixin {

    @Shadow public RoleTrader role;
    @Shadow private EntityNPCInterface npc;

    @Inject(
        method = "clicked",
        at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/NoppesUtilPlayer;consumeItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;ZZ)V",
            ordinal = 0),
        cancellable = true)
    private void cobblecompanion$checkStockAndExtract(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        ItemStack sold = role.inventorySold.getItem(slotId);
        if (sold.isEmpty()) return;

        CustomNpcTraderLinkManager.Target tickerTarget = CustomNpcTraderLinkManager.getTickerLink(npc.getUUID());
        if (tickerTarget == null) {
            serverPlayer.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_trade_not_linked", "This trader is not linked to a stock ticker and cannot sell anything."));
            ci.cancel();
            return;
        }

        MinecraftServer server = level.getServer();
        ResourceKey<Level> dimensionKey = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse(tickerTarget.dimension()));
        ServerLevel tickerLevel = server.getLevel(dimensionKey);
        var blockEntityAtPos = tickerLevel != null ? tickerLevel.getBlockEntity(tickerTarget.pos()) : null;
        // Bugfix (Live-Fund): isKeeperPresent() prüft nur, ob physisch ein "Stock Keeper"-Block
        // NEBEN dem Ticker steht (Create-interne Bedienungs-Voraussetzung für dessen eigenes
        // Bestell-GUI) - hat nichts mit Netzwerk/Lagerbestand zu tun und war hier fälschlich als
        // Gate benutzt. Maßgeblich ist nur, ob der Ticker überhaupt an ein Netzwerk angebunden ist
        // (behaviour != null) - exakt wie beim bestehenden Lagerticker-Bezahl-Feature.
        if (tickerLevel == null || !(blockEntityAtPos instanceof StockTickerBlockEntity ticker) || ticker.behaviour == null) {
            serverPlayer.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_trade_ticker_missing", "The linked stock ticker is no longer present."));
            ci.cancel();
            return;
        }

        UUID freqId = ticker.behaviour.freqId;
        int needed = sold.getCount();
        int available = ticker.getRecentSummary().getCountOf(sold);
        if (available < needed) {
            serverPlayer.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_trade_out_of_stock", "Not enough stock in the linked network (%s needed, %s available).",
                String.valueOf(needed), String.valueOf(available)));
            ci.cancel();
            return;
        }

        if (!CreateNetworkStockHelper.extract(freqId, sold, needed)) {
            serverPlayer.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_trade_out_of_stock", "Not enough stock in the linked network (%s needed, %s available).",
                String.valueOf(needed), String.valueOf(available)));
            ci.cancel();
        }
    }

    @Redirect(
        method = "clicked",
        at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/NoppesUtilPlayer;consumeItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;ZZ)V"))
    private void cobblecompanion$consumeAndDeliver(Player player, ItemStack currency, boolean ignoreDamage, boolean ignoreNBT) {
        NoppesUtilPlayer.consumeItem(player, currency, ignoreDamage, ignoreNBT);
        if (currency.isEmpty() || !(player.level() instanceof ServerLevel level)) return;
        CustomNpcTraderLinkManager.depositOrDrop(level, npc.getUUID(), currency, npc.blockPosition());
    }
}
