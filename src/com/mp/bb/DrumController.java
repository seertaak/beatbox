package com.mp.bb;

import com.mp.bb.BeatAnalyzer.AnalyzeInfo;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.StackPane;

public class DrumController {

    private BeatBox beatbox;
    private AudioEngine audio;
    private AnalyzeInfo ai;
    @FXML
    private MenuBar menuBar;
    @FXML
    private StackPane topPane;
    @FXML
    private StackPane bottomPane;
    
    public void setAudio(AudioEngine audio) {
        this.audio = audio;
    }

    public void setAnalyzeInfo(AnalyzeInfo ai) {
        this.ai = ai;
    }
    
    private void init(BeatBox app, AudioEngine audio, AnalyzeInfo ai) {
        this.beatbox = app;
        this.audio = audio;
        this.ai = ai;
    }

    @FXML
    protected void onMenuQuit(ActionEvent event) {
        try {
            beatbox.stop();
        } catch (Exception ex) {
            Logger.getLogger(DrumController.class.getName())
                  .log(Level.SEVERE, null, ex);
        }
    }

    public static Parent create(BeatBox beatbox, AudioEngine audio,
            AnalyzeInfo ai) 
    {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader();
                    
            Parent r = fxmlLoader.load( 
                    DrumController.class.getResource("drumview.fxml"),
                    ResourceBundle.getBundle("com.mp.bb.drumview", Locale.US));
            DrumController dc = (DrumController) fxmlLoader.getController();       
            dc.init(beatbox, audio, ai);
            
            return r;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setBeatBox(BeatBox beatbox) {
        this.beatbox = beatbox;
    }
}
