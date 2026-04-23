import models.User;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Persistent JSON-backed store of models.User records.
 *
 * File format (users.json):
 * {
 * "users": [
 * {"username":"alice","wins":3,"losses":1,"friends":["bob","carol"]},
 * ...
 * ]
 * }
 */
public class UserStore {

    private final Path file;
    private final Map<String, User> users = new HashMap<>();

    public UserStore(String filename) {
        this.file = Paths.get(filename);
        load();
    }

    public synchronized boolean exists(String username) {
        return users.containsKey(username);
    }

    public synchronized User get(String username) {
        return users.get(username);
    }

    // register user with password
    public synchronized User register(String username, String password) {
        // fail if user already exists
        if (users.containsKey(username))
            return null;
        User u = new User(username, password);
        users.put(username, u);
        save();
        return u;
    }

    // credential check
    public synchronized boolean checkPassword(String username, String password) {
        User u = users.get(username);
        // fail if user doesn't exist
        if (u == null)
            return false;
        String stored = u.getPassword() == null ? "" : u.getPassword();
        String given = password == null ? "" : password;
        // return if the password matches. (no hashing)
        return stored.equals(given);
    }

    // update a win stat for a certain user
    public synchronized void recordWin(String username) {
        User u = users.get(username);
        if (u != null) {
            u.addWin();
            save();
        }
    }

    // update a loss stat for a certain user
    public synchronized void recordLoss(String username) {
        User u = users.get(username);
        if (u != null) {
            u.addLoss();
            save();
        }
    }

    // calculate and apply elo to both users
    // K=32, draws split 0.5/0.5, result is stored in users.json immediately.
    // https://github.com/rodrgds/python-elo-system
    public synchronized int[] applyEloUpdate(String winner, String loser, boolean draw) {
        User w = users.get(winner);
        User l = users.get(loser);
        // fail safe
        if (w == null || l == null)
            return new int[] { 0, 0 };
        int K = 32;
        // calculate expected
        double ew = 1.0 / (1.0 + Math.pow(10, (l.getElo() - w.getElo()) / 400.0));
        double el = 1.0 - ew;
        double sw = draw ? 0.5 : 1.0;
        double sl = draw ? 0.5 : 0.0;
        // calculate deltas
        int dw = (int) Math.round(K * (sw - ew));
        int dl = (int) Math.round(K * (sl - el));
        // set new elo
        w.setElo(w.getElo() + dw);
        l.setElo(l.getElo() + dl);
        // save elo
        save();
        // return elo changes
        return new int[] { dw, dl }; // [winner delta, loser delta]
    }

    // manage a user friends when adding
    public synchronized boolean addFriend(String owner, String friend) {
        User u = users.get(owner);
        // fail if user doesnt exists
        if (u == null)
            return false;
        // fail if the friend doesnt exist
        if (!users.containsKey(friend))
            return false;
        // fail if the friend uname is the same as the owner
        if (owner.equals(friend))
            return false;
        // add friend
        boolean changed = u.addFriend(friend);
        // update if the friend is successfully added
        if (changed)
            save();
        return changed;
    }

    // manage a user friends when removing
    public synchronized boolean removeFriend(String owner, String friend) {
        User u = users.get(owner);
        // fail if user doesnt exists
        if (u == null)
            return false;
        // remove friend
        boolean changed = u.removeFriend(friend);
        // update db if the friend is successfully removed
        if (changed)
            save();
        return changed;
    }

    // add a pending request
    public synchronized boolean addPendingRequest(String recipient, String requester) {
        User u = users.get(recipient);
        // fail if user doesnt exists
        if (u == null)
            return false;
        // fail if the requester doesnt exist
        if (!users.containsKey(requester))
            return false;
        // add the request to the recipient's pending requests'
        boolean changed = u.addPendingRequest(requester);
        // update db if the request is successfully added
        if (changed)
            save();
        return changed;
    }

    // remove a pending request
    public synchronized boolean removePendingRequest(String recipient, String requester) {
        User u = users.get(recipient);
        // fail if user doesnt exists
        if (u == null)
            return false;
        // remove the request from the recipient's pending requests'
        boolean changed = u.removePendingRequest(requester);
        // update db if the request is successfully removed
        if (changed)
            save();
        return changed;
    }

    // record a match result for both players.
    public synchronized void recordMatch(String player, String opponent, String result, int eloChange) {
        User u = users.get(player);
        if (u != null) {
            u.addMatchRecord(opponent, result, eloChange);
            save();
        }
    }

    // set a user online
    public synchronized void setOnline(String username, boolean online) {
        User u = users.get(username);
        if (u != null)
            u.setOnline(online);
    }

    // wipe all user data and persist an empty store
    public synchronized void clearAll() {
        users.clear();
        save();
        System.out.println("[UserStore] Cleared all users.");
    }

    // load users from file
    private void load() {
        if (!Files.exists(file))
            return;
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            List<Map<String, Object>> parsed = parseUsers(content);
            // load each user
            for (Map<String, Object> entry : parsed) {
                String name = (String) entry.get("username");
                String pass = (String) entry.getOrDefault("password", "");
                int wins = ((Number) entry.getOrDefault("wins", 0)).intValue();
                int losses = ((Number) entry.getOrDefault("losses", 0)).intValue();
                int elo = ((Number) entry.getOrDefault("elo", 1000)).intValue();
                @SuppressWarnings("unchecked")
                List<String> friends = (List<String>) entry.getOrDefault("friends", new ArrayList<>());
                @SuppressWarnings("unchecked")
                List<String> pending = (List<String>) entry.getOrDefault("pendingRequests", new ArrayList<>());
                @SuppressWarnings("unchecked")
                List<String> history = (List<String>) entry.getOrDefault("matchHistory", new ArrayList<>());
                // add the user to the hash map
                users.put(name, new User(name, pass, wins, losses, elo, new LinkedHashSet<>(friends),
                        new LinkedHashSet<>(pending), history));
            }
            System.out.println("[UserStore] Loaded " + users.size() + " user(s) from " + file);
        } catch (Exception e) {
            System.err.println("[UserStore] Failed to load " + file + ": " + e.getMessage());
        }
    }

    // save users to file
    private void save() {
        try {
            String json = serialise();
            Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[UserStore] Failed to save " + file + ": " + e.getMessage());
        }
    }

    // serialise users to json
    private String serialise() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"users\": [\n");
        boolean first = true;
        for (User u : users.values()) {
            if (!first)
                sb.append(",\n");
            sb.append("    {");
            sb.append("\"username\":").append(jsonString(u.getUsername())).append(",");
            sb.append("\"password\":").append(jsonString(u.getPassword() == null ? "" : u.getPassword())).append(",");
            sb.append("\"wins\":").append(u.getWins()).append(",");
            sb.append("\"losses\":").append(u.getLosses()).append(",");
            sb.append("\"elo\":").append(u.getElo()).append(",");

            // friend list
            sb.append("\"friends\":[");
            boolean firstFriend = true;
            for (String f : u.getFriends()) {
                if (!firstFriend)
                    sb.append(",");
                sb.append(jsonString(f));
                firstFriend = false;
            }
            sb.append("]");

            // pendingRequests
            sb.append(",\"pendingRequests\":[");
            boolean firstPending = true;
            for (String p : u.getPendingFriendRequests()) {
                if (!firstPending)
                    sb.append(",");
                sb.append(jsonString(p));
                firstPending = false;
            }
            sb.append("]");

            // matchHistory
            sb.append(",\"matchHistory\":[");
            boolean firstMatch = true;
            for (String m : u.getMatchHistory()) {
                if (!firstMatch)
                    sb.append(",");
                sb.append(jsonString(m));
                firstMatch = false;
            }
            sb.append("]}");
            first = false;
        }
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20)
                        sb.append(String.format("\\u%04x", (int) c));
                    else
                        sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // MINIMAL JSON PARSER
    private List<Map<String, Object>> parseUsers(String content) {
        Parser p = new Parser(content);
        p.skipWs();
        p.expect('{');
        Object root = p.parseObject();
        @SuppressWarnings("unchecked")
        Map<String, Object> rootMap = (Map<String, Object>) root;
        Object arr = rootMap.get("users");
        if (!(arr instanceof List))
            return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : (List<?>) arr) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                out.add(m);
            }
        }
        return out;
    }

    private static class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) {
            this.s = s;
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i)))
                i++;
        }

        void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c)
                throw new RuntimeException("Expected '" + c + "' at " + i);
            i++;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length())
                throw new RuntimeException("Unexpected EOF");
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                return parseObject();
            }
            if (c == '[') {
                i++;
                return parseArray();
            }
            if (c == '"')
                return parseString();
            if (c == '-' || Character.isDigit(c))
                return parseNumber();
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw new RuntimeException("Unexpected char '" + c + "' at " + i);
        }

        Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object val = parseValue();
                m.put(key, val);
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                expect('}');
                return m;
            }
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (true) {
                Object v = parseValue();
                list.add(v);
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        String parseString() {
            skipWs();
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"')
                    return sb.toString();
                if (c == '\\') {
                    if (i >= s.length())
                        throw new RuntimeException("Bad escape");
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            if (i + 4 > s.length())
                                throw new RuntimeException("Bad \\u escape");
                            int code = Integer.parseInt(s.substring(i, i + 4), 16);
                            sb.append((char) code);
                            i += 4;
                            break;
                        default:
                            throw new RuntimeException("Bad escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        Number parseNumber() {
            int start = i;
            if (s.charAt(i) == '-')
                i++;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.'))
                i++;
            String num = s.substring(start, i);
            if (num.contains("."))
                return Double.parseDouble(num);
            return Long.parseLong(num);
        }
    }
}