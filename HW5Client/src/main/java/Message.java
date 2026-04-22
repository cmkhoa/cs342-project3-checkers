import java.io.Serializable;
// ADDED: imports needed for the new friends-list payload field
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable message passed between client and server.
 * All fields are public for simplicity; unused fields default to null/0.
 */
public class Message implements Serializable {
    // CHANGED: bumped serialVersionUID from 42L -> 43L because new fields were added
    static final long serialVersionUID = 45L;

    public enum Type {
        // Account flow
        REGISTER,       // client→server : data = desired username
        REGISTER_OK,    // server→client : data = username
        REGISTER_FAIL,  // server→client : data = error reason
        // ADDED: separate LOGIN flow so the server can distinguish "sign in to existing account"
        //        from REGISTER ("create a new account"). Needed for persistent JSON user store.
        LOGIN,          // client→server : data = existing username
        LOGIN_OK,       // server→client : data = username
        LOGIN_FAIL,     // server→client : data = error reason

        // Matchmaking
        WAITING,        // server→client : queued, waiting for opponent
        GAME_START,     // server→client : data = opponent username, playerNum = 1 or 2

        // Gameplay
        MOVE,           // client↔server : fromRow,fromCol,toRow,toCol
        CHAT,           // client↔server : data = "username: text"
        // CHANGED: GAME_OVER is now client→server too (client reports the winning username),
        //          and the semantics are winner USERNAME instead of winner COLOUR so the
        //          server can record the result against the correct account.
        GAME_OVER,      // client→server or server→client : data = winning username or "DRAW"
        PLAY_AGAIN,     // client→server : wants rematch
        QUIT_GAME,      // client→server : disconnecting/returning to menu
        // ADDED: FORFEIT — mid-game concede. Server records it as a loss for the forfeiter.
        FORFEIT,        // client→server : concede mid-game (counts as a loss)

        // ADDED: user-info lookup (powers the profile panel)
        GET_USER_INFO,  // client→server : data = target username (self if null)
        USER_INFO,      // server→client : data = username, wins, losses, online, friendsList

        // ADDED: friends feature
        ADD_FRIEND,     // client→server : data = friend username
        REMOVE_FRIEND,  // client→server : data = friend username
        FRIEND_LIST,    // server→client : data = semicolon-separated "name|online|wins|losses"
        FRIEND_ACTION_RESULT  // server→client : data = status message (e.g. "Added X", "User not found")
    }
    public String password;
    public Type   type;
    public String data;
    public int    fromRow = -1, fromCol = -1, toRow = -1, toCol = -1;
    public int    playerNum; // 1 = RED (bottom), 2 = BLACK (top)

    // ADDED: payload fields used by USER_INFO responses
    public int     wins;
    public int     losses;
    public int elo;
    public int eloChange;
    public boolean online;
    public List<String> friends = new ArrayList<>();

    public Message() {}

    public Message(Type type) {
        this.type = type;
    }

    public Message(Type type, String data) {
        this.type = type;
        this.data = data;
    }

    public Message(Type type, String data, String password) {
        this.type     = type;
        this.data     = data;
        this.password = password;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", data=" + data + "}";
    }
}