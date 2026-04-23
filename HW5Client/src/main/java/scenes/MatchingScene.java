package scenes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

/**
 * Matching / waiting-for-opponent scene.
 */
public class MatchingScene {

    public interface Actions {
        void onCancel();
    }

    public static Scene build(String username, Actions actions) {
        VBox root = UI.sceneRoot();
        root.setAlignment(Pos.CENTER);
        root.setSpacing(0);

        // ── Top strip ──────────────────────────────────────────────────────
        Rectangle strip = UI.accentBar(UI.W, 4);

        // ── Content ────────────────────────────────────────────────────────
        VBox content = new VBox(0);
        content.setAlignment(Pos.CENTER);
        VBox.setVgrow(content, Priority.ALWAYS);
        content.setPadding(new Insets(0, 32, 0, 32));

        ProgressIndicator spinner = new ProgressIndicator(-1);
        spinner.setPrefSize(52, 52);
        spinner.getStyleClass().add("progress-indicator");
        spinner.getStyleClass().add("matching-spinner");

        Region gap1 = new Region();
        gap1.setPrefHeight(24);

        Label waiting = new Label("FINDING MATCH");
        waiting.getStyleClass().add("waiting-text");
        waiting.getStyleClass().add("matching-waiting");

        Label sub = new Label("Waiting for another player to join…");
        sub.getStyleClass().add("waiting-sub");
        sub.setPadding(new Insets(6, 0, 0, 0));

        Region gap2 = new Region();
        gap2.setPrefHeight(40);

        // User chip
        HBox chip = new HBox(8);
        chip.setAlignment(Pos.CENTER);
        chip.getStyleClass().add("user-chip");
        chip.setMaxWidth(160);
        chip.setPadding(new Insets(8, 16, 8, 16));
        Label dot = new Label("●");
        dot.getStyleClass().add("friend-online-dot");
        Label uname = new Label(username.toUpperCase());
        uname.getStyleClass().add("user-chip-label");
        chip.getChildren().addAll(dot, uname);

        Region gap3 = new Region();
        gap3.setPrefHeight(48);

        Button cancel = UI.secondaryButton("CANCEL");
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setOnAction(e -> actions.onCancel());

        content.getChildren().addAll(spinner, gap1, waiting, sub, gap2, chip, gap3, cancel);

        root.getChildren().addAll(strip, content);

        Scene scene = new Scene(root, UI.W, UI.H);
        scene.getStylesheets().add(MatchingScene.class.getResource("/styles.css").toExternalForm());
        return scene;
    }
}