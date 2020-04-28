package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

import java.awt.*;

/**
 * Created by cedric on 10/02/14.
 */
public class Focus_Series_Simulation implements ExtendedPlugInFilter, DialogListener {
    double[] tiltAngles;
    double diameter = 50;
    ImagePlus myimp;
    int flags = CONVERT_TO_FLOAT + DOES_8G + DOES_16 + DOES_32;
    PlugInFilterRunner pfr;
    boolean preview;
    double centerX;

    /**
     * Description of the Method
     *
     * @param arg Description of the Parameter
     * @param imp Description of the Parameter
     * @return Description of the Return Value
     */
    public int setup(String arg, ImagePlus imp) {
        myimp = imp;
        tiltAngles = labels2Angles(imp.getStack());
        return flags;
    }

    /**
     * Main processing method for the HotSpot_Detection object
     *
     * @param ip Description of the Parameter
     */
    public void run(ImageProcessor ip) {
        for (int i = 0; i < myimp.getNSlices(); i++) {
            weighting(myimp.getStack().getProcessor(i + 1), tiltAngles[i], diameter, (tiltAngles[i] < 0) ? centerX : myimp.getWidth() - centerX);
        }
        myimp.resetDisplayRange();
        myimp.updateAndDraw();
    }

    private ImageProcessor weighting(ImageProcessor ip, double tilt, double diameter, double centerX) {
        float[] pixs = (float[]) ip.getPixels();
        int sx = ip.getWidth();
        int sy = ip.getHeight();
        double constPart = ip.getWidth() * Math.cos(Math.toRadians(tilt)) / 2;
        //doing filtering
        for (int j = 0; j < sy; j++) {
            int jj = j * sx;
            for (int i = 0; i < sx; i++) {
                double ci = i - centerX;
                ci = Math.abs(ci);
                double weight = (ci < constPart) ? 1 : (ci - constPart + 1);
                pixs[jj + i] = (float) average(ip, i, j, (int) (weight / diameter));
            }
        }

        return ip;
    }

    public double average(ImageProcessor ip, int x, int y, int radius) {
        double avg = 0;
        int compt = 0;
        for (int j = y - radius; j <= y + radius; j++) {
            int jj = Math.max(j, 0);
            jj = Math.min(jj, ip.getHeight() - 1);

            for (int i = x - radius; i <= x + radius; i++) {
                int ii = Math.max(i, 0);
                ii = Math.min(ii, ip.getWidth() - 1);

                avg += ip.getf(ii, jj);
                compt++;

            }

        }
        return avg / compt;
    }

    /**
     * gets the labels of slices in the given stackImage<BR>
     * tries to convert them to tilt angles information
     *
     * @param stack ImageStack containing the tilt series
     * @return the tilt angles for each image in the tilt series, null if
     * labels are not tilt angles
     */
    static public double[] labels2Angles(ImageStack stack) {
        double[] angles = new double[stack.getSize()];
        int i = 0;
        while (i < stack.getSize()) {
            String str = stack.getSliceLabel(i + 1);
            try {

                double tmp = Double.parseDouble(str);
                angles[i] = tmp;
                System.out.print(" " + tmp);
            } catch (Exception e) {
                return null;
            }
            i++;
        }
        System.out.println();

        return angles;
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
        gd.addNumericField("centerX", imp.getWidth() / 2.0, 1);
        gd.addNumericField("diameter", 10, 2);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        centerX = gd.getNextNumber();
        diameter = gd.getNextNumber();
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
        centerX = gd.getNextNumber();
        diameter = gd.getNextNumber();
        return true;
    }
}
