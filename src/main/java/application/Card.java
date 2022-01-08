package application;

public class Card {
	private Num num;
	private Suit suit;
	private int points;

	public enum Num {
		ACE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING;
	}

	public enum Suit {
		CLUBS, DIAMONDS, HEARTS, SPADES;
	}

	Card(Num num, Suit suit) {
		this.num = num;
		this.suit = suit;
		if (num.equals(Num.ACE))
			points = 11;
		else if (num.compareTo(Num.TEN) > 0)
			points = 10;
		else
			points = num.ordinal() + 1;
	}

	public int getPoints() {
		return points;
	}

	@Override
	public String toString() {
		return num + " OF " + suit;
	}
}
