import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a registered player on the server.
 * Win/loss tracking is left as a stub for the future JSON-persistence feature.
 */
// NOTE: win/loss tracking is no longer a stub — it is now wired up through
//       UserStore and the server's GAME_OVER / FORFEIT handlers.
public class User {
    private final String  username;
    private       String  password;
    private       boolean online;
    private       int     wins;
    private       int     losses;
    private       int     elo = 1000;  // ADDED: Elo rating, defaults to 1000
    // ADDED: friends set (preserves insertion order so the UI list is stable)
    private final Set<String> friends = new LinkedHashSet<>();

    public User(String username) {
        this.username = username;
        this.password = "";
        this.online   = false;
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password == null ? "" : password;
        this.online   = false;
    }

    // constructor used by UserStore when loading users from users.json
    // added elo parameter
    public User(String username, String password, int wins, int losses, int elo, Set<String> friends) {
        this.username = username;
        this.password = password == null ? "" : password;
        this.wins     = wins;
        this.losses   = losses;
        this.elo      = elo;
        this.online   = false;
        if (friends != null) this.friends.addAll(friends);
    }

    public String  getUsername(){ return username; }
    public String  getPassword(){ return password; }
    public boolean isOnline()   { return online;   }
    public int     getWins()    { return wins;   }
    public int     getLosses()  { return losses; }
    // accessor for the friends set (used by UserStore and Server)
    public Set<String> getFriends() { return friends; }

    public void setOnline(boolean online) { this.online = online; }
    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }
    public void addWin()    { wins++;   }
    public void addLoss()   { losses++; }

    public int  getElo()         { return elo; }
    public void setElo(int elo)  { this.elo = elo; }

    // friend-management methods
    public boolean addFriend(String name) {
        if (name == null || name.equals(username)) return false;
        return friends.add(name);
    }

    public boolean removeFriend(String name) {
        return friends.remove(name);
    }

    public boolean hasFriend(String name) {
        return friends.contains(name);
    }

    @Override public String toString() { return username; }
}
