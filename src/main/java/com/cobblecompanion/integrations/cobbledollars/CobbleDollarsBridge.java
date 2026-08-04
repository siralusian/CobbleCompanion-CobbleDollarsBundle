package com.cobblecompanion.integrations.cobbledollars;

import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Einzige Klasse, die direkt gegen die CobbleDollars-API programmiert - siehe
 * com.cobblecompanion.integrations.package-info für die Isolationsregel. Aufrufer müssen VORHER
 * ModAvailability.isCobbleDollarsAvailable() geprüft haben.
 *
 * CobbleDollars ist eine Kotlin-Mod ohne dediziertes API-Package: der De-facto-Zugang läuft über
 * die statischen Kotlin-Extension-Funktionen in PlayerExtensionKt (getCobbleDollars/setCobbleDollars
 * für ONLINE Spieler, getOfflineCobbleDollars/addOfflineCobbleDollars für den persistierten
 * Offline-Kontostand einer UUID).
 */
public final class CobbleDollarsBridge {

    private CobbleDollarsBridge() {}

    public enum TransferResult { OK, INVALID_AMOUNT, INSUFFICIENT_FUNDS }

    public static BigInteger getBalance(ServerPlayer player) {
        return PlayerExtensionKt.getCobbleDollars(player);
    }

    /**
     * Überweist amount (muss > 0 sein) von sender an targetUuid. targetOnline == null bedeutet
     * offline - der Betrag wird dann direkt im persistierten Offline-Konto gutgeschrieben statt
     * über das (an den nicht vorhandenen Player gebundene) Online-Property.
     */
    public static TransferResult transfer(ServerPlayer sender, UUID targetUuid, ServerPlayer targetOnline,
            MinecraftServer server, BigInteger amount) {
        if (amount.signum() <= 0) return TransferResult.INVALID_AMOUNT;

        BigInteger senderBalance = PlayerExtensionKt.getCobbleDollars(sender);
        if (senderBalance.compareTo(amount) < 0) return TransferResult.INSUFFICIENT_FUNDS;

        PlayerExtensionKt.setCobbleDollars(sender, senderBalance.subtract(amount));
        if (targetOnline != null) {
            BigInteger targetBalance = PlayerExtensionKt.getCobbleDollars(targetOnline);
            PlayerExtensionKt.setCobbleDollars(targetOnline, targetBalance.add(amount));
        } else {
            PlayerExtensionKt.addOfflineCobbleDollars(targetUuid, server, amount);
        }
        return TransferResult.OK;
    }

    /**
     * Zieht amount (muss > 0 sein) von player ab, ohne einen Empfänger gutzuschreiben (z.B. für
     * Lagerticker-Bezahlung, siehe integrations.create). Gibt false zurück, wenn der Kontostand
     * nicht ausreicht - dann ändert sich nichts.
     */
    public static boolean charge(ServerPlayer player, BigInteger amount) {
        if (amount.signum() <= 0) return false;
        BigInteger balance = PlayerExtensionKt.getCobbleDollars(player);
        if (balance.compareTo(amount) < 0) return false;
        PlayerExtensionKt.setCobbleDollars(player, balance.subtract(amount));
        return true;
    }

    /** Schreibt amount (muss > 0 sein) gut, ohne einen Sender abzuziehen (z.B. Online-Belohnung). */
    public static boolean grant(ServerPlayer player, BigInteger amount) {
        if (amount.signum() <= 0) return false;
        BigInteger balance = PlayerExtensionKt.getCobbleDollars(player);
        PlayerExtensionKt.setCobbleDollars(player, balance.add(amount));
        return true;
    }
}
