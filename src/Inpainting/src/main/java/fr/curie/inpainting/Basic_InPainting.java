package fr.curie.inpainting;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.ImageProcessor;
//import inputoutput.MRC_Writer;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * Created by cmessaoudi on 17/01/2018.
 */
public class Basic_InPainting implements ExtendedPlugInFilter, DialogListener{
    PlugInFilterRunner pfr;
    boolean preview;
    boolean meanfilter=true;
    boolean xDirection=true;
    boolean yDirection=true;
    boolean zerosToNaN=false;
    boolean externalFilter=false;
    static int[] kernelFilterXY={1,2,1,2,4,2,1,2,1};
    static int[] kernelFilterX={0,0,0,1,2,1,0,0,0};
    static int[] KernelFilterY={0,1,0,0,2,0,0,1,0};

    int flags = DOES_32 + PARALLELIZE_STACKS;
    ImagePlus myimp;

    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awtEvent) {

        xDirection=gd.getNextBoolean();
        yDirection=gd.getNextBoolean();
        meanfilter=gd.getNextBoolean();
        zerosToNaN=gd.getNextBoolean();
        externalFilter=false;
        return true;
    }

    public int showDialog(ImagePlus imagePlus, String s, PlugInFilterRunner plugInFilterRunner) {
        this.pfr = plugInFilterRunner;
        preview = true;
        GenericDialog gd=new GenericDialog("parameters");
        gd.addCheckbox("X direction:",xDirection);
        gd.addCheckbox("Y direction:",yDirection);
        gd.addCheckbox("gaussian filter",meanfilter);
        gd.addCheckbox("inpaint zeros",zerosToNaN);
        gd.addCheckbox("external filter",externalFilter);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if(gd.wasCanceled()) return DONE;
        xDirection=gd.getNextBoolean();
        yDirection=gd.getNextBoolean();
        meanfilter=gd.getNextBoolean();
        zerosToNaN=gd.getNextBoolean();
        externalFilter=gd.getNextBoolean();
        return IJ.setupDialog(imagePlus, flags);
    }

    public void setNPasses(int i) {

    }

    public int setup(String s, ImagePlus imagePlus) {
        myimp=imagePlus;
        return flags;
    }

    public void run(ImageProcessor imageProcessor) {
        ImageProcessor ipNaN=imageProcessor;//.duplicate();
        float[] pixels=(float[]) ipNaN.getPixels();
        if(zerosToNaN) {
            for (int index = 0; index < pixels.length; index++) {
                if (pixels[index] == 0) pixels[index] = Float.NaN;
            }
        }
        basicInpainting(ipNaN,xDirection,yDirection,meanfilter,externalFilter);
        /*ArrayList<ImageProcessor> list=new ArrayList<ImageProcessor>(10);
        ImageProcessor ipNaN=imageProcessor;//.duplicate();
        float[] pixels=(float[]) ipNaN.getPixels();
        if(zerosToNaN) {
            for (int index = 0; index < pixels.length; index++) {
                if (pixels[index] == 0) pixels[index] = Float.NaN;
            }
        }
        list.add(ipNaN);
        int currentIndex=0;
        System.out.println("starting image : nb NaN="+getNumberOfNaN((float[])ipNaN.getPixels()));
        //new ImagePlus("NaNs",ipNaN.duplicate()).show();
        while(stillNaN((float[])list.get(currentIndex).getPixels())){
            ImageProcessor reduced=reduceSize(list.get(currentIndex),xDirection,yDirection);
            currentIndex++;
            if(externalFilter){
                ImagePlus tmpImp=new ImagePlus("reduced "+currentIndex,reduced.duplicate());
                IJ.saveAsTiff(tmpImp,IJ.getDirectory("image")+ File.separator+myimp.getTitle()+"_reduced"+currentIndex+".tif");
            }
            list.add(reduced);
        }
        System.out.println("number of scale down : "+currentIndex);
        int[] kernelFilter=kernelFilterXY;
        if(xDirection&&!yDirection) {
            kernelFilter=kernelFilterX;
            System.out.println("using X kernel");
        }
        if(!xDirection&&yDirection){
            kernelFilter=KernelFilterY;
            System.out.println("using Y kernel");
        }
        while (currentIndex>0){
            currentIndex--;
            fillNaN(list.get(currentIndex),list.get(currentIndex+1),xDirection,yDirection);
            if(currentIndex!=0&&meanfilter){
                System.out.println("filtering");
                if(externalFilter){
                    ImagePlus tmpImp=new ImagePlus("reduced "+currentIndex,list.get(currentIndex).duplicate());
                    IJ.saveAsTiff(tmpImp,IJ.getDirectory("current")+ File.separator+myimp.getTitle()+"_reduced"+currentIndex+"Filled.tif");
                    IJ.runPlugIn(tmpImp, "MRC_Writer", IJ.getDirectory("current")+ File.separator+myimp.getTitle()+"_reduced"+currentIndex+"Filled.mrc");
                    ImagePlus resultImp=IJ.openImage();
                    list.get(currentIndex).copyBits(resultImp.getProcessor(),0,0,Blitter.COPY);
                }else list.get(currentIndex).convolve3x3(kernelFilter);
            }
            //new ImagePlus("reduced inpainted"+currentIndex,list.get(currentIndex)).show();
        }
        //imageProcessor.copyBits(list.get(0), 0, 0, Blitter.COPY);   */

    }

    public static ImageProcessor basicInpainting(ImageProcessor ipNaN, boolean xDirection,boolean yDirection, boolean meanfilter, boolean externalFilter){
        ArrayList<ImageProcessor> list=new ArrayList<ImageProcessor>(10);

        float[] pixels=(float[]) ipNaN.getPixels();
        list.add(ipNaN);
        int currentIndex=0;
        System.out.println("starting image : nb NaN="+getNumberOfNaN((float[])ipNaN.getPixels()));
        //new ImagePlus("NaNs",ipNaN.duplicate()).show();
        while(stillNaN((float[])list.get(currentIndex).getPixels())){
            ImageProcessor reduced=reduceSize(list.get(currentIndex),xDirection,yDirection);
            currentIndex++;
            if(externalFilter){
                ImagePlus tmpImp=new ImagePlus("reduced "+currentIndex,reduced.duplicate());
                IJ.saveAsTiff(tmpImp,IJ.getDirectory("image")+ File.separator+"_reduced"+currentIndex+".tif");
            }
            list.add(reduced);
        }
        System.out.println("number of scale down : "+currentIndex);
        int[] kernelFilter=kernelFilterXY;
        if(xDirection&&!yDirection) {
            kernelFilter=kernelFilterX;
            System.out.println("using X kernel");
        }
        if(!xDirection&&yDirection){
            kernelFilter=KernelFilterY;
            System.out.println("using Y kernel");
        }
        while (currentIndex>0){
            currentIndex--;
            fillNaN(list.get(currentIndex),list.get(currentIndex+1),xDirection,yDirection);
            if(currentIndex!=0&&meanfilter){
                System.out.println("filtering");
                if(externalFilter){
                    ImagePlus tmpImp=new ImagePlus("reduced "+currentIndex,list.get(currentIndex).duplicate());
                    IJ.saveAsTiff(tmpImp,IJ.getDirectory("current")+ File.separator+"_reduced"+currentIndex+"Filled.tif");
                    IJ.runPlugIn(tmpImp, "MRC_Writer", IJ.getDirectory("current")+ File.separator+"_reduced"+currentIndex+"Filled.mrc");
                    ImagePlus resultImp=IJ.openImage();
                    list.get(currentIndex).copyBits(resultImp.getProcessor(),0,0,Blitter.COPY);
                }else list.get(currentIndex).convolve3x3(kernelFilter);
            }
            //new ImagePlus("reduced inpainted"+currentIndex,list.get(currentIndex)).show();
        }
        //imageProcessor.copyBits(list.get(0), 0, 0, Blitter.COPY);
        return ipNaN;
    }

    static int getNumberOfNaN(float[] data){
        int count=0;
        for(float f:data){
            if (Float.isNaN(f))count++;
        }
        return count;
    }

    static boolean stillNaN(float[] data){
        for(float f:data){
            if(Float.isNaN(f))return true;
        }
        return false;
    }

    static ImageProcessor reduceSize(ImageProcessor ip, boolean xDirection,boolean yDirection){
        int newWidth=(xDirection)?ip.getWidth()/2:ip.getWidth();
        int newHeight=(yDirection)?ip.getHeight()/2:ip.getHeight();
        ImageProcessor reduced=ip.createProcessor(newWidth,newHeight);
        for(int y=0;y<reduced.getHeight();y++){
            for(int x=0;x<reduced.getWidth();x++){
                int count=0;
                float sum=0;
                int xreduced=(xDirection)?x*2:x;
                int yreduced=(yDirection)?y*2:y;
                float val=ip.getf(xreduced,yreduced);
                if(!Float.isNaN(val)){
                    sum+=val;
                    count++;
                }
                if(xDirection) {
                    val = ip.getf(xreduced + 1, yreduced);
                    if (!Float.isNaN(val)) {
                        sum += val;
                        count++;
                    }
                }
                if(yDirection) {
                    val = ip.getf(xreduced, yreduced + 1);
                    if (!Float.isNaN(val)) {
                        sum += val;
                        count++;
                    }
                }
                if(xDirection&&yDirection) {
                    val = ip.getf(xreduced + 1, yreduced + 1);
                    if (!Float.isNaN(val)) {
                        sum += val;
                        count++;
                    }
                }
                sum=(count==0)?Float.NaN:sum/count;
                reduced.setf(x,y,sum);

            }
        }


        return reduced;

    }

    static void fillNaN(ImageProcessor ipNaN, ImageProcessor ipReduced, boolean xDirection,boolean yDirection){
        for(int y=0;y<ipNaN.getHeight();y++){
            for(int x=0;x<ipNaN.getWidth();x++){
                if(Double.isNaN(ipNaN.getPixelValue(x,y))){
                    int newX=(xDirection)?x/2:x;
                    int newY=(yDirection)?y/2:y;
                    ipNaN.setf(x,y,(float)ipReduced.getInterpolatedPixel(newX,newY));
                }
            }
        }

    }
}
