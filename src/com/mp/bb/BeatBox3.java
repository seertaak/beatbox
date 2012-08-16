/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 *
 * @author Martin Percossi
 */
public class BeatBox3 extends Application {
    
    public static void main(String[] args) {
        Application.launch(BeatBox3.class, args);
    }
    
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = DrumController.create(null, null, null);
        
        stage.setScene(new Scene(root));
        stage.show();
        
        
    }
}
