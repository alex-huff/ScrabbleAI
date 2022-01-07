package com.alexfh.scrabblesolver.gui;

import com.alexfh.scrabblesolver.ScrabbleGame;
import com.alexfh.scrabblesolver.gui.tile.TileProvider;
import com.alexfh.scrabblesolver.state.IScrabbleBoard;
import com.alexfh.scrabblesolver.util.ScrabbleUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ScrabbleGrid extends JPanel {

    private final TileLabel[][] labels = new TileLabel[15][15];
    private int tileSize = ScrabbleFrame.defaultTileSize;
    private final Dimension size = new Dimension(this.tileSize * 15, this.tileSize * 15);
    private final IScrabbleBoard board;
    private final Runnable onMovesInvalidated;
    private int cursorR = 0;
    private int cursorC = 0;
    private boolean cursorJustSet = false;
    private boolean wasLastMovementForwardVert = false;
    private boolean wasLastMovementForwardHori = false;
    private final char[][] playedWordPreviewChars = IScrabbleBoard.getNewEmptyBoard(15, 15);
    private ScrabbleGame.Move previewedMove;

    public ScrabbleGrid(IScrabbleBoard board, Runnable onMovesInvalidated) {
        this.board = board;
        this.onMovesInvalidated = onMovesInvalidated;

        this.setLayout(new GridLayout(15, 15));

        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                int finalR = r;
                int finalC = c;
                TileLabel label = new TileLabel(
                    new ImageIcon(this.getTileAt(r, c)),
                    isLeft -> this.onTileClicked(finalR, finalC, isLeft),
                    this::onCharPressed
                );
                labels[r][c] = label;

                this.add(label);
            }
        }
    }

    public void showMove(ScrabbleGame.Move move) {
        this.clearSelectedMove();

        this.previewedMove = move;
        ScrabbleGame.Offset offset = this.previewedMove.isVertical() ? ScrabbleGame.vertOffset : ScrabbleGame.horiOffset;
        int startRow = this.previewedMove.row();
        int startCol = this.previewedMove.col();

        for (int i = 0; i < this.previewedMove.playedTiles().length; i++) {
            char placedChar = this.previewedMove.playedTiles()[i];
            int spotInWord = this.previewedMove.tileSpotsInWord()[i];
            int newRow = offset.newRow(startRow, spotInWord);
            int newCol = offset.newCol(startCol, spotInWord);
            char toPlace;

            if (placedChar == ScrabbleUtil.wildCardTile) {
                toPlace = this.previewedMove.playedWord().charAt(spotInWord);
            } else {
                toPlace = Character.toUpperCase(placedChar);
            }

            this.playedWordPreviewChars[newRow][newCol] = toPlace;

            this.updateAndRepaintTileAt(newRow, newCol);
        }
    }

    public void clearSelectedMove() {
        if (this.previewedMove == null) return;

        ScrabbleGame.Offset offset = this.previewedMove.isVertical() ? ScrabbleGame.vertOffset : ScrabbleGame.horiOffset;
        int startRow = this.previewedMove.row();
        int startCol = this.previewedMove.col();

        for (int i = 0; i < this.previewedMove.playedTiles().length; i++) {
            int spotInWord = this.previewedMove.tileSpotsInWord()[i];
            int newRow = offset.newRow(startRow, spotInWord);
            int newCol = offset.newCol(startCol, spotInWord);
            this.playedWordPreviewChars[newRow][newCol] = IScrabbleBoard.emptyMarker;

            this.updateAndRepaintTileAt(newRow, newCol);
        }
    }

    private void updateAndRepaintTileAtCursor() {
        this.updateAndRepaintTileAt(this.cursorR, this.cursorC);
    }

    private void updateAndRepaintTileAt(int r, int c) {
        this.labels[r][c].getIcon().setImage(this.getTileAt(r, c));
        this.labels[r][c].repaint();
    }

    private void onCharPressed(Character character, boolean isShiftDown) {
        if (character == ScrabbleUtil.wildCardMarker) return;

        if (character == ScrabblePanel.backspaceChar) {
            if (this.cursorJustSet || (isShiftDown && this.cursorR > 0) || (!isShiftDown && this.cursorC > 0)) {
                if (!this.cursorJustSet) {
                    if (isShiftDown) {
                        if (this.wasLastMovementForwardHori) {
                            this.cursorC--;
                        } else {
                            this.cursorR--;
                        }
                    } else {
                        if (this.wasLastMovementForwardVert) {
                            this.cursorR--;
                        } else {
                            this.cursorC--;
                        }
                    }

                    this.wasLastMovementForwardVert = false;
                    this.wasLastMovementForwardHori = false;
                }

                if (!this.board.isEmptyAt(this.cursorR, this.cursorC)) {
                    this.board.removeCharAt(this.cursorR, this.cursorC);
                    this.updateAndRepaintTileAtCursor();
                    this.onMovesInvalidated.run();
                }
            }
        } else {
            if ((isShiftDown && this.cursorR < 15) || (!isShiftDown && this.cursorC < 15)) {
                if (isShiftDown) {
                    if (this.wasLastMovementForwardHori) {
                        if (this.cursorR == 14) return;

                        this.cursorR++;
                        this.cursorC--;
                    }

                    this.wasLastMovementForwardVert = true;
                    this.wasLastMovementForwardHori = false;
                } else {
                    if (this.wasLastMovementForwardVert) {
                        if (this.cursorC == 14) return;

                        this.cursorR--;
                        this.cursorC++;
                    }

                    this.wasLastMovementForwardHori = true;
                    this.wasLastMovementForwardVert = false;
                }

                if (
                    !(
                        !this.board.isEmptyAt(this.cursorR, this.cursorC) &&
                            (this.board.getCharAt(this.cursorR, this.cursorC) == character) &&
                            (!this.board.isWildcardAt(this.cursorR, this.cursorC))
                    )
                ) {
                    this.board.setCharAt(this.cursorR, this.cursorC, character);
                    this.board.setWildcardAt(this.cursorR, this.cursorC, false);
                    this.updateAndRepaintTileAtCursor();
                    this.onMovesInvalidated.run();
                }

                if (isShiftDown)
                    this.cursorR++;
                else
                    this.cursorC++;
            }
        }

        this.cursorJustSet = false;
    }

    private void onTileClicked(int r, int c, boolean isLeft) {
        if (isLeft) {
            this.cursorR = r;
            this.cursorC = c;
            this.cursorJustSet = true;
            this.wasLastMovementForwardVert = false;
            this.wasLastMovementForwardHori = false;
        } else {
            if (!this.board.isEmptyAt(r, c)) {
                this.board.setWildcardAt(r, c, !this.board.isWildcardAt(r, c));
                this.updateAndRepaintTileAt(r, c);
                this.onMovesInvalidated.run();
            }
        }
    }

    public IScrabbleBoard getBoardCopy() {
        return this.board.copy();
    }

    private BufferedImage getTileAt(int r, int c) {
        if (this.board.isEmptyAt(r, c)) {
            char previewChar = this.playedWordPreviewChars[r][c];

            if (previewChar != IScrabbleBoard.emptyMarker) {
                char previewCharLower;
                boolean isWild;

                if (Character.isUpperCase(previewChar)) {
                    previewCharLower = Character.toLowerCase(previewChar);
                    isWild = false;
                } else {
                    previewCharLower = previewChar;
                    isWild = true;
                }

                return TileProvider.INSTANCE.getTile(
                    previewCharLower,
                    isWild,
                    true,
                    true,
                    this.tileSize
                );
            }

            return TileProvider.INSTANCE.getBlankTile(
                this.board.getLetterMultiplierAt(r, c),
                this.board.getWordMultiplierAt(r, c),
                this.tileSize
            );
        }

        return TileProvider.INSTANCE.getTile(
            this.board.getCharAt(r, c),
            this.board.isWildcardAt(r, c),
            true,
            false,
            this.tileSize
        );
    }

    public void newSize(int newTileSize) {
        this.tileSize = newTileSize;

        this.size.setSize(this.tileSize * 15, this.tileSize * 15);

        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                this.labels[r][c].getIcon().setImage(this.getTileAt(r, c));
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return this.size;
    }

}