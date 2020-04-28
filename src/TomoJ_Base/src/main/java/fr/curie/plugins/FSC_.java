package fr.curie.plugins;

import fr.curie.plotj.PlotWindow2;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import fr.curie.tomoj.tomography.ResolutionEstimation;

import java.awt.*;
import java.util.Arrays;

/**
 * Created by cedric on 01/02/2016.
 */
public class FSC_ implements PlugIn {
    public void run(String s) {
        int[] ids= WindowManager.getIDList();
        String[] names1=new String[ids.length];
        String[] names2=new String[ids.length];
        for(int i=0;i<ids.length;i++){
            names1[i]=WindowManager.getImage(ids[i]).getTitle();
            names2[i]=WindowManager.getImage(ids[i]).getTitle();
        }
        GenericDialog gd=new GenericDialog("choose files");
        gd.addChoice("first image",names1,names1[0]);
        gd.addChoice("second image",names2,names2[0]);

        gd.showDialog();
        if(gd.wasCanceled()) return;
        ImagePlus img1=WindowManager.getImage(ids[gd.getNextChoiceIndex()]) ;
        ImagePlus img2=WindowManager.getImage(ids[gd.getNextChoiceIndex()]) ;


        double[][] resultfsc = (img1.getNSlices()>1)?ResolutionEstimation.fsc(img1,img2):ResolutionEstimation.frc(img1.getProcessor(),img2.getProcessor());
        System.out.println("fsc formating results");
        double[] x = new double[img1.getWidth() / 2+1];
        double[] y = new double[img1.getWidth() / 2+1];
        double[] half=new double[x.length];
        Arrays.fill(half,0.5);
        ResultsTable rt = new ResultsTable();
        for (int i = 0; i < x.length; i++) {
            //x[i] = ResolutionEstimation.convertToFrequency(i, img1.getWidth());
            x[i]= resultfsc[0][i];
            y[i] = resultfsc[1][i];
            System.out.println("" + x[i] + "\t" + resultfsc[1][i]);
            rt.incrementCounter();
            rt.addValue("index", i);
            rt.addValue("frequence", x[i]);
            rt.addValue("FSC", y[i]);
        }
        rt.setPrecision(5);
        rt.showRowNumbers(true);
        rt.show("FSC_"+img1.getTitle()+"_"+img2.getTitle());

        PlotWindow2 pw = new PlotWindow2();
        pw.removeAllPlots();
        pw.addPlot(x, y, Color.RED, "FSC");
        pw.addPlot(x, resultfsc[2], Color.BLUE, "FSC_noise");
        //pw.addPlot(x, resultfsc[3], Color.GREEN, "error L2");
        pw.addPlot(x, half, Color.BLACK, "0.5 threshold");
        pw.resetMinMax();
        pw.setVisible(true);
    }
}
