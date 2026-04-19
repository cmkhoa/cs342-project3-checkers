import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Background thread that maintains the TCP connection to the server.
 * Incoming {@link Message} objects are delivered via the callback.
 * {@link #send(Message)} is thread-safe.
 */
public class Client extends Thread {

	private Socket             socket;
	private ObjectOutputStream out;
	private ObjectInputStream  in;
	private final Consumer<Message> callback;

	private volatile boolean running = true;

	Client(Consumer<Message> callback) {
		this.callback = callback;
		setDaemon(true);   // don't prevent JVM shutdown
	}

	@Override
	public void run() {
		try {
			socket = new Socket("127.0.0.1", 5555);
			// Create output stream FIRST to avoid deadlock
			out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			in  = new ObjectInputStream(socket.getInputStream());
			socket.setTcpNoDelay(true);
		} catch (Exception e) {
			System.err.println("Could not connect to server: " + e.getMessage());
			Message err = new Message(Message.Type.REGISTER_FAIL,
					"Cannot connect to server at 127.0.0.1:5555");
			callback.accept(err);
			return;
		}

		while (running) {
			try {
				Message msg = (Message) in.readObject();
				callback.accept(msg);
			} catch (Exception e) {
				if (running) {
					System.err.println("Lost connection to server: " + e.getMessage());
					Message disc = new Message(Message.Type.QUIT_GAME, "Connection lost");
					callback.accept(disc);
				}
				break;
			}
		}
	}

	/** Send a message to the server. Safe to call from any thread. */
	public synchronized void send(Message msg) {
		if (out == null) return;
		try {
			out.writeObject(msg);
			out.flush();
			out.reset();   // prevent stale-object caching in ObjectOutputStream
		} catch (IOException e) {
			System.err.println("Send failed: " + e.getMessage());
		}
	}

	/** Gracefully stop the connection. */
	public void disconnect() {
		running = false;
		try { if (socket != null) socket.close(); } catch (IOException ignored) {}
	}
}
