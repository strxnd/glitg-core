package dev.glitg.core.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuiInputParserTest {
    @Test void validatesNumericInput() {
        assertEquals(12, GuiInputParser.nonNegativeInteger("12"));
        assertEquals(0.25, GuiInputParser.nonNegativeDouble("0.25"));
        assertThrows(IllegalArgumentException.class, () -> GuiInputParser.nonNegativeInteger("-1"));
        assertThrows(IllegalArgumentException.class, () -> GuiInputParser.nonNegativeDouble("NaN"));
    }

    @Test void normalizesDurationsAndDefinitionIds() {
        assertEquals("15s", GuiInputParser.duration("15S"));
        assertEquals("my-recipe_2", GuiInputParser.definitionId("My-Recipe_2"));
        assertThrows(IllegalArgumentException.class, () -> GuiInputParser.definitionId("bad id"));
    }

    @Test void parsesListsAndClearInput() {
        assertEquals(List.of("msg", "reply", "combat"), GuiInputParser.stringList("msg, reply, combat"));
        assertEquals(List.of(), GuiInputParser.stringList("clear"));
        assertEquals("", GuiInputParser.stringValue("CLEAR"));
    }
}
