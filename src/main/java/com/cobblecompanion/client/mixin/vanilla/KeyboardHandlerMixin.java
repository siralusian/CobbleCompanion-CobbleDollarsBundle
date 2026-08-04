package com.cobblecompanion.client.mixin.vanilla;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Root Cause (per javap-Bytecode-Analyse von KeyboardHandler.handleDebugKeys bestätigt, siehe
 * GameModeSwitcherScreenMixin-Kommentar für den ursprünglich falsch vermuteten Fix-Ort): der
 * GameModeSwitcherScreen (F3+F4) wird für Spieler ohne echten OP-Status gar nicht erst geöffnet -
 * KeyboardHandler prüft player.hasPermissions(2) BEVOR der Screen konstruiert wird und zeigt bei
 * fehlender Berechtigung nur die Chat-Nachricht "debug.gamemodes.error" statt den Screen zu öffnen.
 * Der GameModeSwitcherScreenMixin (dessen checkToClose()-Injection den eigentlichen Wechsel-Befehl
 * für Nicht-OPs sendet) kam dadurch nie zum Zug, weil der Screen nie existierte.
 *
 * Fix: genau diesen einen hasPermissions(2)-Aufruf umleiten, sodass der Screen für JEDEN Spieler
 * öffnet - handleDebugKeys() enthält per javap DREI hasPermissions(2)-Aufrufe (F3+C
 * "copyRecreateCommand", F3+N "creative/spectator", F3+F4 "GameModeSwitcherScreen"), deshalb
 * gezielt über ordinal=2 (dritter Aufruf) NUR den F3+F4-Zweig treffen, die anderen beiden bleiben
 * unverändert OP-only. Der GameModeSwitcherScreenMixin übernimmt danach unverändert: für echte OPs
 * bleibt das Verhalten exakt vanilla, für alle anderen sendet er beim Loslassen von F4 unseren
 * eigenen /companion gamemode-Befehl statt des vanilla-Wechsels.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Redirect(method = "handleDebugKeys",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasPermissions(I)Z", ordinal = 2))
    private boolean cobblecompanion$allowGameModeSwitcherForNonOps(LocalPlayer player, int level) {
        return true;
    }
}
