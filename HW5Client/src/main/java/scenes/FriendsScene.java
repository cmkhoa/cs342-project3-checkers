package scenes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.util.List;

/**
 * Friends list scene
 */
public class FriendsScene {

    public interface Actions {
        void onBack();

        void onAddFriend(String name);

        void onRemoveFriend(String name);

        void onViewProfile(String name);

        void onAcceptRequest(String name);

        void onDeclineRequest(String name);

        void onChallengeFriend(String name);
    }

    public static Scene build(List<String> friendEntries, List<String> pendingRequests, Actions actions) {
        VBox root = UI.sceneRoot();
        root.setSpacing(0);

        // Top bar
        Rectangle strip = UI.accentBar(UI.W, 4);

        Button back = UI.backButton();
        back.setOnAction(e -> actions.onBack());

        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(12, 8, 0, 8));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Body
        VBox body = new VBox(0);
        body.setPadding(new Insets(20, 24, 24, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        Label heading = UI.titleLabel("FRIENDS");
        Label sub = UI.sectionLabel(friendEntries.size() + " FRIENDS");
        sub.setPadding(new Insets(4, 0, 12, 0));

        // Pending requests
        if (pendingRequests != null && !pendingRequests.isEmpty()) {
            Label pendingLabel = UI.sectionLabel("FRIEND REQUESTS (" + pendingRequests.size() + ")");
            pendingLabel.setPadding(new Insets(0, 0, 8, 0));
            body.getChildren().addAll(heading, pendingLabel);

            for (String requester : pendingRequests) {
                HBox row = buildRequestRow(requester, actions);
                body.getChildren().add(row);
            }

            body.getChildren().add(vspacer(16));
            body.getChildren().add(sub);
        } else {
            body.getChildren().addAll(heading, sub);
        }

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle(
                "-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0; -fx-border-width: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // friend list container
        VBox listContainer = new VBox(4);
        scroll.setContent(listContainer);

        if (friendEntries.isEmpty()) {
            listContainer.getChildren().add(UI.infoLabel("No friends yet — add one below"));
        } else {
            for (String e : friendEntries) {
                String[] parts = e.split("\\|");
                if (parts.length < 5)
                    continue;
                String name = parts[0];
                boolean on = "1".equals(parts[1]);
                String wins = parts[2];
                String losses = parts[3];
                String elo = parts[4];

                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10, 12, 10, 12));
                row.getStyleClass().add("friend-row");

                Label statusDot = new Label(on ? "●" : "○");
                statusDot.setStyle(on ? "-fx-text-fill: #52B788;" : "-fx-text-fill: #7A9A8A;");

                Label nameLbl = new Label(name);
                nameLbl.getStyleClass().add("friend-name-label");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Label statsLbl = new Label(String.format("W%s L%s (%s)", wins, losses, elo));
                statsLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A7570;");

                row.getChildren().addAll(statusDot, nameLbl, spacer, statsLbl);

                if (on) {
                    Button playBtn = new Button("Play");
                    playBtn.getStyleClass().add("btn-green");
                    playBtn.setStyle("-fx-padding: 4 8; -fx-font-size: 11px;");
                    playBtn.setOnAction(evt -> actions.onChallengeFriend(name));
                    row.getChildren().add(playBtn);
                }

                Button removeBtn = new Button("✕");
                removeBtn.getStyleClass().add("btn-danger");
                removeBtn.setStyle("-fx-padding: 2 6; -fx-font-size: 9px;");
                removeBtn.setOnAction(evt -> actions.onRemoveFriend(name));
                row.getChildren().add(removeBtn);

                // Double-click to view profile
                row.setOnMouseClicked(evt -> {
                    if (evt.getClickCount() == 2) {
                        actions.onViewProfile(name);
                    }
                });

                listContainer.getChildren().add(row);
            }
        }

        // Send friend request
        Label addLabel = UI.sectionLabel("SEND REQUEST");
        addLabel.setPadding(new Insets(16, 0, 8, 0));

        TextField addField = UI.styledField("Username…");
        addField.setMaxWidth(Double.MAX_VALUE);

        Button addBtn = UI.greenButton("SEND");
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
        addField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                doAdd.run();
        });

        HBox addRow = new HBox(10, addField, addBtn);
        addRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(addField, Priority.ALWAYS);

        Label hint = UI.infoLabel("Double-tap a friend to view their profile.");

        body.getChildren().addAll(
                scroll,
                addLabel, addRow,
                vspacer(10),
                hint);

        root.getChildren().addAll(strip, topBar, body);

        Scene scene = new Scene(root, UI.W, UI.H);
        scene.getStylesheets().add(FriendsScene.class.getResource("/styles.css").toExternalForm());
        return scene;
    }

    private static HBox buildRequestRow(String requester, Actions actions) {
        Label nameLabel = new Label("⏳ " + requester);
        nameLabel.getStyleClass().add("friend-name-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button accept = new Button("✓");
        accept.getStyleClass().add("btn-green");
        accept.getStyleClass().add("friend-action-btn");
        accept.setOnAction(e -> actions.onAcceptRequest(requester));

        Button decline = new Button("✕");
        decline.getStyleClass().add("btn-danger");
        decline.getStyleClass().add("friend-action-btn");
        decline.setOnAction(e -> actions.onDeclineRequest(requester));

        HBox row = new HBox(8, nameLabel, spacer, accept, decline);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.getStyleClass().add("friend-row");
        VBox.setMargin(row, new Insets(0, 0, 4, 0));
        return row;
    }

    private static Region vspacer(double h) {
        Region r = new Region();
        r.setPrefHeight(h);
        r.setMinHeight(h);
        return r;
    }
}