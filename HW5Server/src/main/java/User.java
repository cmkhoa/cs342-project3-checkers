/**
 * Represents a registered player on the server.
 * Win/loss tracking is left as a stub for the future JSON-persistence feature.
 */
public class User {
    private final String  username;
    private       boolean online;
    private       int     wins;
    private       int     losses;

    public User(String username) {
        this.username = username;
        this.online   = true;
    }

    public String  getUsername(){ return username; }
    public boolean isOnline()   { return online;   }
    public int     getWins()    { return wins;   }
    public int     getLosses()  { return losses; }

    public void setOnline(boolean online) { this.online = online; }
    public void addWin()    { wins++;   }
    public void addLoss()   { losses++; }

    @Override public String toString() { return username; }
}
