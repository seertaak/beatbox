package com.mp.bb.test;

import com.mp.bb.SampleExplorer;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class SampleExplorerTest extends Application {

	@Override
	public void start(Stage primaryStage) throws Exception {
		SampleExplorer se = new SampleExplorer();
		VBox root = new VBox();
		Scene scene = new Scene(root, 800, 600, new Color(.2, .2, .2, 1));
		scene.getStylesheets().add(SampleExplorer.SYTLESHEET);
		primaryStage.setScene(scene);
		primaryStage.show();
		new FileChooser().showOpenDialog(primaryStage.getOwner());
	}
	
	public static void main(String...args) {
		launch(args);
	}

}
