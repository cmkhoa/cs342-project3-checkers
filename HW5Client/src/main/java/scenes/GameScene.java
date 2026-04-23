package scenes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

/**
 * Game scene
 */
public class GameScene {

    public interface Actions {
        void onBoardClick(double x, double y);

        void onBack();

        void onForfeit();

        void onSendChat(String text);

        void onOpponentNameClick();
    }

    // Live node references
    public Canvas boardCanvas;
    public Label statusLabel;
    public Label redCountLabel;
    public Label blackCountLabel;
    public ListView<String> chatListView;
    public Label opponentLabel;

    public Scene build(String myUsername, String opponentName, boolean isOnline, Actions actions) {
        VBox root = UI.sceneRoot();
        root.setSpacing(0);

        // Top bar
        Button back = UI.backButton();
        back.setOnAction(e -> actions.onBack());

        Region barSpacer = new Region();
        HBox.setHgrow(barSpacer, Priority.ALWAYS);

        HBox topBar = new HBox(8, back, barSpacer);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 12, 10, 8));
        topBar.getStyleClass().add("game-top-bar");

        // show opponent name and forfeit button when online
        if (isOnline) {
            opponentLabel = new Label(opponentName.isEmpty() ? "" : opponentName.toUpperCase());
            opponentLabel.getStyleClass().add("game-opponent-label");
            if (!opponentName.isEmpty())
                opponentLabel.setOnMouseClicked(e -> actions.onOpponentNameClick());

            Button forfeitBtn = UI.dangerButton("FORFEIT");
            forfeitBtn.setOnAction(e -> actions.onForfeit());

            topBar.getChildren().addAll(opponentLabel, forfeitBtn);
        }

        // Status bar
        statusLabel = new Label("—");
        statusLabel.getStyleClass().add("status-bar-text");

        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        HBox statusBar = new HBox(leftSpacer, statusLabel, rightSpacer);
        statusBar.setAlignment(Pos.CENTER);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(8, 8, 8, 8));

        // Board
        // create board via canvas and set on mouse click
        boardCanvas = new Canvas(UI.BOARD_PX, UI.BOARD_PX);
        boardCanvas.setOnMouseClicked(e -> actions.onBoardClick(e.getX(), e.getY()));

        StackPane boardWrapper = new StackPane(boardCanvas);
        boardWrapper.getStyleClass().add("game-board-wrapper");
        boardWrapper.setMaxSize(UI.BOARD_PX + 4, UI.BOARD_PX + 4);
        boardWrapper.setPrefSize(UI.BOARD_PX + 4, UI.BOARD_PX + 4);

        HBox boardRow = new HBox(boardWrapper);
        boardRow.setAlignment(Pos.CENTER);
        boardRow.setPadding(new Insets(4, 0, 4, 0));

        // Root (board always present; chat only online)
        root.getChildren().addAll(topBar, statusBar);

        if (isOnline) {
            root.getChildren().add(boardRow);
            root.getChildren().add(buildChatSection(actions));
        } else {
            // Center the board vertically when there's no chat
            Region topSpacer = new Region();
            Region bottomSpacer = new Region();
            VBox.setVgrow(topSpacer, Priority.ALWAYS);
            VBox.setVgrow(bottomSpacer, Priority.ALWAYS);
            root.getChildren().addAll(topSpacer, boardRow, bottomSpacer);
        }

        Scene scene = new Scene(root, UI.W, UI.H);
        scene.getStylesheets().add(GameScene.class.getResource("/styles.css").toExternalForm());
        return scene;
    }

    // Chat box (online only)
    private VBox buildChatSection(Actions actions) {
        Label chatHeader = new Label("CHAT");
        chatHeader.getStyleClass().add("chat-header");
        chatHeader.setPadding(new Insets(8, 0, 4, 0));

        chatListView = new ListView<>();
        chatListView.getStyleClass().add("chat-list");
        chatListView.setPrefHeight(110);
        VBox.setVgrow(chatListView, Priority.ALWAYS);

        TextField chatInput = UI.chatInputField("Message…");
        HBox.setHgrow(chatInput, Priority.ALWAYS);

        Button sendBtn = new Button("SEND");
        sendBtn.getStyleClass().add("btn-send");

        Runnable send = () -> {
            String text = chatInput.getText().trim();
            if (text.isEmpty())
                return;
            actions.onSendChat(text);
            chatInput.clear();
        };
        sendBtn.setOnAction(e -> send.run());
        chatInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                send.run();
        });

        HBox inputRow = new HBox(0, chatInput, sendBtn);
        inputRow.setAlignment(Pos.CENTER);

        VBox section = new VBox(4, chatHeader, chatListView, inputRow);
        section.setPadding(new Insets(0, 12, 12, 12));
        VBox.setVgrow(chatListView, Priority.ALWAYS);
        return section;
    }

    // scrolls the chat view to the bottom
    public void scrollChatToBottom() {
        if (chatListView != null && !chatListView.getItems().isEmpty())
            chatListView.scrollTo(chatListView.getItems().size() - 1);
    }
}