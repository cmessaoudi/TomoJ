package fr.curie.tomoj.application;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import junit.framework.TestCase;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.utils.MatrixUtils;
import fr.curie.tomoj.application.GoldBeadChainGenerator;

/**
 * Created by cmessaoudi on 21/04/2017.
 */
public class GoldBeadDetector_Filter extends TestCase implements PlugInFilter {
    TiltSeries ts=null;
    boolean useMinima;
    double percentageExcludeX;
    double percentageExcludeY;
    int critical_FilterSmall;
    int critical_FilterLarge;
    int critical_MinimaRadius;
    int critical_SeedNumber;
    int patchSize;
    double thresholdFitGoldBead;
    int jumpmax;
    boolean fuseLandmarks;

    public int setup(String s, ImagePlus imagePlus) {
        ts=new TiltSeries(imagePlus);
        GenericDialog gd=new GenericDialog("parameters");
        gd.addCheckbox("use minima",true);
        gd.addNumericField("percentage to exclude X: ",0.1,1);
        gd.addNumericField("percentage to exclude Y: ",0.1,1);
        gd.addNumericField("Filer small: ",2,0);
        gd.addNumericField("Filter large: ",125,0);
        gd.addNumericField("minima radius: ",8,0);
        gd.addNumericField("number of seeds: ",200,0);
        gd.addNumericField("patch size: ",21,0);
        gd.addNumericField("fit threshold: ",0.5,2);
        gd.addNumericField("fit jump max: ",0,0);
        gd.addCheckbox("fuse landmarks",true);
        gd.showDialog();
        if (gd.wasCanceled()) return DONE;
        useMinima=gd.getNextBoolean();
        percentageExcludeX=gd.getNextNumber();
        percentageExcludeY=gd.getNextNumber();
        critical_FilterSmall=(int)gd.getNextNumber();
        critical_FilterLarge=(int)gd.getNextNumber();
        critical_MinimaRadius=(int)gd.getNextNumber();
        critical_SeedNumber=(int)gd.getNextNumber();
        patchSize=(int)gd.getNextNumber();
        thresholdFitGoldBead=gd.getNextNumber();
        jumpmax=(int)gd.getNextNumber();
        fuseLandmarks=gd.getNextBoolean();
        return DOES_32;
    }

    public void run(ImageProcessor imageProcessor) {


        /*ImageProcessor min=new FloatProcessor(imageProcessor.getWidth()/2,imageProcessor.getHeight()/2);
        for(int y=0;y<min.getHeight();y++){
            for(int x=0;x<min.getWidth();x++){
                min.putPixelValue(x,y, Math.min(Math.min(imageProcessor.getPixelValue(2*x,2*y),imageProcessor.getPixelValue(2*x+1,2*y)),Math.min(imageProcessor.getPixelValue(2*x,2*y+1),imageProcessor.getPixelValue(2*x+1,2*y+1))));
            }
        }
        new ImagePlus("min", min).show();

        ImageProcessor min2=new FloatProcessor(min.getWidth()/2,min.getHeight()/2);
        for(int y=0;y<min2.getHeight();y++){
            for(int x=0;x<min2.getWidth();x++){
                min2.putPixelValue(x,y, Math.min(Math.min(min.getPixelValue(2*x,2*y),min.getPixelValue(2*x+1,2*y)),Math.min(min.getPixelValue(2*x,2*y+1),min.getPixelValue(2*x+1,2*y+1))));
            }
        }
        new ImagePlus("min2", min2).show();*/


        //TiltSeries ts=new TiltSeries(this.ts);
        ts.show();
        ts.setTitle("ts");
        TomoJPoints tp=new TomoJPoints(ts);
        ts.setTomoJPoints(tp);
        tp.showAll(true);
        GoldBeadChainGenerator gbcg=new GoldBeadChainGenerator(ts);
        gbcg.setParameters(useMinima,percentageExcludeX,percentageExcludeY,critical_FilterSmall,critical_FilterLarge,critical_SeedNumber,critical_MinimaRadius,patchSize,thresholdFitGoldBead,jumpmax, fuseLandmarks);

        gbcg.run();
        /*ChainsGenerator chaingen=new ChainsGenerator(ts);
        //new ImagePlus("ref", new FloatProcessor(ts.getWidth(),ts.getHeight(),chaingen.createGaussianImage(ts.getWidth(),3,true))).show();

        SeedDetector sd=new SeedDetector(ts);
        Chrono time=new Chrono();
        //sd.getFilteredImageMSD(ts,useMinima,true,percentageExcludeX,percentageExcludeY,critical_FilterSmall,critical_FilterLarge,critical_MinimaRadius,critical_SeedNumber);

        time.start();
        ImageProcessor msd2=sd.getFilteredImageMSD(ts,useMinima,false,percentageExcludeX,percentageExcludeY,critical_FilterSmall,critical_FilterLarge,critical_MinimaRadius,critical_SeedNumber);
        time.stop();
        System.out.println("msd optim : "+time.delayString());
        new ImagePlus("msd optim",msd2).show();
        time.start();
        ImageProcessor msd1=sd.getFilteredImageMSDOld(ts,useMinima,false,percentageExcludeX,percentageExcludeY,critical_FilterSmall,critical_FilterLarge,critical_MinimaRadius,critical_SeedNumber);
        time.stop();
        System.out.println("msd classic : "+time.delayString());
        new ImagePlus("msd classic",msd1).show();*/





        /*SeedDetector sd=(ts instanceof TiltSeries)? new SeedDetector((TiltSeries)ts): new SeedDetector(null);
        if(ts instanceof TiltSeries){
            ArrayList<LandmarksChain> Q=sd.createsSeedsLocalExtrema(ts.getCurrentSlice()-1,useMinima,percentageExcludeX,percentageExcludeY,critical_FilterSmall,critical_FilterLarge,critical_MinimaRadius,critical_SeedNumber);
            int nbBefore = Q.size();
            double s = sd.isGoldBeadPresent(Q, ts.getCurrentSlice()-1, patchSize, useMinima, thresholdFitGoldBead);
            System.out.println("#" + (ts.getCurrentSlice()-1) + " do bead selection : " + nbBefore + " --> " + Q.size());

            TomoJPoints tp=((TiltSeries) ts).getTomoJPoints();
            if(tp!=null){
                for(LandmarksChain c:Q) {
                    Point2D.Double[] tmp=c.getLandmarkchain();
                    for(Point2D.Double p:tmp) if(p!=null) p.setLocation(p.getX()+((TiltSeries)ts).getCenterX(), p.getY()+((TiltSeries)ts).getCenterY());
                    tp.addSetOfPoints(c.getLandmarkchain());
                }
            }
        }else{

            sd.createsSeedsLocalExtrema(ts,useMinima,percentageExcludeX,percentageExcludeY,critical_FilterSmall,critical_FilterLarge,critical_MinimaRadius,critical_SeedNumber);
        }*/


    }

    public void test(){
        System.out.println("test");
        DoubleMatrix1D Yaxis= DoubleFactory1D.dense.make(3);
        Yaxis.setQuick(1,1);
        DoubleMatrix2D Rthetai=MatrixUtils.rotation3DMatrix(45,Yaxis);
        DoubleMatrix2D Rthetai_inv=new DenseDoubleAlgebra().inverse(Rthetai);

        DoubleMatrix2D Rthetai1=MatrixUtils.rotation3DMatrix(46,Yaxis);

        DoubleMatrix2D Rthetai1Rthethaiinv=Rthetai1.zMult(Rthetai_inv,null);

        DoubleMatrix2D Rtheta1= MatrixUtils.rotation3DMatrix(1,Yaxis);

        System.out.println("Rthetai: "+Rthetai);
        System.out.println("Rthetai_inv: "+Rthetai_inv);
        System.out.println("Rthetai1: "+Rthetai1);
        System.out.println("mult: "+Rthetai1Rthethaiinv);

        System.out.println("\nRtheta 1: "+Rtheta1+"\n");


        DoubleMatrix2D Ralpha=MatrixUtils.rotation3DMatrixZ(15);
        DoubleMatrix2D Ralpha_inv=new DenseDoubleAlgebra().inverse(Ralpha);
        System.out.println("Ralpha: "+Ralpha);
        System.out.println("Ralpha_inv: "+Ralpha_inv);

        DoubleMatrix2D result=Ralpha.zMult(Rthetai1Rthethaiinv.zMult(Ralpha_inv,null),null);
        System.out.println("Ralpha Rthetai+1 Rthetai inv Ralpha inv : "+result);
    }
}
