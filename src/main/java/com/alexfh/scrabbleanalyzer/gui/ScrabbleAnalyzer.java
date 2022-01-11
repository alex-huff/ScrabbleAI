package com.alexfh.scrabbleanalyzer.gui;

import com.alexfh.scrabbleanalyzer.ScrabbleGame;
import com.alexfh.scrabbleanalyzer.gui.action.RevertableAction;
import com.alexfh.scrabbleanalyzer.gui.file.ScrabbleAnalyzerFileFilter;
import com.alexfh.scrabbleanalyzer.gui.tile.TileProvider;
import com.alexfh.scrabbleanalyzer.state.IScrabbleGameState;
import com.alexfh.scrabbleanalyzer.state.impl.ScrabbleGameStateImpl;
import com.alexfh.scrabbleanalyzer.state.impl.stream.SAInputStream;
import com.alexfh.scrabbleanalyzer.state.impl.stream.SAOutputStream;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Stack;

public class ScrabbleAnalyzer extends JFrame {

    private static final Dimension screenWidth = Toolkit.getDefaultToolkit().getScreenSize();
    public static final int defaultTileSize = (int) (ScrabbleAnalyzer.screenWidth.getHeight() * .75F / 15);

    private final Stack<RevertableAction> undoStack = new Stack<>();
    private final Stack<RevertableAction> redoStack = new Stack<>();
    private IScrabbleGameState gameState;
    private IScrabbleGameState lastSaveState;
    private final ScrabblePanel scrabblePanel;
    private final BufferedImage iconImage;
    private File saveFile;
    private final String title = "ScrabbleAnalyzer";

    public ScrabbleAnalyzer() {
        this.gameState = ScrabbleGameStateImpl.defaultBlankScrabbleGameState();

        this.setSaveFile();
        this.setLastSaveState();

        this.iconImage = TileProvider.INSTANCE.getTile(
            'a',
            true,
            false,
            false,
            50
        );

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setMinimumSize(new Dimension(400, 400));
        this.setIconImage(iconImage);
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.registerKeybindings();
        this.addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (ScrabbleAnalyzer.this.confirmationIfNotSaved("Are you sure you want to close without saving?")) {
                        ScrabbleGame.threadPool.shutdownNow();
                        System.exit(0);
                    }
                }
            }
        );

        JMenuBar menuBar = new JMenuBar();
        this.scrabblePanel = new ScrabblePanel(
            this::onAction,
            gameState
        );
        JMenu fileMenu = new JMenu("File");
        JMenu editMenu = new JMenu("Edit");
        JMenuItem newFile = new JMenuItem("New File (Ctrl+N)");
        JMenuItem open = new JMenuItem("Open");
        JMenuItem save = new JMenuItem("Save (Ctrl+S)");
        JMenuItem saveAs = new JMenuItem("Save As (Shift+Ctrl+S)");
        JMenuItem clearBoard = new JMenuItem("Clear Board");
        JMenuItem undo = new JMenuItem("Undo (Ctrl+Z)");
        JMenuItem redo = new JMenuItem("Redo (Ctrl+R)");

        newFile.addActionListener(e -> this.newFile());
        open.addActionListener(e -> this.open());
        save.addActionListener(e -> this.save());
        saveAs.addActionListener(e -> this.saveAs());
        clearBoard.addActionListener(e -> this.scrabblePanel.clearBoard());
        undo.addActionListener(e -> this.undo());
        redo.addActionListener(e -> this.redo());
        fileMenu.add(newFile);
        fileMenu.add(open);
        fileMenu.add(save);
        fileMenu.add(saveAs);
        editMenu.add(clearBoard);
        editMenu.add(undo);
        editMenu.add(redo);
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        this.setJMenuBar(menuBar);
        this.add(this.scrabblePanel);
        this.pack();
        this.setVisible(true);
    }

    private void registerKeybindings() {
        JPanel contentPane = ((JPanel) this.getContentPane());
        InputMap inputMap = contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = contentPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("ctrl Z"), "undo");
        actionMap.put(
            "undo",
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ScrabbleAnalyzer.this.undo();
                }
            }
        );
        inputMap.put(KeyStroke.getKeyStroke("ctrl R"), "redo");
        actionMap.put(
            "redo",
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ScrabbleAnalyzer.this.redo();
                }
            }
        );
        inputMap.put(KeyStroke.getKeyStroke("ctrl N"), "newFile");
        actionMap.put(
            "newFile",
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ScrabbleAnalyzer.this.newFile();
                }
            }
        );
        inputMap.put(KeyStroke.getKeyStroke("ctrl S"), "save");
        actionMap.put(
            "save",
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ScrabbleAnalyzer.this.save();
                }
            }
        );
        inputMap.put(KeyStroke.getKeyStroke("shift ctrl S"), "saveAs");
        actionMap.put(
            "saveAs",
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ScrabbleAnalyzer.this.saveAs();
                }
            }
        );
    }

    private boolean confirmationIfNotSaved(String message) {
        return
            this.isSaved() ||
            JOptionPane.showConfirmDialog(
                this,
                message,
                "Confirmation",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                new ImageIcon(this.iconImage)
            ) == JOptionPane.YES_OPTION;
    }

    private boolean isSaved() {
        return this.gameState.isEqualTo(this.lastSaveState);
    }

    private void onAction(RevertableAction revertableAction) {
        if (revertableAction.isNull()) return;

        this.redoStack.clear();
        this.undoStack.push(revertableAction);
    }

    private void undo() {
        if (this.undoStack.empty()) return;

        RevertableAction toUndo = this.undoStack.pop();

        toUndo.undo();
        this.redoStack.push(toUndo);
    }

    private void redo() {
        if (this.redoStack.empty()) return;

        RevertableAction toRedo = this.redoStack.pop();

        toRedo.redo();
        this.undoStack.push(toRedo);
    }

    private void setLastSaveState() {
        this.lastSaveState = this.gameState.copyScrabbleGame();
    }

    private void newFile() {
        if (!this.confirmationIfNotSaved("Are you sure you want to create a new file without saving?"))
            return;

        this.gameState = ScrabbleGameStateImpl.defaultBlankScrabbleGameState();

        this.setSaveFile();
        this.setLastSaveState();
        this.reloadGame();
    }

    private void open() {
        if (!this.confirmationIfNotSaved("Are you sure you want to open a new file without saving?"))
            return;

        try {
            this.openChooser();
        } catch (IOException e) {
            this.fileOpenErrorDialog();
        }
    }

    private void openChooser() throws IOException {
        JFileChooser fileChooser = new JFileChooser();

        fileChooser.setFileFilter(ScrabbleAnalyzerFileFilter.INSTANCE);
        fileChooser.setDialogTitle("Select a file to open");

        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        this.openFromFile(this.getSelectedFileFromChooser(fileChooser));
    }

    private void openFromFile(File fileToOpen) throws IOException {
        this.readFromFile(fileToOpen);
        this.reloadGame();
    }

    private void reloadGame() {
        this.undoStack.clear();
        this.redoStack.clear();
        System.gc();
        this.scrabblePanel.loadNewGame(this.gameState);
    }

    private void save() {
        if (this.saveFile == null) this.saveAs();
        else this.tryToSaveToFile(this.saveFile);
    }

    private void tryToSaveToFile(File file) {
        try {
            this.saveToFile(file);
        } catch (IOException e) {
            this.fileSaveErrorDialog();
        }
    }

    private void fileOpenErrorDialog() {
        JOptionPane.showMessageDialog(this, "Could not open file");
    }

    private void fileSaveErrorDialog() {
        JOptionPane.showMessageDialog(this, "Could not save file");
    }

    private void saveAs() {
        JFileChooser fileChooser = new JFileChooser();

        fileChooser.setFileFilter(ScrabbleAnalyzerFileFilter.INSTANCE);
        fileChooser.setDialogTitle("Select a file to save");

        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File fileToSave = this.getSelectedFileFromChooser(fileChooser);

        this.tryToSaveToFile(fileToSave);
    }

    private File getSelectedFileFromChooser(JFileChooser fileChooser) {
        File selectedFile = fileChooser.getSelectedFile();

        if (selectedFile.exists() ||
            !(fileChooser.getFileFilter() instanceof ScrabbleAnalyzerFileFilter)
        ) return selectedFile;

        return new File(selectedFile.getAbsolutePath() + ScrabbleAnalyzerFileFilter.EXTENSION);
    }

    private void setSaveFile() {
        this.saveFile = null;

        this.setTitle(this.title + " [" + "Untitled]");
    }

    private void setSaveFile(File file) {
        this.saveFile = file;

        this.setTitle(this.title + " [" + file.getName() + "]");
    }

    private void saveToFile(File file) throws IOException {
        FileOutputStream fileOut = new FileOutputStream(file);
        SAOutputStream saOutputStream = new SAOutputStream(fileOut);

        saOutputStream.writeScrabbleGameState(this.gameState);
        saOutputStream.close();
        this.setLastSaveState();
        this.setSaveFile(file);
    }

    private void readFromFile(File file) throws IOException {
        FileInputStream fileIn = new FileInputStream(file);
        SAInputStream saInputStream = new SAInputStream(fileIn);
        this.gameState = saInputStream.readGameState();

        saInputStream.close();
        this.setLastSaveState();
        this.setSaveFile(file);
    }

}