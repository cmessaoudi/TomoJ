package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.*;


/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 11 janv. 2010
 * Time: 09:57:24
 * To change this template use File | Settings | File Templates.
 */
public class MSD_Filter implements ExtendedPlugInFilter, DialogListener {
    ImagePlus myimp;
    int vois = 1;
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
        int ind = pfr.getSliceNumber();
        ImageProcessor lsd = new FloatProcessor(ip.getWidth(), ip.getHeight());
        System.out.println("image:" + ind);
        for (int j = 0; j < ip.getHeight(); j++) {
            IJ.showProgress(j + 1, ip.getHeight());
            for (int i = 0; i < ip.getWidth(); i++) {
                lsd.putPixelValue(i, j, squareDifference(ip, i, j));
            }
        }
        System.out.println("creation of difference image finished");
        //new ImagePlus("msd of"+myimp.getTitle(),lsd).show();
        System.out.println("now copy bits on original image");
        ip.copyBits(lsd, 0, 0, Blitter.COPY);
        // myimp.updateAndRepaintWindow();

    }

    /**
     * Description of the Method
     *
     * @param ip Description of the Parameter
     * @param x  Description of the Parameter
     * @param y  Description of the Parameter
     * @return Description of the Return Value
     */
    public double squareDifference(ImageProcessor ip, int x, int y) {
        double[] neightborhood = getNeightborhoodWithoutCentral(ip, x, y, vois, vois);
        double result = 0;
        double tmp;
        int compt = 0;
        double pixel = ip.getPixelValue(x, y);
        for (int i = 0; i < neightborhood.length; i++) {
            tmp = pixel - neightborhood[i];
            //result += tmp * tmp;
            result += tmp;
            compt++;
        }
        return result / compt;
    }

    /**
     * Gets the neighborhoodWithoutCentral attribute of the HotSpot_Detection
     * object
     *
     * @param ip    Description of the Parameter
     * @param x     Description of the Parameter
     * @param y     Description of the Parameter
     * @param voisx Description of the Parameter
     * @param voisy Description of the Parameter
     * @return The neighborhoodWithoutCentral value
     */
    public double[] getNeightborhoodWithoutCentral(ImageProcessor ip, int x, int y, int voisx, int voisy) {
        int size = (voisx * 2 + 1) * (voisy * 2 + 1) - 1;
        double[] t = new double[size];
        int index = 0;
        for (int j = y - voisy; j <= y + voisy; j++) {
            for (int i = x - voisx; i <= x + voisx; i++) {
                if (i != x || j != y) {
                    if (i >= 0 && j >= 0 && i < ip.getWidth() && j < ip.getHeight()) {
                        t[index] = ip.getPixelValue(i, j);
                        index++;
                    }
                }
            }
        }
        if (index < size) {
            double[] tab = new double[index];
            for (int i = 0; i < index; i++) {
                tab[i] = t[i];
            }
            return tab;
        }
        return t;
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
        GenericDialog gd = new GenericDialog("Mean square difference filter");
        gd.addNumericField("radius of neightborhood", vois, 0);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        vois = (int) gd.getNextNumber();
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
        vois = (int) gd.getNextNumber();
        return true;
    }


}

