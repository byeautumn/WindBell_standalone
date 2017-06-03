package com.byeautumn.wb.output;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by qiangao on 5/26/2017.
 */
public class LabelClass7 implements ILabelClass {

    private static final int NUMBER_LABEL = 7;
    private List<String> labelDescList;
    public LabelClass7()
    {
        this.labelDescList = new ArrayList<>(NUMBER_LABEL);
        labelDescList.add(" down 4% or more");
        labelDescList.add(" down between 1.5% and 4%");
        labelDescList.add(" down between 0.5% and 1.5%");
        labelDescList.add(" sideways between -0.5% and +0.3%");
        labelDescList.add(" up between 0.3% and 1%");
        labelDescList.add(" up between 1% and 2%");
        labelDescList.add(" up 2% or more");
    }

    @Override
    public int getNumLabels() {
        return NUMBER_LABEL;
    }

    @Override
    public List<String> getLabels() {
        return this.labelDescList;
    }

    @Override
    public int getLabel(double percentage) {
        int label = 0;
        if(percentage <= -0.01)
            return label;
        else if(percentage > -0.01 && percentage <= -0.004)
            return label + 1;
        else if(percentage > -0.004 && percentage <= -0.0012)
            return label + 2;
        else if(percentage > -0.0012 && percentage <= 0.0012)
            return label + 3;
        else if(percentage > 0.0012 && percentage <= 0.004)
            return label + 4;
        else if(percentage > 0.004 && percentage <= 0.01)
            return label + 5;
        else if(percentage > 0.01)
            return label + 6;

        return Integer.MIN_VALUE;
    }

    public static boolean isValid(int label)
    {
        return label >= 0 && label < 7;
    }
}
