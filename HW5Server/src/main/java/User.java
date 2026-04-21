// ADDED: imports for the friends Set
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
    private       boolean online;
    private       int     wins;
    private       int     losses;
    // ADDED: friends set (preserves insertion order so the UI list is stable)
    private final Set<String> friends = new LinkedHashSet<>();

    public User(String username) {
        this.username = username;
        //  default online was true; now we default to false and flip it
        //  to true only when the user actually connects. This matches the
        //  real lifecycle now that users persist across server restarts.
        this.online   = false;
    }

    // constructor used by UserStore when loading users from users.json
    public User(String username, int wins, int losses, Set<String> friends) {
        this.username = username;
        this.wins     = wins;
        this.losses   = losses;
        this.online   = false;
        if (friends != null) this.friends.addAll(friends);
    }

    public String  getUsername(){ return username; }
    public boolean isOnline()   { return online;   }
    public int     getWins()    { return wins;   }
    public int     getLosses()  { return losses; }
    // accessor for the friends set (used by UserStore and Server)
    public Set<String> getFriends() { return friends; }

    public void setOnline(boolean online) { this.online = online; }
    public void addWin()    { wins++;   }
    public void addLoss()   { losses++; }

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
