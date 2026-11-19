package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Filters automatic rolls by item registry ID, not by the item's Curios slot or tags. */
public final class CurseAffinityBlacklist {
    private CurseAffinityBlacklist() {}

    public static boolean contains(ItemStack stack) {
        if (!ModConfig.SPEC.isLoaded()) return false;
        var entries = ModConfig.CURSE_AFFINITY_BLACKLIST.get();
        if (entries.isEmpty()) return false;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        for (String entry : entries) {
            if (matches(entry, id.getNamespace(), id.getPath())) return true;
        }
        return false;
    }

    static boolean matches(String rawEntry, String namespace, String path) {
        String entry = rawEntry.trim();
        if (entry.startsWith("@")) return namespace.equals(entry.substring(1));
        int separator = entry.indexOf(':');
        if (separator < 0) return globMatches(entry, namespace);
        return globMatches(entry.substring(0, separator), namespace)
                && globMatches(entry.substring(separator + 1), path);
    }

    /** Match a whole namespace or path. All characters other than '*' and '?' are literal. */
    private static boolean globMatches(String pattern, String value) {
        int p = 0, v = 0, star = -1, retry = 0;
        while (v < value.length()) {
            if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                retry = v;
            } else if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == value.charAt(v))) {
                p++;
                v++;
            } else if (star >= 0) {
                p = star + 1;
                v = ++retry;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }
}
