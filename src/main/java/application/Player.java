package application;

import java.io.IOException;
import java.util.Scanner;

import org.jspace.*;

public class Player {
	private String id;
	private final int START_GATE = 30000;
	private final String START_NAME = "startSpace";
	private RemoteSpace chat;

	public Player() {
		final String uri = "tcp://localhost:" + START_GATE + "/" + START_NAME + "?keep";
		try {
			String username = getInput("Enter your username:");

			RemoteSpace startSpace = new RemoteSpace(uri);

			/*
			 * RemoteSpace lobby = new RemoteSpace(uriLobby); lobby.get("turn", playerid);
			 * lobby.put("drawfromshuffle", playerid); ...do stuff... lobby.put("endturn",
			 * playerid);
			 * 
			 */

			startSpace.put("user connected", username);

			id = (String) (chat.get(new ActualField("uniqueid"), new ActualField(username),
					new FormalField(String.class)))[2];

		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		Player player = new Player();
	}
	
	public String getInput(String question) {
		Scanner reader = new Scanner(System.in);
		
		System.out.println(question);
		String input = reader.nextLine();
		reader.close();
		return input;
	}
}

/* CHAT FUNCTIONALITY
	new Thread(new printMessages(chat)).start();

	while (true) {
		String message = reader.nextLine();
		chat.put("message", username, message);
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
*/


