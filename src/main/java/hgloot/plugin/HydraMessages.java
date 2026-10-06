package hgloot.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import hgloot.plugin.util.HydraIcons;

public final class HydraMessages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static final TextColor HYDRA_DARK_TEAL = TextColor.color(0x0F3D3E);
    public static final TextColor HYDRA_DEEP_CYAN = TextColor.color(0x00A8A8);
    public static final TextColor HYDRA_NEON_CYAN = TextColor.color(0x00E0E0);
    public static final TextColor HYDRA_MUTED_AQUA = TextColor.color(0x4CB5AE);
    public static final TextColor HYDRA_DARK = TextColor.color(0x0A0F14);
    public static final TextColor HYDRA_SOFT_DARK = TextColor.color(0x121A22);

    public static final TextColor ERROR_RED = TextColor.color(0xFF4C4C);
    public static final TextColor WARNING_ORANGE = TextColor.color(0xFFCC44);
    public static final TextColor SUCCESS_GREEN = TextColor.color(0x4CFF88);
    public static final TextColor INFO_AQUA = HYDRA_NEON_CYAN;
    public static final TextColor INFO_BLUE = INFO_AQUA; // Compatibility for existing presentation callers.

    private HydraMessages() {
    }

    public static Component error(String message) {
        return Component.text(message, ERROR_RED);
    }

    public static Component warning(String message) {
        return Component.text(message, WARNING_ORANGE);
    }

    public static Component success(String message) {
        return Component.text(message, SUCCESS_GREEN);
    }

    public static Component info(String message) {
        return Component.text(message, INFO_AQUA);
    }

    public static Component cooldown(String label, int seconds) {
        return Component.text("Ability on cooldown.", ERROR_RED);
    }

    public static Component cooldown(String label, double seconds) {
        return Component.text("Ability on cooldown.", ERROR_RED);
    }

    public static Component ready(String label) {
        return success(label + " ready.");
    }

    public static Component activated(String label) {
        return success(label + " activated.");
    }

    public static Component charges(String label, int current, int max) {
        return info(label + ": " + current + "/" + max);
    }

    public static Component scopedInfo(String scope, String message) {
        return scoped(scope, info(message));
    }

    public static Component scopedWarning(String scope, String message) {
        return scoped(scope, warning(message));
    }

    public static Component scopedError(String scope, String message) {
        return scoped(scope, error(message));
    }

    public static Component scopedSuccess(String scope, String message) {
        return scoped(scope, success(message));
    }

    public static Component scoped(String scope, Component message) {
        return icon(HydraIcons.EXCLAMATION_CHAR, Component.text(scope + " ", HYDRA_DEEP_CYAN).append(message));
    }

    // ----- Icon-led messages (Hydra custom chat glyphs from the resource pack) -----

    public static Component icon(String rawIconChar, Component message) {
        return HydraIcons.component(rawIconChar).append(Component.space()).append(message);
    }

    public static Component iconError(String message) {
        return icon(HydraIcons.EXCLAMATION_RED_CHAR, error(message));
    }

    public static Component iconWarning(String message) {
        return icon(HydraIcons.EXCLAMATION_ORANGE_CHAR, warning(message));
    }

    public static Component iconNotice(String message) {
        return icon(HydraIcons.EXCLAMATION_CHAR, info(message));
    }

    public static Component iconLocked(String message) {
        return icon(HydraIcons.LOCKED_CHAR, error(message));
    }

    public static String legacy(Component component) {
        return LEGACY.serialize(component);
    }

}
