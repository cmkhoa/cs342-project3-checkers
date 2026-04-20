import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Checkers server.
 *
 * Responsibilities:
 *  - Accept TCP connections from clients
 *  - Register unique usernames
 *  - Match waiting players into games (first-in, first-out)
 *  - Relay MOVE and CHAT messages between paired clients
 *  - Handle disconnects and play-again requests
 */
public class Server {

	// ── Server-wide state (access via synchronized(this)) ────────────────────
	private final Map<String, ClientThread> activeSessions  = new HashMap<>();
	private final List<ClientThread>        waitingQueue    = new ArrayList<>();
	// ADDED: persistent user store (users.json in the server's working directory).
	private final UserStore                 userStore       = new UserStore("users.json");

	private       int                       clientCount     = 1;
	private final TheServer                 serverThread;
	private final Consumer<Serializable>    callback;

	// ─────────────────────────────────────────────────────────────────────────
	Server(Consumer<Serializable> callback) {
		this.callback = callback;
		serverThread  = new TheServer();
		serverThread.start();
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  ACCEPTOR THREAD
	// ══════════════════════════════════════════════════════════════════════════

	private class TheServer extends Thread {
		@Override
		public void run() {
			try (ServerSocket ss = new ServerSocket(5555)) {
				log("Server listening on port 5555");
				while (true) {
					Socket s = ss.accept();
					ClientThread ct = new ClientThread(s, clientCount++);
					ct.setDaemon(true);
					ct.start();
					log("Client #" + ct.id + " connected from " + s.getInetAddress());
				}
			} catch (Exception e) {
				log("Server socket error: " + e.getMessage());
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  CLIENT THREAD
	// ══════════════════════════════════════════════════════════════════════════

	private class ClientThread extends Thread {

		final  int              id;
		final  Socket           connection;
		ObjectInputStream       in;
		ObjectOutputStream      out;

		String       username = null;
		ClientThread partner  = null;   // null when not in a game

		// flag used to avoid double-counting a completed game. Both the
		// winner and the loser may report GAME_OVER, so we record stats
		// only for the first report and mark both threads as done.
		private boolean gameResultRecorded = false;

		ClientThread(Socket s, int id) {
			this.connection = s;
			this.id         = id;
		}

		// ── Network I/O ───────────────────────────────────────────────────────

		void send(Message msg) {
			if (out == null) return;
			try {
				synchronized (out) {
					out.writeObject(msg);
					out.flush();
					out.reset();
				}
			} catch (Exception e) {
				log("Send to client #" + id + " failed: " + e.getMessage());
			}
		}

		// ── Thread entry point ────────────────────────────────────────────────

		@Override
		public void run() {
			try {
				out = new ObjectOutputStream(connection.getOutputStream());
				out.flush();
				in  = new ObjectInputStream(connection.getInputStream());
				connection.setTcpNoDelay(true);
			} catch (Exception e) {
				log("Stream setup failed for client #" + id);
				return;
			}

			while (true) {
				try {
					Message msg = (Message) in.readObject();
					handleMessage(msg);
				} catch (Exception e) {
					handleDisconnect();
					break;
				}
			}
		}

		// ── Message dispatch ──────────────────────────────────────────────────

		private void handleMessage(Message msg) {
			switch (msg.type) {

				case REGISTER:
					handleRegister(msg.data);
					break;

				// LOGIN for existing (persisted) accounts.
				case LOGIN:
					handleLogin(msg.data);
					break;

				case MOVE:
				case CHAT:
					// Relay directly to partner
					if (partner != null) partner.send(msg);
					break;

				// client reports the winning username (or "DRAW") so the
				// server can persist wins/losses against the right account.
				case GAME_OVER:
					handleGameOverReport(msg.data);
					break;

				case PLAY_AGAIN:
					handlePlayAgain();
					break;

				case QUIT_GAME:
					handleQuitGame();
					break;

				// mid-game concede
				case FORFEIT:
					handleForfeit();
					break;

				// user-info lookups for the profile panel
				case GET_USER_INFO:
					handleGetUserInfo(msg.data);
					break;

				// friends feature
				case ADD_FRIEND:
					handleAddFriend(msg.data);
					break;

				case REMOVE_FRIEND:
					handleRemoveFriend(msg.data);
					break;

				default:
					log("Unknown message type from " + displayName() + ": " + msg.type);
			}
		}

		// ── Registration ──────────────────────────────────────────────────────

		private void handleRegister(String uname) {
			if (uname == null || uname.trim().isEmpty()) {
				send(new Message(Message.Type.REGISTER_FAIL, "Username cannot be empty."));
				return;
			}
			uname = uname.trim();

			synchronized (Server.this) {
				// CHANGED: duplicate-check now goes against the persistent UserStore
				//          (used to check an in-memory map that only lasted for
				//          the server's current run).  Also reject if somebody
				//          is already logged in on that name.
				if (userStore.exists(uname)) {
					send(new Message(Message.Type.REGISTER_FAIL,
							"\"" + uname + "\" is already taken. Please choose another."));
					log("Registration rejected (exists): " + uname);
					return;
				}
				if (activeSessions.containsKey(uname)) {
					send(new Message(Message.Type.REGISTER_FAIL,
							"\"" + uname + "\" is currently in use."));
					return;
				}
				// ADDED: create the persistent record, then bind this session.
				userStore.register(uname);
				username = uname;
				activeSessions.put(uname, this);
				userStore.setOnline(uname, true);
				log("Registered: " + username);
			}

			send(new Message(Message.Type.REGISTER_OK, username));
			// ADDED: let this user's friends (if any were already in their list from
			//        a prior session) know they just came online, and push them the
			//        initial FRIEND_LIST so the friends panel renders correctly.
			notifyFriendsOfStatusChange();
			sendFriendListTo(this);
			addToQueue();
		}

		// ADDED: LOGIN handler — sign-in flow for existing persisted accounts.
		private void handleLogin(String uname) {
			if (uname == null || uname.trim().isEmpty()) {
				send(new Message(Message.Type.LOGIN_FAIL, "Username cannot be empty."));
				return;
			}
			uname = uname.trim();

			synchronized (Server.this) {
				if (!userStore.exists(uname)) {
					send(new Message(Message.Type.LOGIN_FAIL,
							"No account named \"" + uname + "\". Register first."));
					return;
				}
				if (activeSessions.containsKey(uname)) {
					send(new Message(Message.Type.LOGIN_FAIL,
							"\"" + uname + "\" is already logged in elsewhere."));
					return;
				}
				username = uname;
				activeSessions.put(uname, this);
				userStore.setOnline(uname, true);
				log("Logged in: " + username);
			}

			send(new Message(Message.Type.LOGIN_OK, username));
			notifyFriendsOfStatusChange();
			sendFriendListTo(this);
			addToQueue();
		}

		// ── Matchmaking ───────────────────────────────────────────────────────

		private void addToQueue() {
			synchronized (Server.this) {
				if (!waitingQueue.contains(this)) waitingQueue.add(this);
				send(new Message(Message.Type.WAITING,
						"In queue — " + waitingQueue.size() + " waiting"));
				log(username + " added to queue  (queue size: " + waitingQueue.size() + ")");
				tryMatch();
			}
		}

		private void tryMatch() {
			// Called while holding Server.this monitor
			if (waitingQueue.size() < 2) return;

			ClientThread p1 = waitingQueue.remove(0);
			ClientThread p2 = waitingQueue.remove(0);

			p1.partner = p2;
			p2.partner = p1;
			// ADDED: reset per-game state so the win/loss recorder fires exactly
			//        once per game, even across rematches between the same pair.
			p1.gameResultRecorded = false;
			p2.gameResultRecorded = false;

			Message startP1 = new Message(Message.Type.GAME_START, p2.username);
			startP1.playerNum = 1;   // RED, moves second (bottom of board)

			Message startP2 = new Message(Message.Type.GAME_START, p1.username);
			startP2.playerNum = 2;   // BLACK, moves first (top of board)

			p1.send(startP1);
			p2.send(startP2);

			log("Game started: " + p1.username + " (RED) vs " + p2.username + " (BLACK)");
		}

		// ADDED: Game-over reporting.
		//   Client sends GAME_OVER with data = winning USERNAME, or "DRAW".
		//   The gameResultRecorded flag ensures we tally once even if both
		//   clients report the same result.
		private void handleGameOverReport(String winnerOrDraw) {
			synchronized (Server.this) {
				if (partner == null || username == null) return;
				if (gameResultRecorded) return;
				gameResultRecorded        = true;
				partner.gameResultRecorded = true;

				String a = this.username;
				String b = partner.username;
				if ("DRAW".equalsIgnoreCase(winnerOrDraw)) {
					log("Game ended in a draw: " + a + " vs " + b);
				} else if (a.equals(winnerOrDraw)) {
					userStore.recordWin(a);
					userStore.recordLoss(b);
					log("Result recorded: " + a + " defeated " + b);
				} else if (b.equals(winnerOrDraw)) {
					userStore.recordWin(b);
					userStore.recordLoss(a);
					log("Result recorded: " + b + " defeated " + a);
				} else {
					log("GAME_OVER received with unexpected winner: " + winnerOrDraw);
				}
				pushSelfUserInfo(this);
				pushSelfUserInfo(partner);
			}
		}

		// ── Play-again ────────────────────────────────────────────────────────

		private void handlePlayAgain() {
			synchronized (Server.this) {
				log(username + " wants to play again");
				if (partner != null) {
					partner.partner = null;
					partner = null;
				}
			}
			addToQueue();
		}

		// ── Quit ──────────────────────────────────────────────────────────────

		private void handleQuitGame() {
			synchronized (Server.this) {
				log(username + " quit the game");
				if (partner != null) {
					Message note = new Message(Message.Type.QUIT_GAME,
							username + " left the game.");
					partner.send(note);
					partner.partner = null;
					partner = null;
				}
				waitingQueue.remove(this);
			}
		}

		// ADDED: Forfeit (concede mid-game).  Opposite of QUIT_GAME in that it
		//        explicitly counts as a loss and a win for the opponent, and
		//        notifies both sides via GAME_OVER so the UIs close cleanly.
		private void handleForfeit() {
			synchronized (Server.this) {
				if (partner == null || gameResultRecorded) return;
				gameResultRecorded = true;
				partner.gameResultRecorded = true;

				log(username + " forfeited — " + partner.username + " wins");
				userStore.recordWin(partner.username);
				userStore.recordLoss(this.username);

				// Tell the opponent they win by forfeit, using GAME_OVER with their name.
				Message notice = new Message(Message.Type.GAME_OVER, partner.username);
				partner.send(notice);

				// Also echo to the forfeiter so their UI knows the game ended.
				Message selfNotice = new Message(Message.Type.GAME_OVER, partner.username);
				send(selfNotice);

				pushSelfUserInfo(this);
				pushSelfUserInfo(partner);

				partner.partner = null;
				partner = null;
			}
		}

		// ── Disconnect ────────────────────────────────────────────────────────

		private void handleDisconnect() {
			synchronized (Server.this) {
				String name = username != null ? username : "Client #" + id;
				log(name + " disconnected");

				// CHANGED: if the user was mid-game when they dropped, treat it
				//          as a forfeit so the opponent's win is persisted.
				if (username != null) {
					if (partner != null && !gameResultRecorded) {
						gameResultRecorded = true;
						partner.gameResultRecorded = true;
						userStore.recordWin(partner.username);
						userStore.recordLoss(username);
						Message notice = new Message(Message.Type.GAME_OVER, partner.username);
						partner.send(notice);
						log("Disconnect counted as forfeit: " + partner.username + " wins");
						pushSelfUserInfo(partner);
					}
					// CHANGED: remove from the active-sessions map (not the old
					//          registeredUsers) and flip online flag off.
					activeSessions.remove(username);
					userStore.setOnline(username, false);
				}
				waitingQueue.remove(this);

				if (partner != null) {
					Message note = new Message(Message.Type.QUIT_GAME,
							name + " disconnected.");
					partner.send(note);
					partner.partner = null;
					partner = null;
				}
			}
			// ADDED: notify any online friends that this user went offline so
			//        their friends panels update.  Done outside the synchronised
			//        block so we don't hold the monitor while doing network I/O.
			notifyFriendsOfStatusChange();
			try { connection.close(); } catch (Exception ignored) {}
		}

		// ADDED: ── User info ──────────────────────────────────────────────────

		private void handleGetUserInfo(String target) {
			String query = (target == null || target.trim().isEmpty()) ? username : target.trim();
			User u = userStore.get(query);
			if (u == null) {
				Message fail = new Message(Message.Type.USER_INFO);
				fail.data = query;
				fail.wins = -1;
				fail.losses = -1;
				fail.online = false;
				send(fail);
				return;
			}
			Message info = new Message(Message.Type.USER_INFO);
			info.data   = u.getUsername();
			info.wins   = u.getWins();
			info.losses = u.getLosses();
			info.online = activeSessions.containsKey(u.getUsername());
			info.friends = new ArrayList<>(u.getFriends());
			send(info);
		}

		/** Sends the sender's own user-info record (used after stat changes). */
		private void pushSelfUserInfo(ClientThread target) {
			if (target == null || target.username == null) return;
			User u = userStore.get(target.username);
			if (u == null) return;
			Message info = new Message(Message.Type.USER_INFO);
			info.data   = u.getUsername();
			info.wins   = u.getWins();
			info.losses = u.getLosses();
			info.online = true;
			info.friends = new ArrayList<>(u.getFriends());
			target.send(info);
		}

		// ADDED: ── Friends ──────────────────────────────────────────────────

		private void handleAddFriend(String friendName) {
			if (username == null) return;
			if (friendName == null || friendName.trim().isEmpty()) {
				send(new Message(Message.Type.FRIEND_ACTION_RESULT, "Enter a username."));
				return;
			}
			friendName = friendName.trim();
			if (friendName.equals(username)) {
				send(new Message(Message.Type.FRIEND_ACTION_RESULT, "You can't add yourself."));
				return;
			}
			if (!userStore.exists(friendName)) {
				send(new Message(Message.Type.FRIEND_ACTION_RESULT,
						"No user named \"" + friendName + "\"."));
				return;
			}
			boolean added = userStore.addFriend(username, friendName);
			if (added) {
				log(username + " added friend: " + friendName);
				send(new Message(Message.Type.FRIEND_ACTION_RESULT,
						"Added " + friendName + " as a friend."));
			} else {
				send(new Message(Message.Type.FRIEND_ACTION_RESULT,
						friendName + " is already your friend."));
			}
			sendFriendListTo(this);
		}

		private void handleRemoveFriend(String friendName) {
			if (username == null || friendName == null) return;
			friendName = friendName.trim();
			boolean removed = userStore.removeFriend(username, friendName);
			if (removed) {
				log(username + " removed friend: " + friendName);
				send(new Message(Message.Type.FRIEND_ACTION_RESULT,
						"Removed " + friendName + "."));
			} else {
				send(new Message(Message.Type.FRIEND_ACTION_RESULT,
						friendName + " is not in your friends list."));
			}
			sendFriendListTo(this);
		}

		// ── Helpers ───────────────────────────────────────────────────────────

		private String displayName() {
			return username != null ? username : "client #" + id;
		}
	}

	// ADDED: ══════════════════════════════════════════════════════════════════
	//         FRIEND NOTIFICATION HELPERS  (server-level, not per-thread)
	//         Builds and dispatches FRIEND_LIST messages so that every
	//         interested client sees up-to-date wins/losses/online status.
	// ══════════════════════════════════════════════════════════════════════════

	/**
	 * Builds the data payload for FRIEND_LIST as a list of
	 * "name|online|wins|losses" strings separated by ';'.
	 */
	private String buildFriendListPayload(String owner) {
		User u = userStore.get(owner);
		if (u == null) return "";
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String f : u.getFriends()) {
			User fu = userStore.get(f);
			if (fu == null) continue;
			boolean online = activeSessions.containsKey(f);
			if (!first) sb.append(";");
			sb.append(f).append("|")
					.append(online ? "1" : "0").append("|")
					.append(fu.getWins()).append("|")
					.append(fu.getLosses());
			first = false;
		}
		return sb.toString();
	}

	private void sendFriendListTo(Server.ClientThread target) {
		if (target == null || target.username == null) return;
		String payload;
		synchronized (this) {
			payload = buildFriendListPayload(target.username);
		}
		Message msg = new Message(Message.Type.FRIEND_LIST, payload);
		target.send(msg);
	}

	/**
	 * Called when a user goes online or offline.  Pushes a refreshed friend
	 * list to every currently-connected user, so their panel shows the
	 * updated status.
	 */
	private void notifyFriendsOfStatusChange() {
		List<ClientThread> recipients = new ArrayList<>();
		synchronized (this) {
			for (ClientThread other : activeSessions.values()) {
				if (other.username == null) continue;
				recipients.add(other);
			}
		}
		for (ClientThread r : recipients) sendFriendListTo(r);
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  LOGGING
	// ══════════════════════════════════════════════════════════════════════════

	private void log(String msg) {
		callback.accept("[Server] " + msg);
	}
}