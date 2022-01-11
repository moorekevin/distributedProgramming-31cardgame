// TODO: max number of lobbies, exit lobby
package application;

import java.util.List;
import java.util.UUID;

import org.jspace.*;

import java.util.HashMap;

public class Server {
	public static final String START_URI = "tcp://localhost:9002/";
	public static final String END_URI = "?keep";
	public static HashMap<String, String> users;

	public static void main(String[] args) {
		SpaceRepository rep = new SpaceRepository();
		SequentialSpace startSpace = new SequentialSpace();
		// lobbyName , id
		users = new HashMap<String, String>();
		
		rep.add("startSpace", startSpace);

		final String gateUri = START_URI + END_URI;
		rep.addGate(gateUri);

		System.out.println("Game server running");

		// Connect and assign uniqueID to users
		connectUsers(users, rep, startSpace);
	}

	private static void connectUsers(HashMap<String, String> users, SpaceRepository rep, Space startSpace) {
		new Thread(new lobbyButler(users, rep, startSpace)).start();

		while (true) {
			try {
				String username = ((String) (startSpace.get(new ActualField("user connected"),
						new FormalField(String.class)))[1]);
				String uniqueID = UUID.randomUUID().toString();
				users.put(uniqueID, username);
				startSpace.put("uniqueid", username, uniqueID);

				System.out.println(username + " has connected");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

class listenLobby implements Runnable {
	Space space;

	public listenLobby(Space space) {
		this.space = space;
		
	}

	public void run() {
		try {
			while (true) {
				Object[] request = space.get(new ActualField("serverrequest"), new FormalField(String.class),
						new FormalField(String.class), new FormalField(String.class));
				String action = (String) request[1];
				if (action.equals("username")) {
					String userRequesting = (String) request[2];
					String userRequested = (String) request[3];
					space.put("serverresponse", action, userRequesting, Server.users.get(userRequested));
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

class playerActivity implements Runnable {
	Space space;
	Space lobby;
	String playerID;
	
	public playerActivity(Space space, Space lobby, String playerID) {
		this.space = space;
		this.lobby = lobby;
		this.playerID = playerID;
	}
	
	public void run() {
		try {
			while(true) {
				Thread.sleep(30000); // Waiting to ping players again
				space.put("userpingrequest", playerID);
//				System.out.println("Pinging " + playerID);
				Thread.sleep(5000); // Waiting for player response
//				System.out.println("Getting response for " + playerID);
				Object[] response = space.getp(new ActualField("userpingresponse"), new ActualField(playerID));
				if (response == (null)) {
					lobby.put("inactiveplayer", playerID, Server.users.get(playerID));
					Server.users.remove(playerID);
//					System.out.println("Didnt get response for " + playerID);
					break;
				} else {
//					System.out.println("Got response for " + playerID);
				}
			}
			
		} catch (InterruptedException e) {
			e.printStackTrace();
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
	HashMap<String, String> users;

	public lobbyButler(HashMap<String, String> users, SpaceRepository rep, Space startSpace) {
		this.users = users;
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
					new Thread(new createLobby(rep, startSpace, lobbyName, userID, users)).start();
				} else if (command.equals("s")) {
					new Thread(new startLobby(rep, startSpace, lobbyName, userID)).start();
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
	HashMap<String, String> users;

	public createLobby(SpaceRepository rep, Space startSpace, String lobbyName, String userID,
			HashMap<String, String> users) {
		this.users = users;
		this.rep = rep;
		this.lobbyName = lobbyName;
		this.startSpace = startSpace;
		this.userID = userID;
	}

	public void run() {
		try {
			if (rep.get(lobbyName) != null) {
				System.out.println("User tried to create Lobby \"" + lobbyName + "\" but it already exists");
				startSpace.put("lobbyinfo", "error", userID, "Lobby \"" + lobbyName + "\" already exists");
			} else {
				SequentialSpace createdLobby = new SequentialSpace();
				rep.add(lobbyName, createdLobby);
				startSpace.put("lobbyname", lobbyName, 0);
				System.out.println("Created Lobby \"" + lobbyName + "\" for " + userID);

				createdLobby.put("host", userID);
				createdLobby.put("lobbystatus", "public");

				(new joinLobby(rep, startSpace, lobbyName, userID)).run();
				new Thread(new listenLobby(createdLobby)).start();
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
				System.out.println("User " + userID + "tried to join\"" + lobbyName + "\" but it is unavailable");
				startSpace.put("lobbyinfo", "error", userID, "Lobby \"" + lobbyName + "\" is unavailable");
			} else {
				String status = (String) (lobby.query(new ActualField("lobbystatus"),
						new FormalField(String.class)))[1];
				if (status.equals("private")) { // If lobby is private and the game is already begun
					startSpace.put("lobbyinfo", "error", userID,
							"Lobby \"" + lobbyName + "\" has already started, try another!");
				} else {
					List<Object[]> members = lobby.queryAll(new ActualField("lobbymember"),
							new FormalField(String.class));

					if (members.size() >= 4) { // If lobby is at max capacity
						startSpace.put("lobbyinfo", "error", userID, "Lobby \"" + lobbyName + "\" is full, try another!");
					} else { // Success
						System.out.println("User " + userID + " joining lobby " + lobbyName);
						String lobbyURI = Server.START_URI + lobbyName + Server.END_URI;
						Object[] t = startSpace.get(new ActualField("lobbyname"), new ActualField(lobbyName), new FormalField(Integer.class));
						int cap = (int) t[2] + 1;
						startSpace.put("lobbyname", lobbyName, cap);
						startSpace.put("lobbyinfo", "join", userID, lobbyURI);
						// Start pinging user in lobby
						new Thread(new playerActivity(startSpace, lobby, userID)).start();
					}
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

class startLobby implements Runnable {
	SpaceRepository rep;
	Space startSpace;
	Space lobby;
	String lobbyName;
	String userID;

	public startLobby(SpaceRepository rep, Space startSpace, String lobbyName, String userID) {
		this.startSpace = startSpace;
		this.rep = rep;
		this.userID = userID;
		this.lobby = rep.get(lobbyName);
		this.lobbyName = lobbyName;
	}

	public void run() {
		try {
			lobby.get(new ActualField("lobbystatus"), new ActualField("public"));
			List<Object[]> members = lobby.queryAll(new ActualField("lobbymember"), new FormalField(String.class));
			if (members.size() < 2) {
				lobby.put("lobbystatus", "public");
				startSpace.put("lobbyinfo", "error", userID, "Lobby \"" + lobbyName + "\" only contains you! Wait for players to join!");
			} else {
				startSpace.put("lobbyinfo", "success", userID, "Game is starting!");
				lobby.put("lobbystatus", "private");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
