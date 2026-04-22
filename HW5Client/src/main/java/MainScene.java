import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

/**
 * Main menu scene — two variants:
 *   • Logged-out: Play Local / Log In / Register
 *   • Logged-in:  Play Local / Find Match / My Profile / Friends / Log Out
 */
public class MainScene {

    public interface Actions {
        void onPlayLocal();
        void onLogin();
        void onRegister();
        void onFindMatch();
        void onProfile();
        void onFriends();
        void onLogout();
    }

    public static Scene build(boolean loggedIn, String username, Actions actions) {
        VBox root = UI.sceneRoot();
        root.setSpacing(0);

        root.getChildren().add(buildHeader());

        if (loggedIn) {
            root.getChildren().add(buildLoggedInBody(username, actions));
        } else {
            root.getChildren().add(buildLoggedOutBody(actions));
        }

        Scene scene = new Scene(root, UI.W, UI.H);
        scene.getStylesheets().add(MainScene.class.getResource("/styles.css").toExternalForm());
        return scene;
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private static VBox buildHeader() {
        // Decorative accent strip
        Rectangle strip = UI.accentBar(UI.W, 4);

        Label title = UI.titleLabel("CHECKERS");

        Label sub = UI.subtitleLabel("MULTIPLAYER BOARD GAME");

        VBox header = new VBox(6, strip, title, sub);
        header.setPadding(new Insets(28, 24, 24, 24));
        header.setAlignment(Pos.BOTTOM_LEFT);
        header.getStyleClass().add("top-bar");
        // override padding top since we have the strip
        header.setPadding(new Insets(0, 24, 24, 24));

        VBox wrapper = new VBox(strip, new VBox(6, title, sub) {{
            setPadding(new Insets(24, 24, 24, 24));
        }});
        wrapper.getStyleClass().add("divider-line");
        return wrapper;
    }

    // ── Logged-out body ────────────────────────────────────────────────────

    private static VBox buildLoggedOutBody(Actions actions) {
        VBox body = new VBox(0);
        body.setPadding(new Insets(36, 24, 24, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        // Play local section
        Label playSection = UI.sectionLabel("OFFLINE");
        Button playLocal = UI.primaryButton("Pass & Play");
        playLocal.setOnAction(e -> actions.onPlayLocal());

        // Account section
        Label accountSection = UI.sectionLabel("ONLINE");
        accountSection.setPadding(new Insets(28, 0, 0, 0));
        Button login = UI.primaryButton("LOG IN");
        Button register = UI.greenButton("CREATE ACCOUNT");
        login.setOnAction(e -> actions.onLogin());
        register.setOnAction(e -> actions.onRegister());

        Label hint = UI.infoLabel("Log in to track wins, add friends, and play online.");
        hint.setPadding(new Insets(8, 0, 0, 0));

        body.getChildren().addAll(
            playSection,
            vspacer(8),
            playLocal,
            accountSection,
            vspacer(8),
            login,
            vspacer(8),
            register,
            vspacer(12),
            hint
        );
        return body;
    }

    // ── Logged-in body ─────────────────────────────────────────────────────

    private static VBox buildLoggedInBody(String username, Actions actions) {
        VBox body = new VBox(0);
        body.setPadding(new Insets(24, 24, 24, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        // User chip
        HBox chip = new HBox(8);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("user-chip");
        chip.setMaxWidth(Double.MAX_VALUE);
        chip.setPadding(new Insets(10, 16, 10, 16));
        Label dot = new Label("●");
        dot.getStyleClass().add("friend-online-dot");
        Label uname = new Label(username.toUpperCase());
        uname.getStyleClass().add("user-chip-label");
        chip.getChildren().addAll(dot, uname);

        Label playSection = UI.sectionLabel("PLAY");
        playSection.setPadding(new Insets(24, 0, 0, 0));

        Button playLocal  = UI.primaryButton("PLAY LOCAL");
        Button findMatch  = UI.primaryButton("FIND MATCH");
        playLocal.setOnAction(e -> actions.onPlayLocal());
        findMatch.setOnAction(e -> actions.onFindMatch());

        Label accountSection = UI.sectionLabel("ACCOUNT");
        accountSection.setPadding(new Insets(24, 0, 0, 0));

        Button profile = UI.secondaryButton("MY PROFILE");
        Button friends = UI.secondaryButton("FRIENDS");
        profile.setMaxWidth(Double.MAX_VALUE);
        friends.setMaxWidth(Double.MAX_VALUE);
        profile.setOnAction(e -> actions.onProfile());
        friends.setOnAction(e -> actions.onFriends());

        Button logout = UI.dangerButton("LOG OUT");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setPadding(new Insets(11, 0, 11, 0));
        logout.setPadding(new Insets(11, 20, 11, 20));


        VBox logoutBox = new VBox(logout);
        logoutBox.setAlignment(Pos.BOTTOM_CENTER);
        logoutBox.setPadding(new Insets(20, 0, 0, 0));
        VBox.setVgrow(logoutBox, Priority.ALWAYS);

        body.getChildren().addAll(
                chip,
                playSection, vspacer(8),
                playLocal, vspacer(8), findMatch,
                accountSection, vspacer(8),
                profile, vspacer(8), friends,
                logoutBox
        );
        return body;
    }

    private static Region vspacer(double h) {
        Region r = new Region();
        r.setPrefHeight(h);
        r.setMinHeight(h);
        r.setMaxHeight(h);
        return r;
    }
}