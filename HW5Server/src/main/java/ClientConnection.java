import models.Message;
import models.User;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handles the network I/O for a connected client.
 * Extracted from Server.java to reduce file size.
 */
class ClientConnection extends Thread {

    private final Server server;
    final int id;
    final Socket connection;
    ObjectInputStream in;
    ObjectOutputStream out;

    String username = null;
    ClientConnection partner = null; // null when not in a game

    // flag used to avoid double-counting a completed game. Both the
    // winner and the loser may report GAME_OVER, so we record stats
    // only for the first report and mark both threads as done.
    boolean gameResultRecorded = false;
    int lastEloChange = 0;

    private final MessageHandler messageHandler;

    ClientConnection(Server server, Socket s, int id) {
        this.server = server;
        this.connection = s;
        this.id = id;
        this.messageHandler = new MessageHandler(server, this);
    }

    void send(Message msg) {
        if (out == null)
            return;
        try {
            synchronized (out) {
                out.writeObject(msg);
                out.flush();
                out.reset();
            }
        } catch (Exception e) {
            server.log("Send to client #" + id + " failed: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(connection.getOutputStream());
            out.flush();
            in = new ObjectInputStream(connection.getInputStream());
            connection.setTcpNoDelay(true);
        } catch (Exception e) {
            server.log("Stream setup failed for client #" + id);
            return;
        }

        while (true) {
            try {
                Message msg = (Message) in.readObject();
                messageHandler.handleMessage(msg);
            } catch (Exception e) {
                handleDisconnect();
                break;
            }
        }
    }

    private void handleDisconnect() {
        synchronized (server) {
            String name = username != null ? username : "Client #" + id;
            server.log(name + " disconnected");

            if (username != null) {
                if (partner != null && !gameResultRecorded) {
                    gameResultRecorded = true;
                    partner.gameResultRecorded = true;
                    server.userStore.recordWin(partner.username);
                    server.userStore.recordLoss(username);
                    int[] d = server.userStore.applyEloUpdate(partner.username, username, false);
                    partner.lastEloChange = d[0];
                    Message notice = new Message(Message.Type.GAME_OVER, partner.username);
                    partner.send(notice);
                    notice.eloChange = partner.lastEloChange;
                    server.log("Disconnect counted as forfeit: " + partner.username + " wins");
                    pushSelfUserInfo(partner);
                }
                server.activeSessions.remove(username);
                server.userStore.setOnline(username, false);
            }
            server.waitingQueue.remove(this);

            if (partner != null) {
                Message note = new Message(Message.Type.QUIT_GAME,
                        name + " disconnected.");
                partner.send(note);
                partner.partner = null;
                partner = null;
            }
        }
        server.notifyFriendsOfStatusChange();
        try {
            connection.close();
        } catch (Exception ignored) {
        }
    }

    private void pushSelfUserInfo(ClientConnection target) {
        if (target == null || target.username == null)
            return;
        User u = server.userStore.get(target.username);
        if (u == null)
            return;
        Message info = new Message(Message.Type.USER_INFO);
        info.data = u.getUsername();
        info.wins = u.getWins();
        info.losses = u.getLosses();
        info.elo = u.getElo();
        info.online = true;
        info.friends = new java.util.ArrayList<>(u.getFriends());
        info.matchHistory = new java.util.ArrayList<>(u.getMatchHistory());
        target.send(info);
    }

    String displayName() {
        return username != null ? username : "client #" + id;
    }
}
