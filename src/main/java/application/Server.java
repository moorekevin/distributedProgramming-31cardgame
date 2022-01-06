package application;

import java.util.ArrayList;
import java.util.UUID;

import org.jspace.*;

public class Server {
	
	public static ArrayList<String> users;

	public static void main(String[] args) {
		users = new ArrayList<String>();
		SpaceRepository rep = new SpaceRepository();
		SequentialSpace chat = new SequentialSpace();
		rep.add("chat", chat);

		final String gateUri = "tcp://25.62.120.1:9002/?keep";
		rep.addGate(gateUri);

		new Thread(new printChat(chat)).start();
		
		System.out.println("Chat server running");
		
		while(true) {
			try {
				String user = ((String) (chat.get(new ActualField("user connected"), new FormalField(String.class)))[1]);
				String uniqueID = UUID.randomUUID().toString();
				users.add(uniqueID);
				chat.put("uniqueid", user, uniqueID);
				System.out.println(user + " has connected");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
				
	}
	
	static class printChat implements Runnable {
		Space chat;
		
		public printChat(Space chat) {
			this.chat = chat;
		}

		public void run() {
			while(true) {
				try {
					Object[] t = chat.get(new ActualField("message"),new FormalField(String.class), new FormalField(String.class));
					for (String user : Server.users) {
						chat.put(user, t[1], t[2]);
					}
					System.out.println(t[1] +  ": " + t[2]);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		}
	}
	


}
