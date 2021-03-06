package com.mp.bb;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.management.RuntimeErrorException;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.math.linear.RealMatrix;

import com.mp.bb.BeatAnalyzer.AnalyzeInfo;
import com.sun.org.apache.xalan.internal.xsltc.util.IntegerArray;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.SequentialTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * The states we can be in are:
 *
 * - initial - nothing done, or we've hit reset. - count in - the count in
 * phase, we need to play a click during this and later times (optionally). -
 * recording - the audio is stored into a buffer - processing - the buffer is
 * analyzed for hits/rhythm - processed - the results are displayed to the user,
 * who is given the chance to map hit types to samples. - While the results are
 * being displayed, loop he performed is "played" back, but as a "sampler" thing
 * where each hit is sliced out and played at the right time. These samples are
 * displayed and can be swapped out for stock samples.
 *
 */
public class BeatBox extends Application implements AudioEngineListener {

    private static final String MIC_SIMPLE_JPG = "resources/mic-simple.jpg";
    private static final String KLICK_PNG = "resources/klick.png";
    static final int SAMPLES = 44100 * 3 * 2; // 3 seconds of recording (both channels)
    private static final String SYTLESHEET = "resources/style.css";
    volatile boolean listening = false;
    private AudioEngine audio;
    private BorderPane root;
    private Stage primStage;
    private Scene rootScene;
    private Label countdownLabel;
    private ChoiceBox<String> cbPatLen;

    public BeatBox() {
        audio = new AudioEngine();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public Parent createBeatWizard1() throws Exception {
        final BorderPane root = new BorderPane();

        InputStream is = getClass().getClassLoader().getResourceAsStream(MIC_SIMPLE_JPG);
        ImageView iRedMic = new ImageView(new Image(is));
        iRedMic.setBlendMode(BlendMode.MULTIPLY);
        final Button brec = new Button("", iRedMic);
        brec.setId("record-button");
        brec.setPrefHeight(400);
        brec.setPrefWidth(200);
        brec.setOnAction(new EventHandler<ActionEvent>() {

            public void handle(ActionEvent arg0) {
                SequentialTransition seqtran = new SequentialTransition();
                FadeTransition ft = new FadeTransition(Duration.millis(300), root);
                ft.setToValue(0.0);
                ft.setInterpolator(Interpolator.EASE_BOTH);

                seqtran.getChildren().add(ft);
                seqtran.playFromStart();
                seqtran.setOnFinished(new EventHandler<ActionEvent>() {

                    @Override
                    public void handle(ActionEvent arg0) {
                        createBeatStartRecord();
                    }
                });
            }
        });

        HBox hbox = new HBox(10);
        hbox.setId("record-settings-hbox");
        hbox.setPadding(new Insets(40));
        hbox.setAlignment(Pos.CENTER);
        final Button bbpm = new Button("120 bpm");
        bbpm.setCursor(Cursor.HAND);
        bbpm.setPrefWidth(125);
        bbpm.setPrefHeight(30);
        bbpm.setOnMouseDragged(new EventHandler<MouseEvent>() {

            public void handle(MouseEvent event) {
                //System.out.println(ToStringBuilder.reflectionToString(event, ToStringStyle.SHORT_PREFIX_STYLE));
                audio.bpm(Math.max(50, Math.min(240, Math.abs(event.getY() - 120.0))));
                bbpm.setText(String.valueOf((int) audio.bpm()) + " bpm");
            }
        });
        bbpm.setOnKeyPressed(new EventHandler<KeyEvent>() {

            @Override
            public void handle(KeyEvent event) {
            }
        });
        cbPatLen = new ChoiceBox<>();
        cbPatLen.getStyleClass().add("loop-length-choicebox");
        cbPatLen.setPrefWidth(125);
        cbPatLen.setPrefHeight(30);
        cbPatLen.getItems().addAll("1 bar", "2 bars", "4 bars", "8 bars", "16 bars", "32 bars");
        cbPatLen.getSelectionModel().selectFirst();

        is = getClass().getClassLoader().getResourceAsStream(KLICK_PNG);
        ImageView iv = new ImageView(new Image(is));
        double x = 0.8;
        iv.setScaleY(x);
        iv.setScaleX(x);
        final ToggleButton tbClick = new ToggleButton("", iv);
        tbClick.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent event) {
                audio.toggleClick();
            }
        });
        tbClick.setPrefWidth(125);
        tbClick.setPrefHeight(32);
        tbClick.setMaxHeight(32);
        tbClick.setMinHeight(32);

        hbox.getChildren().addAll(bbpm, cbPatLen, tbClick);

        root.setCenter(brec);
        root.setBottom(hbox);

        return root;
    }

    private Parent createBeatWizard2() {
        final BorderPane root = new BorderPane();

        countdownLabel = new Label();
        Integer i = 8;
        countdownLabel.setStyle("-fx-font: 100 arial; -fx-color: rgb(1, 1, 1)");
        countdownLabel.setText(i.toString());
        root.setCenter(countdownLabel);

        this.root = root;

        return root;
    }

    public Scene newScene(Parent root) {
        Scene scene = new Scene(root, 800, 600, new Color(.2, .2, .2, 1));
        scene.getStylesheets().add(SYTLESHEET);
        return scene;
    }

    @Override
    public void start(Stage primStage) throws Exception {
        this.primStage = primStage;
        Parent root = createBeatWizard1();
        rootScene = newScene(root);
        primStage.setScene(rootScene);
        primStage.show();

        initAudio();
    }

    private void initAudio() {
        audio.init();
    }

    private void createBeatStartRecord() {
        rootScene.setRoot(createBeatWizard2());
        audio.registerCountinListener(this);
        int bars = Integer.valueOf(cbPatLen.getSelectionModel().getSelectedItem().split(" ")[0]);
        audio.setLoopLengthBeats(bars << 2);
        audio.state(State.COUNTIN);
    }

    @Override
    public void onClick(int remBeats) {
        /**
         * <code>onAnalyze</code> is called from the audio thread, so we add the
         * following work to a queue to be run later on the JavaFX GUI thread.
         */
        final int remaining = remBeats;
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                if (countdownLabel == null) {
                    throw new NullPointerException();
                } else {
                    countdownLabel.setText(String.valueOf(remaining));
                }
            }
        });
    }

    @Override
    public void onRecord() {
        /**
         * <code>onAnalyze</code> is called from the audio thread, so we add the
         * following work to a queue to be run later on the JavaFX GUI thread.
         */
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                if (countdownLabel == null) {
                    throw new NullPointerException();
                } else {
                    countdownLabel.setStyle("-fx-font: 80 arial; -fx-color: rgb(1, 1, 1)");
                    countdownLabel.setText("RECORDING");
                }
            }
        });
    }

    @Override
    public void onAnalyze() {
        /**
         * <code>onAnalyze</code> is called from the audio thread, so we add the
         * following work to a queue to be run later on the JavaFX GUI thread.
         */
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                if (countdownLabel == null) {
                    throw new NullPointerException();
                } else {
                    countdownLabel.setStyle("-fx-font: 80 arial; -fx-color: rgb(1, 1, 1)");
                    countdownLabel.setText("ANALYZING");
                }
            }
        });
    }

    @Override
    public void onRefine() {
    }

    @Override
    public void doneAnalyzing(AnalyzeInfo result) {
        final AnalyzeInfo fres = result;
        /**
         * <code>onAnalyze</code> is called from the audio thread, so we add the
         * following work to a queue to be run later on the JavaFX GUI thread.
         */
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                SequentialTransition seqtran = new SequentialTransition();
                FadeTransition ft = new FadeTransition(Duration.millis(500), root);
                ft.setToValue(0.0);
                ft.setInterpolator(Interpolator.EASE_BOTH);

                seqtran.getChildren().add(ft);
                seqtran.playFromStart();
                seqtran.setOnFinished(new EventHandler<ActionEvent>() {

                    @Override
                    public void handle(ActionEvent arg0) {
                        rootScene.setRoot(createBeatWizard3(fres));
                    }
                });
            }
        });
    }

    private Parent createBeatWizard3B(AnalyzeInfo ai) {
        return DrumController.create(this, audio, ai);
    }

    @SuppressWarnings("unchecked")
    private Parent createBeatWizard3(AnalyzeInfo ai) {
        final VBox root = new VBox();

        {
            float max = 0.f;
            for (int i = 0; i < ai.signal.length; i++) {
                if (Math.abs(ai.signal[i]) > max) {
                    max = Math.abs(ai.signal[i]);
                }
            }

            NumberAxis xAxis = new NumberAxis(0, ai.signal.length / 100, 441.0 / 4.0);
            xAxis.setPrefHeight(0);
            NumberAxis yAxis = new NumberAxis(-1.2, 1.2, 0.1);
            yAxis.setPrefWidth(0);

            // for now, we're going to display a line chart with the audio signal!
            final LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
            chart.setCreateSymbols(false);
            chart.setLegendVisible(false);

            Series<Number, Number> series = new Series<>();

            for (int i = 0; i < ai.signal.length / 100; i++) {
                series.getData().add(new XYChart.Data<Number, Number>(i, ai.signal[i * 100] / max));
            }

            chart.setId("signalChart");
            chart.getData().add(series);
            chart.setPadding(Insets.EMPTY);

            root.getChildren().add(chart);
        }

        {
            int N = ai.powerSpectrum.getRowDimension();
            int M = ai.powerSpectrum.getColumnDimension();


            RealMatrix db = BeatAnalyzer.dbSpectrumNormalize(ai.powerSpectrum);
            BufferedImage img = new BufferedImage(N, M, ColorSpace.TYPE_RGB);
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < M; j++) {
                    double entry = db.getEntry(i, j);
                    int rgb = java.awt.Color.HSBtoRGB((float) entry, 1f, (float) entry);
                    img.setRGB(i, M - j - 1, rgb);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                ImageIO.write(img, "png", out);
                out.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            ImageView iv = new ImageView(new Image(in));
            iv.fitWidthProperty().bind(root.widthProperty());
            iv.fitHeightProperty().bind(root.heightProperty().divide(3));

            root.getChildren().add(iv);
        }

        {
            NumberAxis xAxis = new NumberAxis(0, ai.signal.length, 4410.0 / 4.0);
            xAxis.setPrefHeight(0);
            NumberAxis yAxis = new NumberAxis(0, 1, 0.1);
            yAxis.setPrefWidth(0);

            // for now, we're going to display a line chart with the audio signal!
            final AreaChart<Number, Number> chart = new AreaChart<>(xAxis, yAxis);
            //chart.setCreateSymbols(false);
            ///chart.setLegendVisible(true);

            Series<Number, Number> bbScoreSeries = new Series<>();
            bbScoreSeries.setName("Broadband Energy Score");
            Series<Number, Number> locEnergyScoreSeries = new Series<>();
            locEnergyScoreSeries.setName("Local Energy Score");
            Series<Number, Number> spectralDiffScoreSeries = new Series<>();
            spectralDiffScoreSeries.setName("Spectral Difference Score");
            Series<Number, Number> avgScoreSeries = new Series<>();
            avgScoreSeries.setName("Average Score");
            Series<Number, Number> medianScoreSeries = new Series<>();
            medianScoreSeries.setName("Median Score");
            Series<Number, Number> scoreSeries = new Series<>();
            scoreSeries.setName("Score");
            Series<Number, Number> detectSeries = new Series<>();
            detectSeries.setName("Onset Detected");

            for (int i = 0; i < ai.broadbandScore.length; i++) {
                double t = ai.stftFrameSamplePos[i];
                bbScoreSeries.getData().add(new XYChart.Data<Number, Number>(
                        t, ai.broadbandScore[i]));
                locEnergyScoreSeries.getData().add(new XYChart.Data<Number, Number>(
                        t, ai.localEnergyScore[ai.stftFrameSamplePos[i]]));
                spectralDiffScoreSeries.getData().add(new XYChart.Data<Number, Number>(
                        t, ai.spectralDiffScore[i]));

                avgScoreSeries.getData().add(new XYChart.Data<Number, Number>(
                        t, ai.avgScore[i]));
                medianScoreSeries.getData().add(new XYChart.Data<Number, Number>(
                        t, ai.medianScore[i]));
                scoreSeries.getData().add(new XYChart.Data<Number, Number>(
                        t, ai.score[i]));
                detectSeries.getData().add(new XYChart.Data<Number, Number>(
                        t, ai.detectFn[i]));
            }

            chart.setId("detectionChart");
            chart.getData().addAll(detectSeries, scoreSeries);//, avgScoreSeries, medianScoreSeries); //, bbScoreSeries, locEnergyScoreSeries, 
            //spectralDiffScoreSeries);
            chart.setPadding(Insets.EMPTY);

            root.getChildren().add(chart);
        }

        return root;
    }
}
