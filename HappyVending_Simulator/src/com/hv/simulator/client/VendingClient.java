package com.hv.simulator.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.hv.simulator.things.RemoteThing;

public class VendingClient extends Thread 
{
	private RemoteThing rt;
	public VendingClient(RemoteThing rt) 
	{
		this.rt=rt;
	}
	@Override
	public void run() 
	{
		InputStreamReader reader = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(reader);
		try
		{
			String line = null;
			
			while(line ==null || !line.equalsIgnoreCase("q"))
			{
				System.out.println("Please select one option below to dispense ::");
				String[] products = rt.products;
				int i =1;
				for(String productName : products)
				{
					System.out.println(i+"-->"+productName);
					i++;
				}
				line = br.readLine();
				System.out.println("Input Entered ::"+line);
				if(line!=null&&!line.equalsIgnoreCase("q"))
				{
					try
					{
						int selection = Integer.parseInt(line);
						if(selection > products.length || selection < 1)
						{
							System.out.println("Invalid selection, number not in list");
						}else
						{
							rt.dispenseProduct(products[selection-1]);
						}
					}catch(NumberFormatException e)
					{
						System.out.println("Invalid input ["+line+"]. Please enter valid number");
					}
				}
			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}finally
		{
			try {
				br.close();
				reader.close();
			} catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
	}

}
