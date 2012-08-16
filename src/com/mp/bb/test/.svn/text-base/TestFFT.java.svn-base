package com.mp.bb.test;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class TestFFT extends Application {

	@Override
	public void start(Stage primaryStage) throws Exception {
		
		int N = 512;
		
		double max = 0;
		double[] x = new double[N];
		for (int i = 0; i < N; i++) {
			//if ((i / 20) % 2 == 0)
				//x[i] = 1.0;
//			double window = 0.5 * (1 - Math.cos(2*Math.PI*i/(N-1)));
//			x[i] = window;
			
			double f = (double)i/4.0;
			x[i] += Math.sin(f * (double) i / (double) N * 2.0 * Math.PI);
//			x[i] += 2.*Math.random() - 1.0;
			
			System.out.println(x[i]);
			
			if (Math.abs(x[i]) > max)
				max = Math.abs(x[i]);
			
			
		}
		
		double E = 0;
		for (int i = 0; i < x.length; i++) 
			E += x[i]*x[i];
		E /= (double)x.length;
		
//		x[0] = 1.0;
		
		// first, let's just graph this guy.
		
		VBox vbox = new VBox(20);
		
		Axis<Number> xAxis = new NumberAxis(0.0, 2*Math.PI, Math.PI/2);
		Axis<Number> yAxis = new NumberAxis(-max, max, max/20.0);
		
		LineChart<Number, Number> signalChart = new LineChart<>(xAxis, yAxis);
		signalChart.setCreateSymbols(false);
		signalChart.setLegendVisible(false);
		
		Series<Number, Number> series = new Series<>();
		
		for (int i = 0; i < N; i++) {
			series.getData().add(new XYChart.Data<Number, Number>(
					(double) i / (double) N * 2.0 * Math.PI, 
					x[i]));
		}
		
		signalChart.getData().add(series);
		vbox.getChildren().add(signalChart);
		
		// try padding the graph with zeros.
		
		double[] oldx = x;
		N *= 2;
		x = new double[N];
		for (int i = 0; i < N; i++) {
			if (i < oldx.length)
				x[i] = oldx[i];
			else
				x[i] = 0;
		}
		
		// good, now graph the FFT.
		
		FastFourierTransformer fft = new FastFourierTransformer();
		Complex[] X = fft.transform(x);
		
		for (int i = 0; i < X.length; i++) {
			System.out.printf("%4.2f + %4.2fi\n", X[i].getReal(), X[i].getImaginary());
		}
		System.out.printf("%4.2f + %4.2fi\n", X[0].getReal(), X[0].getImaginary());
		
		System.out.println(X.length);
		System.out.println(N);
		
		xAxis = new NumberAxis(0.0, N/2, N/2/10);
		yAxis = new NumberAxis(0.0, 1, 0.1);
		
		LineChart<Number, Number> spectrumChart = new LineChart<>(xAxis, yAxis);
		spectrumChart.setLegendVisible(false);
		
		series = new Series<>();
		
		double sum = 0;
		max = 0;
		for (int i = 0; i < N/2; i++) {
			double v = (X[i].getReal()*X[i].getReal() + X[i].getImaginary()*X[i].getImaginary())/((double)(N*N));
			sum += v;
			if (v > max) {
				max = v;
			}
		}
		sum *= 4.;
		
		for (int i = 0; i < N/2; i++) {
			series.getData().add(new XYChart.Data<Number, Number>(
					i,
					1.0/max*(X[i].getReal()*X[i].getReal() + X[i].getImaginary()*X[i].getImaginary())/((double)(N*N))));
		}
		
		spectrumChart.getData().add(series);
		vbox.getChildren().add(spectrumChart);
		
		// Note: the entries after X.length/2 are REDUNDANT!
		// i.e. X[i] = complex conjugate of X[N-1-i]
		
		Scene scene = new Scene(vbox, 800, 600, Color.GRAY);
		primaryStage.setScene(scene);
		primaryStage.show();
		
		System.out.println(E);
		System.out.println(sum);
		System.out.println(E/sum);
	}

	public static void main( String[] args ) {
		launch(args);
	}
	
}
