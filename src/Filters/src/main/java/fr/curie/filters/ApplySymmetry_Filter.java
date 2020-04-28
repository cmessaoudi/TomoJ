package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 14 janv. 2010
 * Time: 10:32:57
 * To change this template use File | Settings | File Templates.
 */
public class ApplySymmetry_Filter implements ExtendedPlugInFilter, DialogListener {
    ImagePlus myimp;
    double inc = 12;
    int flags = CONVERT_TO_FLOAT + DOES_8G + DOES_16 + DOES_32 + PARALLELIZE_STACKS;
    PlugInFilterRunner pfr;
    boolean preview;


    /**
     * Description of the Method
     *
     * @param arg Description of the Parameter
     * @param imp Description of the Parameter
     * @return Description of the Return Value
     */
    public int setup(String arg, ImagePlus imp) {
        myimp = imp;
        return flags;
    }

    /**
     * Main processing method for the HotSpot_Detection object
     *
     * @param ip Description of the Parameter
     */
    public void run(ImageProcessor ip) {
        //int ind = pfr.getSliceNumber();
        Roi r = new OvalRoi(0, 0, ip.getWidth(), ip.getHeight());
        ip.setRoi(r);
        double bgvalue = ImageStatistics.getStatistics(ip, Measurements.MEAN, null).mean;
        //System.out.println("bg="+bgvalue);
        ImageProcessor result = ip.duplicate();
        double increment = 360 / inc;
        for (int a = 1; a < inc; a++) {
            ImageProcessor tmp = ip.duplicate();
            //tmp.setBackgroundValue(bgvalue);
            //tmp.setValue(bgvalue);
            //tmp.fillOutside(r);
            tmp.rotate(a * increment);
            result.copyBits(tmp, 0, 0, Blitter.ADD);
        }
        ip.copyBits(result, 0, 0, Blitter.COPY);
        ip.multiply(1 / inc);
        ip.setBackgroundValue(bgvalue);
        ip.setValue(bgvalue);
        ip.fillOutside(r);
        //IJ.showProgress(ind,myimp.getNSlices());
        myimp.resetDisplayRange();
        myimp.updateAndDraw();

    }

    /**
     * Description of the Method
     *
     * @param imp     Description of the Parameter
     * @param command Description of the Parameter
     * @param pfr     Description of the Parameter
     * @return Description of the Return Value
     */
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        this.pfr = pfr;
        preview = true;
        GenericDialog gd = new GenericDialog(command);
        gd.addNumericField("symmetry", inc, 2);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        inc = gd.getNextNumber();
        return IJ.setupDialog(imp, flags);
    }

    /**
     * Sets the nPasses attribute of the HotSpot_Detection object
     *
     * @param nPasses The new nPasses value
     */
    public void setNPasses(int nPasses) {
    }

    /**
     * Listener to modifications of the input fields of the dialog
     *
     * @param gd Description of the Parameter
     * @param e  Description of the Parameter
     * @return Description of the Return Value
     */
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        inc = gd.getNextNumber();
        return true;
    }


}
