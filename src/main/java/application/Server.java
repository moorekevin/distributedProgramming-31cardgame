// TODO: max number of lobbies, exit lobby
package application;

import java.util.ArrayList;
import java.util.UUID;

import org.jspace.*;

public class Server {
	public static final String START_URI = "tcp://localhost:3000/";
	public static final String END_URI = "?keep";

	public static void main(String[] args) {
		SpaceRepository rep = new SpaceRepository();
		SequentialSpace startSpace = new SequentialSpace();
		ArrayList<String> users = new ArrayList<String>();

		rep.add("startMenu", startSpace);

		final String gateUri = START_URI + END_URI;
		rep.addGate(gateUri);

		System.out.println("Game server running");

		// Connect and assign uniqueID to users
		connectUsers(users, rep, startSpace);
	}

	private static void connectUsers(ArrayList<String> users, SpaceRepository rep, Space startSpace) {
		new Thread(new lobbyButler(rep, startSpace)).start();

		while (true) {
			try {
				String user = ((String) (startSpace.get(new ActualField("user connected"),
						new FormalField(String.class)))[1]);
				String uniqueID = UUID.randomUUID().toString();
				users.add(uniqueID);
				startSpace.put("uniqueid", user, uniqueID);

				System.out.println(user + " has connected");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

/*
 * static class printChat implements Runnable { Space chat;
 * 
 * public printChat(Space chat) { this.chat = chat; }
 * 
 * public void run() { while(true) { try { Object[] t = chat.get(new
 * ActualField("message"),new FormalField(String.class), new
 * FormalField(String.class)); for (String user : Server.users) { chat.put(user,
 * t[1], t[2]); } System.out.println(t[1] + ": " + t[2]); } catch
 * (InterruptedException e) { e.printStackTrace(); } }
 * 
 * } }
 */

// Creates a lobby or sets a lobby for a player
class lobbyButler implements Runnable {
	SpaceRepository rep;
	Space startSpace;

	public lobbyButler(SpaceRepository rep, Space startSpace) {
		this.rep = rep;
		this.startSpace = startSpace;
	}

	public void run() {
		try {
			while (true) {
				// (UNIQUEIDENTIFIER, "join", "Name")
				// (UNIQUEIDENTIFIER, "host", "Name")
				Object[] request = (startSpace.get(new FormalField(String.class), new FormalField(String.class),
						new FormalField(String.class)));
				String userID = (String) request[0];
				String command = (String) request[1];
				String lobbyName = (String) request[2];

				if (command.equals("join")) {
					new Thread(new joinLobby(rep, startSpace, lobbyName, userID)).start();
				} else if (command.equals("host")) {
					new Thread(new createLobby(rep, startSpace, lobbyName, userID)).start();
				} else {
					startSpace.put("message", userID, "Unknown command, please try again");
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

class createLobby implements Runnable {
	SpaceRepository rep;
	String lobbyName;
	Space startSpace;
	String userID;

	public createLobby(SpaceRepository rep, Space startSpace, String lobbyName, String userID) {
		this.rep = rep;
		this.lobbyName = lobbyName;
		this.startSpace = startSpace;
		this.userID = userID;
	}

	public void run() {
		try {
			if (rep.get(lobbyName) != null) {
				System.out.println("User tried to create lobby " + lobbyName + " but it already exists");
				startSpace.put("message", userID, "Lobby " + lobbyName + " already exists");
			} else {
				SequentialSpace createdLobby = new SequentialSpace();
				rep.add(lobbyName, createdLobby);
				System.out.println("Created lobby " + lobbyName + " for " + userID);
				new joinLobby(rep, startSpace, lobbyName, userID);
				
				createdLobby.put("host", userID);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

class joinLobby implements Runnable {
	SpaceRepository rep;
	Space startSpace;
	Space lobby;
	String lobbyName;
	String userID;

	public joinLobby(SpaceRepository rep, Space startSpace, String lobbyName, String userID) {
		this.startSpace = startSpace;
		this.rep = rep;
		this.userID = userID;
		this.lobby = rep.get(lobbyName);
	}

	public void run() {
		try {
			if (lobby == null) {
				System.out.println("Lobby " + lobbyName + " is unavailable");
				startSpace.put("message", userID, "Lobby " + lobbyName + " is unavailable");
			} else {
				String lobbyURI = Server.START_URI + lobbyName + Server.END_URI;
				startSpace.put("join this lobby", userID, lobbyURI);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
