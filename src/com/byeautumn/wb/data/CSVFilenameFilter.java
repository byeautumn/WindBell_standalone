package com.byeautumn.wb.data;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Created by qiangao on 5/29/2017.
 */
public class CSVFilenameFilter implements FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
        return name.endsWith(".csv");
    }
}
