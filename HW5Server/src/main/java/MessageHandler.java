import models.Message;
import models.User;

import java.util.ArrayList;

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
        // fail safe
        if (uname == null || uname.trim().isEmpty()) {
            client.send(new Message(Message.Type.REGISTER_FAIL, "Username cannot be empty."));
            return;
        }
        if (password == null || password.isEmpty()) {
            client.send(new Message(Message.Type.REGISTER_FAIL, "Password cannot be empty."));
            return;
        }
        uname = uname.trim();

        // make sure user does not already exist, or is not already logged in
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

            // register user, set state, send success msg
            server.userStore.register(uname, password);
            client.username = uname;
            server.activeSessions.put(uname, client);
            server.userStore.setOnline(uname, true);
            server.log("Registered: " + client.username);
        }

        // valid registration, send success msg
        client.send(new Message(Message.Type.REGISTER_OK, client.username));
        server.notifyFriendsOfStatusChange();
        server.sendFriendListTo(client);
        server.pushPendingRequests(client);
        addToQueue();
    }

    private void handleLogin(String uname, String password) {
        // fail safe
        if (uname == null || uname.trim().isEmpty()) {
            client.send(new Message(Message.Type.LOGIN_FAIL, "Username cannot be empty."));
            return;
        }
        if (password == null || password.isEmpty()) {
            client.send(new Message(Message.Type.LOGIN_FAIL, "Password cannot be empty."));
            return;
        }
        uname = uname.trim();

        // make sure user exists, has right password, and is not already logged in
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

            // valid login, send login ok message and update state
            client.username = uname;
            server.activeSessions.put(uname, client);
            server.userStore.setOnline(uname, true);
            server.log("Logged in: " + client.username);
        }

        // send login ok message and update state
        client.send(new Message(Message.Type.LOGIN_OK, client.username));
        server.notifyFriendsOfStatusChange();
        server.sendFriendListTo(client);
        server.pushPendingRequests(client);
        addToQueue();
    }

    private void addToQueue() {
        synchronized (server) {
            // add client to queue if not already there
            if (!server.waitingQueue.contains(client))
                server.waitingQueue.add(client);
            client.send(new Message(Message.Type.WAITING, "In queue — " + server.waitingQueue.size() + " waiting"));
            server.log(client.username + " added to queue  (queue size: " + server.waitingQueue.size() + ")");
            tryMatch();
        }
    }

    private void tryMatch() {
        // check if there are at least 2 players in queue
        if (server.waitingQueue.size() < 2)
            return;

        // find best match by elo gap (o(N^2) optimization is possible but not required)
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

        // remove matched players and set their partner
        ClientConnection p1 = server.waitingQueue.remove(bestJ);
        ClientConnection p2 = server.waitingQueue.remove(bestI);
        p1.partner = p2;
        p1.gameResultRecorded = false;
        p2.partner = p1;
        p2.gameResultRecorded = false;

        // send game start message
        Message startP1 = new Message(Message.Type.GAME_START, p2.username);
        startP1.playerNum = 1;
        p1.send(startP1);
        Message startP2 = new Message(Message.Type.GAME_START, p1.username);
        startP2.playerNum = 2;
        p2.send(startP2);

        // log game start
        server.log("Game started: " + p1.username + " (RED, " + eloOf(p1.username) + ") vs " + p2.username + " (BLACK, "
                + eloOf(p2.username) + ")");
    }

    // return elo of user (default 1000 if not found)
    private int eloOf(String username) {
        User u = server.userStore.get(username);
        return u == null ? 1000 : u.getElo();
    }

    // records the game result and updates the elo of both players, called by the
    // winner
    private void handleGameOverReport(String winnerOrDraw) {
        synchronized (server) {
            // if the client is not in a game or the result has already been recorded,
            // return
            if (client.partner == null || client.username == null)
                return;
            if (client.gameResultRecorded)
                return;
            client.gameResultRecorded = true;
            client.partner.gameResultRecorded = true;

            // get the usernames of the two players
            String a = client.username;
            String b = client.partner.username;
            // update stats based on winnerOrDraw
            if ("DRAW".equalsIgnoreCase(winnerOrDraw)) {
                int[] d = server.userStore.applyEloUpdate(a, b, true);
                client.lastEloChange = d[0];
                client.partner.lastEloChange = d[1];
                server.userStore.recordMatch(a, b, "D", d[0]);
                server.userStore.recordMatch(b, a, "D", d[1]);
                server.log("Game ended in a draw: " + a + " vs " + b);
            } else if (a.equals(winnerOrDraw)) {
                // a won
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
                // b won
                server.userStore.recordWin(b);
                server.userStore.recordLoss(a);
                int[] d = server.userStore.applyEloUpdate(b, a, false);
                client.partner.lastEloChange = d[0];
                client.lastEloChange = d[1];
                server.userStore.recordMatch(b, a, "W", d[0]);
                server.userStore.recordMatch(a, b, "L", d[1]);
                server.log("Result recorded: " + b + " defeated " + a + "  (" + server.signed(d[0]) + " / "
                        + server.signed(d[1]) + ")");
            } else {
                server.log("GAME_OVER received with unexpected winner: " + winnerOrDraw);
            }
            pushSelfUserInfo(client);
            pushSelfUserInfo(client.partner);
        }
    }

    // adds the user back to the queue and removes them from the current game
    private void handlePlayAgain() {
        synchronized (server) {
            if (client.partner != null) {
                client.partner.partner = null;
                client.partner = null;
            }
        }
        addToQueue();
    }

    // handles the quit game message, removing the client from the game and queue
    private void handleQuitGame() {
        synchronized (server) {
            if (client.partner != null) {
                Message note = new Message(Message.Type.QUIT_GAME, client.username + " left the game.");
                client.partner.send(note);
                client.partner.partner = null;
                client.partner = null;
            }
            server.waitingQueue.remove(client);
        }
    }

    // handles the forfeit message, removing the client from the game and queue
    private void handleForfeit() {
        synchronized (server) {
            // if the client is not in a game or the result has already been recorded,
            // return
            if (client.partner == null || client.gameResultRecorded)
                return;
            client.gameResultRecorded = true;
            client.partner.gameResultRecorded = true;

            // record the forfeit as a win for the other player
            server.log(client.username + " forfeited — " + client.partner.username + " wins");
            server.userStore.recordWin(client.partner.username);
            server.userStore.recordLoss(client.username);

            // apply elo update
            int[] d = server.userStore.applyEloUpdate(client.partner.username, client.username, false);
            client.partner.lastEloChange = d[0];
            client.lastEloChange = d[1];
            server.userStore.recordMatch(client.partner.username, client.username, "W", d[0]);
            server.userStore.recordMatch(client.username, client.partner.username, "L", d[1]);

            // push game over message to both players
            Message notice = new Message(Message.Type.GAME_OVER, client.partner.username);
            notice.eloChange = client.partner.lastEloChange;
            client.partner.send(notice);

            Message selfNotice = new Message(Message.Type.GAME_OVER, client.partner.username);
            selfNotice.eloChange = client.lastEloChange;
            client.send(selfNotice);

            // update elo for both players
            pushSelfUserInfo(client);
            pushSelfUserInfo(client.partner);

            // remove the players from the game
            client.partner.partner = null;
            client.partner = null;
        }
    }

    // handles the get user info message
    private void handleGetUserInfo(String target) {
        // get the target username (default to current user if not specified)
        String query = (target == null || target.trim().isEmpty()) ? client.username : target.trim();
        User u = server.userStore.get(query);

        // if user not found, send failure message + default stats
        if (u == null) {
            Message fail = new Message(Message.Type.USER_INFO);
            fail.data = query;
            fail.wins = -1;
            fail.losses = -1;
            fail.online = false;
            client.send(fail);
            return;
        }

        // send user info
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

    // pushes the target user's info to the target client (called by server)
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

    // handles the remove friend message
    private void handleRemoveFriend(String friendName) {
        // fail if username or friendName is null
        if (client.username == null || friendName == null)
            return;
        friendName = friendName.trim();
        // remove friend
        boolean removed = server.userStore.removeFriend(client.username, friendName);
        // send result
        if (removed) {
            server.log(client.username + " removed friend: " + friendName);
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "Removed " + friendName + "."));
        } else {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, friendName + " is not in your friends list."));
        }
        // update friend list
        server.sendFriendListTo(client);
    }

    // handles the send friend request message
    private void handleSendFriendRequest(String targetName) {
        // fail if username is null
        if (client.username == null)
            return;
        if (targetName == null || targetName.trim().isEmpty()) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "Enter a username."));
            return;
        }
        targetName = targetName.trim();

        // fail if friendName is same as username
        if (targetName.equals(client.username)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "You can't add yourself."));
            return;
        }

        // fail if user does not exist
        if (!server.userStore.exists(targetName)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT,
                    "No user named \"" + targetName + "\"."));
            return;
        }
        // fail if user is already a friend
        User self = server.userStore.get(client.username);
        if (self != null && self.hasFriend(targetName)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, targetName + " is already your friend."));
            return;
        }

        // fail if user has already sent a friend request to the target
        User target = server.userStore.get(targetName);
        if (target != null && target.hasPendingRequest(client.username)) {
            client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "Request already sent to " + targetName + "."));
            return;
        }

        // accept if user has already sent a friend request to the target
        if (self != null && self.hasPendingRequest(targetName)) {
            acceptRequest(targetName);
            return;
        }

        // create pending request
        server.userStore.addPendingRequest(targetName, client.username);
        server.log(client.username + " sent friend request to " + targetName);
        client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "Friend request sent to " + targetName + "."));

        // notify recipient of friend request
        synchronized (server) {
            ClientConnection recipientThread = server.activeSessions.get(targetName);
            if (recipientThread != null) {
                recipientThread.send(new Message(Message.Type.FRIEND_REQUEST_RECEIVED, client.username));
                server.pushPendingRequests(recipientThread);
            }
        }
    }

    // handles the accept friend request message
    private void handleAcceptFriendRequest(String requesterName) {
        if (client.username == null || requesterName == null)
            return;
        requesterName = requesterName.trim();
        acceptRequest(requesterName);
    }

    // accepts friend request (called by handleAcceptFriendRequest and
    // handleSendFriendRequest)
    private void acceptRequest(String requesterName) {
        // remove pending request
        server.userStore.removePendingRequest(client.username, requesterName);
        // add to friends list
        server.userStore.addFriend(client.username, requesterName);
        server.userStore.addFriend(requesterName, client.username);
        server.log(client.username + " accepted friend request from " + requesterName);
        // send success message
        client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "You and " + requesterName + " are now friends!"));
        // update friend list
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

    // handles the decline friend request message
    private void handleDeclineFriendRequest(String requesterName) {
        if (client.username == null || requesterName == null)
            return;
        requesterName = requesterName.trim();
        // remove pending request
        server.userStore.removePendingRequest(client.username, requesterName);
        server.log(client.username + " declined friend request from " + requesterName);
        // send result
        client.send(new Message(Message.Type.FRIEND_ACTION_RESULT, "Declined request from " + requesterName + "."));
        // update pending requests
        server.pushPendingRequests(client);
    }

    // handles the challenge message
    private void handleChallenge(String targetName) {
        if (client.username == null || targetName == null)
            return;
        targetName = targetName.trim();
        // fail if target is offline
        ClientConnection target = null;
        synchronized (server) {
            target = server.activeSessions.get(targetName);
        }
        if (target == null) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, targetName + " is offline."));
            return;
        }
        // fail if target is already in a match
        if (target.partner != null && target.partner != client) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, targetName + " is currently in a match."));
            return;
        }
        // send incoming challenge
        target.send(new Message(Message.Type.CHALLENGE_INCOMING, client.username));
    }

    // handles the challenge accept message
    private void handleChallengeAccept(String challengerName) {
        if (client.username == null || challengerName == null)
            return;
        challengerName = challengerName.trim();
        // fail if challenger is offline
        ClientConnection challenger = null;
        synchronized (server) {
            challenger = server.activeSessions.get(challengerName);
        }

        if (challenger == null) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, challengerName + " went offline."));
            return;
        }
        // fail if challenger is already in a match
        if (challenger.partner != null && challenger.partner != client) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, challengerName + " started another match."));
            return;
        }
        // start the match
        synchronized (server) {
            server.waitingQueue.remove(client);
            server.waitingQueue.remove(challenger);

            // set partners
            client.partner = challenger;
            challenger.partner = client;
            client.gameResultRecorded = false;
            challenger.gameResultRecorded = false;

            // assign player numbers randomly
            int r = (int) (Math.random() * 2);
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

            // set red and black names
            String redName = (r == 0) ? challenger.username : client.username;
            String blackName = (r == 0) ? client.username : challenger.username;
            // log game start
            server.log("Game started: " + redName + " (RED, " + eloOf(redName) + ") vs " + blackName + " (BLACK, "
                    + eloOf(blackName) + ")");
        }
    }

    // handles the challenge decline message
    private void handleChallengeDecline(String challengerName) {
        if (client.username == null || challengerName == null)
            return;
        challengerName = challengerName.trim();
        ClientConnection challenger = null;
        synchronized (server) {
            challenger = server.activeSessions.get(challengerName);
        }
        // fail if challenger is offline
        if (challenger == null) {
            client.send(new Message(Message.Type.CHALLENGE_REJECTED, challengerName + " went offline."));
            return;
        }
        // send decline message
        challenger.send(new Message(Message.Type.CHALLENGE_REJECTED, client.username + " declined your challenge."));
    }
}
