package hgloot.plugin.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.RecipeChoice;
import org.junit.jupiter.api.Test;

class RecipeRegistrationParityValidatorTest {
    @Test
    void canonicalShapeRestoresPaperTrimmedTrailingSpaces() {
        assertEquals(List.of(" Q ", "QSQ", " Q "),
            RecipeRegistrationParityValidator.canonicalShape(new String[] {" Q", "QSQ", " Q"}, 3, 3));
    }

    @Test
    void canonicalShapeRejectsOversizedRows() {
        assertThrows(IllegalStateException.class,
            () -> RecipeRegistrationParityValidator.canonicalShape(new String[] {"TOO_WIDE"}, 3, 1));
    }

    @Test
    void canonicalShapeAcceptsPaperTrimmedOuterRowsAndColumns() {
        assertEquals(List.of("a", "b", "c"),
            RecipeRegistrationParityValidator.canonicalShape(new String[] {"a", "b", "c"}, 1, 3));
    }

    @Test
    void gildedLayoutAcceptsPaperRemappedSymbols() {
        Map<Character, RecipeChoice> choices = Map.of(
            'b', new RecipeChoice.MaterialChoice(Material.QUARTZ),
            'd', new RecipeChoice.MaterialChoice(Material.QUARTZ),
            'e', new RecipeChoice.MaterialChoice(Material.WOODEN_SWORD),
            'f', new RecipeChoice.MaterialChoice(Material.QUARTZ),
            'h', new RecipeChoice.MaterialChoice(Material.QUARTZ));

        RecipeRegistrationParityValidator.validateGildedLayout(
            Material.WOODEN_SWORD, new String[] {" b ", "def", " h "}, choices);
    }

    @Test
    void gildedLayoutRejectsWrongRemappedIngredient() {
        Map<Character, RecipeChoice> choices = Map.of(
            'b', new RecipeChoice.MaterialChoice(Material.QUARTZ),
            'd', new RecipeChoice.MaterialChoice(Material.QUARTZ),
            'e', new RecipeChoice.MaterialChoice(Material.STONE_SWORD),
            'f', new RecipeChoice.MaterialChoice(Material.QUARTZ),
            'h', new RecipeChoice.MaterialChoice(Material.QUARTZ));

        assertThrows(IllegalStateException.class, () -> RecipeRegistrationParityValidator.validateGildedLayout(
            Material.WOODEN_SWORD, new String[] {" b ", "def", " h "}, choices));
    }
}
