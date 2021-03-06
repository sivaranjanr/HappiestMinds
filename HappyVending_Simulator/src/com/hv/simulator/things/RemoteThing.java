package com.hv.simulator.things;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.hv.simulator.beans.InventryBean;
import com.hv.simulator.beans.ProductConfigurationBean;
import com.hv.simulator.beans.ProductPropositionBean;
import com.thingworx.communications.client.ConnectedThingClient;
import com.thingworx.communications.client.ConnectionException;
import com.thingworx.communications.client.things.VirtualThing;
import com.thingworx.metadata.DataShapeDefinition;
import com.thingworx.metadata.FieldDefinition;
import com.thingworx.metadata.PropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.annotations.ThingworxServiceResult;
import com.thingworx.relationships.RelationshipTypes.ThingworxEntityTypes;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.AspectCollection;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.constants.Aspects;
import com.thingworx.types.constants.DataChangeType;
import com.thingworx.types.primitives.DatetimePrimitive;
import com.thingworx.types.primitives.InfoTablePrimitive;
import com.thingworx.types.primitives.IntegerPrimitive;
import com.thingworx.types.primitives.LocationPrimitive;
import com.thingworx.types.primitives.StringPrimitive;
import com.thingworx.types.primitives.structs.Location;

public class RemoteThing extends VirtualThing
{
	private static final long serialVersionUID = 1L;
	ConnectedThingClient client;
	String thingName;
	String location;
	static Properties props; 
	Map<String,Double> inventryMap = new HashMap<>();
	public String[] products = {"COFFEE","TEA","ESPRESSO","CAPPUCHINO"};
	static
	{
		props = new Properties();
		try 
		{
			FileInputStream fis = new FileInputStream("Thing_PropertyDefinitions.properties");
			props.load(fis);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public RemoteThing(String name, String description, String identifier, ConnectedThingClient client) 
	{
		super(name, description, identifier, client);
		this.client = client;
		this.thingName=name;
		initializeFromAnnotations();
		defineProperties();
		getVendingMachineLocation();
	}
	
	@Override
	public void processScanRequest() throws Exception 
	{
		try
		{
			this.setPropertyValue("currentTemperature", new IntegerPrimitive(getTemparature()));
			this.setPropertyValue("currentHumidity", new IntegerPrimitive(getHumidity()));
			this.setPropertyValue("locationCoordinates", new LocationPrimitive(new Location(Double.parseDouble(props.getProperty("LOCATION_LONG")),Double.parseDouble(props.getProperty("LOCATION_LAT")))));
			this.setPropertyValue("locationName", new StringPrimitive(props.getProperty("LOCATION_NAME")));
			this.setPropertyValue("cashInVault", new StringPrimitive(props.getProperty("CASH_IN_DEVICE")));
			this.setPropertyValue("inventory", new InfoTablePrimitive(GetInventryDetails()));
			this.setPropertyValue("products", new InfoTablePrimitive(GetProductDetails()));
			this.updateSubscribedProperties(1000);
			
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private void defineProperties()
	{
		int i = 1;
		while(props.containsKey("PROP_DEF_"+i))
		{
			String line = props.getProperty("PROP_DEF_"+i).toString();
			String defvalues[] = line.split("\\|");
			String name = defvalues[0];
			String description = defvalues[1];
			String sbtype = defvalues[2];
			BaseTypes baseType = BaseTypes.fromFriendlyName(sbtype);
			
			PropertyDefinition propertyDefinition = new PropertyDefinition(name, description, baseType);
			boolean persistent = Boolean.getBoolean(defvalues[3]);
			propertyDefinition.setPersistent(persistent);
			
			AspectCollection apc = new AspectCollection();
			apc.put(Aspects.ASPECT_DATACHANGETYPE, new StringPrimitive(DataChangeType.VALUE.name()));
			apc.put("pushType", new StringPrimitive(DataChangeType.VALUE.name()));
			//apc.put("cacheTime",new IntegerPrimitive(0));
			propertyDefinition.setAspects(apc);
			this.defineProperty(propertyDefinition);
			try
			{
				super.initialize();
			}catch(Exception e)
			{
				e.printStackTrace();
			}
			i++;
		}
	}
	
	@ThingworxServiceDefinition(name="GetInventryDetails",description="Get invetry details")
	@ThingworxServiceResult(name="response",baseType="INFOTABLE")
	public InfoTable GetInventryDetails() throws Exception
	{
		JSONObject response = new JSONObject();
		try
		{
			DataShapeDefinition dsd = new DataShapeDefinition();
			dsd.addFieldDefinition(new FieldDefinition("item", BaseTypes.STRING));
			dsd.addFieldDefinition(new FieldDefinition("current_quantity", BaseTypes.NUMBER));
			dsd.addFieldDefinition(new FieldDefinition("max_quantity", BaseTypes.NUMBER));
			
			Gson gson = new Gson();
			response.put("dataShape", dsd.toJSON());
			response.put("rows", new JSONArray(gson.toJson(readInventryFile())));
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return InfoTable.fromJSON(response);
	}
	
	
	@ThingworxServiceDefinition(name="UpdateInventryDetails",description="update invetry details")
	public synchronized void UpdateInventryDetails(@ThingworxServiceParameter(name="inventry",baseType="INFOTABLE") InfoTable inventry,@ThingworxServiceParameter(name="isAddition",baseType="BOOLEAN") Boolean isAddition)
	{
		BufferedWriter br =null;
		try
		{
			ArrayList<InventryBean> currentInventry = readInventryFile();
			@SuppressWarnings("unchecked")
			ArrayList<InventryBean> clonedCurrentInventry =(ArrayList<InventryBean>)currentInventry.clone();
			br = new BufferedWriter(new FileWriter("ThingInventry.csv"));
			ArrayList<String> header = new ArrayList<>();
			boolean isMaxHeaderPresent = false;
			for(FieldDefinition fd : inventry.getDataShape().getFields().getOrderedFields())
			{
				header.add(fd.getName());
				if(fd.getName().equalsIgnoreCase("MAX_QUANTITY"))
					isMaxHeaderPresent = true;
			}
			if(!isMaxHeaderPresent)
				header.add("MAX_QUANTITY");
			int i=1;
			for(String headerName : header)
			{
				br.write(headerName.toUpperCase());
				if(header.size()>i )
					br.write(",");
				i++;
			}
			br.newLine();
			for(ValueCollection row : inventry.getRows())
			{
				i=1;
				for(String headerName : header)
				{
					
					if(headerName.equalsIgnoreCase("ITEM"))
					{
						for(InventryBean ib : currentInventry)
						{
							if(ib.getItem().equalsIgnoreCase(row.getStringValue(headerName)))
							{
								clonedCurrentInventry.remove(ib);
							}
						}
					}
					if(isAddition!=null && isAddition)
					{
						boolean haswritten =false;	
						for(InventryBean ib : currentInventry)
							{
								if(ib.getItem().equalsIgnoreCase(row.getStringValue("item"))&& headerName.equalsIgnoreCase("CURRENT_QUANTITY"))
								{
									br.write(String.valueOf(ib.getCurrent_quantity()+Double.parseDouble(row.getStringValue(headerName))));
									haswritten=true;
								}
								if(ib.getItem().equalsIgnoreCase(row.getStringValue("item"))&& headerName.equalsIgnoreCase("MAX_QUANTITY"))
								{
									if(row.getStringValue(headerName)!=null)
										br.write(String.valueOf(ib.getMax_quantity()+Double.parseDouble(row.getStringValue(headerName))));
									else
										br.write(String.valueOf(ib.getMax_quantity()));
									haswritten=true;
								}
							}
							if(!haswritten)
							br.write(row.getStringValue(headerName));
					}else
					{
						br.write(row.getStringValue(headerName));
					}
					if(header.size()>i )
						br.write(",");
					i++;
				}
				br.newLine();
			}
			for(InventryBean ib : clonedCurrentInventry)
			{
				i=1;
				for(String headerName : header)
				{
					if(headerName.equalsIgnoreCase("ITEM"))
					{
						br.write(ib.getItem());
					}
					if(headerName.equalsIgnoreCase("CURRENT_QUANTITY"))
					{
						br.write(String.valueOf(ib.getCurrent_quantity()));
					}
					if(headerName.equalsIgnoreCase("MAX_QUANTITY"))
					{
						br.write(String.valueOf(ib.getMax_quantity()));
					}
					if(header.size()>i )
						br.write(",");
					i++;
				}
				br.newLine();
			}
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}finally
		{
			if(br!=null)
			{
				try 
				{
					br.close();
				} catch (IOException e) 
				{
					e.printStackTrace();
				}
			}
		}
		
		try 
		{
			InfoTable inventory = GetInventryDetails();
			fireInventryUpdatedEvent(inventory);
			checkAndProcessInventoryOrders(inventory);
		} catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
	
	/*@ThingworxServiceDefinition(name="GetProductDetails",description="Get product details")
	@ThingworxServiceResult(name="response",baseType="INFOTABLE")
	public InfoTable GetProductDetails() throws Exception
	{
		JSONObject response = new JSONObject();
		try
		{
			DataShapeDefinition dsd = new DataShapeDefinition();
			dsd.addFieldDefinition(new FieldDefinition("item", BaseTypes.STRING));
			dsd.addFieldDefinition(new FieldDefinition("current_quantity", BaseTypes.NUMBER));
			dsd.addFieldDefinition(new FieldDefinition("max_quantity", BaseTypes.NUMBER));
			InfoTable currentInventry = GetInventryDetails();
			Map<String, Integer > products = calculateProducts(currentInventry,"current_quantity");
			Map<String, Integer > productsMax = calculateProducts(currentInventry,"max_quantity");
			ArrayList<ProductsBean> prodictsArr = new ArrayList<>();
			for(Entry<String, Integer> product : products.entrySet())
			{
				ProductsBean pb = new ProductsBean();
				pb.setItem(product.getKey());
				pb.setCurrent_quantity(product.getValue());
				pb.setMax_quantity(productsMax.get(product.getKey()));
				prodictsArr.add(pb);
			}
			Gson gson = new Gson();
			response.put("dataShape", dsd.toJSON());
			response.put("rows", new JSONArray(gson.toJson(prodictsArr)));
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return InfoTable.fromJSON(response);
	}*/
	
	@ThingworxServiceDefinition(name="GetProductDetails",description="Get product details")
	@ThingworxServiceResult(name="response",baseType="INFOTABLE")
	public InfoTable GetProductDetails() throws Exception
	{
		JSONObject response = new JSONObject();
		Gson gson = new Gson();
		try
		{
			DataShapeDefinition dsd = new DataShapeDefinition();
			dsd.addFieldDefinition(new FieldDefinition("productName", BaseTypes.STRING));
			dsd.addFieldDefinition(new FieldDefinition("current_quantity", BaseTypes.NUMBER));
			dsd.addFieldDefinition(new FieldDefinition("max_quantity", BaseTypes.NUMBER));
			dsd.addFieldDefinition(new FieldDefinition("proposition", BaseTypes.INFOTABLE));
			dsd.addFieldDefinition(new FieldDefinition("price", BaseTypes.NUMBER));
			
			
			DataShapeDefinition propositionDS = new DataShapeDefinition();
			propositionDS.addFieldDefinition(new FieldDefinition("item", BaseTypes.STRING));
			propositionDS.addFieldDefinition(new FieldDefinition("quantity", BaseTypes.NUMBER));
			
			InfoTable currentInventry = GetInventryDetails();
			Map<String, Integer > products = calculateProducts(currentInventry,"current_quantity");
			Map<String, Integer > productsMax = calculateProducts(currentInventry,"max_quantity");
			ArrayList<ProductConfigurationBean> productsArr = new ArrayList<>();
			for(Entry<String, Integer> product : products.entrySet())
			{
				ProductConfigurationBean pb = new ProductConfigurationBean();
				pb.setProductName(product.getKey());
				pb.setCurrent_quantity(product.getValue());
				pb.setMax_quantity(productsMax.get(product.getKey()));
				
				ArrayList<ProductPropositionBean> productPropArr = new ArrayList<>();
				
				Map<String, Integer> props = getProposition(product.getKey());
				if(props!=null)
				{
					for(Entry<String, Integer> pproduct : props.entrySet())
					{
						ProductPropositionBean ppb = new ProductPropositionBean();
						ppb.setItem(pproduct.getKey());
						ppb.setQuantity(pproduct.getValue());
						productPropArr.add(ppb);
					}
				}
				JSONObject propositionJSON = new JSONObject();
				propositionJSON.put("dataShape", propositionDS.toJSON());
				propositionJSON.put("rows", new JSONArray(gson.toJson(productPropArr)));
				
				pb.setProposition(propositionJSON.toString());
				productsArr.add(pb);
			}
			response.put("dataShape", dsd.toJSON());
			response.put("rows", new JSONArray(gson.toJson(productsArr)));
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return InfoTable.fromJSON(response);
	}
	
	private ArrayList<InventryBean> readInventryFile()
	{
		ArrayList<InventryBean> inventryBeans = new ArrayList<>();
		BufferedReader breader = null;
		try
		{
			breader = new BufferedReader(new FileReader("ThingInventry.csv"));
			String line = null;
			int i=0;
			String[] headerArray=null;
			while((line = breader.readLine())!=null&& line.trim()!=null)
			{
				//ignoring header
				if(i==0)
				{
					headerArray = line.split(",");
					i++;
					continue;
				}else
				{
					InventryBean bean = new InventryBean();
					String[] values = line.split(",");
					int index =0;
					for(String headerName : headerArray)
					{
						if(headerName.equalsIgnoreCase("item"))
						{
							bean.setItem(values[index]);
						}else if(headerName.equalsIgnoreCase("current_quantity"))
						{
							try
							{
								bean.setCurrent_quantity(Double.parseDouble(values[index]));
							}catch(Exception e)
							{
								bean.setCurrent_quantity(0);
							}
						}
						else if(headerName.equalsIgnoreCase("max_quantity"))
						{
							try
							{
								bean.setMax_quantity(Double.parseDouble(values[index]));
							}catch(Exception e)
							{
								bean.setMax_quantity(0);
							}
						}
						index++;
					}
					inventryBeans.add(bean);
				}
			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}finally
		{
			if(breader!=null)
			{
				try {
					breader.close();
				} catch (IOException e) 
				{
					e.printStackTrace();
				}
			}
		}
		return inventryBeans;
	}
	
	private int getTemparature()
	{
		try
		{
			Random randomTemp = new Random();
			return 55+randomTemp.nextInt(10);
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return 0;
	}
	
	private int getHumidity()
	{
		try
		{
			Random randomHumid = new Random();
			return 30+randomHumid.nextInt(20);
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return 0;
	}
	
	public Map<String, Integer> calculateProducts(InfoTable inventry,String quantityField)
	{
		for(ValueCollection row : inventry.getRows())
		{
			inventryMap.put((String)row.getValue("item"), ((Double)row.getValue("current_quantity")*1000));
		}
		Map<String, Integer> response = new HashMap<>();
		
		try
		{
			for(String product : products)
			{
				for(ValueCollection row : inventry.getRows())
				{
					inventryMap.put((String)row.getValue("item"), ((Double)row.getValue(quantityField)*1000));
				}
				response.put(product, 0);
				while(canMake(product))
				{
					response.put(product, response.get(product)+1);
				}
			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return response;
	}
	
	private boolean canMake(String product)
	{
		boolean can = false;
		Map<String, Integer> prop = getProposition(product);
		if(prop!=null)
		{
			for(Entry<String,Integer> entry : prop.entrySet())
			{
				String name = entry.getKey();
				int value = entry.getValue();
				double availableQty = inventryMap.get(name);
				if((availableQty - value) <0)
				{
					can = false;
					break;
				}else
				{
					can = true;
				}
			}
		}
		if(can)
		{
			for(Entry<String,Integer> entry : prop.entrySet())
			{
				String name = entry.getKey();
				int value = entry.getValue();
				double availableQty = inventryMap.get(name);
				inventryMap.put(name, availableQty - value);
			}
		}
		return can;
	}
	
	private Map<String, Integer> getProposition(String productName)
	{
		
		Map<String,Integer> prop = null; 
		Properties propositions = new Properties();
		FileInputStream psFile;
		try {
			psFile = new FileInputStream("Proposition.properties");
			propositions.load(psFile);
			psFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(propositions.containsKey(productName))
		{
			prop = new HashMap<String, Integer>();
			String pString = propositions.getProperty(productName);
			String[] psArr = pString.split(",");
			for(String p : psArr)
			{
				String[] qdetails = p.split(":");
				prop.put(qdetails[0], Integer.parseInt(qdetails[1]));
			}
		}
		/*if(productName.equalsIgnoreCase("COFFEE"))
		{
			prop = new HashMap<String, Integer>();
			prop.put("MILK", 20);
			prop.put("COFFEE", 10);
			prop.put("WATER", 70);
			prop.put("SUGAR", 10);
		}else if(productName.equalsIgnoreCase("CAPPUCHINO"))
		{
			prop = new HashMap<String, Integer>();
			prop.put("MILK", 10);
			prop.put("COFFEE", 30);
			prop.put("WATER", 10);
			prop.put("SUGAR", 10);
		}*/
		return prop;
	}
	
	public void dispenseProduct(String productName)
	{
		try
		{
			Map<String,Integer> prop = getProposition(productName);
			ArrayList<InventryBean> ibArr = readInventryFile();
			boolean isdispensed = false;
			for(String key : prop.keySet())
			{
				boolean isCompoundPresent = false;
				for(InventryBean ib : ibArr)
				{
					if(ib.getItem().equalsIgnoreCase(key))
					{
						isCompoundPresent = true;
						if(prop.get(key)>(ib.getCurrent_quantity()*1000))
						{
							System.out.println("Less Inventry Unable to dispense the product");
							isCompoundPresent = false;
						}else
						{
							ib.setCurrent_quantity(((ib.getCurrent_quantity()*1000)-prop.get(key))/1000);
						}
					}
				}
				if(!isCompoundPresent)
				{
					System.out.println("Unable to dispense.Required Compound is missing / less in inventry");
					isdispensed = false;
					break;
				}else
				{
					isdispensed = true;
				}
			}
			if(isdispensed)
			{
				UpdateInventryDetails(CreateInventryInfoTable(ibArr),false);
			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		System.out.println("*****************************************************************");
		System.out.println(productName+" dispensed.");
		System.out.println("*****************************************************************");
	}
	
	private InfoTable CreateInventryInfoTable(ArrayList<InventryBean> invBeanArr) throws Exception
	{
		JSONObject response = new JSONObject();
		try
		{
			DataShapeDefinition dsd = new DataShapeDefinition();
			dsd.addFieldDefinition(new FieldDefinition("item", BaseTypes.STRING));
			dsd.addFieldDefinition(new FieldDefinition("current_quantity", BaseTypes.NUMBER));
			dsd.addFieldDefinition(new FieldDefinition("max_quantity", BaseTypes.NUMBER));
			
			Gson gson = new Gson();
			response.put("dataShape", dsd.toJSON());
			response.put("rows", new JSONArray(gson.toJson(invBeanArr)));
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return InfoTable.fromJSON(response);
	}
	
	private void fireInventryUpdatedEvent(InfoTable inventory)
	{
		try
		{
			ValueCollection inventoryUpdated = new ValueCollection();
			inventoryUpdated.put("inventory", new InfoTablePrimitive(inventory));
			client.fireEvent(ThingworxEntityTypes.Things, thingName, "inventoryUpdated", inventoryUpdated, 1000);
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private void checkAndProcessInventoryOrders(InfoTable inventory)
	{
		try
		{
			
			InfoTable git=client.invokeService(ThingworxEntityTypes.Things, "HappyVedingGenericServices", "GetPropertyValues", new ValueCollection(), 1000);
			InfoTable it = client.invokeService(ThingworxEntityTypes.Things, "VendingMachine_Order_DT", "GetPropertyValues", new ValueCollection(), 1000);
			int id =0;
			int milkThreshold = (int)Double.parseDouble(git.getRow(0).getValue("MilkThreshold").toString());
			int waterThreshold =(int)Double.parseDouble(git.getRow(0).getValue("WaterThreshold").toString());
			int cofeeBeanThreshold=(int)Double.parseDouble(git.getRow(0).getValue("CoffeeBeanThreshold").toString());
			int sugarThreshold=(int)Double.parseDouble(git.getRow(0).getValue("SugarThreshold").toString());
			for(ValueCollection row : inventory.getRows())
			{
				String item = (String)row.getValue("item");
				double currentQuantity =(Double)row.getValue("current_quantity");
				double maxQuantity =(Double)row.getValue("max_quantity");
				if(item.equalsIgnoreCase("MILK") && currentQuantity<milkThreshold)
				{
					id = Integer.parseInt(it.getRow(0).getValue("id").toString());
					try
					{
						placeOrder(id, "MILK", maxQuantity - currentQuantity, location,currentQuantity,maxQuantity);
					}catch(Exception e)
					{
						
					}
				}else if(item.equalsIgnoreCase("SUGAR") && currentQuantity<sugarThreshold)
				{
					id = Integer.parseInt(it.getRow(0).getValue("id").toString());
					try
					{
						placeOrder(id, "SUGAR", maxQuantity - currentQuantity, location,currentQuantity,maxQuantity);
					}catch(Exception e)
					{
						
					}
				}else if(item.equalsIgnoreCase("COFEE BEANS") && currentQuantity<cofeeBeanThreshold)
				{
					id = Integer.parseInt(it.getRow(0).getValue("id").toString());
					try
					{
						placeOrder(id, "COFEE BEANS", maxQuantity - currentQuantity, location,currentQuantity,maxQuantity);
					}catch(Exception e)
					{
						
					}
				}else if(item.equalsIgnoreCase("WATER") && currentQuantity<waterThreshold)
				{
					id = Integer.parseInt(it.getRow(0).getValue("id").toString());
					try
					{
						placeOrder(id, "WATER", maxQuantity - currentQuantity, location,currentQuantity,maxQuantity);
					}catch(Exception e)
					{
						
					}
				}
			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void placeOrder(int id, String productName,double quantity,String location, double currentQuantity,double maxQuantity) throws TimeoutException, ConnectionException, Exception
	{
		ValueCollection vc = new ValueCollection();
		vc.put("OrderStatus", new StringPrimitive("Open"));
		vc.put("quantity", new IntegerPrimitive(quantity));
		vc.put("User", new StringPrimitive(thingName));
		vc.put("VendingMachineName", new StringPrimitive(thingName));
		vc.put("id", new IntegerPrimitive(id));
		vc.put("productName", new StringPrimitive(productName));
		vc.put("Location", new StringPrimitive(location));
		vc.put("OrderPlacedDate", new DatetimePrimitive(DateTime.now()));
		vc.put("productDeliveryDate", new DatetimePrimitive(DateTime.now().plusDays(2)));
		vc.put("currentQuantity", new IntegerPrimitive(currentQuantity));
		vc.put("maxQuantity", new IntegerPrimitive(maxQuantity));
		client.invokeService(ThingworxEntityTypes.Things, "VendingMachine_Order_DT", "PlaceOrder", vc, 1000);
	}
	
	public void getVendingMachineLocation()
	{
		try
		{
			ValueCollection vc = new ValueCollection();
			vc.put("VendingMachineName", new StringPrimitive(thingName));
			InfoTable it=client.invokeService(ThingworxEntityTypes.Things, "HappyVedingGenericServices", "GetVendingMachineLocation", vc, 1000);
			location = it.getRow(0).getValue("result").toString();
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
}
