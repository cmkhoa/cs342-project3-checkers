import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Simple server GUI.
 * Shows a live log of all server activity (connections, registrations,
 * game starts, chat, disconnects).
 *
 * The clear-log button lets the operator flush the list.
 */
public class GuiServer extends Application {

	private Server           serverConnection;
	private ListView<String> logView;

	// ─────────────────────────────────────────────────────────────────────────
	public static void main(String[] args) { launch(args); }

	@Override
	public void start(Stage stage) {
		// ── Start server, wire log callback ──────────────────────────────────
		serverConnection = new Server(entry -> Platform.runLater(() -> {
			logView.getItems().add(entry.toString());
			logView.scrollTo(logView.getItems().size() - 1);
		}));

		// ── Build UI ─────────────────────────────────────────────────────────
		Label title = new Label("CHECKERS  —  Server");
		title.setFont(Font.font("Inter", FontWeight.NORMAL, 28));
		title.setTextFill(Color.BLACK);

		Label subtitle = new Label("Listening on port 5555");
		subtitle.setFont(Font.font("Inter", 14));
		subtitle.setTextFill(Color.GRAY);

		VBox header = new VBox(4, title, subtitle);
		header.setAlignment(Pos.CENTER_LEFT);
		header.setPadding(new Insets(20, 20, 12, 20));
		header.setStyle("-fx-border-color: transparent transparent #DDDDDD transparent; " +
				"-fx-border-width: 0 0 1.5 0;");

		logView = new ListView<>();
		logView.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 13; " +
				"-fx-background-color: #FAFAFA; -fx-border-color: #CCCCCC;");
		VBox.setVgrow(logView, Priority.ALWAYS);

		// Clear-log button
		Button clearBtn = new Button("Clear Log");
		clearBtn.setFont(Font.font("Inter", 13));
		clearBtn.setStyle("-fx-background-color: white; -fx-border-color: #222; " +
				"-fx-border-width: 1.5; -fx-cursor: hand; -fx-padding: 5 16;");
		clearBtn.setOnAction(e -> logView.getItems().clear());

		HBox footer = new HBox(clearBtn);
		footer.setAlignment(Pos.CENTER_RIGHT);
		footer.setPadding(new Insets(10, 20, 12, 20));
		footer.setStyle("-fx-border-color: #DDDDDD transparent transparent transparent; " +
				"-fx-border-width: 1.5 0 0 0;");

		VBox logSection = new VBox(logView);
		logSection.setPadding(new Insets(0, 20, 0, 20));
		VBox.setVgrow(logSection, Priority.ALWAYS);

		VBox root = new VBox(header, logSection, footer);
		root.setStyle("-fx-background-color: white;");
		VBox.setVgrow(logSection, Priority.ALWAYS);

		// ── Stage ─────────────────────────────────────────────────────────────
		stage.setTitle("Checkers Server");
		stage.setScene(new Scene(root, 560, 520));
		stage.setMinWidth(400);
		stage.setMinHeight(350);
		stage.setOnCloseRequest((WindowEvent e) -> {
			Platform.exit();
			System.exit(0);
		});
		stage.show();
	}
}
