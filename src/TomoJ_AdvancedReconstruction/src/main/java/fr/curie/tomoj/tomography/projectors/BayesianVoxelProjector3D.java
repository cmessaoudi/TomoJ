package fr.curie.tomoj.tomography.projectors;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.io.FileInfo;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.filters.FFTWeighting;

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
public class BayesianVoxelProjector3D extends VoxelProjector3D {
    protected double regularizationLambda=0.1;
    protected double regularizationAlpha=0.1;
    TomoReconstruction2 distanceVolume;
    int currentIteration;
    float[] convolutionKernel={ 1,-2,1,-2,4,-2,1,-2,1,
                                -2,4,-2,4,-8,4,-2,4,-2,
                                1,-2,1,-2,4,-2,1,-2,1};

    public BayesianVoxelProjector3D(TiltSeries ts, TomoReconstruction2 rec, FFTWeighting weightingFilter) {
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
        distanceVolume =new TomoReconstruction2(volumeWidth,volumeHeight,volumeDepth);
        //convolutionVolume=new TomoReconstruction2(volumeWidth,volumeHeight,volumeDepth);
        //convolutionVolume.show();
    }



    public void setRegularizationLambda(double regularizationLambda) {
        this.regularizationLambda = regularizationLambda;
    }

    public void setRegularizationAlpha(double regularizationAlpha) {
        this.regularizationAlpha = regularizationAlpha;
    }



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
            final int increment = 1;

            if (distanceVolume != null && diffs.size() > 0) {
                //System.out.println("error volume");
                //convertDiffToErrorImage(diffs.get(pp));
                for (int y = startY; y < endy; y += increment) {
                    final int yy = y;
                    //System.out.println("y"+y);
                    futures.add(exec.submit(new Thread() {
                        public void run() {
                            //System.out.println("thread");
                            backProjectPartial(distanceVolume, maskVolume, diffs.get(pp), eulers.get(pp), 0, volumeWidth, yy, yy + increment);

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
        }
    }


    /**
     * overides method in Projector <BR>
     * save error volume if it exists
     */
    public void endOfIteration() {
        currentIteration++;
        if (distanceVolume != null) {
            System.out.println("add distance");
            mult(distanceVolume,regularizationAlpha);
            rec.operation(distanceVolume,TomoReconstruction2.ADD);
            if(positivityConstraint) {
                System.out.println("apply positivity constraint");
                rec.applyPositivityConstraint();
            }
        }
    }

    public void startOfIteration() {
        System.out.println("bayesian");
        convolve(rec,convolutionKernel,distanceVolume,-regularizationLambda);
    }
    /**
     * puts all the values in error volume to 0
     */
    public void clearErrorVolume() {
        if (distanceVolume == null) return;
        for (int i = 0; i < volumeDepth; i++) {
            float[] tmp = (float[]) distanceVolume.getImageStack().getPixels(i + 1);
            Arrays.fill(tmp, 0);
        }
    }


    /**
     * create the data to store the error volume
     */
    public void createErrorVolume() {
        distanceVolume =new TomoReconstruction2(volumeWidth,volumeHeight,volumeDepth);
    }



    public void mult(final ImagePlus imp,final double value){
        ArrayList<Future> futures=new ArrayList<Future>(imp.getImageStackSize());
        for(int z=0; z<imp.getImageStackSize();z++){
            final int zz=z;
            futures.add(exec.submit(new Thread(){
                public void run(){
                    FloatProcessor ip=(FloatProcessor)imp.getImageStack().getProcessor(zz+1);
                    ip.multiply(value);
                }
            }));
        }
        try {
            for (Future f : futures) f.get();
        }catch (Exception e){e.printStackTrace();}
    }

    public void convolve(final TomoReconstruction2 src, final float[] kernel, final TomoReconstruction2 dest, final double factor){
        ArrayList<Future> futures=new ArrayList<Future>(dest.getImageStackSize()*dest.getHeight());
        for(int z=0;z< dest.getSizez();z++){
            final int zz=z;
            for(int y=0;y<dest.getHeight();y++){
                final int yy=y;
                futures.add(exec.submit(new Thread() {
                    public void run() {
                        double total;
                        for(int x=0;x<dest.getWidth();x++){
                            //if(x<1&&yy<1||zz<1||x>dest.getWidth()-2||yy>dest.getHeight()-2||zz>dest.getSizez()-2) dest.putPixel(x,yy,zz,0);
                            //else{
                                float[] neighborhood=src.getCubicNeightborhoodWithClamp(x,yy,zz,1);
                                total=0;
                                for(int index=0;index<neighborhood.length;index++){
                                    total+=neighborhood[index]*kernel[index];
                                }
                                dest.putPixel(x,yy,zz,total*factor);
                            //}
                        }
                    }
                }));
            }
        }
        try {
            for (Future f : futures) f.get();
        }catch (Exception e){e.printStackTrace();}
    }

}
