import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.util.List;

/**
 * Friends list scene — view, add, remove friends.
 * Each entry in friendEntries has format: "name|online|wins|losses"
 */
public class FriendsScene {

    public interface Actions {
        void onBack();
        void onAddFriend(String name);
        void onRemoveFriend(String name);
        void onViewProfile(String name);
    }

    public static Scene build(List<String> friendEntries, Actions actions) {
        VBox root = UI.sceneRoot();
        root.setSpacing(0);

        // ── Top strip ──────────────────────────────────────────────────────
        Rectangle strip = UI.accentBar(UI.W, 4);

        Button back = UI.backButton();
        back.setOnAction(e -> actions.onBack());

        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(12, 8, 0, 8));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // ── Body ────────────────────────────────────────────────────────────
        VBox body = new VBox(0);
        body.setPadding(new Insets(20, 24, 24, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        Label heading = UI.titleLabel("FRIENDS");
        Label sub = UI.sectionLabel(friendEntries.size() + " FRIENDS");
        sub.setPadding(new Insets(4, 0, 20, 0));

        // ── Friends list ────────────────────────────────────────────────────
        ListView<String> listView = new ListView<>();
        listView.getStyleClass().add("list-view-styled");
        VBox.setVgrow(listView, Priority.ALWAYS);

        if (friendEntries.isEmpty()) {
            listView.getItems().add("  No friends yet — add one below");
        } else {
            for (String e : friendEntries) {
                String[] parts = e.split("\\|");
                if (parts.length < 5) continue;
                String name   = parts[0];
                boolean on    = "1".equals(parts[1]);
                String wins   = parts[2];
                String losses = parts[3];
                listView.getItems().add(String.format(
                        "%s %s    W%s  L%s  (%s)",
                        on ? "●" : "○", name, wins, losses, parts[4]));  // parts[4] = elo
            }
        }

        // Double-click → profile
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String sel = listView.getSelectionModel().getSelectedItem();
                if (sel == null || sel.startsWith("  No friends")) return;
                String name = extractName(sel);
                if (name != null) actions.onViewProfile(name);
            }
        });

        // ── Add friend ──────────────────────────────────────────────────────
        Label addLabel = UI.sectionLabel("ADD FRIEND");
        addLabel.setPadding(new Insets(16, 0, 8, 0));

        TextField addField = UI.styledField("Username to add…");
        addField.setMaxWidth(Double.MAX_VALUE);

        Button addBtn = UI.greenButton("ADD");
        addBtn.setMaxWidth(120);
        addBtn.setPadding(new Insets(11, 0, 11, 0));

        Runnable doAdd = () -> {
            String name = addField.getText().trim();
            if (!name.isEmpty()) {
                actions.onAddFriend(name);
                addField.clear();
            }
        };
        addBtn.setOnAction(e -> doAdd.run());
        addField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) doAdd.run(); });

        HBox addRow = new HBox(10, addField, addBtn);
        addRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(addField, Priority.ALWAYS);

        // ── Remove button ───────────────────────────────────────────────────
        Button removeBtn = UI.dangerButton("REMOVE SELECTED");
        removeBtn.setMaxWidth(Double.MAX_VALUE);
        removeBtn.setPadding(new Insets(10, 0, 10, 0));
        removeBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null || sel.startsWith("  No friends")) return;
            String name = extractName(sel);
            if (name != null) actions.onRemoveFriend(name);
        });

        Label hint = UI.infoLabel("Double-tap a friend to view their profile.");

        body.getChildren().addAll(
                heading, sub,
                listView,
                addLabel, addRow,
                vspacer(10),
                removeBtn,
                vspacer(8),
                hint
        );

        root.getChildren().addAll(strip, topBar, body);

        Scene scene = new Scene(root, UI.W, UI.H);
        scene.getStylesheets().add(FriendsScene.class.getResource("/styles.css").toExternalForm());
        return scene;
    }

    private static String extractName(String listEntry) {
        if (listEntry == null) return null;
        // Format: "● name    W3  L1" or "○ name    W0  L0"
        String s = listEntry.replaceAll("^[●○]\\s*", "").trim();
        int space = s.indexOf(' ');
        return space > 0 ? s.substring(0, space).trim() : s.trim();
    }

    private static Region vspacer(double h) {
        Region r = new Region();
        r.setPrefHeight(h);
        r.setMinHeight(h);
        return r;
    }
}