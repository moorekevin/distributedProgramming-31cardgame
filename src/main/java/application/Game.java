package application;

import java.util.List;

import org.jspace.*;

public class Game implements Runnable {
	RandomSpace shuffleDeck = new RandomSpace();
	StackSpace discardDeck = new StackSpace();
	Space lobbySpace;

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
			List<Object[]> membersID = lobbySpace.queryAll(new ActualField("lobbymember"),
					new FormalField(String.class));
			for (Object[] member : membersID) {
//				Card[] initialHand = new Card[3];
				String id = (String) member[1];
				for (int i = 0; i < 3; i++) {
					Card card = (Card) shuffleDeck.get(new FormalField(Card.class))[0];
					lobbySpace.put("dealingcards", id, card);
//					initialHand[i] = card;
				}
//				lobbySpace.put("dealingcards", id, initialHand);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
