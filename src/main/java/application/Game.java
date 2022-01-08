package application;

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
		/*try {
			
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}*/
	}
}
