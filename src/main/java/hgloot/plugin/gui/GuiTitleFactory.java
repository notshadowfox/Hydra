package hgloot.plugin.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class GuiTitleFactory {
    private static final String SHIFT_LEFT_8 = "\uF801";
    private static final String NAVIGATOR_GLYPH = "\uE101";
    private static final String GAMEMODE_SWITCHER_GLYPH = "\uE102";
    private static final String NAVIGATOR_MINIGAMES_GLYPH = "\uE120";
    private static final String FRIENDS_ALL_GLYPH = "\uE121";
    private static final String FRIENDS_REQUESTS_GLYPH = "\uE122";
    private static final String FRIENDS_BLOCKED_GLYPH = "\uE123";
    private static final String PARTY_LEADER_GLYPH = "\uE124";
    private static final String PARTY_MEMBER_GLYPH = "\uE125";
    private static final String COSMETICS_FAVORITES_GLYPH = "\uE126";
    private static final String RECIPES_LEGENDARY_GLYPH = "\uE127";
    private static final String RECIPES_CRAFTING_UTILITY_GLYPH = "\uE128";
    private static final String RECIPES_CRAFTING_LEGENDARY_GLYPH = "\uE129";
    private static final String NAVIGATOR_HUNGER_GAMES_GLYPH = "\uE12A";
    private static final String SHARD_OPEN_GLYPH = "\uE12B";
    private static final String SHARD_OPENING_GLYPH = "\uE12C";
    private static final String[] BATTLE_PASS_GLYPHS = {
        "\uE12D", "\uE12E", "\uE12F", "\uE130", "\uE131", "\uE132", "\uE133", "\uE134"
    };
    private static final String AUCTION_FIRST_PAGE_GLYPH = "\uE135";
    private static final String AUCTION_MIDDLE_PAGE_GLYPH = "\uE136";
    private static final String AUCTION_LAST_PAGE_GLYPH = "\uE137";
    private static final String AUCTION_SINGLE_PAGE_GLYPH = "\uE138";
    private static final String KIT_EDITOR_GLYPH = "\uE139";
    private static final String SETTINGS_PAGE_ONE_GLYPH = "\uE13B";
    private static final String SETTINGS_PAGE_TWO_GLYPH = "\uE13C";
    private static final String COSMETICS_GLYPH = "\uE110";
    private static final String HATS_GLYPH = "\uE111";
    private static final String HYDRA_STYLES_GLYPH = "\uE112";
    private static final String KILL_EFFECTS_GLYPH = "\uE113";
    private static final String KILL_MESSAGES_GLYPH = "\uE114";
    private static final String RECIPES_GLYPH = "\uE115";
    private static final String SOCIALS_GLYPH = "\uE116";
    private static final String WEAPON_SKINS_GLYPH = "\uE117";
    private static final String TRAILS_GLYPH = "\uE118";
    private static final String TRIMS_GLYPH = "\uE119";
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");
    private static final Key GUI_FONT = Key.key("hydra", "gui");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private GuiTitleFactory() {}

    public static Component menuTitle(int size, String legacyTitle) {
        return menuTitle(size, legacyTitle, null);
    }

    public static Component menuTitle(int size, String legacyTitle, String backgroundGlyph) {
        String plainTitle = PLAIN.serialize(LEGACY.deserialize(legacyTitle == null ? "" : legacyTitle)).trim();
        if (plainTitle.isBlank()) {
            plainTitle = "Menu";
        }
        if (backgroundGlyph == null || backgroundGlyph.isBlank()) {
            return Component.text(plainTitle);
        }
        return Component.text(backgroundGlyph)
            .append(Component.text(" " + plainTitle, NamedTextColor.WHITE));
    }

    public static Component gamemodeSwitcherTitle(String legacyTitle) {
        return Component.text(GAMEMODE_SWITCHER_GLYPH).font(GUI_FONT);
    }

    public static Component navigatorTitle(String legacyTitle) {
        return Component.text(SHIFT_LEFT_8).font(DEFAULT_FONT)
            .append(Component.text(NAVIGATOR_GLYPH, NamedTextColor.WHITE).font(GUI_FONT));
    }

    public static Component themedTitle(String theme, String legacyTitle) {
        String glyph = glyphForTheme(theme);
        if (glyph == null) {
            return menuTitle(27, legacyTitle);
        }
        return Component.text(SHIFT_LEFT_8).font(DEFAULT_FONT)
            .append(Component.text(glyph, NamedTextColor.WHITE).font(GUI_FONT));
    }

    public static Component battlePassTitle(int page) {
        int index = Math.max(0, Math.min(BATTLE_PASS_GLYPHS.length - 1, page - 1));
        return Component.text(SHIFT_LEFT_8).font(DEFAULT_FONT)
            .append(Component.text(BATTLE_PASS_GLYPHS[index], NamedTextColor.WHITE).font(GUI_FONT));
    }

    public static Component auctionHouseTitle(boolean hasPreviousPage, boolean hasNextPage) {
        String glyph;
        if (hasPreviousPage && hasNextPage) {
            glyph = AUCTION_MIDDLE_PAGE_GLYPH;
        } else if (hasPreviousPage) {
            glyph = AUCTION_LAST_PAGE_GLYPH;
        } else if (hasNextPage) {
            glyph = AUCTION_FIRST_PAGE_GLYPH;
        } else {
            glyph = AUCTION_SINGLE_PAGE_GLYPH;
        }
        return Component.text(SHIFT_LEFT_8).font(DEFAULT_FONT)
            .append(Component.text(glyph, NamedTextColor.WHITE).font(GUI_FONT));
    }

    private static String glyphForTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return null;
        }
        return switch (theme.toLowerCase()) {
            case "cosmetics" -> COSMETICS_GLYPH;
            case "hats" -> HATS_GLYPH;
            case "cosmetics_favorites" -> COSMETICS_FAVORITES_GLYPH;
            case "hydra_styles", "rank_styles" -> HYDRA_STYLES_GLYPH;
            case "kill_effects" -> KILL_EFFECTS_GLYPH;
            case "kill_messages" -> KILL_MESSAGES_GLYPH;
            case "recipes" -> RECIPES_GLYPH;
            case "recipes_legendary" -> RECIPES_LEGENDARY_GLYPH;
            case "recipes_crafting_utility" -> RECIPES_CRAFTING_UTILITY_GLYPH;
            case "recipes_crafting_legendary" -> RECIPES_CRAFTING_LEGENDARY_GLYPH;
            case "socials", "social" -> SOCIALS_GLYPH;
            case "weapon_skins", "legendary_skins" -> WEAPON_SKINS_GLYPH;
            case "trails", "projectile_trails" -> TRAILS_GLYPH;
            case "trims", "armor_trims" -> TRIMS_GLYPH;
            case "navigator_minigames", "minigames" -> NAVIGATOR_MINIGAMES_GLYPH;
            case "navigator_hunger_games" -> NAVIGATOR_HUNGER_GAMES_GLYPH;
            case "shard_open" -> SHARD_OPEN_GLYPH;
            case "shard_opening" -> SHARD_OPENING_GLYPH;
            case "friends_all" -> FRIENDS_ALL_GLYPH;
            case "friends_requests" -> FRIENDS_REQUESTS_GLYPH;
            case "friends_blocked" -> FRIENDS_BLOCKED_GLYPH;
            case "kit_editor" -> KIT_EDITOR_GLYPH;
            case "settings_page_1" -> SETTINGS_PAGE_ONE_GLYPH;
            case "settings_page_2" -> SETTINGS_PAGE_TWO_GLYPH;
            case "party_leader" -> PARTY_LEADER_GLYPH;
            case "party_member" -> PARTY_MEMBER_GLYPH;
            default -> null;
        };
    }
}
