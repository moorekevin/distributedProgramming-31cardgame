package application;

public class Card {
	private Num num;
	private Suit suit;
	private int points;

	public enum Num {
		ACE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING;
	}

	public enum Suit {
		CLUBS("♣"), DIAMONDS("♦"), HEARTS("♥"), SPADES("♠");

		private String custom;

		private Suit(String custom) {
			this.custom = custom;
		}

		@Override
		public String toString() {
			return custom;
		}
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

	public String getSuit() {
		return "" + suit;
	}

	@Override
	public String toString() {
		// A,2,3,...,Q,K
		String numChar = num.ordinal() + 1 == 13 ? "K" : num.ordinal() + 1 == 12 ? "Q" : num.ordinal() + 1 == 11 ? "J" : num.ordinal() + 1 == 1 ? "A" : "" + (num.ordinal() + 1);
		
		return suit + numChar;
	}
}
