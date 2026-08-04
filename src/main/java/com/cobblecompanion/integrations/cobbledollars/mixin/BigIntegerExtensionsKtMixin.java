package com.cobblecompanion.integrations.cobbledollars.mixin;

import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.harmex.cobbledollars.common.utils.MiscUtilsKt;
import fr.harmex.cobbledollars.common.utils.extensions.BigIntegerExtensionsKt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * Patcht CobbleDollars' EIGENE zentrale Anzeige-/Eingabe-Funktionen (per javap-Bytecode-Analyse
 * bestätigt als die einzige Formatierungs-/Parse-Stelle, über die CobbleDollars-Shop/Bank-Screens,
 * die HUD-Anzeige und ALLE Befehle laufen - siehe integrations.cobbledollars-Klassenkommentare)
 * auf die Ein-Nachkommastellen-Anzeige aus {@link CobbleDollarsScale} (SCALE=10). Ohne diesen
 * Mixin würde CobbleDollars' eigene UI weiterhin den rohen (10x zu großen) Wert zeigen, obwohl der
 * gespeicherte Kontostand jetzt "Zehntel-Cobbledollars" sind.
 *
 * format()/formatFull() unterscheiden sich in CobbleDollars original nur durch K/M/B/...-
 * Abkürzung bei sehr großen Beträgen vs. volle Zahl - hier bewusst vereinfacht: beide zeigen
 * immer die volle Dezimalzahl (keine Abkürzung mehr). Für eine Item-Preis-/Kontostand-Ökonomie
 * ist das die bessere Wahl als eine abgekürzte UND kommagetrennte Zahl gleichzeitig lesbar zu
 * halten.
 */
@Mixin(BigIntegerExtensionsKt.class)
public class BigIntegerExtensionsKtMixin {

    @Inject(method = "readBigInt(Lcom/mojang/brigadier/StringReader;)Ljava/math/BigInteger;", at = @At("HEAD"), cancellable = true)
    private static void cobblecompanion$readDecimal(StringReader reader, CallbackInfoReturnable<BigInteger> cir) throws CommandSyntaxException {
        String token = reader.readString();
        BigInteger decimalParsed = CobbleDollarsScale.parseToRaw(token);
        // Fällt auf CobbleDollars' eigene Ganzzahl-Auslegung zurück, WENN die Eingabe kein
        // gültiges Dezimalformat ist (z.B. leer/Buchstaben) - identisches Fehlerverhalten wie vor
        // diesem Mixin (inkl. eines möglichen null-Rückgabewerts, den der Aufrufer per
        // Kotlin-!!-Operator selbst in eine Exception umwandelt).
        cir.setReturnValue(decimalParsed != null ? decimalParsed : MiscUtilsKt.parseBigIntegerOrNull(token));
    }

    @Inject(method = "format(Ljava/math/BigInteger;Z)Lnet/minecraft/network/chat/MutableComponent;", at = @At("HEAD"), cancellable = true)
    private static void cobblecompanion$format(BigInteger amount, boolean colored, CallbackInfoReturnable<MutableComponent> cir) {
        cir.setReturnValue(Component.literal(CobbleDollarsScale.formatRaw(amount)));
    }

    @Inject(method = "formatFull(Ljava/math/BigInteger;Z)Lnet/minecraft/network/chat/MutableComponent;", at = @At("HEAD"), cancellable = true)
    private static void cobblecompanion$formatFull(BigInteger amount, boolean colored, CallbackInfoReturnable<MutableComponent> cir) {
        cir.setReturnValue(Component.literal(CobbleDollarsScale.formatRaw(amount)));
    }
}
