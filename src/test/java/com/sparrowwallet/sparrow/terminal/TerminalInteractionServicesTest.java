package com.sparrowwallet.sparrow.terminal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalInteractionServicesTest {
    @Test
    public void linesAfterALineBreakWrapAtTheFullWidth() {
        String paragraph = "Consider using a node on this computer or your local network, connecting over a Tor onion address.";
        String formatted = TerminalInteractionServices.formatLines("A first line of forty characters or so.\n\n" + paragraph, 50);

        String[] lines = formatted.split("\n");
        assertEquals("A first line of forty characters or so.", lines[0]);
        assertEquals("", lines[1]);
        assertEquals("Consider using a node on this computer or your ", lines[2]);
        assertTrue(Arrays.stream(lines).allMatch(line -> line.length() < 50));
        assertEquals(5, lines.length);
    }

    @Test
    public void wrapsTextWithoutLineBreaksAtTheWidth() {
        String formatted = TerminalInteractionServices.formatLines("one two three four five six seven eight nine ten eleven twelve", 20);

        assertArrayEquals(new String[] {"one two three four ", "five six seven ", "eight nine ten ", "eleven twelve"}, formatted.split("\n"));
    }
}
