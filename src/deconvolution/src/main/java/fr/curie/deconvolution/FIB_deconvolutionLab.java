package fr.curie.deconvolution;

import deconvolution.Deconvolution;
import ij.*;
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import imagej.IJImager;

public class FIB_deconvolutionLab implements PlugInFilter {

    ImagePlus imp;
    int flags = DOES_8G + DOES_16 + DOES_32;
    boolean process2D=true;
    ImagePlus psf;
    String deconvOptions=" -algorithm RL 10";
    ImageStack is;
    


    @Override
    public int setup(String s, ImagePlus imagePlus) {
        imp=imagePlus;
        GenericDialog gd=new GenericDialog("deconvolve");
        int[] IDList= WindowManager.getIDList();
        String[] titles=WindowManager.getImageTitles();

        gd.addChoice("psf image",titles,titles[1]);
        gd.addCheckbox("process 2D",process2D);
        gd.addStringField("deconvolution options",deconvOptions, 20);

        gd.showDialog();
        psf=WindowManager.getImage(IDList[gd.getNextChoiceIndex()]);
        process2D=gd.getNextBoolean();
        deconvOptions=gd.getNextString();

        //return process2D? IJ.setupDialog(imp, flags):flags;
        return flags;
    }

    @Override
    public void run(ImageProcessor imageProcessor) {
        ImageProcessor ip = imp.getImageStack().getProcessor(1).duplicate();
        ImagePlus tmp = new ImagePlus("tmp", ip);
        FileSaver fs=new FileSaver(tmp);
        String filepath=IJ.getDirectory("temp")+"tmp.tif";
        //tmp.show();
        ImageProcessor psfIp = psf.getImageStack().getProcessor(1).duplicate();
        ImagePlus tmpPsf = new ImagePlus("tmpPsf", psfIp);
        FileSaver fspsf=new FileSaver(tmpPsf);
        String psfpath=  IJ.getDirectory("temp")+"psf.tif";
        String output = IJ.getDirectory("current");
        String algorithm = deconvOptions+" -path " + output;
        String parameters = " -monitor no ";
        Deconvolution deconv=new Deconvolution("Run",algorithm+parameters);


        for(int i=0;i<imp.getImageStackSize();i++) {
            ip.copyBits(imp.getImageStack().getProcessor(i+1),0,0,Blitter.COPY);
            psfIp.copyBits(psf.getImageStack().getProcessor(i+1),0,0,Blitter.COPY);
            deconv.deconvolve(IJImager.create(tmp),IJImager.create(tmpPsf));

            /*WindowManager.toFront(tmp.getWindow());

            ip.copyBits(imp.getImageStack().getProcessor(i+1),0,0,Blitter.COPY);
            //tmp.updateAndDraw();
            fs.saveAsTiff(filepath);
            psfIp.copyBits(psf.getImageStack().getProcessor(i+1),0,0,Blitter.COPY);
            //tmpPsf.updateAndDraw();
            fspsf.saveAsTiff(psfpath);


            String image = " -image file "+filepath;
            String psfString = " -psf file " + psfpath;
            String algorithm = " -algorithm RL 10 -path " + output;
            String parameters = " -monitor no ";

            int prevn = WindowManager.getImageCount();
            IJ.run("DeconvolutionLab2 Run", image + psfString + algorithm + parameters);

            while(WindowManager.getImageCount() == prevn)
                IJ.wait(50);
            ImagePlus res=WindowManager.getImage(WindowManager.getIDList()[WindowManager.getImageCount()-1]);
            //imp.getImageStack().getProcessor(i+1).copyBits(new ImagePlus("Final Display of LW").getProcessor(),0,0,Blitter.COPY);
            imp.getImageStack().getProcessor(i+1).copyBits(res.getProcessor(),0,0,Blitter.COPY);
            res.close();      */
            IJ.showProgress((i+1.0)/imp.getImageStackSize());
        }

    }
}
