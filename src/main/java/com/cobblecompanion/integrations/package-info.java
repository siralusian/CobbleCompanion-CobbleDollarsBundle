/**
 * Isolationsgrenze für optionale Fremd-Mod-Integrationen (RCT, Cobbledollars, Create).
 *
 * Regel: NUR Klassen in diesem Package (und seinen Unterpaketen, z.B. integrations.rct,
 * integrations.cobbledollars, integrations.create) dürfen Typen aus den compileOnly-Jars
 * dieser Mods importieren. Der Rest von CobbleCompanion greift ausschließlich über die
 * Facade-Klassen hier zu (aktuell ModAvailability, weitere folgen je Feature).
 *
 * Grund: eine Klasse, die einen fremden Typ importiert, kann NoClassDefFoundError werfen
 * sobald sie geladen wird - auch wenn der Codepfad nie läuft. Klassen mit solchen Referenzen
 * dürfen deshalb erst NACH einer erfolgreichen ModAvailability-Prüfung instanziiert werden,
 * nie in Klassen stehen, die beim Mod-Start unbedingt geladen werden.
 *
 * Diese Trennung hält den Code außerdem so geschnitten, dass er sich bei Bedarf mechanisch
 * (Package verschieben) in eine eigene Mod auslagern lässt.
 */
package com.cobblecompanion.integrations;
