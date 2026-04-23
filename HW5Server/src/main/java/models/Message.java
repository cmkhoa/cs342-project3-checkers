package models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable message passed between client and server.
 * All fields are public for simplicity, unused fields default to null/0
 */
public class Message implements Serializable {
    static final long serialVersionUID = 47L;

    public enum Type {
        // Account flow
        REGISTER, // client -> server
        REGISTER_OK, // server -> client
        REGISTER_FAIL, // server -> client
        LOGIN, // client -> server
        LOGIN_OK, // server -> client
        LOGIN_FAIL, // server -> client

        // Matchmaking
        WAITING, // client -> server
        GAME_START, // server -> client

        // Gameplay
        MOVE, // client <-> server
        CHAT, // client <-> server
        GAME_OVER, // client -> server or server -> client
        PLAY_AGAIN, // client -> server
        QUIT_GAME, // client -> server
        FORFEIT, // client -> server

        // user-info lookup
        GET_USER_INFO, // client -> server
        USER_INFO, // server -> client

        // friends feature
        REMOVE_FRIEND, // client -> server
        FRIEND_LIST, // server -> client
        FRIEND_ACTION_RESULT, // server -> client

        // friend-request flow
        SEND_FRIEND_REQUEST, // client -> server
        FRIEND_REQUEST_RECEIVED, // server -> client
        ACCEPT_FRIEND_REQUEST, // client -> server
        DECLINE_FRIEND_REQUEST, // client -> server
        PENDING_REQUESTS, // server -> client

        // Direct Challenges / Rematches
        CHALLENGE, // client -> server
        CHALLENGE_INCOMING, // server -> client
        CHALLENGE_ACCEPT, // client -> server
        CHALLENGE_DECLINE, // client -> server
        CHALLENGE_REJECTED // server -> client
    }

    public Type type;
    public String data;
    public int fromRow = -1, fromCol = -1, toRow = -1, toCol = -1;
    public int playerNum; // 1 = RED (bottom), 2 = BLACK (top)

    // payload fields used by USER_INFO responses
    public int wins;
    public int losses;
    public int elo;
    public int eloChange;
    public boolean online;
    public List<String> friends = new ArrayList<>();
    /** Each entry: "opponent|W or L or D|eloChange" — most recent first. */
    public List<String> matchHistory = new ArrayList<>();
    public String password;

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