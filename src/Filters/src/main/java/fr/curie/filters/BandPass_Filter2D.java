package fr.curie.filters;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 15 janv. 2010
 * Time: 15:10:52
 * To change this template use File | Settings | File Templates.
 */
public class BandPass_Filter2D implements ExtendedPlugInFilter, DialogListener {
    ImagePlus myimp;
    double lowcut = 0;
    double lowpass = 1;
    double highpass = 100;
    double highcut = 101;
    int flags = CONVERT_TO_FLOAT + DOES_8G + DOES_16 + DOES_32 + PARALLELIZE_STACKS;
    PlugInFilterRunner pfr;
    boolean preview;


    public double[] getParameters() {
        return new double[]{lowcut, lowpass, highpass, highcut};
    }

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
        float[] pixs = (float[]) ip.getPixels();
        //binning and/or bandpass filter
        int sx = ip.getWidth();
        int sy = ip.getHeight();
        //FloatFFT_2D fft = new FloatFFT_2D(sx, sy);
        DenseFloatMatrix2D H1 = new DenseFloatMatrix2D(sy, sx);
        H1.assign(pixs);
        DenseFComplexMatrix2D fft = H1.getFft2();
        float[] fft1 = fft.elements();
        int size = sx * sy;
        //float[] fft1 = new float[pixs.length * 2];
        //System.arraycopy(pixs, 0, fft1, 0, size);
        //fft.realForwardFull(fft1);

        double cx = (sx) / 2;
        double cy = (sy) / 2;
        //doing filtering
        double auxmin = Math.PI / (lowpass - lowcut);
        double auxmax = Math.PI / (highcut - highpass);
        for (int j = 0; j < sy; j++) {
            int jj = j * sx;
            double cj = (j > cy) ? j - sy : j;
            for (int i = 0; i < sx; i++) {
                double ci = (i > cx) ? i - sx : i;
                double dist = Math.sqrt(ci * ci + cj * cj);
                if (dist >= lowcut && dist < lowpass) {
                    float tmp = (float) (1 + Math.cos((dist - lowpass) * auxmin)) * .5f;
                    fft1[(jj + i) * 2] *= tmp;
                    fft1[(jj + i) * 2 + 1] *= tmp;
                } else if (dist >= lowpass && dist <= highpass) {
                } else if (dist > highpass && dist <= highcut) {
                    float tmp = (float) (1 + Math.cos((dist - highpass) * auxmax)) * .5f;
                    fft1[(jj + i) * 2] *= tmp;
                    fft1[(jj + i) * 2 + 1] *= tmp;
                } else {
                    fft1[(jj + i) * 2] = 0;
                    fft1[(jj + i) * 2 + 1] = 0;
                }
            }
        }
        //fft.complexInverse(fft1, true);
        fft.ifft2(true);
        fft1 = fft.elements();
        //pixs = new float[size];
        for (int j = 0; j < pixs.length; j++) {
            pixs[j] = fft1[j * 2];
        }

        //ip.copyBits(result, 0, 0, Blitter.COPY);
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
        gd.addNumericField("low_cut (pixels)", 0, 2);
        gd.addNumericField("low_pass (pixels)", 1, 2);
        gd.addNumericField("high_pass (pixels)", 100, 2);
        gd.addNumericField("high_cut (pixels)", 101, 2);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        lowcut = gd.getNextNumber();
        lowpass = gd.getNextNumber();
        highpass = gd.getNextNumber();
        highcut = gd.getNextNumber();
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
        lowcut = gd.getNextNumber();
        lowpass = gd.getNextNumber();
        highpass = gd.getNextNumber();
        highcut = gd.getNextNumber();
        return true;
    }


}
