package application;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Scanner;

import org.jspace.*;

public class Player {
	private String id;
	private final int START_GATE = 9002;
	private final String START_NAME = "startSpace";
	private RemoteSpace startSpace;

	public Player() {
		final String uri = "tcp://localhost:" + START_GATE + "/" + START_NAME + "?keep";
		try {
			String username = getInput("Enter your username: ");


			startSpace = new RemoteSpace(uri);
			/*
			 * RemoteSpace lobby = new RemoteSpace(uriLobby); lobby.get("turn", playerid);
			 * lobby.put("drawfromshuffle", playerid); ...do stuff... lobby.put("endturn",
			 * playerid);
			 * 
			 */

			startSpace.put("user connected", username);
			id = (String) (startSpace.get(new ActualField("uniqueid"), new ActualField(username),
					new FormalField(String.class)))[2];
			new Thread(new communicateToServer()).start();

		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Player player = new Player();
		try {
			player.readStartOption();
			player.joinLobby();
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	public void readStartOption() throws InterruptedException, IOException {
		String command = getInput("Do you want to (h)ost or (j)oin a lobby?\nSay:").toLowerCase();
		
		String lobbyName = getInput("What is the name of the lobby?\n Spaces will get replaced with \"_\"\nType:").toLowerCase().replaceAll("\\s+","_");
		
		startSpace.put("lobby request", id, command, lobbyName);
		

		
	}

	@SuppressWarnings("resource")
	public String getInput(String question) {
		Scanner reader = new Scanner(System.in);

		System.out.println(question);
		String input = reader.nextLine();
		return input;
	}

	public void joinLobby() throws InterruptedException, IOException {
		try {
			String lobbyURI = (String) (startSpace.get(new ActualField("join this lobby"), new ActualField(id),
					new FormalField(String.class)))[2];
			RemoteSpace lobbySpace = new RemoteSpace(lobbyURI);

			Object[] isHost = lobbySpace.getp(new ActualField("host"), new ActualField(id));
			if (isHost != null) {
				new Thread(new Lobby(lobbySpace)).start();
			}
			System.out.println("Joined lobby ");
			startPlaying(lobbySpace);
		} catch (UnknownHostException e) {
			System.out.println("ERROR: Cannot find lobby, check URI");
		}
	}

	public void startPlaying(Space lobbySpace) {

	}

	class communicateToServer implements Runnable {
		public void run() {
			while (true) {
				try {
					String message = (String) (startSpace.get(new ActualField("message"), new ActualField(id), new FormalField(String.class)))[2];
					System.out.println(message);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}

/*
 * CHAT FUNCTIONALITY new Thread(new printMessages(chat)).start();
 * 
 * while (true) { String message = reader.nextLine(); chat.put("message",
 * username, message); } class printMessages implements Runnable { Space chat;
 * 
 * public printMessages(Space chat) { this.chat = chat; }
 * 
 * public void run() { while (true) { Object[] t; try { t = chat.get(new
 * ActualField(id), new FormalField(String.class), new
 * FormalField(String.class)); System.out.println(t[1] + ": " + t[2]); } catch
 * (InterruptedException e) { e.printStackTrace(); } } }
 * 
 * }
 */
