package application;

import java.util.HashMap;
import java.util.List;

import org.jspace.*;

public class Game implements Runnable {
	private RandomSpace shuffleDeck = new RandomSpace();
	private StackSpace discardDeck = new StackSpace();
	private Space lobbySpace;
	private HashMap<String, Integer> membersID;
	// TODO: Add a scoreboard hashmap/space
	// TODO: move has31 to game

	public Game(Space lobbySpace) throws InterruptedException {
		this.lobbySpace = lobbySpace;
		this.membersID = new HashMap<String, Integer>();

		new Thread(new checkInactivePlayers()).start();

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
			List<Object[]> tList = lobbySpace.queryAll(new ActualField("lobbymember"), new FormalField(String.class));

			for (Object[] member : tList) {
				membersID.put((String) member[1], 0);
			}

			for (String member : membersID.keySet()) {
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
			lobbySpace.put("token", "startofturn", (String) membersID.keySet().toArray()[i]);
			// lobbySpace.put(new ActualField("playerturn",id , username);
			String knockedPlayer = null;
			String lastPlayer = "";

			while (true) {
				// Success: (response, id, action, "success", "You have picked a card!", card);
				// Fail: (response, id, action, "error", "Illegal command", null)

				String id = (String) membersID.keySet().toArray()[i];

				if (!lastPlayer.equals(id)) { // Tells all other players whose turn it is
					tellPlayers("whosturn", id);
					lastPlayer = id;
				}

				Object[] t = lobbySpace.get(new ActualField("action"), new FormalField(String.class),
						new ActualField(id));

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
					if (calcPoints(hand) == 31) {
						endGame();
					}
					
					lobbySpace.put("token", "chooseknock", id); // hasDiscardedCards
					break;
				case "dontknock":
				case "knock":
					if (action.equals("knock")) {
						knockedPlayer = id;
						lobbySpace.put("response", id, action, "success", "You have knocked and ended your turn!");
						tellPlayers("whosknocked", id);
						// TODO: send a message to all players that a player with username has knocked
					} else {
						lobbySpace.put("response", id, action, "success", "You have ended your turn without knocking!");
					}

					i++;
					// Start over from player 0
					if (i >= membersID.size()) {
						i -= membersID.size();
					}

					String nextId = (String) membersID.keySet().toArray()[i];
					

					if (nextId.equals(knockedPlayer)) {
						endGame();
					} else {
						lobbySpace.put("token", "startofturn", nextId); // Next player's turn
					}
					break;
				case "31":
					lobbySpace.put("response", id, action, "success", "You have 31!");
					
					membersID.put(id, membersID.get(id) + 1);
					String[] memberList = (String[]) membersID.keySet().toArray();
					for (String member : membersID.keySet()) {
						lobbySpace.put("scoreboard", member, memberList, membersID.get(member));
					}
					
					tellPlayers("won", id);

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

	private void tellPlayers(String what, String id) throws InterruptedException {
		for (String member : membersID.keySet()) {
			lobbySpace.put("info", member, what, id);
		}
	}

	private void endGame() throws InterruptedException {
		tellPlayers("requestcards", "");

		Card[][] playerHands = new Card[membersID.size()][3];
		
		Object[] temp = membersID.keySet().toArray();
		String[] memberList = new String[temp.length];

		for (int i = 0; i < memberList2.length; i++) {
			memberList[i] = (String) memberList2[i];
		}
				
		String winningPlayer = null;
		int winningPoints = c0;
		
		for (int i = 0; i < memberList.length; i++) {
			Card[] hand = (Card[]) lobbySpace.get(new ActualField("playerhand"), new ActualField(memberList[i]),
					new FormalField(Card[].class))[2];
			int points = calcPoints(hand);
			if (winningPlayer == null || points > winningPoints) {
				winningPoints = points;
				winningPlayer = memberList[i];
			}
		}
		

		for (int i = 1; i < playerHands.length; i++) {
			int points = calcPoints(playerHands[i]);
			if (points > winningPoints) {
				winningPoints = points;
				winningPlayer = memberList[i];
			}
		}
		membersID.put(winningPlayer, membersID.get(winningPlayer) + 1);
		Integer[] scoreList = new Integer[memberList.length];
		
		for (int i = 0; i < scoreList.length; i++) {
			scoreList[i] = membersID.get(memberList[i]);
		}
		for (String member : membersID.keySet()) {
						lobbySpace.put("scoreboard", member, memberList, scoreList);
		}
		tellPlayers("won", winningPlayer);

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
				membersID.remove(id);
				tellPlayers("inactiveplayer", username);
				endGame();

				// TODO: Check that they have received and printed message

			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}

	}
}
