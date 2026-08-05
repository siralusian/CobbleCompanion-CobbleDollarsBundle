package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.KnownPlayersHelper;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverConfigSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.KnownPlayersSyncPacket;
import com.cobblecompanion.data.ClientKeyStateManager;
import com.cobblecompanion.data.FriendsManager;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Vorgabe: für den "Schlauen Beobachter" (create:content_observer) kann nur ein echter
 * Minecraft-OP per Strg+Rechtsklick einstellen, welche Items er tracken soll und wieviel
 * Cobbledollars dafür an welche Spieler ausgezahlt werden (siehe ContentObserverConfigManager) -
 * gleiches Muster wie CreateStockTickerInteractionHandler (Strg statt Shift, echte OP-Rechte statt
 * AdminPermissionManager).
 *
 * Erweiterung (Nutzer-Vorgabe, verknüpfte Zähler/Abzieher-Gruppen, Item-Abstimmung): hält der
 * Spieler dabei einen Schlauer-Beobachter-BLOCK (das Item) in der Hand, statt Strg+Rechtsklick den
 * Editor zu öffnen, wird stattdessen die Gruppen-"Frequenz" des angeklickten Blocks auf GENAU
 * DIESES Item "abgestimmt" (Datenkomponente {@link ContentObserverDataComponents#GROUP_TUNING},
 * gleiches Muster wie Creates LogisticallyLinkedBlockItem.assignFrequency - siehe dessen
 * Klassenkommentar-Vorbild). Ein anderes/frisches Item in der Hand ist davon NICHT betroffen
 * ("differenzierte" Abstimmung, Nutzer-Fund aus dem ersten Live-Test). Rechtsklick in die Luft mit
 * einem abgestimmten Item entfernt die Abstimmung wieder ({@link #onRightClickEmpty}).
 *
 * Für JEDEN Rechtsklick mit einem Schlauer-Beobachter-Item (auch ohne Strg, auch wenn KEIN
 * existierender Beobachter angeklickt wird) merkt sich {@link ContentObserverGroupTuningManager}
 * kurzzeitig die aktuelle Abstimmung des gehaltenen Items - wird direkt danach tatsächlich ein
 * neuer Beobachter platziert, übernimmt {@link ContentObserverPlacementHandler} sie.
 *
 * Zusätzlich (Nutzer-Vorgabe): ist ein Beobachter bereits konfiguriert, darf sein Filter-Item NICHT
 * mehr von Nicht-OP-Spielern per normalem Rechtsklick verändert werden (Creates eigene Filter-
 * Slot-Interaktion) - nur Strg+Rechtsklick von einem OP darf ihn (über unseren Editor) noch ändern.
 */
public class ContentObserverInteractionHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Level level = event.getLevel();
        ItemStack heldItem = player.getMainHandItem();

        // Nutzer-Vorgabe: für JEDEN Rechtsklick mit einem Beobachter-Item merken, ob/wohin es
        // abgestimmt ist - unabhängig davon, ob überhaupt ein existierender Beobachter angeklickt
        // wurde (die eigentliche Platzierung ist ein SEPARATER Rechtsklick, meist auf einen ganz
        // anderen Block/eine andere Fläche).
        if (isContentObserverItem(heldItem)) {
            ContentObserverGroupData tuning = heldItem.get(ContentObserverDataComponents.GROUP_TUNING.get());
            ContentObserverGroupTuningManager.setPending(player.getUUID(), tuning != null ? tuning.groupId() : null);
        }

        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof SmartObserverBlockEntity)) return;

        boolean ctrlHeld = ClientKeyStateManager.isCtrlHeld(player.getUUID());
        if (!ctrlHeld) {
            handlePlainRightClick(event, player, level, pos);
            return;
        }

        if (!player.hasPermissions(2)) return; // kein OP - Strg+Rechtsklick hat hier keine Wirkung
        event.setCanceled(true);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);

        if (isContentObserverItem(heldItem)) {
            handleGroupTuning(player, level, pos, heldItem);
            return;
        }

        openEditor(player, level, pos);
    }

    /** Nutzer-Vorgabe: Rechtsklick in die Luft mit einem abgestimmten Beobachter-Item entfernt die Abstimmung wieder (wie Creates eigenes Frequenz-Tuning). */
    @SubscribeEvent
    public void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack heldItem = player.getMainHandItem();
        if (!isContentObserverItem(heldItem)) return;
        if (heldItem.get(ContentObserverDataComponents.GROUP_TUNING.get()) == null) return;

        heldItem.remove(ContentObserverDataComponents.GROUP_TUNING.get());
        heldItem.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        ContentObserverGroupTuningManager.setPending(player.getUUID(), null);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.contentobserver_group_untuned", "Content Observer tool is no longer tuned to a group."));
    }

    private static boolean isContentObserverItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SmartObserverBlock;
    }

    private void handlePlainRightClick(PlayerInteractEvent.RightClickBlock event, ServerPlayer player, Level level, BlockPos pos) {
        // Nutzer-Vorgabe: konfigurierte Beobachter dürfen Nicht-OPs nicht mehr per normalem
        // Rechtsklick den Filter ändern lassen (Creates eigene Kurz-Interaktion, siehe
        // FilteringBehaviour.onShortInteract) - der Filter bleibt fest auf dem admin-eingestellten
        // Item, bis ein OP ihn über den eigenen Editor wieder ändert.
        if (player.hasPermissions(2)) return;
        if (!ContentObserverConfigManager.isConfigured(level.dimension(), pos)) return;
        event.setCanceled(true);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    private void handleGroupTuning(ServerPlayer player, Level level, BlockPos pos, ItemStack heldItem) {
        ContentObserverConfigManager.BlockConfig cfg = ContentObserverConfigManager.get(level.dimension(), pos);
        if (cfg == null) {
            cfg = new ContentObserverConfigManager.BlockConfig();
            ContentObserverConfigManager.set(level.dimension(), pos, cfg);
        }
        if (cfg.groupId == null) {
            cfg.groupId = UUID.randomUUID().toString();
            ContentObserverConfigManager.set(level.dimension(), pos, cfg);
        }

        heldItem.set(ContentObserverDataComponents.GROUP_TUNING.get(), new ContentObserverGroupData(cfg.groupId));
        heldItem.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        ContentObserverGroupTuningManager.setPending(player.getUUID(), cfg.groupId);

        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.contentobserver_group_tuned",
            "Content Observer tool tuned - every observer you place with it now joins this group."));
    }

    private void openEditor(ServerPlayer player, Level level, BlockPos pos) {
        ContentObserverConfigManager.BlockConfig cfg = ContentObserverConfigManager.get(level.dimension(), pos);
        UUID networkFreqId = ContentObserverBridge.findLinkedNetworkFreqId(level, pos);
        String networkListId = networkFreqId != null ? CentralItemPriceManager.getListIdForNetwork(networkFreqId) : null;

        // Nutzer-Vorgabe (3. Live-Test, "geteilte Katalog-Liste ohne Block-Rolle"): ist der Block
        // gruppiert, zeigt der Editor den EINEN geteilten Gruppen-Katalog (statt der eigenen Liste,
        // die bei gruppierten Blöcken leer/unbenutzt bleibt) - jeder Eintrag trägt seine eigenen
        // Zähler-/Abzieher-Zugehörigkeits-Flags mit (siehe ContentObserverGroupCatalogManager).
        List<String> encodedRules = new ArrayList<>();
        if (cfg != null && cfg.groupId != null) {
            for (ContentObserverGroupCatalogManager.CatalogEntry entry : ContentObserverGroupCatalogManager.getCatalog(cfg.groupId)) {
                encodedRules.add(ContentObserverConfigSyncPacket.encodeCatalogEntry(entry.itemId, resolveTargetName(entry.targetPlayerUuid),
                    entry.amountPerItem, entry.inCounterList, entry.inSubtractorList));
            }
        } else if (cfg != null) {
            for (ContentObserverConfigManager.Rule rule : cfg.rules) {
                encodedRules.add(ContentObserverConfigSyncPacket.encodeCatalogEntry(rule.itemId, resolveTargetName(rule.targetPlayerUuid),
                    rule.amountPerItem, false, false));
            }
        }

        String groupId = cfg != null && cfg.groupId != null ? cfg.groupId : "";
        String groupName = groupId.isBlank() ? "" : ContentObserverGroupManager.getName(groupId);
        String enabledCounterCsv = (cfg != null && cfg.enabledCounterItemIds != null) ? String.join(",", cfg.enabledCounterItemIds) : "";
        String enabledSubtractorCsv = (cfg != null && cfg.enabledSubtractorItemIds != null) ? String.join(",", cfg.enabledSubtractorItemIds) : "";
        String groupMeta = ContentObserverConfigSyncPacket.encodeGroupMeta(groupId, groupName, enabledCounterCsv, enabledSubtractorCsv);
        int promiseExpiryStage = cfg != null ? cfg.promiseExpiryStage : 0;
        ContentObserverConfigManager.GroupCounts counts = ContentObserverConfigManager.countGroupMembers(groupId.isBlank() ? null : groupId);
        String networkListName = networkListId != null ? CentralItemPriceManager.getListName(networkListId) : "";
        String meta = ContentObserverConfigSyncPacket.encodeMeta(promiseExpiryStage, cfg != null && cfg.subtractorBlock,
            networkFreqId != null, counts.counters(), counts.subtractors(), networkListName);

        // Nutzer-Vorgabe: KOMPLETTE Netzwerk-Preisliste mitschicken statt nur für ein Item - der
        // Editor kann damit für jedes getippte Item lokal sofort nachschlagen (siehe
        // ContentObserverConfigSyncPacket.networkPriceMap()).
        List<String> encodedNetworkPrices = new ArrayList<>();
        if (networkListId != null) {
            Map<String, Long> sellPrices = CentralItemPriceManager.getSellPrices(networkListId);
            Map<String, Long> buyPrices = CentralItemPriceManager.getBuyPrices(networkListId);
            java.util.Set<String> allItemIds = new java.util.LinkedHashSet<>();
            allItemIds.addAll(sellPrices.keySet());
            allItemIds.addAll(buyPrices.keySet());
            for (String itemId : allItemIds) {
                encodedNetworkPrices.add(ContentObserverConfigSyncPacket.encodeNetworkPrice(itemId,
                    CentralItemPriceManager.sellPriceForItem(networkListId, itemId),
                    CentralItemPriceManager.buyPriceForItem(networkListId, itemId)));
            }
        }

        PacketDistributor.sendToPlayer(player, new ContentObserverConfigSyncPacket(pos, encodedRules, groupMeta, meta, encodedNetworkPrices));

        // Nutzer-Vorgabe: Zielspieler-Feld bekommt eine Vorschlagsfunktion wie der Verkaufserlöse-
        // Empfänger-Picker im Lagerticker - dafür braucht der Client dieselbe bekannte-Spieler-Liste.
        if (player.getServer() != null) {
            PacketDistributor.sendToPlayer(player, new KnownPlayersSyncPacket(
                KnownPlayersSyncPacket.from(KnownPlayersHelper.getAllKnownPlayers(player.getServer()))));
        }
    }

    private static String resolveTargetName(String targetPlayerUuid) {
        if (targetPlayerUuid == null) return "";
        try {
            String known = FriendsManager.getKnownName(UUID.fromString(targetPlayerUuid));
            return known != null ? known : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
