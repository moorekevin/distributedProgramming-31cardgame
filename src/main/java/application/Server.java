// TODO: max number of lobbies, exit lobby
package application;

import java.util.List;
import java.util.UUID;

import org.jspace.*;

import java.util.HashMap;

public class Server {
	public static final int LOBBY_CAPACITY = 4;
	public static final String START_URI = "tcp://localhost:9002/";
	public static final String END_URI = "?keep";
	public HashMap<String, String> users;
	public HashMap<Space, Thread> lobbies;
	SpaceRepository rep = new SpaceRepository();
	SequentialSpace startSpace = new SequentialSpace();
	
	public static void main(String[] args) {
		Server server = new Server();
		server.rep = new SpaceRepository();
		server.startSpace = new SequentialSpace();
		// lobbyName , id
		server.users = new HashMap<String, String>();

		server.rep.add("startSpace", server.startSpace);

		final String gateUri = START_URI + END_URI;
		server.rep.addGate(gateUri);

		System.out.println("Game server running");

		// Connect and assign uniqueID to users
		server.connectUsers();
	}

	private void connectUsers() {
		new Thread(new lobbyButler()).start();

		while (true) {
			try {
				String username = ((String) (startSpace.get(new ActualField("user connected"),
						new FormalField(String.class)))[1]);
				String uniqueID = UUID.randomUUID().toString();
				users.put(uniqueID, username);
				startSpace.put("uniqueid", username, uniqueID);

				System.out.println(username + " has connected" + " assigned ID " + uniqueID);
			} catch (InterruptedException e) {
				e.printStackTrace();
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
						space.put("serverresponse", action, userRequesting, users.get(userRequested));
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	class playerActivity implements Runnable {
		Space lobby;
		String playerID;

		public playerActivity(Space lobby, String playerID) {
			this.lobby = lobby;
			this.playerID = playerID;
		}

		public void run() {
			try {
				while (true) {
					Thread.sleep(5000); // Waiting to ping players again
					startSpace.put("userrequest", "ping", playerID);
					Thread.sleep(5000); // Waiting for player response
					Object[] response = startSpace.getp(new ActualField("userresponse"), new ActualField("ping"),
							new ActualField(playerID));
					if (response == (null)) {
						String host = (String) (lobby.query(new ActualField("host"), new FormalField(String.class)))[1];
						String lobbyName = (String) (lobby.query(new ActualField("lobbyname"),
								new FormalField(String.class)))[1];
						if (host.equals(playerID)) {
							lobby.put("exitlobby");
							startSpace.get(new ActualField("lobbyname"), new ActualField(lobbyName),
									new FormalField(Integer.class));
							rep.remove(lobbyName);
						} else {
							lobby.put("inactiveplayer", playerID, users.get(playerID));
							lobby.get(new ActualField("lobbymember"), new ActualField(playerID));
							users.remove(playerID);
							Object[] t = startSpace.getp(new ActualField("lobbyname"), new ActualField(lobbyName),
									new FormalField(Integer.class));
							if (t != null) {
								List<Object[]> members = lobby.queryAll(new ActualField("lobbymember"),
										new FormalField(String.class));
								startSpace.put("lobbyname", lobbyName, members.size());
							}
						}
						System.out.println("Didnt get response for " + playerID);
						break;
					}
				}

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	// Creates a lobby or sets a lobby for a player
	class lobbyButler implements Runnable {
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
						new Thread(new joinLobby(lobbyName, userID)).start();
					} else if (command.equals("h")) {
						new Thread(new createLobby(lobbyName, userID)).start();
					} else if (command.equals("s")) {
						new Thread(new startLobby(lobbyName, userID)).start();
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
		String lobbyName;
		String userID;

		public createLobby(String lobbyName, String userID) {
			this.lobbyName = lobbyName;
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
					startSpace.put("lobbyname", lobbyName, 0); // Used for giving an overview of which lobbies are
																// available to players
					System.out.println("Created Lobby \"" + lobbyName + "\" for " + userID);

					createdLobby.put("host", userID);
					createdLobby.put("lobbyname", lobbyName);
					createdLobby.put("lobbystatus", "public");

					(new joinLobby(lobbyName, userID)).run();

					new Thread(new listenLobby(createdLobby)).start();

				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	class joinLobby implements Runnable {
		String lobbyName;
		String userID;

		public joinLobby(String lobbyName, String userID) {
			this.userID = userID;
			this.lobbyName = lobbyName;
		}

		public void run() {
			try {
				Space lobby = rep.get(lobbyName);
				if (lobby == null) { // If no lobby with that name
					System.out.println("User " + userID + "tried to join \"" + lobbyName + "\" but it is unavailable");
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

						if (members.size() >= Server.LOBBY_CAPACITY) { // If lobby is at max capacity
							startSpace.put("lobbyinfo", "error", userID,
									"Lobby \"" + lobbyName + "\" is full, try another!");
						} else { // Success
							System.out.println("User " + userID + " joining lobby " + lobbyName);

							// Puts to all players in members
							// Player himself has not been added to members yet
							for (Object[] member : members) {
								lobby.put("info", (String) member[1], "joinedplayer", users.get(userID));
							}

							String lobbyURI = Server.START_URI + lobbyName + Server.END_URI;

							startSpace.get(new ActualField("lobbyname"), new ActualField(lobbyName), new FormalField(Integer.class));
							startSpace.put("lobbyname", lobbyName, members.size() + 1);
							startSpace.put("lobbyinfo", "join", userID, lobbyURI);
							// Start pinging user in lobby
							new Thread(new playerActivity(lobby, userID)).start();
						}
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	class startLobby implements Runnable {
		String lobbyName;
		String userID;

		public startLobby(String lobbyName, String userID) {
			this.userID = userID;
			this.lobbyName = lobbyName;
		}

		public void run() {
			try {
				Space lobby = rep.get(lobbyName);
				lobby.get(new ActualField("lobbystatus"), new ActualField("public"));
				List<Object[]> members = lobby.queryAll(new ActualField("lobbymember"), new FormalField(String.class));
				if (members.size() < 2) {
					lobby.put("lobbystatus", "public");
					startSpace.put("lobbyinfo", "error", userID,
							"Lobby \"" + lobbyName + "\" only contains you! Wait for players to join!");
				} else {
					startSpace.put("lobbyinfo", "success", userID, "Game is starting!");
					lobby.put("lobbystatus", "private");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}