package scenes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

/**
 * Login / Register scene.
 *
 * registerMode=true  → "Create Account" flow (REGISTER message)
 * registerMode=false → "Log In" flow (LOGIN message)
 *
 * Both flows now take a username AND password.
 */
public class AuthScene {

    public interface Actions {
        // CHANGED: now passes both username and password to the controller
        void onSubmit(String username, String password);
        void onToggle();
        void onBack();
    }

    public static Scene build(boolean registerMode, Actions actions) {
        VBox root = UI.sceneRoot();
        root.setSpacing(0);

        // ── Top bar ────────────────────────────────────────────────────────
        Rectangle strip = UI.accentBar(UI.W, 4);

        Button back = UI.backButton();
        back.setOnAction(e -> actions.onBack());
        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(14, 8, 0, 8));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // ── Body ────────────────────────────────────────────────────────────
        VBox body = new VBox(0);
        body.setPadding(new Insets(40, 24, 24, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        Label heading = UI.titleLabel(registerMode ? "CREATE\nACCOUNT" : "LOG IN");
        heading.setWrapText(true);

        Label sub = UI.subtitleLabel(registerMode
                ? "CHOOSE A USERNAME AND PASSWORD"
                : "ENTER YOUR CREDENTIALS");
        sub.setPadding(new Insets(8, 0, 32, 0));

        // Username
        Label userLabel = UI.sectionLabel("USERNAME");
        userLabel.setPadding(new Insets(0, 0, 6, 0));
        TextField usernameField = UI.styledField(registerMode ? "e.g. ChessMaster99" : "Your username");

        // ADDED: password field (uses PasswordField so input is masked)
        Label passLabel = UI.sectionLabel("PASSWORD");
        passLabel.setPadding(new Insets(16, 0, 6, 0));
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(registerMode ? "Choose a password" : "Your password");
        passwordField.getStyleClass().add(UI.FIELD_STYLED);
        passwordField.setMaxWidth(Double.MAX_VALUE);

        Label errorLabel = new Label("");
        errorLabel.getStyleClass().add("info-text");
        errorLabel.getStyleClass().add("auth-error-label");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setPadding(new Insets(8, 0, 0, 0));

        Button submit = UI.primaryButton(registerMode ? "CREATE ACCOUNT" : "LOG IN");
        submit.setPadding(new Insets(16, 0, 16, 0));

        VBox submitBox = new VBox(submit);
        submitBox.setPadding(new Insets(24, 0, 0, 0));

        // ── Toggle link ─────────────────────────────────────────────────────
        String toggleText = registerMode
                ? "Already have an account?  LOG IN"
                : "New here?  CREATE ACCOUNT";
        Button toggleBtn = new Button(toggleText);
        toggleBtn.getStyleClass().add("auth-toggle-btn");
        toggleBtn.setOnAction(e -> actions.onToggle());

        VBox toggleBox = new VBox(toggleBtn);
        toggleBox.setAlignment(Pos.CENTER);
        VBox.setVgrow(toggleBox, Priority.ALWAYS);
        toggleBox.setPadding(new Insets(0, 0, 16, 0));
        VBox spacerGrow = new VBox();
        VBox.setVgrow(spacerGrow, Priority.ALWAYS);

        // CHANGED: validates both username and password before submitting
        Runnable doSubmit = () -> {
            String uname = usernameField.getText().trim();
            String pass  = passwordField.getText();
            if (uname.isEmpty()) {
                errorLabel.setText("Please enter a username.");
                errorLabel.setVisible(true);
                return;
            }
            if (pass.isEmpty()) {
                errorLabel.setText("Please enter a password.");
                errorLabel.setVisible(true);
                return;
            }
            errorLabel.setVisible(false);
            actions.onSubmit(uname, pass);
        };

        submit.setOnAction(e -> doSubmit.run());
        // ADDED: Enter in username jumps to password; Enter in password submits
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) passwordField.requestFocus();
        });
        passwordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) doSubmit.run();
        });

        body.getChildren().addAll(
                heading, sub,
                userLabel, usernameField,
                passLabel, passwordField,
                errorLabel,
                submitBox,
                spacerGrow,
                toggleBox
        );

        root.getChildren().addAll(strip, topBar, body);

        Scene scene = new Scene(root, UI.W, UI.H);
        scene.getStylesheets().add(AuthScene.class.getResource("/styles.css").toExternalForm());
        return scene;
    }
}