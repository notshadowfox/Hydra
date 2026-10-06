package dev.zxdzero.ZxdzeroEvents.tooltip;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public enum Tooltip {
    LEFT_CLICK("\uE100", " Left-click ", TextColor.color(0x85cc16)),
    RIGHT_CLICK("\uE101", " Right-click ", TextColor.color(0x0099db)),
    SHIFT_LEFT_CLICK("\uE207 \uE100", " Shift-click ", TextColor.color(0x85cc16)),
    SHIFT_RIGHT_CLICK("\uE205 \uE101", " Shift-click ", TextColor.color(0x0099db)),
    SHIFT_CLICK("\uE203 \uE102", " Shift-click ", TextColor.color(0xf49e0b));

    private final String icon;
    private final String action;
    private final TextColor textColor;

    Tooltip(String icon, String action, TextColor textColor) {
        this.icon = icon;
        this.action = action;
        this.textColor = textColor;
    }

    public Component toComponent(String text) {
        Component component = Component.text(icon).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false).font(Key.key("tooltip:default"));
        Component component1 = Component.text(action).color(textColor).decoration(TextDecoration.ITALIC, false).font(Key.key("minecraft:default"));
        Component component2 = Component.text(text).color(textColor).decoration(TextDecoration.ITALIC, false).font(Key.key("minecraft:default"));
        return component.append(component1).append(component2);
    }
}
