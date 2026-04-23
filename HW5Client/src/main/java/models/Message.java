package models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable message passed between client and server.
 * All fields are public for simplicity; unused fields default to null/0.
 */
public class Message implements Serializable {
    static final long serialVersionUID = 47L;

    public enum Type {
        // Account flow
        REGISTER, // client→server : data = desired username
        REGISTER_OK, // server→client : data = username
        REGISTER_FAIL, // server→client : data = error reason
        LOGIN, // client→server : data = existing username
        LOGIN_OK, // server→client : data = username
        LOGIN_FAIL, // server→client : data = error reason

        // Matchmaking
        WAITING, // server→client : queued, waiting for opponent
        GAME_START, // server→client : data = opponent username, playerNum = 1 or 2

        // Gameplay
        MOVE, // client↔server : fromRow,fromCol,toRow,toCol
        CHAT, // client↔server : data = "username: text"
        GAME_OVER, // client→server or server→client : data = winning username or "DRAW"
        PLAY_AGAIN, // client→server : wants rematch
        QUIT_GAME, // client→server : disconnecting/returning to menu
        FORFEIT, // client→server : concede mid-game (counts as a loss)

        // User-info lookup
        GET_USER_INFO, // client→server : data = target username (self if null)
        USER_INFO, // server→client : data = username, wins, losses, online, friendsList

        // Friends
        ADD_FRIEND, // (legacy, still handled) client→server : data = friend username
        REMOVE_FRIEND, // client→server : data = friend username
        FRIEND_LIST, // server→client : data = semicolon-separated "name|online|wins|losses|elo"
        FRIEND_ACTION_RESULT, // server→client : data = status message

        // Friend-request flow
        SEND_FRIEND_REQUEST, // client→server : data = target username
        FRIEND_REQUEST_RECEIVED, // server→client : data = requester username
        ACCEPT_FRIEND_REQUEST, // client→server : data = requester username
        DECLINE_FRIEND_REQUEST, // client→server : data = requester username
        PENDING_REQUESTS, // server→client : data = semicolon-separated requester usernames

        // Direct Challenges / Rematches
        CHALLENGE, // client→server : data = target username
        CHALLENGE_INCOMING, // server→client : data = challenger username
        CHALLENGE_ACCEPT, // client→server : data = challenger username
        CHALLENGE_DECLINE, // client→server : data = challenger username
        CHALLENGE_REJECTED // server→client : data = error or decline reason
    }

    public String password;
    public Type type;
    public String data;
    public int fromRow = -1, fromCol = -1, toRow = -1, toCol = -1;
    public int playerNum; // 1 = RED (bottom), 2 = BLACK (top)

    public int wins;
    public int losses;
    public int elo;
    public int eloChange;
    public boolean online;
    public List<String> friends = new ArrayList<>();
    /** Each entry: "opponent|W or L or D|eloChange" — most recent first. */
    public List<String> matchHistory = new ArrayList<>();

    public Message() {
    }

    public Message(Type type) {
        this.type = type;
    }

    public Message(Type type, String data) {
        this.type = type;
        this.data = data;
    }

    public Message(Type type, String data, String password) {
        this.type = type;
        this.data = data;
        this.password = password;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", data=" + data + "}";
    }
}
