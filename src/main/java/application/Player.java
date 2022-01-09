package application;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.jspace.*;

public class Player {
	private String id;
	private final int START_GATE = 9002;
	private final String START_NAME = "startSpace";
	private RemoteSpace startSpace;
	private RemoteSpace lobbySpace;
	private SequentialSpace handSpace = new SequentialSpace();

	public Player() {
		final String uri = "tcp://localhost:" + START_GATE + "/" + START_NAME + "?keep";
		try {
			String username = getInput("Enter your username");

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
			new Thread(new getMessagesFromServer()).start();

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

	// TODO: when asking h/j, handle miss-input
	private void readStartOption() throws InterruptedException, IOException {
		String command;
		do {
			command = getInput("Do you want to (h)ost or (j)oin a lobby?").toLowerCase();
		} while (!command.equals("h") && !command.equals("j"));

		String lobbyName = getInput("What is the name of the lobby?\n Spaces will get replaced with \"_\"")
				.toLowerCase().replaceAll("\\s+", "_");

		startSpace.put("lobbyrequest", id, command, lobbyName);

		Object[] t = startSpace.query(new ActualField("lobbyinfo"), new FormalField(String.class), new ActualField(id),
				new FormalField(String.class));
		if (printError((String) t[1], (String) t[3])) {
			startSpace.get(new ActualField("lobbyinfo"), new FormalField(String.class), new ActualField(id),
					new FormalField(String.class));
			readStartOption();
		}
	}

	private boolean printError(String checkError, String error) {
		if (checkError.equals("error")) {
			System.out.println("ERROR: " + error + "\n");
			return true;
		} else {
			return false;
		}
	}

	@SuppressWarnings("resource")
	private String getInput(String question) {
		Scanner reader = new Scanner(System.in);

		System.out.print(question + "\n> ");
		String input = reader.nextLine();
		return input;
	}

	private void joinLobby() throws InterruptedException, IOException {
		try {
			String lobbyURI = (String) (startSpace.get(new ActualField("lobbyinfo"), new ActualField("join"),
					new ActualField(id), new FormalField(String.class)))[3];
			lobbySpace = new RemoteSpace(lobbyURI);
			// lobbySpace.queryAll
			lobbySpace.put("lobbymember", id);

			System.out.println("Joined lobby. Waiting for game to start\n");
		} catch (UnknownHostException e) {
			// Should not happen
			printError("error", "Cannot find lobby, check URI");
		}
	}

	private boolean isHost() throws InterruptedException {
		Object[] isHost = lobbySpace.queryp(new ActualField("host"), new ActualField(id));
		return (isHost != null);
	}

	private void createGame() throws InterruptedException {

		while (true) {
			String command = getInput("Do you want to (s)tart the game").toLowerCase(); // TODO: or (e)xit lobby?
			if (command.equals("s")) {
				lobbySpace.get(new ActualField("lobbystatus"), new ActualField("public"));
				lobbySpace.put("lobbystatus", "private");
				break;
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
			System.out.println("Your turn");
			displayHand(getHand()); // 3
			draw(); // +1
			discard(getHand()); // 4

			if (!has31(getHand())) {
				knockOption();
				doAnAction("endturn", null);
			} else {
				doAnAction("31", null);
			}

		}

	}

	private List<Object[]> getHand() {
		return handSpace.queryAll(new FormalField(Card.class));
	}

	private void displayHand(List<Object[]> allCards) {
		System.out.println("You have the following cards: ");
		int i = 0;
		for (Object[] obj : allCards) {
			Card card = ((Card) obj[0]);
			System.out.print("(" + i + "): " + card.toString() + " | ");
			i++;
		}
		System.out.println();
	}

	private void getToken(String action) throws InterruptedException {
		lobbySpace.get(new ActualField("token"), new ActualField(action), new ActualField(id));
	}

	private void discard(List<Object[]> allCards) throws InterruptedException {
		getToken("discardacard");

		int cardNumber = Integer.parseInt(getInput("Which card would you like to discard (1),(2),(3),(4)?")) - 1;
		while (cardNumber < 0 || cardNumber > 3) {
			printUnknownCommand("" + cardNumber);
			cardNumber = Integer.parseInt(getInput("Try again: Which card would you like to discard (1),(2),(3),(4)?"))
					- 1;
		}
	
		Card discardThis = (Card) allCards.get(cardNumber)[0];
		handSpace.get(new ActualField(discardThis));
		doAnAction("discard", discardThis);
	}

	private void printUnknownCommand(String command) {
		printError("error", "Unknown command \"" + command + "\"");
	}

	private void knockOption() throws InterruptedException {
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

	private void draw() throws InterruptedException {
		Object drawnAction = null;
		String instruction = "Draw from (s)huffled pile or (d)iscarded pile";
		drawnAction = getTwoCommands("s", "d", instruction, "pickshuffled", "pickdiscarded");
		handSpace.put((Card) drawnAction);
	}

	private Object getTwoCommands(String command1, String command2, String instruction, String action1, String action2)
			throws InterruptedException {
		String command = getInput(instruction).toLowerCase();
		while (!(command.equals(command1) || command.equals(command2))) {
			printUnknownCommand(command);
			command = getInput("Try again: " + instruction).toLowerCase();
		}
		Object respondedObject = null;
		if (command.equals(command1))
			respondedObject = doAnAction(action1, null);
		if (command.equals(command2))
			respondedObject = doAnAction(action2, null);
		return respondedObject;
	}

	// Success: (response, id, action, "success", "You have picked a card!", card);
	// Fail: (response, id, action, "error", "Illegal command", null)

	private Object doAnAction(String action, Object optionalActionObject) throws InterruptedException {
		lobbySpace.put("action", action, id, optionalActionObject);

		Object[] response = (lobbySpace.get(new ActualField("response"), new ActualField(id), new ActualField(action),
				new FormalField(String.class), new FormalField(String.class), new FormalField(Object.class)));
		String succeded = (String) response[3];
		String gameMessage = (String) response[4];
		if (printError(succeded, gameMessage)) {
			// TODO: Needs to do something Error occured

		} else {
			System.out.println(gameMessage);
		}
		return response[5]; // TODO: Might return null
	}

	class getMessagesFromServer implements Runnable {
		// TODO: Might not be needed anymore?
		public void run() {
			while (true) {
				try {
					String message = (String) (startSpace.get(new ActualField("message"), new ActualField(id),
							new FormalField(String.class)))[2];
					System.out.println(message);
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