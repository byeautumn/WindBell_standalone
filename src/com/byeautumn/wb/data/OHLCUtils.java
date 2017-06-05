package com.byeautumn.wb.data;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.byeautumn.wb.data.OHLCDataLineFormater.DATA_ITEM;

public class OHLCUtils
{
	private static final Logger log = LoggerFactory.getLogger(OHLCUtils.class);
	public static OHLCElement buildOHLCElement(OHLCDataLine dataLine) throws ParseException
	{
		String[] strData = dataLine.getData();
		if(null == strData || strData.length < OHLCDataLineFormater.DATA_ITEM.values().length)
			return null;
		
		OHLCDataLineFormater formater = dataLine.getFormater();
		int dateIdx = formater.getDataItemIndex(OHLCDataLineFormater.DATA_ITEM.DATE);
		int openIdx = formater.getDataItemIndex(OHLCDataLineFormater.DATA_ITEM.OPEN);
		int highIdx = formater.getDataItemIndex(OHLCDataLineFormater.DATA_ITEM.HIGH);
		int lowIdx = formater.getDataItemIndex(OHLCDataLineFormater.DATA_ITEM.LOW);
		int closeIdx = formater.getDataItemIndex(OHLCDataLineFormater.DATA_ITEM.CLOSE);
		int volumeIdx = formater.getDataItemIndex(OHLCDataLineFormater.DATA_ITEM.VOLUME);
		SimpleDateFormat dateFormat = formater.getDateFormat();
		OHLCElement elem = new OHLCElement();
		elem.setDateValue(dateFormat.parse(strData[dateIdx]));
		elem.setOpenValue(Double.parseDouble(strData[openIdx]));
		elem.setHighValue(Double.parseDouble(strData[highIdx]));
		elem.setLowValue(Double.parseDouble(strData[lowIdx]));
		elem.setCloseValue(Double.parseDouble(strData[closeIdx]));
		elem.setVolumeValue(Long.parseLong(strData[volumeIdx]));
		
		return elem;
	}
	
	public static OHLCElementTable buildOHLCElementTable(List<String> textData, boolean isFirstLineTitle)
	{
		if(null == textData)
			return null;
		
		OHLCElementTable table = new OHLCElementTable();
		int idx = isFirstLineTitle ? 1 : 0;
		
		OHLCDataLineFormater formater = null;
		
		if(isFirstLineTitle)
			formater = OHLCDataLineFormater.parseFormaterString(textData.get(0), ",");
		
		if(null == formater)
			formater = OHLCDataLineFormater.getDefault();
		
		//NOTE: The new Yahoo data has "Adj Close" which considers the stock historical splits and should be the real close price.
		formater.setDataItemIndex(DATA_ITEM.CLOSE, 5);
		formater.setDataItemIndex(DATA_ITEM.VOLUME, 6);
		
		for(; idx < textData.size(); ++idx)
		{
			OHLCDataLine dataLine = new OHLCDataLine(textData.get(idx));
			dataLine.setFormater(formater);
			OHLCElement elem = null;
			try
			{
				elem = OHLCUtils.buildOHLCElement(dataLine);
			}catch(ParseException pe)
			{
				pe.printStackTrace();
				System.err.println("Error line: " + textData.get(idx));
				continue;
			} catch (NumberFormatException nfe)
			{
				nfe.printStackTrace();
				System.err.println("Error line: " + textData.get(idx));
				continue;
			}
			if(null != elem)
				table.addOHLCElement(elem);
		}
		return table;
	}
	
	public static OHLCElementTable readOHLCDataSourceFile(String fileName)
	{
		Path path = Paths.get(fileName);
		try
		{
			List<String> strList = Files.readAllLines(path, Charset.defaultCharset());
			return OHLCUtils.buildOHLCElementTable(strList, true);
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public static void downloadYahooHistoricalData(String symbol, String destFileName)
	{
		//Hard-coded the start date of 1980-1-1.
		String urlStr = String.format("https://query1.finance.yahoo.com/v7/finance/download/%s?period1=315550800&period2=1496548800&interval=1d&events=history&crumb=OLCTwrLWJEY", symbol);
		log.info("The urlStr: " + urlStr);
		log.info("The destination file name: " + destFileName);
		URL url;
		try {
			url = new URL(urlStr);
			FileUtils.copyURLToFile(url, new File(destFileName), 100000, 100000);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public static void main(String[] args)
	{
//		String textLine = "2014-09-09,131.01,133.73,129.22,130.11,1599700,130.11";
//		OHLCDataLine dataLine = new OHLCDataLine(textLine);
//		try
//		{
//			OHLCElement elem = OHLCUtils.buildOHLCElement(dataLine);
//			System.out.println(elem.printSelf());
//		}catch(ParseException pe)
//		{
//			pe.printStackTrace();
//		}
//		System.out.println("++++++++++++++++++++++++");
//		String sourceFile = "src/com/byeautumn/wb/input/source/Yahoo/SPX_Daily_All.csv";
//		OHLCElementTable table = OHLCUtils.readOHLCDataSourceFile(sourceFile);
//		System.out.println(table.getEarliest().printSelf());
		String symbol = "FB";
		String destFileName = String.format("../resources/source/Yahoo/BigPoolLSTMRunner/bigPool/%s_Daily.csv", symbol);
		OHLCUtils.downloadYahooHistoricalData(symbol, destFileName);
	}

}
