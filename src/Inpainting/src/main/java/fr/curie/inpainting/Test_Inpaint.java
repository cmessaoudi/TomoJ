package fr.curie.inpainting;

import fractsplinewavelets.CoefProcessing;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilter;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import imageware.Builder;
import imageware.ImageWare;
import fr.curie.tomoj.tomography.projectors.CompressedSensingProjector;
import fr.curie.utils.align.AlignImages;
import fr.curie.wavelets.WaveletProcessing;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by cmessaoudi on 19/10/2017.
 */
public class Test_Inpaint implements PlugInFilter {
    ImagePlus imp;
    int[] iters= new int[]{2,2,0};
    int nbProcessing=10;
    double threshold=10;
    int[] kernelFilter={1,2,1,2,4,2,1,2,1};
    String[] waveletType={"orthonormal","bspline","dual"};
    int windex;
    double waveletDegrees=3;
    int waveType;
    boolean fillNaN=false;
    boolean basicConvolve=true;
    ImagePlus refImag;

    public int setup(String s, ImagePlus imagePlus) {
        imp=imagePlus;
        GenericDialog gd=new GenericDialog("test inpainting with CS");
        gd.addCheckbox("use basic inpaint as first step?",true);
        gd.addCheckbox("gaussian filter in basic inpainting?", true);
        gd.addNumericField("number of processing",nbProcessing,0);
        gd.addChoice("wavelet type",waveletType,waveletType[0]);
        gd.addNumericField("wavelet degrees",waveletDegrees,2);
        gd.addNumericField("hard threshold",threshold, 2);
        gd.addNumericField("X nb reduction",iters[0],0);
        gd.addNumericField("Y nb reduction",iters[1],0);
        String[] titles = WindowManager.getImageTitles();
        gd.addChoice("reference image",titles,titles[0]);
        gd.showDialog();
        if (gd.wasCanceled()) return PlugInFilter.DONE;
        fillNaN=!gd.getNextBoolean();
        basicConvolve=gd.getNextBoolean();
        nbProcessing=(int)gd.getNextNumber();
        windex=gd.getNextChoiceIndex();
        switch (windex){
            case 0:
                waveType= CompressedSensingProjector.WAVELET_ORTHONORMAL;
                break;
            case 1:
                waveType=CompressedSensingProjector.WAVELET_BSPLINE;
                break;
            case 2:
                waveType=CompressedSensingProjector.WAVELET_DUAL;
        }
        waveletDegrees=gd.getNextNumber();
        threshold=gd.getNextNumber();
        iters[0]=(int)gd.getNextNumber();
        iters[1]=(int)gd.getNextNumber();
        int index=gd.getNextChoiceIndex();
        System.out.println("index="+index);
        int ID=WindowManager.getNthImageID(index);
        System.out.println("ids: "+ID);
        int[] ids=WindowManager.getIDList();
        for(int i:ids) System.out.println(i);
        refImag= WindowManager.getImage(ids[index]);
        if(refImag==null){
            IJ.log("ref is null");
        }
        return PlugInFilter.DOES_32;
    }

    public void run(ImageProcessor imageProcessor) {
        ImageProcessor original=imageProcessor.duplicate();
        if(fillNaN){
            fillNaNWithValue(imageProcessor,0);
        }else {
            ArrayList<ImageProcessor> list = new ArrayList<ImageProcessor>(10);
            ImageProcessor ipNaN = imageProcessor.duplicate();
            float[] pixels = (float[]) ipNaN.getPixels();
            for (int index = 0; index < pixels.length; index++) {
                if (pixels[index] == 0) pixels[index] = Float.NaN;
            }
            list.add(ipNaN);
            int currentIndex = 0;
            System.out.println("starting image : nb NaN=" + getNumberOfNaN((float[]) ipNaN.getPixels()));
            //new ImagePlus("NaNs",ipNaN.duplicate()).show();
            while (stillNaN((float[]) list.get(currentIndex).getPixels())) {
                ImageProcessor reduced = reduceSize(list.get(currentIndex));
                currentIndex++;
                //new ImagePlus("reduced "+currentIndex,reduced.duplicate()).show();
                list.add(reduced);
            }
            System.out.println("number of scale down : " + currentIndex);

            while (currentIndex > 0) {
                currentIndex--;
                fillNaN(list.get(currentIndex), list.get(currentIndex + 1));
                if(basicConvolve) list.get(currentIndex).convolve3x3(kernelFilter);

                //new ImagePlus("reduced inpainted"+currentIndex,list.get(currentIndex)).show();
            }
            imageProcessor.copyBits(list.get(0), 0, 0, Blitter.COPY);
        }

        //compute score with reference
        double correlationbefore = AlignImages.correlation((float[])refImag.getProcessor().convertToFloatProcessor().getPixels(),(float[])imageProcessor.convertToFloatProcessor().getPixels());
        //IJ.log("correlation between "+refImag.getTitle()+" and "+imp.getTitle()+" (after basic inpainting): "+correlation);

        double[] correlations = new double[nbProcessing];
        for(int nbfois=1;nbfois<=nbProcessing;nbfois++) {
            ImageWare buffer = Builder.create(imp, 3);
            WaveletProcessing.doTransform3D(buffer, iters, waveType, waveletDegrees, 0);
            CoefProcessing.doHardThreshold3D(buffer, threshold);
            WaveletProcessing.doInverse3D(buffer, iters, waveType, waveletDegrees, 0);

            for (int y = 0; y < buffer.getSizeY(); y++) {
                for (int x = 0; x < buffer.getSizeX(); x++) {
                    double value = buffer.getPixel(x, y, 0);
                    imageProcessor.putPixelValue(x, y, value);
                }
            }

            for (int y = 0; y < imageProcessor.getHeight(); y++) {
                for (int x = 0; x < imageProcessor.getWidth();x++) {
                    double val=original.getPixelValue(x,y);
                    if(!Double.isNaN(val)){
                        imageProcessor.putPixelValue(x,y,val);
                    }
                }
            }
            IJ.showProgress(nbfois,nbProcessing);
            correlations[nbfois-1]=AlignImages.correlation((float[])refImag.getProcessor().convertToFloatProcessor().getPixels(),(float[])imageProcessor.convertToFloatProcessor().getPixels());
        }
        //double correlationafter = AlignImages.correlation((float[])refImag.getProcessor().convertToFloatProcessor().getPixels(),(float[])imageProcessor.convertToFloatProcessor().getPixels());
        // IJ.log("correlation between "+refImag.getTitle()+" and "+imp.getTitle()+" (after CS "+nbProcessing+"iterations , threshold:"+threshold+"): "+correlation);
        ResultsTable rt=ResultsTable.getResultsTable();
        rt.setPrecision(8);
        for(int i=0;i<nbProcessing;i++) {
            rt.incrementCounter();
            rt.addValue("image title",imp.getTitle());
            rt.addValue("basic inpaint", "" + (!fillNaN));
            rt.addValue("basic gaussian filter", ""+basicConvolve);
            rt.addValue("nb steps CS", nbProcessing);
            rt.addValue("CS wavelet type", waveletType[windex]);
            rt.addValue("CS wavelet degrees", waveletDegrees);
            rt.addValue("CS threshold", threshold);
            rt.addValue("CS iter X",iters[0]);
            rt.addValue("CS iter Y",iters[1]);
            rt.addValue("after first step", correlationbefore);
            rt.addValue("step", (i+1));
            rt.addValue("after CS steps", correlations[i]);
        }
        rt.show("Results");





        /*ArrayList<ImageProcessor> list=new ArrayList<ImageProcessor>(10);
        GenericDialog gd=new GenericDialog("Inpaint option");
        gd.addCheckbox("NaN already present in image", false);
        gd.addNumericField("percentage of image to put to NaN",0.5,2);
        gd.addCheckbox("simulation as STEM acquisition",false);
        gd.addCheckbox("simulation lines",false);
        gd.addCheckbox("simulation lines (row/column)",false);
        gd.showDialog();
        if(gd.wasCanceled()) return;

        boolean existingNaN=gd.getNextBoolean();
        double percentageNaN=gd.getNextNumber();
        boolean stemNaN=gd.getNextBoolean();
        boolean linesNaN=gd.getNextBoolean();
        boolean linesRowColNaN=gd.getNextBoolean();

        ImageProcessor ipNaN;
        if(existingNaN){
            ipNaN=imageProcessor.duplicate();
            float[] pixels=(float[]) ipNaN.getPixels();
            for(int index=0;index<pixels.length;index++){
                if(pixels[index]==0) pixels[index]=Float.NaN;
            }
        }else{
            if(stemNaN){
                ipNaN=new FloatProcessor(imageProcessor.getWidth(),imageProcessor.getHeight());
                float[] pixels=(float[]) ipNaN.getPixels();
                Arrays.fill(pixels,Float.NaN);
                int nbNaN= (int) (percentageNaN*pixels.length);
                int i = pixels.length;
                while (i > nbNaN) {
                    int x = (int) (Math.random() * imageProcessor.getWidth());
                    int y = (int) (Math.random() * imageProcessor.getHeight());
                    x=Math.min(x,imageProcessor.getWidth()-16);
                    y=Math.min(y,imageProcessor.getHeight()-4);
                    //System.out.println("x:"+x+" , y:"+y);
                    //ipNaN.putPixelValue(x,y,imageProcessor.getPixelValue(x,y));
                    for(int yy=y;yy<y+4;yy++){
                        for(int xx=x;xx<x+16;xx++){
                            ipNaN.setf(xx,yy,imageProcessor.getf(xx,yy));
                        }
                    }
                    i-=8*4;
                }

            }else if(linesNaN){
                ipNaN=new FloatProcessor(imageProcessor.getWidth(),imageProcessor.getHeight());
                float[] pixels=(float[]) ipNaN.getPixels();
                Arrays.fill(pixels,Float.NaN);
                int nbNaN= (int) (percentageNaN*pixels.length);
                int i = pixels.length;
                while (i > nbNaN) {

                    int y = (int) (Math.random() * imageProcessor.getHeight());
                    //y=Math.min(y,ipNaN.getHeight()-4);
                    //System.out.println("x:"+x+" , y:"+y);
                    //ipNaN.putPixelValue(x,y,imageProcessor.getPixelValue(x,y));

                    for (int xx = 0; xx < ipNaN.getWidth(); xx++) {
                        ipNaN.setf(xx, y, imageProcessor.getf(xx, y));
                        //ipNaN.setf(xx, y+1, imageProcessor.getf(xx, y));
                        //ipNaN.setf(xx, y+2, imageProcessor.getf(xx, y));
                        //ipNaN.setf(xx, y+3, imageProcessor.getf(xx, y));
                    }
                    i -= ipNaN.getWidth();
                    //i -= ipNaN.getWidth()*4;

                }

            }else if(linesRowColNaN){
                ipNaN=new FloatProcessor(imageProcessor.getWidth(),imageProcessor.getHeight());
                float[] pixels=(float[]) ipNaN.getPixels();
                Arrays.fill(pixels,Float.NaN);
                int nbNaN= (int) (percentageNaN*pixels.length);
                int i = pixels.length;
                while (i > nbNaN) {
                    boolean row=Math.random()>0.5;
                    if (row) {
                        int y = (int) (Math.random() * imageProcessor.getHeight());
                        //System.out.println("x:"+x+" , y:"+y);
                        //ipNaN.putPixelValue(x,y,imageProcessor.getPixelValue(x,y));

                        for (int xx = 0; xx < ipNaN.getWidth(); xx++) {
                            ipNaN.setf(xx, y, imageProcessor.getf(xx, y));
                        }
                        i -= ipNaN.getWidth();
                    }else{
                        int x = (int) (Math.random() * imageProcessor.getWidth());
                        //System.out.println("x:"+x+" , y:"+y);
                        //ipNaN.putPixelValue(x,y,imageProcessor.getPixelValue(x,y));

                        for (int yy = 0; yy < ipNaN.getHeight(); yy++) {
                            ipNaN.setf(x, yy, imageProcessor.getf(x, yy));
                        }
                        i -= ipNaN.getHeight();
                    }
                }

            }else{
                ipNaN=imageProcessor.duplicate();
                float[] pixels=(float[]) ipNaN.getPixels();
                int nbNaN= (int) (percentageNaN*pixels.length);
                int i = 0;
                while (i < nbNaN) {
                    int index = (int) (Math.random() * pixels.length);
                    if (!Float.isNaN(pixels[index])) {
                        pixels[index] = Float.NaN;
                        i++;
                    }
                }
            }
        }

        list.add(ipNaN);
        int currentIndex=0;
        System.out.println("starting image : nb NaN="+getNumberOfNaN((float[])ipNaN.getPixels()));
        new ImagePlus("NaNs",ipNaN.duplicate()).show();
        while(stillNaN((float[])list.get(currentIndex).getPixels())){
            ImageProcessor reduced=reduceSize(list.get(currentIndex));
            currentIndex++;
            new ImagePlus("reduced "+currentIndex,reduced.duplicate()).show();
            list.add(reduced);
        }

        while (currentIndex>0){
            currentIndex--;
            fillNaN(list.get(currentIndex),list.get(currentIndex+1));
            new ImagePlus("reduced inpainted"+currentIndex,list.get(currentIndex)).show();
        }*/

    }

    int getNumberOfNaN(float[] data){
        int count=0;
        for(float f:data){
            if (Float.isNaN(f))count++;
        }
        return count;
    }

    boolean stillNaN(float[] data){
        for(float f:data){
            if(Float.isNaN(f))return true;
        }
        return false;
    }

    ImageProcessor reduceSize(ImageProcessor ip){
        ImageProcessor reduced=ip.createProcessor(ip.getWidth()/2,ip.getHeight()/2);
        for(int y=0;y<reduced.getHeight();y++){
            for(int x=0;x<reduced.getWidth();x++){
                int count=0;
                float sum=0;
                float val=ip.getf(x*2,y*2);
                if(!Float.isNaN(val)){
                    sum+=val;
                    count++;
                }
                val=ip.getf(x*2+1,y*2);
                if(!Float.isNaN(val)){
                    sum+=val;
                    count++;
                }
                val=ip.getf(x*2,y*2+1);
                if(!Float.isNaN(val)){
                    sum+=val;
                    count++;
                }
                val=ip.getf(x*2+1,y*2+1);
                if(!Float.isNaN(val)){
                    sum+=val;
                    count++;
                }
                reduced.setf(x,y,sum/count);

            }
        }


        return reduced;

    }

    void fillNaNWithValue(ImageProcessor ipNaN, float value){
        for(int y=0;y<ipNaN.getHeight();y++){
            for(int x=0;x<ipNaN.getWidth();x++){
                if(Double.isNaN(ipNaN.getPixelValue(x,y))){
                    ipNaN.setf(x,y,value);
                }
            }
        }
    }

    void fillNaN(ImageProcessor ipNaN, ImageProcessor ipReduced){
        for(int y=0;y<ipNaN.getHeight();y++){
            for(int x=0;x<ipNaN.getWidth();x++){
                if(Double.isNaN(ipNaN.getPixelValue(x,y))){
                    ipNaN.setf(x,y,(float)ipReduced.getInterpolatedPixel(x/2,y/2));
                }
            }
        }
    }
}
