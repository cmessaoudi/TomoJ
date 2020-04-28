package fr.curie.tomoj.tomography;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import fr.curie.tomoj.align.AffineAlignment;
import fr.curie.tomoj.align.Alignment;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.*;
import ij.macro.Interpreter;
import ij.plugin.frame.ContrastAdjuster;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import fr.curie.utils.Chrono;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * TiltSeries is the main class where the tilt series is kept<BR>
 * it extends ImagePlus and so is displayable in ImageJ. Original data is kept untouched, only displayed image is aligned and normalized (via overriding setSlice)<BR>
 * it keeps also the tilt angles, the tilt axis, the transformation matrices to apply between consecutive images and the landmarks on each image <BR>
 * it produce aligned images. <BR>
 * it allow computation of transformation matrices by <ul>
 * <li>cross correlation </li>
 * <li>affine transformation</li>
 * <li>centering the barycenter of landmarks on each image</li>
 * <li>generate landmarks automatically and align them</li>
 * </ul>
 * it can give the 3D euler matrix for each image to apply for 3D reconstruction
 * User: MESSAOUDI C�dric
 * Date: 18 nov. 2008
 * Time: 11:35:15
 */
// TODO: Integrate Stackview features into TiltSeries
public class TiltSeries extends ImagePlus {
    /**
     * fill blanks in aligned images with zeros
     */
    public final static int FILL_NONE = 0;
    /**
     * fill blanks in aligned images with the average of the image
     */
    public final static int FILL_AVG = 1;
    /**
     * fill blanks in aligned images with NaN (used in ART/SIRT reconstruction to say you have no information)
     */
    public final static int FILL_NaN = 2;
    public final static int ZERO_ONE = 1;
    public final static int ELECTRON_TOMO = 3;
    public final static int ZERO_ONE_COMMONFIELD = 2;


    public final static int ALIGN_AFFINE2D = 20;
    public final static int ALIGN_NONLINEAR = 21;
    public final static int ALIGN_PROJECTOR = 22;

    //    protected int alignMethodForReconstruction = ALIGN_AFFINE2D;
    // TEST by Antoine
    public int alignMethodForReconstruction = ALIGN_AFFINE2D;

    protected double[] tiltAngles; // arrays with tilt angles for each images
    protected double tiltaxis; // general tilt axis of tilt series
    protected double centerx, centery; // center of rotation coordinates
    //parameter for getPixels functions
    protected boolean applyT, normalize; // apply Transforms, normalize the images
    protected boolean combineTransforms; // combine transforms if false global transforms
    protected boolean tiltAxisVertical = false; // set tilt axis vertical
    protected boolean updatePoints = true;
    protected int zeroindex; // index of nearest to zero degree tilt
    //protected AffineTransform[] transform; //transforms to apply directly or after combination for each image
    //protected AffineTransform[] invTransform;  // store the inverse transforms used to apply the transforms to compute them only once (have to be put to null after every change of any transform - done when using setTransform or addTransform functions
    protected int fill; // fill with what? see the static fields starting with FILL_****
    //parameters for getPixelsForAlignment
    protected double bandpassLowCut, bandpassHighCut, bandpassLowkeep, bandpassHighKeep; // parameters for bandpass filtering
    protected int binning; // value of binning
    protected int roisx, roisy; //roi width and height
    protected int varianceFilterSize = -1; //variance filter radius
    protected boolean integerTranslation = true;
    protected int completion;
    protected double projectionCenterX, projectionCenterY;
    protected ImageStatistics[] stats;
    protected ImageStack data;
    protected double min = Double.NaN;
    protected double max = Double.NaN;
    //protected AffineTransform zeroT;
    protected TomoJPoints tp;
    //protected DoubleMatrix2D[] eulerMatrices;
    protected int normalizationType;
    protected int normalizationMinSize;
    protected boolean showTiltAxis;
    protected boolean showInIJ;
    protected double Threshold = Double.MIN_VALUE;
    protected double ThresholdHisteresis = Double.MIN_VALUE;
    protected boolean applyThreshold = false;
    protected boolean expand = false;
    //public double[] alignmentDual = null;
    //protected float[][] alignmentDualPixsTmp;
    //protected String path;
    int nbcpu = Prefs.getThreads();
    protected Alignment alignment;


    public TiltSeries(ImagePlus imp) {
        this(imp, new double[imp.getStackSize()]);
    }

    public TiltSeries(ImagePlus imp, double[] tiltangles) {
        super(imp.getTitle(), imp.getImageStack());
        //path=imp.getFileInfo().directory;
        tiltAngles = tiltangles;
        firstInit();
    }

    private void firstInit() {
        tiltaxis = 0;
        updateInternalData();
        applyT = true;
        combineTransforms = true;
        normalize = true;
        normalizationType = ZERO_ONE;
        fill = FILL_AVG;
        bandpassLowCut = Double.NaN;
        bandpassHighCut = Double.NaN;
        bandpassLowkeep = Double.NaN;
        bandpassHighKeep = Double.NaN;
        binning = 1;
        roisx = 0;
        roisy = 0;
        //transform = new AffineTransform[getStackSize()];
        //invTransform = new AffineTransform[getStackSize()];
        alignment=new AffineAlignment(this,combineTransforms);
        //zeroT = new AffineTransform();
        zeroindex = 0;
        double tmp = Math.abs(tiltAngles[0]);
        stats = new ImageStatistics[getStackSize()];
        for (int i = 0; i < getStackSize(); i++) {
            //transform[i] = new AffineTransform();
            //invTransform[i] = null;
            if (Math.abs(tiltAngles[i]) < tmp) {
                tmp = Math.abs(tiltAngles[i]);
                zeroindex = i;
                //System.out.println("zero index=" + zeroindex);
            }
            data.setSliceLabel("" + tiltAngles[i], i + 1);
        }
        //eulerMatrices = new DoubleMatrix2D[data.getSize()];
        //for (int i = 0; i < eulerMatrices.length; i++) eulerMatrices[i] = null;
        showInIJ = true;

    }

    public void updateInternalData() {
        data = getImageStack();
        width = data.getWidth();
        height = data.getHeight();
        centerx = (width - 1.0) / 2;
        centery = (height - 1.0) / 2;
        projectionCenterX = (width - 1) / 2.0;
        projectionCenterY = (height - 1) / 2.0;

    }

    public static float[] getPixels(float[] imgpix, AffineTransform T, int width, int height, boolean normalize, int fill) {
        return getPixels(imgpix, T, new FloatStatistics(new FloatProcessor(width, height, imgpix, null)), width, height, normalize, fill);
    }

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
     * @param ts       the tilt series to export
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
    public static void exportAlignedImages(TiltSeries ts, String dir, String filename) {
        String nameLC = filename.toLowerCase();
        if (nameLC.endsWith(".sel")) {
            IJ.runPlugIn(ts, "Sel_Writer", dir + filename);
        } else {
            ImageStack is = new ImageStack(ts.getWidth(), ts.getHeight());
            for (int i = 0; i < ts.getStackSize(); i++) {
                is.addSlice("" + ts.getTiltAngle(i), ts.getPixels(i));
                IJ.showStatus("creating aligned stack " + (i + 1) + "/" + ts.getStackSize());
            }
            ImagePlus imp = new ImagePlus(filename, is);

            if (nameLC.endsWith(".mrc")) {
                IJ.runPlugIn(imp, "MRC_Writer", dir + filename);
            } else if (nameLC.endsWith(".xmp") || nameLC.endsWith(".spi")) {
                IJ.runPlugIn(imp, "Spider_Writer", dir + filename);
            } else {
                imp.show();
                IJ.selectWindow(filename);
                IJ.save(dir + filename);
                imp.close();
            }
        }
    }


    public TomoJPoints getTomoJPoints() {
        return tp;
    }

    public void setTomoJPoints(TomoJPoints tp) {
        this.tp = tp;
    }

    public void updatePoints(boolean value) {
        updatePoints = value;
    }

    /**
     * to display or not things in ImageJ
     * @param value true to display in IJ
     */
    public void setShowInImageJ(boolean value) {
        showInIJ = value;
    }

    /**
     * set the tilt angles of all the images in the tilt series
     * @param anglesDeg array of angles in degree
     */
    public void setTiltAngles(double[] anglesDeg) {
        tiltAngles = anglesDeg;
        updateZeroIndex();
        alignment.resetEulerMatrices();
        alignment.setZeroIndex(zeroindex);
        //eulerMatrices = new DoubleMatrix2D[data.getSize()];
        //for (int i = 0; i < eulerMatrices.length; i++) eulerMatrices[i] = null;
    }

    public void updateZeroIndex() {
        double tmp = Math.abs(tiltAngles[0]);
        ImageStack is = getStack();
        for (int i = 0; i < getStackSize(); i++) {
            if (Math.abs(tiltAngles[i]) < tmp) {
                tmp = Math.abs(tiltAngles[i]);
                zeroindex = i;
                //System.out.println("zero index=" + zeroindex);
            }
            is.setSliceLabel("" + tiltAngles[i], i + 1);
        }
        if(alignment!=null) alignment.setZeroIndex(zeroindex);
    }

    /**
     * set the tilt angle of an image in the tilt series
     * @param index    image number
     * @param angleDeg tilt angle in degree
     */
    public void setTiltAngle(int index, double angleDeg) {
        tiltAngles[index] = angleDeg;
        updateZeroIndex();
        alignment.resetEulerMatrices();
        alignment.setZeroIndex(zeroindex);
        //eulerMatrices = new DoubleMatrix2D[data.getSize()];
        //for (int i = 0; i < eulerMatrices.length; i++) eulerMatrices[i] = null;
    }

    public double[] getTiltAngles() {
        return tiltAngles;
    }

    public double getTiltAxis(int index) {
        return tiltaxis;
    }

    /**
     * set a threshold below which there will be NaN instead of the true values (added for reconstruction without gold beads)
     * @param value threshold level
     * @param apply true to apply the given threshold
     */
    public void setThreshold(double value, boolean apply) {
        Threshold = value;
        applyThreshold = apply;
    }

    public void setThresholdHisteresis(double value1, double value2, boolean apply) {
        Threshold = value1;
        ThresholdHisteresis = value2;
        applyThreshold = apply;
    }

    public double getCenterX() {
        return centerx;
    }

    public double getCenterY() {
        return centery;
    }

    public void setCenter(double cx, double cy) {
        centerx = cx;
        centery = cy;
    }

    public void putTiltAxisVertical(boolean value) {
        tiltAxisVertical = value;
        alignment.resetEulerMatrices();
        //eulerMatrices = new DoubleMatrix2D[data.getSize()];
        //for (int i = 0; i < eulerMatrices.length; i++) eulerMatrices[i] = null;
    }

    public boolean isPutTiltAxisVertical() {
        return tiltAxisVertical;
    }

    /**
     * set the normalization
     * @param value the images are normalized (0,1)
     */
    public void setNormalize(boolean value) {
        if (value != normalize) {
            min = Double.NaN;
            max = Double.NaN;
            normalize = value;
        }
    }

    /**
     * are the images normalized?
     * @return true if normalized, false if not
     */
    public boolean isNormalized() {
        return normalize;
    }

    public int getNormalizationType() {
        return normalizationType;
    }

    public void setNormalizationType(int value) {
        if (normalizationType != value) {
            normalizationType = value;
            resetNormalizationStats();
        }
        if (normalizationType == ELECTRON_TOMO || normalizationType == ZERO_ONE_COMMONFIELD) {
            setNormalizationMinSize(0);
        } else {
            setNormalizationMinSize(Math.max(getWidth(), getHeight()));
        }

    }

    public void resetNormalizationStats() {
        for (int i = 0; i < getImageStackSize(); i++) {
            stats[i] = null;
        }
        min = Double.NaN;
        max = Double.NaN;
    }

    public void setNormalizationMinSize(int size) {
        normalizationMinSize = size;
    }

    public int getFillType() {
        return fill;
    }

    public void setFillType(int value) {
        fill = value;
    }

    public int getAlignMethodForReconstruction() {
        return alignMethodForReconstruction;
    }

    public void setAlignMethodForReconstruction(int alignMethodForReconstruction) {
        this.alignMethodForReconstruction = alignMethodForReconstruction;
        alignment.resetEulerMatrices();

    }

    public void showTiltAxis(boolean value) {
        showTiltAxis = value;
    }

    public boolean isTiltAxisShowed() {
        return showTiltAxis;
    }

    /**
     * remove an image from the tilt series (beware it is starting from 0)
     * @param index        image number
     * @param combineOther if the alignment of other images should be kept identical (so combine the transform of removed image to the previous)
     */
    public void removeImage(int index, boolean combineOther) {
        System.out.println("remove image " + index + " combine: " + combineOther);
        ImageStack is = getStack();
        is.deleteSlice(index + 1);
        double[] tmpA = tiltAngles;
        tiltAngles = new double[is.getSize()];
        int offset = 0;
        for (int i = 0; i < tiltAngles.length; i++) {
            tiltAngles[i] = tmpA[i + offset];
        }
        //eulerMatrices = new DoubleMatrix2D[is.getSize()];
        //for (int i = 0; i < eulerMatrices.length; i++) eulerMatrices[i] = null;
        if (tp != null) tp.removeImage(index);
        updateZeroIndex();


        for (int i = 0; i < getStackSize(); i++)  stats[i] = null;
        if(alignment!=null) {
            alignment.removeTransform(index);
            alignment.setZeroIndex(zeroindex);
        }

        threadStats();
    }

    public synchronized void updateAndDraw() {
        if (win != null) {
            win.getCanvas().setImageUpdated();
            /*if (listeners.size()>0)*/
            notifyListeners(UPDATED);
        }
        draw();
    }

    /**
     * Activates the specified slice. The index must be >= 1
     * and <= N, where N in the number of slices in the stack.
     * Does nothing if this ImagePlus does not use a stack.
     */
    public synchronized void setSlice(int index) {
        Chrono time = new Chrono();
        time.start();
        //updateInternalData();
        // System.out.println("setSlice " + index);
        if (index > 0 && index <= getStackSize()) {
            Roi roi = getRoi();
            if (ip == null) {
                ip = getProcessor();
            }
            updatePoint();
//            if (roi != null && !(roi instanceof PointRoi)) {
//                setRoi(roi.getBounds());
//            } else

            if (tp != null && tp.isVisible()) {
                setRoi(tp.getRoi(index - 1));
            }
            if (showTiltAxis) {
                setRoi(getTiltAxisAsRoi());
            }
            updatePosition(1, 1, index);
            currentSlice = index;
            Object pixels = getPixels(currentSlice - 1);
            if (pixels != null) {
                ip.setSnapshotPixels(null);
                ip.setPixels(pixels);
            }
            if (win != null && win instanceof StackWindow)
                ((StackWindow) win).updateSliceSelector();
            if (IJ.altKeyDown() && !IJ.isMacro()) {
                if (getType() == GRAY16 || getType() == GRAY32) {
                    ip.resetMinAndMax();
                    IJ.showStatus(index + ": min=" + ip.getMin() + ", max=" + ip.getMax());
                }
                ContrastAdjuster.update();
            }
            //System.out.println("after roi="+getRoi());
            if (getType() == COLOR_RGB)
                ContrastAdjuster.update();

            if (!Interpreter.isBatchMode())
                updateAndRepaintWindow();
            if (ip.getMinThreshold() != ImageProcessor.NO_THRESHOLD) {
                ImageStatistics st = new FloatStatistics(ip, ImageStatistics.MEAN, null);
                double mini = st.histMin;
                int[] hist = st.histogram;
                int pixnb = (int) (width * height * Threshold);
                double th = 0;
                long sum = 0;
                for (int i = 0; i < hist.length; i++) {
                    sum += hist[i];
                    if (sum < pixnb) {
                        th = i;
                    }
                }
                System.out.println("threshold=" + Threshold + " th on image " + index + " =" + th + " mini=" + mini);
                th /= 256;
                th = th * (st.histMax - st.histMin) + st.histMin;
                ip.setThreshold(mini, th, ImageProcessor.RED_LUT);
                System.out.println("threshold=" + Threshold + " th on image " + index + " =" + th + " mini=" + mini);
                System.out.println("stats of image " + index + " =" + st);
            }
        }
        //toto
        time.stop();
        //System.out.println("set Slice time needed=" + time.delay());
    }

    public void updatePoint() {
        Roi roi = getRoi();
        //System.out.println("update point roi="+roi);
        if (roi != null && roi instanceof PointRoi && tp != null && tp.isVisible()) {
            if (((PointRoi) roi).getNCoordinates() == 1 && updatePoints) {
                //System.out.println("roi bounds:("+roi.getBounds().getX()+", "+roi.getBounds().getY()+"  coordinates:("+((PointRoi) roi).getXCoordinates()[0]+", "+((PointRoi) roi).getYCoordinates()[0]);
                Roi cur = tp.getRoi(currentSlice - 1);
                //System.out.println("roi bounds:("+roi.getBounds().getX()+", "+roi.getBounds().getY()+"  coordinates:("+((PointRoi) roi).getXCoordinates()[0]+", "+((PointRoi) roi).getYCoordinates()[0]);

                if (cur == null || roi.getBounds().getX() != cur.getBounds().getX() || roi.getBounds().getY() != cur.getBounds().getY())
                    tp.updatePoint(currentSlice - 1, (PointRoi) roi);
            }
        }
        if (roi == null && tp != null && tp.isVisible()) {
            tp.setPoint(tp.getCurrentIndex(), currentSlice - 1, null);
        }
        //try{
        //System.out.println("update point marker coord:"+tp.getPoint(0,currentSlice-1));
        //}catch(Exception e){}
    }

    public Roi getTiltAxisAsRoi() {
        double ta = tiltaxis;
        if (tiltAxisVertical) ta = 0;

        double a = Math.sin(Math.toRadians(ta - 90)) / Math.cos(Math.toRadians(ta - 90));
        //double sx2 = width / 2.0;
        //double sy2 = height / 2.0;
        double sx2 = getProjectionCenterX();
        double sy2 = getProjectionCenterY();
        int x1 = 0;
        int y1 = (int) ((x1 - sx2) * a + sy2);
        if (y1 < 0 || y1 > height) {
            //IJ.write("changing first point : before x1=" + x1 + ", y1=" + y1);
            y1 = 0;
            x1 = (int) ((y1 - sy2) / a + sx2);
        }
        int x2 = width;
        int y2 = (int) ((x2 - sx2) * a + sy2);
        if (y2 < 0 || y2 > height) {
            //IJ.write("changing second point : before x2=" + x2 + ", y2=" + y2);
            y2 = height;
            x2 = (int) ((y2 - sy2) / a + sx2);
        }
        return new Line(x1, y1, x2, y2);

    }

    public float[] getPixels(int index) {
        float[] res= (alignment==null)?getOriginalPixels(index):alignment.applyTransformOnImage(this,index);

        /*AffineTransform T = alignment.getTransform(index);
        float[] res = getPixels(index, T);  */
        if (applyThreshold) {
            res = applyThreshold(res);
        }
        return res;
    }

    public double getProjectionCenterX() {
        return projectionCenterX;
        //return alignmentDual!=null?(width / 2)+alignmentDual[3]:width/2;
    }

    public double getProjectionCenterY() {
        return projectionCenterY;
        //return alignmentDual!=null?(height / 2)+alignmentDual[4]:height/2;
    }



    /* Modified from protected to public by Antoine */
    public float[] getPixels(int index, AffineTransform T) {
        Object imgpix = data.getPixels(index + 1);
        if (stats[index] == null) {
            //stats[index] = new FloatStatistics(new FloatProcessor(width, height, (float[]) imgpix, null));
            stats[index] = getImageStatistics(index);
            //if(tiltAngles[index]==-90) System.out.println("recompute stats mean:"+stats[index].mean+",  stdDev:"+stats[index].stdDev);

        }
        return getPixels((float[]) imgpix, T, stats[index], width, height, normalize, fill);
    }

    public float[] getOriginalPixels(int index) {
        return (float[]) data.getPixels(index + 1);
    }

    protected float[] applyThreshold(float[] res) {
        ImageStatistics st = new FloatStatistics(new FloatProcessor(width, height, res, null), ImageStatistics.MEAN, null);
        int[] hist = st.histogram;
        int pixnb1 = (int) (width * height * Threshold);
        int pixnb2 = (int) (width * height * ThresholdHisteresis);
        double th1 = 0;
        double th2 = 0;
        long sum = 0;
        for (int i = 0; i < hist.length; i++) {
            sum += hist[i];
            if (sum < pixnb1) {
                th1 = i;
            }
            if (sum < pixnb2) {
                th2 = i;
            }
        }
        //System.out.println("threshold="+Threshold+" th on image "+index+" ="+ th+" mini="+mini);
        th1 /= 256;
        th1 = th1 * (st.histMax - st.histMin) + st.histMin;
        th2 /= 256;
        th2 = th2 * (st.histMax - st.histMin) + st.histMin;
        //System.out.println("thresholds ("+th1+", "+th2+")");
        float value = Float.NaN;
        if (fill == FILL_AVG) value = (float) st.mean;
        if (fill == FILL_NONE) value = 0;
        int count = 0;
        ArrayList<Integer> Q = new ArrayList<Integer>(200);
        for (int i = 0; i < res.length; i++) {
            if (res[i] < th1) res[i] = value;
            else if (res[i] < th2) {
                count++;
                Q.add(i);
            }
        }
        //System.out.println("size of Q="+Q.size());
        if (Float.isNaN(value)) {
            while (count > 0) {
                count = 0;
                for (int i = Q.size() - 1; i >= 0; i--) {
                    Integer ind = Q.get(i);
                    if (ind - 1 >= 0 && Float.isNaN(res[ind - 1])) {
                        count++;
                        Q.remove(i);
                        res[ind] = value;
                    } else if (ind + 1 < res.length && Float.isNaN(res[ind + 1])) {
                        count++;
                        Q.remove(i);
                        res[ind] = value;
                    } else if (ind - width >= 0 && Float.isNaN(res[ind - width])) {
                        count++;
                        Q.remove(i);
                        res[ind] = value;
                    } else if (ind + width < res.length && Float.isNaN(res[ind + width])) {
                        count++;
                        Q.remove(i);
                        res[ind] = value;
                    }
                }
            }
        } else {
            while (count > 0) {
                count = 0;
                for (int i = Q.size() - 1; i >= 0; i--) {
                    Integer ind = Q.get(i);
                    if (ind - 1 >= 0 && res[ind - 1] == value) {
                        count++;
                        Q.remove(i);
                        res[ind] = value;
                    } else if (ind + 1 < res.length && res[ind + 1] == value) {
                        count++;
                        Q.remove(i);
                        res[ind] = value;
                    } else if (ind - width >= 0 && res[ind - width] == value) {
                        count++;
                        Q.remove(i);
                        res[ind] = value;
                    } else if (ind + width < res.length && res[ind + width] == value) {
                        count++;
                        Q.remove(i);
                        res[ind] = value;
                    }
                }
            }
        }
        return res;

    }

    public ImageStatistics getImageStatistics(int index) {
        double tilt = tiltAngles[index];
        //System.out.println("before region selection mean="+result.mean+" stdDev="+result.stdDev);
        double cx = centerx;
        double cy = centery;
        int roisx = (int) Math.max(Math.round(width * Math.cos(Math.toRadians(tilt))), normalizationMinSize);
        //System.out.println("width of Selection:"+roisx);
        double[] points = new double[8];
        points[0] = (width - roisx) / 2 - cx;
        points[1] = -cy;
        points[2] = points[0] + roisx;
        points[3] = points[1];
        points[4] = points[2];
        points[5] = height - cy;
        points[6] = points[0];
        points[7] = points[5];
        double[] resp = new double[8];
        alignment.getTransform(index).transform(points, 0, resp, 0, 4);

        int[] xs = {(int) (resp[0] + cx), (int) (resp[2] + cx), (int) (resp[4] + cx), (int) (resp[6] + cx)};
        int[] ys = {(int) (resp[1] + cy), (int) (resp[3] + cy), (int) (resp[5] + cy), (int) (resp[7] + cy)};
        PolygonRoi r = new PolygonRoi(xs, ys, 4, Roi.POLYGON);
        ImageProcessor ip = data.getProcessor(index + 1);
        ip.setRoi(r);
        return ip.getStatistics();

    }

    public static float[] getPixels(final float[] imgpix, final AffineTransform T, final ImageStatistics stats, final int width, final int height, final boolean normalize, final int fill) {
        //Chrono time=new Chrono();
        //time.start();
        final double mean = stats.mean;
        final double stdDev = stats.stdDev;
        final float[] respix;
        if (T.isIdentity()) {
            if (normalize) {
                respix = new float[width * height];
                for (int i = 0; i < imgpix.length; i++) {
                    respix[i] = (float) ((imgpix[i] - stats.mean) / stats.stdDev);
                }
            } else {
                //respix = imgpix;
                respix = new float[imgpix.length];
                System.arraycopy(imgpix, 0, respix, 0, imgpix.length);
            }
        } else {
            respix = new float[width * height];
            double centerx = (width) / 2.0;
            double centery = (height) / 2.0;
            //ImageStatistics stats = new FloatStatistics(new FloatProcessor(width, height, imgpix, null));

            //double[] TinvMatrix = new double[6];
            final AffineTransform Tinv;
            try {
                //AffineTransform Tinv = affT.createInverse();
                //T.createInverse().getMatrix(TinvMatrix);
                Tinv = T.createInverse();
            } catch (Exception e) {
                System.out.println("error in getting pixels!!!" + e);
                return null;
            }


            float pix;
            double xx;
            double yy;
            int jj;
            int ix0, iy0;
            double dx0, dy0;
            double fac4;// fac1 ,fac2, fac3 ;
            float value1, value2, value3, value4;
            int pos;
            //Point2D tmp;
            //Point2D res;
            double jc;
            final double[] ligne = new double[width * 2];
            final double[] res = new double[width * 2];
            for (int i = 0; i < width; i++) {
                ligne[i * 2] = i - centerx;
            }
            for (int j = 0; j < height; j++) {
                jj = j * width;
                jc = j - centery;
                for (int i = 1; i < ligne.length; i += 2) {
                    ligne[i] = jc;
                }
                Tinv.transform(ligne, 0, res, 0, width);
                //System.out.println("different line");
                for (int i = 0; i < width; i++) {
                    xx = res[i * 2] + centerx;
                    yy = res[i * 2 + 1] + centery;
                    ix0 = (int) xx;
                    iy0 = (int) yy;
                    dx0 = xx - ix0;
                    dy0 = yy - iy0;

                    //System.out.println("dx0="+dx0+" dy0="+dy0);
                    if (ix0 >= 0 && ix0 < width && iy0 >= 0 && iy0 < height) {
                        //en bas a gauche
                        if (ix0 == width - 1 || iy0 == height - 1) {
                            pix = imgpix[ix0 + iy0 * width];
                            if (normalize) {
                                pix = (float) ((pix - stats.mean) / stats.stdDev);
                            }
                        } else {
                            fac4 = (dx0 * dy0);
                            // fac1 = (1 - dx0 - dy0 + fac4);
                            //fac2 = (dx0 - fac4);
                            //fac3 = (dy0 - fac4);

                            /*value1 = imgpix[ix0 + iy0 * width];
                            value2 = imgpix[ix0 + 1 + iy0 * width];
                            value3 = imgpix[ix0 + (iy0 + 1) * width];
                            value4 = imgpix[ix0 + 1 + (iy0 + 1) * width];*/
                            value1 = imgpix[pos = ix0 + iy0 * width];
                            value3 = imgpix[pos + width];
                            value2 = imgpix[++pos];
                            value4 = imgpix[pos + width];
                            /*if (normalize) {

                                value1 = (float) ((value1 - mean) / stdDev);
                                value2 = (float) ((value2 - mean) / stdDev);
                                value3 = (float) ((value3 - mean) / stdDev);
                                value4 = (float) ((value4 - mean) / stdDev);
                            }*/
                            //pix = (float) (value1 * fac1 + value2 * fac2 + value3 * fac3 + value4 * fac4);
                            pix = (float) (value1 * (1 - dx0 - dy0 + fac4) + value2 * (dx0 - fac4) + value3 * (dy0 - fac4) + value4 * fac4);
                            if (normalize) pix = (float) ((pix - mean) / stdDev);
                        }
                        //result.putPixelValue(i, j, pix);
                        respix[jj + i] = pix;

                        //}else if(ix0==-1||iy0==-1||ix0==width||iy0==height){
                        //    respix[jj+i]=10000000;
                    } else if (fill == FILL_AVG) {
                        if (!normalize) {
                            respix[jj + i] = (float) stats.mean;
                        }
                    } else if (fill == FILL_NaN) {
                        respix[jj + i] = Float.NaN;
                    }
                }
            }
        }
        //time.stop();
        //System.out.println("getPixel time "+time.delay());
        return respix;
    }



    /**
     * get the tilt angle of an image in the tilt series
     * @param index image number
     * @return the tilt angle in degree
     */
    public double getTiltAngle(int index) {
        return tiltAngles[index];
    }



    public void setProjectionCenter(double x, double y) {
        projectionCenterX = x;
        projectionCenterY = y;
    }

    protected float[] getPixels(int index, AffineTransform T, boolean normalize) {
        Object imgpix = data.getPixels(index + 1);
        if (stats[index] == null) {
            //stats[index] = new FloatStatistics(new FloatProcessor(width, height, (float[]) imgpix, null));
            stats[index] = getImageStatistics(index);
            //if(tiltAngles[index]==-90) System.out.println("recompute stats mean:"+stats[index].mean+",  stdDev:"+stats[index].stdDev);

        }
        return getPixels((float[]) imgpix, T, stats[index], width, height, normalize, fill);
    }

    /**
     * gives the pixels for the alignment by applying as needed a centered ROI and/or a bandpass filter and/or a binning <BR>
     * the binning is done by downsampling in fourier space
     * @param index index of the image wanted (beginning at 0)
     * @return 1D float array containing the pixels of the 2D image of size roiWidth/binning * roiHeight/binning (if no ROI then width/binning * height/binning)
     */
    public float[] getPixelsForAlignment(int index) {
        return getPixelsForAlignment(index, null);
    }

    public float[] getPixelsForAlignment(int index, Point2D.Double centerPosition) {
        float[] pixs;
        if (roisx > 0) {
            if (centerPosition == null) {
                centerPosition = new Point2D.Double(0, 0);
            }
            pixs = getSubImagePixels(index, roisx, roisy, centerPosition, applyT, combineTransforms);
        } else {
            pixs = getPixels(index);
        }
        return applyFiltersForAlignment(pixs);
        //return pixs;
    }

    public float[] applyFiltersForAlignment(float[] pixs) {
        int sx = (roisx > 0) ? roisx : width;
        int sy = (roisx > 0) ? roisy : height;
        //System.out.println("pixels size="+pixs.length+" sx="+sx+" sy="+sy);
        // FloatFFT_2D fft;
        // float[] fft1 = null;

        //binning and/or bandpass filter
        int nsx = sx / binning;
        int nsy = sy / binning;
        if (binning > 1 || !Double.isNaN(bandpassHighCut)) {
            /*DenseDoubleMatrix2D H1 = new DenseDoubleMatrix2D(sy, sx);
            H1.assign(pixs);
            DenseDComplexMatrix2D fft = H1.getFft2();
            double[] fft1 = fft.elements();*/
            DenseFloatMatrix2D H1 = new DenseFloatMatrix2D(sy, sx);
            H1.assign(pixs);
            DenseFComplexMatrix2D fft = H1.getFft2();
            float[] fft1 = fft.elements();
            /*fft = new FloatFFT_2D(sx, sy);
            int size = sx * sy;
            fft1 = new float[pixs.length * 2];
            System.arraycopy(pixs, 0, fft1, 0, size);
            fft.realForwardFull(fft1);*/


            int nsize = nsx * nsy;
            double ncx = (nsx) / 2;
            double ncy = (nsy) / 2;
            double cx = (sx) / 2;
            double cy = (sy) / 2;
            //bandpass if needed
            if (!Double.isNaN(bandpassHighCut)) {
                //doing filtering
                double auxmin = Math.PI / (bandpassLowkeep - bandpassLowCut);
                double auxmax = Math.PI / (bandpassHighCut - bandpassHighKeep);
                for (int j = 0; j < sy; j++) {
                    int jj = j * sx;
                    double cj = (j > cy) ? j - sy : j;
                    for (int i = 0; i < sx; i++) {
                        double ci = (i > cx) ? i - sx : i;
                        double dist = Math.sqrt(ci * ci + cj * cj);
                        if (dist >= bandpassLowCut && dist < bandpassLowkeep) {
                            double tmp = (1 + Math.cos((dist - bandpassLowkeep) * auxmin)) * .5f;
                            fft1[(jj + i) * 2] *= tmp;
                            fft1[(jj + i) * 2 + 1] *= tmp;
                        } else if (dist >= bandpassLowkeep && dist <= bandpassHighKeep) {
                        } else if (dist > bandpassHighKeep && dist <= bandpassHighCut) {
                            double tmp = (1 + Math.cos((dist - bandpassHighKeep) * auxmax)) * .5f;
                            fft1[(jj + i) * 2] *= tmp;
                            fft1[(jj + i) * 2 + 1] *= tmp;
                        } else {
                            fft1[(jj + i) * 2] = 0;
                            fft1[(jj + i) * 2 + 1] = 0;
                        }
                    }
                }
            }
            //downsampling in fourier
            float[] bfft = new float[nsize * 2];
            for (int y = 0; y < nsy; y++) {
                int yy = y * nsx;
                int posy = (y > ncy) ? (y + sy - nsy) * sx : y * sx;
                for (int x = 0; x < nsx; x++) {
                    int posx = (x > ncx) ? (x - nsx + sx) : x;
                    bfft[(yy + x) * 2] = fft1[(posy + posx) * 2];
                    bfft[(yy + x) * 2 + 1] = fft1[(posy + posx) * 2 + 1];
                }
            }
            /*fft = new FloatFFT_2D(nsx, nsy);
            fft.complexInverse(bfft, true); */
            fft = new DenseFComplexMatrix2D(nsy, nsx);
            fft.assign(bfft);
            fft.ifft2(true);
            bfft = fft.elements();
            pixs = new float[nsize];
            for (int j = 0; j < pixs.length; j++) {
                pixs[j] = bfft[j * 2];
            }
        }
        if (varianceFilterSize > 0) {
            //System.out.println("apply variance");
            double sum, sum2;
            int N;
            float val;
            float[] pixsCopy = Arrays.copyOf(pixs, pixs.length);
            for (int pos = 0; pos < pixs.length; pos++) {
                sum = 0;
                sum2 = 0;
                N = 0;
                for (int radiusy = -varianceFilterSize; radiusy <= varianceFilterSize; radiusy++) {
                    for (int radiusx = -varianceFilterSize; radiusx <= varianceFilterSize; radiusx++) {
                        int pos2 = pos + radiusy * nsx + radiusx;
                        if (pos2 >= 0 && pos2 < pixs.length) {
                            val = pixsCopy[pos2];
                            sum += val;
                            sum2 += val * val;
                            N++;
                        }
                    }
                }
                sum /= N;
                pixs[pos] = (float) (sum2 / N - sum * sum);
            }
        }
        return pixs;
        //System.out.println("pixels size="+pixs.length+" sx="+sx+" sy="+sy);
        //return binning(pixs,sx,sy,binning);
    }

    public float[] normalizeImage(float[] img, ImageStatistics stats) {
        for (int i = 0; i < img.length; i++) {
            img[i] = (float) ((img[i] - stats.mean) / stats.stdDev);
        }
        return img;
    }


    /*public float[] getPixelsDual(int index) {
        float[] res;
        if (tp != null && tp.getLandmarks3D() != null && tp.getLandmarks3D().getBestAlignment() != null) {
            if (alignMethodForReconstruction == ALIGN_NONLINEAR) {
                System.out.println("export aligned images non linear");
                res = tp.getLandmarks3D().getBestAlignment().produceAlignedImage(index, fill == FILL_NaN ? Float.NaN : 0);
                if (normalize) {
                    if (stats[index] == null) stats[index] = getImageStatistics(index);
                    res = normalizeImage(res, stats[index]);
                }
                //new ImagePlus("aligned"+index,new FloatProcessor(width,height,res)).show();
            } else {
                //System.out.println("ali3D present");
                res = getPixels(index, getTransform(index));
            }
        } else if (tp != null && tp.getLandmarks3DDual() != null && tp.getLandmarks3DDual().getBestAlignment() != null) {
            if (alignMethodForReconstruction == ALIGN_NONLINEAR) {
                System.out.println("export aligned images non linear");
                if (tp == tp.getLandmarks3DDual().getSuperTomoJPoints().getTomoJPoints(0)) {
                    res = tp.getLandmarks3DDual().getBestAlignment().produceAlignedImage(index, fill == FILL_NaN ? Float.NaN : 0);
                } else {
                    int offset = tp.getLandmarks3DDual().getSuperTomoJPoints().getnImagesi(0);
                    res = tp.getLandmarks3DDual().getBestAlignment().produceAlignedImage(index + offset, fill == FILL_NaN ? Float.NaN : 0);
                }
                if (normalize) {
                    if (stats[index] == null) stats[index] = getImageStatistics(index);
                    res = normalizeImage(res, stats[index]);
                }
                //new ImagePlus("aligned"+index,new FloatProcessor(width,height,res)).show();
            } else {
                //System.out.println("ali3D present");
                res = getPixels(index, getTransform(index));
            }
        } else {
            AffineTransform T = new AffineTransform();
            if (alignmentDual != null) {
                //T.translate(alignmentDual[3], alignmentDual[4]);
                //T.rotate(Math.toRadians(-alignmentDual[0]));
            }

            T.concatenate(getTransform(index, applyT));
            //AffineTransform T = getTransform(index, applyT);
            res = getPixels(index, T);

        }

        if (alignmentDual != null) {
            //res = shiftProjectionInZ(res, getWidth(), getHeight(), index, alignmentDual[5]);
        }
        if (applyThreshold) {
            res = applyThreshold(res);
        }
        return res;
    }    */

    public float[] getSubImagePixels(int index, int sx, int sy, Point2D coordCentered, boolean applyTransforms, boolean combineTransforms) {
        //AffineTransform Tinv = getCombinedInverseTransform(index);
        AffineTransform T=new AffineTransform();
        if(applyTransforms) {
            T = alignment.getTransform(index);
        }
        try {
            AffineTransform Tinv = T.createInverse();
            final float[] imgpix = (float[]) data.getPixels(index + 1);
            if (stats[index] == null) {
                //stats[index] = new FloatStatistics(new FloatProcessor(width, height, imgpix, null));
                stats[index] = getImageStatistics(index);
            }
            return getSubImagePixels(imgpix, Tinv, stats[index], width, height, sx, sy, coordCentered, normalize);
        } catch (Exception E) {
            System.out.println(E);
            return null;
        }

    }

    public void threadStats() {
        new Thread() {
            public void run() {
                Chrono time = new Chrono();
                time.start();
                //final int nbproc = Runtime.getRuntime().availableProcessors();
                final Thread[] threads = new Thread[nbcpu - 1];
                for (int t = 0; t < nbcpu - 1; t++) {
                    final int start = t;
                    threads[t] = new Thread() {
                        public void run() {
                            for (int index = start; index < stats.length; index += nbcpu - 1)
                                if (stats[index] == null)
                                    stats[index] = getImageStatistics(index);
                            setPriority(Thread.MIN_PRIORITY);
                        }
                    };
                    threads[t].start();
                }
                for (int t = 0; t < nbcpu - 1; t++) {
                    try {
                        threads[t].join();
                    } catch (Exception e) {
                        System.out.println(e);
                    }

                }
                time.stop();
//                for (int i = 0; i < stats.length; i++) {
//                    System.out.println("#" + i + " avg:" + stats[i].mean + " , stdDev:" + stats[i].stdDev + " , min:" + stats[i].min + " , max:" + stats[i].max);
//                }
                System.out.println("thread Stats with " + (nbcpu - 1) + " threads finished in " + time.delayString());
            }
        }.start();

    }



    /**
     * gives the statistics for an image
     * @param index index of image
     * @return the statistics corresponding to the image :<BR>
     * <LI> { mean, standard deviation} if images are normalized</LI>
     * <LI> { 0, 1} if images are not normalized</LI>
     * </BR>
     */
    public float[] getStatsAsArray(int index) {
        if (stats[index] == null) stats[index] = getImageStatistics(index);
        float[] res = {0, 1};
        if (normalize) {
            res[0] = (float) stats[index].mean;
            res[1] = (float) stats[index].stdDev;
        }
        return res;
    }

    public void computeMinMax() {
        for (int i = 0; i < getImageStackSize(); i++) {
            stats[i] = getImageStatistics(i);
        }
    }

    public double getMin() {
        if (stats[zeroindex] == null) {
            stats[zeroindex] = getImageStatistics(zeroindex);
        }
        double value = stats[zeroindex].min;
        if (normalizationType == ELECTRON_TOMO || normalizationType == ZERO_ONE) {
            value -= stats[zeroindex].mean;
            value /= stats[zeroindex].stdDev;
        }
        return value;
    }

    public double getMax() {
        if (stats[zeroindex] == null) {
            stats[zeroindex] = getImageStatistics(zeroindex);
        }
        double value = stats[zeroindex].max;
        if (normalizationType == ELECTRON_TOMO || normalizationType == ZERO_ONE) {
            value -= stats[zeroindex].mean;
            value /= stats[zeroindex].stdDev;
        }
        return value;
    }

    public boolean isShowInIJ() {
        return showInIJ;
    }

    /**
     * get the completion of processes
     * @return the completion level (divide by the number of images to get the percentage)
     */
    public int getCompletion() {
        return completion;
    }

    /**
     * allow interruption of processes
     */
    public void interrupt() {
        completion = -1000;
    }


    public void setBandpassFilter(double lowCut, double lowKeep, double highKeep, double highCut) {
        if (Double.isNaN(lowCut) || Double.isNaN(lowKeep) || Double.isNaN(highKeep) || Double.isNaN(highCut)) {
            bandpassHighCut = Double.NaN;
            bandpassHighKeep = Double.NaN;
            bandpassLowCut = Double.NaN;
            bandpassLowkeep = Double.NaN;
        } else {
            bandpassHighCut = highCut;
            bandpassHighKeep = highKeep;
            bandpassLowCut = lowCut;
            bandpassLowkeep = lowKeep;
        }
    }

    public double[] getBandpassParameters() {
        double[] tmp = new double[4];
        tmp[0] = bandpassLowCut;
        tmp[1] = bandpassLowkeep;
        tmp[2] = bandpassHighKeep;
        tmp[3] = bandpassHighCut;
        return tmp;
    }

    public void setBinning(int value) {
        if (value < 1) value = 1;
        binning = value;
    }

    public int getBinningFactor() {
        return binning;
    }

    public int getVarianceFilterSize() {
        return varianceFilterSize;
    }

    public void setVarianceFilterSize(int value) {
        varianceFilterSize = value;
    }

    public boolean isIntegerTranslation() {
        return integerTranslation;
    }

    public void setIntegerTranslation(boolean value) {
        integerTranslation = value;
    }

    public void setAlignmentRoi(int roiWidth, int roiHeight) {
        roisx = roiWidth;
        roisy = roiHeight;
    }

    public Rectangle getAlignmentRoi() {
        int scx = (roisx) / 2;
        int scy = (roisy) / 2;
        return new Rectangle(-scx, -scy, roisx, roisy);
    }

    public int[] getAlignmentImageSize() {
        int[] res = new int[2];
        res[0] = width;
        res[1] = height;
        if (roisx > 0) {
            res[0] = roisx;
            res[1] = roisy;
        }
        if (binning > 1) {
            res[0] /= binning;
            res[1] /= binning;
        }
        return res;
    }



    public int getZeroIndex() {
        return zeroindex;
    }


    /**
     * are the transforms applied to the displayed images?
     * @return true if applied, false if not
     */
    public boolean isApplyingTransform() {
        return applyT;
    }

    /**
     * are the transform combined?
     * @return true if combined, false if not
     */
    public boolean iscombiningTransforms() {
        return combineTransforms;
    }

    /**
     * set the application or not of the transforms to the images displayed
     * @param apply true if transforms are to be applied
     */
    public void applyTransforms(boolean apply) {
        applyT = apply;
    }

    /**
     * set the combination of transforms<BR>
     * if false the transforms of each image are considered absolute transform<BR>
     * if true (default) the transforms are considered as transforms to the neighbor next image
     * @param combine true to combine the transforms
     */
    public void combineTransforms(boolean combine) {
        combineTransforms = combine;
    }


    /**
     * get the tilt axis of the tilt series
     * @return the tilt axis angle in degree to go the vertical axis
     */
    public double getTiltAxis() {
        return tiltaxis;
    }

    /**
     * set the tilt axis of the tilt series
     * @param angleDeg tilt axis angle in degree to go the vertical axis
     */
    public void setTiltAxis(double angleDeg) {
        tiltaxis = angleDeg;
        if(alignment instanceof AffineAlignment){
            ((AffineAlignment) alignment).setTiltAxis(tiltaxis);
            ((AffineAlignment) alignment).setTiltAxisVertical(true);
        }
        alignment.resetEulerMatrices();
    }

    public Alignment getAlignment(){
        return alignment;
    }

    public void setAlignment(Alignment alignment){ this.alignment=alignment;}

    

}
