// TODO: max number of lobbies, exit lobby
package application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspace.*;

public class Server {
	public static final String START_URI = "tcp://localhost:9002/";
	public static final String END_URI = "?keep";

	public static void main(String[] args) {
		SpaceRepository rep = new SpaceRepository();
		SequentialSpace startSpace = new SequentialSpace();
		SequentialSpace lobbyMembers = new SequentialSpace();
		// lobbyName , id
		ArrayList<String> users = new ArrayList<String>();

		rep.add("startSpace", startSpace);

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
				String username = ((String) (startSpace.get(new ActualField("user connected"),
						new FormalField(String.class)))[1]);
				String uniqueID = UUID.randomUUID().toString();
				users.add(uniqueID);
				startSpace.put("uniqueid", username, uniqueID);

				System.out.println(username + " has connected");
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
				// ("lobby request", UNIQUEIDENTIFIER, "join", "Name")
				// ("lobby request", UNIQUEIDENTIFIER, "host", "Name")
				Object[] request = (startSpace.get(new ActualField("lobbyrequest"), new FormalField(String.class),
						new FormalField(String.class), new FormalField(String.class)));
				String userID = (String) request[1];
				String command = ((String) request[2]).toLowerCase();
				String lobbyName = (String) request[3];

				if (command.equals("j")) {
					new Thread(new joinLobby(rep, startSpace, lobbyName, userID)).start();
				} else if (command.equals("h")) {
					new Thread(new createLobby(rep, startSpace, lobbyName, userID)).start();
				} else {
					startSpace.put("error", userID, "Unknown command \"" + command + "\", please try again");
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
				startSpace.put("lobbyinfo", "error", userID, "Lobby " + lobbyName + " already exists");
			} else {
				SequentialSpace createdLobby = new SequentialSpace();
				rep.add(lobbyName, createdLobby);
				System.out.println("Created lobby " + lobbyName + " for " + userID);

				createdLobby.put("host", userID);
				createdLobby.put("lobbystatus", "public");
				
				(new joinLobby(rep, startSpace, lobbyName, userID)).run();
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
		this.lobbyName = lobbyName;
	}

	public void run() {
		try {
			if (lobby == null) { // If no lobby with that name
				System.out.println("User " + userID  + "tried to join " + lobbyName + " but it is unavailable");
				startSpace.put("lobbyinfo", "error", userID, "Lobby " + lobbyName + " is unavailable");
			} else {
				String status = (String) (lobby.query(new ActualField("lobbystatus"), new FormalField(String.class)))[1];
				if (status.equals("private")) { // If lobby is private and the game is already begun
					startSpace.put("lobbyinfo", "error", userID, "Lobby " + lobbyName + " has already started, try another!");
				} else {
					List<Object[]> members = lobby.queryAll(new ActualField("lobbymember"), new FormalField(String.class));
					
					if (members.size() >= 4) { // If lobby is at max capacity
						startSpace.put("lobbyinfo", "error", userID, "Lobby " + lobbyName + " is full, try another!");
					} else { // Success
						System.out.println("User " + userID + " joining lobby " + lobbyName);
						String lobbyURI = Server.START_URI + lobbyName + Server.END_URI;
						startSpace.put("lobbyinfo", "join", userID, lobbyURI);
					}
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
