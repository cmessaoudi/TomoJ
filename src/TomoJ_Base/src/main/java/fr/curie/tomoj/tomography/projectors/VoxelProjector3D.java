package fr.curie.tomoj.tomography.projectors;

import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.jet.math.tdouble.DoubleFunctions;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.filters.FFTWeighting;
//import v3.tomoj.TestDual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * model of projector with voxel base and parallel beam <BR>
 * the projection is done via Joseph projection method it is ray-driven (take position in projection and estimate origin in 3D and follow ray - step is 1 and interpolate in 3D volume)  <BR>
 * the back projection is Voxel-driven method (projects position of voxel on projection and interpolate pixels) <BR>
 * Created by cedric on 18/09/2014.
 */
public class VoxelProjector3D extends Projector {
    protected static float ACCURACY = 0.0000000001f;
    protected ExecutorService exec;
    protected double scale = 1.0;
    protected boolean longObjectCompensation = false;
    int nbcpu = Prefs.getThreads();
    ArrayList<ImageProcessor> oriProjs;
    ArrayList<ImageProcessor> projs;
    ArrayList<ImageProcessor> norms;
    ArrayList<ImageProcessor> diffs;
    ArrayList<DoubleMatrix2D> eulers;
    ImagePlus errorVolume;
    TomoReconstruction2 maskVolume;
    String savedir;
    int currentIteration;
    protected boolean positivityConstraint = false;


    protected double nbRaysPerPixels = 1;

    public VoxelProjector3D(TiltSeries ts, TomoReconstruction2 rec, FFTWeighting weightingFilter) {
        super(ts, rec, weightingFilter);
        exec = Executors.newFixedThreadPool(nbcpu);
        oriProjs = new ArrayList<ImageProcessor>();
        projs = new ArrayList<ImageProcessor>();
        norms = new ArrayList<ImageProcessor>();
        diffs = new ArrayList<ImageProcessor>();
        eulers = new ArrayList<DoubleMatrix2D>();
        FileInfo fi = ts.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts.getFileInfo();
        }
        savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
        //if (savedir == "" || savedir == null) savedir = TestDual.DIR; // For test purpose by Antoine, can be removed.
    }

    public boolean isLongObjectCompensation() {
        return longObjectCompensation;
    }

    /**
     * long object compensation is for when information in projections comes also from stuff outside reconstruction
     *
     * @param longObjectCompensation
     */
    public void setLongObjectCompensation(boolean longObjectCompensation) {
        this.longObjectCompensation = longObjectCompensation;
    }

    /**
     * if there is a different scale between reconstruction and projections <BR>
     * to have all projection in the scale of reconstruction the value should be <BR>
     * projection width / reconstruction width
     *
     * @param value scale between projection and reconstruction
     */
    public void setScale(double value) {
        scale = value;
    }

    /**
     * in projection it is possible to have only 1 ray per pixel or 4 (takes 4 times longer!) to have better estimate of projection value
     *
     * @param nbRaysPerPixels
     */
    public void setNbRaysPerPixels(double nbRaysPerPixels) {
        this.nbRaysPerPixels = nbRaysPerPixels;
    }

    public void setPositivityConstraint(boolean positivityConstraint) {
        this.positivityConstraint = positivityConstraint;
    }

    /**
     * add a projection for back projection or to be able to do difference in iterative methods
     *
     * @param index index of projection in TiltSeries
     */
    public void addProjection(int index) {
        //System.out.println("adding projection "+index);
        oriProjs.add(new FloatProcessor(ts.getWidth(), ts.getHeight(), ts.getPixels(index)));
        eulers.add(ts.getAlignment().getEulerMatrix(index));
//        System.out.println("Euler (add project) :" + ts.getEulerMatrix(index)); // TEST
        //System.out.println("Iteration: " + currentIteration + 1); // TEST
    }

    /**
     * clear all projections data
     */
    public void clearAllProjections() {
        projs.clear();
        oriProjs.clear();
        diffs.clear();
        norms.clear();
        eulers.clear();
    }

    public float[] getProjection(int index) {
        return (float[]) projs.get(index).getPixels();
    }

    public ImageProcessor getProjectionImage(int index) { return projs.get(index); }

    public ImageProcessor getNormImage(int index) { return norms.get(index); }

    public ImageProcessor getDifferenceImage(int index) {
        //System.out.println("there are "+diffs.size()+" difference images, get "+index);
        return diffs.get(index); }

    public ImageProcessor getOriginalProjectionImage(int index) { return oriProjs.get(index); }


    /**
     * overides method from Projector.<BR>
     * this is the method called by reconstruction for back projection <BR>
     * computes all voxels between the coordinates startY and endY (not included) on Y axis <BR>
     * if there is an error volume it convert the difference image into error image and then backprojects it in the error volume
     *
     * @param startY position on Y axis to start computation
     * @param endy   position on Y axis to end computation (this last position is not computed)
     */
    public void backproject(final int startY, final int endy) {
        completion = 0;
        double completionIncrement = (endy - startY) / (double) rec.getHeight();
        //System.out.println("voxelPorjector3D bp update: startY:"+startY+" endy:"+endy);
        //if(exec==null) exec = Executors.newFixedThreadPool(nbcpu);
        final ArrayList<ImageProcessor> usedProjs = (diffs.size() == 0) ? oriProjs : diffs;
        //System.out.println("backproject nb projections is:"+usedProjs.size());
        for (int p = 0; p < usedProjs.size(); p++) {
            if (weightingFilter != null) {
                //System.out.println("weighting "+p);
                weightingFilter.weighting(usedProjs.get(p));
            }
            final int pp = p;
            ArrayList<Future> futures = new ArrayList<Future>();
            final int increment = 2;
            for (int y = startY; y < endy; y += increment) {
                final int yy = y;
                //System.out.println("y"+y);
                futures.add(exec.submit(new Thread() {
                    public void run() {
                        //System.out.println("thread y:"+yy+"-->"+(yy+increment));
                        backProjectPartial(rec, maskVolume, usedProjs.get(pp), eulers.get(pp), 0, volumeWidth, yy, Math.min(yy + increment, endy ));
                    }
                }));
            }

            if (errorVolume != null && diffs.size() > 0) {
                //System.out.println("error volume");
                convertDiffToErrorImage(diffs.get(pp));
                for (int y = startY; y < endy; y += increment) {
                    final int yy = y;
                    //System.out.println("y"+y);
                    futures.add(exec.submit(new Thread() {
                        public void run() {
                            //System.out.println("thread");
                            backProjectPartial(errorVolume, maskVolume, diffs.get(pp), eulers.get(pp), 0, volumeWidth, yy, yy + increment);

                        }
                    }));
                }
            }

            try {
                for (Future f : futures) {
                    f.get();
                }

            } catch (Exception e1) {
                System.err.println(e1);
            }
            completion += completionIncrement;
        }
        completion = 0;
    }

    /**
     * this is the true computation of backprojection
     *
     * @param rec
     * @param proj
     * @param euler
     * @param startx
     * @param endx
     * @param starty
     * @param endy
     */
    public void backProjectPartial(ImagePlus rec, TomoReconstruction2 mask, ImageProcessor proj, DoubleMatrix2D euler, int startx, int endx, int starty, int endy) {
        // System.out.println("partial");
        double cix = projectionCenters[2];
        double ciy = projectionCenters[3];
        //System.out.println("new:("+cix+", "+ciy+")");
        proj.setInterpolationMethod(ImageProcessor.BICUBIC);
        if (globalDeformation != null) {
            System.out.println("voxel projector3D back project global deformation not null");
        }else{
            //System.out.println("voxel projector3D back project global deformation is null");
        }
        for (int k = 0; k < volumeDepth; k++) {
            //System.out.println("k loop");
            double z = k - volumeCenters[2];
            /*if(globalDeformationInv!=null){
                z*=globalDeformationInv.getQuick(2,2);
                z-=globalAli.getQuick(2,0);
            }
            double ztx = z * euler.getQuick(0, 2);
            double zty = z * euler.getQuick(1, 2); */
            float[] planez = (float[]) rec.getImageStack().getPixels(k + 1);
            for (int j = starty; j < endy; j++) {
                //System.out.println("j loop");
                double y = j - volumeCenters[1];
                for (int i = startx; i < endx; i++) {
                    if (mask == null || (mask != null && mask.getPixel(i, j, k) > 0)) {
                        /*if(i==256&&j==511){
                            System.out.println("voxel projector debug bp ");
                        }*/
                        //System.out.println("i loop");
                        double x = i - volumeCenters[0];
                        double ztmp = z;
                        double ytmp = y;
                        if (globalDeformation != null) {
                            DoubleMatrix2D r = DoubleFactory2D.dense.make(1, 3);
                            r.setQuick(0, 0, x);
                            r.setQuick(0, 1, ytmp);
                            r.setQuick(0, 2, ztmp);
                            r.assign(globalAli.viewDice(), DoubleFunctions.minus);
                            r = r.zMult(globalDeformationInv, null);
                            x = r.getQuick(0, 0);
                            ytmp = r.getQuick(0, 1);
                            ztmp = r.getQuick(0, 2);
                        }

                        double ztx = ztmp * euler.getQuick(0, 2);
                        double zty = ztmp * euler.getQuick(1, 2);
                        double ytx = ytmp * euler.getQuick(0, 1);
                        double yty = ytmp * euler.getQuick(1, 1);
                        /*if(globalDeformationInv!=null){
                            y=y*globalDeformationInv.getQuick(1,1)+x*globalDeformationInv.getQuick(1,0);
                            y-=globalAli.getQuick(1,0);
                        }
                        double ytx = y * euler.getQuick(0, 1);
                        double yty = y * euler.getQuick(1, 1);
                        if(globalDeformationInv!=null) {
                            x*=globalDeformationInv.getQuick(0,0);
                            x-=globalAli.getQuick(0,0);
                        }  */
                        int jj = j * volumeWidth;
                        //compute coordinate of volume in projection
                        double xx = (x * euler.getQuick(0, 0) + ytx + ztx) * scale + cix;
                        double yy = (x * euler.getQuick(1, 0) + yty + zty) * scale + ciy;

                        if (xx >= 0 && xx < proj.getWidth() && yy >= 0 && yy < proj.getHeight()) {
                            //interpolation
                            float value = (float) proj.getInterpolatedValue(xx, yy);
                            /*if(i==256&&j==511){
                                System.out.println("voxel projector debug bp "+value);
                            }*/
                            if (!Float.isNaN(value)) {
                                float tmp = planez[i + jj];
                                //rec.addToPixelFast(i, j, k, value);
                                //if (Float.isNaN(planez[i + jj]))
                                //    planez[i + jj] = 0;

                                planez[i + jj] += value;
                                if (Float.isNaN(planez[i + jj]))
                                    planez[i + jj] = 0;
                                if (Float.isInfinite(planez[i + jj])) planez[i + jj] = 0;
                                if (positivityConstraint && planez[i + jj] < 0) planez[i + jj] = 0;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * convert a difference image into error image (square the values)
     *
     * @param diff original image that will be modified
     */
    protected void convertDiffToErrorImage(ImageProcessor diff) {
        float[] diffPixels = (float[]) diff.getPixels();
        float f;
        for (int i = 0; i < diffPixels.length; i++) {
            f = diffPixels[i];
            if (!Float.isNaN(f)) {
                diffPixels[i] = f * f;
            }
        }
    }

    /**
     * overides method in Projector<BR>
     * computes the projection between coordinates startY and endY (not included) on Y axis
     *
     * @param startY
     * @param endY
     */
    public void project(final int startY, final int endY) {
        completion = 0;
        double completionIncrement = (endY - startY) / (double) rec.getHeight();
        projs.clear();
        norms.clear();
        int offset=(ts.getHeight()>rec.getHeight()&& scale==1)? (ts.getHeight()-rec.getHeight())/2 : 0;
        //System.out.println("project CPU offset="+offset);

        for (int p = 0; p < eulers.size(); p++) {
            final int pp = p;
            final ImageProcessor proj = new FloatProcessor(ts.getWidth(), ts.getHeight());
            final ImageProcessor norm = new FloatProcessor(ts.getWidth(), ts.getHeight());
            ArrayList<Future> res = new ArrayList<Future>();
            int increment = 2;
            int start = (int) Math.round((startY+offset) * scale);
            int end = projectionHeight;//(int) Math.max(Math.round((endY +offset) * scale), projectionHeight);
            int index = 0;
            for (int j = start; j < end; j += increment) {
                final int starty = j;
                final int endy = (starty + increment <  projectionHeight) ? starty + increment : projectionHeight;
                final int startx = 0;
                final int endx = projectionWidth;

                res.add(exec.submit(new Thread() {
                    public void run() {
                        projectJosephPartial(rec, maskVolume, eulers.get(pp), proj, norm, startx, endx, starty, endy);
                    }
                }));
            }

            try {
                for (Future f : res) {
                    f.get();
                }
            } catch (Exception e1) {
                e1.printStackTrace();
                System.out.println("");
            }
            projs.add(proj);
            norms.add(norm);
            completion += completionIncrement;
        }
        completion = 0;
    }

    /**
     * overides method in Projector <BR>
     * do projection and then difference
     *
     * @param factor
     * @param startY
     * @param endY
     * @return
     */
    public double[] projectAndDifference(double factor, int startY, int endY) {
        project(startY, endY);
        //System.out.println("oriproj:"+oriProjs.size()+" , projs:"+projs.size()+" , norms:"+norms.size()+" , diffs:"+diffs.size());
        double[] errors = new double[eulers.size()];
        diffs.clear();
        for (int p = 0; p < eulers.size(); p++) {
            ImageProcessor diff = new FloatProcessor(projectionWidth, projectionHeight);
            errors[p] = difference(oriProjs.get(p), projs.get(p), norms.get(p), diff, factor);
            diffs.add(diff);
        }
//        projs.clear();
//        norms.clear();
        //System.out.println("proj:"+projs.size()+" diff:"+diffs.size());
        return errors;
    }

    /**
     * computes the difference between 2 images and normalize it<BR>
     * ( (experimental / theoretical) / norm ) * factor
     *
     * @param exp
     * @param th
     * @param norm
     * @param diff
     * @param factor
     * @return
     */
    public static double difference(ImageProcessor exp, ImageProcessor th, ImageProcessor norm, ImageProcessor diff, double factor) {
        float[] exppixs = (float[]) exp.getPixels();
        float[] thpixs = (float[]) th.getPixels();
        float[] npixs = (float[]) norm.getPixels();
        final float[] dpix = (float[]) diff.getPixels();
        double value;
        double error = 0;
        for (int pos = 0; pos < exppixs.length; pos++) {
            float n = npixs[pos];
            float expvalue = exppixs[pos];
            float thvalue = thpixs[pos];
            if (!Float.isNaN(expvalue) && !Float.isNaN(thvalue)) {
                value = ((expvalue - thvalue) / n) * factor;
                dpix[pos] = (float) value;
                error += value * value;
            }
        }
        return error;
    }

    /**
     * the true computation of projection
     *
     * @param rec
     * @param euler
     * @param proj
     * @param norm_proj
     * @param startx
     * @param endx
     * @param starty
     * @param endy
     */
    public void projectJosephPartialOld(TomoReconstruction2 rec, TomoReconstruction2 mask, final DoubleMatrix2D euler, final ImageProcessor proj, final ImageProcessor norm_proj, int startx, int endx, int starty, int endy) {
        //System.out.print("ASART forward="+forward+" norm_proj="+(norm_proj!=null));
        //System.out.println("joseph");
        final float[] projpix = (float[]) proj.getPixels();
        final float[] normpix = (float[]) norm_proj.getPixels();
        final int psx = proj.getWidth();

        final DoubleMatrix2D eulert = euler.viewDice();
        //final DoubleMatrix2D eulert = new DenseDoubleAlgebra().inverse(euler);


        final double dirx = (euler.getQuick(2, 0) == 0) ? ACCURACY : euler.getQuick(2, 0);
        final double diry = (euler.getQuick(2, 1) == 0) ? ACCURACY : euler.getQuick(2, 1);
        final double dirz = (euler.getQuick(2, 2) == 0) ? ACCURACY : euler.getQuick(2, 2);
        final double normVector = StrictMath.sqrt(dirx * dirx + diry * diry + dirz * dirz);
        final double dir2x = dirx / normVector;
        final double dir2y = diry / normVector;
        final double dir2z = dirz / normVector;
        final int xsign = (int) Math.signum(dirx);
        final int ysign = (int) Math.signum(diry);
        final int zsign = (int) Math.signum(dirz);
        final double half_x_sign = 0.5 * xsign;
        final double half_y_sign = 0.5 * ysign;
        final double half_z_sign = 0.5 * zsign;
        nbRaysPerPixels = 1;
        final double step = (nbRaysPerPixels == 1) ? 0 : 0.3333;
        final double josephStep = Math.min(Math.min(Math.abs(1.0 / dirx), Math.abs(1.0 / diry)), Math.abs(1.0 / dirz));
        //System.out.println("joseph step="+josephStep+"\ndir=("+dirx+", "+diry+", "+dirz+")");

        double value;
        double ray_sum;
        double norm_sum;
        double norm_max = 0;
        double r_px = 0;
        double r_py = 0;
        //coordinate of pixel being projected
        double p1x;
        double p1y;
        double p1z;
        //coordinate of pixel in universal space
        double vx;
        double vy;
        double vz;
        int idx_x;
        int idx_y;
        int idx_z;
        double alpha_xmin;
        double alpha_xmax;
        double alpha_ymin;
        double alpha_ymax;
        double alpha_zmin;
        double alpha_zmax;

        double alpha_min;
        double alpha_max;

        for (int j = starty; j < endy; j++) {
            int jj = j * psx;
            //time.start();
            for (int i = startx; i < endx; i++) {
                value = projpix[jj + i];
                ray_sum = 0;
                norm_sum = 0;
                boolean debug = false;
                //if(i==1&&j==0)debug=true;
                for (int rays_per_pixel = 0; rays_per_pixel < nbRaysPerPixels; rays_per_pixel++) {
                    switch (rays_per_pixel) {
                        case 0:
                            r_px = i - projectionCenters[2] - step;
                            r_py = j - projectionCenters[3] - step;
                            break;
                        case 1:
                            r_px = i - projectionCenters[2] - step;
                            r_py = j - projectionCenters[3] + step;
                            break;
                        case 2:
                            r_px = i - projectionCenters[2] + step;
                            r_py = j - projectionCenters[3] - step;
                            break;
                        case 3:
                            r_px = i - projectionCenters[2] + step;
                            r_py = j - projectionCenters[3] + step;
                            break;
                    }
                    //r_px = i - pcx;
                    // r_py = j - pcy;
                    r_px /= scale;
                    r_py /= scale;
                    if (debug) System.out.println("r_px=" + r_px + " r_py=" + r_py);
                    //express r_p in the universal coordinate system
                    p1x = eulert.getQuick(0, 0) * r_px + eulert.getQuick(0, 1) * r_py;
                    p1y = eulert.getQuick(1, 0) * r_px + eulert.getQuick(1, 1) * r_py;
                    p1z = eulert.getQuick(2, 0) * r_px + eulert.getQuick(2, 1) * r_py;
                    if (globalDeformation != null) {
                        DoubleMatrix2D r = DoubleFactory2D.dense.make(3, 1);
                        r.setQuick(0, 0, p1x);
                        r.setQuick(1, 0, p1y);
                        r.setQuick(2, 0, p1z);
                        r = globalDeformation.zMult(r, null);
                        r.assign(globalAli, DoubleFunctions.plus);
                        p1x = r.getQuick(0, 0);
                        p1y = r.getQuick(1, 0);
                        p1z = r.getQuick(2, 0);
//                        p1y=p1y*globalDeformation.getQuick(1,1)+p1x*globalDeformation.getQuick(1,0);
//                        p1x*=globalDeformation.getQuick(0,0);
//                        p1z*=globalDeformation.getQuick(2,2);
//                        p1x+=globalAli.getQuick(0,0);
//                        p1y+=globalAli.getQuick(1,0);
//                        p1z+=globalAli.getQuick(2,0);
                    }
                    p1x += volumeCenters[0];
                    p1y += volumeCenters[1];
                    p1z += volumeCenters[2];
                    if (debug) System.out.println("p1=(" + p1x + ", " + p1y + ", " + p1z + ")");
                    //compute min and max alpha for the ray intersecting the volume
                    alpha_xmin = (0 - 0.5 - p1x) / dirx;
                    alpha_xmax = (volumeWidth + 0.5 - p1x) / dirx;
                    alpha_ymin = (0 - 0.5 - p1y) / diry;
                    alpha_ymax = (volumeHeight + 0.5 - p1y) / diry;
                    alpha_zmin = (0 - 0.5 - p1z) / dirz;
                    alpha_zmax = (volumeDepth + 0.5 - p1z) / dirz;

                    alpha_min = Math.max(Math.max(Math.min(alpha_xmin, alpha_xmax), Math.min(alpha_ymin, alpha_ymax)), Math.min(alpha_zmin, alpha_zmax));
                    alpha_max = Math.min(Math.min(Math.max(alpha_xmin, alpha_xmax), Math.max(alpha_ymin, alpha_ymax)), Math.max(alpha_zmin, alpha_zmax));

                    if (debug) System.out.println("alphamin=" + alpha_min + ", alphamax=" + alpha_max);

                    if (alpha_max - alpha_min < ACCURACY) {
                        continue;
                    }
                    //compute the first point in the volume intersecting the ray
                    vx = dirx * alpha_min + p1x;
                    vy = diry * alpha_min + p1y;
                    vz = dirz * alpha_min + p1z;
                    if (debug) System.out.println("init V=(" + vx + ", " + vy + ", " + vz + ")");
                    int test = 100;
                    while ((vx < 0 || vx >= volumeWidth || vy < 0 || vy >= volumeHeight || vz < 0 || vz >= volumeDepth) && test > 0) {
                        vx += dirx;
                        vy += diry;
                        vz += dirz;
                        test--;
                        if (debug) System.out.println("modified V=(" + vx + ", " + vy + ", " + vz + ")");
                    }


                    do {
                        //if(i==0&&j==0) System.out.println(" V=("+vx+", "+vy+", "+vz+")");
                        ray_sum += rec.getPixel(vx, vy, vz);

                        //norm_sum += josephStep;
                        if (mask == null) norm_sum++;
                        else {
                            float val = mask.getPixel(vx, vy, vz);
                            if (val > 0) norm_sum++;
                        }
                        vx += dir2x;
                        vy += dir2y;
                        vz += dir2z;

                        if (debug)
                            System.out.println("new V=(" + vx + ", " + vy + ", " + vz + ")\nraysum=" + ray_sum + "\nnormsum=" + norm_sum);
                        //}while((alpha_max - alpha) > ACCURACY);
                    }
                    while (vx >= -1 && vx <= volumeWidth && vy >= -1 && vy <= volumeHeight && vz >= -1 && vz <= volumeDepth);
                    //time.stop();
                    //System.out.println("time for each ray " + time.delayString());
                }//for rays
                //norm_sum--;
                if (debug) System.out.println("ray_sum=" + ray_sum + "\nnorm_sum=" + norm_sum);
                if (norm_sum > 0) {
                    norm_sum /= nbRaysPerPixels;
                    ray_sum /= nbRaysPerPixels;
                    projpix[jj + i] = (float) (ray_sum);
                    normpix[jj + i] = (float) (norm_sum);
                    if (norm_sum > norm_max) {
                        norm_max = norm_sum;
                    }
                } else {
                    projpix[jj + i] = Float.NaN;
                    normpix[jj + i] = 0;
                }

            }
            //time.stop();
            //System.out.println("project basic time for 1 line" + time.delayString());
        }
        //test for normalizing with maximum length
        if (longObjectCompensation) {
            for (int i = 0; i < normpix.length; i++) {
                normpix[i] = (float) (norm_max);
            }
            //System.out.println(""+norm_max);
        }
    }


    /**
     * the true computation of projection  with deformations
     * computation is done by condering that (1) P(x,y)=Ai.rj(x,y,z)
     * where Ai is the projection matrix with tilt around an axis and deformation matrix
     * if I define Ai as    [ a b c ]
     *                      [ d e f ]
     * so
     * (2) Px = ax + by + cz
     * (3) Py = dx + ey + fz
     *
     * then when having Px and Py we can find the x and y if z is fixed (we will go through all volume)
     * so from equation (2) we get (4) x = (Px - by - cz) / a
     * and then replacing x in equation (4) we get (5) y = (Py - dPx/a - (f-dc/a)z) / (e - db/a)
     *
     * for optimization problem we will determine a starting position with equation 5 and 4
     * and then follow a direction given by
     * using 2 points with a variation in z of 1 and developing from equations 4 and 5 we get
     * (6) X2 = X1 + dirX = X1 + ( b*(f-dc/a)/(e-db/a) - c)/a
     * (7) Y2 = Y1 + dirY = Y1 + (- fa - dc) / (ea-db)
     *
     * @param rec
     * @param proj
     * @param norm_proj
     * @param startx
     * @param endx
     * @param starty
     * @param endy
     */
    public void projectJosephPartial(final TomoReconstruction2 rec, final TomoReconstruction2 mask, DoubleMatrix2D ridi, final ImageProcessor proj, final ImageProcessor norm_proj, int startx, int endx, int starty, int endy) {
        //System.out.print("ASART forward="+forward+" norm_proj="+(norm_proj!=null));
        //System.out.println("joseph");
        final float[] projpix = (float[]) proj.getPixels();
        final float[] normpix = (float[]) norm_proj.getPixels();
        final int psx = proj.getWidth();
        //DoubleMatrix2D ridi=Aitheta.getEuler();
        if (globalDeformationInv != null) ridi = ridi.zMult(globalDeformationInv, null);

        //System.out.println(ridi);

        final double icenterFactorForYdetermination = ridi.getQuick(1, 0) / ridi.getQuick(0, 0);
        final double zcenterFactorForYdetermination = (ridi.getQuick(1, 2) - (ridi.getQuick(1, 0) * ridi.getQuick(0, 2) / ridi.getQuick(0, 0)));
        final double divisionFactorForYdetermination = ridi.getQuick(1, 1) - (ridi.getQuick(1, 0) * ridi.getQuick(0, 1) / ridi.getQuick(0, 0));
        final double yFactorForXdetermination = ridi.getQuick(0, 1);
        final double zFactorForXdetermination = ridi.getQuick(0, 2);
        final double divisionFactorForXdetermination = ridi.getQuick(0, 0);

//        final double dirX=(yFactorForXdetermination*zcenterFactorForYdetermination/divisionFactorForYdetermination-zFactorForXdetermination)/divisionFactorForXdetermination;
//        final double dirY=-zcenterFactorForYdetermination/divisionFactorForYdetermination;

        final double dirY = -(ridi.getQuick(1, 2) - ridi.getQuick(1, 0) * ridi.getQuick(0, 2) / ridi.getQuick(0, 0)) / (ridi.getQuick(1, 1) - ridi.getQuick(1, 0) * ridi.getQuick(0, 1) / ridi.getQuick(0, 0));
        final double dirX = (ridi.getQuick(0, 1) * (-dirY) - ridi.getQuick(0, 2)) / ridi.getQuick(0, 0);
        final double normIncrement = Math.sqrt(dirX * dirX + dirY * dirY + 1);   //the one is for Z direction (it is fixed in the loop)

        final double volcenterx = (globalDeformation == null) ? volumeCenters[0] : volumeCenters[0] + globalAli.getQuick(0, 0);
        final double volcentery = (globalDeformation == null) ? volumeCenters[1] : volumeCenters[1] + globalAli.getQuick(1, 0);
        final double volcenterz = (globalDeformation == null) ? volumeCenters[2] : volumeCenters[2] + globalAli.getQuick(2, 0);

        final double borderXmax = volumeWidth + ACCURACY;
        final double borderYmax = volumeHeight + ACCURACY;
        double ijPartForY;
        boolean entered;
        float ray_sum;
        float norm_sum;
        float norm_max = 0;
        double x, y, xcentered, ycentered, icentered, jcentered;
        double zcentered; // TEST
        double oldx, oldy;
        int jj = (starty - 1) * psx;
        jcentered = (starty - 1.0 - projectionCenters[1]) / scale;
        double jCenteredIncrement = 1.0 / scale;
        float value;

//        Chrono time=new Chrono();
//        long totalReadTime=0;
//        long totalRead=0;
//        long totalexternalTime=0;
        //for all pixels position in projection
        for (int j = starty; j < endy; j++) {
            //int jj = j * psx;
            //jcentered=(j-projectionCenters[1])/scale;
            jj += psx;
            jcentered += jCenteredIncrement;
            if(j>=0&&j<projectionHeight) {
                for (int i = startx; i < endx; i++) {

                /*if(i==256 &&j==511)
                {
                    System.out.println("voxel projector test debug");
                }*/
                    icentered = (i - projectionCenters[0]) / scale;
                    ray_sum = 0;
                    norm_sum = 0;
                    ijPartForY = jcentered - icenterFactorForYdetermination * icentered;
                    oldy = (ijPartForY - zcenterFactorForYdetermination * (-1 - volcenterz)) / divisionFactorForYdetermination;
                    oldx = icentered - yFactorForXdetermination * oldy - zFactorForXdetermination * (-1 - volcenterz);
                    oldx /= divisionFactorForXdetermination;
                    entered = false;
                    //if(i==256&&j==256) System.out.println("dirX="+dirX+", dirY="+dirY+", oldx="+oldx+", oldy="+oldy);
                    //for all z in reconstruction --> rj(?,?,z)
                    for (int z = 0; z < rec.getSizez(); z++) {
                        // ######TEST######
////                    zcentered=z-volcenterz;
//                    zcentered=z-volumeCenters[2];
//                    if(globalDeformation!=null){
//                        zcentered-=globalAli.getQuick(2,0);
//                    }
////                    compute rj corresponding to this z
//                    ycentered=ijPartForY-zcenterFactorForYdetermination*zcentered ;
//                    ycentered/=divisionFactorForYdetermination;
//                    xcentered=icentered-yFactorForXdetermination*ycentered-zFactorForXdetermination*zcentered;
////                    xcentered/=divisionFactorForXdetermination;
                        //####################
//                    time.start();
                        xcentered = oldx + dirX;
                        ycentered = oldy + dirY;
                        x = xcentered + volcenterx;
                        y = ycentered + volcentery;
                        //if(i==256&&j==256) System.out.print("(x"+x+", y"+y+", z"+z+")");
                        if (x + ACCURACY >= 0 && x < borderXmax && y + ACCURACY >= 0 && y < borderYmax) {
                            //ray_sum += rec.getImageStack().getProcessor(z + 1).getInterpolatedValue(x, y);
                            //norm=Math.sqrt((xcentered-oldx)*(xcentered-oldx)+(ycentered-oldy)*(ycentered-oldy)+1);//the +1 is for the z direction
//                        time.start();
                            value = rec.getPixel(x, y, z);
                            //if(Double.isNaN(value))
                            //    value=0;
                            ray_sum += value;
//                        time.stop();
//                        totalReadTime+=time.delay();
//                        totalRead++;
                            //if(j==251)System.out.println("("+i+","+j+") inside ["+x+","+y+","+z+"] so ray_sum:"+ray_sum);

                            if ((mask == null || mask.getPixel((int) x, (int) y, z) > 0)) norm_sum += normIncrement;
//                        if(!inside&&(mask==null||mask[(int)x][(int)y][z])) norm_sum+=norm;
//                        inside=true;
                            entered = true;
                        } else {
//                        totalexternalTime+=time.delay();
                            if (entered) {
                                if ((mask == null || mask.getPixel((int) x, (int) y, z) > 0))
                                    norm_sum += normIncrement;//norm_sum+=Math.sqrt((xcentered-oldx)*(xcentered-oldx)+(ycentered-oldy)*(ycentered-oldy)+1);;
                                break;
                            }
                        }
                        //System.out.flush();
                        oldx = xcentered;
                        oldy = ycentered;
                    }//end for Z
                    if (norm_sum > 0) {
                        //norm_sum /= nbRaysPerPixels;
                        //ray_sum /= nbRaysPerPixels;
                        projpix[jj + i] = (float) (ray_sum * normIncrement);
                    /*if(i==256 &&j==511)
                    {
                        System.out.println("voxel projector test debug "+(ray_sum*normIncrement)+" in proj "+projpix[jj+i]);
                    }*/
                        normpix[jj + i] = (float) (norm_sum);
                        if (norm_sum > norm_max) {
                            norm_max = norm_sum;
                        }
                    } else {
                        projpix[jj + i] = Float.NaN;
                        normpix[jj + i] = 0;
                    }
                    //System.out.println("("+i+","+j+") ray_sum:"+projpix[jj+i]);
                }
            }//end if j in range
            //System.out.println(""+j);
        }//end for all pixels in projection


        //test for normalizing with maximum length
        if (longObjectCompensation) {
            for (int i = 0; i < normpix.length; i++) {
                normpix[i] = (float) (norm_max);
            }
            //System.out.println(""+norm_max);
        }
//        System.out.println("time read:"+totalReadTime);
    }


    /**
     * overides method in Projector <BR>
     * save error volume if it exists
     */
    public void endOfIteration() {
        currentIteration++;
        if (errorVolume != null) {
            //save it
            FileSaver fs = new FileSaver(errorVolume);
            fs.saveAsTiffStack(savedir + ts.getTitle() + "errorVolume_" + currentIteration + ".tif");
            //clear it
            clearErrorVolume();
        }
    }

    /**
     * puts all the values in error volume to 0
     */
    public void clearErrorVolume() {
        if (errorVolume == null) return;
        for (int i = 0; i < volumeDepth; i++) {
            float[] tmp = (float[]) errorVolume.getImageStack().getPixels(i + 1);
            Arrays.fill(tmp, 0);
        }
    }

    public void startOfIteration() {

    }

    /**
     * create the data to store the error volume
     */
    public void createErrorVolume() {
        ImageStack is = new ImageStack(volumeWidth, volumeHeight);
        for (int i = 0; i < volumeDepth; i++) {
            is.addSlice(new FloatProcessor(volumeWidth, volumeHeight));
        }
        errorVolume = new ImagePlus("error Volume", is);
    }

    public void createMask() {
        maskVolume = new TomoReconstruction2(volumeWidth, volumeHeight, volumeDepth);
        for (int z = 0; z < volumeDepth; z++) {
            float[] pixs = (float[]) maskVolume.getImageStack().getPixels(z + 1);
            Arrays.fill(pixs, 1.0f);
        }

    }

    /**
     * puts all the values in mask to true
     */
    public void clearMaskVolume() {
        if (maskVolume == null) return;
        for (int z = 0; z < volumeDepth; z++) {
            float[] pixs = (float[]) maskVolume.getImageStack().getPixels(z + 1);
            Arrays.fill(pixs, 1.0f);
        }
    }
}
