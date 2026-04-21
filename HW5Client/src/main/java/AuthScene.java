import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
 * On success the server sends REGISTER_OK / LOGIN_OK and the app
 * navigates to the main scene (logged-in variant).
 */
public class AuthScene {

    public interface Actions {
        void onSubmit(String username);
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
                ? "CHOOSE A UNIQUE USERNAME"
                : "ENTER YOUR USERNAME");
        sub.setPadding(new Insets(8, 0, 32, 0));

        Label fieldLabel = UI.sectionLabel("USERNAME");
        fieldLabel.setPadding(new Insets(0, 0, 6, 0));

        TextField usernameField = UI.styledField(registerMode ? "e.g. ChessMaster99" : "Your username");

        Label errorLabel = new Label("");
        errorLabel.getStyleClass().add("info-text");
        errorLabel.setStyle("-fx-text-fill: #A63228;");
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
        toggleBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #2D6A4F; " +
                "-fx-font-size: 12px; -fx-cursor: hand; -fx-border-color: transparent; " +
                "-fx-underline: true;");
        toggleBtn.setOnAction(e -> actions.onToggle());

        VBox toggleBox = new VBox(toggleBtn);
        toggleBox.setAlignment(Pos.CENTER);
        VBox.setVgrow(toggleBox, Priority.ALWAYS);
        toggleBox.setPadding(new Insets(0, 0, 16, 0));
        VBox spacerGrow = new VBox();
        VBox.setVgrow(spacerGrow, Priority.ALWAYS);

        Runnable doSubmit = () -> {
            String text = usernameField.getText().trim();
            if (text.isEmpty()) {
                errorLabel.setText("Please enter a username.");
                errorLabel.setVisible(true);
                return;
            }
            errorLabel.setVisible(false);
            actions.onSubmit(text);
        };

        submit.setOnAction(e -> doSubmit.run());
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) doSubmit.run();
        });

        body.getChildren().addAll(
                heading, sub,
                fieldLabel, usernameField, errorLabel,
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