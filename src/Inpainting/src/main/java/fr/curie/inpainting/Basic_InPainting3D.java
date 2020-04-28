package fr.curie.inpainting;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.CurveFitter;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import fr.curie.utils.Chrono;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by cmessaoudi on 17/01/2018.
 */
public class Basic_InPainting3D implements PlugInFilter, DialogListener{
    PlugInFilterRunner pfr;
    boolean preview;
    boolean meanfilter=true;
    boolean xDirection=true;
    boolean yDirection=true;
    boolean zDirection=true;
    boolean zerosToNaN=false;
    int[] kernelFilterXY={1,2,1,2,4,2,1,2,1};
    int[] kernelFilterX={0,0,0,1,2,1,0,0,0};
    int[] KernelFilterY={0,1,0,0,2,0,0,1,0};

    ImagePlus imp;

    int flags = DOES_32 + STACK_REQUIRED;

    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awtEvent) {

        xDirection=gd.getNextBoolean();
        yDirection=gd.getNextBoolean();
        zDirection=gd.getNextBoolean();
        meanfilter=gd.getNextBoolean();
        zerosToNaN=gd.getNextBoolean();
        return true;
    }

    public int showDialog(ImagePlus imagePlus) {
        //this.pfr = plugInFilterRunner;
        this.imp=imagePlus;
        preview = true;
        GenericDialog gd=new GenericDialog("parameters");
        gd.addCheckbox("X direction:",xDirection);
        gd.addCheckbox("Y direction:",yDirection);
        gd.addCheckbox("Z direction:",zDirection);
        gd.addCheckbox("mean filter",meanfilter);
        gd.addCheckbox("inpaint zeros",zerosToNaN);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if(gd.wasCanceled()) return PlugInFilter.DONE;
        xDirection=gd.getNextBoolean();
        yDirection=gd.getNextBoolean();
        zDirection=gd.getNextBoolean();
        meanfilter=gd.getNextBoolean();
        zerosToNaN=gd.getNextBoolean();
        return flags;
    }

    public int setup(String s, ImagePlus imagePlus) {
        return showDialog(imagePlus);
    }

    public void run(ImageProcessor imageProcessor) {
        if(zDirection&&!xDirection&&!yDirection) {
            final AtomicInteger progress=new AtomicInteger(0);
            ExecutorService exec= Executors.newFixedThreadPool(Prefs.getThreads());
            ArrayList<Future> jobs=new ArrayList<Future>(imp.getHeight()*imp.getWidth());
            Chrono time=new Chrono();
            time.start();
            for(int y = 0; y<imp.getHeight(); y++){
                final int yy=y;
                jobs.add(exec.submit(new Thread(){
                    @Override
                    public void run() {
                        for (int x = 0; x < imp.getWidth(); x++) {
                            final int xx = x;
                            run1D_zaxis(xx, yy);
                            synchronized (progress) {
                                IJ.showProgress(progress.incrementAndGet(), imp.getHeight() * imp.getWidth());
                            }

                        }
                    }
                }));

            }
            try{
                for (Future job:jobs){
                    job.get();
                }
            }catch (Exception e){e.printStackTrace();}

            time.stop();
            IJ.showStatus("time to compute: "+time.delayString());
            System.out.println("time to compute: "+time.delayString());

        } else if(!zDirection) run2D(imageProcessor);
        else run3D();
    }

    public void run3D(){
        ArrayList<ImageStack> list = new ArrayList<ImageStack>(10);
        ImageStack isNaN=imp.getImageStack();
        if(zerosToNaN) convertZerosToNaN(isNaN);
        list.add(isNaN);
        int currentIndex=0;
        System.out.println("starting image : nb NaN="+getNumberOfNaN(isNaN));
        //new ImagePlus("NaNs",ipNaN.duplicate()).show();
        while(stillNaN(list.get(currentIndex))){
            ImageStack reduced=reduceSize(list.get(currentIndex));
            currentIndex++;
            //new ImagePlus("reduced "+currentIndex,reduced.duplicate()).show();
            list.add(reduced);
        }
        System.out.println("number of scale down : "+currentIndex);

        int[] kernelFilter=kernelFilterXY;
        if(xDirection&&!yDirection) kernelFilter=kernelFilterX;
        if(!xDirection&&yDirection) kernelFilter=KernelFilterY;
        while (currentIndex>0){
            currentIndex--;
            fillNaN(list.get(currentIndex),list.get(currentIndex+1));
            if(currentIndex!=0&&meanfilter){
                ImageStack is=list.get(currentIndex);
                for(int z=1;z<=is.getSize();z++){
                    is.getProcessor(z).convolve3x3(kernelFilter);
                }
            }
            //new ImagePlus("reduced inpainted"+currentIndex,list.get(currentIndex)).show();
        }



    }

    public void run2D(ImageProcessor imageProcessor){
        ArrayList<ImageProcessor> list=new ArrayList<ImageProcessor>(10);
        ImageProcessor ipNaN=imageProcessor.duplicate();
        if(zerosToNaN) {
            float[] pixels=(float[]) ipNaN.getPixels();
            for (int index = 0; index < pixels.length; index++) {
                if (pixels[index] == 0) pixels[index] = Float.NaN;
            }
        }
        list.add(ipNaN);
        int currentIndex=0;
        System.out.println("starting image : nb NaN="+getNumberOfNaN((float[])ipNaN.getPixels()));
        //new ImagePlus("NaNs",ipNaN.duplicate()).show();
        while(stillNaN((float[])list.get(currentIndex).getPixels())){
            ImageProcessor reduced=reduceSize(list.get(currentIndex));
            currentIndex++;
            //new ImagePlus("reduced "+currentIndex,reduced.duplicate()).show();
            list.add(reduced);
        }
        System.out.println("number of scale down : "+currentIndex);
        int[] kernelFilter=kernelFilterXY;
        if(xDirection&&!yDirection) kernelFilter=kernelFilterX;
        if(!xDirection&&yDirection) kernelFilter=KernelFilterY;
        while (currentIndex>0){
            currentIndex--;
            fillNaN(list.get(currentIndex),list.get(currentIndex+1));
            if(currentIndex!=0&&meanfilter){
                list.get(currentIndex).convolve3x3(kernelFilter);
            }
            //new ImagePlus("reduced inpainted"+currentIndex,list.get(currentIndex)).show();
        }
        imageProcessor.copyBits(list.get(0), 0, 0, Blitter.COPY);

    }

    public void run1D_zaxis(int x, int y){
        ArrayList<float[]> list= new ArrayList<float[]>(10);
        float[] dataZ=new float[imp.getImageStackSize()];
        int position=y*imp.getWidth()+x;
        for(int z=0;z<imp.getImageStackSize();z++){
            //dataZ[z]=imp.getImageStack().getProcessor(z+1).getPixelValue(x,y);
            dataZ[z]=((float[])imp.getImageStack().getPixels(z+1))[position];
        }
        list.add(dataZ);
        int currentIndex=0;
        //System.out.println("starting image : nb NaN="+getNumberOfNaN(dataZ));
        while(stillNaN(list.get(currentIndex))){
            list.add(reduceSize(list.get(currentIndex)));
            currentIndex++;
        }

        //System.out.println("number of scale down : "+currentIndex);
        while (currentIndex>0){
            currentIndex--;
            fillNaN(list.get(currentIndex),list.get(currentIndex+1));
            if(currentIndex!=0&&meanfilter){
                filterGaussian1D(list.get(currentIndex));
            }
        }
        for(int z=0;z<imp.getImageStackSize();z++){
            //imp.getImageStack().getProcessor(z+1).putPixelValue(x,y,dataZ[z]);
            ((float[])imp.getImageStack().getPixels(z+1))[position]=dataZ[z];
        }
    }

    public void run1D_zaxisInterpolation(int x, int y){
        ArrayList<Double> valueslist=new ArrayList<Double>(imp.getImageStackSize());
        ArrayList<Double> zlist=new ArrayList<Double>(imp.getImageStackSize());
        for(int z=0;z<imp.getImageStackSize();z++){
            double value=imp.getImageStack().getProcessor(z+1).getPixelValue(x,y);
            if(!Double.isNaN(value)){
                zlist.add((double)z);
                valueslist.add(value);
            }
        }

        double[] zs=new double[zlist.size()];
        double[] values=new double[valueslist.size()];
        for(int i=0;i<zs.length;i++){
            zs[i]=zlist.get(i);
            values[i]=valueslist.get(i);
        }

        CurveFitter cf=new CurveFitter(zs,values);
        cf.doFit(CurveFitter.POLY6);
        for(int z=0;z<imp.getImageStackSize();z++){
            double value=imp.getImageStack().getProcessor(z+1).getPixelValue(x,y);
            if(Double.isNaN(value)){
                imp.getImageStack().getProcessor((z+1)).putPixelValue(x,y,cf.f(z));
            }
        }






    }

    int getNumberOfNaN(float[] data){
        int count=0;
        for(float f:data){
            if (Float.isNaN(f))count++;
        }
        return count;
    }

    int getNumberOfNaN(ImageStack is){
        int count=0;
        for(int z=1;z<is.getSize();z++){
            count+=getNumberOfNaN((float[])is.getPixels(z));
        }
        return count;
    }

    boolean stillNaN(float[] data){
        for(float f:data){
            if(Float.isNaN(f))return true;
        }
        return false;
    }

    boolean stillNaN(ImageStack is){
        for(int z=1;z<=is.getSize();z++){
            if(stillNaN((float[])is.getPixels(z))) return true;
        }
        return false;
    }

    ImageStack reduceSize(ImageStack is){
        int newWidth=(xDirection)?is.getWidth()/2:is.getWidth();
        int newHeight=(yDirection)?is.getHeight()/2:is.getHeight();
        int newDepth=(zDirection)?is.getSize()/2:is.getSize();
        ImageStack reduced = new ImageStack(newWidth,newHeight);
        for(int z=0;z<newDepth;z++) {
            ImageProcessor reducedip = is.getProcessor(1).createProcessor(newWidth, newHeight);
            int correspondingZ=(zDirection)?z*2:z;
            ImageProcessor ip1=is.getProcessor(correspondingZ+1); //+1 for the change of base in imageStack (not starting at 0)
            ImageProcessor ip2=(zDirection)? is.getProcessor(correspondingZ+2):ip1;
            for (int y = 0; y < newHeight; y++) {
                for (int x = 0; x < newWidth; x++) {
                    int count = 0;
                    float sum = 0;
                    int xreduced=(xDirection)?x*2:x;
                    int yreduced=(yDirection)?y*2:y;
                    float val = ip1.getf(xreduced, yreduced);
                    if (!Float.isNaN(val)) {
                        sum += val;
                        count++;
                    }
                    if(xDirection) {
                        val = ip1.getf(xreduced + 1, yreduced);
                        if (!Float.isNaN(val)) {
                            sum += val;
                            count++;
                        }
                    }
                    if(yDirection) {
                        val = ip1.getf(xreduced, yreduced + 1);
                        if (!Float.isNaN(val)) {
                            sum += val;
                            count++;
                        }
                    }
                    if(xDirection&&yDirection) {
                        val = ip1.getf(xreduced + 1, yreduced + 1);
                        if (!Float.isNaN(val)) {
                            sum += val;
                            count++;
                        }
                    }
                    if(zDirection){
                        val = ip2.getf(xreduced, yreduced);
                        if (!Float.isNaN(val)) {
                            sum += val;
                            count++;
                        }
                        if(xDirection) {
                            val = ip2.getf(xreduced + 1, yreduced);
                            if (!Float.isNaN(val)) {
                                sum += val;
                                count++;
                            }
                        }
                        if(yDirection) {
                            val = ip2.getf(xreduced, yreduced + 1);
                            if (!Float.isNaN(val)) {
                                sum += val;
                                count++;
                            }
                        }
                        if(xDirection&&yDirection) {
                            val = ip2.getf(xreduced + 1, yreduced + 1);
                            if (!Float.isNaN(val)) {
                                sum += val;
                                count++;
                            }
                        }
                    }
                    sum = (count == 0) ? Float.NaN : sum / count;
                    reducedip.setf(x, y, sum);

                }
            }
            reduced.addSlice(is.getSliceLabel(z+1),reducedip);
        }
        return reduced;
    }

    ImageProcessor reduceSize(ImageProcessor ip){
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

    float[] reduceSize(float[] data){
        int newSize=data.length/2;
        float[] reduced=new float[newSize];
        for(int i=0;i<reduced.length;i++) {
            int count = 0;
            float sum = 0;
            int indexBig = i * 2;
            if (!Float.isNaN(data[indexBig])) {
                sum += data[indexBig];
                count++;
            }
            indexBig=Math.min(indexBig+1, data.length-1);
            if (!Float.isNaN(data[indexBig])) {
                sum += data[indexBig];
                count++;
            }
            sum = (count == 0) ? Float.NaN : sum / count;
            reduced[i] = sum;
        }

        return reduced;
    }

    void fillNaN(float[] dataNaN, float[] dataReduced){
        for(int i=0;i<dataNaN.length;i++){
            if(Float.isNaN(dataNaN[i])){
                dataNaN[i]=dataReduced[i/2];
            }
        }
    }

    void fillNaN(ImageProcessor ipNaN, ImageProcessor ipReduced){
        for(int y=0;y<ipNaN.getHeight();y++){
            for(int x=0;x<ipNaN.getWidth();x++){
                if(Double.isNaN(ipNaN.getPixelValue(x,y))){
                    int reducedX=(xDirection)?x/2:x;
                    int reducedY=(yDirection)?y/2:y;
                    ipNaN.setf(x,y,(float)ipReduced.getInterpolatedPixel(reducedX,reducedY));
                }
            }
        }

    }

    void fillNaN(ImageStack isNaN, ImageStack isReduced){
        for (int z=0;z<isNaN.getSize();z++){
            ImageProcessor ipNaN=isNaN.getProcessor(z+1);
            int reducedZ=(zDirection)?z/2:z;
            ImageProcessor ipReduced=isReduced.getProcessor(Math.min(reducedZ+1,isReduced.getSize()));
            for(int y=0;y<ipNaN.getHeight();y++){
                for(int x=0;x<ipNaN.getWidth();x++){
                    if(Double.isNaN(ipNaN.getPixelValue(x,y))){
                        int reducedX=(xDirection)?x/2:x;
                        int reducedY=(yDirection)?y/2:y;
                        ipNaN.setf(x,y,(float)ipReduced.getInterpolatedPixel(reducedX,reducedY));
                    }
                }
            }
        }
    }

    void convertZerosToNaN(ImageStack is){
        for(int z=1;z<=is.getSize();z++){
            float[] pixels=(float[]) is.getPixels(z);
            for (int index = 0; index < pixels.length; index++) {
                if (pixels[index] == 0) pixels[index] = Float.NaN;
            }
        }
    }

    void filterGaussian1D(float[] data){
        float[] ori= new float[data.length];
        System.arraycopy(data,0,ori,0,data.length);
        float sum;
        //int count=0;
        for (int index=0;index<data.length;index++){
            sum=ori[index]*2;
            //count=2;
            sum+=ori[Math.abs(index-1)];
            //count++;
            int itmp=index+1;
            if(itmp>=ori.length) itmp=index-1;
            sum+=ori[itmp];
            //count++;

            //data[index]=sum/count;
            data[index]=sum/4;

        }
    }
}
