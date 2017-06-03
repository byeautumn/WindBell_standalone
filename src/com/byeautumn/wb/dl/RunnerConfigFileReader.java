package com.byeautumn.wb.dl;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by qiangao on 5/21/2017.
 */
public class RunnerConfigFileReader {
    private static final Logger log = LoggerFactory.getLogger(RunnerConfigFileReader.class);
    Map<String, String> propertyMap;

    public RunnerConfigFileReader(String configFileName)
    {
        File configFile = new File(configFileName);
        if(!configFile.exists())
        {
            log.error("The config file doesn't exist.");
            return;
        }

        parseConfigFile(configFile);
    }

    private void parseConfigFile(File configFile)
    {
        try
        {
            List<String> lines = FileUtils.readLines(configFile);
            if(null == lines)
            {
                log.error("The config file is empty and cannot be parsed.");
                return;
            }
            if(null == propertyMap)
                propertyMap = new HashMap<>();

            for(String line : lines)
            {
                if(line.isEmpty())
                    continue;
                if(line.startsWith("//") || line.startsWith("#"))
                    continue;
                String[] pair = line.trim().split("=");
                if(pair.length != 2)
                    continue;
                propertyMap.put(pair[0].trim(), pair[1].trim());
            }
        } catch (IOException ioe)
        {
            log.error(ioe.getMessage());
        }
    }

    public String getProperty(String key)
    {
        String value = propertyMap.get(key);
        if(null == value)
            log.info("Property " + key + " doesn't exist.");

        return value;
    }

    public String printSelf()
    {
        StringBuffer sb = new StringBuffer();
        for(Map.Entry e : propertyMap.entrySet())
        {
            sb.append(e.getKey()).append(": ").append(e.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }
}
