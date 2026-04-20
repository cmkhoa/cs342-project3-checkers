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
// CHANGED (scenes list): added "profile" (user-info panel) and "friends" scenes
//   below, and the main menu's buttons swap once logged in (persistent login).
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
	// loggedIn tracks whether the user has an authenticated server session.
	// Once true, the main menu shows profile/friends buttons instead of
	// REGISTER/LOGIN (persistent login between games).
	private boolean loggedIn     = false;

	// cached user-info & friends state (updated via server pushes)
	private int    myWins   = 0;
	private int    myLosses = 0;
	/** Each entry: "name|online|wins|losses" */
	private List<String> friendEntries = new ArrayList<>();

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
	// ensures we only send GAME_OVER to the server once per game, even
	// if the logic engine reports it a second time (edge-case safety).
	private boolean         resultReported = false;

	// live UI state for the friends panel + the profile-refresh guard.
	private ListView<String> friendsListView;
	/**
	 * When non-null, a USER_INFO response for this username should refresh the
	 * profile scene. Prevents server-pushed USER_INFO (e.g. after forfeit /
	 * game end) from hijacking the current scene.
	 */
	private String awaitingProfileFor = null;

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
	// CHANGED: the menu now has two modes — logged-out (original buttons) and
	//          logged-in (PLAY LOCAL / FIND MATCH / MY PROFILE / FRIENDS / LOG OUT).
	private Scene buildMainScene() {
		// clear any pending profile request so a stale server response
		// can't bounce us back to the profile scene.
		awaitingProfileFor = null;
		VBox root = styledRoot();
		root.setAlignment(Pos.CENTER);
		root.setSpacing(0);

		Label title = titleLabel("CHECKERS");
		VBox.setMargin(title, new Insets(60, 0, 120, 0));

		// logged-in variant — shows the signed-in username and extra menu items.
		if (loggedIn) {
			VBox.setMargin(title, new Insets(40, 0, 30, 0));
			Label who = new Label("Logged in as: " + myUsername);
			who.setFont(Font.font("Inter", FontWeight.BOLD, 18));
			VBox.setMargin(who, new Insets(0, 0, 30, 0));

			Button playLocal  = menuButton("PLAY LOCAL");
			Button findMatch  = menuButton("FIND MATCH");
			Button profile    = menuButton("MY PROFILE");
			Button friends    = menuButton("FRIENDS");
			Button logout     = menuButton("LOG OUT");

			playLocal.setOnAction(e -> startLocalGame());
			findMatch.setOnAction(e -> {
				if (clientConnection != null) clientConnection.send(new Message(Message.Type.PLAY_AGAIN));
				showScene(buildMatchingScene());
			});
			profile.setOnAction(e -> openProfileScene(myUsername));
			friends.setOnAction(e -> openFriendsScene());
			logout.setOnAction(e -> doLogout());

			VBox buttons = new VBox(12, playLocal, findMatch, profile, friends, logout);
			buttons.setAlignment(Pos.CENTER);

			root.getChildren().addAll(title, who, buttons);
			return new Scene(root, W, H);
		}

		Button playLocal   = menuButton("PLAY LOCAL");
		// the original label was "PLAY ONLINE" — renamed to "LOG IN"
		// to distinguish sign-in from registration now that both exist.
		Button playOnline  = menuButton("LOG IN");
		Button register    = menuButton("REGISTER");

		VBox buttons = new VBox(12, playLocal, playOnline, register);
		buttons.setAlignment(Pos.CENTER);

		root.getChildren().addAll(title, buttons);

		playLocal.setOnAction(e -> startLocalGame());
		playOnline.setOnAction(e -> showScene(buildLoginScene(false)));
		// the REGISTER button previously went to buildRegisterScene();
		// that method just delegates to buildLoginScene(true), so we
		// go straight there and skip the indirection.
		register.setOnAction(e -> showScene(buildLoginScene(true)));

		return new Scene(root, W, H);
	}

	/** Login screen (existing account → matching) */
	private Scene buildLoginScene(boolean registerMode) {
		VBox root = styledRoot();
		root.setAlignment(Pos.CENTER);

		Label title = titleLabel("CHECKERS");
		VBox.setMargin(title, new Insets(40, 0, 60, 0));
		// small subtitle so the user knows whether this is a sign-in or sign-up form.
		Label sub = new Label(registerMode ? "Create a new account" : "Sign in to your account");
		sub.setFont(Font.font("Inter", 16));
		sub.setTextFill(Color.GRAY);

		TextField userField = styledTextField("Username");
		Button cancel  = menuButton("CANCEL");
		Button confirm = menuButton(registerMode ? "REGISTER" : "LOGIN");

		VBox form = new VBox(16, userField, new HBox(12, cancel, confirm));
		((HBox) form.getChildren().get(1)).setAlignment(Pos.CENTER);
		form.setAlignment(Pos.CENTER);
		form.setMaxWidth(360);

		// include the new subtitle in the layout.
		root.getChildren().addAll(title, sub, form);

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
		// clear any pending profile request
		awaitingProfileFor = null;
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
			// CHANGED: CANCEL used to fully disconnect and wipe session.  Now that
			//          we have persistent login, CANCEL only leaves the queue
			//          (send QUIT_GAME) and returns to the main menu with the
			//          user still signed in.
			if (clientConnection != null)
				clientConnection.send(new Message(Message.Type.QUIT_GAME));
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

		// Forfeit button — concedes the current online game.
		Button forfeitBtn = new Button("Forfeit");
		forfeitBtn.setFont(Font.font("Inter", 14));
		forfeitBtn.setStyle("-fx-background-color: #C0392B; -fx-text-fill: white; " +
				"-fx-cursor: hand; -fx-padding: 5 14;");
		forfeitBtn.setOnAction(e -> confirmForfeit());
		forfeitBtn.setDisable(!isOnline);

		opponentLabel = new Label(opponentName.isEmpty() ? "" : "vs " + opponentName);
		opponentLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 16));
		opponentLabel.setStyle("-fx-underline: true;");
		// clicking the opponent's name (online play only) opens their profile.
		if (isOnline && !opponentName.isEmpty()) {
			opponentLabel.setStyle("-fx-underline: true; -fx-cursor: hand;");
			opponentLabel.setOnMouseClicked(e -> openProfileScene(opponentName));
		}

		HBox topBar = new HBox(backBtn);
		topBar.setAlignment(Pos.CENTER_LEFT);
		topBar.setPadding(new Insets(10, 12, 8, 12));
		if (!opponentName.isEmpty()) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			topBar.getChildren().addAll(spacer, opponentLabel);
		}
		// Adds the forfeit button on the right edge of the top bar.
		// Adds a spacer first if no opponent-label spacer exists yet.
		if (opponentName.isEmpty()) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			topBar.getChildren().add(spacer);
		}
		topBar.getChildren().add(forfeitBtn);

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

	//  ══════════════════════════════════════════════════════════════════
	//        PROFILE SCENE  (user info panel — checklist item)
	//        Shows wins / losses / win-rate / online status for any user.
	// ══════════════════════════════════════════════════════════════════════════

	private Scene buildProfileScene(String target, int wins, int losses, boolean online) {
		VBox root = styledRoot();
		root.setAlignment(Pos.TOP_CENTER);
		root.setSpacing(18);

		Button back = new Button("← Back");
		back.setFont(Font.font("Inter", 14));
		back.setStyle("-fx-background-color: transparent; -fx-border-color: black; " +
				"-fx-border-width: 1.5; -fx-cursor: hand; -fx-padding: 5 12;");
		back.setOnAction(e -> showScene(buildMainScene()));
		HBox topBar = new HBox(back);
		topBar.setAlignment(Pos.CENTER_LEFT);
		topBar.setPadding(new Insets(10, 12, 0, 12));

		Label title = titleLabel("PROFILE");
		VBox.setMargin(title, new Insets(20, 0, 10, 0));

		Label uname = new Label(target);
		uname.setFont(Font.font("Inter", FontWeight.BOLD, 28));

		Label status = new Label(online ? "● Online" : "○ Offline");
		status.setFont(Font.font("Inter", 14));
		status.setTextFill(online ? Color.web("#2E8B57") : Color.GRAY);

		int games = (wins < 0 || losses < 0) ? 0 : (wins + losses);
		String winRate = games == 0 ? "—" : String.format("%.0f%%", 100.0 * wins / games);

		GridPane stats = new GridPane();
		stats.setHgap(40);
		stats.setVgap(8);
		stats.setAlignment(Pos.CENTER);
		addStatCell(stats, 0, "Wins",     wins   < 0 ? "—" : String.valueOf(wins));
		addStatCell(stats, 1, "Losses",   losses < 0 ? "—" : String.valueOf(losses));
		addStatCell(stats, 2, "Win Rate", winRate);
		stats.setPadding(new Insets(20, 0, 20, 0));

		VBox body = new VBox(14, title, uname, status, stats);
		body.setAlignment(Pos.CENTER);

		// If viewing someone else's profile, offer add/remove friend
		HBox actionRow = new HBox(10);
		actionRow.setAlignment(Pos.CENTER);
		if (target != null && !target.equals(myUsername) && wins >= 0) {
			boolean alreadyFriend = isFriend(target);
			Button friendBtn = menuButton(alreadyFriend ? "REMOVE FRIEND" : "ADD FRIEND");
			friendBtn.setPrefSize(260, 48);
			String friendName = target;
			friendBtn.setOnAction(e -> {
				if (clientConnection == null) return;
				if (alreadyFriend)
					clientConnection.send(new Message(Message.Type.REMOVE_FRIEND, friendName));
				else
					clientConnection.send(new Message(Message.Type.ADD_FRIEND, friendName));
				// Refresh after a brief delay so the server's FRIEND_LIST arrives first.
				new Thread(() -> {
					try { Thread.sleep(200); } catch (InterruptedException ignored) {}
					Platform.runLater(() -> {
						awaitingProfileFor = friendName;
						clientConnection.send(new Message(Message.Type.GET_USER_INFO, friendName));
					});
				}).start();
			});
			actionRow.getChildren().add(friendBtn);
		}

		root.getChildren().addAll(topBar, body, actionRow);
		return new Scene(root, W, H);
	}

	private void addStatCell(GridPane g, int col, String label, String value) {
		Label l = new Label(label);
		l.setFont(Font.font("Inter", 13));
		l.setTextFill(Color.GRAY);
		Label v = new Label(value);
		v.setFont(Font.font("Inter", FontWeight.BOLD, 24));
		VBox cell = new VBox(2, v, l);
		cell.setAlignment(Pos.CENTER);
		g.add(cell, col, 0);
	}

	// ══════════════════════════════════════════════════════════════════
	//        FRIENDS SCENE  (add-friends checklist item)
	// ══════════════════════════════════════════════════════════════════════════

	private Scene buildFriendsScene() {
		awaitingProfileFor = null;
		VBox root = styledRoot();
		root.setSpacing(0);

		Button back = new Button("← Back");
		back.setFont(Font.font("Inter", 14));
		back.setStyle("-fx-background-color: transparent; -fx-border-color: black; " +
				"-fx-border-width: 1.5; -fx-cursor: hand; -fx-padding: 5 12;");
		back.setOnAction(e -> showScene(buildMainScene()));
		HBox topBar = new HBox(back);
		topBar.setAlignment(Pos.CENTER_LEFT);
		topBar.setPadding(new Insets(10, 12, 0, 12));

		Label title = titleLabel("FRIENDS");
		HBox titleRow = new HBox(title);
		titleRow.setAlignment(Pos.CENTER);
		titleRow.setPadding(new Insets(10, 0, 20, 0));

		friendsListView = new ListView<>();
		friendsListView.setStyle("-fx-font-size: 14; -fx-border-color: #222; -fx-border-width: 1.5;");
		refreshFriendsListView();

		// Double-click opens a friend's profile
		friendsListView.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2) {
				String sel = friendsListView.getSelectionModel().getSelectedItem();
				if (sel == null) return;
				String name = extractFriendName(sel);
				if (name != null) openProfileScene(name);
			}
		});

		// Add-friend input
		TextField addField = styledTextField("Add by username…");
		Button addBtn = menuButton("ADD");
		addBtn.setPrefSize(100, 40);
		addBtn.setFont(Font.font("Inter", 14));
		Runnable doAdd = () -> {
			String name = addField.getText().trim();
			if (name.isEmpty()) return;
			if (clientConnection != null)
				clientConnection.send(new Message(Message.Type.ADD_FRIEND, name));
			addField.clear();
		};
		addBtn.setOnAction(e -> doAdd.run());
		addField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) doAdd.run(); });

		HBox addRow = new HBox(10, addField, addBtn);
		addRow.setAlignment(Pos.CENTER);
		HBox.setHgrow(addField, Priority.ALWAYS);

		// Remove-selected button
		Button removeBtn = new Button("Remove Selected");
		removeBtn.setFont(Font.font("Inter", 13));
		removeBtn.setStyle("-fx-background-color: white; -fx-border-color: #C0392B; " +
				"-fx-text-fill: #C0392B; -fx-border-width: 1.5; -fx-cursor: hand; -fx-padding: 6 14;");
		removeBtn.setOnAction(e -> {
			String sel = friendsListView.getSelectionModel().getSelectedItem();
			if (sel == null) return;
			String name = extractFriendName(sel);
			if (name != null && clientConnection != null)
				clientConnection.send(new Message(Message.Type.REMOVE_FRIEND, name));
		});

		Label hint = new Label("Double-click a friend to view their profile.");
		hint.setFont(Font.font("Inter", 12));
		hint.setTextFill(Color.GRAY);

		VBox body = new VBox(12, friendsListView, addRow, removeBtn, hint);
		body.setPadding(new Insets(0, 20, 20, 20));
		VBox.setVgrow(friendsListView, Priority.ALWAYS);

		root.getChildren().addAll(topBar, titleRow, body);
		VBox.setVgrow(body, Priority.ALWAYS);
		return new Scene(root, W, H);
	}

	// helpers for the friends scene
	private boolean isFriend(String name) {
		for (String e : friendEntries) {
			if (e.startsWith(name + "|")) return true;
		}
		return false;
	}

	private String extractFriendName(String listEntry) {
		// Entries look like "● alice  —  W3 L1"
		if (listEntry == null) return null;
		int dash = listEntry.indexOf("—");
		String head = (dash > 0) ? listEntry.substring(0, dash) : listEntry;
		head = head.replace("●", "").replace("○", "").trim();
		return head.isEmpty() ? null : head;
	}

	private void refreshFriendsListView() {
		if (friendsListView == null) return;
		friendsListView.getItems().clear();
		if (friendEntries.isEmpty()) {
			friendsListView.getItems().add("  (No friends yet — add one below!)");
			return;
		}
		for (String e : friendEntries) {
			String[] parts = e.split("\\|");
			if (parts.length < 4) continue;
			String name   = parts[0];
			boolean on    = "1".equals(parts[1]);
			String wins   = parts[2];
			String losses = parts[3];
			String dot    = on ? "●" : "○";
			friendsListView.getItems().add(
					String.format("%s %s   —   W%s  L%s", dot, name, wins, losses));
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  GAME FLOW
	// ══════════════════════════════════════════════════════════════════════════

	/** Start a local pass-and-play game (no network). */
	private void startLocalGame() {
		isOnline    = false;
		// CHANGED
		myUsername  = loggedIn ? myUsername : "Player 1";
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

		if (clientConnection == null) {
			clientConnection = new Client(this::handleServerMessage);
			clientConnection.start();
		}

		// Give the socket a moment to open, then send REGISTER
		// CHANGED: send LOGIN instead of REGISTER when isRegister is false, now
		//          that the two flows are distinct on the server.
		final boolean reg = isRegister;
		new Thread(() -> {
			try { Thread.sleep(400); } catch (InterruptedException ignored) {}
			Message msg = new Message(reg ? Message.Type.REGISTER : Message.Type.LOGIN, username);
			clientConnection.send(msg);
		}).start();
	}

	// LOG OUT from the logged-in main menu.  Drops the socket
	// and wipes cached user state so the next login starts clean.
	private void doLogout() {
		if (clientConnection != null) {
			clientConnection.send(new Message(Message.Type.QUIT_GAME));
			clientConnection.disconnect();
			clientConnection = null;
		}
		loggedIn      = false;
		isOnline      = false;
		myUsername    = "";
		myWins        = 0;
		myLosses      = 0;
		friendEntries.clear();
		showScene(buildMainScene());
	}

	/** Initialise a fresh game (local or online). */
	private void initGame() {
		game     = new CheckersLogic();
		gameOver = false;
		// reset the report-once guard for this new game.
		resultReported = false;
		selectedRow = selectedCol = -1;
		legalMovesForSelected = new ArrayList<>();
	}

	/** Start a new online game after GAME_START received. */
	private void startOnlineGame(String opponent, int playerNum) {
		opponentName = opponent;
		myPlayerNum  = playerNum;
		// re-enable online routing for this match (startLocalGame may
		// have toggled isOnline off earlier in the session).
		isOnline     = true;
		initGame();
		Platform.runLater(() -> {
			showScene(buildGameScene());
			opponentLabel.setText("vs " + opponent);
			updateStatus();
			drawBoard();
		});
	}

	// helpers to open profile / friends scenes with fresh server data.

	private void openProfileScene(String target) {
		if (clientConnection == null) {
			// Offline — show a local stub if we're viewing ourselves.
			if (target != null && target.equals(myUsername)) {
				awaitingProfileFor = null;
				showScene(buildProfileScene(myUsername, myWins, myLosses, false));
			}
			return;
		}
		// Show a placeholder scene immediately; USER_INFO response refreshes it.
		awaitingProfileFor = target;
		showScene(buildProfileScene(target, -1, -1, false));
		clientConnection.send(new Message(Message.Type.GET_USER_INFO, target));
	}

	private void openFriendsScene() {
		showScene(buildFriendsScene());
		// Ask server for a freshest list by prodding GET_USER_INFO on ourselves.
		if (clientConnection != null)
			clientConnection.send(new Message(Message.Type.GET_USER_INFO, myUsername));
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  NETWORK MESSAGE HANDLER
	// ══════════════════════════════════════════════════════════════════════════

	/** Called from Client thread — must push UI work to Platform.runLater. */
	private void handleServerMessage(Message msg) {
		switch (msg.type) {

			// REGISTER_OK now also flips loggedIn=true and caches username.
			case REGISTER_OK:
				// LOGIN_OK uses the same code path as REGISTER_OK.
			case LOGIN_OK:
				loggedIn   = true;
				myUsername = msg.data != null ? msg.data : myUsername;
				Platform.runLater(() -> showScene(buildMatchingScene()));
				break;

			case REGISTER_FAIL:
				Platform.runLater(() -> {
					// route the user back to the register form (was the
					// dedicated register scene, which now just delegates).
					showScene(buildLoginScene(true));
					showAlert(msg.data != null ? msg.data : "Registration failed.");
				});
				break;

			// LOGIN_FAIL — same idea but for the sign-in form.
			case LOGIN_FAIL:
				Platform.runLater(() -> {
					showScene(buildLoginScene(false));
					showAlert(msg.data != null ? msg.data : "Login failed.");
				});
				break;

			case WAITING:
				Platform.runLater(() -> {
					if (game == null || game.isGameOver() || gameOver)
						showScene(buildMatchingScene());
				});
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

			// ADDED: server-driven game-over — e.g. opponent forfeited or disconnected.
			case GAME_OVER:
				Platform.runLater(() -> handleServerGameOver(msg.data));
				break;

			case QUIT_GAME:
				Platform.runLater(() -> {
					String reason = (msg.data != null) ? msg.data : "Opponent disconnected";
					if (chatListView != null)
						chatListView.getItems().add("⚠ " + reason);
					if (game != null && !game.isGameOver() && !gameOver)
						showGameOverDialog("Opponent left the game.");
				});
				break;

			// user-info response handler (drives the profile scene).
			case USER_INFO:
				Platform.runLater(() -> {
					String who = msg.data;
					if (who == null) return;
					if (who.equals(myUsername)) {
						myWins   = msg.wins;
						myLosses = msg.losses;
					}
					// Only replace the scene if we're currently awaiting a profile
					// for this user (opened via openProfileScene).
					if (who.equals(awaitingProfileFor)) {
						awaitingProfileFor = null;
						showScene(buildProfileScene(who, msg.wins, msg.losses, msg.online));
					}
				});
				break;

			// friends-list push from the server.
			case FRIEND_LIST:
				Platform.runLater(() -> {
					friendEntries.clear();
					if (msg.data != null && !msg.data.isEmpty()) {
						for (String part : msg.data.split(";")) {
							if (!part.trim().isEmpty()) friendEntries.add(part);
						}
					}
					refreshFriendsListView();
				});
				break;

			// acknowledgement/error for ADD_FRIEND / REMOVE_FRIEND.
			case FRIEND_ACTION_RESULT:
				Platform.runLater(() -> {
					if (msg.data != null) showAlert(msg.data);
				});
				break;

			default:
				break;
		}
	}

	// handler for server-pushed GAME_OVER (opponent forfeit / disconnect).
	// The server has already persisted the win/loss, so we just update the UI.
	private void handleServerGameOver(String winnerUsername) {
		if (gameOver) return;
		gameOver = true;
		resultReported = true;  // server already recorded
		updateStatus();
		drawBoard();

		String resultText;
		if ("DRAW".equalsIgnoreCase(winnerUsername)) {
			resultText = "It's a draw!";
		} else if (winnerUsername != null && winnerUsername.equals(myUsername)) {
			resultText = "You win! 🎉  (Opponent left / forfeited)";
		} else if (winnerUsername != null) {
			resultText = "You lose.  (" + winnerUsername + " won)";
		} else {
			resultText = "Game over.";
		}
		showGameOverDialog(resultText);
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
		// CHANGED: also check our local gameOver flag, not just game.isGameOver(),
		//          so server-driven endings (forfeit / disconnect) show "Game Over".
		if (game.isGameOver() || gameOver) {
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
		// winner USERNAME computed locally so we can tell the server who won.
		String winnerUsername = null;
		if ("DRAW".equals(winnerColour)) {
			resultText = "It's a draw!";
			winnerUsername = "DRAW";
		} else if (isOnline) {
			boolean iWon = ("RED".equals(winnerColour) && myPlayerNum == 1)
					|| ("BLACK".equals(winnerColour) && myPlayerNum == 2);
			resultText = iWon ? "You win! 🎉" : "You lose.";
			winnerUsername = iWon ? myUsername : opponentName;
		} else {
			resultText = winnerColour + " wins!";
		}

		// report the winner to the server so the persistent stats update.
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
		alert.setContentText("Would you like to play again?");

		ButtonType playAgain = new ButtonType("Play Again");
		ButtonType quit      = new ButtonType("Main Menu", ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(playAgain, quit);

		alert.showAndWait().ifPresent(btn -> {
			if (btn == playAgain) {
				if (loggedIn && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.PLAY_AGAIN));
					showScene(buildMatchingScene());
				} else {
					startLocalGame();
				}
			} else {
				isOnline = false;
				if (loggedIn && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.QUIT_GAME));
				}
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
		// CHANGED: if the game is still in progress online, warn the user that
		//          leaving via Back will count as a forfeit and send FORFEIT
		//          instead of a plain QUIT_GAME so stats update correctly.
		boolean willForfeit = isOnline && game != null && !game.isGameOver() && !gameOver;
		confirm.setHeaderText(willForfeit
				? "Leaving will count as a forfeit. Return to main menu?"
				: "Return to main menu?");
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.YES) {
				if (willForfeit && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.FORFEIT));
					resultReported = true;
				} else if (isOnline && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.QUIT_GAME));
				}
				isOnline = false;
				showScene(buildMainScene());
			}
		});
	}

	// ADDED: confirmForfeit — driven by the new Forfeit button in the top bar.
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
				// Server will push GAME_OVER back — that handler updates UI.
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