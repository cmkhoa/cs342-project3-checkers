import models.Message;
import models.User;

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
 * - Accept TCP connections from clients
 * - Register unique usernames
 * - Match waiting players into games elo based with 100 points range
 * - Relay MOVE and CHAT messages between paired clients
 * - Handle disconnects and play-again requests
 */
public class Server {

	// Server-wide state
	final Map<String, ClientConnection> activeSessions = new HashMap<>();
	final List<ClientConnection> waitingQueue = new ArrayList<>();
	final UserStore userStore = new UserStore("users.json");

	private int clientCount = 1;
	private final TheServer serverThread;
	private final Consumer<Serializable> callback;

	Server(Consumer<Serializable> callback) {
		this.callback = callback;
		serverThread = new TheServer();
		serverThread.start();
	}

	void clearAllUsers() {
		synchronized (this) {
			userStore.clearAll();
			log("All user data cleared by server operator.");
		}
	}

	// ACCEPTOR THREAD
	private class TheServer extends Thread {
		@Override
		public void run() {
			try (ServerSocket ss = new ServerSocket(5555)) {
				log("Server listening on port 5555");
				while (true) {
					Socket s = ss.accept();
					ClientConnection ct = new ClientConnection(Server.this, s, clientCount++);
					ct.setDaemon(true);
					ct.start();
					log("Client #" + ct.id + " connected from " + s.getInetAddress());
				}
			} catch (Exception e) {
				log("Server socket error: " + e.getMessage());
			}
		}
	}

	// FRIEND NOTIFICATION HELPERS
	private String buildFriendListPayload(String owner) {
		// get the user
		User u = userStore.get(owner);
		if (u == null)
			return "";
		// build the payload
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String f : u.getFriends()) {
			User fu = userStore.get(f);
			if (fu == null)
				continue;
			boolean online = activeSessions.containsKey(f);
			if (!first)
				sb.append(";");
			sb.append(f).append("|").append(online ? "1" : "0").append("|").append(fu.getWins()).append("|")
					.append(fu.getLosses()).append("|").append(fu.getElo());
			first = false;
		}
		return sb.toString();
	}

	// builds and sends the friend list to the target client
	void sendFriendListTo(ClientConnection target) {
		if (target == null || target.username == null)
			return;
		String payload;
		synchronized (this) {
			payload = buildFriendListPayload(target.username);
		}
		Message msg = new Message(Message.Type.FRIEND_LIST, payload);
		target.send(msg);
	}

	// Pushes the list of pending incoming friend requests to the given client.
	void pushPendingRequests(ClientConnection target) {
		if (target == null || target.username == null)
			return;
		User u = userStore.get(target.username);
		if (u == null)
			return;
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String req : u.getPendingFriendRequests()) {
			if (!first)
				sb.append(";");
			sb.append(req);
			first = false;
		}
		Message msg = new Message(Message.Type.PENDING_REQUESTS, sb.toString());
		target.send(msg);
	}

	// Called when a user goes online or offline.
	// Pushes a refreshed friend list to every currently-connected user,
	// so their panel shows the updated status.
	void notifyFriendsOfStatusChange() {
		List<ClientConnection> recipients = new ArrayList<>();
		synchronized (this) {
			for (ClientConnection other : activeSessions.values()) {
				if (other.username == null)
					continue;
				recipients.add(other);
			}
		}
		for (ClientConnection r : recipients)
			sendFriendListTo(r);
	}

	void log(String msg) {
		callback.accept("[Server] " + msg);
	}

	String signed(int n) {
		return (n >= 0 ? "+" : "") + n;
	}
}