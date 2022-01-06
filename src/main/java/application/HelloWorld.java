package application;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class HelloWorld extends Application {

    public static void main(String[] args) {
        launch();
    }
    
    @Override
    public void start(Stage stage) {
    	stage.setTitle("31 Card Game");
    	
        Parent menu;
		try {
			menu = FXMLLoader.load(getClass().getResource("Game.fxml"));
	        Scene menuScene = new Scene(menu);
	        
	        stage.setScene(menuScene);
	        stage.show();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
	
}
/*
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("hellofx.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 400, 300));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
*/
