package hgloot.plugin.ultraroyales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegendaryItemLoreTest {
    private static final Path ITEMS = Path.of("src/main/java/hgloot/plugin/ultraroyales/Items.java");

    @Test
    void authoritativeItemsContainTheApprovedDescriptionsAndColors() throws Exception {
        String source = Files.readString(ITEMS);

        assertLore(source, "A venomous staff that marks enemies for your spiders.", "GRAY");
        assertLore(source, "RIGHT CLICK to release a Venom Wave, poisoning and slowing enemies.", "RED");
        assertLore(source, "A heavy bow with slower, more powerful arrows.", "GRAY");
        assertLore(source, "3 Fractures SHATTER the target, dealing bonus damage and a brief debuff.", "RED");
        assertLore(source, "A water-powered saber that grows stronger near water.", "GRAY");
        assertLore(source, "SNEAK + RIGHT CLICK to absorb nearby water faster.", "GREEN");
        assertLore(source, "A powerful hook built to drag enemies out of position.", "GRAY");
        assertLore(source, "Hook the same target again within 20s to trigger DEEP HOOK.", "RED");
        assertLore(source, "Dealing bonus damage with a stronger pull and reveal.", "RED");
        assertLore(source, "Summons a warhorse built for devastating charges.", "GRAY");
        assertLore(source, "RIGHT CLICK to summon your warhorse and begin charging.", "GREEN");
        assertLore(source, "A storm-powered conch that shocks nearby enemies.", "GRAY");
        assertLore(source, "RIGHT CLICK to activate Storm Aura around yourself.", "YELLOW");
        assertLore(source, "A vicious blade that hunts down a single marked target.", "GRAY");
        assertLore(source, "Killing your marked target heals you.", "GREEN");
    }

    @Test
    void hungerGamesPreviewDoesNotOverwriteSpiderStaffLoreByIndex() throws Exception {
        String source = Files.readString(ITEMS);
        int methodStart = source.indexOf("private static List<Component> buildSpiderStaffHgLore");
        int methodEnd = source.indexOf("private static List<Component> buildSpongeSaberHgLore", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("new ArrayList<>(original)"));
        assertTrue(!method.contains("replaceLineAt"));
    }

    private static void assertLore(String source, String text, String color) {
        assertTrue(
            source.contains("loreLine(\"" + text + "\", NamedTextColor." + color + ")"),
            () -> "Missing " + color + " lore: " + text
        );
    }
}
