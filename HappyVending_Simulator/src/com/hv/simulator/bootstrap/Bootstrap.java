package com.hv.simulator.bootstrap;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import com.hv.simulator.things.RemoteThing;
import com.thingworx.communications.client.ClientConfigurator;
import com.thingworx.communications.client.ConnectedThingClient;
import com.thingworx.communications.client.things.VirtualThing;

public class Bootstrap extends ConnectedThingClient 
{
	static Properties prop = new Properties();
	public Bootstrap(ClientConfigurator config) throws Exception 
	{
		super(config);
	}

	public static void main(String[] args) 
	{
		try
		{
			loadProperties();
			ClientConfigurator conf = new ClientConfigurator();
			conf.setAppKey(prop.getProperty("APP_KEY"));
			conf.setUri(prop.getProperty("THINGWORX_WS_URI"));
			
			Bootstrap boot = new Bootstrap(conf);
			boot.start();

			if(boot.waitForConnection(Integer.parseInt(prop.getProperty("CONNECTION_WAIT_TIME"))))
			{
				RemoteThing rt = new RemoteThing(prop.getProperty("THING_NAME"), prop.getProperty("THING_DESCRIPTION"), prop.getProperty("THING_ID"), boot);
				boot.bindThing(rt);
				
				while(!boot.isShutdown())
				{
					for (VirtualThing vt : boot.getThings().values()) 
					{
						vt.processScanRequest();
					}
					Thread.sleep(5000);
				}
			}
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private static void loadProperties() throws IOException
	{
		FileInputStream fis = new FileInputStream("HappyVending_Config.properties");
		prop.load(fis);
	}

}
