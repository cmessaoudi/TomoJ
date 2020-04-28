package fr.curie.tomoj;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import fr.curie.tomoj.landmarks.AlignmentLandmarkDualImproved;
import fr.curie.tomoj.tomography.TiltSeries;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageStatistics;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;


/**
 * SuperTiltSeries stores multiple TiltSeries for multiple tilt series alignment and
 * reconstruction.
 *
 * Warning : for now this only works with two TiltSeries.
 *
 * @author Antoine COSSA
 * Date: 11 apr. 2019
 */
public class SuperTiltSeries extends ImagePlus {
    //    /**
//     * fill blanks in aligned images with zeros
//     */
//    public final static int FILL_NONE = 0;
//    /**
//     * fill blanks in aligned images with the average of the image
//     */
//    public final static int FILL_AVG = 1;
//    /**
//     * fill blanks in aligned images with NaN (used in ART/SIRT reconstruction to say you have no information)
//     */
//    public final static int FILL_NaN = 2;
//    public final static int ZERO_ONE = 1;
//    public final static int ELECTRON_TOMO = 3;
//    public final static int ZERO_ONE_COMMONFIELD = 2;
//
//
//    public final static int ALIGN_AFFINE2D = 20;
//    public final static int ALIGN_NONLINEAR = 21;
//    public final static int ALIGN_PROJECTOR = 22;
//
//    protected int alignMethodForReconstruction = ALIGN_AFFINE2D;
//
    private double[] tiltAngles; // arrays with tilt angles for each images
    //    protected double centerx, centery; // center of rotation coordinates
    // parameter for getPixels functions
    protected boolean applyT, normalize; // apply Transforms, normalize the images
    //    protected boolean combineTransforms; // combine transforms if false global transforms
    protected boolean tiltAxisVertical = false; // set tilt axis vertical
    protected boolean updatePoints = true;
    protected AlignmentLandmarkDualImproved alignment;
    protected int binning; // value of binning
    //    protected int roisx, roisy; //roi width and height
//    protected int varianceFilterSize = -1; //variance filter radius
//    protected boolean integerTranslation = true;
//    protected int completion;
//    protected double projectionCenterX, projectionCenterY;
//    protected ImageStatistics[] stats;
    protected ImageStack data;
    protected double min = Double.NaN;
    protected double max = Double.NaN;
    //    protected AffineTransform zeroT;
//    protected TomoJPoints tp;
    protected DoubleMatrix2D[] eulerMatrices;
//    protected int normalizationType;
//    protected int normalizationMinSize;
    protected boolean showTiltAxis;
    protected boolean showInIJ;
    protected double Threshold = Double.MIN_VALUE;
    protected double ThresholdHisteresis = Double.MIN_VALUE;
    protected boolean applyThreshold = false;
//    protected boolean expand = false;
//    protected double[] alignmentDual = null;
//    protected float[][] alignmentDualPixsTmp;
//    //protected String path;
//    int nbcpu = Prefs.getThreads();
//
//
    /**
     * List of all TiltSeries
     */
    private ArrayList<TiltSeries> tsList;
    /**
     * Number of images in the i-th TiltSeries
     */
    private ArrayList<Integer> nImagesi;
    /**
     * Total number of images
     */
    private int nImages;

    private SuperTomoJPoints stp;

    public SuperTiltSeries(ArrayList<TiltSeries> tsList) {
        this.tsList = tsList;
        init();
    }

    public SuperTiltSeries(SuperTomoJPoints stp) {
        this.stp = stp;
        tsList = new ArrayList<>();
        for (int i = 0; i < stp.getSize(); i++) {
            tsList.add(stp.getTomoJPoints(i).getTiltSeries());
        }
        init();
    }

    private void init() {
        nImages = 0;
        nImagesi = new ArrayList<>(tsList.size());

        for (TiltSeries ts : tsList) {
            // Gets the number of image in the TiltSeries and adds it to the
            // total number of images.
            int n = ts.getStackSize();
            nImages += n;
            nImagesi.add(n);
        }
    }

    public void setAlignment(AlignmentLandmarkDualImproved align){ this.alignment=align;}

    public AlignmentLandmarkDualImproved getAlignment() { return alignment;}

    public void updateInternalData() {
        for (TiltSeries ts : tsList) {
            ts.updateInternalData();
        }
    }

    public ArrayList<TiltSeries> getAllTS() { return tsList; }

    public TiltSeries getTiltSeries(int index){ return tsList.get(index); }

//    public static float[] getPixels(float[] imgpix, AffineTransform T, int width, int height, boolean normalize, int fill) {
//        return getPixels(imgpix, T, new FloatStatistics(new FloatProcessor(width, height, imgpix, null)), width, height, normalize, fill);
//    }

    public static float[] getSubImagePixels(final float[] imgpix, final AffineTransform Tinv, final ImageStatistics stats, final int width, final int height, final int newWidth, final int newHeight, final Point2D coordCentered, final boolean normalize) {
        return getSubImagePixels(imgpix, Tinv, stats, width, height, newWidth, newHeight, coordCentered, normalize, false);
    }

    public static float[] getSubImagePixels(final float[] imgpix, final AffineTransform Tinv, final ImageStatistics stats, final int width, final int height, final int newWidth, final int newHeight, final Point2D coordCentered, final boolean normalize, final boolean fillNaN) {
        double scx = (newWidth) / 2.0;
        double scy = (newHeight) / 2.0;
        double centerx = width / 2;
        double centery = height / 2;
        final float[] piece = new float[newWidth * newHeight];
        int jj;
        double y, yy, xx;
        int ix0, iy0;
        double fac4, fac1, fac2, fac3;
        float value1, value2, value3, value4;
        double dx0, dy0;
        double pix;
        //Point2D tmp;
        //Point2D res;
        //AffineTransform Tinv = getCombinedInverseTransform(index);
        //final float[] imgpix = (float[]) data.getPixels(index + 1);
        /*if (stats[index] == null) {
            //stats[index] = new FloatStatistics(new FloatProcessor(width, height, imgpix, null));
            stats[index] = getImageStatistics(index);
        }*/
        final double[] line = new double[newWidth * 2];
        final double[] res = new double[newWidth * 2];

        for (int i = 0; i < newWidth; i++) {
            line[i * 2] = i + coordCentered.getX() - scx;
        }

        for (int j = 0; j < newHeight; j++) {
            jj = j * newWidth;
            y = j + coordCentered.getY() - scy;
            for (int i = 0; i < newWidth; i++) {
                //x = i + coordCentered.getX() - scx;
                line[i * 2 + 1] = y;
            }
            Tinv.transform(line, 0, res, 0, newWidth);
            for (int i = 0; i < newWidth; i++) {
                xx = res[i * 2] + centerx;
                yy = res[i * 2 + 1] + centery;
                ix0 = (int) xx;
                iy0 = (int) yy;
                dx0 = xx - ix0;
                dy0 = yy - iy0;

                //System.out.println("before T ("+tmp.getX()+", "+tmp.getY()+")  after ("+res.getX()+", "+res.getY()+") ");
                if (ix0 >= 0 && ix0 < width && iy0 >= 0 && iy0 < height) {
                    //en bas a gauche
                    if (ix0 == width - 1 || iy0 == height - 1) {
                        pix = imgpix[ix0 + iy0 * width];

                    } else {
                        fac4 = (dx0 * dy0);
                        fac1 = (1 - dx0 - dy0 + fac4);
                        fac2 = (dx0 - fac4);
                        fac3 = (dy0 - fac4);

                        value1 = imgpix[ix0 + iy0 * width];
                        value2 = imgpix[ix0 + 1 + iy0 * width];
                        value3 = imgpix[ix0 + (iy0 + 1) * width];
                        value4 = imgpix[ix0 + 1 + (iy0 + 1) * width];
                        pix = value1 * fac1 + value2 * fac2 + value3 * fac3 + value4 * fac4;

                    }
                    if (normalize) {
                        pix = (float) ((pix - stats.mean) / stats.stdDev);
                    }
                    //result.putPixelValue(i, j, pix);
                    piece[jj + i] = (float) pix;

                } else {
                    if (fillNaN) piece[jj + i] = Float.NaN;
                    else piece[jj + i] = (normalize) ? 0 : (float) stats.mean;
                }


            }
        }
        return piece;
    }


    /**
     * exports the aligned image to a file
     *
     * @param sts      the super tilt series to export
     * @param dir      directory where to save the images
     * @param filename name of the file <BR>
     *                 <UL>
     *                 <LI>filename finish with mrc: the file is saved in MRC format </LI>
     *                 <LI>filename finish with spi: the file is saved in Spider format </LI>
     *                 <LI>filename finish with xmp: the file is saved in spider format </LI>
     *                 <LI>filename finish with sel: the file is saved in multiple files in spider format and a sel file is created (text file with the list of images files)</LI>
     *                 <LI>filename finish with anything else: the file is saved in tif format </LI>
     *                 </UL>
     */
    public void exportAlignedImages(SuperTiltSeries sts, String dir, String filename) {
        for (TiltSeries ts : tsList) {
            ts.exportAlignedImages(ts, dir, filename);
        }
    }

    public SuperTomoJPoints getSuperTomoJPoints() {
        return stp;
    }

    public void setSuperTomoJPoints(SuperTomoJPoints stp) {
        this.stp = stp;
    }

    public void updatePoints(boolean value) {
        for (TiltSeries ts : tsList) {
            ts.updatePoints(value);
        }
        updatePoints = value;
    }

    /**
     * to display or not things in ImageJ
     *
     * @param value true to display in IJ
     */
    public void setShowInImageJ(boolean value) {
        for (TiltSeries ts : tsList) {
            ts.setShowInImageJ(value);
        }
        showInIJ = value;
    }

//    /**
//     * set the tilt angles of all the images in the tilt series
//     *
//     * @param anglesDeg array of angles in degree
//     */
//    public void setTiltAngles(double[] anglesDeg) {
//        tiltAngles = anglesDeg;
//        updateZeroIndex();
//        eulerMatrices = new DoubleMatrix2D[data.getSize()];
//        for(int i=0;i<eulerMatrices.length;i++) eulerMatrices[i]=null;
//    }

    public void updateZeroIndex() {
        for (TiltSeries ts : tsList) {
            ts.updateZeroIndex();
        }
    }

    /**
     * set the tilt angle of an image in the tilt series
     *
     * @param index    image number
     * @param angleDeg tilt angle in degree
     */
    public void setTiltAngle(int index, double angleDeg) {
        int offset = 0;
        for (TiltSeries ts : tsList) {
            int size = ts.getStackSize();
            if (index >= size) {
                offset += size;
            } else if (index >= offset && index < size) {
                ts.setTiltAngle(index, angleDeg);
            }
        }
    }

    public double[] getTiltAngles() {
        if (tiltAngles == null) {
            double[] tiltAngles = new double[nImages];
            int index = 0;
            for (TiltSeries ts : tsList) {
                System.arraycopy(ts.getTiltAngles(), index, tiltAngles, index, ts.getTiltAngles().length);
                index += ts.getStackSize();
            }
            this.tiltAngles = tiltAngles;
        }
        return tiltAngles;
    }

    public double getTiltAxis(int index) {
        int offset = 0;
        for (TiltSeries ts : tsList) {
            int size = ts.getStackSize();
            if (index >= size) {
                offset += size;
            } else if (index >= offset && index < size) {
                return ts.getTiltAxis(index);
            }
        }
        return 0;
    }

    /**
     * set a threshold below which there will be NaN instead of the true values (added for reconstruction without gold beads)
     *
     * @param value threshold level
     * @param apply true to apply the given threshold
     */
    public void setThreshold(double value, boolean apply) {
        for (TiltSeries ts : tsList) {
            ts.setThreshold(value, apply);
        }
        Threshold = value;
        applyThreshold = apply;
    }

    public void setThresholdHisteresis(double value1, double value2, boolean apply) {
        for (TiltSeries ts : tsList) {
            ts.setThresholdHisteresis(value1, value2, apply);
        }
        Threshold = value1;
        ThresholdHisteresis = value2;
        applyThreshold = apply;
    }

    /**
     * @param index of the TiltSeries in tsList
     * @return center x of the corresponding TiltSeries
     */
    public double getCenterX(int index) {
        return tsList.get(index).getCenterX();
    }

    /**
     * @param index of the TiltSeries in tsList
     * @return center y of the corresponding TiltSeries
     */
    public double getCenterY(int index) {
        return tsList.get(index).getCenterY();
    }

    /**
     * @param index of the TiltSeries in tsList
     * @param cx    Center x
     * @param cy    Center y
     */
    public void setCenter(int index, double cx, double cy) {
        tsList.get(index).setCenter(cx, cy);
    }

    public void putTiltAxisVertical(boolean value) {
        for (TiltSeries ts : tsList) {
            ts.putTiltAxisVertical(value);
        }
//        tiltAxisVertical = value;
//        eulerMatrices = new DoubleMatrix2D[data.getSize()];
//        for (int i = 0; i < eulerMatrices.length; i++) eulerMatrices[i] = null;
    }

    /**
     * @param index of the TiltSeries in tsList
     * @return
     */
    public boolean isPutTiltAxisVertical(int index) {
        return tsList.get(index).isPutTiltAxisVertical();
    }

    /**
     * set the normalization
     *
     * @param value the images are normalized (0,1)
     */
    public void setNormalize(boolean value) {
        for (TiltSeries ts : tsList) {
            ts.setNormalize(value);
        }
        if (value != normalize) {
            min = Double.NaN;
            max = Double.NaN;
            normalize = value;
        }
    }

    /**
     * are the images normalized?
     *
     * @param index of the TiltSeries in tsList
     * @return true if normalized, false if not
     */
    public boolean isNormalized(int index) {
        return tsList.get(index).isNormalized();
    }

    public void resetEulerMatrices() {
        for (TiltSeries ts : tsList) {
            ts.getAlignment().resetEulerMatrices();
        }
    }


}
