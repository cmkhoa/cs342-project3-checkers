import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Main JavaFX client application for Checkers.
 *
 * Scenes:
 *   "main"     – PLAY LOCAL / PLAY ONLINE / REGISTER
 *   "login"    – username entry for online play (existing account)
 *   "register" – username entry to create an account
 *   "matching" – waiting for an opponent
 *   "game"     – gameplay + chat
 */
public class GuiClient extends Application {

	// ── Window ────────────────────────────────────────────────────────────────
	private Stage  primaryStage;
	private static final int W = 600;  // window width
	private static final int H = 790;  // window height

	// ── Network ───────────────────────────────────────────────────────────────
	private Client  clientConnection;
	private boolean isOnline;

	// ── Player info ───────────────────────────────────────────────────────────
	private String myUsername    = "";
	private String opponentName  = "";
	private int    myPlayerNum   = 1;   // 1 = RED (bottom), 2 = BLACK (top)

	// ── Game state ────────────────────────────────────────────────────────────
	private CheckersLogic game;
	private int    selectedRow   = -1;
	private int    selectedCol   = -1;
	private List<int[]> legalMovesForSelected = new ArrayList<>();

	// ── Board canvas constants ────────────────────────────────────────────────
	private static final int CELL      = 60;   // pixels per cell
	private static final int BOARD_PX  = CELL * 8;  // 480

	// ── Live UI nodes (rebuilt per game scene) ────────────────────────────────
	private Canvas          boardCanvas;
	private Label           statusLabel;
	private Label           opponentLabel;
	private Label           redCountLabel;
	private Label           blackCountLabel;
	private ListView<String> chatListView;
	private boolean         gameOver = false;

	// ─────────────────────────────────────────────────────────────────────────
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
		showScene(buildMainScene());
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  SCENE BUILDERS
	// ══════════════════════════════════════════════════════════════════════════

	/** Main menu: PLAY LOCAL / PLAY ONLINE / REGISTER */
	private Scene buildMainScene() {
		VBox root = styledRoot();
		root.setAlignment(Pos.CENTER);
		root.setSpacing(0);

		Label title = titleLabel("CHECKERS");
		VBox.setMargin(title, new Insets(60, 0, 120, 0));

		Button playLocal   = menuButton("PLAY LOCAL");
		Button playOnline  = menuButton("PLAY ONLINE");
		Button register    = menuButton("REGISTER");

		VBox buttons = new VBox(12, playLocal, playOnline, register);
		buttons.setAlignment(Pos.CENTER);

		root.getChildren().addAll(title, buttons);

		playLocal.setOnAction(e -> startLocalGame());
		playOnline.setOnAction(e -> showScene(buildLoginScene(false)));
		register.setOnAction(e -> showScene(buildRegisterScene()));

		return new Scene(root, W, H);
	}

	/** Login screen (existing account → matching) */
	private Scene buildLoginScene(boolean registerMode) {
		VBox root = styledRoot();
		root.setAlignment(Pos.CENTER);

		Label title = titleLabel("CHECKERS");
		VBox.setMargin(title, new Insets(40, 0, 60, 0));

		TextField userField = styledTextField("Username");
		Button cancel  = menuButton("CANCEL");
		Button confirm = menuButton(registerMode ? "REGISTER" : "LOGIN");

		VBox form = new VBox(16, userField, new HBox(12, cancel, confirm));
		((HBox) form.getChildren().get(1)).setAlignment(Pos.CENTER);
		form.setAlignment(Pos.CENTER);
		form.setMaxWidth(360);

		root.getChildren().addAll(title, form);

		cancel.setOnAction(e -> showScene(buildMainScene()));
		confirm.setOnAction(e -> attemptConnect(userField.getText().trim(), registerMode));
		userField.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER)
				attemptConnect(userField.getText().trim(), registerMode);
		});

		return new Scene(root, W, H);
	}

	/** Register screen (new account → matching) */
	private Scene buildRegisterScene() {
		return buildLoginScene(true);
	}

	/** Matching / waiting-for-opponent screen */
	private Scene buildMatchingScene() {
		VBox root = styledRoot();
		root.setAlignment(Pos.CENTER);
		root.setSpacing(20);

		Label title  = titleLabel("CHECKERS");
		Label user   = new Label("Logged in as: " + myUsername);
		user.setFont(Font.font("Inter", 16));

		Label waiting = new Label("Waiting for opponent…");
		waiting.setFont(Font.font("Inter", FontWeight.NORMAL, 20));

		ProgressIndicator spinner = new ProgressIndicator();
		spinner.setPrefSize(50, 50);

		Button cancel = menuButton("CANCEL");
		cancel.setOnAction(e -> {
			if (clientConnection != null) {
				clientConnection.send(new Message(Message.Type.QUIT_GAME));
				clientConnection.disconnect();
				clientConnection = null;
			}
			isOnline = false;
			showScene(buildMainScene());
		});

		root.getChildren().addAll(title, user, spinner, waiting, cancel);
		return new Scene(root, W, H);
	}

	/** Main game screen: board + status + chat */
	private Scene buildGameScene() {
		// ── Top bar ────────────────────────────────────────────────────────
		Button backBtn = new Button("← Back");
		backBtn.setFont(Font.font("Inter", 14));
		backBtn.setStyle("-fx-background-color: transparent; -fx-border-color: black; " +
				"-fx-border-width: 1.5; -fx-cursor: hand; -fx-padding: 5 12;");
		backBtn.setOnAction(e -> confirmBack());

		opponentLabel = new Label(opponentName.isEmpty() ? "" : "vs " + opponentName);
		opponentLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 16));
		opponentLabel.setStyle("-fx-underline: true;");

		HBox topBar = new HBox(backBtn);
		topBar.setAlignment(Pos.CENTER_LEFT);
		topBar.setPadding(new Insets(10, 12, 8, 12));
		if (!opponentName.isEmpty()) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			topBar.getChildren().addAll(spacer, opponentLabel);
		}

		// ── Status row ─────────────────────────────────────────────────────
		statusLabel = new Label();
		statusLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 17));
		updateStatus();

		redCountLabel   = new Label();
		blackCountLabel = new Label();
		redCountLabel.setFont(Font.font("Inter", 13));
		blackCountLabel.setFont(Font.font("Inter", 13));
		updatePieceCountLabels();

		HBox statusRow = new HBox(16, redCountLabel, statusLabel, blackCountLabel);
		statusRow.setAlignment(Pos.CENTER);
		statusRow.setPadding(new Insets(0, 0, 6, 0));

		// ── Board ──────────────────────────────────────────────────────────
		boardCanvas = new Canvas(BOARD_PX, BOARD_PX);
		boardCanvas.setOnMouseClicked(e -> handleBoardClick(e.getX(), e.getY()));

		StackPane boardWrapper = new StackPane(boardCanvas);
		boardWrapper.setStyle("-fx-border-color: #222; -fx-border-width: 2;");
		boardWrapper.setMaxSize(BOARD_PX + 4, BOARD_PX + 4);
		boardWrapper.setPrefSize(BOARD_PX + 4, BOARD_PX + 4);

		HBox boardCenter = new HBox(boardWrapper);
		boardCenter.setAlignment(Pos.CENTER);
		boardCenter.setPadding(new Insets(0, 0, 8, 0));

		// ── Chat ───────────────────────────────────────────────────────────
		chatListView = new ListView<>();
		chatListView.setPrefHeight(150);
		chatListView.setStyle("-fx-font-size: 13; -fx-border-color: #222; -fx-border-width: 1.5; " +
				"-fx-background-radius: 10; -fx-border-radius: 10;");

		TextField chatInput = styledTextField("Enter message…");
		chatInput.setPrefWidth(440);
		Button sendBtn = new Button("Send");
		sendBtn.setFont(Font.font("Inter", 14));
		sendBtn.setStyle("-fx-background-color: #222; -fx-text-fill: white; " +
				"-fx-cursor: hand; -fx-padding: 8 16;");

		Runnable sendChat = () -> {
			String text = chatInput.getText().trim();
			if (text.isEmpty()) return;
			String line = myUsername.isEmpty() ? text : myUsername + ": " + text;
			chatListView.getItems().add(line);
			scrollChat();
			chatInput.clear();
			if (isOnline && clientConnection != null)
				clientConnection.send(new Message(Message.Type.CHAT, line));
		};
		sendBtn.setOnAction(e -> sendChat.run());
		chatInput.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) sendChat.run(); });

		HBox chatInputRow = new HBox(10, chatInput, sendBtn);
		chatInputRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(chatInput, Priority.ALWAYS);

		Label chatHeader = new Label("Chat");
		chatHeader.setFont(Font.font("Inter", FontWeight.BOLD, 14));

		VBox chatSection = new VBox(6, chatHeader, chatListView, chatInputRow);
		chatSection.setPadding(new Insets(0, 12, 12, 12));

		// ── Root layout ────────────────────────────────────────────────────
		VBox root = styledRoot();
		root.setSpacing(0);
		root.getChildren().addAll(topBar, statusRow, boardCenter, chatSection);
		VBox.setVgrow(chatSection, Priority.ALWAYS);

		drawBoard();
		return new Scene(root, W, H);
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  GAME FLOW
	// ══════════════════════════════════════════════════════════════════════════

	/** Start a local pass-and-play game (no network). */
	private void startLocalGame() {
		isOnline    = false;
		myUsername  = "Player 1";
		opponentName = "Player 2";
		myPlayerNum  = 1;
		initGame();
		showScene(buildGameScene());
	}

	/** Connect to server, register username, then enter matching queue. */
	private void attemptConnect(String username, boolean isRegister) {
		if (username.isEmpty()) {
			showAlert("Please enter a username.");
			return;
		}

		myUsername = username;
		isOnline   = true;

		// Show matching scene while connecting
		showScene(buildMatchingScene());

		// Connect in background
		clientConnection = new Client(this::handleServerMessage);
		clientConnection.start();

		// Give the socket a moment to open, then send REGISTER
		new Thread(() -> {
			try { Thread.sleep(400); } catch (InterruptedException ignored) {}
			Message msg = new Message(Message.Type.REGISTER, username);
			clientConnection.send(msg);
		}).start();
	}

	/** Initialise a fresh game (local or online). */
	private void initGame() {
		game     = new CheckersLogic();
		gameOver = false;
		selectedRow = selectedCol = -1;
		legalMovesForSelected = new ArrayList<>();
	}

	/** Start a new online game after GAME_START received. */
	private void startOnlineGame(String opponent, int playerNum) {
		opponentName = opponent;
		myPlayerNum  = playerNum;
		initGame();
		Platform.runLater(() -> {
			showScene(buildGameScene());
			opponentLabel.setText("vs " + opponent);
			updateStatus();
			drawBoard();
		});
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  NETWORK MESSAGE HANDLER
	// ══════════════════════════════════════════════════════════════════════════

	/** Called from Client thread — must push UI work to Platform.runLater. */
	private void handleServerMessage(Message msg) {
		switch (msg.type) {

			case REGISTER_OK:
				Platform.runLater(() -> showScene(buildMatchingScene()));
				break;

			case REGISTER_FAIL:
				Platform.runLater(() -> {
					showScene(buildRegisterScene());
					showAlert(msg.data != null ? msg.data : "Registration failed.");
				});
				break;

			case WAITING:
				Platform.runLater(() -> showScene(buildMatchingScene()));
				break;

			case GAME_START:
				startOnlineGame(msg.data, msg.playerNum);
				break;

			case MOVE:
				// Opponent's move — apply to local board
				Platform.runLater(() -> {
					if (game != null) {
						game.makeMove(msg.fromRow, msg.fromCol, msg.toRow, msg.toCol);
						// If opponent finished their jump, clear any mid-jump UI
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
					if (chatListView != null && msg.data != null) {
						chatListView.getItems().add(msg.data);
						scrollChat();
					}
				});
				break;

			case QUIT_GAME:
				Platform.runLater(() -> {
					String reason = (msg.data != null) ? msg.data : "Opponent disconnected";
					if (chatListView != null)
						chatListView.getItems().add("⚠ " + reason);
					showGameOverDialog("Opponent left the game.");
				});
				break;

			default:
				break;
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  BOARD INTERACTION
	// ══════════════════════════════════════════════════════════════════════════

	private void handleBoardClick(double x, double y) {
		if (game == null || game.isGameOver() || gameOver) return;

		// Online play: only act on your turn
		if (isOnline) {
			boolean myTurn = (myPlayerNum == 1) == game.isRedTurn();
			if (!myTurn) return;
		}

		int clickRow = (int) (y / CELL);
		int clickCol = (int) (x / CELL);
		if (clickRow < 0 || clickRow >= 8 || clickCol < 0 || clickCol >= 8) return;

		// If a destination is clicked for the currently selected piece
		if (selectedRow >= 0) {
			for (int[] dest : legalMovesForSelected) {
				if (dest[0] == clickRow && dest[1] == clickCol) {
					executeMove(selectedRow, selectedCol, clickRow, clickCol);
					return;
				}
			}
		}

		// If mid-jump, can only click the jumping piece's destinations
		if (game.isMidJump()) return;

		// Select a new piece
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

		// Send to server for online play
		if (isOnline && clientConnection != null) {
			Message msg = new Message(Message.Type.MOVE);
			msg.fromRow = fr; msg.fromCol = fc;
			msg.toRow   = tr; msg.toCol   = tc;
			clientConnection.send(msg);
		}

		// Update selection for multi-jump
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

	// ══════════════════════════════════════════════════════════════════════════
	//  BOARD RENDERING
	// ══════════════════════════════════════════════════════════════════════════

	private void drawBoard() {
		if (boardCanvas == null || game == null) return;
		GraphicsContext gc = boardCanvas.getGraphicsContext2D();
		int[][] board = game.getBoard();
		List<int[]> capturePieces = game.isGameOver() ? new ArrayList<>() : game.getPiecesWithCaptures();
		boolean showCaptures = game.anyCapture() && !game.isMidJump();

		for (int r = 0; r < 8; r++) {
			for (int c = 0; c < 8; c++) {
				double x = c * CELL, y = r * CELL;
				boolean darkSquare = (r + c) % 2 == 1;

				// ── Cell background ──────────────────────────────────────
				if (r == selectedRow && c == selectedCol) {
					gc.setFill(Color.web("#F6E27A")); // selected
				} else if (isLegalDestination(r, c)) {
					gc.setFill(darkSquare ? Color.web("#9ECB7A") : Color.web("#D4F0B0")); // highlight
				} else if (darkSquare) {
					gc.setFill(Color.web("#CCCCCC")); // dark board square
				} else {
					gc.setFill(Color.WHITE);
				}
				gc.fillRect(x, y, CELL, CELL);

				// ── Mandatory-capture indicator ──────────────────────────
				if (showCaptures && isCaptureRequired(capturePieces, r, c)) {
					gc.setStroke(Color.web("#E07700"));
					gc.setLineWidth(3);
					gc.strokeRect(x + 1.5, y + 1.5, CELL - 3, CELL - 3);
				}

				// ── Piece ────────────────────────────────────────────────
				int piece = board[r][c];
				if (piece != CheckersLogic.EMPTY) {
					drawPiece(gc, piece, x, y);
				}

				// ── Legal-move dot on empty destination ──────────────────
				if (isLegalDestination(r, c) && piece == CheckersLogic.EMPTY) {
					gc.setFill(Color.web("#4A7A30", 0.6));
					double ds = CELL * 0.25;
					gc.fillOval(x + (CELL - ds) / 2, y + (CELL - ds) / 2, ds, ds);
				}
			}
		}

		// ── Cell grid lines ───────────────────────────────────────────────
		gc.setStroke(Color.web("#AAAAAA", 0.5));
		gc.setLineWidth(0.5);
		for (int i = 0; i <= 8; i++) {
			gc.strokeLine(i * CELL, 0, i * CELL, BOARD_PX);
			gc.strokeLine(0, i * CELL, BOARD_PX, i * CELL);
		}
	}

	private void drawPiece(GraphicsContext gc, int piece, double cellX, double cellY) {
		double pad  = CELL * 0.10;
		double sz   = CELL - 2 * pad;
		double px   = cellX + pad;
		double py   = cellY + pad;

		// Shadow
		gc.setFill(Color.color(0, 0, 0, 0.18));
		gc.fillOval(px + 2, py + 3, sz, sz);

		// Piece body
		boolean isRed = (piece == CheckersLogic.RED || piece == CheckersLogic.RED_KING);
		gc.setFill(isRed ? Color.web("#C0392B") : Color.web("#222222"));
		gc.fillOval(px, py, sz, sz);

		// Inner highlight ring
		gc.setStroke(isRed ? Color.web("#E07060") : Color.web("#555555"));
		gc.setLineWidth(2);
		double inset = sz * 0.12;
		gc.strokeOval(px + inset, py + inset, sz - 2 * inset, sz - 2 * inset);

		// King crown — golden inner circle
		if (piece == CheckersLogic.RED_KING || piece == CheckersLogic.BLACK_KING) {
			double ks = sz * 0.38;
			gc.setFill(Color.web("#FFD700"));
			gc.fillOval(px + (sz - ks) / 2, py + (sz - ks) / 2, ks, ks);
			gc.setStroke(Color.web("#B8860B"));
			gc.setLineWidth(1.5);
			gc.strokeOval(px + (sz - ks) / 2, py + (sz - ks) / 2, ks, ks);
		}
	}

	private boolean isLegalDestination(int r, int c) {
		for (int[] m : legalMovesForSelected)
			if (m[0] == r && m[1] == c) return true;
		return false;
	}

	private boolean isCaptureRequired(List<int[]> list, int r, int c) {
		for (int[] pos : list)
			if (pos[0] == r && pos[1] == c) return true;
		return false;
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  STATUS UPDATES
	// ══════════════════════════════════════════════════════════════════════════

	private void updateStatus() {
		if (statusLabel == null || game == null) return;
		if (game.isGameOver()) {
			statusLabel.setText("Game Over");
			return;
		}
		String current;
		if (isOnline) {
			boolean myTurn = (myPlayerNum == 1) == game.isRedTurn();
			current = myTurn ? "Your turn" : opponentName + "'s turn";
		} else {
			current = game.isRedTurn() ? "Red's turn" : "Black's turn";
		}
		// Show mid-jump hint
		if (game.isMidJump()) current += " — must continue jump!";
		statusLabel.setText("STATUS: " + current);
	}

	private void updatePieceCountLabels() {
		if (redCountLabel == null || game == null) return;
		int[] counts = game.getPieceCounts();
		redCountLabel.setText("🔴 " + counts[0]);
		blackCountLabel.setText("⚫ " + counts[1]);
	}

	private void scrollChat() {
		if (chatListView != null && !chatListView.getItems().isEmpty())
			chatListView.scrollTo(chatListView.getItems().size() - 1);
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  GAME OVER
	// ══════════════════════════════════════════════════════════════════════════

	private void handleGameOver() {
		gameOver = true;
		updateStatus();
		drawBoard();

		String winnerColour = game.getWinner();
		String resultText;
		if ("DRAW".equals(winnerColour)) {
			resultText = "It's a draw!";
		} else if (isOnline) {
			boolean iWon = ("RED".equals(winnerColour) && myPlayerNum == 1)
					|| ("BLACK".equals(winnerColour) && myPlayerNum == 2);
			resultText = iWon ? "You win! 🎉" : "You lose.";
		} else {
			resultText = winnerColour + " wins!";
		}
		showGameOverDialog(resultText);
	}

	private void showGameOverDialog(String message) {
		Alert alert = new Alert(Alert.AlertType.NONE);
		alert.setTitle("Game Over");
		alert.setHeaderText(message);
		alert.setContentText("Would you like to play again?");

		ButtonType playAgain = new ButtonType("Play Again");
		ButtonType quit      = new ButtonType("Quit", ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(playAgain, quit);

		alert.showAndWait().ifPresent(btn -> {
			if (btn == playAgain) {
				if (isOnline && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.PLAY_AGAIN));
					showScene(buildMatchingScene());
				} else {
					startLocalGame();
				}
			} else {
				if (isOnline && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.QUIT_GAME));
					clientConnection.disconnect();
					clientConnection = null;
				}
				isOnline = false;
				showScene(buildMainScene());
			}
		});
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  NAVIGATION HELPERS
	// ══════════════════════════════════════════════════════════════════════════

	private void confirmBack() {
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
				"Leave the current game?", ButtonType.YES, ButtonType.NO);
		confirm.setTitle("Leave Game");
		confirm.setHeaderText("Return to main menu?");
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.YES) {
				if (isOnline && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.QUIT_GAME));
					clientConnection.disconnect();
					clientConnection = null;
				}
				isOnline = false;
				showScene(buildMainScene());
			}
		});
	}

	private void showScene(Scene scene) {
		primaryStage.setScene(scene);
		if (!primaryStage.isShowing()) primaryStage.show();
	}

	private void showAlert(String msg) {
		Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
		a.setHeaderText(null);
		a.setTitle("Checkers");
		a.showAndWait();
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  STYLE HELPERS
	// ══════════════════════════════════════════════════════════════════════════

	private VBox styledRoot() {
		VBox v = new VBox();
		v.setStyle("-fx-background-color: white;");
		v.setPrefSize(W, H);
		return v;
	}

	private Label titleLabel(String text) {
		Label l = new Label(text);
		l.setFont(Font.font("Inter", FontWeight.NORMAL, 42));
		l.setTextFill(Color.BLACK);
		return l;
	}

	private Button menuButton(String text) {
		Button b = new Button(text);
		b.setFont(Font.font("Inter", FontWeight.NORMAL, 22));
		b.setPrefSize(352, 62);
		b.setStyle("-fx-background-color: white; -fx-border-color: black; " +
				"-fx-border-width: 2; -fx-cursor: hand;");
		b.setOnMouseEntered(e -> b.setStyle("-fx-background-color: #F0F0F0; " +
				"-fx-border-color: black; -fx-border-width: 2; -fx-cursor: hand;"));
		b.setOnMouseExited(e -> b.setStyle("-fx-background-color: white; " +
				"-fx-border-color: black; -fx-border-width: 2; -fx-cursor: hand;"));
		return b;
	}

	private TextField styledTextField(String prompt) {
		TextField tf = new TextField();
		tf.setPromptText(prompt);
		tf.setFont(Font.font("Inter", 16));
		tf.setStyle("-fx-border-color: black; -fx-border-width: 1.5; " +
				"-fx-background-color: white; -fx-padding: 8;");
		tf.setPrefWidth(320);
		return tf;
	}
}
