import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Main JavaFX client application for Checkers.
 *
 * Scenes are now built by dedicated scene-builder classes:
 *   MainScene, AuthScene, MatchingScene, GameScene,
 *   ProfileScene, FriendsScene
 *
 * This class owns all application state and wires scenes together.
 */
public class GuiClient extends Application {

	// ── Window ────────────────────────────────────────────────────────────
	private Stage primaryStage;

	// ── Network ───────────────────────────────────────────────────────────
	private Client  clientConnection;
	private boolean isOnline;

	// ── Player info ───────────────────────────────────────────────────────
	private String  myUsername   = "";
	private String  opponentName = "";
	private int     myPlayerNum  = 1;
	private boolean loggedIn     = false;
	private int     myWins       = 0;
	private int     myLosses     = 0;
	private final List<String> friendEntries = new ArrayList<>();

	// ── Game state ────────────────────────────────────────────────────────
	private CheckersLogic game;
	private int     selectedRow  = -1;
	private int     selectedCol  = -1;
	private List<int[]> legalMovesForSelected = new ArrayList<>();
	private boolean gameOver       = false;
	private boolean resultReported = false;

	// ── Live scene references ─────────────────────────────────────────────
	private GameScene activeGameScene;
	private String    awaitingProfileFor = null;

	// ─────────────────────────────────────────────────────────────────────
	public static void main(String[] args) { launch(args); }

	@Override
	public void start(Stage stage) {
		primaryStage = stage;
		primaryStage.setTitle("Checkers");
		primaryStage.setResizable(false);
		primaryStage.setOnCloseRequest((WindowEvent e) -> {
			if (clientConnection != null) clientConnection.disconnect();
			Platform.exit();
			System.exit(0);
		});
		showMain();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  SCENE NAVIGATION
	// ══════════════════════════════════════════════════════════════════════

	private void showMain() {
		awaitingProfileFor = null;
		primaryStage.setScene(MainScene.build(loggedIn, myUsername, new MainScene.Actions() {
			@Override public void onPlayLocal()  { startLocalGame(); }
			@Override public void onLogin()      { showAuth(false); }
			@Override public void onRegister()   { showAuth(true); }
			@Override public void onFindMatch()  { findMatchOnline(); }
			@Override public void onProfile()    { openProfileScene(myUsername); }
			@Override public void onFriends()    { openFriendsScene(); }
			@Override public void onLogout()     { doLogout(); }
		}));
		primaryStage.show();
	}

	private void showAuth(boolean registerMode) {
		primaryStage.setScene(AuthScene.build(registerMode,
			new AuthScene.Actions() {
				@Override public void onSubmit(String username) { attemptConnect(username, registerMode); }
				@Override public void onToggle()                { showAuth(!registerMode); }
				@Override public void onBack()                  { showMain(); }
			}));
	}

	private void showMatching() {
		awaitingProfileFor = null;
		primaryStage.setScene(MatchingScene.build(myUsername, () -> {
			if (clientConnection != null)
				clientConnection.send(new Message(Message.Type.QUIT_GAME));
			showMain();
		}));
	}

	private void showGame() {
		activeGameScene = new GameScene();
		primaryStage.setScene(activeGameScene.build(myUsername, opponentName, isOnline,
				new GameScene.Actions() {
					@Override public void onBoardClick(double x, double y) { handleBoardClick(x, y); }
					@Override public void onBack()    { confirmBack(); }
					@Override public void onForfeit() { confirmForfeit(); }
					@Override public void onSendChat(String text) { sendChat(text); }
					@Override public void onOpponentNameClick() { openProfileScene(opponentName); }
				}));
		drawBoard();
		updateStatus();
		updatePieceCountLabels();
	}

	private void openProfileScene(String target) {
		if (clientConnection == null) {
			if (target != null && target.equals(myUsername)) {
				primaryStage.setScene(ProfileScene.build(
						myUsername, myUsername, myWins, myLosses, false, false,
						buildProfileActions()));
			}
			return;
		}
		awaitingProfileFor = target;
		// Show placeholder immediately; USER_INFO fills it in
		primaryStage.setScene(ProfileScene.build(
				myUsername, target, -1, -1, false, isFriend(target),
				buildProfileActions()));
		clientConnection.send(new Message(Message.Type.GET_USER_INFO, target));
	}

	private ProfileScene.Actions buildProfileActions() {
		return new ProfileScene.Actions() {
			@Override public void onBack() { showMain(); }
			@Override public void onAddFriend(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.ADD_FRIEND, name));
				scheduleProfileRefresh(name);
			}
			@Override public void onRemoveFriend(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.REMOVE_FRIEND, name));
				scheduleProfileRefresh(name);
			}
		};
	}

	private void scheduleProfileRefresh(String target) {
		new Thread(() -> {
			try { Thread.sleep(250); } catch (InterruptedException ignored) {}
			Platform.runLater(() -> {
				awaitingProfileFor = target;
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.GET_USER_INFO, target));
			});
		}).start();
	}

	private void openFriendsScene() {
		awaitingProfileFor = null;
		primaryStage.setScene(FriendsScene.build(friendEntries, new FriendsScene.Actions() {
			@Override public void onBack() { showMain(); }
			@Override public void onAddFriend(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.ADD_FRIEND, name));
			}
			@Override public void onRemoveFriend(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.REMOVE_FRIEND, name));
			}
			@Override public void onViewProfile(String name) { openProfileScene(name); }
		}));
		if (clientConnection != null)
			clientConnection.send(new Message(Message.Type.GET_USER_INFO, myUsername));
	}

	// ══════════════════════════════════════════════════════════════════════
	//  GAME FLOW
	// ══════════════════════════════════════════════════════════════════════

	private void startLocalGame() {
		isOnline     = false;
		opponentName = "Player 2";
		myPlayerNum  = 1;
		initGame();
		showGame();
	}

	private void findMatchOnline() {
		if (clientConnection != null)
			clientConnection.send(new Message(Message.Type.PLAY_AGAIN));
		showMatching();
	}

	private void attemptConnect(String username, boolean isRegister) {
		if (username.isEmpty()) {
			showAlert("Please enter a username.");
			return;
		}
		myUsername = username;
		isOnline   = true;
		showMatching();

		if (clientConnection == null) {
			clientConnection = new Client(this::handleServerMessage);
			clientConnection.start();
		}

		final boolean reg = isRegister;
		new Thread(() -> {
			try { Thread.sleep(400); } catch (InterruptedException ignored) {}
			Message msg = new Message(reg ? Message.Type.REGISTER : Message.Type.LOGIN, username);
			clientConnection.send(msg);
		}).start();
	}

	private void doLogout() {
		if (clientConnection != null) {
			clientConnection.send(new Message(Message.Type.QUIT_GAME));
			clientConnection.disconnect();
			clientConnection = null;
		}
		loggedIn = false;
		isOnline = false;
		myUsername = "";
		myWins = 0;
		myLosses = 0;
		friendEntries.clear();
		showMain();
	}

	private void initGame() {
		game           = new CheckersLogic();
		gameOver       = false;
		resultReported = false;
		selectedRow = selectedCol = -1;
		legalMovesForSelected = new ArrayList<>();
	}

	private void startOnlineGame(String opponent, int playerNum) {
		opponentName = opponent;
		myPlayerNum  = playerNum;
		isOnline     = true;
		initGame();
		Platform.runLater(() -> {
			showGame();
			updateStatus();
			drawBoard();
		});
	}

	// ══════════════════════════════════════════════════════════════════════
	//  NETWORK MESSAGE HANDLER
	// ══════════════════════════════════════════════════════════════════════

	private void handleServerMessage(Message msg) {
		switch (msg.type) {

			case REGISTER_OK:
			case LOGIN_OK:
				loggedIn   = true;
				myUsername = msg.data != null ? msg.data : myUsername;
				// Navigate to main screen (logged-in state) after successful auth
				Platform.runLater(() -> showMain());
				break;

			case REGISTER_FAIL:
				Platform.runLater(() -> {
					showAuth(true);
					showAlert(msg.data != null ? msg.data : "Registration failed.");
				});
				break;

			case LOGIN_FAIL:
				Platform.runLater(() -> {
					showAuth(false);
					showAlert(msg.data != null ? msg.data : "Login failed.");
				});
				break;

			case WAITING:
				Platform.runLater(() -> {
					if (game == null || game.isGameOver() || gameOver)
						showMatching();
				});
				break;

			case GAME_START:
				startOnlineGame(msg.data, msg.playerNum);
				break;

			case MOVE:
				Platform.runLater(() -> {
					if (game != null) {
						game.makeMove(msg.fromRow, msg.fromCol, msg.toRow, msg.toCol);
						if (!game.isMidJump()) {
							selectedRow = selectedCol = -1;
							legalMovesForSelected = new ArrayList<>();
						}
						drawBoard();
						updateStatus();
						updatePieceCountLabels();
						if (game.isGameOver()) handleGameOver();
					}
				});
				break;

			case CHAT:
				Platform.runLater(() -> {
					if (activeGameScene != null && activeGameScene.chatListView != null && msg.data != null) {
						activeGameScene.chatListView.getItems().add(msg.data);
						activeGameScene.scrollChatToBottom();
					}
				});
				break;

			case GAME_OVER:
				Platform.runLater(() -> handleServerGameOver(msg.data));
				break;

			case QUIT_GAME:
				Platform.runLater(() -> {
					String reason = msg.data != null ? msg.data : "Opponent disconnected";
					if (activeGameScene != null && activeGameScene.chatListView != null)
						activeGameScene.chatListView.getItems().add("⚠ " + reason);
					if (game != null && !game.isGameOver() && !gameOver)
						showGameOverDialog("Opponent left the game.");
				});
				break;

			case USER_INFO:
				Platform.runLater(() -> {
					String who = msg.data;
					if (who == null) return;
					if (who.equals(myUsername)) {
						myWins   = msg.wins;
						myLosses = msg.losses;
					}
					if (who.equals(awaitingProfileFor)) {
						awaitingProfileFor = null;
						primaryStage.setScene(ProfileScene.build(
								myUsername, who, msg.wins, msg.losses, msg.online,
								isFriend(who), buildProfileActions()));
					}
				});
				break;

			case FRIEND_LIST:
				Platform.runLater(() -> {
					friendEntries.clear();
					if (msg.data != null && !msg.data.isEmpty()) {
						for (String part : msg.data.split(";")) {
							if (!part.trim().isEmpty()) friendEntries.add(part);
						}
					}
				});
				break;

			case FRIEND_ACTION_RESULT:
				Platform.runLater(() -> {
					if (msg.data != null) showAlert(msg.data);
				});
				break;

			default:
				break;
		}
	}

	private void handleServerGameOver(String winnerUsername) {
		if (gameOver) return;
		gameOver       = true;
		resultReported = true;
		updateStatus();
		drawBoard();

		String resultText;
		if ("DRAW".equalsIgnoreCase(winnerUsername)) {
			resultText = "It's a draw!";
		} else if (winnerUsername != null && winnerUsername.equals(myUsername)) {
			resultText = "You win! (Opponent left / forfeited)";
		} else if (winnerUsername != null) {
			resultText = "You lose.  (" + winnerUsername + " won)";
		} else {
			resultText = "Game over.";
		}
		showGameOverDialog(resultText);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  BOARD INTERACTION
	// ══════════════════════════════════════════════════════════════════════

	private void handleBoardClick(double x, double y) {
		if (game == null || game.isGameOver() || gameOver) return;
		if (isOnline) {
			boolean myTurn = (myPlayerNum == 1) == game.isRedTurn();
			if (!myTurn) return;
		}

		int clickRow = (int) (y / UI.CELL);
		int clickCol = (int) (x / UI.CELL);
		if (clickRow < 0 || clickRow >= 8 || clickCol < 0 || clickCol >= 8) return;

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

		if (isOnline && clientConnection != null) {
			Message msg = new Message(Message.Type.MOVE);
			msg.fromRow = fr; msg.fromCol = fc;
			msg.toRow   = tr; msg.toCol   = tc;
			clientConnection.send(msg);
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
		if (game.isGameOver()) handleGameOver();
	}

	private void sendChat(String text) {
		if (activeGameScene == null) return;
		String line = myUsername.isEmpty() ? text : myUsername + ": " + text;
		activeGameScene.chatListView.getItems().add(line);
		activeGameScene.scrollChatToBottom();
		if (isOnline && clientConnection != null)
			clientConnection.send(new Message(Message.Type.CHAT, line));
	}

	// ══════════════════════════════════════════════════════════════════════
	//  BOARD RENDERING
	// ══════════════════════════════════════════════════════════════════════

	private void drawBoard() {
		if (activeGameScene == null || activeGameScene.boardCanvas == null || game == null) return;
		GraphicsContext gc = activeGameScene.boardCanvas.getGraphicsContext2D();
		int[][] board = game.getBoard();
		List<int[]> capturePieces = game.isGameOver() ? new ArrayList<>() : game.getPiecesWithCaptures();
		boolean showCaptures = game.anyCapture() && !game.isMidJump();

		for (int r = 0; r < 8; r++) {
			for (int c = 0; c < 8; c++) {
				double x = c * UI.CELL, y = r * UI.CELL;
				boolean dark = (r + c) % 2 == 1;

				// Cell background
				if (r == selectedRow && c == selectedCol) {
					gc.setFill(Color.web("#D4EAC8"));  // selected — soft green
				} else if (isLegalDest(r, c)) {
					gc.setFill(dark ? Color.web("#9ECBA0") : Color.web("#C8E8CB"));
				} else if (dark) {
					gc.setFill(Color.web("#C8C3BB"));  // dark square
				} else {
					gc.setFill(Color.web("#F5F0E8"));  // light square
				}
				gc.fillRect(x, y, UI.CELL, UI.CELL);

				// Mandatory capture border
				if (showCaptures && isCaptureRequired(capturePieces, r, c)) {
					gc.setStroke(Color.web("#2D6A4F"));
					gc.setLineWidth(2.5);
					gc.strokeRect(x + 1.5, y + 1.5, UI.CELL - 3, UI.CELL - 3);
				}

				// Piece
				int piece = board[r][c];
				if (piece != CheckersLogic.EMPTY) drawPiece(gc, piece, x, y);

				// Move dot
				if (isLegalDest(r, c) && piece == CheckersLogic.EMPTY) {
					gc.setFill(Color.web("#2D6A4F", 0.55));
					double ds = UI.CELL * 0.28;
					gc.fillOval(x + (UI.CELL - ds) / 2, y + (UI.CELL - ds) / 2, ds, ds);
				}
			}
		}

		// Grid lines
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

		// Shadow
		gc.setFill(Color.color(0, 0, 0, 0.15));
		gc.fillOval(px + 2, py + 3, sz, sz);

		// Body
		boolean isRed = (piece == CheckersLogic.RED || piece == CheckersLogic.RED_KING);
		gc.setFill(isRed ? Color.web("#B83030") : Color.web("#1A1A1A"));
		gc.fillOval(px, py, sz, sz);

		// Inner ring
		gc.setStroke(isRed ? Color.web("#D96060") : Color.web("#444444"));
		gc.setLineWidth(1.5);
		double inset = sz * 0.13;
		gc.strokeOval(px + inset, py + inset, sz - 2 * inset, sz - 2 * inset);

		// King crown
		if (piece == CheckersLogic.RED_KING || piece == CheckersLogic.BLACK_KING) {
			double ks = sz * 0.36;
			gc.setFill(Color.web("#52B788"));
			gc.fillOval(px + (sz - ks) / 2, py + (sz - ks) / 2, ks, ks);
			gc.setStroke(Color.web("#2D6A4F"));
			gc.setLineWidth(1.2);
			gc.strokeOval(px + (sz - ks) / 2, py + (sz - ks) / 2, ks, ks);
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  STATUS / UI UPDATES
	// ══════════════════════════════════════════════════════════════════════

	private void updateStatus() {
		if (activeGameScene == null || activeGameScene.statusLabel == null || game == null) return;
		if (game.isGameOver() || gameOver) {
			activeGameScene.statusLabel.setText("GAME OVER");
			activeGameScene.statusLabel.getStyleClass().removeAll("status-bar-turn", "status-bar-wait");
			activeGameScene.statusLabel.getStyleClass().add("status-bar-text");
			return;
		}
		String current;
		boolean myTurn = !isOnline || ((myPlayerNum == 1) == game.isRedTurn());
		if (isOnline) {
			current = myTurn ? "YOUR TURN" : (opponentName.toUpperCase() + "'S TURN");
		} else {
			current = game.isRedTurn() ? "RED'S TURN" : "BLACK'S TURN";
		}
		if (game.isMidJump()) current += "  —  MUST JUMP";
		activeGameScene.statusLabel.setText(current);
		activeGameScene.statusLabel.getStyleClass().removeAll("status-bar-turn", "status-bar-wait", "status-bar-text");
		activeGameScene.statusLabel.getStyleClass().add(myTurn ? "status-bar-turn" : "status-bar-wait");
	}

	private void updatePieceCountLabels() {
		if (activeGameScene == null || game == null) return;
		int[] counts = game.getPieceCounts();
		if (activeGameScene.redCountLabel != null)
			activeGameScene.redCountLabel.setText("● " + counts[0]);
		if (activeGameScene.blackCountLabel != null)
			activeGameScene.blackCountLabel.setText(counts[1] + " ●");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  GAME OVER
	// ══════════════════════════════════════════════════════════════════════

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
			winnerUsername = iWon ? myUsername : opponentName;
		} else {
			resultText = winnerColour + " wins!";
		}

		if (isOnline && !resultReported && winnerUsername != null && clientConnection != null) {
			clientConnection.send(new Message(Message.Type.GAME_OVER, winnerUsername));
			resultReported = true;
		}

		showGameOverDialog(resultText);
	}

	private void showGameOverDialog(String message) {
		Alert alert = new Alert(Alert.AlertType.NONE);
		alert.setTitle("Game Over");
		alert.setHeaderText(message);
		alert.setContentText("What would you like to do?");

		ButtonType playAgain = new ButtonType("Play Again");
		ButtonType mainMenu  = new ButtonType("Main Menu", ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(playAgain, mainMenu);

		alert.showAndWait().ifPresent(btn -> {
			if (btn == playAgain) {
				if (loggedIn && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.PLAY_AGAIN));
					showMatching();
				} else {
					startLocalGame();
				}
			} else {
				isOnline = false;
				if (loggedIn && clientConnection != null)
					clientConnection.send(new Message(Message.Type.QUIT_GAME));
				showMain();
			}
		});
	}

	// ══════════════════════════════════════════════════════════════════════
	//  NAVIGATION HELPERS
	// ══════════════════════════════════════════════════════════════════════

	private void confirmBack() {
		boolean willForfeit = isOnline && game != null && !game.isGameOver() && !gameOver;
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
				willForfeit ? "Leaving will count as a forfeit." : "Return to main menu?",
				ButtonType.YES, ButtonType.NO);
		confirm.setTitle("Leave Game");
		confirm.setHeaderText(willForfeit ? "Forfeit and leave?" : "Return to main menu?");
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.YES) {
				if (willForfeit && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.FORFEIT));
					resultReported = true;
				} else if (isOnline && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.QUIT_GAME));
				}
				isOnline = false;
				showMain();
			}
		});
	}

	private void confirmForfeit() {
		if (!isOnline || game == null || game.isGameOver() || gameOver) return;
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
				"Forfeiting counts as a loss. Are you sure?",
				ButtonType.YES, ButtonType.NO);
		confirm.setTitle("Forfeit");
		confirm.setHeaderText("Forfeit this game?");
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.YES && clientConnection != null) {
				clientConnection.send(new Message(Message.Type.FORFEIT));
				resultReported = true;
			}
		});
	}

	private void showAlert(String msg) {
		Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
		a.setHeaderText(null);
		a.setTitle("Checkers");
		a.showAndWait();
	}

	// ── Helpers ───────────────────────────────────────────────────────────

	private boolean isFriend(String name) {
		for (String e : friendEntries)
			if (e.startsWith(name + "|")) return true;
		return false;
	}

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
}