// TODO: Add extra spaces between text in terminal

package application;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;
import org.jspace.SequentialSpace;

public class Player {
	private final int START_GATE = 9002;
	private final String START_NAME = "startSpace";
	private String username;
	private String id;
	private String lobbyName;
	private Card cardInUse = null;
	private RemoteSpace startSpace;
	private RemoteSpace lobbySpace;
	private SequentialSpace handSpace = new SequentialSpace();
	private SequentialSpace messageTokens = new SequentialSpace();
	private Thread play;
	private Thread createAndPlay;
	private Thread game;
	
	private boolean hasJoinedLobby;


	public Player() {
		final String uri = "tcp://localhost:" + START_GATE + "/" + START_NAME + "?keep";
		try {
			username = getInput("Enter your username");
			hasJoinedLobby = false;
			startSpace = new RemoteSpace(uri);

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
		player.initiateStartSequence();
	}
	
	/// Game initialization and create/join methods ///
	
	private void initiateStartSequence() {
		try {
			readStartOption();

			joinLobby();
			
			createAndPlay = new Thread(new CreateAndPlayGame());
			createAndPlay.start();
			/*if (isHost()) {
				createGame();
			} 
			
			startPlaying();
			*/
			
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	private void readStartOption() throws InterruptedException, IOException {
		String command = getInput("Do you want to (h)ost or (j)oin a lobby?").toLowerCase();
		while (!(command.equals("h") || command.equals("j"))) {
			printError(command);
			command = getInput("Try again: Do you want to (h)ost or (j)oin a lobby?").toLowerCase();
		}

		List<Object[]> lobbyList = startSpace.queryAll(new ActualField("lobbyname"), new FormalField(String.class),
				new FormalField(Integer.class));
		if (command.equals("j") && lobbyList.size() == 0) {
			System.out.println("No available lobbies. Try hosting a server instead!");
			readStartOption();
		} else {
			if (command.equals("j")) {
				System.out.println("List of available lobbies:");
				for (int i = 0; i < lobbyList.size(); i++) {
					System.out.println(" " + (i + 1) + ") " + ((String) lobbyList.get(i)[1]) + " ("
							+ (int) lobbyList.get(i)[2] + "/" + Server.LOBBY_CAPACITY + ")");
				}
				System.out.println();
			}
			lobbyName = getInput("What is the name of the lobby?").toLowerCase().replaceAll("\\s+", "_");

			startSpace.put("lobbyrequest", id, command, lobbyName);

			Object[] t = startSpace.query(new ActualField("lobbyinfo"), new FormalField(String.class),
					new ActualField(id), new FormalField(String.class));
			if (((String) t[1]).equals("error")) {
				System.out.println((String) t[3]);
				startSpace.get(new ActualField("lobbyinfo"), new FormalField(String.class), new ActualField(id),
						new FormalField(String.class));
				readStartOption();
			}
		}
	}
	
	private void joinLobby() throws InterruptedException, IOException {
		try {
			String lobbyURI = (String) (startSpace.get(new ActualField("lobbyinfo"), new ActualField("join"),
					new ActualField(id), new FormalField(String.class)))[3];
			lobbySpace = new RemoteSpace(lobbyURI);

			lobbySpace.put("lobbymember", id);
			hasJoinedLobby = true;
			new Thread(new getMessagesFromLobby()).start();
			new Thread(new checkIfHostExit()).start();
			
			System.out.println("Joined lobby. Waiting for game to start.");
			
		} catch (UnknownHostException e) {

			printError("Cannot find lobby, check URI");
		}
	}
	
	
	private boolean isHost() throws InterruptedException {
		Object[] isHost = lobbySpace.queryp(new ActualField("host"), new ActualField(id));
		return (isHost != null);
	}
	
	/// Playing methods ///
	
	private void startPlaying() throws InterruptedException {
		if (play != null) {
			play.interrupt();
			try {
				lobbySpace.put("readytorestart");
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		getDealtCards();
		play = new Thread(new Play());
		play.start();
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
	
	private void discard(List<Card> allCards) throws InterruptedException {
		getToken("discardacard");
		displayHand(getHand());
		String command = getInput("Which card would you like to discard (1),(2),(3),(4)?");
		int cardNumber;
		while (true) {
			try {
				cardNumber = Integer.parseInt(command) - 1;
				break;
			} catch (NumberFormatException e) {
				printError(command);
				command = getInput("Try again: Which card would you like to discard (1),(2),(3),(4)?");
				continue;
			}
		}

		while (cardNumber < 0 || cardNumber > 3) {
			printError("" + (cardNumber + 1));
			cardNumber = Integer.parseInt(getInput("Try again: Which card would you like to discard (1),(2),(3),(4)?"))
					- 1;
		}

		Card discardThis = allCards.get(cardNumber);
		handSpace.get(new ActualField(discardThis));
		cardInUse = allCards.get(cardNumber);
		doAnAction("discard");
		cardInUse = null;
	}
	
	private void knockOption() throws InterruptedException {
		lobbySpace.put("action", "requestknock", id);
		Boolean hasKnocked = (Boolean) lobbySpace.get(new ActualField("response"), new ActualField(id), new FormalField(Boolean.class))[2];
		
		getToken("chooseknock");
		if (!hasKnocked) {
			String instruction = "Do you wish to (k)nock or (d)on't knock?";
			getTwoCommands("k", "d", instruction, "knock", "dontknock");
		} else {
			doAnAction("dontknock");
		}
		
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
			ArrayList<Card> handArrayList = getHand();
			Card[] handArray = new Card[handArrayList.size()];
			for (int i = 0; i < handArrayList.size(); i++) {
				handArray[i] = handArrayList.get(i);
			}
			
			lobbySpace.put("action", action, id, cardInUse, handArray);

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
			return;
		}

		if (((String) response[3]).equals("error")) {
			printError((String) response[4]);
		} else {
			System.out.println((String) response[4]);
		}

	}

	/// Prints ///

	private void displayHand(List<Card> allCards) {
		System.out.println("You have the following cards: ");
		int i = 1;
		for (Card card : allCards) {
			System.out.print("(" + i + "): " + card.toString() + "    ");
			i++;
		}
		System.out.println();
	}
	
	private void printError(String error) {
		System.out.println("ERROR: \"" + error + "\"\n");
	}

	
	/// Getters ///
	@SuppressWarnings("resource") 
	private String getInput(String question) {
		Scanner reader = new Scanner(System.in);
		System.out.print(question + "\n> ");
		return reader.nextLine();
	}

	private void getDealtCards() throws InterruptedException {
		new Thread(new gameRestarter()).start();
		handSpace.getAll(new FormalField(Card.class));
		// Get dealt cards
		Card[] initialHand = (Card[]) (lobbySpace.get(new ActualField("dealingcards"), new ActualField(id),
				new FormalField(Card[].class)))[2];
		
		for (Card thisCard : initialHand) {
			handSpace.put(thisCard);
		}
	}
	
	
	private ArrayList<Card> getHand() {
		List<Object[]> allCards = handSpace.queryAll(new FormalField(Card.class));
		ArrayList<Card> listToReturn = new ArrayList<Card>();
		for (Object[] obj : allCards) {
			Card card = ((Card) obj[0]);
			listToReturn.add(card);
		}
		return listToReturn;
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
	
	private void getToken(String action) throws InterruptedException {
		lobbySpace.get(new ActualField("token"), new ActualField(action), new ActualField(id));
	}

	
	public Player getPlayer() {
		return this;
	}
	
	private void createGame() throws InterruptedException{
		
		while (true) {
			String command = getInput("Do you want to (s)tart the game").toLowerCase();
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
		if (game != null) {
			game.interrupt();
			// remove all old tokens
			/*List<Object[]> tokens =*/ lobbySpace.getAll(new ActualField("token"), new FormalField(String.class), new FormalField(String.class));
			// System.out.println("ALL TOKENS:");
			// for (Object[] token : tokens) {
			//	System.out.println((String) token[1] + " - " + (String) token[2]);
			// }
		}
		game = new Thread(new Game(lobbySpace,id));
		game.start();
		
	}

	/// Threads ///
	class CreateAndPlayGame implements Runnable {
		@Override
		public void run() {
			try {
				Player player = getPlayer();
				if (player.isHost()) {
					if (play != null) {
						play.interrupt();
						// Check if all players are ready to play
						Scoreboard sb = (Scoreboard) lobbySpace.query(new ActualField("scoreboard"), new FormalField(Scoreboard.class))[1];
						for (int i = 0; i < sb.keySet().size()-1; i++) {
							lobbySpace.get(new ActualField("readytorestart"));
						}
					}
					
					player.createGame();
				}
				player.startPlaying();
			} catch (InterruptedException e) {
				// Do nothing
				
			} 
		}
		
	}
	
	
	/* P1 is host
	P1 CreateAndPlayGame() -> p1.createGame(); -> p1.startPlaying();
	P2 CreateAndPlayGame() -> p2.startPlaying 
	
	p1.createGame() -> Close other game thread -> new Game Thread -> Put token startTurn
	
	pX.startPlaying() -> Close old Play Thread -> new Play Thread -> Get token startTurn
	
	P3
	
	P1  
	P2
	
	P3
	*/
	
	class Play implements Runnable {
		public void run() {
			try {
				while (true) {
					getToken("startofturn");
					 
					messageTokens.get(new ActualField("printedturn"));
					displayHand(getHand()); // 3
					
					draw(); // +1
					discard(getHand()); // 4
					
					
					displayHand(getHand());
	
					knockOption();
					lobbySpace.getAll(new ActualField("token"), new ActualField("startofturn"), new ActualField(id));
				}
			} catch (InterruptedException e) {
					// Do nothing
			}
		}
	}
	
	class gameRestarter implements Runnable {
		@Override
		public void run() {
			try {
				if (isHost()) {
					lobbySpace.get(new ActualField("restartgame"));
					Scoreboard sb = (Scoreboard) lobbySpace.query(new ActualField("scoreboard"), new FormalField(Scoreboard.class))[1];
					for (String member : sb.keySet()) {
						lobbySpace.get(new ActualField("printedscores"), new ActualField(member));
						lobbySpace.put("restartgame", member);
					}
					lobbySpace.get(new ActualField("lobbystatus"), new ActualField("private"));
					lobbySpace.put("lobbystatus", "public");
				}
				lobbySpace.get(new ActualField("restartgame"), new ActualField(id));
				
				
				
				createAndPlay.interrupt();
				createAndPlay = new Thread(new CreateAndPlayGame());
				createAndPlay.start();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	class getMessagesFromLobby implements Runnable {
		public void run() {
			while (hasJoinedLobby) {
				try {
					Object[] req = lobbySpace.get(new ActualField("info"), new ActualField(id),
							new FormalField(String.class), new FormalField(String.class));
					String playerID = (String) req[3];
					switch ((String) req[2]) {
					case "whosturn":
						if (playerID.equals(id)) {
							System.out.println("It is now your turn");
						} else {
							lobbySpace.put("serverrequest", "username", id, playerID);
							String username = (String) (lobbySpace.get(new ActualField("serverresponse"),
									new ActualField("username"), new ActualField(id),
									new FormalField(String.class)))[3];
							System.out.println("It is now player " + username + "'s turn");
						}
						messageTokens.put("printedturn");
						break;

					case "whosknocked":
						if (!playerID.equals(id)) {
							lobbySpace.put("serverrequest", "username", id, playerID);
							String username = (String) (lobbySpace.get(new ActualField("serverresponse"),
									new ActualField("username"), new ActualField(id),
									new FormalField(String.class)))[3];
							System.out.println("Be aware! Player " + username +
									" has knocked. This is the last round!");
						}
						break;

					case "joinedplayer":
						System.out.print("\nPlayer " + playerID + " has joined the lobby\n> ");
						break;
					case "inactiveplayer":
						// PlayerID is not ID but a username here
						System.out.println("Player " + playerID + " has left the game. Game restarting");
						break;
					case "won":
						play.interrupt();
						if (playerID.equals(id)) {
							System.out.println("Congratulations! You have won this round!");
						} else {
							lobbySpace.put("serverrequest", "username", id, playerID);
							String username = (String) (lobbySpace.get(new ActualField("serverresponse"),
									new ActualField("username"), new ActualField(id),
									new FormalField(String.class)))[3];
							System.out.println("Player " + username + " won this round!");
						}
						showSBandRestart();
						break;
					case "quit":
						play.interrupt();
						showSBandRestart();
						break;
					case "requestcards":
						List<Object[]> cardList = handSpace.queryAll(new FormalField(Card.class));
						Card[] cards = new Card[Game.CARDS_IN_HAND];
						for (int i = 0; i < cards.length; i++) {
							cards[i] = (Card) cardList.get(i)[0];
						}
						
						lobbySpace.put("playerhand", id, cards);
						
						break;
					default:
						// Error Stuff
						break;
					}

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		private void showSBandRestart() throws InterruptedException {
			//  (scoreboard, id, memberList, score)
			Object[] sbReq = lobbySpace.query(new ActualField("scoreboard"), new FormalField(Scoreboard.class));
			Scoreboard scoreboard = (Scoreboard) sbReq[1];
			System.out.println("\nScoreboard");
			for (String member : scoreboard.keySet()){
						lobbySpace.put("serverrequest", "username", id, member);
						
						String username = (String) (lobbySpace.get(new ActualField("serverresponse"), new ActualField("username"), new ActualField(id),
								new FormalField(String.class)))[3];
						System.out.format("  %-16s %02d%n", '"' + username + "\":", scoreboard.get(member));
			}
			if (isHost()) {
				lobbySpace.put("restartgame");
			}
			lobbySpace.put("printedscores", id);
		}
	}

	class getPingsFromServer implements Runnable {
		public void run() {
			while (true) {
				try {
					startSpace.get(new ActualField("userrequest"),new ActualField("ping"), new ActualField(id));
					startSpace.put("userresponse","ping",id);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	class checkIfHostExit implements Runnable {
		
		public checkIfHostExit() {
		}
		
		public void run() {
			try {
				lobbySpace.query(new ActualField("exitlobby"));
				System.out.println("Host has left, going back to lobby menu");
				hasJoinedLobby = false;
				initiateStartSequence();
			} catch (InterruptedException e) {
				
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