package fr.curie.utils;

import javax.swing.*;

/**
 * Created by cedric on 27/11/2014.
 */
public class HighPrecisionDisplaySpinner extends JSpinner {
    String precision="0.0000000000";


    public HighPrecisionDisplaySpinner(double currentValue, double minValue, double maxValue, double step, int precision){
        super(new SpinnerNumberModel(currentValue, minValue, maxValue, step));
        this.precision="0.";
        while (precision>0) {
            this.precision+="0";
            precision--;
        }
        System.out.println(this.precision);
        setEditor(createEditor(getModel()));
    }
    protected NumberEditor createEditor(SpinnerModel model){
        //System.out.println("call to createEditor "+precision);
        return new NumberEditor(this,(precision==null)?"0.0000000000":precision);
    }
}
