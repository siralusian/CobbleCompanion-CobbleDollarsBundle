package com.cobblecompanion.integrations.cobbledollars;

import java.math.BigInteger;

/**
 * Zentrale Umrechnung zwischen dem intern gespeicherten/übertragenen CobbleDollars-Rohwert (ganze
 * Zahl, wie CobbleDollars selbst sie speichert) und der Anzeige (deutsches Zahlenformat: Komma als
 * Dezimaltrennzeichen) (Nutzer-Vorgabe: Item-Preise sollen Nachkommastellen erlauben, z.B. für
 * Rezepte mit ungerade teilbaren Materialkosten).
 *
 * SCALE=10 bedeutet: 1 gespeicherte Einheit = 0,1 angezeigte Cobbledollars. Gilt für JEDEN
 * CobbleDollars-Wert im gesamten Mod-Verbund (Kontostände, Preise, Überweisungen) - siehe
 * BigIntegerExtensionsKtMixin (patcht CobbleDollars' eigene Anzeige/Eingabe-Funktionen auf
 * denselben Faktor) und CobbleDollarsScaleMigration (einmalige ×10-Umrechnung bestehender Welten).
 *
 * Genauigkeit ist auf EINE Nachkommastelle begrenzt (SCALE=10, nicht 100/1000) - reicht für den
 * Anwendungsfall (Rezept-Materialkosten glatt auf Ausbeute verteilen), vermeidet aber unnötig
 * feingranulare/verwirrende Cent-artige Beträge.
 *
 * Nutzer-Vorgabe (Anzeige-Feinschliff): nie mehr als 4 Zeichen (3 Ziffern + höchstens 1 Buchstabe-
 * Suffix) - siehe formatRaw(). Ab 100 (in der jeweils aktiven Einheit) fällt die Nachkommastelle
 * weg, ab 1000 wird auf die nächste Tausender-Einheit (K/M/B/...) gekürzt. Beispielreihe (Nutzer-
 * Vorgabe): 0,5 / 5,5 / 55,5 / 555 / 5K / 5,5K / 55,5K / 555K / 5M / 5,5M / 55,5M / 555M.
 */
public final class CobbleDollarsScale {

    public static final BigInteger SCALE = BigInteger.TEN;

    // Gleiche Suffix-Reihenfolge wie CobbleDollars' eigene (jetzt per Mixin ersetzte) Kürzung, per
    // javap-Bytecode-Analyse von BigIntegerExtensionsKt.format aus dem CobbleDollars-Jar bestätigt.
    private static final String[] SUFFIXES = {
        "", "K", "M", "B", "T", "Q", "Qi", "Sx", "Sp", "O", "N", "D",
        "UD", "DD", "TD", "QD", "QiD", "SxD"
    };

    private CobbleDollarsScale() {}

    /**
     * "1,5" -> 15, "1.5" -> 15 (Punkt wird als Komma-Alias akzeptiert, Tippgewohnheit), "1" -> 10,
     * "-2,3" -> -23. Rundet NICHT - überzählige Nachkommastellen (z.B. "1,55") werden zur nächsten
     * Zehntel-Einheit abgeschnitten (truncate statt round), damit ein Admin nie unbeabsichtigt mehr
     * verlangt als eingetippt. null bei ungültiger Eingabe (leer, kein Zahlenformat, mehr als ein
     * Trennzeichen) - Aufrufer behandeln das wie zuvor "kein gültiger Betrag". Kürzungs-Suffixe
     * (K/M/...) werden NICHT als Eingabe akzeptiert - nur die volle Zahl.
     */
    public static BigInteger parseToRaw(String input) {
        if (input == null) return null;
        String trimmed = input.trim().replace('.', ',');
        if (trimmed.isEmpty()) return null;

        boolean negative = trimmed.startsWith("-");
        String unsigned = negative ? trimmed.substring(1) : trimmed;
        if (unsigned.isEmpty()) return null;

        String wholePart;
        String decimalPart;
        int comma = unsigned.indexOf(',');
        if (comma < 0) {
            wholePart = unsigned;
            decimalPart = "0";
        } else {
            if (unsigned.indexOf(',', comma + 1) >= 0) return null; // mehr als ein Trennzeichen
            wholePart = unsigned.substring(0, comma);
            decimalPart = unsigned.substring(comma + 1);
            if (wholePart.isEmpty()) wholePart = "0";
            if (decimalPart.isEmpty()) decimalPart = "0";
            // Nur die erste Nachkommastelle zählt (SCALE=10) - Rest wird abgeschnitten.
            decimalPart = decimalPart.substring(0, 1);
        }

        try {
            BigInteger whole = new BigInteger(wholePart);
            int decimalDigit = Integer.parseInt(decimalPart);
            BigInteger raw = whole.multiply(SCALE).add(BigInteger.valueOf(decimalDigit));
            return negative ? raw.negate() : raw;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Deutsches Kompaktformat, maximal 4 Zeichen (3 Ziffern + höchstens 1 Buchstabe-Suffix):
     * 15 -&gt; "1,5", 5550 -&gt; "555", 50000 -&gt; "5K", 55000 -&gt; "5,5K". Siehe Klassenkommentar
     * für die vollständige Beispielreihe.
     */
    public static String formatRaw(BigInteger raw) {
        if (raw == null) return "0";
        boolean negative = raw.signum() < 0;
        // n = Zehntel der jeweils AKTUELLEN Einheit (startet als Zehntel-Cobbledollars, siehe SCALE).
        long n;
        try {
            n = raw.abs().longValueExact();
        } catch (ArithmeticException overflow) {
            // Praktisch nie erreichbar (>~9,2 * 10^17 Cobbledollars) - Notbremse statt Absturz.
            n = Long.MAX_VALUE;
        }

        int tier = 0;
        // Ganzzahl-Anteil (n/10) müsste >= 1000 haben -> in die nächste Tausender-Einheit kürzen.
        while (n >= 10_000L && tier < SUFFIXES.length - 1) {
            n = Math.round(n / 1000.0);
            tier++;
        }

        long whole = n / 10;
        long tenths = n % 10;

        String numberText;
        if (whole >= 100) {
            // Ab 3 Ziffern keine Nachkommastelle mehr (sonst >4 Zeichen) - auf ganze Zahl runden.
            long rounded = Math.round(n / 10.0);
            if (rounded >= 1000 && tier < SUFFIXES.length - 1) {
                // Rundungs-Übertrag (z.B. 999,6 -> 1000) eine weitere Einheit hochstufen.
                rounded = Math.round(rounded / 1000.0);
                tier++;
            }
            numberText = String.valueOf(rounded);
        } else if (tenths == 0) {
            numberText = String.valueOf(whole);
        } else {
            numberText = whole + "," + tenths;
        }

        return (negative ? "-" : "") + numberText + SUFFIXES[tier];
    }
}
