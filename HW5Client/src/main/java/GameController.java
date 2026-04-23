import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import models.CheckersAI;
import models.CheckersLogic;
import models.Message;
import scenes.GameScene;
import scenes.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the active game: board rendering, click handling, move execution,
 * AI scheduling, status updates, and game-over flow.
 *
 * Delegates non-game actions (scene navigation, networking) back to GuiClient
 * via the {@link Host} callback interface.
 */
public class GameController {

    /** Callback interface so the controller can request actions from the host app. */
    public interface Host {
        void showMain();
        void showGame();
        void showMatching();
        void sendMessage(Message msg);
        void showAlert(String text);
        boolean isLoggedIn();
        boolean hasConnection();
        String getMyUsername();
        String getOpponentName();
        void closePendingAlert();
    }

    // ── State ──────────────────────────────────────────────────────────────
    private final Host host;

    CheckersLogic game;
    GameScene     activeGameScene;
    boolean       isOnline;
    boolean       isAI;
    boolean       gameOver;
    boolean       resultReported;
    int           myPlayerNum = 1;

    private CheckersAI   ai;
    private int          selectedRow = -1;
    private int          selectedCol = -1;
    private List<int[]>  legalMovesForSelected = new ArrayList<>();
    public  Alert        gameOverAlert;

    public GameController(Host host) {
        this.host = host;
    }

    // ── Game lifecycle ─────────────────────────────────────────────────────

    public void initGame() {
        game           = new CheckersLogic();
        gameOver       = false;
        resultReported = false;
        selectedRow = selectedCol = -1;
        legalMovesForSelected = new ArrayList<>();
        closeGameOverDialog();
    }

    public void configureLocal() {
        isOnline = false;
        isAI     = false;
        ai       = null;
    }

    public void configureAI() {
        isOnline = false;
        isAI     = true;
        ai       = new CheckersAI(5);
    }

    public void configureOnline(int playerNum) {
        isOnline    = true;
        isAI        = false;
        ai          = null;
        myPlayerNum = playerNum;
    }

    /** Call after showGame() for AI mode — BLACK moves first. */
    public void startAIFirstMove() {
        scheduleAIMove();
    }

    // ── Board interaction ──────────────────────────────────────────────────

    public void handleBoardClick(double x, double y) {
        if (game == null || game.isGameOver() || gameOver) return;
        if (isOnline) {
            boolean myTurn = (myPlayerNum == 1) == game.isRedTurn();
            if (!myTurn) return;
        }
        if (isAI && !game.isRedTurn()) return;

        int rawRow = (int) (y / UI.CELL);
        int rawCol = (int) (x / UI.CELL);
        if (rawRow < 0 || rawRow >= 8 || rawCol < 0 || rawCol >= 8) return;

        int clickRow = (isOnline && myPlayerNum == 2) ? 7 - rawRow : rawRow;
        int clickCol = (isOnline && myPlayerNum == 2) ? 7 - rawCol : rawCol;

        if (selectedRow >= 0) {
            for (int[] dest : legalMovesForSelected) {
                if (dest[0] == clickRow && dest[1] == clickCol) {
                    executeMove(selectedRow, selectedCol, clickRow, clickCol);
                    return;
                }
            }
        }

        if (game.isMidJump()) return;

        List<int[]> moves = game.getLegalMoves(clickRow, clickCol);
        if (!moves.isEmpty()) {
            selectedRow = clickRow;
            selectedCol = clickCol;
            legalMovesForSelected = moves;
        } else {
            selectedRow = selectedCol = -1;
            legalMovesForSelected = new ArrayList<>();
        }
        drawBoard();
    }

    private void executeMove(int fr, int fc, int tr, int tc) {
        game.makeMove(fr, fc, tr, tc);

        if (isOnline && host.hasConnection()) {
            Message msg = new Message(Message.Type.MOVE);
            msg.fromRow = fr; msg.fromCol = fc;
            msg.toRow   = tr; msg.toCol   = tc;
            host.sendMessage(msg);
        }

        if (game.isMidJump()) {
            selectedRow = game.getJumpingRow();
            selectedCol = game.getJumpingCol();
            legalMovesForSelected = game.getLegalMoves(selectedRow, selectedCol);
        } else {
            selectedRow = selectedCol = -1;
            legalMovesForSelected = new ArrayList<>();
        }

        drawBoard();
        updateStatus();
        updatePieceCountLabels();
        if (game.isGameOver()) {
            handleGameOver();
            return;
        }
        if (isAI && !game.isRedTurn() && !game.isMidJump()) {
            scheduleAIMove();
        }
    }

    // ── AI ──────────────────────────────────────────────────────────────────

    private void scheduleAIMove() {
        if (ai == null || game == null || game.isGameOver() || gameOver) return;
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            int[] move = ai.bestMove(game);
            if (move == null) return;
            Platform.runLater(() -> {
                if (game == null || game.isGameOver() || gameOver) return;
                game.makeMove(move[0], move[1], move[2], move[3]);

                while (game.isMidJump()) {
                    int jr = game.getJumpingRow(), jc = game.getJumpingCol();
                    List<int[]> caps = game.getLegalMoves(jr, jc);
                    if (caps.isEmpty()) break;
                    int[] cont = ai.bestMove(game);
                    if (cont != null) {
                        game.makeMove(cont[0], cont[1], cont[2], cont[3]);
                    } else {
                        break;
                    }
                }

                selectedRow = selectedCol = -1;
                legalMovesForSelected = new ArrayList<>();
                drawBoard();
                updateStatus();
                updatePieceCountLabels();
                if (game.isGameOver()) handleGameOver();
            });
        }).start();
    }

    // ── Board rendering ────────────────────────────────────────────────────

    public void drawBoard() {
        if (activeGameScene == null || activeGameScene.boardCanvas == null || game == null) return;
        GraphicsContext gc = activeGameScene.boardCanvas.getGraphicsContext2D();
        int[][] board = game.getBoard();
        List<int[]> capturePieces = game.isGameOver() ? new ArrayList<>() : game.getPiecesWithCaptures();
        boolean showCaptures = game.anyCapture() && !game.isMidJump();
        boolean flip = isOnline && myPlayerNum == 2;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int dr = flip ? 7 - r : r;
                int dc = flip ? 7 - c : c;
                double x = dc * UI.CELL, y = dr * UI.CELL;
                boolean dark = (dr + dc) % 2 == 1;

                if (r == selectedRow && c == selectedCol) {
                    gc.setFill(Color.web("#D4EAC8"));
                } else if (isLegalDest(r, c)) {
                    gc.setFill(dark ? Color.web("#9ECBA0") : Color.web("#C8E8CB"));
                } else if (dark) {
                    gc.setFill(Color.web("#C8C3BB"));
                } else {
                    gc.setFill(Color.web("#F5F0E8"));
                }
                gc.fillRect(x, y, UI.CELL, UI.CELL);

                if (showCaptures && isCaptureRequired(capturePieces, r, c)) {
                    gc.setStroke(Color.web("#2D6A4F"));
                    gc.setLineWidth(2.5);
                    gc.strokeRect(x + 1.5, y + 1.5, UI.CELL - 3, UI.CELL - 3);
                }

                int piece = board[r][c];
                if (piece != CheckersLogic.EMPTY) drawPiece(gc, piece, x, y);

                if (isLegalDest(r, c) && piece == CheckersLogic.EMPTY) {
                    gc.setFill(Color.web("#2D6A4F", 0.55));
                    double ds = UI.CELL * 0.28;
                    gc.fillOval(x + (UI.CELL - ds) / 2, y + (UI.CELL - ds) / 2, ds, ds);
                }
            }
        }

        gc.setStroke(Color.web("#B0ABA3", 0.4));
        gc.setLineWidth(0.5);
        for (int i = 0; i <= 8; i++) {
            gc.strokeLine(i * UI.CELL, 0, i * UI.CELL, UI.BOARD_PX);
            gc.strokeLine(0, i * UI.CELL, UI.BOARD_PX, i * UI.CELL);
        }
    }

    private void drawPiece(GraphicsContext gc, int piece, double cx, double cy) {
        double pad = UI.CELL * 0.11;
        double sz  = UI.CELL - 2 * pad;
        double px  = cx + pad, py = cy + pad;

        gc.setFill(Color.color(0, 0, 0, 0.15));
        gc.fillOval(px + 2, py + 3, sz, sz);

        boolean isRed = (piece == CheckersLogic.RED || piece == CheckersLogic.RED_KING);
        gc.setFill(isRed ? Color.web("#B83030") : Color.web("#1A1A1A"));
        gc.fillOval(px, py, sz, sz);

        gc.setStroke(isRed ? Color.web("#D96060") : Color.web("#444444"));
        gc.setLineWidth(1.5);
        double inset = sz * 0.13;
        gc.strokeOval(px + inset, py + inset, sz - 2 * inset, sz - 2 * inset);

        if (piece == CheckersLogic.RED_KING || piece == CheckersLogic.BLACK_KING) {
            double ks = sz * 0.36;
            gc.setFill(Color.web("#52B788"));
            gc.fillOval(px + (sz - ks) / 2, py + (sz - ks) / 2, ks, ks);
            gc.setStroke(Color.web("#2D6A4F"));
            gc.setLineWidth(1.2);
            gc.strokeOval(px + (sz - ks) / 2, py + (sz - ks) / 2, ks, ks);
        }
    }

    // ── Status updates ─────────────────────────────────────────────────────

    public void updateStatus() {
        if (activeGameScene == null || activeGameScene.statusLabel == null || game == null) return;
        if (game.isGameOver() || gameOver) {
            activeGameScene.statusLabel.setText("GAME OVER");
            activeGameScene.statusLabel.getStyleClass().removeAll("status-bar-turn", "status-bar-wait");
            activeGameScene.statusLabel.getStyleClass().add("status-bar-text");
            return;
        }
        String current;
        boolean myTurn;
        if (isOnline) {
            myTurn = (myPlayerNum == 1) == game.isRedTurn();
            current = myTurn ? "YOUR TURN" : (host.getOpponentName().toUpperCase() + "'S TURN");
        } else if (isAI) {
            myTurn = game.isRedTurn();
            current = myTurn ? "YOUR TURN" : "COMPUTER THINKING…";
        } else {
            myTurn = true;
            current = game.isRedTurn() ? "RED'S TURN" : "BLACK'S TURN";
        }
        if (game.isMidJump()) current += "  —  MUST JUMP";
        activeGameScene.statusLabel.setText(current);
        activeGameScene.statusLabel.getStyleClass().removeAll("status-bar-turn", "status-bar-wait", "status-bar-text");
        activeGameScene.statusLabel.getStyleClass().add(myTurn ? "status-bar-turn" : "status-bar-wait");
    }

    public void updatePieceCountLabels() {
        if (activeGameScene == null || game == null) return;
        int[] counts = game.getPieceCounts();
        if (activeGameScene.redCountLabel != null)
            activeGameScene.redCountLabel.setText("● " + counts[0]);
        if (activeGameScene.blackCountLabel != null)
            activeGameScene.blackCountLabel.setText(counts[1] + " ●");
    }

    // ── Game over ──────────────────────────────────────────────────────────

    public void handleServerGameOver(Message msg) {
        String winnerUsername = msg.data;
        if (gameOver) return;
        gameOver       = true;
        resultReported = true;
        updateStatus();
        drawBoard();

        int delta = msg.eloChange;
        String eloTag = delta != 0 ? "   (" + (delta >= 0 ? "+" : "") + delta + " Elo)" : "";

        String resultText;
        if ("DRAW".equalsIgnoreCase(winnerUsername)) {
            resultText = "It's a draw!" + eloTag;
        } else if (winnerUsername != null && winnerUsername.equals(host.getMyUsername())) {
            resultText = "You win! (Opponent left / forfeited)" + eloTag;
        } else if (winnerUsername != null) {
            resultText = "You lose.  (" + winnerUsername + " won)" + eloTag;
        } else {
            resultText = "Game over.";
        }
        showGameOverDialog(resultText);
    }

    private void handleGameOver() {
        gameOver = true;
        updateStatus();
        drawBoard();

        String winnerColour = game.getWinner();
        String resultText;
        String winnerUsername = null;

        if ("DRAW".equals(winnerColour)) {
            resultText    = "It's a draw!";
            winnerUsername = "DRAW";
        } else if (isOnline) {
            boolean iWon = ("RED".equals(winnerColour) && myPlayerNum == 1)
                    || ("BLACK".equals(winnerColour) && myPlayerNum == 2);
            resultText    = iWon ? "You win! 🎉" : "You lose.";
            winnerUsername = iWon ? host.getMyUsername() : host.getOpponentName();
        } else if (isAI) {
            boolean iWon = "RED".equals(winnerColour);
            resultText = iWon ? "You win! 🎉" : "Computer wins!";
        } else {
            resultText = winnerColour + " wins!";
        }

        if (isOnline && !resultReported && winnerUsername != null && host.hasConnection()) {
            host.sendMessage(new Message(Message.Type.GAME_OVER, winnerUsername));
            resultReported = true;
        }

        showGameOverDialog(resultText);
    }

    public void showGameOverDialog(String message) {
        closeGameOverDialog();
        gameOverAlert = new Alert(Alert.AlertType.NONE);
        gameOverAlert.setTitle("Game Over");
        gameOverAlert.setHeaderText(message);
        gameOverAlert.setContentText("What would you like to do?");

        ButtonType rematch = null;
        ButtonType playAgain;
        
        if (isOnline) {
            rematch = new ButtonType("Rematch");
            playAgain = new ButtonType("Find New Match");
        } else {
            playAgain = new ButtonType("Play Again");
        }
        
        ButtonType mainMenu = new ButtonType("Main Menu", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        if (isOnline) {
            gameOverAlert.getButtonTypes().setAll(rematch, playAgain, mainMenu);
        } else {
            gameOverAlert.getButtonTypes().setAll(playAgain, mainMenu);
        }

        final ButtonType finalRematch = rematch;

        gameOverAlert.showAndWait().ifPresent(btn -> {
            if (btn == finalRematch) {
                if (isOnline && host.isLoggedIn() && host.hasConnection()) {
                    host.sendMessage(new Message(Message.Type.CHALLENGE, host.getOpponentName()));
                    host.showAlert("Rematch request sent! Waiting for opponent to accept within 15 seconds...");
                }
            } else if (btn == playAgain) {
                if (isAI) {
                    configureAI();
                    initGame();
                    host.showGame();
                    startAIFirstMove();
                } else if (isOnline && host.isLoggedIn() && host.hasConnection()) {
                    host.sendMessage(new Message(Message.Type.PLAY_AGAIN));
                    host.showMatching();
                } else {
                    configureLocal();
                    initGame();
                    host.showGame();
                }
            } else if (btn == mainMenu) {
                isOnline = false;
                if (host.isLoggedIn() && host.hasConnection())
                    host.sendMessage(new Message(Message.Type.QUIT_GAME));
                host.showMain();
            }
            gameOverAlert = null;
        });
    }

    public void closeGameOverDialog() {
        if (gameOverAlert != null && gameOverAlert.isShowing()) {
            gameOverAlert.setResult(ButtonType.CLOSE);
            gameOverAlert.close();
            gameOverAlert = null;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private boolean isLegalDest(int r, int c) {
        for (int[] m : legalMovesForSelected)
            if (m[0] == r && m[1] == c) return true;
        return false;
    }

    private boolean isCaptureRequired(List<int[]> list, int r, int c) {
        for (int[] pos : list)
            if (pos[0] == r && pos[1] == c) return true;
        return false;
    }

    public void sendChat(String text) {
        if (activeGameScene == null) return;
        String username = host.getMyUsername();
        String line = username.isEmpty() ? text : username + ": " + text;
        activeGameScene.chatListView.getItems().add(line);
        activeGameScene.scrollChatToBottom();
        if (isOnline && host.hasConnection())
            host.sendMessage(new Message(Message.Type.CHAT, line));
    }
}
