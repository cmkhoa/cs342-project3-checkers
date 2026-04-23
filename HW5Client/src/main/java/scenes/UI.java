package scenes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Shared UI helpers and constants for the Checkers client.
 * All scene builders use these to stay consistent.
 */
public class UI {

    // ── Window dimensions (18:9 vertical) ─────────────────────────────────
    public static final int W = 420;
    public static final int H = 840;

    // ── Board ──────────────────────────────────────────────────────────────
    public static final int CELL     = W / 8 - 1;       // 52px
    public static final int BOARD_PX = CELL * 8;    // 416px

    // ── CSS classes ───────────────────────────────────────────────────────
    // Buttons
    public static final String BTN_PRIMARY   = "btn-primary";
    public static final String BTN_SECONDARY = "btn-secondary";
    public static final String BTN_BACK      = "btn-back";
    public static final String BTN_DANGER    = "btn-danger";
    public static final String BTN_GREEN     = "btn-green";
    public static final String BTN_SEND      = "btn-send";
    // Text
    public static final String TITLE         = "title-label";
    public static final String SECTION       = "section-heading";
    public static final String SUBTITLE      = "subtitle-label";
    public static final String INFO          = "info-text";
    // Layout
    public static final String CARD          = "card";
    public static final String CARD_DARK     = "card-dark";
    public static final String TOP_BAR       = "top-bar";
    public static final String STATUS_BAR    = "status-bar";
    // Lists
    public static final String LIST_STYLED   = "list-view-styled";
    public static final String CHAT_LIST     = "chat-list";
    // Fields
    public static final String FIELD_STYLED  = "text-field-styled";
    public static final String CHAT_INPUT    = "chat-input";

    // ── Factory helpers ───────────────────────────────────────────────────

    public static VBox sceneRoot() {
        VBox v = new VBox();
        v.getStyleClass().add("scene-root");
        v.setPrefSize(W, H);
        v.setMaxSize(W, H);
        return v;
    }

    public static Label titleLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(TITLE);
        return l;
    }

    public static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(SECTION);
        return l;
    }

    public static Label subtitleLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(SUBTITLE);
        return l;
    }

    public static Label infoLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(INFO);
        l.setWrapText(true);
        return l;
    }

    /**
     * Full-width primary action button.
     */
    public static Button primaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(BTN_PRIMARY);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    /**
     * Ghost / outline button.
     */
    public static Button secondaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(BTN_SECONDARY);
        return b;
    }

    /**
     * Back navigation button (← label).
     */
    public static Button backButton() {
        Button b = new Button("←");
        b.getStyleClass().add(BTN_BACK);
        return b;
    }

    public static Button dangerButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(BTN_DANGER);
        return b;
    }

    public static Button greenButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(BTN_GREEN);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    public static TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add(FIELD_STYLED);
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    public static TextField chatInputField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add(CHAT_INPUT);
        return tf;
    }

    /**
     * Thin horizontal rule divider.
     */
    public static Region divider() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMaxWidth(Double.MAX_VALUE);
        r.getStyleClass().add("ui-divider");
        return r;
    }

    /**
     * Flexible spacer for HBox/VBox.
     */
    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    /**
     * Coloured dot indicator (online/offline).
     */
    public static Label onlineDot(boolean online) {
        Label l = new Label(online ? "●" : "○");
        l.getStyleClass().add(online ? "friend-online-dot" : "friend-offline-dot");
        return l;
    }

    /**
     * Green accent rectangle decoration.
     */
    public static Rectangle accentBar(double w, double h) {
        Rectangle r = new Rectangle(w, h);
        r.setFill(Color.web("#52B788"));
        return r;
    }

    /**
     * Standard content padding for scene bodies.
     */
    public static Insets contentPadding() {
        return new Insets(20, 24, 24, 24);
    }

    /**
     * Wrap content in a padded VBox that grows to fill.
     */
    public static VBox contentBox(javafx.scene.Node... nodes) {
        VBox box = new VBox(16);
        box.setPadding(contentPadding());
        box.getChildren().addAll(nodes);
        return box;
    }
}