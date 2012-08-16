package com.mp.bb;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import javafx.scene.input.MouseEvent;

public class RotarySlider extends Control {
	
	private DoubleProperty value;
	private DoubleProperty min;
	private DoubleProperty max;
	
	private double dragY;
	private double initY;
	
	public RotarySlider() {
		this.value = new SimpleDoubleProperty(0.5);
		this.min = new SimpleDoubleProperty(0.0);
		this.max = new SimpleDoubleProperty(1.0);
		this.dragY = 0;
		final RotarySliderSkin skin = new RotarySliderSkin(this);
		setSkin(skin);
		
		setOnMousePressed(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				dragY = event.getY();
				initY = value.get();
			}
		});
		setOnMouseDragged(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (Double.isNaN(dragY))
					throw new IllegalStateException();
				double v = Math.min(Math.max((-event.getY() + dragY)/100.0 + initY, 0), 1.0);
				value.set(v);
				skin.update();
			}
		});
	}
	
	public DoubleProperty valueProperty() {
		return value;
	}
	
	public void setValue(double value) {
		this.value.set(value);
	}
	
	public double getValue() {
		return value.get();
	}
	
	public DoubleProperty minProperty() {
		return min;
	}
	
	public void setMin(double min) {
		this.min.set(min);
	}
	
	public double getMin() {
		return min.get();
	}

	public DoubleProperty maxProperty() {
		return max;
	}
	
	public void setMax(double max) {
		this.max.set(max);
	}
	
	public double getMax() {
		return max.get();
	}
}
