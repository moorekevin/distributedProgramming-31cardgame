package application;

import java.util.ArrayList;
import java.util.List;

import org.jspace.*;

public class Game implements Runnable {
	private RandomSpace shuffleDeck = new RandomSpace();
	private StackSpace discardDeck = new StackSpace();
	private Space lobbySpace;
	private List<String> membersID;
	// TODO: Add a scoreboard hashmap/space

	public Game(Space lobbySpace) throws InterruptedException {
		this.lobbySpace = lobbySpace;
		this.membersID = new ArrayList<String>();

		// Add 52 cards to shuffleDeck
		for (Card.Num num : Card.Num.values()) {
			for (Card.Suit suit : Card.Suit.values()) {
				Card c = new Card(num, suit);
				shuffleDeck.put(c);
			}
		}
		// Add one card to discard pile
		Card card = (Card) shuffleDeck.get(new FormalField(Card.class))[0];
		discardDeck.put(card); // TODO: show discarded pile to player
	}

	public void run() {
		try {
			// Deal 3 cards to all joined members
			List<Object[]> tList = lobbySpace.queryAll(new ActualField("lobbymember"), new FormalField(String.class));
			for (Object[] member : tList) {
				membersID.add((String) member[1]);
			}

			for (String member : membersID) {
				Card[] initialHand = new Card[3];
				String id = member;
				for (int i = 0; i < 3; i++) {
					Card card = (Card) shuffleDeck.get(new FormalField(Card.class))[0];
					initialHand[i] = card;
				}
				lobbySpace.put("dealingcards", id, initialHand);
			}

			int i = 0;
			// Player 0 starts
			lobbySpace.put("token", "startofturn", (String) membersID.get(i));
			// lobbySpace.put(new ActualField("playerturn",id , username);
			String knockedPlayer = null;
			String lastPlayer = "";

			while (true) {
				// Success: (response, id, action, "success", "You have picked a card!", card);
				// Fail: (response, id, action, "error", "Illegal command", null)

				String id = membersID.get(i);

				if (!lastPlayer.equals(id)) { // Tells all other players whose turn it is
					tellTurns("whosturn", membersID.get(i));
					lastPlayer = id;
				}

				Object[] t = lobbySpace.get(new ActualField("action"), new FormalField(String.class),
						new ActualField(id)); // <--

				String action = (String) t[1];

				switch (action) {
				case "getdiscarded":
					Card topDiscard = (Card) discardDeck.query(new FormalField(Card.class))[0];
					lobbySpace.put("topdiscard", id, topDiscard);
					break;
				case "pickshuffled":
				case "pickdiscarded":
					// Picks from either shuffled or discarded deck
					Card card = (Card) (action.equals("pickshuffled") ? shuffleDeck.get(new FormalField(Card.class))[0]
							: discardDeck.get(new FormalField(Card.class))[0]);

					lobbySpace.put("response", id, action, "success", "You have picked a card!", card);
					lobbySpace.put("token", "discardacard", id); // hasDiscardedCards
					break;
				case "discard":
					lobbySpace.put("response", action, id, "ok");
					t = lobbySpace.get(new ActualField("action"), new FormalField(String.class), new ActualField(id),
							new FormalField(Card.class));
					Card c = (Card) t[3];
					discardDeck.put(c);
					lobbySpace.put("response", id, action, "success", "You have discarded a card!");
					lobbySpace.put("token", "chooseknock", id); // hasDiscardedCards
					break;
				case "dontknock":
				case "knock":
					if (action.equals("knock")) {
						knockedPlayer = id;
						lobbySpace.put("response", id, action, "success", "You have knocked and ended your turn!");
						tellTurns("whosknocked",id);
						// TODO: send a message to all players that a player with username has knocked
					} else {
						lobbySpace.put("response", id, action, "success", "You have ended your turn without knocking!");
					}

					i++;
					// Start over from player 0
					if (i >= membersID.size()) {
						i -= membersID.size();
					}

					String nextId = (String) membersID.get(i);

					if (nextId.equals(knockedPlayer)) {
						endGame();
					} else {
						lobbySpace.put("token", "startofturn", nextId); // Next player's turn
					}
					break;
				case "31":
					endGame();
					break;
				default:
					lobbySpace.put("response", id, action, "error", "Illegal command", null);
					break;
				}

			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void tellTurns(String what, String id) throws InterruptedException {
		for (String member : membersID) {
			lobbySpace.put("info", member, what, id);
		}
	}

	private void endGame() throws InterruptedException {
		lobbySpace.put("generalmessage", "Game has ended");
		for (String member : membersID) {
			//lobbySpace.put("")
		}
	}
}
