package com.hv.simulator.bootstrap;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import com.hv.simulator.client.VendingClient;
import com.hv.simulator.things.RemoteThing;
import com.thingworx.communications.client.ClientConfigurator;
import com.thingworx.communications.client.ConnectedThingClient;
import com.thingworx.communications.client.things.VirtualThing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

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
			Logger rootLogger =(Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
			rootLogger.setLevel(Level.DEBUG);
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
				
				VendingClient client = new VendingClient(rt);
				client.start();
				
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
