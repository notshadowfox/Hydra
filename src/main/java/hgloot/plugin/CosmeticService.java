package hgloot.plugin;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class CosmeticService {
    private final CosmeticStateStore stateStore;
    private final Supplier<Collection<String>> defaultOwnedCosmetics;
    private final Function<String, String> cosmeticNormalizer;
    private final Predicate<String> validOwnedCosmetic;
    private final BiPredicate<String, String> validLegendarySelection;
    private final Predicate<String> validRankStyle;
    private final String noneCosmeticId;
    private final String defaultRankStyleId;

    CosmeticService(
        CosmeticStateStore stateStore,
        Supplier<Collection<String>> defaultOwnedCosmetics,
        Function<String, String> cosmeticNormalizer,
        Predicate<String> validOwnedCosmetic,
        BiPredicate<String, String> validLegendarySelection,
        Predicate<String> validRankStyle,
        String noneCosmeticId,
        String defaultRankStyleId
    ) {
        this.stateStore = stateStore;
        this.defaultOwnedCosmetics = defaultOwnedCosmetics;
        this.cosmeticNormalizer = cosmeticNormalizer;
        this.validOwnedCosmetic = validOwnedCosmetic;
        this.validLegendarySelection = validLegendarySelection;
        this.validRankStyle = validRankStyle;
        this.noneCosmeticId = noneCosmeticId;
        this.defaultRankStyleId = defaultRankStyleId;
    }

    void load() {
        stateStore.load(defaultOwnedCosmetics.get(), cosmeticNormalizer, validLegendarySelection, validRankStyle);
    }

    Set<String> ensureOwnedCosmetics(UUID playerId) {
        return stateStore.ensureOwnedCosmetics(playerId, defaultOwnedCosmetics.get(), validOwnedCosmetic);
    }

    boolean owns(UUID playerId, String cosmeticId) {
        String normalized = cosmeticNormalizer.apply(cosmeticId);
        if (normalized == null || noneCosmeticId.equals(normalized)) {
            return true;
        }
        return ensureOwnedCosmetics(playerId).contains(normalized);
    }

    boolean grantOwnership(UUID playerId, String cosmeticId) {
        String normalized = cosmeticNormalizer.apply(cosmeticId);
        return normalized != null
            && !noneCosmeticId.equals(normalized)
            && validOwnedCosmetic.test(normalized)
            && stateStore.grantOwnership(playerId, normalized, defaultOwnedCosmetics.get(), validOwnedCosmetic);
    }

    boolean revokeOwnership(UUID playerId, String cosmeticId) {
        String normalized = cosmeticNormalizer.apply(cosmeticId);
        return normalized != null
            && !noneCosmeticId.equals(normalized)
            && validOwnedCosmetic.test(normalized)
            && stateStore.revokeOwnership(playerId, normalized, defaultOwnedCosmetics.get(), validOwnedCosmetic);
    }

    String selectedHatCosmeticId(UUID playerId) {
        String equippedId = cosmeticNormalizer.apply(stateStore.equippedCosmeticId(playerId, CosmeticStateStore.EQUIP_GROUP_HAT));
        if (equippedId == null || noneCosmeticId.equals(equippedId)) {
            return noneCosmeticId;
        }
        if (!validOwnedCosmetic.test(equippedId) || !owns(playerId, equippedId)) {
            stateStore.setEquippedCosmeticId(playerId, CosmeticStateStore.EQUIP_GROUP_HAT, null);
            return noneCosmeticId;
        }
        return equippedId;
    }

    void setSelectedHatCosmeticId(UUID playerId, String cosmeticId) {
        String normalized = cosmeticNormalizer.apply(cosmeticId);
        if (normalized == null || noneCosmeticId.equals(normalized)) {
            stateStore.setEquippedCosmeticId(playerId, CosmeticStateStore.EQUIP_GROUP_HAT, null);
            return;
        }
        stateStore.setEquippedCosmeticId(playerId, CosmeticStateStore.EQUIP_GROUP_HAT, normalized);
    }

    Set<String> favoriteCosmeticIds(UUID playerId) {
        return stateStore.favoriteCosmeticIds(playerId);
    }

    boolean isFavoriteCosmetic(UUID playerId, String cosmeticId) {
        String normalized = cosmeticNormalizer.apply(cosmeticId);
        return normalized != null && stateStore.favoriteCosmeticIds(playerId).contains(normalized);
    }

    boolean toggleFavoriteCosmetic(UUID playerId, String cosmeticId, boolean eligible) {
        String normalized = cosmeticNormalizer.apply(cosmeticId);
        if (normalized == null || noneCosmeticId.equals(normalized) || !eligible) {
            return false;
        }
        boolean favorite = !stateStore.favoriteCosmeticIds(playerId).contains(normalized);
        stateStore.setFavoriteCosmetic(playerId, normalized, favorite);
        return favorite;
    }

    String selectedKillMessageId(UUID playerId) {
        String equippedId = cosmeticNormalizer.apply(stateStore.equippedCosmeticId(playerId, CosmeticStateStore.EQUIP_GROUP_KILL_MESSAGE));
        if (equippedId == null || noneCosmeticId.equals(equippedId)) {
            return null;
        }
        if (!validOwnedCosmetic.test(equippedId) || !owns(playerId, equippedId)) {
            stateStore.setEquippedCosmeticId(playerId, CosmeticStateStore.EQUIP_GROUP_KILL_MESSAGE, null);
            return null;
        }
        return equippedId;
    }

    void setSelectedKillMessageId(UUID playerId, String cosmeticId) {
        String normalized = cosmeticNormalizer.apply(cosmeticId);
        if (normalized == null || noneCosmeticId.equals(normalized)) {
            stateStore.setEquippedCosmeticId(playerId, CosmeticStateStore.EQUIP_GROUP_KILL_MESSAGE, null);
            return;
        }
        stateStore.setEquippedCosmeticId(playerId, CosmeticStateStore.EQUIP_GROUP_KILL_MESSAGE, normalized);
    }

    String selectedSystemCosmeticId(UUID playerId, String group) {
        if (group == null || group.isBlank()) {
            return null;
        }
        String cosmeticId = cosmeticNormalizer.apply(stateStore.selectedVariantId(playerId, CosmeticStateStore.systemVariantGroup(group)));
        if (cosmeticId == null || noneCosmeticId.equals(cosmeticId)) {
            return null;
        }
        if (!validOwnedCosmetic.test(cosmeticId) || !owns(playerId, cosmeticId)) {
            stateStore.setSelectedVariantId(playerId, CosmeticStateStore.systemVariantGroup(group), null, null);
            return null;
        }
        return cosmeticId;
    }

    void setSelectedSystemCosmeticId(UUID playerId, String group, String cosmeticId) {
        if (group == null || group.isBlank()) {
            return;
        }
        String normalized = cosmeticNormalizer.apply(cosmeticId);
        if (normalized == null || noneCosmeticId.equals(normalized)) {
            stateStore.setSelectedVariantId(playerId, CosmeticStateStore.systemVariantGroup(group), null, null);
            return;
        }
        stateStore.setSelectedVariantId(playerId, CosmeticStateStore.systemVariantGroup(group), normalized, null);
    }

    String selectedLegendarySkinId(UUID playerId, String legendaryKey) {
        if (legendaryKey == null || legendaryKey.isBlank()) {
            return null;
        }
        String group = CosmeticStateStore.legendaryVariantGroup(legendaryKey);
        String cosmeticId = cosmeticNormalizer.apply(stateStore.selectedVariantId(playerId, group));
        if (cosmeticId == null || noneCosmeticId.equals(cosmeticId)) {
            return null;
        }
        if (!validLegendarySelection.test(legendaryKey, cosmeticId) || !owns(playerId, cosmeticId)) {
            stateStore.setSelectedVariantId(playerId, group, null, null);
            return null;
        }
        return cosmeticId;
    }

    void setSelectedLegendarySkinId(UUID playerId, String legendaryKey, String cosmeticId) {
        if (legendaryKey == null || legendaryKey.isBlank()) {
            return;
        }
        stateStore.setSelectedVariantId(
            playerId,
            CosmeticStateStore.legendaryVariantGroup(legendaryKey),
            cosmeticNormalizer.apply(cosmeticId),
            null
        );
    }

    String selectedRankStyleId(UUID playerId) {
        String styleId = stateStore.selectedVariantId(playerId, CosmeticStateStore.VARIANT_GROUP_RANK_STYLE);
        if (styleId == null || !validRankStyle.test(styleId)) {
            stateStore.setSelectedVariantId(playerId, CosmeticStateStore.VARIANT_GROUP_RANK_STYLE, null, defaultRankStyleId);
            return defaultRankStyleId;
        }
        return styleId;
    }

    void setSelectedRankStyleId(UUID playerId, String styleId) {
        stateStore.setSelectedVariantId(playerId, CosmeticStateStore.VARIANT_GROUP_RANK_STYLE, styleId, defaultRankStyleId);
    }

    Set<UUID> playerIdsWithSelectedHats() {
        return stateStore.playerIdsWithEquippedGroup(CosmeticStateStore.EQUIP_GROUP_HAT);
    }

    Set<UUID> playerIdsWithSelectedKillMessages() {
        return stateStore.playerIdsWithEquippedGroup(CosmeticStateStore.EQUIP_GROUP_KILL_MESSAGE);
    }

    Set<UUID> playerIdsWithLegendarySelections() {
        return stateStore.playerIdsWithVariantPrefix(CosmeticStateStore.legendaryVariantGroup(""));
    }
}
