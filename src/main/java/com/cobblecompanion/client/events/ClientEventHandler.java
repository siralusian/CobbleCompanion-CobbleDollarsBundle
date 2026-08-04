package com.cobblecompanion.client.events;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientNetworkUtil;
import com.cobblecompanion.client.screens.CompanionScreen;
import com.cobblecompanion.network.CtrlKeyStatePacket;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "cobblecompanion", value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        Screen screen = event.getScreen();
        if (screen != null && (
            screen.getClass().getName().contains("PokedexGui") ||
            screen.getClass().getName().contains("pokedex"))) {
            event.setNewScreen(new CompanionScreen(screen));
        }
    }

    // Minecraft repliziert Strg (anders als Shift/Sneak) nicht automatisch zum Server - wird nur
    // bei einer Änderung gesendet (siehe CtrlKeyStatePacket/ClientKeyStateManager-Kommentar),
    // nicht jeden Tick.
    private static boolean ctrlHeldSent = false;
    private static boolean altHeldSent = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Bugfix (Live-Crash): vorher nur auf "überhaupt verbunden" geprüft, nicht darauf, ob DIESER
        // Server das jeweilige Payload überhaupt kennt (ältere/keine CobbleCompanion-Version auf dem
        // Server) - NeoForge wirft dann beim Senden eine UnsupportedOperationException, die den
        // Client crasht (jeden Tick bei gedrückter Strg/Alt-Taste, also praktisch garantiert). Jetzt
        // zusätzlich per NetworkRegistry.hasChannel() geprüft, ob der Server dieses Payload für diese
        // Verbindung überhaupt ausgehandelt hat - wenn nicht, wird einfach gar nichts gesendet
        // (Feature ist dann serverseitig ohnehin wirkungslos), statt zu crashen.
        boolean ctrlHeld = Screen.hasControlDown();
        if (ctrlHeld != ctrlHeldSent && ClientNetworkUtil.canSendToServer(CtrlKeyStatePacket.TYPE.id())) {
            ctrlHeldSent = ctrlHeld;
            PacketDistributor.sendToServer(new CtrlKeyStatePacket(ctrlHeld));
        }

        boolean altHeld = Screen.hasAltDown();
        if (altHeld != altHeldSent && ClientNetworkUtil.canSendToServer(com.cobblecompanion.network.AltKeyStatePacket.TYPE.id())) {
            altHeldSent = altHeld;
            PacketDistributor.sendToServer(new com.cobblecompanion.network.AltKeyStatePacket(altHeld));
        }
    }
}