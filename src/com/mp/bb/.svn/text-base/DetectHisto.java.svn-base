package com.mp.bb;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class DetectHisto {
	
	private static final int NUM_BUCKETS = 50;
	private static final float N = NUM_BUCKETS;
	
	private float lambda;
	private float[] buckets;
	private float total;

	public DetectHisto(float lambda) {
		this.lambda = lambda;
		buckets = new float[NUM_BUCKETS];
	}
	
	public float percentile(float perc) {
		float w = 0;
		for (int i = 0; i < NUM_BUCKETS; i++) {
			w += buckets[i]/total;
			if (w > perc)
				return (float) i / N;
		}
		return 1;
	}
	
	public float percentileOf(float value) {
		int bucket = (int) (value * N);
		float w = 0;
		for (int i = 0; i < bucket; i++)
			w += buckets[i];
		return w/total;
	}
	
	/**
	 * Data is assumed to be normalized between 0 and 1.
	 * @param detectSignal
	 */
	public void add(float x) {
		int bucket = (int) (x * N);
		if (bucket < 0)
			bucket = 0;
		if (bucket >= NUM_BUCKETS)
			bucket = NUM_BUCKETS-1;
		buckets[bucket] += 1f;
		total += 1;
		decay();
	}

	private void decay() {
		for (int i = 0; i < buckets.length; i++)
			buckets[i] *= lambda;
		total *= lambda;
	}
	
	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
