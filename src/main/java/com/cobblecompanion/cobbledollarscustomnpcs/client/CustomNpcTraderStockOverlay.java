package com.cobblecompanion.cobbledollarscustomnpcs.client;

import com.cobblecompanion.cobbledollarscustomnpcs.client.data.ClientCustomNpcTraderStockHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import noppes.npcs.client.gui.player.GuiNPCTrader;
import noppes.npcs.containers.ContainerNPCTrader;

/**
 * Nutzer-Vorgabe: "neben dem Kauf-Item anzeigen wieviel davon im Lager verfügbar ist" - zeichnet
 * die vom Server gesendete Netzwerk-Lagerbestandszahl (siehe CustomNpcTraderStockSyncPacket) klein
 * in die untere rechte Ecke jedes Verkaufsslots im CustomNPCs-Bestell-GUI, rot eingefärbt, wenn
 * weniger vorhanden ist als für einen Kauf benötigt (Item-Stückzahl im Slot).
 *
 * Bewusst OHNE Mixin gelöst: ScreenEvent.Render.Post feuert für JEDEN offenen Screen inkl.
 * GuiNPCTrader, ohne dessen kompilierten Code zu verändern. Registrierung manuell + clientseitig
 * gated über ModList.isLoaded("customnpcs") && ModList.isLoaded("create") (siehe
 * CobbleCompanionDollarsCustomNPCs.clientSetup), da direkter Import von GuiNPCTrader/
 * ContainerNPCTrader.
 */
public class CustomNpcTraderStockOverlay {

    private static final int TEXT_COLOR_OK = 0xFFFFFF;
    private static final int TEXT_COLOR_INSUFFICIENT = 0xFF5555;

    @SubscribeEvent
    public void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof GuiNPCTrader screen)) return;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
        if (!(containerScreen.getMenu() instanceof ContainerNPCTrader menu)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        for (int i = 0; i < 18 && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            int available = ClientCustomNpcTraderStockHelper.get(i);
            if (available < 0) continue;

            String text = String.valueOf(available);
            int color = available < slot.getItem().getCount() ? TEXT_COLOR_INSUFFICIENT : TEXT_COLOR_OK;
            int x = containerScreen.getGuiLeft() + slot.x + 17;
            int y = containerScreen.getGuiTop() + slot.y + 9;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, text,
                x - net.minecraft.client.Minecraft.getInstance().font.width(text), y, color, true);
            graphics.pose().popPose();
        }
    }
}
