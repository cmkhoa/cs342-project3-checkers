import java.io.Serializable;

/**
 * Serializable message passed between client and server.
 * All fields are public for simplicity; unused fields default to null/0.
 */
public class Message implements Serializable {
    static final long serialVersionUID = 42L;

    public enum Type {
        // Account flow
        REGISTER,       // client→server : data = desired username
        REGISTER_OK,    // server→client : data = username
        REGISTER_FAIL,  // server→client : data = error reason

        // Matchmaking
        WAITING,        // server→client : queued, waiting for opponent
        GAME_START,     // server→client : data = opponent username, playerNum = 1 or 2

        // Gameplay
        MOVE,           // client↔server : fromRow,fromCol,toRow,toCol
        CHAT,           // client↔server : data = "username: text"
        GAME_OVER,      // server→client : data = "RED"/"BLACK"/"DRAW" (winner colour)
        PLAY_AGAIN,     // client→server : wants rematch
        QUIT_GAME       // client→server : disconnecting/returning to menu
    }

    public Type   type;
    public String data;
    public int    fromRow = -1, fromCol = -1, toRow = -1, toCol = -1;
    public int    playerNum; // 1 = RED (bottom), 2 = BLACK (top)

    public Message() {}

    public Message(Type type) {
        this.type = type;
    }

    public Message(Type type, String data) {
        this.type = type;
        this.data = data;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", data=" + data + "}";
    }
}
