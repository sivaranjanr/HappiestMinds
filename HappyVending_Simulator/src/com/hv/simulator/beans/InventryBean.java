package com.hv.simulator.beans;

public class InventryBean 
{
	String item;
	double current_quantity;
	double max_quantity;
	
	public String getItem() {
		return item;
	}
	public void setItem(String item) {
		this.item = item;
	}
	public double getCurrent_quantity() {
		return current_quantity;
	}
	public void setCurrent_quantity(double current_quantity) {
		this.current_quantity = current_quantity;
	}
	public double getMax_quantity() {
		return max_quantity;
	}
	public void setMax_quantity(double max_quantity) {
		this.max_quantity = max_quantity;
	}
	
	
}
