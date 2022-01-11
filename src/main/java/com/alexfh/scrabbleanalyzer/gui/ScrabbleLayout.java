package com.alexfh.scrabbleanalyzer.gui;

import java.awt.*;

public class ScrabbleLayout extends GridBagLayout {

    @Override
    protected void arrangeGrid(Container parent) {
        if (parent instanceof ScrabblePanel scrabblePanel) {
            Dimension dimension = parent.getSize();

            scrabblePanel.onResize((int) dimension.getWidth(), (int) dimension.getHeight());
        }

        super.arrangeGrid(parent);
    }

}