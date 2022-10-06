package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Panel;

import java.util.List;

public class MasterActionWindow extends BasicWindow {
    public MasterActionWindow(SparrowTerminal sparrowTerminal) {
        setHints(List.of(Hint.CENTERED));

        Panel panel = new Panel(new BorderLayout());

        MasterActionListBox masterActionListBox = new MasterActionListBox(sparrowTerminal);
        panel.addComponent(masterActionListBox, BorderLayout.Location.CENTER);

        setComponent(panel);
    }
}
