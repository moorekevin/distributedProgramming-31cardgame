package application;

import java.io.IOException;
import java.util.Scanner;

import org.jspace.*;

public class Client {
	static String id;

	public static void main(String[] args) {
		final String uri = "tcp://25.62.120.1:9002/chat?keep";
		Scanner reader = new Scanner(System.in);
		try {
			System.out.println("Enter your username: ");
			String username = reader.nextLine();
			
			RemoteSpace chat = new RemoteSpace(uri);
			
			chat.put("user connected", username);
			
			id = (String) (chat.get(new ActualField("uniqueid"),new ActualField(username), new FormalField(String.class)))[2];
						
			System.out.println("Connected to chat - start chatting!");
			
			new Thread(new printMessages(chat)).start();
			
			while (true) {
				String message = reader.nextLine();
				chat.put("message",username, message);
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	static class printMessages implements Runnable {
		Space chat;
		
		public printMessages(Space chat) {
			this.chat = chat;
		}

		public void run() {
			while(true) {
				Object[] t;
				try {
					t = chat.get(new ActualField(id), new FormalField(String.class), new FormalField(String.class));
					System.out.println(t[1] + ": " + t[2]);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
	}

}
