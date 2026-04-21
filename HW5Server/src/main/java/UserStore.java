// ============================================================================
// NEW FILE: UserStore.java
//   Introduced to satisfy the "json user store" checklist item.
//   Provides persistent, thread-safe, JSON-backed storage of User records
//   (wins, losses, friends) so that player profiles survive server restarts.
//   Uses a hand-rolled minimal JSON reader/writer so no new Maven dependencies
//   are required.
// ============================================================================

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Persistent JSON-backed store of User records.
 *
 * File format (users.json):
 * {
 *   "users": [
 *     {"username":"alice","wins":3,"losses":1,"friends":["bob","carol"]},
 *     ...
 *   ]
 * }
 *
 * A hand-rolled minimal JSON parser keeps this class dependency-free.
 * The parser is intentionally strict about the shape we write — it only
 * has to round-trip data this class produced.
 *
 * All public methods are thread-safe (synchronised on the UserStore instance).
 */
public class UserStore {

    private final Path                  file;
    private final Map<String, User>     users = new HashMap<>();

    public UserStore(String filename) {
        this.file = Paths.get(filename);
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────

    public synchronized boolean exists(String username) {
        return users.containsKey(username);
    }

    public synchronized User get(String username) {
        return users.get(username);
    }

    /** Register a brand-new user. Returns null if the name is already taken. */
    public synchronized User register(String username) {
        if (users.containsKey(username)) return null;
        User u = new User(username);
        users.put(username, u);
        save();
        return u;
    }

    /** Record a completed game result for a user and persist. */
    public synchronized void recordWin(String username) {
        User u = users.get(username);
        if (u != null) { u.addWin(); save(); }
    }

    public synchronized void recordLoss(String username) {
        User u = users.get(username);
        if (u != null) { u.addLoss(); save(); }
    }

    public synchronized boolean addFriend(String owner, String friend) {
        User u = users.get(owner);
        if (u == null)            return false;
        if (!users.containsKey(friend)) return false;  // friend must be registered
        if (owner.equals(friend)) return false;
        boolean changed = u.addFriend(friend);
        if (changed) save();
        return changed;
    }

    public synchronized boolean removeFriend(String owner, String friend) {
        User u = users.get(owner);
        if (u == null) return false;
        boolean changed = u.removeFriend(friend);
        if (changed) save();
        return changed;
    }

    public synchronized void setOnline(String username, boolean online) {
        User u = users.get(username);
        if (u != null) u.setOnline(online);
        // Online status is in-memory only — no need to persist.
    }

    public synchronized Collection<User> all() {
        return new ArrayList<>(users.values());
    }

    // ── Load ──────────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(file)) return;
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            List<Map<String, Object>> parsed = parseUsers(content);
            for (Map<String, Object> entry : parsed) {
                String name   = (String) entry.get("username");
                int    wins   = ((Number) entry.getOrDefault("wins",   0)).intValue();
                int    losses = ((Number) entry.getOrDefault("losses", 0)).intValue();
                @SuppressWarnings("unchecked")
                List<String> friends = (List<String>) entry.getOrDefault("friends", new ArrayList<>());
                users.put(name, new User(name, wins, losses, new LinkedHashSet<>(friends)));
            }
            System.out.println("[UserStore] Loaded " + users.size() + " user(s) from " + file);
        } catch (Exception e) {
            System.err.println("[UserStore] Failed to load " + file + ": " + e.getMessage());
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────

    private void save() {
        try {
            String json = serialise();
            Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[UserStore] Failed to save " + file + ": " + e.getMessage());
        }
    }

    private String serialise() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"users\": [\n");
        boolean first = true;
        for (User u : users.values()) {
            if (!first) sb.append(",\n");
            sb.append("    {");
            sb.append("\"username\":").append(jsonString(u.getUsername())).append(",");
            sb.append("\"wins\":").append(u.getWins()).append(",");
            sb.append("\"losses\":").append(u.getLosses()).append(",");
            sb.append("\"friends\":[");
            boolean firstFriend = true;
            for (String f : u.getFriends()) {
                if (!firstFriend) sb.append(",");
                sb.append(jsonString(f));
                firstFriend = false;
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
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else          sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MINIMAL JSON PARSER
    //  Handles the subset produced by serialise(): objects, arrays, strings,
    //  integers, and the top-level { "users": [ ... ] } shape.
    // ══════════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> parseUsers(String content) {
        Parser p = new Parser(content);
        p.skipWs();
        p.expect('{');
        Object root = p.parseObject();
        @SuppressWarnings("unchecked")
        Map<String, Object> rootMap = (Map<String, Object>) root;
        Object arr = rootMap.get("users");
        if (!(arr instanceof List)) return new ArrayList<>();
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

        Parser(String s) { this.s = s; }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c)
                throw new RuntimeException("Expected '" + c + "' at " + i);
            i++;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw new RuntimeException("Unexpected EOF");
            char c = s.charAt(i);
            if (c == '{') { i++; return parseObject(); }
            if (c == '[') { i++; return parseArray();  }
            if (c == '"') return parseString();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            if (s.startsWith("true",  i)) { i += 4; return Boolean.TRUE;  }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null",  i)) { i += 4; return null; }
            throw new RuntimeException("Unexpected char '" + c + "' at " + i);
        }

        Map<String, Object> parseObject() {
            // caller consumed '{'
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object val = parseValue();
                m.put(key, val);
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> parseArray() {
            // caller consumed '['
            List<Object> list = new ArrayList<>();
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
            while (true) {
                Object v = parseValue();
                list.add(v);
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
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
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw new RuntimeException("Bad escape");
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw new RuntimeException("Bad \\u escape");
                            int code = Integer.parseInt(s.substring(i, i + 4), 16);
                            sb.append((char) code);
                            i += 4;
                            break;
                        default: throw new RuntimeException("Bad escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        Number parseNumber() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            String num = s.substring(start, i);
            if (num.contains(".")) return Double.parseDouble(num);
            return Long.parseLong(num);
        }
    }
}