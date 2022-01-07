package application;

import java.io.IOException;
import java.util.Scanner;

import org.jspace.*;

public class Player {
	private String id;
	private final int START_GATE = 30000;
	private final String startMenu = "startMenu";
	private RemoteSpace chat;

	public Player() {
		final String uri = "tcp://localhost:" + START_GATE + "/" + startMenu + "?keep";
		Scanner reader = new Scanner(System.in);
		try {
			System.out.println("Enter your username: ");
			String username = reader.nextLine();

			chat = new RemoteSpace(uri);

			/*
			 * RemoteSpace lobby = new RemoteSpace(uriLobby); lobby.get("turn", playerid);
			 * lobby.put("drawfromshuffle", playerid); ...do stuff... lobby.put("endturn",
			 * playerid);
			 * 
			 */

			chat.put("user connected", username);

			id = (String) (chat.get(new ActualField("uniqueid"), new ActualField(username),
					new FormalField(String.class)))[2];

			System.out.println("Connected to chat - start chatting!");

			new Thread(new printMessages(chat)).start();

			while (true) {
				String message = reader.nextLine();
				chat.put("message", username, message);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		Player player = new Player();
	}

	class printMessages implements Runnable {
		Space chat;

		public printMessages(Space chat) {
			this.chat = chat;
		}

		public void run() {
			while (true) {
				Object[] t;
				try {
					t = chat.get(new ActualField(id), new FormalField(String.class), new FormalField(String.class));
					System.out.println(t[1] + ": " + t[2]);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}

}
