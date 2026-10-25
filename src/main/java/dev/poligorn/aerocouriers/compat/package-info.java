/**
 * Bridges to optional mods (Jade, claims mods, FFItemWeight, Numismatics). Each bridge is a separate class that is
 * referenced only after {@code ModList.get().isLoaded(modId)}, so the mod runs without any of them.
 */
package dev.poligorn.aerocouriers.compat;
