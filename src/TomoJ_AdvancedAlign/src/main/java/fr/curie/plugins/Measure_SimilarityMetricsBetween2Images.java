package fr.curie.plugins;

import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import fr.curie.utils.align.AlignImages;

public class Measure_SimilarityMetricsBetween2Images implements PlugIn{
    public void run(String s) {
        GenericDialog gd=new GenericDialog("similarity measures");
        String[] imgs= WindowManager.getImageTitles();
        int[] ids=WindowManager.getIDList();
        gd.addChoice("first immage", imgs,imgs[0]);
        gd.addChoice("second image", imgs, imgs[1]);
        gd.addNumericField("range",255,0);
        gd.addCheckbox("2D",true);

        gd.showDialog();
        if (gd.wasCanceled()) return;
        ImagePlus img1=WindowManager.getImage(ids[gd.getNextChoiceIndex()]);
        ImagePlus img2=WindowManager.getImage(ids[gd.getNextChoiceIndex()]);
        double range = gd.getNextNumber();
        boolean is2D = gd.getNextBoolean();
        double[] metrics=null;
        if(img1.getNSlices()<2 || is2D) {
            metrics=computeMetrics2D(img1,img2,range);
        }else{
            metrics=computeMetrics3D(img1, img2,range);
        }

        ResultsTable rt=ResultsTable.getResultsTable();
        if(rt==null)rt=new ResultsTable();
        rt.incrementCounter();
        rt.addValue("image1",img1.getShortTitle());
        rt.addValue("image2",img2.getShortTitle());
        rt.addValue("rmse",metrics[0]);
        rt.addValue("correlation",metrics[1]);
        rt.addValue("psnr",metrics[2]);
        rt.show("Results");

    }

    public double[] computeMetrics2D(ImagePlus img1, ImagePlus img2, double range){
        System.out.println("compute metrics 2D");
        double rmse= AlignImages.rmse(img1.getProcessor(),img2.getProcessor());
        double ncc= AlignImages.correlation(img1.getProcessor(),img2.getProcessor());
        double psnr= AlignImages.psnr(img1.getProcessor(),img2.getProcessor(),range);
        System.out.println("rmse="+rmse);
        System.out.println("ncc="+ncc);
        System.out.println("psnr="+psnr);
        return new double[]{rmse,ncc,psnr};
    }
    public double[] computeMetrics3D(ImagePlus img1, ImagePlus img2, double range){
        System.out.println("compute metrics in 3D");
        double rmse=0;
        double ncc=0;
        double psnr=0;
        ImageStack is1=img1.getImageStack();
        ImageStack is2=img2.getImageStack();
        for(int z=1;z<=img1.getNSlices();z++){
            rmse+= AlignImages.rmse(is1.getProcessor(z),is2.getProcessor(z));
            ncc+= AlignImages.correlation(is1.getProcessor(z),is2.getProcessor(z));
            psnr+= AlignImages.psnr(is1.getProcessor(z),is2.getProcessor(z),range);

        }

        rmse/=img1.getImageStackSize();
        ncc/=img1.getImageStackSize();
        psnr/=img1.getImageStackSize();
        double psnr2= 10*Math.log10(range*range/rmse);
        System.out.println("rmse="+rmse);
        System.out.println("ncc="+ncc);
        System.out.println("psnr="+psnr);
        System.out.println("psnr(true)="+psnr2);

        return new double[]{rmse,ncc,psnr2};


    }
}
