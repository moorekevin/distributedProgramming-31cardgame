package application;

import java.util.List;

import org.jspace.*;

public class Game implements Runnable {
	private RandomSpace shuffleDeck = new RandomSpace();
	private StackSpace discardDeck = new StackSpace();
	private Space lobbySpace;
	private List<Object[]> membersID;
	// TODO: Add a scoreboard hashmap/space
	
	public Game(Space lobbySpace) throws InterruptedException {
		this.lobbySpace = lobbySpace;

		// Add 52 cards to shuffleDeck
		for (Card.Num num : Card.Num.values()) {
			for (Card.Suit suit : Card.Suit.values()) {
				Card c = new Card(num, suit);
				shuffleDeck.put(c);
			}
		}
		// Add one card to discard pile
		Card card = (Card) shuffleDeck.get(new FormalField(Card.class))[0];
		discardDeck.put(card);
	}

	public void run() {
		try {
			// Deal 3 cards to all joined members
			membersID = lobbySpace.queryAll(new ActualField("lobbymember"), new FormalField(String.class));

			for (Object[] member : membersID) {
				Card[] initialHand = new Card[3];
				String id = (String) member[1];
				for (int i = 0; i < 3; i++) {
					Card card = (Card) shuffleDeck.get(new FormalField(Card.class))[0];
					initialHand[i] = card;
				}
				lobbySpace.put("dealingcards", id, initialHand);
			}

			int i = 0;
			// Player 0 starts
			lobbySpace.put("token", "startOfTurn", (String) membersID.get(i)[1]);
			
			String knockedPlayer = null;

			while (true) {
				// Success: (response, id, action, "success", "You have picked a card!", card);
				// Fail: (response, id, action, "error", "Illegal command", null)

				String id = (String) membersID.get(i)[1];

				Object[] t = lobbySpace.get(new ActualField("action"), new FormalField(String.class),
						new ActualField((String) membersID.get(0)[1]), new FormalField(Card.class));

				String action = (String) t[1];

				if (action.equals("pickshuffled") || action.equals("pickdiscarded")) {
					// Picks from either shuffled or discarded deck
					Card card = (Card) (action.equals("pickshuffled") ? shuffleDeck.get(new FormalField(Card.class))[0]
							: discardDeck.get(new FormalField(Card.class))[0]);

					lobbySpace.put("response", id, action, "success", "You have picked a card!", card);
					lobbySpace.put("token", "discardacard", id); // hasDiscardedCards
				} else if (action.equals("discard")) {
					Card card = (Card) t[3];
					discardDeck.put(card);
					lobbySpace.put("response", id, action, "success", "You have discarded a card!", card);
					lobbySpace.put("token", "chooseknock", id); // hasDiscardedCards
				} else if (action.equals("dontknock") || action.equals("knock")) {
					if (action.equals("knock")) {
						knockedPlayer = id;
						// TODO: send a message to all players that a player with username has knocked
					}

					i++;
					// Start over from player 0
					if (i >= membersID.size()) {
						i -= membersID.size();
					}

					String nextId = (String) membersID.get(i)[1];

					if (nextId.equals(knockedPlayer)) {
						endGame();
					} else {
						lobbySpace.put("token", "startofturn", nextId); // Next player's turn
					}

				} else {
					lobbySpace.put("response", id, action, "error", "Illegal command", null);
				}

			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void endGame() {
		System.out.println("End of game");
	}
}
