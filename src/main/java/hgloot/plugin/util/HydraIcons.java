package hgloot.plugin.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;

/**
 * Custom chat glyphs supplied by the Hydra resource pack, plus the plain-text
 * Hydra notice marker.
 *
 * <p>The remaining icon PNGs are registered as bitmap providers in the pack's
 * {@code assets/minecraft/font/default.json} (textures under
 * {@code assets/hydra/textures/font/icon/}). Because they live in the
 * {@code minecraft:default} font, the Private-Use characters below render
 * inline in ANY message — including legacy {@code §}-coded strings sent via
 * {@code player.sendMessage(String)} — with no need to build Adventure
 * components. The leading {@link ChatColor#WHITE} keeps the colored glyph from
 * being tinted by whatever color precedes it (white = no multiply).</p>
 *
 * <p>Glyph art is themed/colored already, so do not recolor the icon itself.
 * The generic exclamation variants now share a text marker and aqua prefix;
 * the message body carries success, caution, or error color.</p>
 *
 * <p>If a remaining glyph looks mis-sized in chat, tune {@code ascent}/{@code
 * height} in {@code default.json} rather than changing message formatting.</p>
 */
public final class HydraIcons {

    /** Font the glyphs live in (the merged default font). */
    public static final Key FONT = Key.key("minecraft", "default");

    /** Raw Private-Use codepoints (no color), for building Components. */
    public static final String EXCLAMATION_CHAR = "[!]";
    public static final String EXCLAMATION_ORANGE_CHAR = EXCLAMATION_CHAR;
    public static final String EXCLAMATION_RED_CHAR = EXCLAMATION_CHAR;
    public static final String LOCKED_CHAR = "";             // locked mode
    public static final String SHARD_CHAR = "";              // Shard item artwork

    public static final String[] SPONGE_SABER_WATER_BAR_CHARS = {
        "\uE300", "\uE301", "\uE302", "\uE303", "\uE304", "\uE305", "\uE306",
        "\uE307", "\uE308", "\uE309", "\uE30A", "\uE30B", "\uE30C"
    };

    /** Legacy-string ready notice marker; old variant names remain as aliases. */
    public static final String EXCLAMATION = ChatColor.AQUA + EXCLAMATION_CHAR;
    public static final String EXCLAMATION_ORANGE = EXCLAMATION;
    public static final String EXCLAMATION_RED = EXCLAMATION;
    public static final String LOCKED = ChatColor.WHITE + LOCKED_CHAR;
    public static final String SHARD = ChatColor.WHITE + SHARD_CHAR;

    private HydraIcons() {
    }

    /** Icon followed by a single space, for use as a message prefix. */
    public static String prefix(String icon) {
        return icon + " ";
    }

    /** Adventure component for a notice marker or remaining resource-pack glyph. */
    public static Component component(String rawChar) {
        return Component.text(rawChar).color(rawChar.equals(EXCLAMATION_CHAR)
            ? net.kyori.adventure.text.format.NamedTextColor.AQUA
            : net.kyori.adventure.text.format.NamedTextColor.WHITE);
    }

    /** Adventure component for the Sponge Saber 0-12 water meter bar. */
    public static Component spongeSaberWaterBar(int water) {
        int clamped = Math.max(0, Math.min(SPONGE_SABER_WATER_BAR_CHARS.length - 1, water));
        return Component.text(SPONGE_SABER_WATER_BAR_CHARS[clamped])
            .font(FONT)
            .color(net.kyori.adventure.text.format.NamedTextColor.WHITE);
    }
}
