package fr.curie.tomoj.tomography;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.io.FileInfo;
import ij.process.Blitter;
import ij.process.FloatBlitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.projectors.Projector;
import fr.curie.utils.Chrono;
//import v3.tomoj.TestDual;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 18/09/2014.
 */
public class TomoReconstruction2 extends ImagePlus {
    protected static float ACCURACY = 0.0000000001f;
    private double centerx, centery, centerz;
    private int sizez;
    protected ImageStack data;
    protected double completion;
    protected ExecutorService exec;
    protected int currentIterationNb;
    protected boolean show = true;
    int nbcpu = Prefs.getThreads();
    long totalTimeProjection = 0;
    long totalTimeBackProjection = 0;
    boolean displayIterationInfo = true;
    public static int ADD = FloatBlitter.ADD;
    public static int SUBTRACT = FloatBlitter.SUBTRACT;
    public static int MULTIPLY = FloatBlitter.MULTIPLY;
    public static int DIVIDE = FloatBlitter.DIVIDE;
    protected Projector projector = null;



    /**
     * create a new reconstruction having the same data as the ImagePlus (the data are shared)
     *
     * @param imp original data
     */
    public TomoReconstruction2(ImagePlus imp) {
        super(imp.getTitle(), imp.getImageStack());
        data = getImageStack();
        init();
    }

    /**
     * initialization of some inner data of the reconstruction (center, sizes)
     */
    protected void init() {
        centerx = (width - 1) / 2.0;
        centery = (height - 1) / 2.0;
        centerz = (getImageStackSize() - 1) / 2.0;
        //pcenters = new double[]{centerx, centery, centerx, centery};
        sizez = getImageStackSize();
        exec = Executors.newFixedThreadPool(nbcpu);
        initCompletion();
    }

    public void initCompletion() {
        completion = 0;
    }

    /**
     * create a new reconstruction that is a copy of another reconstruction (the data are copied and not shared)
     *
     * @param toCopyFrom the original reconstruction
     */
    public TomoReconstruction2(TomoReconstruction2 toCopyFrom) {
        this(toCopyFrom.getWidth(), toCopyFrom.getHeight(), toCopyFrom.getImageStackSize());
        //ImageStack is = getImageStack();
        ImageStack ori = toCopyFrom.getImageStack();
        for (int i = 1; i <= sizez; i++) {
            float[] orit = (float[]) ori.getPixels(i);
            System.arraycopy(orit, 0, (float[]) data.getPixels(i), 0, orit.length);
        }
        initCompletion();
    }

    /**
     * create empty reconstruction
     *
     * @param width  width of reconstruction
     * @param height height of reconstruction
     * @param thick  thickness of reconstruction
     */
    public TomoReconstruction2(int width, int height, int thick) {
        super();
        data = new ImageStack(width, height);
        for (int i = 0; i < thick; i++) {
            data.addSlice("", new FloatProcessor(width, height));
        }
        setStack("rec", data);
        init();
    }

    public int getSizez() {
        return sizez;
    }

    public void setCenter(double cx, double cy, double cz) {
        centerx = cx;
        centery = cy;
        centerz = cz;
    }

    public double getCenterx() {
        return centerx;
    }

    public double getCentery() {
        return centery;
    }

    public double getCenterz() {
        return centerz;
    }

    public double getCompletion() {
        if (projector == null) return completion;
        return completion + projector.getCompletion();
    }

    public void interrupt() {
        completion = -1000;
        exec.shutdownNow();
    }

    public void setDisplayIterationInfo(boolean show) {
        displayIterationInfo = show;
    }

    /**
     * get voxel value with with bilinear interpolation on plane XY (need for optimization of joseph projection)
     *
     * @param x coordinate on X axis
     * @param y coordinate on Y axis
     * @param z coordinate on Z axis
     * @return voxel value
     */
    public float getPixel(final double x, final double y, final int z) {
//        if (x < -1 || x >= width || y < -1 || y >= height || z < -1 || z >= sizez) {
//            return 0;
//        }
        final int xbase = (x < 0) ? 0 : (int) x;
        final int ybase = (y < 0) ? 0 : (int) y;
//        int xbase = (int) x;
//        int ybase = (int) y;
        //if (xbase == width - 1 || ybase == height - 1 || zbase == sizez - 1) return getPixel(xbase, ybase, zbase);
        if (xbase == width - 1 || ybase == height - 1 || z == sizez - 1)
            return ((float[]) data.getPixels(z + 1))[xbase + ybase * width];

        final float xFraction = (x < 0) ? 0 : (float) x - xbase;
        final float yFraction = (y < 0) ? 0 : (float) y - ybase;
//        double xFraction = x - xbase;
//        double yFraction = y - ybase;
//        if (xbase <0) {
//            xbase = 0;
//            xFraction = 0;
//        }
//        if (ybase <0) {
//            ybase = 0;
//            yFraction = 0;
//        }
        final float[] imagez = ((float[]) data.getPixels(z + 1));
        final int index = ybase * width + xbase;
        final float xyz = imagez[index];
        //final float x1yz =imagez[index+1];// imagez[ybase * width + xbase + 1];
        //final float x1y1z =imagez[index+width+1];// imagez[(ybase + 1) * width + xbase + 1];
        final float xy1z = imagez[index + width];//imagez[(ybase + 1) * width + xbase];
//        final double upperAvplane = xy1z + xFraction * (x1y1z - xy1z);
//        final double lowerAvplane = xyz + xFraction * (x1yz - xyz);
        //final double upperAvplane = xy1z + xFraction * (imagez[index+width+1] - xy1z);
        final float lowerAvplane = xyz + xFraction * (imagez[index + 1] - xyz);
        return (lowerAvplane + yFraction * ((xy1z + xFraction * (imagez[index + width + 1] - xy1z)) - lowerAvplane));
        //return (float)(lowerAvplane + yFraction * (upperAvplane - lowerAvplane));
    }


    /**
     * get voxel value with bilinear interpolation on plane XZ (needed for 2D version of projectors
     *
     * @param x coordinate on X axis
     * @param y coordinate on Y axis
     * @param z coordinate on Z axis
     * @return voxel value
     */
    public float getPixel(double x, int y, double z) {
        int xbase = (int) x;
        int zbase = (int) z;
        if (xbase == width - 1 || zbase == sizez - 1) return getPixel(xbase, y, zbase);


        double xFraction = x - xbase;
        double zFraction = z - zbase;
        if (xbase == -1) {
            xbase = 0;
            xFraction = 0;
        }
        if (zbase == -1) {
            zbase = 0;
            zFraction = 0;
        }
        float[] imagez = ((float[]) data.getPixels(zbase + 1));
        float[] imagez1 = ((float[]) data.getPixels(zbase + 2));

        float xyz = imagez[y * width + xbase];
        float x1yz = imagez[y * width + xbase + 1];
        double lowerAvplane = xyz + xFraction * (x1yz - xyz);

        float xyz1 = imagez1[y * width + xbase];
        float x1yz1 = imagez1[y * width + xbase + 1];
        double lowerAvplane1 = xyz1 + xFraction * (x1yz1 - xyz1);
        return (float) (lowerAvplane + zFraction * (lowerAvplane1 - lowerAvplane));

    }

    /**
     * get the pixel value at position (x,y,z) <br>
     * the positions starts at 0 and go to width, height and thickness respectively. contrary to ImageStack z starts from 0 not 1.<br>
     * a test is performed to be sure that position is accessible
     *
     * @param x position on x axis
     * @param y position on y axis
     * @param z position on z axis. starts at 0 not 1 as in ImageStack
     * @return the value at the given position, 0 if position given is outside the reconstruction
     */
    public float getPixel(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= sizez) {
            return 0;
        }
        return ((float[]) data.getPixels(z + 1))[y * width + x];
    }

    /**
     * get voxel value with trilinear interpolation
     *
     * @param x coordinate on X axis
     * @param y coordinate on Y axis
     * @param z coordinate on Z axis
     * @return voxel value
     */
    public float getPixel(double x, double y, double z) {
        if (x < -1 || x >= width || y < -1 || y >= height || z < -1 || z >= sizez) {
            return 0;
        }
        int xbase = (int) x;
        int ybase = (int) y;
        int zbase = (int) z;
        //if (xbase == width - 1 || ybase == height - 1 || zbase == sizez - 1) return getPixel(xbase, ybase, zbase);
        if (xbase == width - 1 || ybase == height - 1 || zbase == sizez - 1)
            return ((float[]) data.getPixels(zbase + 1))[xbase + ybase * width];
        double xFraction = x - xbase;
        double yFraction = y - ybase;
        double zFraction = z - zbase;
        if (xbase == -1) {
            xbase = 0;
            xFraction = 0;
        }
        if (ybase == -1) {
            ybase = 0;
            yFraction = 0;
        }
        if (zbase == -1) {
            zbase = 0;
            zFraction = 0;
        }
        float[] imagez = ((float[]) data.getPixels(zbase + 1));
        float[] imagez1 = ((float[]) data.getPixels(zbase + 2));

        float xyz = imagez[ybase * width + xbase];
        float x1yz = imagez[ybase * width + xbase + 1];
        float x1y1z = imagez[(ybase + 1) * width + xbase + 1];
        float xy1z = imagez[(ybase + 1) * width + xbase];
        double upperAvplane = xy1z + xFraction * (x1y1z - xy1z);
        double lowerAvplane = xyz + xFraction * (x1yz - xyz);
        double plane = lowerAvplane + yFraction * (upperAvplane - lowerAvplane);

        float xyz1 = imagez1[ybase * width + xbase];
        float x1yz1 = imagez1[ybase * width + xbase + 1];
        float x1y1z1 = imagez1[(ybase + 1) * width + xbase + 1];
        float xy1z1 = imagez1[(ybase + 1) * width + xbase];
        double upperAvplane1 = xy1z1 + xFraction * (x1y1z1 - xy1z1);
        double lowerAvplane1 = xyz1 + xFraction * (x1yz1 - xyz1);
        double plane1 = lowerAvplane1 + yFraction * (upperAvplane1 - lowerAvplane1);
        return (float) (plane + zFraction * (plane1 - plane));
        //return (float)plane ;
    }

    public float[] getCubicNeightborhood(int x, int y, int z, int radius) {
        int diameter = radius * 2 + 1;
        float[] result = new float[diameter * diameter * diameter];
        int index = 0;
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                for (int k = -radius; k <= radius; k++) {
                    result[index++] = getPixel(x + i, y + j, z + k);
                }
            }
        }
        return result;
    }

    public float[] getCubicNeightborhoodWithClamp(int x, int y, int z, int radius) {
        int diameter = radius * 2 + 1;
        float[] result = new float[diameter * diameter * diameter];
        int index = 0;
        int xx, yy, zz;
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                for (int k = -radius; k <= radius; k++) {
                    xx = x + i;
                    if (xx < 0) xx = 0;
                    if (xx >= width) xx = width - 1;
                    yy = y + i;
                    if (yy < 0) yy = 0;
                    if (yy >= height) yy = height - 1;
                    zz = z + i;
                    if (zz < 0) zz = 0;
                    if (zz >= sizez) zz = sizez - 1;
                    result[index++] = getPixel(x + i, y + j, z + k);
                }
            }
        }
        return result;
    }

    /**
     * put a value at position (x,y,z).<br>
     * the positions starts at 0 and go to width, height and thickness respectively. contrary to ImageStack z starts from 0 not 1.<br>
     * NO TEST is performed to be sure that position is accessible
     *
     * @param x     position on x axis
     * @param y     position on y axis
     * @param z     position on z axis. starts at 0 not 1 as in ImageStack
     * @param value the value to put at the position
     */
    public void putPixel(int x, int y, int z, double value) {
        ((float[]) data.getPixels(z + 1))[y * width + x] = (float) value;
    }

    /**
     * add a value to the pixel value at position (x,y,z) <br>
     * the positions starts at 0 and go to width, height and thickness respectively. contrary to ImageStack z starts from 0 not 1.<br>
     * a test is performed to be sure that position is accessible
     *
     * @param x     position on x axis
     * @param y     position on y axis
     * @param z     position on z axis. starts at 0 not 1 as in ImageStack
     * @param value the value to add to the value at the given position
     */
    public void addToPixel(int x, int y, int z, double value) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= sizez || Double.isNaN(value)) {
            return;
        }
        ((float[]) data.getPixels(z + 1))[y * width + x] += (float) value;
    }

    /**
     * add a value to the pixel value at position (x,y,z) <br>
     * the positions starts at 0 and go to width, height and thickness respectively. contrary to ImageStack z starts from 0 not 1.<br>
     * NO TEST is performed on position to speed process
     *
     * @param x     position on x axis
     * @param y     position on y axis
     * @param z     position on z axis. starts at 0 not 1 as in ImageStack
     * @param value the value to add to the value at the given position
     */
    public void addToPixelFast(ImageStack data, int x, int y, int z, double value) {
        ((float[]) data.getPixels(z + 1))[y * width + x] += (float) value;
    }

    public void operation(final ImagePlus other, final int type) {
        ArrayList<Future> futures = new ArrayList<Future>(sizez);
        for (int z = 0; z < sizez; z++) {
            final int zz = z;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    FloatProcessor ip = (FloatProcessor) data.getProcessor(zz + 1);
                    FloatBlitter fb = new FloatBlitter(ip);
                    fb.copyBits(other.getImageStack().getProcessor(zz + 1), 0, 0, type);
                }
            }));
        }
        try {
            for (Future f : futures) f.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void applyPositivityConstraint() {
        FloatProcessor zeros = new FloatProcessor(width, height);
        for (int z = 0; z < sizez; z++) {
            FloatProcessor ip = (FloatProcessor) data.getProcessor(z + 1);
            FloatBlitter fb = new FloatBlitter(ip);
            fb.copyBits(zeros, 0, 0, FloatBlitter.MAX);
        }
    }

    public void resetPixels() {
        resetPixels(data);
    }

    /**
     * put all pixels to zero
     */
    public void resetPixels(ImageStack data) {
        int length = width * height;
        float[] zeros = new float[length];
        for (int i = 1; i <= sizez; i++) {
            float[] tmp = (float[]) data.getPixels(i);
            System.arraycopy(zeros, 0, tmp, 0, length);
        }
    }

    /**
     * performs Weighted BackProjection
     *
     * @param ts        tilt series to reconstruct
     * @param projector the projector/back projector to use in the algorithm
     */
    public void WBP(final TiltSeries ts, final Projector projector) {
        WBP(ts, projector, 0, height);
    }

    /**
     * performs Weighted BackProjection
     *
     * @param ts        tilt series to reconstruct
     * @param projector the projector/back projector to use in the algorithm
     * @param startY    start of computed slice in Y
     * @param endY      end of computed slice in Y
     */
    public void WBP(final TiltSeries ts, final Projector projector, int startY, int endY) {
        WBP(ts, projector, startY, endY, 1);
    }

    /**
     * performs Weighted BackProjection
     *
     * @param ts        tilt series to reconstruct
     * @param projector the projector/back projector to use in the algorithm
     * @param startY    start of computed slice in Y
     * @param endY      end of computed slice in Y
     * @param update    how much projection to process at the same time
     */
    public void WBP(final TiltSeries ts, final Projector projector, int startY, int endY, int update) {
        double completionIncrement = (endY - startY) / (double) height;
        Chrono time = new Chrono(ts.getImageStackSize());
        if (completion < 0) initCompletion();
        //int nbcpu = Runtime.getRuntime().availableProcessors();
        //int nbcpu= Prefs.getThreads();
        //float[] mask = (weighting) ? createWeightingMask(ts, diameter) : null;
        time.start();
        String method = "BP : ";
        if (projector.isWeighting()) {
            method = "W" + method;
        }
        //setProjectionCenter(ts);
        final ArrayList<ImageProcessor> ip = new ArrayList<ImageProcessor>(update);
        final ArrayList<Integer> eulerIndex = new ArrayList<Integer>(update);
        int index = 0;
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            if (completion < 0) return;
            projector.addProjection(i);
            if (completion < 0) return;
            index++;
            //final DoubleMatrix2D euler = ts.getEulerMatrix(i);
            if (index >= update || i == ts.getImageStackSize() - 1) {
                projector.backproject(startY, endY);
                projector.clearAllProjections();
                index = 0;
            }
            completion += completionIncrement;
            time.stop();
            String strtime = method + 100 * (i + 1) / ts.getImageStackSize() + "% remaining " + time.remainString(i + 1);
            System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(i + 1) + "           ");
//            if (show) {
//                IJ.showStatus(strtime);
//            }
        }
        System.out.println("\n");
    }

    public void WBP(final TiltSeries ts, final Projector projector, ReconstructionParameters params, int startY, int endY) {
        int[] imgsIndexes= params.getAvailableIndexes(ts);
        System.out.println(Arrays.toString(imgsIndexes));
        int update=params.getUpdateNb();
        double completionIncrement = (endY - startY) / (double) height;
        Chrono time = new Chrono(imgsIndexes.length);
        if (completion < 0) initCompletion();
        //int nbcpu = Runtime.getRuntime().availableProcessors();
        //int nbcpu= Prefs.getThreads();
        //float[] mask = (weighting) ? createWeightingMask(ts, diameter) : null;
        time.start();
        String method = "BP : ";
        if (projector.isWeighting()) {
            method = "W" + method;
        }
        //setProjectionCenter(ts);
        final ArrayList<ImageProcessor> ip = new ArrayList<ImageProcessor>(update);
        final ArrayList<Integer> eulerIndex = new ArrayList<Integer>(update);
        int index = 0;
        for (int i = 0; i < imgsIndexes.length; i++) {
            if (completion < 0) return;
            projector.addProjection(imgsIndexes[i]);
            if (completion < 0) return;
            index++;
            //final DoubleMatrix2D euler = ts.getEulerMatrix(i);
            if (index >= update || i == imgsIndexes.length - 1) {
                projector.backproject(startY, endY);
                projector.clearAllProjections();
                index = 0;
            }
            completion += completionIncrement;
            time.stop();
            String strtime = method + 100 * (i + 1) / imgsIndexes.length + "% remaining " + time.remainString(i + 1);
            System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(i + 1) + "           ");
//            if (show) {
//                IJ.showStatus(strtime);
//            }
        }
        System.out.println("\n");
    }



    public ArrayList<Double> OSSART(TiltSeries ts, Projector projector, ReconstructionParameters params) {
        return OSSART(ts, projector, params, 0, height);
    }



    public ArrayList<Double> OSSART(TiltSeries ts, Projector projector, ReconstructionParameters params, int startY, int endY) {
        this.projector = projector;

        System.out.println("OS-SART : ");
        System.out.println("iterations : " + params.getNbIterations());
        System.out.println("relaxation coefficient : " + params.getRelaxationCoefficient());
        params.resetTmpNbCall();
        System.out.println("update : " + params.getUpdateNb());
        if (completion < 0) initCompletion();
        if (exec == null) exec = Executors.newFixedThreadPool(nbcpu);
        totalTimeProjection = 0;
        totalTimeBackProjection = 0;

        FileInfo fi = ts.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts.getFileInfo();
        }
        String savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");

        //if (savedir == "" || savedir == null) savedir = TestDual.DIR; // For test purpose by Antoine, can be removed.

        double[] err;
        double err0 = Double.MAX_VALUE;
        ArrayList<Double> errorsIterations = new ArrayList<Double>();

        System.out.println("temporary files are saved in " + savedir);
        //IJ.log("temporary files are saved in " + savedir);
        savedir += ts.getTitle();

        savedir += "OS-SART(" + startY + "," + endY + ")_";

        Chrono time = new Chrono();
        time.start();
        try {
            PrintWriter pw = new PrintWriter(savedir + params.getNbIterations() + "_error.txt");
            for (int i = 1; i <= params.getNbIterations(); i++) {
                currentIterationNb = i;
                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                System.out.println("(" + startY + ", " + endY + ") iteration #" + i);

                projector.startOfIteration();
                err = osartIteration(projector, ts, params, startY, endY);
                projector.endOfIteration();
                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                //System.out.println("\naverage dissimilarity : " + err1[0] + " \n[" + err1[1] + " (img " + err1[2] + ")" + " - " + err1[3] + " (img " + err1[4] + ")]");
                System.out.println("(" + startY + ", " + endY + ")\ndissimilarity\taverage :\t" + err[0] + "\n" + "\t\tminimum :\t" + err[1] + " (image " + err[2] + ")\n" + "\t\tmaximum :\t" + err[3] + " (image " + err[4] + ")");
                pw.println(i + "\t" + err[0] + "\t" + err[1] + "\t" + err[2] + "\t" + err[3] + "\t" + err[4]);
                pw.flush();
                errorsIterations.add(err[0]);
                if (err[0] > err0 * 1.1) {
                    System.out.println("(" + startY + ", " + endY + ") dissimilarity increasing --> decrease lambda. STOP");
                    //completion=-1000;
                    err0 = err[0];
                } else {
                    err0 = err[0];
                }


                //FileSaver fs = new FileSaver(this);
                //if (i < nbiteration) {
                //fs.saveAsZip(savedir + this.getTitle() + "_rc" + relaxationcoeff + "_ite" + i + ".zip");
                /* }
              if (i >= 2) {
                  File del = new File(savedir + "tmp_ite" + (i - 1) + ".zip");
                  if (del.exists()) {
                      del.delete();
                  }
              }  */
            }
            pw.close();
        } catch (FileNotFoundException e) {
            System.out.println("error opening file" + e);
        }
        time.stop();
        System.out.println("Total time: " + time.delayString());
        System.out.println("time spent in projection-difference: " + Chrono.timeString(totalTimeProjection));
        System.out.println("time spent in backprojection: " + Chrono.timeString(totalTimeBackProjection));
        return errorsIterations;
    }

    protected double[] osartIteration(Projector projector, final TiltSeries ts, ReconstructionParameters params, int startY, int endY) {
        projector.clearAllProjections();
        double completionIncrement = (endY - startY) / (double) height;
        int nbproj = ts.getImageStackSize();
        int[] indexes = orderShuffleART(ts, params);
        System.out.println(Arrays.toString(indexes));
        double[] err1 = new double[5];
        //setProjectionCenter(ts);
        final double relaxationcoeff= params.relaxationCoefficient;
        final int update = params.updateNb;
        float factorimg = (float) (relaxationcoeff / update);
        int diffIndex = 0;
        double som = 0;
        double errmin = Float.MAX_VALUE;
        double errmax = 0;
        int imin = 0;
        int imax = 0;
        int cc = 0;

        Chrono time = new Chrono(nbproj * 2);
        Chrono timeProjection = new Chrono();
        Chrono timeBackProjection = new Chrono();
        time.start();
        for (int t = 0; t < indexes.length; t++) {
            //projecting
            if (completion < 0) return err1;
            projector.addProjection(indexes[t]);


            if (completion < 0) return err1;

            diffIndex++;
            //backprojecting
            if (completion < 0) return err1;
            // completion += completionIncrement;
//            cc++;
//            time.stop();
//            String strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / (nbproj*2) + "% remaining " + time.remainString(cc);
//            if(displayIterationInfo) System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
//            if (show) {
//                IJ.showStatus(strtime);
//            }
            if (diffIndex == update || t == ts.getImageStackSize() - 1) {
                //System.out.println("updating "+diffIndex);

                //diffimgs[d].filter(ImageProcessor.MEDIAN_FILTER);
                //projectBasicThreaded(diffEuler[d], diffimgs[d], null, factorimg, false, electronTomographyNormalization, pcenters[2], pcenters[3]);
                timeProjection.start();
                double[] error = projector.projectAndDifference(factorimg, startY, endY);
                cc += diffIndex;
                time.stop();
                String strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / (nbproj * 2) + "% remaining " + time.remainString(cc);
                if (displayIterationInfo)
                    System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");

                completion += completionIncrement * diffIndex;
                for (int e = 0; e < error.length; e++) {
                    som += error[e];
                    if (error[e] > errmax) {
                        errmax = error[e];
                        imax = indexes[t - error.length + e + 1];
                    } else if (error[e] < errmin) {
                        errmin = error[e];
                        imin = indexes[t - error.length + e + 1];
                    }
                }
                timeProjection.stop();
                totalTimeProjection += timeProjection.delay();
                timeBackProjection.start();
                projector.backproject(startY, endY);
                timeBackProjection.stop();
                totalTimeBackProjection += timeBackProjection.delay();
                projector.clearAllProjections();
                completion += completionIncrement * diffIndex;
                cc += diffIndex;
                strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / (nbproj * 2) + "% remaining " + time.remainString(cc);
                if (displayIterationInfo)
                    System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
//                if (show) {
//                    IJ.showStatus(strtime);
//                }

                diffIndex = 0;
            }

        }
        String strtime = "[" + startY + "-" + endY + "] : 100% ";
        if (displayIterationInfo)
            System.out.print("\r                                                                 \r" + strtime + " total :" + time.delayString() + "           ");
//
        err1[0] = Math.sqrt(som / (double) (nbproj * width * height));
        err1[1] = Math.sqrt(errmin / (width * height));
        err1[2] = imin;
        err1[3] = Math.sqrt(errmax / (width * height));
        err1[4] = imax;
        System.out.println("");
        return err1;
    }

    public int[] orderShuffleART(TiltSeries ts, ReconstructionParameters params) {
        int[] availableImages=params.getAvailableIndexes(ts);
        int nbproj = availableImages.length;
        DoubleMatrix1D[] W = new DoubleMatrix1D[nbproj];
        for (int im = 0; im < nbproj; im++) {
            W[im] = ts.getAlignment().getEulerMatrix(im).viewRow(2);
        }

        boolean[] atraiter = new boolean[nbproj];
        Arrays.fill(atraiter,true);

        System.out.println("nb proj:" + nbproj);
        int[] indexes = new int[nbproj];
        int cc = 0;
        //Random r = new Random(System.currentTimeMillis());
        int im = 0;
        int updateindex = 0;
        while (cc < nbproj) {
            //if first projection considered -> randomly taken
            if (cc == 0) {
                im = (int) (Math.random() * nbproj);
                //im = r.nextInt(nbproj);
            } else {
                //if not the first projection considered -> we take the nearest to perpendicular projection
                double mini = Double.MAX_VALUE;
                double sp;
                int omini = -1;
                for (int o = 0; o < atraiter.length; o++) {
                    if (atraiter[o]) {
                        sp = Math.abs(W[im].zDotProduct(W[o]));
                        if (sp < mini) {
                            mini = sp;
                            omini = o;
                        }
                    }

                }
                im = omini;
            }
            if (atraiter[im]) {
                indexes[cc] = availableImages[im];
                atraiter[im] = false;
                cc++;
            }
        }
        return indexes;
    }

    public ArrayList<Double> regularization(TiltSeries ts, Projector projector, ReconstructionParameters params, int startY, int endY) {
        System.out.println("regularization : ");
        System.out.println("iterations : " + params.getNbIterations());
        if (completion < 0) initCompletion();
        if (exec == null) exec = Executors.newFixedThreadPool(nbcpu);
        totalTimeProjection = 0;
        totalTimeBackProjection = 0;
        long totalTimeEndIteration = 0;
        long totalTimeStartIteration = 0;
        Chrono timeEndIteration = new Chrono();
        Chrono timeStartIteration = new Chrono();
        final int update = ts.getImageStackSize();
        double factor = 1.0 / ts.getImageStackSize() * update;

        FileInfo fi = ts.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts.getFileInfo();
        }
        String savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");

        //if (savedir == "" || savedir == null) savedir = TestDual.DIR; // For test purpose by Antoine, can be removed.

        double[] err;
        double err0 = Double.MAX_VALUE;
        ArrayList<Double> errorsIterations = new ArrayList<Double>();

        System.out.println("temporary files are saved in " + savedir);
        //IJ.log("temporary files are saved in " + savedir);
        savedir += ts.getTitle();

        savedir += "Regularization(" + startY + "," + endY + ")_";

        Chrono time = new Chrono();
        time.start();
        try {
            PrintWriter pw = new PrintWriter(savedir + params.getNbIterations() + "_error.txt");
            for (int i = 1; i <= params.getNbIterations(); i++) {
                currentIterationNb = i;
                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                System.out.println("(" + startY + ", " + endY + ") iteration #" + i);
                timeStartIteration.start();
                projector.startOfIteration();
                timeStartIteration.stop();
                totalTimeStartIteration += timeStartIteration.delay();

                err = osartIteration(projector, ts, params, startY, endY);

                timeEndIteration.start();
                projector.endOfIteration();
                timeEndIteration.stop();
                totalTimeEndIteration += timeEndIteration.delay();
                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                //System.out.println("\naverage dissimilarity : " + err1[0] + " \n[" + err1[1] + " (img " + err1[2] + ")" + " - " + err1[3] + " (img " + err1[4] + ")]");
                System.out.println("(" + startY + ", " + endY + ")\ndissimilarity\taverage :\t" + err[0] + "\n" + "\t\tminimum :\t" + err[1] + " (image " + err[2] + ")\n" + "\t\tmaximum :\t" + err[3] + " (image " + err[4] + ")");
                pw.println(i + "\t" + err[0] + "\t" + err[1] + "\t" + err[2] + "\t" + err[3] + "\t" + err[4]);
                pw.flush();
                errorsIterations.add(err[0]);
                if (err[0] > err0 * 1.1) {
                    System.out.println("(" + startY + ", " + endY + ") dissimilarity increasing --> decrease lambda. STOP");
                    //completion=-1000;
                    err0 = err[0];
                } else {
                    err0 = err[0];
                }


                //FileSaver fs = new FileSaver(this);
                //if (i < nbiteration) {
                //fs.saveAsZip(savedir + this.getTitle() + "_rc" + relaxationcoeff + "_ite" + i + ".zip");
                    /* }
                  if (i >= 2) {
                      File del = new File(savedir + "tmp_ite" + (i - 1) + ".zip");
                      if (del.exists()) {
                          del.delete();
                      }
                  }  */
            }
            pw.close();
        } catch (FileNotFoundException e) {
            System.out.println("error opening file" + e);
        }
        time.stop();
        System.out.println("Total time: " + time.delayString());
        System.out.println("time spent in projection-difference: " + Chrono.timeString(totalTimeProjection));
        System.out.println("time spent in backprojection: " + Chrono.timeString(totalTimeBackProjection));
        System.out.println("time spent in start iteration : " + Chrono.timeString(totalTimeEndIteration));
        System.out.println("time spent in end iteration : " + Chrono.timeString(totalTimeEndIteration));
        return errorsIterations;
    }

    public ArrayList<Double> ADASIRT(TiltSeries ts1, Projector projector1, TiltSeries ts2, Projector projector2, ReconstructionParameters params, int startY, int endY) {
        int nbiteration=params.getNbIterations();
        System.out.println("ADASIRT : ");
        System.out.println("iterations : " + nbiteration);
        if (completion < 0) initCompletion();
        if (exec == null) exec = Executors.newFixedThreadPool(nbcpu);
        totalTimeProjection = 0;
        totalTimeBackProjection = 0;
        long totalTimeEndIteration = 0;
        long totalTimeStartIteration = 0;
        Chrono timeEndIteration = new Chrono();
        Chrono timeStartIteration = new Chrono();
        //final int update=ts.getImageStackSize();
        //double factor=1.0/ts.getImageStackSize()*update;
        double factor = 1.2;
        FileInfo fi = ts1.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts1.getFileInfo();
        }
        String savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
        // TEST by Antoine
        //if (savedir == "" || savedir == null) savedir = TestDual.DIR; // For test purpose by Antoine, can be removed.

        double[] err;
        double err0 = Double.MAX_VALUE;
        ArrayList<Double> errorsIterations = new ArrayList<Double>();

        System.out.println("temporary files are saved in " + savedir);
        //IJ.log("temporary files are saved in " + savedir);
        savedir += ts1.getTitle();

        savedir += "ADASIRT(" + startY + "," + endY + ")_";

        Chrono time = new Chrono();
        time.start();
        try {
            PrintWriter pw = new PrintWriter(savedir + nbiteration + "_error.txt");
            for (int i = 1; i <= nbiteration; i++) {
                currentIterationNb = i;
                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                System.out.println("(" + startY + ", " + endY + ") iteration #" + i);
                timeStartIteration.start();
                projector1.startOfIteration();
                timeStartIteration.stop();
                totalTimeStartIteration += timeStartIteration.delay();

                err = osartIteration(projector1, ts1, params, startY, endY);

                timeEndIteration.start();
                projector1.endOfIteration();
                timeEndIteration.stop();
                totalTimeEndIteration += timeEndIteration.delay();

                projector2.startOfIteration();
                timeStartIteration.stop();
                totalTimeStartIteration += timeStartIteration.delay();

                err = osartIteration(projector2, ts2, params, startY, endY);
                timeEndIteration.start();
                projector1.endOfIteration();
                timeEndIteration.stop();
                totalTimeEndIteration += timeEndIteration.delay();
                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                //System.out.println("\naverage dissimilarity : " + err1[0] + " \n[" + err1[1] + " (img " + err1[2] + ")" + " - " + err1[3] + " (img " + err1[4] + ")]");
                System.out.println("(" + startY + ", " + endY + ")\ndissimilarity\taverage :\t" + err[0] + "\n" + "\t\tminimum :\t" + err[1] + " (image " + err[2] + ")\n" + "\t\tmaximum :\t" + err[3] + " (image " + err[4] + ")");
                pw.println(i + "\t" + err[0] + "\t" + err[1] + "\t" + err[2] + "\t" + err[3] + "\t" + err[4]);
                pw.flush();
                errorsIterations.add(err[0]);
                if (err[0] > err0 * 1.1) {
                    System.out.println("(" + startY + ", " + endY + ") dissimilarity increasing --> decrease lambda. STOP");
                    completion = -1000;
                } else {
                    err0 = err[0];
                }


                //FileSaver fs = new FileSaver(this);
                //if (i < nbiteration) {
                //fs.saveAsZip(savedir + this.getTitle() + "_rc" + relaxationcoeff + "_ite" + i + ".zip");
                        /* }
                      if (i >= 2) {
                          File del = new File(savedir + "tmp_ite" + (i - 1) + ".zip");
                          if (del.exists()) {
                              del.delete();
                          }
                      }  */
            }
            pw.close();
        } catch (FileNotFoundException e) {
            System.out.println("error opening file" + e);
        }
        time.stop();
        System.out.println("Total time: " + time.delayString());
        System.out.println("time spent in projection-difference: " + Chrono.timeString(totalTimeProjection));
        System.out.println("time spent in backprojection: " + Chrono.timeString(totalTimeBackProjection));
        System.out.println("time spent in start iteration : " + Chrono.timeString(totalTimeEndIteration));
        System.out.println("time spent in end iteration : " + Chrono.timeString(totalTimeEndIteration));
        return errorsIterations;
    }

    /**
     * ADOSSART - Alternative Dual-axis Ordered Subset SART
     * @author Antoine Cossa
     * @date April 2019
     *
     * @param ts1           First TiltSeries
     * @param projector1    Projector used for projections and backprojections of the first TiltSeries
     * @param ts2           Second TiltSeries
     * @param projector2    Projector used for projections and backprojections of the second TiltSeries
     *                                                                                    0 / 1 / 2
     * @param startY
     * @param endY
     * @return
     */
    public ArrayList<Double> ADOSSART(TiltSeries ts1, Projector projector1, TiltSeries ts2, Projector projector2, ReconstructionParameters params, int startY, int endY) {
        int nbiteration=params.getNbIterations();
        System.out.println("ADOSSART : ");
        System.out.println("iterations : " + nbiteration);

        if (completion < 0) initCompletion();
        if (exec == null) exec = Executors.newFixedThreadPool(nbcpu);
        totalTimeProjection = 0;
        totalTimeBackProjection = 0;
        long totalTimeEndIteration = 0;
//        long totalTimeStartIteration = 0;
        Chrono timeEndIteration = new Chrono();
//        Chrono timeStartIteration = new Chrono();
        //final int update=ts.getImageStackSize();
//        final int update = 1;
//        double factor=1.0/((ts1.getImageStackSize() + ts2.getStackSize()) * update);
//        double factor = 1.2;
        double factor = params.relaxationCoefficient;
//        double factor = 1.0 / (nbiteration * 2); // TEST
//        double factor = 1.0 / nbiteration;
        FileInfo fi = ts1.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts1.getFileInfo();
        }
        String savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
        // TEST by Antoine
        //String savedir = TestDual.DIR;
//        if (savedir.equals("") || savedir == null) savedir = TestDual.DIR; // For test purpose by Antoine, can be removed.

        double[] err;
        double err0 = Double.MAX_VALUE;
        ArrayList<Double> errorsIterations = new ArrayList<>();

        System.out.println("temporary files are saved in " + savedir);
        //IJ.log("temporary files are saved in " + savedir);
        savedir += ts1.getTitle();

        savedir += "ADASIRT(" + startY + "," + endY + ")_";

        Chrono time = new Chrono();
        time.start();
        try {
            PrintWriter pw = new PrintWriter(savedir + nbiteration + "_error.txt");
            for (int i = 1; i <= nbiteration; i++) {
                currentIterationNb = i;
                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                System.out.println("(" + startY + ", " + endY + ") iteration #" + i);

                err = osartIterationDual(projector1, ts1, projector2, ts2, params, startY, endY);

                timeEndIteration.start();
                projector1.endOfIteration();
                projector2.endOfIteration();
                timeEndIteration.stop();
                totalTimeEndIteration += timeEndIteration.delay();

                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                //System.out.println("\naverage dissimilarity : " + err1[0] + " \n[" + err1[1] + " (img " + err1[2] + ")" + " - " + err1[3] + " (img " + err1[4] + ")]");
                System.out.println("(" + startY + ", " + endY + ")\ndissimilarity\taverage :\t" + err[0] + "\n" + "\t\tminimum :\t" + err[1] + " (image " + err[2] + ")\n" + "\t\tmaximum :\t" + err[3] + " (image " + err[4] + ")");
                pw.println(i + "\t" + err[0] + "\t" + err[1] + "\t" + err[2] + "\t" + err[3] + "\t" + err[4]);
                pw.flush();
                errorsIterations.add(err[0]);
                if (err[0] > err0 * 1.1) {
                    System.out.println("(" + startY + ", " + endY + ") dissimilarity increasing --> decrease lambda. STOP");
                    completion = -1000;
                } else {
                    err0 = err[0];
                }

                //FileSaver fs = new FileSaver(this);
                //if (i < nbiteration) {
                //fs.saveAsZip(savedir + this.getTitle() + "_rc" + relaxationcoeff + "_ite" + i + ".zip");
                        /* }
                      if (i >= 2) {
                          File del = new File(savedir + "tmp_ite" + (i - 1) + ".zip");
                          if (del.exists()) {
                              del.delete();
                          }
                      }  */
            }
            pw.close();
        } catch (FileNotFoundException e) {
            System.out.println("error opening file" + e);
        }
        time.stop();
        System.out.println("Total time: " + time.delayString());
        System.out.println("time spent in projection-difference: " + Chrono.timeString(totalTimeProjection));
        System.out.println("time spent in backprojection: " + Chrono.timeString(totalTimeBackProjection));
        System.out.println("time spent in start iteration : " + Chrono.timeString(totalTimeEndIteration));
        System.out.println("time spent in end iteration : " + Chrono.timeString(totalTimeEndIteration));
        return errorsIterations;
    }


    public ArrayList<Double> ADASIRT2Single(TiltSeries ts, Projector projector, ReconstructionParameters params, int startY, int endY) {
        int nbiteration=params.getNbIterations();
        System.out.println("ADASIRT : ");
        System.out.println("iterations : " + nbiteration);

        if (completion < 0) initCompletion();
        if (exec == null) exec = Executors.newFixedThreadPool(nbcpu);
        totalTimeProjection = 0;
        totalTimeBackProjection = 0;
        long totalTimeEndIteration = 0;
        long totalTimeStartIteration = 0;
        Chrono timeEndIteration = new Chrono();
        Chrono timeStartIteration = new Chrono();

        double factor = 1.0 / nbiteration;
        FileInfo fi = ts.getOriginalFileInfo();
        if (fi == null) {
            fi = ts.getFileInfo();
        }
        String savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
        // TEST by Antoine
        //if (savedir == "" || savedir == null) savedir = TestDual.DIR; // For test purpose by Antoine, can be removed.

        double[] err;
        double err0 = Double.MAX_VALUE;
        ArrayList<Double> errorsIterations = new ArrayList<>();

        System.out.println("temporary files are saved in " + savedir);
        //IJ.log("temporary files are saved in " + savedir);
        savedir += ts.getTitle();

        savedir += "ADASIRT(" + startY + "," + endY + ")_";

        Chrono time = new Chrono();
        time.start();
        try {
            PrintWriter pw = new PrintWriter(savedir + nbiteration + "_error.txt");
            for (int i = 1; i <= nbiteration; i++) {
                currentIterationNb = i;
                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }
                System.out.println("(" + startY + ", " + endY + ") iteration #" + i);

                err = osartIteration(projector, ts, params, startY, endY);

                timeEndIteration.start();
                projector.endOfIteration();
                timeEndIteration.stop();
                totalTimeEndIteration += timeEndIteration.delay();

                if (completion < 0) {
                    pw.close();
                    return errorsIterations;
                }

                System.out.println("(" + startY + ", " + endY + ")\ndissimilarity\taverage :\t" + err[0] + "\n" + "\t\tminimum :\t" + err[1] + " (image " + err[2] + ")\n" + "\t\tmaximum :\t" + err[3] + " (image " + err[4] + ")");
                pw.println(i + "\t" + err[0] + "\t" + err[1] + "\t" + err[2] + "\t" + err[3] + "\t" + err[4]);
                pw.flush();
                errorsIterations.add(err[0]);
                if (err[0] > err0 * 1.1) {
                    System.out.println("(" + startY + ", " + endY + ") dissimilarity increasing --> decrease lambda. STOP");
                    completion = -1000;
                } else {
                    err0 = err[0];
                }
            }
            pw.close();
        } catch (FileNotFoundException e) {
            System.out.println("error opening file" + e);
        }
        time.stop();
        System.out.println("Total time: " + time.delayString());
        System.out.println("time spent in projection-difference: " + Chrono.timeString(totalTimeProjection));
        System.out.println("time spent in backprojection: " + Chrono.timeString(totalTimeBackProjection));
        System.out.println("time spent in start iteration : " + Chrono.timeString(totalTimeEndIteration));
        System.out.println("time spent in end iteration : " + Chrono.timeString(totalTimeEndIteration));
        return errorsIterations;
    }

//    public ArrayList<Double> ADASIRT2(TiltSeries ts1, Projector projector1,TiltSeries ts2, Projector projector2, int nbiteration, int type, int startY, int endY) {
//        System.out.println("ADASIRT : ");
//        System.out.println("iterations : " + nbiteration);
//
//        if (completion < 0) initCompletion();
//        if (exec == null) exec = Executors.newFixedThreadPool(nbcpu);
//        totalTimeProjection = 0;
//        totalTimeBackProjection = 0;
//        long totalTimeEndIteration = 0;
//        long totalTimeStartIteration = 0;
//        Chrono timeEndIteration = new Chrono();
//        Chrono timeStartIteration = new Chrono();
//        //final int update=ts.getImageStackSize();
//        //double factor=1.0/ts.getImageStackSize()*update;
//        double factor = 1.2;
//        FileInfo fi = ts1.getOriginalFileInfo();
//        if (fi == null) {
//            //System.out.println("original File Info null");
//            fi = ts1.getFileInfo();
//        }
////        String savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
//        // TEST by Antoine
//        String savedir = "C:\\Users\\antoi\\Desktop\\Stage\\";
//        double[] err;
//        double err0 = Double.MAX_VALUE;
//        ArrayList<Double> errorsIterations = new ArrayList<>();
//
//        System.out.println("temporary files are saved in " + savedir);
//        //IJ.log("temporary files are saved in " + savedir);
//        savedir += ts1.getTitle();
//
//        savedir += "ADASIRT("+startY+","+endY+")_";
//
//        Chrono time = new Chrono();
//        time.start();
//        try {
//            PrintWriter pw = new PrintWriter(savedir + nbiteration + "_error.txt");
//            for (int i = 1; i <= nbiteration; i++) {
//                currentIterationNb = i;
//                if (completion < 0) {
//                    pw.close();
//                    return errorsIterations;
//                }
//                System.out.println("("+startY+", "+endY+") iteration #" + i);
//
//                timeStartIteration.start();
//                projector1.startOfIteration();
//                projector2.startOfIteration();
//                timeStartIteration.stop();
//                totalTimeStartIteration+=timeStartIteration.delay();
//
//                err = osartIterationDual(projector1, ts1, projector2, ts2, factor, 1, type, startY, endY);
//
//                timeEndIteration.start();
//                projector1.endOfIteration();
//                projector2.endOfIteration();
//                timeEndIteration.stop();
//                totalTimeEndIteration += timeEndIteration.delay();
//
//                if (completion < 0) {
//                    pw.close();
//                    return errorsIterations;
//                }
//                //System.out.println("\naverage dissimilarity : " + err1[0] + " \n[" + err1[1] + " (img " + err1[2] + ")" + " - " + err1[3] + " (img " + err1[4] + ")]");
//                System.out.println("("+startY+", "+endY+")\ndissimilarity\taverage :\t" + err[0]+"\n"+"\t\tminimum :\t" + err[1] + " (image " + err[2] + ")\n"+"\t\tmaximum :\t" + err[3] + " (image " + err[4] + ")");
//                pw.println(i + "\t" + err[0] + "\t" + err[1] + "\t" + err[2] + "\t" + err[3] + "\t" + err[4]);
//                pw.flush();
//                errorsIterations.add(err[0]);
//                if (err[0] > err0 * 1.1) {
//                    System.out.println("("+startY+", "+endY+") dissimilarity increasing --> decrease lambda. STOP");
//                    completion=-1000;
//                } else {
//                    err0 = err[0];
//                }
//
//                //FileSaver fs = new FileSaver(this);
//                //if (i < nbiteration) {
//                //fs.saveAsZip(savedir + this.getTitle() + "_rc" + relaxationcoeff + "_ite" + i + ".zip");
//                        /* }
//                      if (i >= 2) {
//                          File del = new File(savedir + "tmp_ite" + (i - 1) + ".zip");
//                          if (del.exists()) {
//                              del.delete();
//                          }
//                      }  */
//            }
//            pw.close();
//        } catch (FileNotFoundException e) {
//            System.out.println("error opening file" + e);
//        }
//        time.stop();
//        System.out.println("Total time: " + time.delayString());
//        System.out.println("time spent in projection-difference: " + Chrono.timeString(totalTimeProjection));
//        System.out.println("time spent in backprojection: " + Chrono.timeString(totalTimeBackProjection));
//        System.out.println("time spent in start iteration : " + Chrono.timeString(totalTimeEndIteration));
//        System.out.println("time spent in end iteration : " + Chrono.timeString(totalTimeEndIteration));
//        return errorsIterations;
//    }

    private double[] osartIterationDual(Projector projector1, final TiltSeries ts1, Projector projector2, final TiltSeries ts2, ReconstructionParameters params, int startY, int endY) {
        double relaxationcoeff=params.getRelaxationCoefficient();
        int update=params.getUpdateNb();

        int alternate = 2;
        projector1.clearAllProjections();
        projector2.clearAllProjections();
        double completionIncrement = (endY - startY) / (double) height;
        int nbproj1 = ts1.getImageStackSize();
        int nbproj2 = ts2.getImageStackSize();
        int[] indexes1 = orderShuffleART(ts1, params);
        int[] indexes2 = orderShuffleART(ts2, params);
        double[] err1 = new double[5];
        float factorimg = (float) (relaxationcoeff / update);
        int diffIndex = 0;
        double som = 0;
        double errmin = Float.MAX_VALUE;
        double errmax = 0;
        int imin = 0;
        int imax = 0;
        int cc = 0;

        Chrono time = new Chrono((nbproj1 + nbproj2) * 2);
        Chrono timeProjection = new Chrono();
        Chrono timeBackProjection = new Chrono();
        time.start();

        for (int t = 0; t < indexes1.length - 1; t += alternate) {
            // Two images from TS1
            for (int i = 0; i < alternate; i++) {

                if (t + 1 >= indexes1.length) break; // Not so pretty...

//              projecting
                if (completion < 0) return err1;
                projector1.addProjection(indexes1[t + i]);
                //System.out.println("project "+indexes1[t+i]+ " from first tilt series");
                if (completion < 0) return err1;
                diffIndex++;

                //backprojecting
                if (completion < 0) return err1;
                if (diffIndex == update || t + i == ts1.getImageStackSize() - 1) {
                    timeProjection.start();
                    double[] error = projector1.projectAndDifference(factorimg, startY, endY);
                    cc += diffIndex;
                    time.stop();
                    String strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / ((nbproj1 + nbproj2) * 2) + "% remaining " + time.remainString(cc);
                    if (displayIterationInfo)
                        System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");

                    completion += completionIncrement * diffIndex;
                    for (int e = 0; e < error.length; e++) {
                        som += error[e];
                        if (error[e] > errmax) {
                            errmax = error[e];
                            imax = indexes1[t + i - error.length + e + 1];
                        } else if (error[e] < errmin) {
                            errmin = error[e];
                            imin = indexes1[t + i - error.length + e + 1];
                        }
                    }
                    timeProjection.stop();
                    totalTimeProjection += timeProjection.delay();
                    timeBackProjection.start();
                    projector1.backproject(startY, endY);
                    timeBackProjection.stop();
                    totalTimeBackProjection += timeBackProjection.delay();
                    projector1.clearAllProjections();
                    completion += completionIncrement * diffIndex;
                    cc += diffIndex;
                    strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / ((nbproj1 + nbproj2) * 2) + "% remaining " + time.remainString(cc);
                    if (displayIterationInfo)
                        System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
                    diffIndex = 0;
                }

            }

            // Two images from TS2
            for (int i = 0; i < alternate; i++) {

                if (t + 1 >= indexes2.length) break; // Not so pretty...

                // projecting
                if (completion < 0) return err1;
                projector2.addProjection(indexes2[t + i]);
                //System.out.println("project "+indexes2[t+i]+ " from second tilt series");
                if (completion < 0) return err1;
                diffIndex++;

                // backprojecting
                if (completion < 0) return err1;
                if (diffIndex == update || t + i == ts2.getImageStackSize() - 1) {
                    timeProjection.start();
                    double[] error = projector2.projectAndDifference(factorimg, startY, endY);
                    cc += diffIndex;
                    time.stop();
                    String strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / ((nbproj1 + nbproj2) * 2) + "% remaining " + time.remainString(cc);
                    if (displayIterationInfo)
                        System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");

                    completion += completionIncrement * diffIndex;
                    for (int e = 0; e < error.length; e++) {
                        som += error[e];
                        if (error[e] > errmax) {
                            errmax = error[e];
                            imax = indexes2[t + i - error.length + e + 1];
                        } else if (error[e] < errmin) {
                            errmin = error[e];
                            imin = indexes2[t + i - error.length + e + 1];
                        }
                    }
                    timeProjection.stop();
                    totalTimeProjection += timeProjection.delay();
                    timeBackProjection.start();
                    projector2.backproject(startY, endY);
                    timeBackProjection.stop();
                    totalTimeBackProjection += timeBackProjection.delay();
                    projector2.clearAllProjections();
                    completion += completionIncrement * diffIndex;
                    cc += diffIndex;
                    strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / ((nbproj1 + nbproj2) * 2) + "% remaining " + time.remainString(cc);
                    if (displayIterationInfo)
                        System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
                    diffIndex = 0;
                }

            }
        }

        String strtime = "[" + startY + "-" + endY + "] : 100% ";
        if (displayIterationInfo)
            System.out.print("\r                                                                 \r"
                    + strtime + " total :" + time.delayString() + "           ");

        err1[0] = Math.sqrt(som / (double) ((nbproj1 + nbproj2) * width * height));
        err1[1] = Math.sqrt(errmin / (width * height));
        err1[2] = imin;
        err1[3] = Math.sqrt(errmax / (width * height));
        err1[4] = imax;
        System.out.println("");
        return err1;
    }

    public TomoReconstruction2 getCopy(){
        return new TomoReconstruction2(this.duplicate());
    }

    public void copyFrom(TomoReconstruction2 recToCopy){
        for(int z=0;z<getSizez();z++){
            ImageProcessor tmp=this.data.getProcessor(z+1);
            tmp.copyBits(recToCopy.data.getProcessor(z+1),0,0, Blitter.COPY);
        }
    }

//    /**
//     * This code was used to reconstruct both tilt-series separately, in two
//     * different volumes.
//     */
//    protected double[] osartIterationDual(Projector projector1, final TiltSeries ts1, Projector projector2, final TiltSeries ts2, final double relaxationcoeff, int update,int type, int startY, int endY, boolean firstSeries) {
//        projector1.clearAllProjections();
//        projector2.clearAllProjections();
//        double completionIncrement = (endY - startY) / (double) height;
//        int nbproj1 = ts1.getImageStackSize();
//        int nbproj2 = ts2.getImageStackSize();
//        int[] indexes1 = orderShuffleART(ts1,type);
//        int[] indexes2 = orderShuffleART(ts2,type);
//        double[] err1 = new double[5];
//        float factorimg = (float) (relaxationcoeff / update);
//        int diffIndex = 0;
//        double som = 0;
//        double errmin = Float.MAX_VALUE;
//        double errmax = 0;
//        int imin = 0;
//        int imax = 0;
//        int cc = 0;
//
//        Chrono time = new Chrono((nbproj1 + nbproj2) * 2);
//        Chrono timeProjection = new Chrono();
//        Chrono timeBackProjection = new Chrono();
//        time.start();
//
//        for (int t = 0; t < indexes1.length - 1; t++) {
//
////            // Two images from TS1
//            if (firstSeries == true) {
////                for (int i = 0; i < 2; i++) {
//                if (t + 1 >= indexes1.length) break; // Not so pretty...
//
////              projecting
//                if (completion < 0) return err1;
//                projector1.addProjection(indexes1[t]);
//                if (completion < 0) return err1;
//                diffIndex++;
//
//                //backprojecting
//                if (completion < 0) return err1;
//                if (diffIndex == update || t == ts1.getImageStackSize() - 1) {
//                    timeProjection.start();
//                    double[] error = projector1.projectAndDifference(factorimg, startY, endY);
//                    cc += diffIndex;
//                    time.stop();
//                    String strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / (nbproj1 * 2) + "% remaining " + time.remainString(cc);
//                    if (displayIterationInfo)
//                        System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
//
//                    completion += completionIncrement * diffIndex;
//                    for (int e = 0; e < error.length; e++) {
//                        som += error[e];
//                        if (error[e] > errmax) {
//                            errmax = error[e];
//                            imax = indexes1[t - error.length + e + 1];
//                        } else if (error[e] < errmin) {
//                            errmin = error[e];
//                            imin = indexes1[t - error.length + e + 1];
//                        }
//                    }
//                    timeProjection.stop();
//                    totalTimeProjection += timeProjection.delay();
//                    timeBackProjection.start();
//                    projector1.backproject(startY, endY);
//                    timeBackProjection.stop();
//                    totalTimeBackProjection += timeBackProjection.delay();
//                    projector1.clearAllProjections();
//                    completion += completionIncrement * diffIndex;
//                    cc += diffIndex;
//                    strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / ((nbproj1 + nbproj2) * 2) + "% remaining " + time.remainString(cc);
//                    if (displayIterationInfo)
//                        System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
//                    diffIndex = 0;
//                }
//            } else {
//                // Two images from TS2
////                for (int i = 0; i < 2; i++) {
//
//                if (t + 1 >= indexes2.length) break; // Not so pretty...
//
////              projecting
//                if (completion < 0) return err1;
//                projector2.addProjection(indexes2[t]);
//                if (completion < 0) return err1;
//                diffIndex++;
//
//                //backprojecting
//                if (completion < 0) return err1;
//                if (diffIndex == update || t == ts2.getImageStackSize() - 1) {
//                    timeProjection.start();
//                    double[] error = projector2.projectAndDifference(factorimg, startY, endY);
//                    cc += diffIndex;
//                    time.stop();
//                    String strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / ((nbproj1 + nbproj2) * 2) + "% remaining " + time.remainString(cc);
//                    if (displayIterationInfo)
//                        System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
//
//                    completion += completionIncrement * diffIndex;
//                    for (int e = 0; e < error.length; e++) {
//                        som += error[e];
//                        if (error[e] > errmax) {
//                            errmax = error[e];
//                            imax = indexes2[t - error.length + e + 1];
//                        } else if (error[e] < errmin) {
//                            errmin = error[e];
//                            imin = indexes2[t - error.length + e + 1];
//                        }
//                    }
//                    timeProjection.stop();
//                    totalTimeProjection += timeProjection.delay();
//                    timeBackProjection.start();
//                    projector2.backproject(startY, endY);
//                    timeBackProjection.stop();
//                    totalTimeBackProjection += timeBackProjection.delay();
//                    projector2.clearAllProjections();
//                    completion += completionIncrement * diffIndex;
//                    cc += diffIndex;
//                    strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / (nbproj2 * 2) + "% remaining " + time.remainString(cc);
//                    if (displayIterationInfo)
//                        System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
//                    diffIndex = 0;
//                }
//            }
//        }
////        ######################################################################
//
//        String strtime = "[" + startY + "-" + endY + "] : 100% ";
//        if(displayIterationInfo) System.out.print("\r                                                                 \r"
//                + strtime + " total :" + time.delayString() + "           ");
//
//        err1[0] = Math.sqrt(som / (double) ((nbproj1 + nbproj2) * width * height));
//        err1[1] = Math.sqrt(errmin / (width * height));
//        err1[2] = imin;
//        err1[3] = Math.sqrt(errmax / (width * height));
//        err1[4] = imax;
//        System.out.println("");
//        return err1;
//    }


//    /**
//     * Perform an OS-SART on ????????????????????
//     *
//     * @param projector
//     * @param ts
//     * @param shuffleIndex
//     * @param t
//     * @param update
//     * @param startY
//     * @param endY
//     */
//    public double[] osartSubIteration(Projector projector, final TiltSeries ts, int[] shuffleIndex, int t, int update, int startY, int endY, double[] err1) {
//
//        //projecting
//        if (completion < 0) return err1;
//        projector.addProjection(shuffleIndex[t]);
//        projector.addProjection(shuffleIndex[t]);
//
//        if (completion < 0) return err1;
//        diffIndex++;
//        //backprojecting
//        if (completion < 0) return err1;
//        if (diffIndex == update || t == ts.getImageStackSize() - 1) {
//            timeProjection.start();
//            double[] error = projector.projectAndDifference(factorimg, startY, endY);
//            cc += diffIndex;
//            time.stop();
//            String strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / (nbproj * 2) + "% remaining " + time.remainString(cc);
//            if (displayIterationInfo)
//                System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
//
//            completion += completionIncrement * diffIndex;
//            for (int e = 0; e < error.length; e++) {
//                som += error[e];
//                if (error[e] > errmax) {
//                    errmax = error[e];
//                    imax = indexes[t - error.length + e + 1];
//                } else if (error[e] < errmin) {
//                    errmin = error[e];
//                    imin = indexes[t - error.length + e + 1];
//                }
//            }
//            timeProjection.stop();
//            totalTimeProjection += timeProjection.delay();
//            timeBackProjection.start();
//            projector.backproject(startY, endY);
//            timeBackProjection.stop();
//            totalTimeBackProjection += timeBackProjection.delay();
//            projector.clearAllProjections();
//            completion += completionIncrement * diffIndex;
//            cc += diffIndex;
//            strtime = "[" + startY + "-" + endY + "] : " + 100 * cc / (nbproj * 2) + "% remaining " + time.remainString(cc);
//            if (displayIterationInfo)
//                System.out.print("\r                                                                 \r" + strtime + " total :" + time.totalTimeEstimateString(cc) + "           ");
//            diffIndex = 0;
//        }
//
//    }

}
