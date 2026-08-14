package dev.glitg.core.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InterfaceThemeTest {
    @Test
    void mapsSemanticColoursOntoTheHousePalette() {
        assertEquals(
                "<#D4AF37>Gold</#D4AF37> <#AAA28F>copy</#AAA28F> <#72C38E>ready</#72C38E> <#DE6B63>danger</#DE6B63>",
                InterfaceTheme.apply("<gold>Gold</gold> <gray>copy</gray> <green>ready</green> <red>danger</red>"));
    }

    @Test
    void leavesExplicitColoursAndFormattingUntouched() {
        assertEquals(
                "<#123456><bold>Custom</bold></#123456>",
                InterfaceTheme.apply("<#123456><bold>Custom</bold></#123456>"));
    }

    @Test
    void supportsBritishColourAliases() {
        assertEquals(
                "<#AAA28F>soft</#AAA28F> <#625C50>dim</#625C50>",
                InterfaceTheme.apply("<grey>soft</grey> <dark_grey>dim</dark_grey>"));
    }
}
