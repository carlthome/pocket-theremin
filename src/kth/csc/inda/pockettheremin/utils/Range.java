package kth.csc.inda.pockettheremin.utils;

public class Range {
	final public double max, min, range;
	private double current;

	public Range(double max, double min) {
		this.max = max;
		this.min = min;
		this.range = max - min;
		this.current = min;
	}

	public void set(double value) {
		if (value > max)
			value = max;

		if (value < min)
			value = min;

		this.current = value;
	}

	public double get() {
		return current;
	}

	public double getPercent() {
		return current / range;
	}
}