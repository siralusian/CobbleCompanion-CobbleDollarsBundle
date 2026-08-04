/**
 * Öffentliche Erweiterungsschnittstelle von CobbleCompanion: Basis. Andere CobbleCompanion-Mods
 * (CobbleDollars, CobblemonWorker, ...) implementieren {@link com.cobblecompanion.api.CompanionTabExtension}
 * und melden sich über {@link com.cobblecompanion.api.CompanionExtensions} an - ohne dieses Package
 * direkt zu benutzen, bleibt der jeweilige Tab in Basis leer/inaktiv (kein Absturz, keine
 * Compile-Abhängigkeit in die andere Richtung).
 *
 * Stabilitätsversprechen: Klassen in diesem Package sind die einzige unterstützte Art, wie
 * Fremd-Module CompanionScreen erweitern - interne CompanionScreen-Klassen (client.screens/*)
 * sind kein API-Vertrag und können sich jederzeit ändern.
 */
package com.cobblecompanion.api;
