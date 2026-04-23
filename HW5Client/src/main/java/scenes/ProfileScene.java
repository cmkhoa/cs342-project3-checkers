package scenes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.List;
import java.util.Set;

/**
 * User profile scene — shows wins, losses, Elo, online status,
 * recent match history (collapsible), and friend actions.
 */
public class ProfileScene {

    public interface Actions {
        void onBack();
        void onAddFriend(String name);
        void onRemoveFriend(String name);
    }

    /**
     * @param matchHistory list of "opponent|W or L or D|eloChange" strings, most recent first
     * @param friendNames  set of usernames that are friends (used to hide "add" button per history row)
     */
    public static Scene build(String myUsername,
                              String targetUsername,
                              int wins, int losses, int elo, boolean online,
                              boolean isFriend,
                              List<String> matchHistory,
                              Set<String> friendNames,
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

        HBox statsRow = new HBox(0);
        statsRow.setAlignment(Pos.CENTER);

        statsRow.getChildren().addAll(
                statCell("WINS",   hasData ? String.valueOf(wins)   : "—"),
                statDivider(),
                statCell("LOSSES", hasData ? String.valueOf(losses) : "—"),
                statDivider(),
                statCell("ELO",    hasData ? String.valueOf(elo)    : "—")
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
                Button addBtn = UI.greenButton("SEND FRIEND REQUEST");
                addBtn.setMaxWidth(Double.MAX_VALUE);
                addBtn.setPadding(new Insets(12, 0, 12, 0));
                addBtn.setOnAction(e -> actions.onAddFriend(targetUsername));
                actionArea.getChildren().add(addBtn);
            }
        }

        // ── Match history (collapsible) ──────────────────────────────────────
        VBox historySection = buildHistorySection(matchHistory, friendNames, myUsername, actions);

        // ── Body layout ──────────────────────────────────────────────────────
        VBox body = new VBox(0);
        body.setPadding(new Insets(24, 24, 24, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        Label sectionLabel = UI.sectionLabel("PLAYER PROFILE");
        sectionLabel.setPadding(new Insets(0, 0, 16, 0));

        body.getChildren().addAll(sectionLabel, profileCard, vspacer(12), statsRow, actionArea,
                vspacer(16), historySection);

        root.getChildren().addAll(strip, topBar, body);

        Scene scene = new Scene(root, UI.W, UI.H);
        scene.getStylesheets().add(ProfileScene.class.getResource("/styles.css").toExternalForm());
        return scene;
    }

    // ── Match history section ────────────────────────────────────────────────

    private static VBox buildHistorySection(List<String> matchHistory,
                                            Set<String> friendNames,
                                            String myUsername,
                                            Actions actions) {
        VBox container = new VBox(0);

        if (matchHistory == null || matchHistory.isEmpty()) {
            Label empty = UI.infoLabel("No recent games.");
            empty.setPadding(new Insets(12, 0, 0, 0));
            container.getChildren().add(empty);
            return container;
        }

        // Toggle button
        VBox historyRows = new VBox(4);
        historyRows.setVisible(false);
        historyRows.setManaged(false);

        Button toggle = new Button("▸  RECENT GAMES (" + matchHistory.size() + ")");
        toggle.getStyleClass().add("btn-secondary");
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setAlignment(Pos.CENTER_LEFT);
        toggle.setStyle(toggle.getStyle() + "-fx-font-size: 11px; -fx-letter-spacing: 1px; -fx-padding: 10 12;");

        toggle.setOnAction(e -> {
            boolean show = !historyRows.isVisible();
            historyRows.setVisible(show);
            historyRows.setManaged(show);
            toggle.setText((show ? "▾" : "▸") + "  RECENT GAMES (" + matchHistory.size() + ")");
        });

        // Build rows
        for (String entry : matchHistory) {
            String[] parts = entry.split("\\|");
            if (parts.length < 3) continue;
            String opponent  = parts[0];
            String result    = parts[1]; // W, L, or D
            String eloDelta  = parts[2]; // e.g. +15 or -12

            HBox row = buildMatchRow(opponent, result, eloDelta, friendNames, myUsername, actions);
            historyRows.getChildren().add(row);
        }

        container.getChildren().addAll(toggle, vspacer(4), historyRows);
        return container;
    }

    private static HBox buildMatchRow(String opponent, String result, String eloDelta,
                                      Set<String> friendNames, String myUsername,
                                      Actions actions) {
        // Result indicator
        String resultDisplay;
        String resultColor;
        switch (result) {
            case "W": resultDisplay = "WIN";  resultColor = "#52B788"; break;
            case "L": resultDisplay = "LOSS"; resultColor = "#E85D5D"; break;
            default:  resultDisplay = "DRAW"; resultColor = "#B0ABA3"; break;
        }

        Label resultLabel = new Label(resultDisplay);
        resultLabel.setMinWidth(40);
        resultLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + resultColor
                + "; -fx-letter-spacing: 1px;");

        // Opponent name
        Label opponentLabel = new Label(opponent);
        opponentLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1A1A1A; -fx-font-weight: bold;");

        // Elo change
        boolean positive = eloDelta.startsWith("+");
        Label eloLabel = new Label(eloDelta);
        eloLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                + (positive ? "#52B788" : "#E85D5D") + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, resultLabel, opponentLabel, spacer, eloLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle("-fx-background-color: #EDE8DF; -fx-background-radius: 8;");

        // Add friend button if not already friends and not self
        boolean isSelf = opponent.equals(myUsername);
        boolean alreadyFriend = friendNames != null && friendNames.contains(opponent);
        if (!isSelf && !alreadyFriend) {
            Button addBtn = new Button("+");
            addBtn.getStyleClass().add("btn-green");
            addBtn.setStyle("-fx-min-width: 30; -fx-max-width: 30; -fx-padding: 4 0; -fx-font-size: 13px;");
            addBtn.setOnAction(e -> {
                actions.onAddFriend(opponent);
                addBtn.setText("✓");
                addBtn.setDisable(true);
            });
            row.getChildren().add(addBtn);
        }

        return row;
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