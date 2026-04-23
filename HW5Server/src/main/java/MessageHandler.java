import models.Message;


import java.util.ArrayList;
import java.util.List;

/**
 * Handles all incoming messages for a specific client connection.
 * Extracted from Server.java to reduce file size.
 */
class MessageHandler {

    private final Server server;
    private final ClientConnection client;

    MessageHandler(Server server, ClientConnection client) {
        this.server = server;
        this.client = client;
    }

    void handleMessage(Message msg) {
        switch (msg.type) {
            case REGISTER:
                handleRegister(msg.data, msg.password);
                break;
            case LOGIN:
                handleLogin(msg.data, msg.password);
                break;
            case MOVE:
            case CHAT:
                if (client.partner != null)
                    client.partner.send(msg);
                break;
            case GAME_OVER:
                handleGameOverReport(msg.data);
                break;
            case PLAY_AGAIN:
                handlePlayAgain();
                break;
            case QUIT_GAME:
                handleQuitGame();
                break;
            case FORFEIT:
                handleForfeit();
                break;
            case GET_USER_INFO:
                handleGetUserInfo(msg.data);
                break;
            case ADD_FRIEND:
                handleAddFriend(msg.data);
                break;
            case REMOVE_FRIEND:
                handleRemoveFriend(msg.data);
                break;
            case SEND_FRIEND_REQUEST:
                handleSendFriendRequest(msg.data);
                break;
            case ACCEPT_FRIEND_REQUEST:
                handleAcceptFriendRequest(msg.data);
                break;
            case DECLINE_FRIEND_REQUEST:
                handleDeclineFriendRequest(msg.data);
                break;
            case CHALLENGE:
                handleChallenge(msg.data);
                break;
            case CHALLENGE_ACCEPT:
                handleChallengeAccept(msg.data);
                break;
            case CHALLENGE_DECLINE:
                handleChallengeDecline(msg.data);
                break;
            default:
                server.log("Unknown message type from " + client.displayName() + ": " + msg.type);
        }
    }

    private void handleRegister(String uname, String password) {
        if (uname == null || uname.trim().isEmpty()) {
            client.send(new Message(Message.Type.REGISTER_FAIL, "Username cannot be empty."));
            return;
        }
        if (password == null || password.isEmpty()) {
            client.send(new Message(Message.Type.REGISTER_FAIL, "Password cannot be empty."));
            return;
        }
        uname = uname.trim();

        synchronized (server) {
            if (server.userStore.exists(uname)) {
                client.send(new Message(Message.Type.REGISTER_FAIL,
                        "\"" + uname + "\" is already taken. Please choose another."));
                server.log("Registration rejected (exists): " + uname);
                return;
            }
            if (server.activeSessions.containsKey(uname)) {
                client.send(new Message(Message.Type.REGISTER_FAIL,
                        "\"" + uname + "\" is currently in use."));
                return;
            }
            server.userStore.register(uname, password);
            client.username = uname;
            server.activeSessions.put(uname, client);
            server.userStore.setOnline(uname, true);
            server.log("Registered: " + client.username);
        }

        client.send(new Message(Message.Type.REGISTER_OK, client.username));
        server.notifyFriendsOfStatusChange();
        server.sendFriendListTo(client);
        server.pushPendingRequests(client);
        addToQueue();
    }

    private void handleLogin(String uname, String password) {
        if (uname == null || uname.trim().isEmpty()) {
            client.send(new Message(Message.Type.LOGIN_FAIL, "Username cannot be empty."));
            return;
        }
        if (password == null || password.isEmpty()) {
            client.send(new Message(Message.Type.REGISTER_FAIL, "Password cannot be empty."));
            return;
        }
        uname = uname.trim();

        synchronized (server) {
            if (!server.userStore.exists(uname)) {
                client.send(new Message(Message.Type.LOGIN_FAIL,
                        "No account named \"" + uname + "\". Register first."));
                return;
            }
            if (!server.userStore.checkPassword(uname, password)) {
                client.send(new Message(Message.Type.LOGIN_FAIL,
                        "Incorrect password for \"" + uname + "\"."));
                server.log("Login rejected (bad password): " + uname);
                return;
            }
            if (server.activeSessions.containsKey(uname)) {
                client.send(new Message(Message.Type.LOGIN_FAIL,
                        "\"" + uname + "\" is already logged in elsewhere."));
                return;
            }

            client.username = uname;
            server.activeSessions.put(uname, client);
            server.userStore.setOnline(uname, true);
            server.log("Logged in: " + client.username);
        }

        client.send(new Message(Message.Type.LOGIN_OK, client.username));
        server.notifyFriendsOfStatusChange();
        server.sendFriendListTo(client);
        server.pushPendingRequests(client);
        addToQueue();
    }

    private void addToQueue() {
        synchronized (server) {
            if (!server.waitingQueue.contains(client))
                server.waitingQueue.add(client);
            client.send(new Message(Message.Type.WAITING,
                    "In queue — " + server.waitingQueue.size() + " waiting"));
            server.log(client.username + " added to queue  (queue size: " + server.waitingQueue.size() + ")");
            tryMatch();
        }
    }

    private void tryMatch() {
        if (server.waitingQueue.size() < 2)
            return;

        int bestI = 0, bestJ = 1, bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < server.waitingQueue.size(); i++) {
            for (int j = i + 1; j < server.waitingQueue.size(); j++) {
                int ei = eloOf(server.waitingQueue.get(i).username);
                int ej = eloOf(server.waitingQueue.get(j).username);
                int gap = Math.abs(ei - ej);
                if (gap < bestGap) {
                    bestGap = gap;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        ClientConnection p1 = server.waitingQueue.remove(bestJ);
        ClientConnection p2 = server.waitingQueue.remove(bestI);

        p1.partner = p2;
        p2.partner = p1;
        p1.gameResultRecorded = false;
        p2.gameResultRecorded = false;

        Message startP1 = new Message(Message.Type.GAME_START, p2.username);
        startP1.playerNum = 1;

        Message startP2 = new Message(Message.Type.GAME_START, p1.username);
        startP2.playerNum = 2;

        p1.send(startP1);
        p2.send(startP2);

        server.log("Game started: " + p1.username + " (RED, " + eloOf(p1.username) + ") vs "
                + p2.username + " (BLACK, " + eloOf(p2.username) + ")");
    }

    private int eloOf(String username) {
        User u = server.userStore.get(username);
        return u == null ? 1000 : u.getElo();
    }

    private void handleGameOverReport(String winnerOrDraw) {
        synchronized (server) {
            if (client.partner == null || client.username == null)
                return;
            if (client.gameResultRecorded)
                return;
            client.gameResultRecorded = true;
            client.partner.gameResultRecorded = true;

            String a = client.username;
            String b = client.partner.username;
            if ("DRAW".equalsIgnoreCase(winnerOrDraw)) {
                int[] d = server.userStore.applyEloUpdate(a, b, true);
                client.lastEloChange = d[0];
                client.partner.lastEloChange = d[1];
                server.userStore.recordMatch(a, b, "D", d[0]);
                server.userStore.recordMatch(b, a, "D", d[1]);
                server.log("Game ended in a draw: " + a + " vs " + b);
            } else if (a.equals(winnerOrDraw)) {
                server.userStore.recordWin(a);
                server.userStore.recordLoss(b);
                int[] d = server.userStore.applyEloUpdate(a, b, false);
                client.lastEloChange = d[0];
                client.partner.lastEloChange = d[1];
                server.userStore.recordMatch(a, b, "W", d[0]);
                server.userStore.recordMatch(b, a, "L", d[1]);
                server.log("Result recorded: " + a + " defeated " + b
                        + "  (" + server.signed(d[0]) + " / " + server.signed(d[1]) + ")");
            } else if (b.equals(winnerOrDraw)) {
                server.userStore.recordWin(b);
                server.userStore.recordLoss(a);
                int[] d = server.userStore.applyEloUpdate(b, a, false);
                client.partner.lastEloChange = d[0];
                client.lastEloChange = d[1];
                server.userStore.recordMatch(b, a, "W", d[0]);
                server.userStore.recordMatch(a, b, "L", d[1]);
                server.log("Result recorded: " + b + " defeated " + a + "  (" + server.signed(d[0]) + " / " + server.signed(d[1]) + ")");
            } else {
                server.log("GAME_OVER received with unexpected winner: " + winnerOrDraw);
            }
            pushSelfUserInfo(client);
            pushSelfUserInfo(client.partner);
        }
    }

    private void handlePlayAgain() {
        synchronized (server) {
            server.log(client.username + " wants to play again");
            if (client.partner != null) {
                client.partner.partner = null;
                client.partner = null;
            }
        }
        addToQueue();
    }

    private void handleQuitGame() {
        synchronized (server) {
            server.log(client.username + " quit the game");
            if (client.partner != null) {
                Message note = new Message(Message.Type.QUIT_GAME,
                        client.username + " left the game.");
                client.partner.send(note);
                client.partner.partner = null;
                client.partner = null;
            }
            server.waitingQueue.remove(client);
        }
    }

    private void handleForfeit() {
        synchronized (server) {
            if (client.partner == null || client.gameResultRecorded)
                return;
            client.gameResultRecorded = true;
            client.partner.gameResultRecorded = true;

            server.log(client.username + " forfeited — " + client.partner.username + " wins");
            server.userStore.recordWin(client.partner.username);
            server.userStore.recordLoss(client.username);

            int[] d = server.userStore.applyEloUpdate(client.partner.username, client.username, false);
            client.partner.lastEloChange = d[0];
            client.lastEloChange = d[1];
            server.userStore.recordMatch(client.partner.username, client.username, "W", d[0]);
            server.userStore.recordMatch(client.username, client.partner.username, "L", d[1]);

            Message notice = new Message(Message.Type.GAME_OVER, client.partner.username);
            client.partner.send(notice);
            notice.eloChange = client.partner.lastEloChange;

            Message selfNotice = new Message(Message.Type.GAME_OVER, client.partner.username);
            client.send(selfNotice);
            notice.eloChange = client.partner.lastEloChange;

            pushSelfUserInfo(client);
            pushSelfUserInfo(client.partner);

            client.partner.partner = null;
            client.partner = null;
        }
    }

    private void handleGetUserInfo(String target) {
        String query = (target == null || target.trim().isEmpty()) ? client.username : target.trim();
        User u = server.userStore.get(query);
        if (u == null) {
            Message fail = new Message(Message.Type.USER_INFO);
            fail.data = query;
            fail.wins = -1;
            fail.losses = -1;
            fail.online = false;
            client.send(fail);
            return;
        }
        Message info = new Message(Message.Type.USER_INFO);
        info.data = u.getUsername();
        info.wins = u.getWins();
        info.losses = u.getLosses();
        info.elo = u.getElo();
        info.online = server.activeSessions.containsKey(u.getUsername());
        info.friends = new ArrayList<>(u.getFriends());
        info.matchHistory = new ArrayList<>(u.getMatchHistory());
        client.send(info);
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
        info.friends = new ArrayList<>(u.getFriends());
        info.matchHistory = new ArrayList<>(u.getMatchHistory());
        target.send(info);
    }

    private void handleAddFriend(String friendName) {
        if (client.username == null)
            return;
        if (friendName == null || friendName.trim().isEmpty()) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "Enter a username."));
            return;
        }
        friendName = friendName.trim();
        if (friendName.equals(client.username)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "You can't add yourself."));
            return;
        }
        if (!server.userStore.exists(friendName)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    "No user named \"" + friendName + "\"."));
            return;
        }
        boolean added = server.userStore.addFriend(client.username, friendName);
        if (added) {
            server.log(client.username + " added friend: " + friendName);
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    "Added " + friendName + " as a friend."));
        } else {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    friendName + " is already your friend."));
        }
        server.sendFriendListTo(client);
    }

    private void handleRemoveFriend(String friendName) {
        if (client.username == null || friendName == null)
            return;
        friendName = friendName.trim();
        boolean removed = server.userStore.removeFriend(client.username, friendName);
        if (removed) {
            server.log(client.username + " removed friend: " + friendName);
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    "Removed " + friendName + "."));
        } else {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    friendName + " is not in your friends list."));
        }
        server.sendFriendListTo(client);
    }

    private void handleSendFriendRequest(String targetName) {
        if (client.username == null)
            return;
        if (targetName == null || targetName.trim().isEmpty()) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "Enter a username."));
            return;
        }
        targetName = targetName.trim();
        if (targetName.equals(client.username)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "You can't add yourself."));
            return;
        }
        if (!server.userStore.exists(targetName)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    "No user named \"" + targetName + "\"."));
            return;
        }
        User self = server.userStore.get(client.username);
        if (self != null && self.hasFriend(targetName)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    targetName + " is already your friend."));
            return;
        }
        User target = server.userStore.get(targetName);
        if (target != null && target.hasPendingRequest(client.username)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    "Request already sent to " + targetName + "."));
            return;
        }
        if (self != null && self.hasPendingRequest(targetName)) {
            acceptRequest(targetName);
            return;
        }
        server.userStore.addPendingRequest(targetName, client.username);
        server.log(client.username + " sent friend request to " + targetName);
        client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                "Friend request sent to " + targetName + "."));
        synchronized (server) {
            ClientConnection recipientThread = server.activeSessions.get(targetName);
            if (recipientThread != null) {
                recipientThread.send(new Message(
                        Message.Type.FRIEND_REQUEST_RECEIVED, client.username));
                server.pushPendingRequests(recipientThread);
            }
        }
    }

    private void handleAcceptFriendRequest(String requesterName) {
        if (client.username == null || requesterName == null)
            return;
        requesterName = requesterName.trim();
        acceptRequest(requesterName);
    }

    private void acceptRequest(String requesterName) {
        server.userStore.removePendingRequest(client.username, requesterName);
        server.userStore.addFriend(client.username, requesterName);
        server.userStore.addFriend(requesterName, client.username);
        server.log(client.username + " accepted friend request from " + requesterName);
        client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                "You and " + requesterName + " are now friends!"));
        server.sendFriendListTo(client);
        server.pushPendingRequests(client);
        synchronized (server) {
            ClientConnection requesterThread = server.activeSessions.get(requesterName);
            if (requesterThread != null) {
                requesterThread.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                        client.username + " accepted your friend request!"));
                server.sendFriendListTo(requesterThread);
            }
        }
    }

    private void handleDeclineFriendRequest(String requesterName) {
        if (client.username == null || requesterName == null)
            return;
        requesterName = requesterName.trim();
        server.userStore.removePendingRequest(client.username, requesterName);
        server.log(client.username + " declined friend request from " + requesterName);
        client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                "Declined request from " + requesterName + "."));
        server.pushPendingRequests(client);
    }

    private void handleChallenge(String targetName) {
        if (client.username == null || targetName == null) return;
        ClientConnection target = null;
        synchronized (server) {
            target = server.activeSessions.get(targetName);
        }
        
        if (target == null) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, targetName + " is offline."));
            return;
        }
        
        if (target.partner != null && target.partner != client) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, targetName + " is currently in a match."));
            return;
        }
        
        target.send(new Message(Message.Type.CHALLENGE_INCOMING, client.username));
    }

    private void handleChallengeAccept(String challengerName) {
        if (client.username == null || challengerName == null) return;
        ClientConnection challenger = null;
        synchronized (server) {
            challenger = server.activeSessions.get(challengerName);
        }
        
        if (challenger == null) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, challengerName + " went offline."));
            return;
        }
        
        if (challenger.partner != null && challenger.partner != client) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, challengerName + " started another match."));
            return;
        }
        
        synchronized (server) {
            server.waitingQueue.remove(client);
            server.waitingQueue.remove(challenger);
            
            client.partner = challenger;
            challenger.partner = client;
            client.gameResultRecorded = false;
            challenger.gameResultRecorded = false;
            
            int r = (int)(Math.random() * 2);
            if (r == 0) {
                Message m1 = new Message(Message.Type.GAME_START, challenger.username);
                m1.playerNum = 1;
                client.send(m1);
                
                Message m2 = new Message(Message.Type.GAME_START, client.username);
                m2.playerNum = 2;
                client.partner.send(m2);
            } else {
                Message m1 = new Message(Message.Type.GAME_START, client.username);
                m1.playerNum = 1;
                client.partner.send(m1);
                
                Message m2 = new Message(Message.Type.GAME_START, challenger.username);
                m2.playerNum = 2;
                client.send(m2);
            }
            String redName = (r == 0) ? challenger.username : client.username;
            String blackName = (r == 0) ? client.username : challenger.username;
            server.log("Game started: " + redName + " (RED, " + eloOf(redName) + ") vs " 
                    + blackName + " (BLACK, " + eloOf(blackName) + ")");
        }
    }

    private void handleChallengeDecline(String challengerName) {
        if (client.username == null || challengerName == null) return;
        ClientConnection challenger = null;
        synchronized (server) {
            challenger = server.activeSessions.get(challengerName);
        }
        
        if (challenger != null) {
            challenger.send(new Message(Message.Type.CHALLENGE_REJECTED, client.username + " declined your challenge."));
        }
    }
}
