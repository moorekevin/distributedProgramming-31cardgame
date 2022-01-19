/*
 * TODO: Restart after winning game: Kevin, Aryan
 * -> Start game igen så folk kan nå at enter
 * TODO: Tell lobby someone has joined
 * TODO: GUI filer
 */

package application;

import java.util.List;

import org.jspace.*;

public class Game implements Runnable {
	private final int CARDS_IN_HAND = 3;
	private final int WINNING_SCORE = 31;
	private RandomSpace shuffleDeck;
	private StackSpace discardDeck;
	private Space lobbySpace;
	private Scoreboard membersScore;
	private Thread checkInactivePlayers;

	public Game(Space lobbySpace) throws InterruptedException {
		this.lobbySpace = lobbySpace;
		this.membersScore = new Scoreboard();

		checkInactivePlayers = new Thread(new checkInactivePlayers());
		checkInactivePlayers.start();
	}
	
	private void setGameUp() throws InterruptedException{
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
			System.out.println("Running game");
			setGameUp();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private void playingGame() throws InterruptedException {
		int i = 0;
		
		// Player 0 starts
		lobbySpace.put("token", "startofturn", (String) membersScore.keySet().toArray()[i]);
		
		String knockedPlayer = null;
		String lastPlayer = "";
				
		playingGameLoop: while (true) {
			// Success: (response, id, action, "success", "You have picked a card!", card);
			// Fail: (response, id, action, "error", "Illegal command", null)
			String id = (String) membersScore.keySet().toArray()[i];
			if (!lastPlayer.equals(id)) { // Tells all other players whose turn it is
				tellPlayers("whosturn", id);
				lastPlayer = id;
			}

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
				Card card = (Card) (action.equals("pickshuffled") ? shuffleDeck.get(new FormalField(Card.class))[0]
						: discardDeck.get(new FormalField(Card.class))[0]);

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
					tellPlayers("whosknocked", id);
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

				endGame(id);
				break;				
			default:
				lobbySpace.put("response", id, action, "error", "Illegal command", null);
				break;
			}
		}
	}

	private void tellPlayers(String what, String id) throws InterruptedException {
		for (String member : membersScore.keySet()) {
			lobbySpace.put("info", member, what, id);
		}
	}

	private void endRound(boolean roundFinished) throws InterruptedException {
		
		tellPlayers("requestcards", "");

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
			endGame(winningPlayer);
		} 
	}

	private void endGame(String winningPlayer) throws InterruptedException {
		
		Object[] lastSBReq = lobbySpace.getp(new ActualField("scoreboard"), new FormalField(Scoreboard.class)); // Getting the scoreboard from previous games
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
			tellPlayers("won", winningPlayer);
		}  else {
			tellPlayers("quit","");
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
				tellPlayers("inactiveplayer", username);
				endGame(null);
				
			} catch (InterruptedException e) {
				// Donothing
			}

		}

	}
}
