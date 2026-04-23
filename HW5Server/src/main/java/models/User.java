package models;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a registered player on the server.
 */
public class User {

    private final String username;
    private String password;
    private boolean online;
    private int wins;
    private int losses;
    private int elo = 1000;
    private final Set<String> friends = new LinkedHashSet<>();
    private final Set<String> pendingFriendRequests = new LinkedHashSet<>();
    private final List<String> matchHistory = new ArrayList<>();

    // constructors
    public User(String username) {
        this.username = username;
        this.password = "";
        this.online = false;
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password == null ? "" : password;
        this.online = false;
    }

    public User(String username, String password, int wins, int losses, int elo, Set<String> friends,
            Set<String> pendingRequests) {
        this.username = username;
        this.password = password == null ? "" : password;
        this.wins = wins;
        this.losses = losses;
        this.elo = elo;
        this.online = false;
        if (friends != null)
            this.friends.addAll(friends);
        if (pendingRequests != null)
            this.pendingFriendRequests.addAll(pendingRequests);
    }

    // loading constructor with match history
    public User(String username, String password, int wins, int losses, int elo, Set<String> friends,
            Set<String> pendingRequests, List<String> history) {
        this(username, password, wins, losses, elo, friends, pendingRequests);
        if (history != null)
            this.matchHistory.addAll(history);
    }

    // Getters
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isOnline() {
        return online;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getElo() {
        return elo;
    }

    public Set<String> getFriends() {
        return friends;
    }

    // Setters
    public void setOnline(boolean online) {
        this.online = online;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public void setElo(int elo) {
        this.elo = elo;
    }

    public void addWin() {
        wins++;
    }

    public void addLoss() {
        losses++;
    }

    // friend-management methods
    public boolean addFriend(String name) {
        if (name == null || name.equals(username))
            return false;
        return friends.add(name);
    }

    public boolean removeFriend(String name) {
        return friends.remove(name);
    }

    public boolean hasFriend(String name) {
        return friends.contains(name);
    }

    // pending friend request methods
    public Set<String> getPendingFriendRequests() {
        return pendingFriendRequests;
    }

    public boolean addPendingRequest(String from) {
        if (from == null || from.equals(username))
            return false;
        return pendingFriendRequests.add(from);
    }

    public boolean removePendingRequest(String from) {
        return pendingFriendRequests.remove(from);
    }

    public boolean hasPendingRequest(String from) {
        return pendingFriendRequests.contains(from);
    }

    // match history
    public List<String> getMatchHistory() {
        return matchHistory;
    }

    // Add a record (most recent first). Keeps only 10 entries.
    public void addMatchRecord(String opponent, String result, int eloChange) {
        String sign = eloChange >= 0 ? "+" + eloChange : String.valueOf(eloChange);
        matchHistory.add(0, opponent + "|" + result + "|" + sign);
        while (matchHistory.size() > 10)
            matchHistory.remove(matchHistory.size() - 1);
    }

    @Override
    public String toString() {
        return username;
    }
}
