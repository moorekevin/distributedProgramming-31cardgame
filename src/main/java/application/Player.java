package application;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.jspace.*;

import application.Card.Num;
import application.Card.Suit;

public class Player {
	private String username;
	private String id;
	private String lobbyName;
	private Card cardInUse = null;
	private final int START_GATE = 9002;
	private final String START_NAME = "startSpace";
	private RemoteSpace startSpace;
	private RemoteSpace lobbySpace;
	private SequentialSpace handSpace = new SequentialSpace();
	private SequentialSpace messageTokens = new SequentialSpace();

	public Player() {
		final String uri = "tcp://localhost:" + START_GATE + "/" + START_NAME + "?keep";
		try {
			username = getInput("Enter your username");

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
			new Thread(new getPingsFromServer()).start();
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Player player = new Player();
		try {
			player.readStartOption();

			player.joinLobby();
			if (player.isHost()) {
				player.createGame();
			}
			player.startPlaying();
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	private boolean isHost() throws InterruptedException {
		Object[] isHost = lobbySpace.queryp(new ActualField("host"), new ActualField(id));
		return (isHost != null);
	}

	private void readStartOption() throws InterruptedException, IOException {
		String command = getInput("Do you want to (h)ost or (j)oin a lobby?").toLowerCase();
		while (!(command.equals("h") || command.equals("j"))) {
			printError(command);
			command = getInput("Try again: Do you want to (h)ost or (j)oin a lobby?").toLowerCase();
		}
		lobbyName = getInput("What is the name of the lobby?\n Spaces will get replaced with \"_\"").toLowerCase()
				.replaceAll("\\s+", "_");

		startSpace.put("lobbyrequest", id, command, lobbyName);

		Object[] t = startSpace.query(new ActualField("lobbyinfo"), new FormalField(String.class), new ActualField(id),
				new FormalField(String.class));
		if (((String) t[1]).equals("error")) {
			System.out.println((String) t[3]);
			startSpace.get(new ActualField("lobbyinfo"), new FormalField(String.class), new ActualField(id),
					new FormalField(String.class));
			readStartOption();
		}

	}

	@SuppressWarnings("resource")
	private String getInput(String question) {
		Scanner reader = new Scanner(System.in);
		System.out.print(question + "\n> ");
		return reader.nextLine();
	}

	private void joinLobby() throws InterruptedException, IOException {
		try {
			String lobbyURI = (String) (startSpace.get(new ActualField("lobbyinfo"), new ActualField("join"),
					new ActualField(id), new FormalField(String.class)))[3];
			lobbySpace = new RemoteSpace(lobbyURI);

			lobbySpace.put("lobbymember", id);

			new Thread(new getMessagesFromLobby()).start();

			System.out.println("Joined lobby. Waiting for game to start\n");
		} catch (UnknownHostException e) {

			printError("Cannot find lobby, check URI");
		}
	}

	private void createGame() throws InterruptedException {

		while (true) {
			String command = getInput("Do you want to (s)tart the game").toLowerCase(); // TODO: or (e)xit lobby?
			if (command.equals("s")) {
				startSpace.put("lobbyrequest", id, command, lobbyName);

				Object[] t = startSpace.get(new ActualField("lobbyinfo"), new FormalField(String.class),
						new ActualField(id), new FormalField(String.class));
				if (!((String) t[1]).equals("error")) {
					break;
				} else {
					printError((String) t[3]);
				}

			}
			/*
			 * else if (command.equals("e")) {
			 * 
			 * }
			 */
		}

		new Thread(new Game(lobbySpace)).start();
	}

	private void startPlaying() throws InterruptedException {

		// Get dealt cards
		Card[] initialHand = (Card[]) (lobbySpace.get(new ActualField("dealingcards"), new ActualField(id),
				new FormalField(Card[].class)))[2];
		for (Card thisCard : initialHand) {
			handSpace.put(thisCard);
		}

		while (true) {
			getToken("startofturn");
			messageTokens.get(new ActualField("printedturn"));

			displayHand(getHand()); // 3
			if (has31(getHand())) {
				doAnAction("31");
			}

			draw(); // +1
			discard(getHand()); // 4
			displayHand(getHand());

			if (has31(getHand())) {
				doAnAction("31");
			}

			knockOption();
		}

	}

	private void printError(String error) {
		System.out.println("ERROR: Unknown command \"" + error + "\"\n");
	}

	private List<Object[]> getHand() {
		return handSpace.queryAll(new FormalField(Card.class));
	}

	private void getToken(String action) throws InterruptedException {
		lobbySpace.get(new ActualField("token"), new ActualField(action), new ActualField(id));
	}

	private void displayHand(List<Object[]> allCards) {
		System.out.println("You have the following cards: ");
		int i = 1;
		for (Object[] obj : allCards) {
			Card card = ((Card) obj[0]);
			System.out.print("(" + i + "): " + card.toString() + "    ");
			i++;
		}
		System.out.println();
	}

	private void draw() throws InterruptedException {
		Card topOfDiscarded = null;
		// query top of discarded pile
		lobbySpace.put("action", "getdiscarded", id);
		Object[] q = lobbySpace.get(new ActualField("topdiscard"), new ActualField(id), new FormalField(Card.class));
		topOfDiscarded = (Card) q[2];

		String instruction = "Draw from (s)huffled pile or (d)iscarded pile: " + topOfDiscarded; // Show discarded pile
																									// top
		getTwoCommands("s", "d", instruction, "pickshuffled", "pickdiscarded");
		handSpace.put(cardInUse);
	}

	private void discard(List<Object[]> allCards) throws InterruptedException {
		getToken("discardacard");
		displayHand(getHand());
		
		int cardNumber = Integer.parseInt(getInput("Which card would you like to discard (1),(2),(3),(4)?")) - 1;
		while (cardNumber < 0 || cardNumber > 3) {
			printError("" + cardNumber + 1);
			cardNumber = Integer.parseInt(getInput("Try again: Which card would you like to discard (1),(2),(3),(4)?"))
					- 1;
		}

		Card discardThis = (Card) allCards.get(cardNumber)[0];
		handSpace.get(new ActualField(discardThis));
		cardInUse = (Card) allCards.get(cardNumber)[0];
		doAnAction("discard");
		cardInUse = null;
	}

	private void knockOption() throws InterruptedException {
		// TODO: Should not be able to knock if someone else already has
		getToken("chooseknock");
		String instruction = "Do you wish to (k)nock or (d)on't knock?";
		getTwoCommands("k", "d", instruction, "knock", "dontknock");

	}

	private boolean has31(List<Object[]> allCards) {
		String suit = ((Card) allCards.get(0)[0]).getSuit();
		boolean sameSuit = false;
		int points = 0;

		for (int i = 1; i < allCards.size(); i++) {
			sameSuit = suit.equals(((Card) allCards.get(i)[0]).getSuit());
		}
		for (int i = 0; i < allCards.size(); i++) {
			points += ((Card) allCards.get(i)[0]).getPoints();
		}

		return sameSuit && points == 31;

	}

	private void getTwoCommands(String command1, String command2, String instruction, String action1, String action2)
			throws InterruptedException {
		String command = getInput(instruction).toLowerCase();
		while (!(command.equals(command1) || command.equals(command2))) {
			printError(command);
			command = getInput("Try again: " + instruction).toLowerCase();
		}
		if (command.equals(command1))
			doAnAction(action1);
		if (command.equals(command2))
			doAnAction(action2);

	}

	private void doAnAction(String action) throws InterruptedException { // discard card
		lobbySpace.put("action", action, id);
		Object[] response = null;
		switch (action) {
		case "pickshuffled":
		case "pickdiscarded":

			response = (lobbySpace.get(new ActualField("response"), new ActualField(id), new ActualField(action),
					new FormalField(String.class), new FormalField(String.class), new FormalField(Card.class)));

			cardInUse = (Card) response[5];
			break;

		case "discard":
			lobbySpace.get(new ActualField("response"), new ActualField(action), new ActualField(id),
					new ActualField("ok"));

			lobbySpace.put("action", action, id, cardInUse);

			response = (lobbySpace.get(new ActualField("response"), new ActualField(id), new ActualField(action),
					new FormalField(String.class), new FormalField(String.class)));
			break;

		case "dontknock":
		case "knock":
		case "31":
			response = (lobbySpace.get(new ActualField("response"), new ActualField(id), new ActualField(action),
					new FormalField(String.class), new FormalField(String.class)));
			break;

		default:
			// TODO: Error stuff
			return;
		}

		// TODO: Maybe just delete this part
		if (((String) response[3]).equals("error")) {
			printError((String) response[4]);
		} else {
			System.out.println((String) response[4]);
		}

	}

	class getMessagesFromLobby implements Runnable {
		public void run() {
			while (true) {
				try {
					Object[] req = lobbySpace.get(new ActualField("info"), new ActualField(id),
							new FormalField(String.class), new FormalField(String.class));
					String playerID = (String) req[3];
					if (req[2].equals("whosturn")) {
						if (playerID.equals(id)) {
							System.out.println("It is now your turn");
							messageTokens.put("printedturn");
						} else {
							lobbySpace.put("serverrequest", "username", id, playerID);
							String username = (String) (lobbySpace.get(new ActualField("serverresponse"),
									new ActualField("username"), new ActualField(id),
									new FormalField(String.class)))[3];
							System.out.println("It is now player " + username + "'s turn");
						}
					} else if (req[2].equals("whosknocked")) {
						if (!playerID.equals(id)) {
							lobbySpace.put("serverrequest", "username", id, playerID);
							String username = (String) (lobbySpace.get(new ActualField("serverresponse"),
									new ActualField("username"), new ActualField(id),
									new FormalField(String.class)))[3];
							System.out
									.println("Be aware! Player " + username + " has knocked. This is the last round!");
						}
					} else if (req[2].equals("inactiveplayer")) {
						// PlayerID is not ID but a username here
						System.out.println("Player " + playerID + " has left the game. Game restarting");
					} else if (req[2].equals("won")) {
						
					} else if (req[2].equals("requestcards")) {
						
					}

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	class getPingsFromServer implements Runnable {
		public void run() {
			while (true) {
				try {
					startSpace.get(new ActualField("userpingrequest"), new ActualField(id));
					startSpace.put("userpingresponse",id);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}

//   put = ("action",command,userid)
//   get = ("response", userid, message, card (optional) )
// Deal cards
// put("dealingcards", userid, message, card[3])

// Wait on turn (should be in a loop)
// lobbySpace.get(new ActualField("turn"), new ActualField(id))

// Action: Draw from shuffled pile (pile 1, random space) - player
// lobbySpace.put("action","pickshuffled", userid)
// String card = (String) (lobbySpace.get(new ActualField("response"), new ActualField(userid), new FormalField(String.class), new FormalField(String.class)))[3] 
// handSpace.put(card)

// Action: Draw from last put card pile (pile 2 LIFO space)
// lobbySpace.put("action","pickdiscarded", userid)
// String card = (String) (lobbySpace.get(new ActualField("response"), new ActualField(userid), new FormalField(String.class), new FormalField(String.class)))[3] 
// handSpace.put(card)

// Action Put a card back to last put card pile (pile 2)
// String card = handSpace.get(requestedCard)
// lobbySpace.put("action", "putback",userid,card);
// lobbySpace.get("response", new ActualField(userid),message) // message could be OK from server to indicate that he can continue

// Action: Knock 
// lobbySpace.put("action", "knock", userid)
// lobby.get("response", new ActualField(userid), message)

// Action: Endturn
// lobbySpace.put("action", "endturn", userid)

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
