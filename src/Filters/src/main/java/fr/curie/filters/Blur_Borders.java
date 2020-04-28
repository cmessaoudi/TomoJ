package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

import java.awt.*;

/**
 * Description of the Class
 *
 * @author MESSAOUDI C�dric
 * @created 26 f�vrier 2007
 */
public class Blur_Borders implements ExtendedPlugInFilter, DialogListener {
    ImagePlus myimp;
    int flags = DOES_8G + DOES_16 + DOES_32 + PARALLELIZE_STACKS;
    PlugInFilterRunner pfr;
    boolean preview;
    double sizePercent = 0.03;


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
        for (int y = 0; y < ip.getHeight(); y++) {
            double ypercent = (y < ip.getHeight() / 2) ? y / (double) ip.getHeight() : (ip.getHeight() - y) / (double) ip.getHeight();
            for (int x = 0; x < ip.getWidth(); x++) {
                double xpercent = (x < ip.getWidth() / 2) ? x / (double) ip.getWidth() : (ip.getWidth() - x) / (double) ip.getWidth();
                if (xpercent < sizePercent || ypercent < sizePercent) {
                    double val = ip.getPixelValue(x, y);
                    double coeff = (xpercent < sizePercent && ypercent < sizePercent) ? Math.sqrt((1 - xpercent / sizePercent) * (1 - xpercent / sizePercent) + (1 - ypercent / sizePercent) * (1 - ypercent / sizePercent)) : 1.0 - Math.min(xpercent, ypercent) / sizePercent;

                    coeff *= Math.PI / 2;
                    val *= Math.cos(coeff);
                    ip.putPixelValue(x, y, val);
                }

            }
        }


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
        GenericDialog gd = new GenericDialog("Blur borders");
        gd.addNumericField("size", sizePercent, 2);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        sizePercent = gd.getNextNumber();
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
        sizePercent = gd.getNextNumber();
        return true;
    }


}

