import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * User profile scene — shows wins, losses, win-rate, online status.
 * Also offers Add/Remove Friend when viewing another player's profile.
 */
public class ProfileScene {

    public interface Actions {
        void onBack();
        void onAddFriend(String name);
        void onRemoveFriend(String name);
    }

    public static Scene build(String myUsername,
                              String targetUsername,
                              int wins, int losses, boolean online,
                              boolean isFriend,
                              Actions actions) {
        VBox root = UI.sceneRoot();
        root.setSpacing(0);

        // ── Top strip + back ────────────────────────────────────────────────
        Rectangle strip = UI.accentBar(UI.W, 4);

        Button back = UI.backButton();
        back.setOnAction(e -> actions.onBack());
        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(12, 8, 0, 8));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // ── Profile card ────────────────────────────────────────────────────
        VBox profileCard = new VBox(0);
        profileCard.getStyleClass().add("card-dark");
        profileCard.setAlignment(Pos.CENTER_LEFT);
        profileCard.setPadding(new Insets(24, 24, 24, 24));

        Label name = new Label(targetUsername != null ? targetUsername.toUpperCase() : "—");
        name.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #F5F0E8; -fx-letter-spacing: 2px;");
        name.setWrapText(true);

        Label statusDot = new Label(online ? "● ONLINE" : "○ OFFLINE");
        statusDot.setStyle(online
                ? "-fx-font-size: 11px; -fx-text-fill: #52B788; -fx-letter-spacing: 2px; -fx-padding: 6 0 0 0;"
                : "-fx-font-size: 11px; -fx-text-fill: #7A9A8A; -fx-letter-spacing: 2px; -fx-padding: 6 0 0 0;");

        profileCard.getChildren().addAll(name, statusDot);

        // ── Stats row ────────────────────────────────────────────────────────
        boolean hasData = wins >= 0 && losses >= 0;
        int games = hasData ? wins + losses : 0;
        String winRateStr = games == 0 ? "—" : String.format("%.0f%%", 100.0 * wins / games);

        HBox statsRow = new HBox(0);
        statsRow.setAlignment(Pos.CENTER);

        statsRow.getChildren().addAll(
                statCell("WINS",     hasData ? String.valueOf(wins)   : "—"),
                statDivider(),
                statCell("LOSSES",   hasData ? String.valueOf(losses) : "—"),
                statDivider(),
                statCell("WIN RATE", winRateStr)
        );

        // ── Action buttons ───────────────────────────────────────────────────
        VBox actionArea = new VBox(10);
        actionArea.setPadding(new Insets(20, 0, 0, 0));

        boolean isSelf = targetUsername != null && targetUsername.equals(myUsername);
        if (!isSelf && hasData) {
            if (isFriend) {
                Button removeBtn = UI.dangerButton("REMOVE FRIEND");
                removeBtn.setMaxWidth(Double.MAX_VALUE);
                removeBtn.setPadding(new Insets(12, 0, 12, 0));
                removeBtn.setOnAction(e -> actions.onRemoveFriend(targetUsername));
                actionArea.getChildren().add(removeBtn);
            } else {
                Button addBtn = UI.greenButton("ADD FRIEND");
                addBtn.setMaxWidth(Double.MAX_VALUE);
                addBtn.setPadding(new Insets(12, 0, 12, 0));
                addBtn.setOnAction(e -> actions.onAddFriend(targetUsername));
                actionArea.getChildren().add(addBtn);
            }
        }

        // ── Body layout ──────────────────────────────────────────────────────
        VBox body = new VBox(0);
        body.setPadding(new Insets(24, 24, 24, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        Label sectionLabel = UI.sectionLabel("PLAYER PROFILE");
        sectionLabel.setPadding(new Insets(0, 0, 16, 0));

        body.getChildren().addAll(sectionLabel, profileCard, vspacer(12), statsRow, actionArea);

        root.getChildren().addAll(strip, topBar, body);

        Scene scene = new Scene(root, UI.W, UI.H);
        scene.getStylesheets().add(ProfileScene.class.getResource("/styles.css").toExternalForm());
        return scene;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static VBox statCell(String label, String value) {
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1A1A1A;");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #7A7570; -fx-letter-spacing: 2px;");

        VBox cell = new VBox(4, val, lbl);
        cell.setAlignment(Pos.CENTER);
        cell.setStyle("-fx-background-color: #EDE8DF; -fx-padding: 20 0;");
        HBox.setHgrow(cell, Priority.ALWAYS);
        cell.setMaxWidth(Double.MAX_VALUE);
        return cell;
    }

    private static Rectangle statDivider() {
        Rectangle r = new Rectangle(1, 60);
        r.setFill(Color.web("#D4CFC5"));
        return r;
    }

    private static Region vspacer(double h) {
        Region r = new Region();
        r.setPrefHeight(h);
        r.setMinHeight(h);
        return r;
    }
}