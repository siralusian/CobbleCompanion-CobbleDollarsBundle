package com.cobblecompanion.client.data;

/** Hält den zuletzt vom Server empfangenen Creative-Zeit-Status (Wallet-Tab). */
public class ClientCreativeTimeHelper {

    private static long pricePerMinute = 0;
    private static long remainingSeconds = 0;
    private static boolean canEditPrice = false;
    private static boolean purchaseEnabled = true;

    public static void setStatus(long pricePerMinute, long remainingSeconds, boolean canEditPrice, boolean purchaseEnabled) {
        ClientCreativeTimeHelper.pricePerMinute = pricePerMinute;
        ClientCreativeTimeHelper.remainingSeconds = remainingSeconds;
        ClientCreativeTimeHelper.canEditPrice = canEditPrice;
        ClientCreativeTimeHelper.purchaseEnabled = purchaseEnabled;
    }

    public static long getPricePerMinute() { return pricePerMinute; }
    public static long getRemainingSeconds() { return remainingSeconds; }
    public static boolean canEditPrice() { return canEditPrice; }
    public static boolean isPurchaseEnabled() { return purchaseEnabled; }
}
