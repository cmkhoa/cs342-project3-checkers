import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import models.Message;
import network.Client;
import scenes.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main JavaFX client application for Checkers.
 * Handles scene navigation, account management, and network message dispatch.
 */
public class GuiClient extends Application implements GameController.Host {

	// window
	private Stage primaryStage;

	// network
	private Client clientConnection;
	private boolean isOnline;

	// player info
	private String myUsername = "";
	private String opponentName = "";
	private boolean loggedIn = false;
	private int myWins = 0;
	private int myLosses = 0;
	private int myElo = 1000;
	private final List<String> friendEntries = new ArrayList<>();

	// Friend requests
	private final List<String> pendingRequests = new ArrayList<>();

	// Game controller
	private final GameController gc = new GameController(this);

	// Profile navigation
	private String awaitingProfileFor = null;
	private boolean profileReturnToGame = false;
	private Alert pendingAlert = null;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) {
		primaryStage = stage;
		primaryStage.setTitle("Checkers");
		primaryStage.setResizable(false);
		primaryStage.setOnCloseRequest((WindowEvent e) -> {
			if (clientConnection != null)
				clientConnection.disconnect();
			Platform.exit();
			System.exit(0);
		});
		showMain();
	}

	// host callbacks

	@Override
	public void showMain() {
		navigateToMain();
	}

	@Override
	public void showMatching() {
		navigateToMatching();
	}

	@Override
	public void showAlert(String text) {
		alertInfo(text);
	}

	@Override
	public void closePendingAlert() {
		Platform.runLater(() -> {
			if (pendingAlert != null && pendingAlert.isShowing()) {
				pendingAlert.setResult(ButtonType.OK);
				pendingAlert.close();
				pendingAlert = null;
			}
		});
	}

	@Override
	public boolean isLoggedIn() {
		return loggedIn;
	}

	@Override
	public boolean hasConnection() {
		return clientConnection != null;
	}

	@Override
	public String getMyUsername() {
		return myUsername;
	}

	@Override
	public String getOpponentName() {
		return opponentName;
	}

	@Override
	public void sendMessage(Message msg) {
		if (clientConnection != null)
			clientConnection.send(msg);
	}

	@Override
	public void showGame() {
		gc.activeGameScene = new GameScene();
		primaryStage.setScene(gc.activeGameScene.build(myUsername, opponentName, gc.isOnline,
				new GameScene.Actions() {
					@Override
					public void onBoardClick(double x, double y) {
						gc.handleBoardClick(x, y);
					}

					@Override
					public void onBack() {
						confirmBack();
					}

					@Override
					public void onForfeit() {
						confirmForfeit();
					}

					@Override
					public void onSendChat(String text) {
						gc.sendChat(text);
					}

					@Override
					public void onOpponentNameClick() {
						openProfileScene(opponentName);
					}
				}));
		gc.drawBoard();
		gc.updateStatus();
		gc.updatePieceCountLabels();
	}

	// scene navigation

	private void navigateToMain() {
		awaitingProfileFor = null;
		primaryStage.setScene(MainScene.build(loggedIn, myUsername, new MainScene.Actions() {
			@Override
			public void onPlayLocal() {
				startLocalGame();
			}

			@Override
			public void onPlayAI() {
				startAIGame();
			}

			@Override
			public void onLogin() {
				showAuth(false);
			}

			@Override
			public void onRegister() {
				showAuth(true);
			}

			@Override
			public void onFindMatch() {
				findMatchOnline();
			}

			@Override
			public void onProfile() {
				openProfileScene(myUsername);
			}

			@Override
			public void onFriends() {
				openFriendsScene();
			}

			@Override
			public void onLogout() {
				doLogout();
			}
		}));
		primaryStage.show();
	}

	private void showAuth(boolean registerMode) {
		primaryStage.setScene(AuthScene.build(registerMode,
				new AuthScene.Actions() {
					@Override
					public void onSubmit(String username, String password) {
						attemptConnect(username, password, registerMode);
					}

					@Override
					public void onToggle() {
						showAuth(!registerMode);
					}

					@Override
					public void onBack() {
						navigateToMain();
					}
				}));
	}

	private void navigateToMatching() {
		awaitingProfileFor = null;
		primaryStage.setScene(MatchingScene.build(myUsername, () -> {
			if (clientConnection != null)
				clientConnection.send(new Message(Message.Type.QUIT_GAME));
			navigateToMain();
		}));
	}

	// Profile / Friends

	private void openProfileScene(String target) {
		profileReturnToGame = (gc.activeGameScene != null && !gc.gameOver
				&& gc.game != null && !gc.game.isGameOver());

		if (clientConnection == null) {
			if (target != null && target.equals(myUsername)) {
				primaryStage.setScene(ProfileScene.build(
						myUsername, myUsername, myWins, myLosses, myElo, false, false,
						new ArrayList<>(), friendNameSet(), buildProfileActions()));
			}
			return;
		}
		awaitingProfileFor = target;
		primaryStage.setScene(ProfileScene.build(
				myUsername, target, -1, -1, 0, false, isFriend(target),
				new ArrayList<>(), friendNameSet(), buildProfileActions()));
		clientConnection.send(new Message(Message.Type.GET_USER_INFO, target));
	}

	private ProfileScene.Actions buildProfileActions() {
		return new ProfileScene.Actions() {
			@Override
			public void onBack() {
				if (profileReturnToGame && gc.activeGameScene != null) {
					showGame();
				} else {
					navigateToMain();
				}
				profileReturnToGame = false;
			}

			@Override
			public void onAddFriend(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.SEND_FRIEND_REQUEST, name));
				scheduleProfileRefresh(name);
			}

			@Override
			public void onRemoveFriend(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.REMOVE_FRIEND, name));
				scheduleProfileRefresh(name);
			}
		};
	}

	private void scheduleProfileRefresh(String target) {
		new Thread(() -> {
			try {
				Thread.sleep(250);
			} catch (InterruptedException ignored) {
			}
			Platform.runLater(() -> {
				awaitingProfileFor = target;
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.GET_USER_INFO, target));
			});
		}).start();
	}

	private void openFriendsScene() {
		awaitingProfileFor = null;
		primaryStage.setScene(FriendsScene.build(friendEntries, pendingRequests, new FriendsScene.Actions() {
			@Override
			public void onBack() {
				navigateToMain();
			}

			@Override
			public void onAddFriend(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.SEND_FRIEND_REQUEST, name));
			}

			@Override
			public void onRemoveFriend(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.REMOVE_FRIEND, name));
			}

			@Override
			public void onViewProfile(String name) {
				openProfileScene(name);
			}

			@Override
			public void onAcceptRequest(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.ACCEPT_FRIEND_REQUEST, name));
			}

			@Override
			public void onDeclineRequest(String name) {
				if (clientConnection != null)
					clientConnection.send(new Message(Message.Type.DECLINE_FRIEND_REQUEST, name));
			}

			@Override
			public void onChallengeFriend(String name) {
				if (clientConnection != null) {
					clientConnection.send(new Message(Message.Type.CHALLENGE, name));
					navigateToMatching();
				}
			}
		}));
		if (clientConnection != null)
			clientConnection.send(new Message(Message.Type.GET_USER_INFO, myUsername));
	}

	// game flow

	private void startLocalGame() {
		opponentName = "Player 2";
		gc.myPlayerNum = 1;
		gc.configureLocal();
		gc.initGame();
		showGame();
	}

	private void startAIGame() {
		opponentName = "Computer";
		gc.myPlayerNum = 1;
		gc.configureAI();
		gc.initGame();
		showGame();
		gc.startAIFirstMove();
	}

	private void findMatchOnline() {
		if (clientConnection != null)
			clientConnection.send(new Message(Message.Type.PLAY_AGAIN));
		navigateToMatching();
	}

	private void startOnlineGame(String opponent, int playerNum) {
		closePendingAlert();
		opponentName = opponent;
		gc.myPlayerNum = playerNum;
		gc.configureOnline(playerNum);
		gc.initGame();
		isOnline = true;
		Platform.runLater(() -> {
			showGame();
			gc.updateStatus();
			gc.drawBoard();
		});
	}

	// account management

	private void attemptConnect(String username, String password, boolean isRegister) {
		if (username.isEmpty()) {
			alertInfo("Please enter a username.");
			return;
		}
		if (password == null || password.isEmpty()) {
			alertInfo("Please enter a password.");
			return;
		}
		myUsername = username;
		isOnline = true;
		navigateToMatching();

		if (clientConnection == null) {
			clientConnection = new Client(this::handleServerMessage);
			clientConnection.start();
		}

		final boolean reg = isRegister;
		final String pw = password;
		new Thread(() -> {
			try {
				Thread.sleep(400);
			} catch (InterruptedException ignored) {
			}
			Message msg = new Message(
					reg ? Message.Type.REGISTER : Message.Type.LOGIN,
					username, pw);
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
		navigateToMain();
	}

	// network message handler

	private void handleServerMessage(Message msg) {
		switch (msg.type) {
			case REGISTER_OK:
			case LOGIN_OK:
				loggedIn = true;
				myUsername = msg.data != null ? msg.data : myUsername;
				Platform.runLater(this::navigateToMain);
				break;

			case REGISTER_FAIL:
				Platform.runLater(() -> {
					showAuth(true);
					alertInfo(msg.data != null ? msg.data : "Registration failed.");
				});
				break;

			case LOGIN_FAIL:
				Platform.runLater(() -> {
					showAuth(false);
					alertInfo(msg.data != null ? msg.data : "Login failed.");
				});
				break;

			case WAITING:
				Platform.runLater(() -> {
					if (gc.game == null || gc.game.isGameOver() || gc.gameOver)
						navigateToMatching();
				});
				break;

			case GAME_START:
				Platform.runLater(() -> startOnlineGame(msg.data, msg.playerNum));
				break;

			case MOVE:
				Platform.runLater(() -> {
					if (gc.game != null) {
						gc.game.makeMove(msg.fromRow, msg.fromCol, msg.toRow, msg.toCol);
						if (!gc.game.isMidJump()) {
							// reset selection after opponent's move completes
						}
						gc.drawBoard();
						gc.updateStatus();
						gc.updatePieceCountLabels();
						if (gc.game.isGameOver())
							gc.showGameOverDialog("Game over.");
					}
				});
				break;

			case CHAT:
				Platform.runLater(() -> {
					if (gc.activeGameScene != null && gc.activeGameScene.chatListView != null && msg.data != null) {
						gc.activeGameScene.chatListView.getItems().add(msg.data);
						gc.activeGameScene.scrollChatToBottom();
					}
				});
				break;

			case GAME_OVER:
				Platform.runLater(() -> gc.handleServerGameOver(msg));
				break;

			case QUIT_GAME:
				Platform.runLater(() -> {
					String reason = msg.data != null ? msg.data : "Opponent disconnected";
					if (gc.activeGameScene != null && gc.activeGameScene.chatListView != null)
						gc.activeGameScene.chatListView.getItems().add("⚠ " + reason);
					if (gc.game != null && !gc.game.isGameOver() && !gc.gameOver)
						gc.showGameOverDialog("Opponent left the game.");
				});
				break;

			case USER_INFO:
				Platform.runLater(() -> {
					String who = msg.data;
					if (who == null)
						return;
					if (who.equals(myUsername)) {
						myWins = msg.wins;
						myLosses = msg.losses;
						myElo = msg.elo;
					}
					if (who.equals(awaitingProfileFor)) {
						awaitingProfileFor = null;
						primaryStage.setScene(ProfileScene.build(
								myUsername, who, msg.wins, msg.losses, msg.elo, msg.online,
								isFriend(who), msg.matchHistory, friendNameSet(),
								buildProfileActions()));
					}
				});
				break;

			case FRIEND_LIST:
				Platform.runLater(() -> {
					friendEntries.clear();
					if (msg.data != null && !msg.data.isEmpty()) {
						for (String part : msg.data.split(";")) {
							if (!part.trim().isEmpty())
								friendEntries.add(part);
						}
					}
				});
				break;

			case FRIEND_ACTION_RESULT:
				Platform.runLater(() -> {
					if (msg.data != null)
						alertInfo(msg.data);
				});
				break;

			case PENDING_REQUESTS:
				Platform.runLater(() -> {
					pendingRequests.clear();
					if (msg.data != null && !msg.data.isEmpty()) {
						for (String part : msg.data.split(";")) {
							if (!part.trim().isEmpty())
								pendingRequests.add(part.trim());
						}
					}
				});
				break;

			case FRIEND_REQUEST_RECEIVED:
				Platform.runLater(() -> {
					if (msg.data != null)
						alertInfo(msg.data + " sent you a friend request!");
				});
				break;

			case CHALLENGE_INCOMING:
				Platform.runLater(() -> {
					if (msg.data != null) {
						String challenger = msg.data;
						Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
						alert.setTitle("Incoming Challenge");
						alert.setHeaderText(challenger + " has challenged you to a game!");
						alert.setContentText("Do you want to accept?");

						ButtonType btnAccept = new ButtonType("Accept", ButtonBar.ButtonData.OK_DONE);
						ButtonType btnDecline = new ButtonType("Decline", ButtonBar.ButtonData.CANCEL_CLOSE);
						alert.getButtonTypes().setAll(btnAccept, btnDecline);

						Thread timer = new Thread(() -> {
							try {
								Thread.sleep(15000); // 15 seconds
								Platform.runLater(() -> {
									if (alert.isShowing()) {
										alert.setResult(btnDecline);
										alert.close();
									}
								});
							} catch (InterruptedException ignored) {
							}
						});
						timer.setDaemon(true);
						timer.start();

						alert.showAndWait().ifPresent(result -> {
							if (result == btnAccept) {
								clientConnection.send(new Message(Message.Type.CHALLENGE_ACCEPT, challenger));
								gc.closeGameOverDialog();
								navigateToMatching(); // Show waiting scene while server starts game
							} else {
								clientConnection.send(new Message(Message.Type.CHALLENGE_DECLINE, challenger));
							}
						});
					}
				});
				break;

			case CHALLENGE_REJECTED:
				Platform.runLater(() -> {
					if (msg.data != null)
						alertInfo(msg.data);
					// If we were waiting for a match that was rejected, return to where we were
					if (primaryStage.getScene() != null
							&& primaryStage.getScene().getRoot().toString().contains("MatchingScene")) {
						navigateToMain(); // or stay? Usually back to main menu or friends
					}
				});
				break;

			default:
				break;
		}
	}

	// navigation helpers

	private void confirmBack() {
		boolean willForfeit = gc.isOnline && gc.game != null && !gc.game.isGameOver() && !gc.gameOver;
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
				willForfeit ? "Leaving will count as a forfeit." : "Return to main menu?", ButtonType.YES,
				ButtonType.NO);
		confirm.setTitle("Leave Game");
		confirm.setHeaderText(willForfeit ? "Forfeit and leave?" : "Return to main menu?");
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.YES) {
				if (willForfeit && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.FORFEIT));
					gc.resultReported = true;
				} else if (gc.isOnline && clientConnection != null) {
					clientConnection.send(new Message(Message.Type.QUIT_GAME));
				}
				gc.isOnline = false;
				isOnline = false;
				navigateToMain();
			}
		});
	}

	private void confirmForfeit() {
		if (!gc.isOnline || gc.game == null || gc.game.isGameOver() || gc.gameOver)
			return;
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Forfeiting counts as a loss. Are you sure?",
				ButtonType.YES, ButtonType.NO);
		confirm.setTitle("Forfeit");
		confirm.setHeaderText("Forfeit this game?");
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.YES && clientConnection != null) {
				clientConnection.send(new Message(Message.Type.FORFEIT));
				gc.resultReported = true;
			}
		});
	}

	private void alertInfo(String msg) {
		Platform.runLater(() -> {
			if (pendingAlert != null && pendingAlert.isShowing()) {
				pendingAlert.close();
			}
			pendingAlert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
			pendingAlert.setHeaderText(null);
			pendingAlert.setTitle("Checkers");
			pendingAlert.show();
		});
	}

	// helpers

	private boolean isFriend(String name) {
		for (String e : friendEntries) {
			if (e.startsWith(name + "|"))
				return true;
		}
		return false;
	}

	private Set<String> friendNameSet() {
		Set<String> names = new HashSet<>();
		for (String e : friendEntries) {
			int pipe = e.indexOf('|');
			names.add(pipe > 0 ? e.substring(0, pipe) : e);
		}
		return names;
	}
}