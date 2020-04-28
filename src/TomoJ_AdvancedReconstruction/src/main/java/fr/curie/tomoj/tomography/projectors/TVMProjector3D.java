package fr.curie.tomoj.tomography.projectors;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import fr.curie.filters.TotalVariationDenoising_3D;
import fr.curie.filters.Total_Variation_Denoising;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.filters.FFTWeighting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 07/04/2015.
 */
public class TVMProjector3D extends VoxelProjector3D {

    protected double regularizationLambda=0.1;
    protected double regularizationAlpha=0.1;
    TomoReconstruction2 distanceVolume;
    protected float theta = 25.0f;
    protected float dt=0.05f;
    protected float g= 1.0f;
    protected int tvmIteration=5;

    public TVMProjector3D(TiltSeries ts, TomoReconstruction2 rec, FFTWeighting weightingFilter) {
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

    public void setTvmTheta(double theta) {
        this.theta = (float)theta;
    }

    public void setTvmDt(double dt) {
        this.dt = (float)dt;
    }

    public void setTvmG(double g) {
        this.g = (float)g;
    }

    public void setTvmIteration(int tvmIteration) {
        this.tvmIteration = tvmIteration;
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
   /* public void backproject(final int startY, final int endy) {
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

            //if (distanceVolume != null && diffs.size() > 0) {
                //System.out.println("error volume");
                //convertDiffToErrorImage(diffs.get(pp));
                for (int y = startY; y < endy; y += increment) {
                    final int yy = y;
                    //System.out.println("y"+y);
                    futures.add(exec.submit(new Thread() {
                        public void run() {
                            //System.out.println("thread");
                            backProjectPartial(rec, maskVolume, diffs.get(pp), eulers.get(pp), 0, volumeWidth, yy, yy + increment);

                        }
                    }));
                }
            //}

            try {
                for (Future f : futures) {
                    f.get();
                }

            } catch (Exception e1) {
                System.err.println(e1);
            }
        }
    }*/


    /**
     * overides method in Projector <BR>
     * save error volume if it exists
     */
    public void endOfIteration() {
        currentIteration++;
//        if (distanceVolume != null) {
//            /*if(convolutionVolume!=null){
//                System.out.println("tvm");
//                tvm(rec,convolutionKernel,convolutionVolume,regularizationLambda);
//                //mult(convolutionVolume,regularizationLambda);
//                distanceVolume.operation(convolutionVolume,TomoReconstruction2.SUBTRACT);
//            } */
//            System.out.println("add distance");
//            mult(distanceVolume,regularizationAlpha);
//            rec.operation(distanceVolume,TomoReconstruction2.ADD);
//        }
        /*if(positivityConstraint) {
            System.out.println("apply positivity constraint");
            rec.applyPositivityConstraint();
        }*/
        dt=(float)regularizationLambda;
        System.out.println("tvm denoising: theta="+theta+", g="+g+", dt="+dt+", iterations="+tvmIteration);
        TotalVariationDenoising_3D.denoise3D(rec.getImageStack(),theta,g,dt,tvmIteration);
        //FileSaver fs = new FileSaver(rec);
        //fs.saveAsTiffStack(IJ.getDirectory("current"));
    }

    public void startOfIteration() {
        //System.out.println("tvm");
        //tvm(rec,distanceVolume,-regularizationLambda);
    }
    /**
     * puts all the values in error volume to 0
     */
    /*public void clearErrorVolume() {
        if (distanceVolume == null) return;
        for (int i = 0; i < volumeDepth; i++) {
            float[] tmp = (float[]) distanceVolume.getImageStack().getPixels(i + 1);
            Arrays.fill(tmp, 0);
        }
    }*/


    /**
     * create the data to store the error volume
     */
    /*public void createErrorVolume() {
        distanceVolume =new TomoReconstruction2(volumeWidth,volumeHeight,volumeDepth);
    }*/



    /*public void mult(final ImagePlus imp,final double value){
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
    }*/


    /*public void tvm(final TomoReconstruction2 src, final TomoReconstruction2 dest, final double factor){
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
                            float val=src.getPixel(x,yy,zz);

                            double sum=0;
                            double tmp;
                            if(x+1<dest.getWidth()){
                                tmp=src.getPixel(x+1,yy,zz);
                                sum+=(tmp-val)*(tmp-val);
                            }
                            if(x-1>=0){
                                tmp=src.getPixel(x-1,yy,zz);
                                sum+=(val-tmp)*(val-tmp);
                            }
                            if(yy+1<dest.getHeight()){
                                tmp=src.getPixel(x,yy+1,zz);
                                sum+=(tmp-val)*(tmp-val);
                            }
                            if(yy-1>=0){
                                tmp=src.getPixel(x,yy-1,zz);
                                sum+=(val-tmp)*(val-tmp);
                            }
                            if(zz+1<dest.getSizez()){
                                tmp=src.getPixel(x,yy,zz+1);
                                sum+=(tmp-val)*(tmp-val);
                            }
                            if(zz-1>=0){
                                tmp=src.getPixel(x,yy,zz-1);
                                sum+=(val-tmp)*(val-tmp);
                            }
                            dest.putPixel(x,yy,zz,Math.sqrt(sum)*factor);
                            //}
                        }
                    }
                }));
            }
        }
        try {
            for (Future f : futures) f.get();
        }catch (Exception e){e.printStackTrace();}
    }*/
}
