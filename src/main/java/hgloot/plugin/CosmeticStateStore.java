package hgloot.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

final class CosmeticStateStore {
    static final String EQUIP_GROUP_HAT = "hat";
    static final String EQUIP_GROUP_KILL_MESSAGE = "kill_message";
    static final String VARIANT_GROUP_RANK_STYLE = "rank_style";
    private static final String LEGENDARY_VARIANT_PREFIX = "legendary:";
    private static final String SYSTEM_VARIANT_PREFIX = "system:";

    private final JavaPlugin plugin;
    private final String ownedRoot;
    private final String selectedHatRoot;
    private final String selectedKillMessageRoot;
    private final String selectedLegendaryRoot;
    private final String selectedRankStyleRoot;
    private final String selectedSystemRoot;
    private final String favoritesRoot;
    private final Map<UUID, PlayerCosmeticState> states = new HashMap<>();

    CosmeticStateStore(
        JavaPlugin plugin,
        String ownedRoot,
        String selectedHatRoot,
        String selectedKillMessageRoot,
        String selectedLegendaryRoot,
        String selectedRankStyleRoot,
        String selectedSystemRoot,
        String favoritesRoot
    ) {
        this.plugin = plugin;
        this.ownedRoot = ownedRoot;
        this.selectedHatRoot = selectedHatRoot;
        this.selectedKillMessageRoot = selectedKillMessageRoot;
        this.selectedLegendaryRoot = selectedLegendaryRoot;
        this.selectedRankStyleRoot = selectedRankStyleRoot;
        this.selectedSystemRoot = selectedSystemRoot;
        this.favoritesRoot = favoritesRoot;
    }

    void load(
        Collection<String> validOwnedCosmetics,
        Function<String, String> cosmeticNormalizer,
        BiPredicate<String, String> validLegendarySelection,
        Predicate<String> validRankStyle
    ) {
        states.clear();
        loadOwnedCosmetics(validOwnedCosmetics, cosmeticNormalizer);
        loadSelectedHat(validOwnedCosmetics, cosmeticNormalizer);
        loadSelectedKillMessage(validOwnedCosmetics, cosmeticNormalizer);
        loadSelectedLegendary(validLegendarySelection, cosmeticNormalizer);
        loadSelectedRankStyle(validRankStyle);
        loadSelectedSystem(validOwnedCosmetics, cosmeticNormalizer);
        loadFavorites(validOwnedCosmetics, cosmeticNormalizer);
    }

    Set<String> ensureOwnedCosmetics(UUID playerId, Collection<String> defaultOwnedCosmetics, Predicate<String> validOwnedCosmetic) {
        PlayerCosmeticState state = state(playerId);
        if (!state.ownedInitialized) {
            state.ownedInitialized = true;
            return state.ownedCosmetics;
        }
        if (state.ownedCosmetics.removeIf(cosmeticId -> !validOwnedCosmetic.test(cosmeticId))) {
            saveOwnedCosmetics(playerId, state);
        }
        return state.ownedCosmetics;
    }

    boolean grantOwnership(UUID playerId, String cosmeticId, Collection<String> defaultOwnedCosmetics, Predicate<String> validOwnedCosmetic) {
        Set<String> owned = ensureOwnedCosmetics(playerId, defaultOwnedCosmetics, validOwnedCosmetic);
        if (!owned.add(cosmeticId)) {
            return false;
        }
        saveOwnedCosmetics(playerId, state(playerId));
        return true;
    }

    boolean revokeOwnership(UUID playerId, String cosmeticId, Collection<String> defaultOwnedCosmetics, Predicate<String> validOwnedCosmetic) {
        PlayerCosmeticState state = state(playerId);
        Set<String> owned = ensureOwnedCosmetics(playerId, defaultOwnedCosmetics, validOwnedCosmetic);
        if (!owned.remove(cosmeticId)) {
            return false;
        }

        boolean hatChanged = cosmeticId.equals(state.equippedByGroup.remove(EQUIP_GROUP_HAT));
        boolean killMessageChanged = cosmeticId.equals(state.equippedByGroup.remove(EQUIP_GROUP_KILL_MESSAGE));
        Set<String> removedLegendaryGroups = new HashSet<>();
        state.selectedVariantsByGroup.entrySet().removeIf(entry -> {
            boolean remove = cosmeticId.equals(entry.getValue());
            if (remove && entry.getKey().startsWith(LEGENDARY_VARIANT_PREFIX)) {
                removedLegendaryGroups.add(entry.getKey());
            }
            return remove;
        });
        boolean rankChanged = cosmeticId.equals(state.selectedVariantsByGroup.remove(VARIANT_GROUP_RANK_STYLE));
        boolean systemChanged = state.selectedVariantsByGroup.entrySet().removeIf(entry ->
            entry.getKey().startsWith(SYSTEM_VARIANT_PREFIX) && cosmeticId.equals(entry.getValue())
        );
        boolean favoriteChanged = state.favoriteCosmeticIds.remove(cosmeticId);

        saveOwnedCosmetics(playerId, state);
        if (hatChanged) {
            saveSelectedHat(playerId, state);
        }
        if (killMessageChanged) {
            saveSelectedKillMessage(playerId, state);
        }
        if (!removedLegendaryGroups.isEmpty()) {
            saveSelectedLegendary(playerId, state);
        }
        if (rankChanged) {
            saveSelectedRankStyle(playerId, state, null);
        }
        if (systemChanged) {
            saveSelectedSystem(playerId, state);
        }
        if (favoriteChanged) {
            saveFavorites(playerId, state);
        }
        return true;
    }

    Set<String> favoriteCosmeticIds(UUID playerId) {
        return Set.copyOf(state(playerId).favoriteCosmeticIds);
    }

    boolean setFavoriteCosmetic(UUID playerId, String cosmeticId, boolean favorite) {
        PlayerCosmeticState state = state(playerId);
        boolean changed = favorite
            ? state.favoriteCosmeticIds.add(cosmeticId)
            : state.favoriteCosmeticIds.remove(cosmeticId);
        if (changed) {
            saveFavorites(playerId, state);
        }
        return changed;
    }

    String equippedCosmeticId(UUID playerId, String equipGroup) {
        return state(playerId).equippedByGroup.get(equipGroup);
    }

    void setEquippedCosmeticId(UUID playerId, String equipGroup, String cosmeticId) {
        PlayerCosmeticState state = state(playerId);
        if (cosmeticId == null || cosmeticId.isBlank()) {
            state.equippedByGroup.remove(equipGroup);
        } else {
            state.equippedByGroup.put(equipGroup, cosmeticId);
        }
        if (EQUIP_GROUP_HAT.equals(equipGroup)) {
            saveSelectedHat(playerId, state);
        } else if (EQUIP_GROUP_KILL_MESSAGE.equals(equipGroup)) {
            saveSelectedKillMessage(playerId, state);
        }
    }

    String selectedVariantId(UUID playerId, String variantGroup) {
        return state(playerId).selectedVariantsByGroup.get(variantGroup);
    }

    void setSelectedVariantId(UUID playerId, String variantGroup, String variantId, String defaultValue) {
        PlayerCosmeticState state = state(playerId);
        if (variantId == null || variantId.isBlank() || variantId.equalsIgnoreCase(defaultValue)) {
            state.selectedVariantsByGroup.remove(variantGroup);
        } else {
            state.selectedVariantsByGroup.put(variantGroup, variantId);
        }
        if (VARIANT_GROUP_RANK_STYLE.equals(variantGroup)) {
            saveSelectedRankStyle(playerId, state, defaultValue);
        } else if (variantGroup.startsWith(LEGENDARY_VARIANT_PREFIX)) {
            saveSelectedLegendary(playerId, state);
        } else if (variantGroup.startsWith(SYSTEM_VARIANT_PREFIX)) {
            saveSelectedSystem(playerId, state);
        }
    }

    Set<UUID> playerIdsWithEquippedGroup(String equipGroup) {
        Set<UUID> playerIds = new HashSet<>();
        for (Map.Entry<UUID, PlayerCosmeticState> entry : states.entrySet()) {
            if (entry.getValue().equippedByGroup.containsKey(equipGroup)) {
                playerIds.add(entry.getKey());
            }
        }
        return playerIds;
    }

    Set<UUID> playerIdsWithVariantPrefix(String variantPrefix) {
        Set<UUID> playerIds = new HashSet<>();
        for (Map.Entry<UUID, PlayerCosmeticState> entry : states.entrySet()) {
            for (String key : entry.getValue().selectedVariantsByGroup.keySet()) {
                if (key.startsWith(variantPrefix)) {
                    playerIds.add(entry.getKey());
                    break;
                }
            }
        }
        return playerIds;
    }

    static String legendaryVariantGroup(String legendaryKey) {
        return LEGENDARY_VARIANT_PREFIX + legendaryKey;
    }

    static String systemVariantGroup(String group) {
        return SYSTEM_VARIANT_PREFIX + (group == null ? "" : group.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private void loadOwnedCosmetics(Collection<String> validOwnedCosmetics, Function<String, String> cosmeticNormalizer) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ownedRoot);
        if (section == null) {
            return;
        }
        Set<String> validIds = new HashSet<>(validOwnedCosmetics);
        for (String key : section.getKeys(false)) {
            UUID playerId = parseUuid(key);
            if (playerId == null) {
                continue;
            }
            PlayerCosmeticState state = state(playerId);
            state.ownedInitialized = true;
            for (String rawId : section.getStringList(key)) {
                String normalized = cosmeticNormalizer.apply(rawId);
                if (normalized != null && validIds.contains(normalized)) {
                    state.ownedCosmetics.add(normalized);
                }
            }
        }
    }

    private void loadSelectedHat(Collection<String> validOwnedCosmetics, Function<String, String> cosmeticNormalizer) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(selectedHatRoot);
        if (section == null) {
            return;
        }
        Set<String> validIds = new HashSet<>(validOwnedCosmetics);
        for (String key : section.getKeys(false)) {
            UUID playerId = parseUuid(key);
            if (playerId == null) {
                continue;
            }
            String normalized = cosmeticNormalizer.apply(section.getString(key));
            if (normalized != null && validIds.contains(normalized)) {
                state(playerId).equippedByGroup.put(EQUIP_GROUP_HAT, normalized);
            }
        }
    }

    private void loadSelectedLegendary(
        BiPredicate<String, String> validLegendarySelection,
        Function<String, String> cosmeticNormalizer
    ) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection(selectedLegendaryRoot);
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID playerId = parseUuid(key);
            if (playerId == null) {
                continue;
            }
            ConfigurationSection playerSection = root.getConfigurationSection(key);
            if (playerSection == null) {
                continue;
            }
            PlayerCosmeticState state = state(playerId);
            for (String legendaryKey : playerSection.getKeys(false)) {
                String normalized = cosmeticNormalizer.apply(playerSection.getString(legendaryKey));
                if (normalized != null && validLegendarySelection.test(legendaryKey, normalized)) {
                    state.selectedVariantsByGroup.put(legendaryVariantGroup(legendaryKey), normalized);
                }
            }
        }
    }

    private void loadSelectedKillMessage(Collection<String> validOwnedCosmetics, Function<String, String> cosmeticNormalizer) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(selectedKillMessageRoot);
        if (section == null) {
            return;
        }
        Set<String> validIds = new HashSet<>(validOwnedCosmetics);
        for (String key : section.getKeys(false)) {
            UUID playerId = parseUuid(key);
            if (playerId == null) {
                continue;
            }
            String normalized = cosmeticNormalizer.apply(section.getString(key));
            if (normalized != null && validIds.contains(normalized)) {
                state(playerId).equippedByGroup.put(EQUIP_GROUP_KILL_MESSAGE, normalized);
            }
        }
    }

    private void loadSelectedRankStyle(Predicate<String> validRankStyle) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(selectedRankStyleRoot);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID playerId = parseUuid(key);
            if (playerId == null) {
                continue;
            }
            String styleId = section.getString(key);
            if (styleId != null && validRankStyle.test(styleId)) {
                state(playerId).selectedVariantsByGroup.put(VARIANT_GROUP_RANK_STYLE, styleId);
            }
        }
    }

    private void loadSelectedSystem(Collection<String> validOwnedCosmetics, Function<String, String> cosmeticNormalizer) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection(selectedSystemRoot);
        if (root == null) {
            return;
        }
        Set<String> validIds = new HashSet<>(validOwnedCosmetics);
        for (String key : root.getKeys(false)) {
            UUID playerId = parseUuid(key);
            if (playerId == null) {
                continue;
            }
            ConfigurationSection playerSection = root.getConfigurationSection(key);
            if (playerSection == null) {
                continue;
            }
            PlayerCosmeticState state = state(playerId);
            for (String group : playerSection.getKeys(false)) {
                String normalized = cosmeticNormalizer.apply(playerSection.getString(group));
                if (normalized != null && validIds.contains(normalized)) {
                    state.selectedVariantsByGroup.put(systemVariantGroup(group), normalized);
                }
            }
        }
    }

    private void loadFavorites(Collection<String> validOwnedCosmetics, Function<String, String> cosmeticNormalizer) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(favoritesRoot);
        if (section == null) {
            return;
        }
        Set<String> validIds = new HashSet<>(validOwnedCosmetics);
        for (String key : section.getKeys(false)) {
            UUID playerId = parseUuid(key);
            if (playerId == null) {
                continue;
            }
            PlayerCosmeticState state = state(playerId);
            for (String rawId : section.getStringList(key)) {
                String normalized = cosmeticNormalizer.apply(rawId);
                if (normalized != null && validIds.contains(normalized)) {
                    state.favoriteCosmeticIds.add(normalized);
                }
            }
        }
    }

    private void saveOwnedCosmetics(UUID playerId, PlayerCosmeticState state) {
        plugin.getConfig().set(ownedRoot + "." + playerId, new ArrayList<>(state.ownedCosmetics));
        plugin.saveConfig();
    }

    private void saveSelectedHat(UUID playerId, PlayerCosmeticState state) {
        String equippedId = state.equippedByGroup.get(EQUIP_GROUP_HAT);
        plugin.getConfig().set(selectedHatRoot + "." + playerId, equippedId);
        plugin.saveConfig();
    }

    private void saveSelectedKillMessage(UUID playerId, PlayerCosmeticState state) {
        String equippedId = state.equippedByGroup.get(EQUIP_GROUP_KILL_MESSAGE);
        plugin.getConfig().set(selectedKillMessageRoot + "." + playerId, equippedId);
        plugin.saveConfig();
    }

    private void saveSelectedLegendary(UUID playerId, PlayerCosmeticState state) {
        String path = selectedLegendaryRoot + "." + playerId;
        plugin.getConfig().set(path, null);
        for (Map.Entry<String, String> entry : state.selectedVariantsByGroup.entrySet()) {
            if (!entry.getKey().startsWith(LEGENDARY_VARIANT_PREFIX)) {
                continue;
            }
            plugin.getConfig().set(path + "." + entry.getKey().substring(LEGENDARY_VARIANT_PREFIX.length()), entry.getValue());
        }
        plugin.saveConfig();
    }

    private void saveSelectedRankStyle(UUID playerId, PlayerCosmeticState state, String defaultRankStyleId) {
        String styleId = state.selectedVariantsByGroup.get(VARIANT_GROUP_RANK_STYLE);
        String path = selectedRankStyleRoot + "." + playerId;
        if (styleId == null || (defaultRankStyleId != null && defaultRankStyleId.equals(styleId))) {
            plugin.getConfig().set(path, null);
        } else {
            plugin.getConfig().set(path, styleId);
        }
        plugin.saveConfig();
    }

    private void saveSelectedSystem(UUID playerId, PlayerCosmeticState state) {
        String path = selectedSystemRoot + "." + playerId;
        plugin.getConfig().set(path, null);
        for (Map.Entry<String, String> entry : state.selectedVariantsByGroup.entrySet()) {
            if (!entry.getKey().startsWith(SYSTEM_VARIANT_PREFIX)) {
                continue;
            }
            String group = entry.getKey().substring(SYSTEM_VARIANT_PREFIX.length());
            if (!group.isBlank()) {
                plugin.getConfig().set(path + "." + group, entry.getValue());
            }
        }
        plugin.saveConfig();
    }

    private void saveFavorites(UUID playerId, PlayerCosmeticState state) {
        plugin.getConfig().set(favoritesRoot + "." + playerId, new ArrayList<>(state.favoriteCosmeticIds));
        plugin.saveConfig();
    }

    private PlayerCosmeticState state(UUID playerId) {
        return states.computeIfAbsent(playerId, ignored -> new PlayerCosmeticState());
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final class PlayerCosmeticState {
        private final Set<String> ownedCosmetics = new LinkedHashSet<>();
        private final Map<String, String> equippedByGroup = new HashMap<>();
        private final Map<String, String> selectedVariantsByGroup = new HashMap<>();
        private final Set<String> favoriteCosmeticIds = new LinkedHashSet<>();
        private boolean ownedInitialized;
    }
}
