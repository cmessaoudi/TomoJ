package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.*;

import java.awt.*;

/**
 * Description of the Class
 *
 * @author MESSAOUDI C�dric
 * @created 26 f�vrier 2007
 */
public class HotSpot_Detection implements ExtendedPlugInFilter, DialogListener {
    //distribution table of student bilateral
    //for unilateral test use your alpha risk*2
    final static double[] student0_5 = {Double.NaN, 1, 0.8165, 0.7649, 0.7407, 0.7267, 0.7176, 0.7111, 0.7064, 0.7027, 0.6998, 0.6974, 0.6955, 0.6938, 0.6924, 0.6912, 0.6901, 0.6892, 0.6884, 0.6876, 0.687, 0.6864, 0.6858, 0.6853, 0.6848, 0.6844, 0.684, 0.6837, 0.6834, 0.683, 0.6828, 0.6825, 0.6822, 0.682, 0.6818, 0.6816, 0.6814, 0.6812, 0.681, 0.6808, 0.6807, 0.6805, 0.6804, 0.6802, 0.6801, 0.68, 0.6799, 0.6797, 0.6796, 0.6795, 0.6794, 0.6793, 0.6792, 0.6791, 0.6791, 0.679, 0.6789, 0.6788, 0.6787, 0.6787, 0.6786, 0.6785, 0.6785, 0.6784, 0.6783, 0.6783, 0.6782, 0.6782, 0.6781, 0.6781, 0.678, 0.678, 0.6779, 0.6779, 0.6778, 0.6778, 0.6777, 0.6777, 0.6776, 0.6776, 0.6776, 0.6775, 0.6775, 0.6775, 0.6774, 0.6774, 0.6774, 0.6773, 0.6773, 0.6773, 0.6772, 0.6772, 0.6772, 0.6771, 0.6771, 0.6771, 0.6771, 0.677, 0.677, 0.677, 0.677, 0.6745};
    final static double[] student0_2 = {Double.NaN, 3.0777, 1.8856, 1.6377, 1.5332, 1.4759, 1.4398, 1.4149, 1.3968, 1.383, 1.3722, 1.3634, 1.3562, 1.3502, 1.345, 1.3406, 1.3368, 1.3334, 1.3304, 1.3277, 1.3253, 1.3232, 1.3212, 1.3195, 1.3178, 1.3163, 1.315, 1.3137, 1.3125, 1.3114, 1.3104, 1.3095, 1.3086, 1.3077, 1.307, 1.3062, 1.3055, 1.3049, 1.3042, 1.3036, 1.3031, 1.3025, 1.302, 1.3016, 1.3011, 1.3007, 1.3002, 1.2998, 1.2994, 1.2991, 1.2987, 1.2984, 1.298, 1.2977, 1.2974, 1.2971, 1.2969, 1.2966, 1.2963, 1.2961, 1.2958, 1.2956, 1.2954, 1.2951, 1.2949, 1.2947, 1.2945, 1.2943, 1.2941, 1.2939, 1.2938, 1.2936, 1.2934, 1.2933, 1.2931, 1.2929, 1.2928, 1.2926, 1.2925, 1.2924, 1.2922, 1.2921, 1.292, 1.2918, 1.2917, 1.2916, 1.2915, 1.2914, 1.2912, 1.2911, 1.291, 1.2909, 1.2908, 1.2907, 1.2906, 1.2905, 1.2904, 1.2903, 1.2903, 1.2902, 1.2901, 1.2816};
    final static double[] student0_1 = {Double.NaN, 6.3137, 2.92, 2.3534, 2.1318, 2.015, 1.9432, 1.8946, 1.8595, 1.8331, 1.8125, 1.7959, 1.7823, 1.7709, 1.7613, 1.7531, 1.7459, 1.7396, 1.7341, 1.7291, 1.7247, 1.7207, 1.7171, 1.7139, 1.7109, 1.7081, 1.7056, 1.7033, 1.7011, 1.6991, 1.6973, 1.6955, 1.6939, 1.6924, 1.6909, 1.6896, 1.6883, 1.6871, 1.686, 1.6849, 1.6839, 1.6829, 1.682, 1.6811, 1.6802, 1.6794, 1.6787, 1.6779, 1.6772, 1.6766, 1.6759, 1.6753, 1.6747, 1.6741, 1.6736, 1.673, 1.6725, 1.672, 1.6716, 1.6711, 1.6706, 1.6702, 1.6698, 1.6694, 1.669, 1.6686, 1.6683, 1.6679, 1.6676, 1.6672, 1.6669, 1.6666, 1.6663, 1.666, 1.6657, 1.6654, 1.6652, 1.6649, 1.6646, 1.6644, 1.6641, 1.6639, 1.6636, 1.6634, 1.6632, 1.663, 1.6628, 1.6626, 1.6624, 1.6622, 1.662, 1.6618, 1.6616, 1.6614, 1.6612, 1.6611, 1.6609, 1.6607, 1.6606, 1.6604, 1.6602, 1.6449};
    final static double[] student0_05 = {Double.NaN, 12.7062, 4.3027, 3.1824, 2.7765, 2.5706, 2.4469, 2.3646, 2.306, 2.2622, 2.2281, 2.201, 2.1788, 2.1604, 2.1448, 2.1315, 2.1199, 2.1098, 2.1009, 2.093, 2.086, 2.0796, 2.0739, 2.0687, 2.0639, 2.0595, 2.0555, 2.0518, 2.0484, 2.0452, 2.0423, 2.0395, 2.0369, 2.0345, 2.0322, 2.0301, 2.0281, 2.0262, 2.0244, 2.0227, 2.0211, 2.0195, 2.0181, 2.0167, 2.0154, 2.0141, 2.0129, 2.0117, 2.0106, 2.0096, 2.0086, 2.0076, 2.0066, 2.0057, 2.0049, 2.004, 2.0032, 2.0025, 2.0017, 2.001, 2.0003, 1.9996, 1.999, 1.9983, 1.9977, 1.9971, 1.9966, 1.996, 1.9955, 1.9949, 1.9944, 1.9939, 1.9935, 1.993, 1.9925, 1.9921, 1.9917, 1.9913, 1.9908, 1.9905, 1.9901, 1.9897, 1.9893, 1.989, 1.9886, 1.9883, 1.9879, 1.9876, 1.9873, 1.987, 1.9867, 1.9864, 1.9861, 1.9858, 1.9855, 1.9852, 1.985, 1.9847, 1.9845, 1.9842, 1.984, 1.96};
    final static double[] student0_02 = {Double.NaN, 31.821, 6.9645, 4.5407, 3.7469, 3.3649, 3.1427, 2.9979, 2.8965, 2.8214, 2.7638, 2.7181, 2.681, 2.6503, 2.6245, 2.6025, 2.5835, 2.5669, 2.5524, 2.5395, 2.528, 2.5176, 2.5083, 2.4999, 2.4922, 2.4851, 2.4786, 2.4727, 2.4671, 2.462, 2.4573, 2.4528, 2.4487, 2.4448, 2.4411, 2.4377, 2.4345, 2.4314, 2.4286, 2.4258, 2.4233, 2.4208, 2.4185, 2.4163, 2.4141, 2.4121, 2.4102, 2.4083, 2.4066, 2.4049, 2.4033, 2.4017, 2.4002, 2.3988, 2.3974, 2.3961, 2.3948, 2.3936, 2.3924, 2.3912, 2.3901, 2.389, 2.388, 2.387, 2.386, 2.3851, 2.3842, 2.3833, 2.3824, 2.3816, 2.3808, 2.38, 2.3793, 2.3785, 2.3778, 2.3771, 2.3764, 2.3758, 2.3751, 2.3745, 2.3739, 2.3733, 2.3727, 2.3721, 2.3716, 2.371, 2.3705, 2.37, 2.3695, 2.369, 2.3685, 2.368, 2.3676, 2.3671, 2.3667, 2.3662, 2.3658, 2.3654, 2.365, 2.3646, 2.3642, 2.3263};
    final static double[] student0_01 = {Double.NaN, 63.6559, 9.925, 5.8408, 4.6041, 4.0321, 3.7074, 3.4995, 3.3554, 3.2498, 3.1693, 3.1058, 3.0545, 3.0123, 2.9768, 2.9467, 2.9208, 2.8982, 2.8784, 2.8609, 2.8453, 2.8314, 2.8188, 2.8073, 2.797, 2.7874, 2.7787, 2.7707, 2.7633, 2.7564, 2.75, 2.744, 2.7385, 2.7333, 2.7284, 2.7238, 2.7195, 2.7154, 2.7116, 2.7079, 2.7045, 2.7012, 2.6981, 2.6951, 2.6923, 2.6896, 2.687, 2.6846, 2.6822, 2.68, 2.6778, 2.6757, 2.6737, 2.6718, 2.67, 2.6682, 2.6665, 2.6649, 2.6633, 2.6618, 2.6603, 2.6589, 2.6575, 2.6561, 2.6549, 2.6536, 2.6524, 2.6512, 2.6501, 2.649, 2.6479, 2.6469, 2.6458, 2.6449, 2.6439, 2.643, 2.6421, 2.6412, 2.6403, 2.6395, 2.6387, 2.6379, 2.6371, 2.6364, 2.6356, 2.6349, 2.6342, 2.6335, 2.6329, 2.6322, 2.6316, 2.6309, 2.6303, 2.6297, 2.6291, 2.6286, 2.628, 2.6275, 2.6269, 2.6264, 2.6259, 2.5758};
    final static double[] student0_001 = {Double.NaN, 636.619, 31.598, 12.929, 8.61, 6.869, 5.959, 5.408, 5.041, 4.781, 4.587,
            4.437, 4.318, 4.221, 4.140, 4.073, 4.015, 3.965, 3.922, 3.883, 3.850,
            3.819, 3.792, 3.767, 3.745, 3.725, 3.707, 3.690, 3.674, 3.659, 3.646,
            3.551, 3.460, 3.373, 3.291};
    ImagePlus myimp;
    int vois = 1;
    boolean showimages = false;
    boolean showlocalisation = false;
    //ImageStack tophatstack;
    ImageStack hotspotstack;
    ImageStack msdstack;
    ImagePlus hsImp;
    ImagePlus msdImp;
    int index = 0;
    int flags = DOES_8G + DOES_16 + DOES_32 + PARALLELIZE_STACKS;
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
        ImageProcessor hotspot = new ByteProcessor(ip.getWidth(), ip.getHeight());
        ImageProcessor lsd = new FloatProcessor(ip.getWidth(), ip.getHeight());
        //ImageProcessor tophat = topHat(ip, vois, vois, voistype, lightobject);
        boolean change = true;
        //iterative process
        while (change) {
            change = false;
            //compute mean square difference between central pixel and neightborhood
            for (int j = 0; j < ip.getHeight(); j++) {
                for (int i = 0; i < ip.getWidth(); i++) {
                    //lsd.putPixelValue(i, j, squareDifference(tophat, i, j));
                    lsd.setf(i, j, (float)squareDifference(ip, i, j, hotspot));
                }
            }
            ImagePlus imp2 = new ImagePlus("mean square difference ", lsd.duplicate());
            ImageStatistics stats = imp2.getStatistics(ImageStatistics.MEAN + ImageStatistics.MIN_MAX + ImageStatistics.STD_DEV + ImageStatistics.MEDIAN);
            double seuil = stats.histMax;
            int zero = 0;
            //computing threshold from histogram
            for (int i = 0; i < stats.histogram.length; i++) {
                if (stats.histogram[i] == 0) {
                    zero++;
                } else {
                    zero = 0;
                }
                if (zero == 3) {
                    seuil = stats.histMin + i * stats.binSize;
                    break;
                }
            }
            if (showimages || showlocalisation) {
                IJ.log(" histo min " + stats.histMin + " histo max " + stats.histMax + " binsize " + stats.binSize);
                IJ.log(" histo stddev " + stats.stdDev);
                IJ.log(" seuil " + seuil);
            }
            //put pixels above theshold in the mask of hotspots
            for (int j = 0; j < ip.getHeight(); j++) {
                for (int i = 0; i < ip.getWidth(); i++) {
                    if (lsd.getf(i, j) > seuil) {
                        change = true;
                        hotspot.setf(i, j, 255);
                    }
                }
            }
        }
        //while
        //changing aberrant pixel with mean value
        ImageProcessor ip2 = ip.duplicate();
        int count=0;
        for (int j = 0; j < ip.getHeight(); j++) {
            for (int i = 0; i < ip.getWidth(); i++) {
                if (hotspot.getf(i, j) > 0) {
                    if (showlocalisation) {
                        IJ.log("image #" + ind + " hotspot in position (" + i + ", " + j + ")");
                    }
                    ip2.setf(i, j, (float)newValue(ip, i, j, vois, vois, hotspot));
                    count++;
                }
            }
        }
        ip.copyBits(ip2, 0, 0, Blitter.COPY);
        if (showimages) {
            if (preview) {
                new ImagePlus("msd of " + myimp.getTitle(), lsd).show();
                new ImagePlus("hotspot of " + myimp.getTitle(), hotspot).show();
            } else {
                msdstack.getProcessor(ind).copyBits(lsd, 0, 0, Blitter.COPY);
                hotspotstack.getProcessor(ind).copyBits(hotspot, 0, 0, Blitter.COPY);
            }
            if (hsImp != null) {
                hsImp.updateAndRepaintWindow();
                msdImp.updateAndRepaintWindow();
            }
        }
        System.out.println("image #"+ind+" : "+count+" aberrant pixels detected");
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
        GenericDialog gd = new GenericDialog("HotSpot detection");
        gd.addNumericField("radius of neightborhood", vois, 0);
        gd.addCheckbox("show points coordinates", showlocalisation);
        gd.addCheckbox("show intermediate images", showimages);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        vois = (int) gd.getNextNumber();
        showlocalisation = gd.getNextBoolean();
        showimages = gd.getNextBoolean();
        if (showimages) {
            msdstack = new ImageStack(myimp.getWidth(), myimp.getHeight());
            hotspotstack = new ImageStack(myimp.getWidth(), myimp.getHeight());
            for (int i = 0; i < myimp.getStackSize(); i++) {
                hotspotstack.addSlice("", new ByteProcessor(imp.getWidth(), imp.getHeight()));
                msdstack.addSlice("", new FloatProcessor(imp.getWidth(), imp.getHeight()));
            }
            msdImp = new ImagePlus("msd of " + myimp.getTitle(), msdstack);
            msdImp.show();
            hsImp = new ImagePlus("hotspot of " + myimp.getTitle(), hotspotstack);
            hsImp.show();
        } else {
            msdstack = null;
            hotspotstack = null;
        }
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
        showlocalisation = gd.getNextBoolean();
        showimages = gd.getNextBoolean();
        if (showimages && msdstack == null) {
            msdstack = new ImageStack(myimp.getWidth(), myimp.getHeight());
            hotspotstack = new ImageStack(myimp.getWidth(), myimp.getHeight());
        } else {
            msdstack = null;
            hotspotstack = null;
        }
        return true;
    }

    /**
     * Description of the Method
     *
     * @param ip    Description of the Parameter
     * @param i     Description of the Parameter
     * @param j     Description of the Parameter
     * @param voisx Description of the Parameter
     * @param voisy Description of the Parameter
     * @param mask  Description of the Parameter
     * @return Description of the Return Value
     */
    public double newValue(ImageProcessor ip, int i, int j, int voisx, int voisy, ImageProcessor mask) {
        double sum = 0;
        int compt = 0;
        for (int y = j - voisy; y <= j + voisy; y++) {
            for (int x = i - voisx; x <= i + voisx; x++) {
                if (i != x || j != y) {
                    if (x >= 0 && x >= 0 && x < ip.getWidth() && x < ip.getHeight() && mask.getPixelValue(x, y) == 0) {
                        sum += ip.getPixelValue(x, y);
                        compt++;
                    }
                }
            }
        }
        if (compt == 0) {
            return newValue(ip, i, j, voisx + 1, voisy + 1, mask);
        }
        return sum / compt;
    }


    /**
     * Description of the Method
     *
     * @param ip   Description of the Parameter
     * @param x    Description of the Parameter
     * @param y    Description of the Parameter
     * @param mask Description of the Parameter
     * @return Description of the Return Value
     */
    public double squareDifference(ImageProcessor ip, int x, int y, ImageProcessor mask) {
        if (mask.getPixelValue(x, y) != 0) {
            return 0;
        }
        double[] neightborhood = getNeightborhoodWithoutCentral(ip, x, y, vois, vois);
        double[] maskN = getNeightborhoodWithoutCentral(mask, x, y, vois, vois);
        double result = 0;
        double tmp;
        int compt = 0;
        double pixel = ip.getf(x, y);
        for (int i = 0; i < neightborhood.length; i++) {
            if (maskN[i] == 0) {
                tmp = pixel - neightborhood[i];
                result += tmp * tmp;
                compt++;
            }
        }
        return result / compt;
    }


    /**
     * Gets the hotSpot attribute of the HotSpot_Detection object
     *
     * @param ip Description of the Parameter
     * @param x  Description of the Parameter
     * @param y  Description of the Parameter
     * @return The hotSpot value
     */
    public boolean isHotSpot(final ImageProcessor ip, final int x, final int y) {
        final double[] neighbor = getNeightborhoodWithoutCentral(ip, x, y, vois / 2, vois / 2);
        final double m0 = ip.getPixelValue(x, y);
        final int ddl = neighbor.length - 1;
        final double avg = average(neighbor);
        final double var = variance(neighbor, avg);
        double t = (avg - m0) / var * Math.sqrt(ddl);
        double Tv = getStudent(0.001, ddl);
        if (t > Tv) {
            return true;
        }
        return false;
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
     * @param tab Description of the Parameter
     * @return Description of the Return Value
     */
    public double average(final double[] tab) {
        double avg = 0;
        for (int i = tab.length - 1; i >= 0; i--) {
            avg += tab[i];
        }
        return avg / tab.length;
    }

    /**
     * Description of the Method
     *
     * @param tab Description of the Parameter
     * @param avg Description of the Parameter
     * @return Description of the Return Value
     */
    public double variance(final double[] tab, final double avg) {
        double sum = 0;
        double tmp;
        for (int i = tab.length - 1; i >= 0; i--) {
            tmp = tab[i] - avg;
            sum+=tmp*tmp;
        }
        return sum/tab.length;
    }

    /**
     * Gets the student attribute of the DynamicMedian2D_ object
     *
     * @param ddl   Description of the Parameter
     * @param alpha Description of the Parameter
     * @return The student value
     */
    public double getStudent(double alpha, int ddl) {
        if (alpha == 0.001) {
            if (ddl > 30) {
                if (ddl <= 40) {
                    ddl = 31;
                } else if (ddl <= 80) {
                    ddl = 32;
                } else if (ddl < 120) {
                    ddl = 33;
                } else {
                    ddl = 34;
                }
            }
            return student0_001[ddl];
        } else {
            if (ddl > 101) {
                ddl = 101;
            }
            if (alpha == 0.5) {
                return student0_5[ddl];
            } else if (alpha == 0.2) {
                return student0_2[ddl];
            } else if (alpha == 0.1) {
                return student0_1[ddl];
            } else if (alpha == 0.05) {
                return student0_05[ddl];
            } else if (alpha == 0.02) {
                return student0_02[ddl];
            } else if (alpha == 0.01) {
                return student0_01[ddl];
            }
        }
        return Double.NaN;
    }
}

