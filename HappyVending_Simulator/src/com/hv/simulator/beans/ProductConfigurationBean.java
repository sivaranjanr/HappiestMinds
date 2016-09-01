package com.hv.simulator.beans;

import org.json.JSONObject;

public class ProductConfigurationBean 
{
	String productName;
	double price;
	double current_quantity;
	double max_quantity;
	String proposition;

	public double getPrice() {
		return price;
	}
	public void setPrice(double price) {
		this.price = price;
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
	public String getProductName() {
		return productName;
	}
	public void setProductName(String productName) {
		this.productName = productName;
	}
	public String getProposition() {
		return proposition;
	}
	public void setProposition(String proposition) {
		this.proposition = proposition;
	}
	

}
