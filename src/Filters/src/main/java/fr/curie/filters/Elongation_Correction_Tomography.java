package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Description of the Class
 *
 * @author MESSAOUDI C�dric
 * @created 7 janvier 2009
 */
public class Elongation_Correction_Tomography implements PlugInFilter {
    ImagePlus iplus;


    /**
     * Description of the Method
     *
     * @param arg Description of the Parameter
     * @param imp Description of the Parameter
     * @return Description of the Return Value
     */
    public int setup(String arg, ImagePlus imp) {
        iplus = imp;
        return DOES_ALL + STACK_REQUIRED + NO_CHANGES;
    }


    /**
     * Main processing method for the Elongation_Correction_Tomography object
     *
     * @param ip Description of the Parameter
     */
    public void run(ImageProcessor ip) {
        double thetamax = IJ.getNumber("maximum tilt angle (absolute value)", 60);
        if (thetamax == IJ.CANCELED) {
            return;
        }
        double thetamaxR = Math.toRadians(thetamax);
        double sint = Math.sin(thetamaxR);
        double cost = Math.cos(thetamaxR);
        double exz = Math.sqrt((thetamaxR + sint * cost) / (thetamaxR - sint * cost));

        IJ.log("elongation=" + exz);
        int nsz = (int) (iplus.getStackSize() / exz);
        IJ.log("new size in z" + nsz);

        ImageStack is = iplus.createEmptyStack();
        ImageStack oris = iplus.getImageStack();
        for (int i = 0; i < nsz; i++) {
            double z = i * exz;
            int zbase = (int) z;
            double zFraction = z - zbase;
            ImageProcessor ori1 = oris.getProcessor(zbase + 1);
            ImageProcessor ori2 = oris.getProcessor(zbase + 2);
            ImageProcessor tmp = ip.createProcessor(ip.getWidth(), ip.getHeight());
            is.addSlice("", tmp);
            for (int x = 0; x < tmp.getWidth(); x++) {
                for (int y = 0; y < tmp.getHeight(); y++) {
                    double pix1 = ori1.getPixelValue(x, y);
                    double pix2 = ori2.getPixelValue(x, y);
                    tmp.putPixelValue(x, y, pix1 + zFraction * (pix2 - pix1));
                }
            }
        }

        new ImagePlus(iplus.getTitle() + "_ElongationCorrected", is).show();
    }
}

