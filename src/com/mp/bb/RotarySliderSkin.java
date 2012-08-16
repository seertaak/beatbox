package com.mp.bb;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;

public class RotarySliderSkin implements Skin<RotarySlider> {
	
	private Group root;
	private RotarySlider control;
	private Arc selArc;
	
	public RotarySliderSkin(RotarySlider control) {
		this.control = control;
		
		Circle bg = new Circle(0, 0, 60);
		bg.setFill(Color.WHITE);
		
		Arc mainArc = new Arc(0, 0, 50, 50, -60, 300);
		mainArc.setFill(Color.TRANSPARENT);
		mainArc.setType(ArcType.OPEN);
		mainArc.setStroke(Color.BLACK);
		mainArc.setStrokeWidth(4.0);
		
		selArc = new Arc(0, 0, 50, 50, -120, 300.0);
		selArc.setFill(Color.TRANSPARENT);
		selArc.setType(ArcType.OPEN);
		selArc.setStroke(Color.GOLD.darker());
		selArc.setStrokeWidth(7.0);
		
		this.root = new Group(bg, mainArc, selArc);
		update();
	}

	public void update() {
		double perc = (control.getValue() - control.getMin()) / (control.getMax() - control.getMin());
		selArc.setLength(-perc*300.0);
	}

	@Override
	public void dispose() {
	}

	@Override
	public Node getNode() {
		return root;
	}
	
	@Override
	public RotarySlider getSkinnable() {
		return control;
	}

}
