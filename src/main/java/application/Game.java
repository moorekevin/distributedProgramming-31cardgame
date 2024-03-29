package application;

import java.util.ArrayList;
import java.util.List;

import org.jspace.*;

public class Game implements Runnable {
	public static final int CARDS_IN_HAND = 3;
	private final int WINNING_SCORE = 31;
	private RandomSpace shuffleDeck;
	private StackSpace discardDeck;
	private Space lobbySpace;
	private Scoreboard membersScore;
	private Thread checkInactivePlayers;
	private String hostID;

	public Game(Space lobbySpace, String hostID) throws InterruptedException {
		this.hostID = hostID;
		this.lobbySpace = lobbySpace;
		this.membersScore = new Scoreboard();
		checkInactivePlayers = new Thread(new checkInactivePlayers());
		checkInactivePlayers.start();
	}

	private void setGameUp() throws InterruptedException {
		shuffleDeck = new RandomSpace();
		discardDeck = new StackSpace();

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

		// Deal 3 cards to all joined members
		List<Object[]> tList = lobbySpace.queryAll(new ActualField("lobbymember"), new FormalField(String.class));

		for (Object[] member : tList) {
			if (!membersScore.containsKey((String) member[1])) {
				membersScore.put((String) member[1], 0);
			}
		}
		String winningPlayer = null;
		
		for (String member : membersScore.keySet()) {
			Card[] initialHand = new Card[CARDS_IN_HAND];
			String id = member;
			for (int i = 0; i < initialHand.length; i++) {
				Card c = (Card) shuffleDeck.get(new FormalField(Card.class))[0];
				initialHand[i] = c;
			}
			if (calcPoints(initialHand) == WINNING_SCORE) {
				winningPlayer = member;
			}
			lobbySpace.put("dealingcards", id, initialHand);
		}
		if (winningPlayer != null) {
			endRound(true);
		} else {
			playingGame();
		}
	}

	public void run() {
		try {
			setGameUp();
		} catch (InterruptedException e) {
		}
	}

	private void playingGame() throws InterruptedException {
		// Player 0 starts
		int i = 0;

		ArrayList<String> membersList = new ArrayList<String>();
		membersList.addAll(membersScore.keySet());

		for (String member : membersList) {
			if (member.equals(hostID))
				break;
			i++;
		}
		String knockedPlayer = null;
		String lastPlayer = "";

		playingGameLoop: while (true) {
			lobbySpace.put("token", "startofturn", membersList.get(i));
			String id = membersList.get(i);
			if (!lastPlayer.equals(id)) { // Tells all other players whose turn it is
				tellPlayers("whosturn", id, 0);
				lastPlayer = id;
			}
			lobbySpace.put("token", "startofturn", membersList.get(i));
			Object[] t = lobbySpace.get(new ActualField("action"), new FormalField(String.class), new ActualField(id));

			String action = (String) t[1];
			switch (action) {
			case "getdiscarded":
				Card topDiscard = (Card) discardDeck.query(new FormalField(Card.class))[0];
				lobbySpace.put("topdiscard", id, topDiscard);
				break;
				
			case "pickshuffled":
			case "pickdiscarded":
				// Picks from either shuffled or discarded deck
				Card card = null;
				if (action.equals("pickshuffled")) {
					Object[] tempCard = shuffleDeck.getp(new FormalField(Card.class));
					if (tempCard != null) {
						card = (Card) tempCard[0];
					} else {
						Card tempDisc = (Card) discardDeck.get(new FormalField(Card.class))[0];
						List<Object[]> cardList = discardDeck.getAll(new FormalField(Card.class));
						for (Object[] cardTemp : cardList) {
							shuffleDeck.put((Card) cardTemp[0]);
						}
						discardDeck.put(tempDisc);
						card = (Card) shuffleDeck.get(new FormalField(Card.class))[0];
					}
				} else
					card = (Card) discardDeck.get(new FormalField(Card.class))[0];

				lobbySpace.put("response", id, action, "success", "You have picked a card!", card);
				lobbySpace.put("token", "discardacard", id); // hasDiscardedCards
				break;
				
			case "discard":
				lobbySpace.put("response", action, id, "ok");
				t = lobbySpace.get(new ActualField("action"), new FormalField(String.class), new ActualField(id),
						new FormalField(Card.class), new FormalField(Card[].class));
				Card c = (Card) t[3];
				discardDeck.put(c);
				lobbySpace.put("response", id, action, "success", "You have discarded a card!");

				Card[] hand = (Card[]) t[4];
				if (calcPoints(hand) == WINNING_SCORE) {
					endRound(true);
					break playingGameLoop;
				}

				lobbySpace.put("token", "chooseknock", id); // hasDiscardedCards
				break;
				
			case "requestknock":
				lobbySpace.put("response", id, knockedPlayer != null);
				break;
			case "dontknock":
			case "knock":
				if (action.equals("knock")) {
					knockedPlayer = id;
					lobbySpace.put("response", id, action, "success", "You have knocked and ended your turn!");
					tellPlayers("whosknocked", id, 0);
				} else {
					lobbySpace.put("response", id, action, "success", "Your turn has ended!");
				}

				i++;
				// Start over from player 0
				if (i >= membersScore.size()) {
					i -= membersScore.size();
				}

				String nextId = (String) membersScore.keySet().toArray()[i];

				if (nextId.equals(knockedPlayer)) {
					endRound(true);
					break playingGameLoop;
				} else {
					lobbySpace.put("token", "startofturn", nextId); // Next player's turn
				}
				break;
				
			case "31":
				lobbySpace.put("response", id, action, "success", "You have 31!");
				
				endGame(id, WINNING_SCORE);
				break;
				
			default:
				lobbySpace.put("response", id, action, "error", "Illegal command", null);
				break;
			}
		}
	}

	private void tellPlayers(String what, String id, Integer score) throws InterruptedException {
		for (String member : membersScore.keySet()) {
			lobbySpace.put("info", member, what, id, score);
		}
	}

	private void endRound(boolean roundFinished) throws InterruptedException {
		tellPlayers("requestcards", "", 0);

		Object[] temp = membersScore.keySet().toArray();
		String[] memberList = new String[temp.length];

		for (int i = 0; i < temp.length; i++) {
			memberList[i] = (String) temp[i];
		}

		String winningPlayer = null;
		int winningPoints = 0;
		for (int i = 0; i < memberList.length; i++) {
			Card[] hand = (Card[]) lobbySpace.get(new ActualField("playerhand"), new ActualField(memberList[i]),
					new FormalField(Card[].class))[2];
			int points = calcPoints(hand);
			if (winningPlayer == null || points > winningPoints) {
				winningPoints = points;
				winningPlayer = memberList[i];
			}
		}
		if (roundFinished) {
			endGame(winningPlayer, winningPoints);
		}
	}

	private void endGame(String winningPlayer, Integer winningPoints) throws InterruptedException {
		// Getting the scoreboard from the previous games
		Object[] lastSBReq = lobbySpace.getp(new ActualField("scoreboard"), new FormalField(Scoreboard.class));

		if (lastSBReq != null) {
			Scoreboard lastScoreboard = (Scoreboard) lastSBReq[1];
			for (String member : membersScore.keySet()) {
				if (lastScoreboard.containsKey(member)) {
					membersScore.put(member, (int) lastScoreboard.get(member));
				}
			}
		}

		if (winningPlayer != null) {
			membersScore.put(winningPlayer, (Integer) (membersScore.get(winningPlayer) + 1));
			tellPlayers("won", winningPlayer, winningPoints);
		} else {
			tellPlayers("quit", "", 0);
		}

		lobbySpace.put("scoreboard", membersScore);
		if (checkInactivePlayers != null) {
			checkInactivePlayers.interrupt();
		}

	}

	private int calcPoints(Card[] hand) {
		int maxPoints = 0;

		for (Card.Suit suit : Card.Suit.values()) {
			int points = 0;
			for (Card card : hand) {
				if (card.getSuit() == suit)
					points += card.getPoints();
			}
			if (points > maxPoints)
				maxPoints = points;
		}
		return maxPoints;
	}

	class checkInactivePlayers implements Runnable {
		public void run() {
			try {
				Object[] inactivePlayer = lobbySpace.get(new ActualField("inactiveplayer"),
						new FormalField(String.class), new FormalField(String.class));
				String id = (String) inactivePlayer[1];
				String username = (String) inactivePlayer[2];
				membersScore.remove(id);
				tellPlayers("inactiveplayer", username, 0);
				endGame(null, null);
			} catch (InterruptedException e) {
			}
		}
	}
}
