package com.cobblecompanion.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Verhindert, dass unser "beschnittenes" Creative (gekaufte Zeit per CreativeTimeManager ODER
 * per Dimensionsregel erzwungen, siehe DimensionGamemodeManager) missbraucht wird, um sich über
 * das server-seitige Pro-Gamemode-Inventar-Datenpack hinweg Items zu beschaffen: Items droppen und
 * später im Survival-Modus wieder aufheben, Items in einer Kiste zwischenlagern, ODER (Nutzer-
 * Vorgabe) Cobblemon-Items benutzen (Sonderbonbon/Donnerstein auf Pokémon anwenden, Pokébälle zum
 * Rufen eines Pokémon - beides sind cobblemon-namespaced Items, ein Namespace-Check auf dem
 * gehaltenen Item deckt also automatisch auch das "kein Pokémon rufen können" mit ab, ohne jedes
 * einzelne Item einzeln listen zu müssen), Enderstorage-Blöcke platzieren (könnten sonst als
 * heimliches Dauerlager über Gamemode-Wechsel hinweg dienen) ODER Traveler's-Backpack-Rucksäcke
 * öffnen (getragen oder platziert - gleicher Grund). Echte Minecraft-OPs sind von allem
 * ausgenommen (Nutzer-Vorgabe, unverändert aus der ursprünglichen Kauf-Variante übernommen).
 *
 * Rein vanilla/NeoForge (keine Fremd-Mod-Typen außer dem reinen Namespace-String-Vergleich, der
 * KEINEN Cobblemon-Klassenimport braucht) - deshalb keine Isolationsgrenze zu beachten,
 * unconditional registriert in CobbleCompanion (siehe Konstruktor).
 */
public class CreativeRestrictionHandler {

    public static boolean isRestricted(ServerPlayer player) {
        if (player.hasPermissions(2)) return false;
        if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) return false;
        return CreativeTimeManager.hasActiveSession(player.getUUID()) || DimensionGamemodeManager.isForcedCreative(player);
    }

    /**
     * Bugfix (Live-Fund): der Namespace-Check erfasste versehentlich auch den Pokédex selbst
     * (z.B. "cobblemon:pokedex_red") - der ist zwar cobblemon-namespaced, aber kein "Item, das im
     * beschnittenen Creative missbraucht werden könnte" im Sinne der Nutzer-Vorgabe (Sonderbonbon/
     * Donnerstein/Pokébälle), sondern nur ein GUI-Öffner. Alle Pokédex-Farbvarianten UND der
     * "pokedex_screen" teilen sich das Namensmuster "pokedex" im Pfad - Ausschluss über
     * Pfad-Substring statt einer starren Farb-Liste, damit künftige Cobblemon-Farbvarianten
     * automatisch mit ausgeschlossen bleiben.
     */
    private static boolean isCobblemonItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"cobblemon".equals(id.getNamespace())) return false;
        return !id.getPath().contains("pokedex");
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!isRestricted(player)) return;
        event.setCanceled(true);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.creative_drop_blocked", "You can't drop items while using purchased Creative time."));
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isRestricted(player)) return;

        Level level = event.getLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof Container)) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.creative_container_blocked", "You can't open containers while using purchased Creative time."));
    }

    /** Nutzer-Vorgabe: Cobblemon-Items (Sonderbonbon, Donnerstein, Pokébälle, ...) dürfen im beschnittenen Creative nicht benutzt werden - deckt auch das Rufen von Pokémon ab. */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isRestricted(player)) return;
        if (!isCobblemonItem(event.getItemStack())) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.creative_cobblemon_item_blocked", "You can't use Cobblemon items while using restricted Creative mode."));
    }

    /** Wie onRightClickItem, aber für die Anwendung EINES gehaltenen Items AUF eine Entity (z.B. Sonderbonbon auf ein Pokémon anwenden). */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isRestricted(player)) return;
        if (!isCobblemonItem(player.getItemInHand(event.getHand()))) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.creative_cobblemon_item_blocked", "You can't use Cobblemon items while using restricted Creative mode."));
    }

    /** Nutzer-Vorgabe: Enderstorage-Blöcke dürfen im beschnittenen Creative nicht platziert werden. */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isRestricted(player)) return;
        if (!"enderstorage".equals(BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).getNamespace())) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.creative_enderstorage_blocked", "You can't place Ender Storage blocks while using restricted Creative mode."));
    }

    /**
     * Nutzer-Vorgabe: Traveler's-Backpack-Rucksäcke dürfen im beschnittenen Creative nicht
     * geöffnet werden - weder getragen (Curios-loses Item, wird per eigenem Tastendruck-Paket
     * geöffnet) noch als platzierter Block in der Welt. Reiner Klassennamen-String-Vergleich statt
     * Mixin/Klassenimport (Traveler's Backpack liegt nicht als Compile-Dependency vor, siehe
     * Klassenkommentar) - PlayerContainerEvent.Open ist zwar NICHT abbrechbar (Menü ist zu diesem
     * Zeitpunkt serverseitig schon gesetzt und der Öffnen-Packet bereits verschickt), player.
     * closeContainer() direkt danach schließt es aber wieder, bevor der Spieler etwas entnehmen
     * kann - deckt beide Fälle (getragen UND platziert) mit einem einzigen Handler ab, da beide
     * dasselbe Menü öffnen.
     */
    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isRestricted(player)) return;
        if (!event.getContainer().getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("travelersbackpack")) return;

        player.closeContainer();
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.creative_backpack_blocked", "You can't open Traveler's Backpacks while using restricted Creative mode."));
    }
}
