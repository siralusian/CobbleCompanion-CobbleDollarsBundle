package com.cobblecompanion.client.data;

/** Hält die zuletzt vom Server empfangene Op/AdminOp-Berechtigung für den Professor-Tab. */
public class ClientAdminHelper {

    private static boolean isOp = false;
    private static boolean isAdminOp = false;

    public static void apply(boolean op, boolean adminOp) {
        isOp = op;
        isAdminOp = adminOp;
    }

    /** true bei Op ODER AdminOp - steuert, ob der Professor-Tab überhaupt angezeigt wird. */
    public static boolean hasAccess() {
        return isOp || isAdminOp;
    }

    public static boolean isAdminOp() {
        return isAdminOp;
    }
}
