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
				line = br.readLine();
				System.out.println("Input Entered ::"+line);
				rt.dispenseProduct("COFFEE");
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
