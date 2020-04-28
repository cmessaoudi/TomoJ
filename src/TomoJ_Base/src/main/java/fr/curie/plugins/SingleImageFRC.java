package fr.curie.plugins;

import fr.curie.plotj.PlotWindow2;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.ResolutionEstimation;

import java.awt.*;

public class SingleImageFRC implements ExtendedPlugInFilter {

    int flags = DOES_8G + DOES_16 + DOES_32 + PARALLELIZE_STACKS;
    PlugInFilterRunner pfr;
    ImagePlus myimp;
    Integer nbprocessed=0;
    double[] frcx;
    double[] frcy;
    double[] frciso;
boolean display;
double translateOffset=-0.5;

    @Override
    public int setup(String arg, ImagePlus imp) {
        myimp = imp;
        frcx=new double[imp.getImageStackSize()];
        frcy=new double[imp.getImageStackSize()];
        frciso=new double[imp.getImageStackSize()];
        return flags;
    }

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        this.pfr = pfr;

        GenericDialog gd=new GenericDialog("Single Image FRC");
        gd.addNumericField("translate offset",translateOffset, 2);
        gd.addCheckbox("display intermediate images",false);
        gd.showDialog();

        if(gd.wasOKed()){
            translateOffset=gd.getNextNumber();
            display=gd.getNextBoolean();
        }
        return IJ.setupDialog(imp, flags);
    }

    @Override
    public void setNPasses(int nPasses) {

    }

    @Override
    public void run(ImageProcessor ip) {
        int ind = pfr.getSliceNumber()-1;
        //create sub images
        FloatProcessor img1= new FloatProcessor(ip.getWidth()/2,ip.getHeight()/2);
        FloatProcessor img2= new FloatProcessor(ip.getWidth()/2,ip.getHeight()/2);
        FloatProcessor img3= new FloatProcessor(ip.getWidth()/2,ip.getHeight()/2);
        FloatProcessor img4= new FloatProcessor(ip.getWidth()/2,ip.getHeight()/2);

        for(int y=0;y<img1.getHeight();y++){
            for(int x=0;x<img1.getWidth();x++){
                img1.setf(x,y,ip.getPixelValue(x*2,y*2));
                img2.setf(x,y,ip.getPixelValue(x*2+1,y*2));
                img3.setf(x,y,ip.getPixelValue(x*2,y*2+1));
                img4.setf(x,y,ip.getPixelValue(x*2+1,y*2+1));
            }
        }
        img2.translate(translateOffset,0);
        img3.translate(0,translateOffset);
        img4.translate(translateOffset,translateOffset);
        if(display) {
            new ImagePlus(myimp.getTitle() + "-" + ind + "img1", img1).show();
            new ImagePlus(myimp.getTitle() + "-" + ind + "img2", img2).show();
            new ImagePlus(myimp.getTitle() + "-" + ind + "img3", img3).show();
            new ImagePlus(myimp.getTitle() + "-" + ind + "img4", img4).show();
        }
        frcx[ind]=(computeResolutionFRC(img1,img2)+computeResolutionFRC(img3,img4))/2;
        frcy[ind]=(computeResolutionFRC(img1,img3)+computeResolutionFRC(img2,img4))/2;
        frciso[ind]=(computeResolutionFRC(img1,img4)+computeResolutionFRC(img2,img3))/2;

        if(display){
            displayFRCcurves(img1,img2,img3,img4,2, "FRCx");
            displayFRCcurves(img1,img3,img2,img4,2, "FRCy");
            displayFRCcurves(img1,img4,img2,img3,2, "FRCiso");
        }


        synchronized (nbprocessed){
            nbprocessed++;
            if(nbprocessed==myimp.getImageStackSize()){
                displayResults();
            }
        }

    }

    public double computeResolutionFRC(ImageProcessor img1, ImageProcessor img2){
        return computeResolutionFRC(img1,img2,2);

    }

    public double computeResolutionFRC(ImageProcessor img1, ImageProcessor img2, int smoothRadius){
        double[][] resultfsc = ResolutionEstimation.frc(img1,img2);
        int index=0;
        while(getsmoothValue(resultfsc,index,smoothRadius)>0.5&&index<resultfsc[1].length-1){
            index++;
        }
        return 1/resultfsc[0][index];
    }

    public double getsmoothValue(double[][] fsc, int index, int radius){
        if(radius<=0) return fsc[1][index];
        double sum=0;
        int count=0;
        int ii;
        for(int i=index-radius;i<=index+radius;i++){
            ii=Math.max(i,0);
            ii=Math.min(ii,fsc[1].length-1);
            sum+=fsc[1][ii];
            count++;
        }
        return sum/count;
    }

    public void displayFRCcurves(ImageProcessor img1,ImageProcessor img2, ImageProcessor img3, ImageProcessor img4, int smoothRadius, String plotTitle){
        double[][] resultfsc1 = ResolutionEstimation.frc(img1,img2);
        double[][] resultfsc2 = ResolutionEstimation.frc(img3,img4);
        double[] freq=new double[resultfsc1[0].length];
        double[] frc1=new double[resultfsc1[0].length];
        double[] frc2=new double[resultfsc1[0].length];
        for(int i=0;i<freq.length;i++){
            freq[i]=resultfsc1[0][i];
            frc1[i]=getsmoothValue(resultfsc1,i,smoothRadius);
            frc2[i]=getsmoothValue(resultfsc2,i,smoothRadius);
        }
        PlotWindow2 pw = new PlotWindow2(plotTitle);
        pw.removeAllPlots();
        pw.addPlot(freq, frc1, Color.RED, "FRC1");
        pw.addPlot(freq, frc2, Color.BLUE, "FRC2");
        pw.resetMinMax();
        pw.setVisible(true);

    }

    public void displayResults(){
        ResultsTable rt = new ResultsTable();
        for (int i = 0; i < frcx.length; i++) {
            System.out.println("" + i + "\t" + frcx[i]+"\t"+frcy[i]+"\t"+frciso[i]);
            rt.incrementCounter();
            rt.addValue("index", i);
            rt.addValue("FRCx", frcx[i]);
            rt.addValue("FRCy", frcy[i]);
            rt.addValue("FRCiso", frciso[i]);
        }
        rt.setPrecision(5);
        rt.showRowNumbers(true);
        rt.show("FRC"+myimp.getTitle());
        if(frcx.length>1) {
            double[] x=new double[frcx.length];
            for(int i=0;i<x.length;i++) x[i]=i+1;
            PlotWindow2 pw = new PlotWindow2();
            pw.removeAllPlots();
            pw.addPlot(x, frcx, Color.RED, "FRCx");
            pw.addPlot(x, frcy, Color.BLUE, "FRCy");
            pw.addPlot(x, frciso, Color.GREEN, "FRCiso");
            pw.resetMinMax();
            pw.setVisible(true);
        }


    }
}
