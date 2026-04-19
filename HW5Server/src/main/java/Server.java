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
	private final Map<String, ClientThread> registeredUsers = new HashMap<>();
	private final List<ClientThread>        waitingQueue    = new ArrayList<>();

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

				case MOVE:
				case CHAT:
					// Relay directly to partner
					if (partner != null) partner.send(msg);
					break;

				case PLAY_AGAIN:
					handlePlayAgain();
					break;

				case QUIT_GAME:
					handleQuitGame();
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
				if (registeredUsers.containsKey(uname)) {
					send(new Message(Message.Type.REGISTER_FAIL,
							"\"" + uname + "\" is already taken. Please choose another."));
					log("Registration rejected (duplicate): " + uname);
					return;
				}
				username = uname;
				registeredUsers.put(uname, this);
				log("Registered: " + username);
			}

			send(new Message(Message.Type.REGISTER_OK, username));
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

			Message startP1 = new Message(Message.Type.GAME_START, p2.username);
			startP1.playerNum = 1;   // RED, moves second (bottom of board)

			Message startP2 = new Message(Message.Type.GAME_START, p1.username);
			startP2.playerNum = 2;   // BLACK, moves first (top of board)

			p1.send(startP1);
			p2.send(startP2);

			log("Game started: " + p1.username + " (RED) vs " + p2.username + " (BLACK)");
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

		// ── Disconnect ────────────────────────────────────────────────────────

		private void handleDisconnect() {
			synchronized (Server.this) {
				String name = username != null ? username : "Client #" + id;
				log(name + " disconnected");

				if (username != null) registeredUsers.remove(username);
				waitingQueue.remove(this);

				if (partner != null) {
					Message note = new Message(Message.Type.QUIT_GAME,
							name + " disconnected.");
					partner.send(note);
					partner.partner = null;
					partner = null;
				}
			}
			try { connection.close(); } catch (Exception ignored) {}
		}

		// ── Helpers ───────────────────────────────────────────────────────────

		private String displayName() {
			return username != null ? username : "client #" + id;
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	//  LOGGING
	// ══════════════════════════════════════════════════════════════════════════

	private void log(String msg) {
		callback.accept("[Server] " + msg);
	}
}
