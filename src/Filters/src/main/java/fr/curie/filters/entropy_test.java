package fr.curie.filters;

import fr.curie.plotj.PlotWindow2;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.*;
import fr.curie.plotj.Trace;
import fr.curie.utils.StudentStatisitics;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by cedric on 01/12/2014.
 */
public class entropy_test  implements ExtendedPlugInFilter, DialogListener {
        //distribution table of student bilateral
        //for unilateral test use your alpha risk*2

        ImagePlus myimp;
        int radius = 2;
    boolean doVariance=true;
        boolean showimages = false;
    AtomicInteger count;
    double[] entropies;
    double[] min;
    double[] max;
    double[] avg;
    double[] stddev;

    double[] dog1;
    double[] dog2;
    double[] dog3;
    double[] ratio12;
    double[] ratio23;
        boolean showlocalisation = false;
        //ImageStack tophatstack;
        ImageStack hotspotstack;
        ImageStack msdstack;
        ImagePlus hsImp;
        ImagePlus msdImp;
        int index = 0;
        int flags = DOES_8G + DOES_16 + DOES_32 + PARALLELIZE_STACKS;
        PlugInFilterRunner pfr;
        boolean preview;


        /**
         * Description of the Method
         *
         * @param arg Description of the Parameter
         * @param imp Description of the Parameter
         * @return Description of the Return Value
         */
        public int setup(String arg, ImagePlus imp) {
            myimp = imp;
            count=new AtomicInteger(0);
            entropies=new double[imp.getImageStackSize()];
            min=new double[imp.getImageStackSize()];
            max=new double[imp.getImageStackSize()];
            avg=new double[imp.getImageStackSize()];
            stddev=new double[imp.getImageStackSize()];
            dog1=new double[imp.getImageStackSize()];
            dog2=new double[imp.getImageStackSize()];
            dog3=new double[imp.getImageStackSize()];
            ratio12=new double[imp.getImageStackSize()];
            ratio23=new double[imp.getImageStackSize()];
            return flags;
        }

        /**
         * Main processing method for the HotSpot_Detection object
         *
         * @param ip Description of the Parameter
         */
        public void run(ImageProcessor ip) {
            int ind = pfr.getSliceNumber();
            //compute variance
            ip=ip.convertToFloat();


            ip=doG(ip);
            ImageStatistics statDoG=new FloatStatistics(ip);
            System.out.println("#"+ind+" min="+statDoG.min+" max="+statDoG.max);
            double[] tmp=doG2(ip);
            dog1[ind-1]=tmp[0];
            dog2[ind-1]=tmp[1];
            dog3[ind-1]=tmp[2];
            ratio12[ind-1]=tmp[3];
            ratio23[ind-1]=tmp[4];


            FHT fft=  new FHT(pad(ip));
            fft.transform();
            ImageProcessor fftps=fft.getPowerSpectrum().convertToFloatProcessor();
            ImagePlus ps=new ImagePlus("power spectrum",fftps);
            ApplySymmetry_Filter sym=new ApplySymmetry_Filter();
            sym.setup("symmetry=360", ps);
            sym.run(fftps);
            //ps.show();
            float[] radiusfft=new float[fftps.getWidth()/2];
            fftps.getRow(fftps.getWidth()/2,fftps.getHeight()/2,radiusfft,fftps.getWidth()/2);
            double total=0;
            double mintmp=Double.MAX_VALUE;
            for(int i=0;i<radiusfft.length;i++){
                if(radiusfft[i]<mintmp) mintmp=radiusfft[i];
                total+=radiusfft[i];
            }
            System.out.println("min="+mintmp);
            /*for(int i=fftps.getWidth()/2;i<fftps.getWidth();i++){
                radiusfft[i-fftps.getWidth()/2]=fftps.getPixelValue(i,fftps.getHeight()/2);
            } */
            //compute entropy
            FloatProcessor fp=new FloatProcessor(radiusfft.length,1,radiusfft);
            fp.resetMinAndMax();
            ImagePlus imptmp=new ImagePlus("fft row",fp);
            //imptmp.show();
            ImageStatistics stats = imptmp.getStatistics(ImageStatistics.MEAN + ImageStatistics.MIN_MAX + ImageStatistics.STD_DEV + ImageStatistics.MEDIAN);
            //ImageStatistics stats=new FloatStatistics(fp,ImageStatistics.MEAN + ImageStatistics.MIN_MAX + ImageStatistics.STD_DEV + ImageStatistics.MEDIAN,null);
            double entropy=0;
            for(int i=0;i<256;i++){
                //double p=stats.histogram[i]/(double)stats.pixelCount ;
                double p=radiusfft[i]/total;
                //System.out.println("#"+i+" p:"+p+" pixelcount"+((double)stats.pixelCount));
                if(p>0) {
                    double pe = p * Math.log(p) / Math.log(2);
                    entropy += pe;
                    //System.out.println("#" + i + " ent:" + pe + " entropy" + entropy);
                }
            }
            entropies[ind-1]=-entropy;
            min[ind-1]=stats.min;
            max[ind-1]=stats.max;
            avg[ind-1]=stats.mean;
            stddev[ind-1]=stats.stdDev;



            min[ind-1]=statDoG.min;
            max[ind-1]=statDoG.max;
            avg[ind-1]=statDoG.max-statDoG.min;



            /*ImageProcessor variance = ip.duplicate();
            if(doVariance) {
                ImageProcessor blurimage = ip.duplicate();
                GaussianBlur blurfilter=new GaussianBlur();
                blurfilter.blurGaussian(blurimage,radius,radius,0.0002);
                for (int j = 0; j < ip.getHeight(); j++) {
                    for (int i = 0; i < ip.getWidth(); i++) {
                        //lsd.putPixelValue(i, j, squareDifference(tophat, i, j));
                        //variance.setf(i, j, (float) variance(ip, i, j, radius));
                        variance.setf(i, j, (float) variance(blurimage, i, j, radius));
                    }
                }
            }

            ImagePlus impVar = new ImagePlus("variance "+ind,variance);
            ImageStatistics stats=new FloatStatistics(variance,ImageStatistics.MEAN + ImageStatistics.MIN_MAX + ImageStatistics.STD_DEV + ImageStatistics.MEDIAN,null);
            //ImageStatistics stats = impVar.getStatistics(ImageStatistics.MEAN + ImageStatistics.MIN_MAX + ImageStatistics.STD_DEV + ImageStatistics.MEDIAN);
            min[ind-1]=stats.min;
            max[ind-1]=stats.max;
            avg[ind-1]=stats.mean;
            variance.resetMinAndMax();
            variance= variance.convertToByte(true);
            ImagePlus imp2 = new ImagePlus("variance for entropy"+ind,variance);
            if(showimages) {
                impVar.show();
                imp2.show();
            }
            //compute histogram
            stats = imp2.getStatistics(ImageStatistics.MEAN + ImageStatistics.MIN_MAX + ImageStatistics.STD_DEV + ImageStatistics.MEDIAN);


            byte[] pixs=(byte[])variance.getPixels();
            double entropy=0;
            for(int i=0;i<256;i++){
                double p=stats.histogram[i]/(double)stats.pixelCount ;
                //System.out.println("#"+i+" p:"+p+" pixelcount"+((double)stats.pixelCount));
                if(p>0) {
                    double pe = p * Math.log(p) / Math.log(2);
                    entropy += pe;
                    //System.out.println("#" + i + " ent:" + pe + " entropy" + entropy);
                }
            }
            entropies[ind-1]=-entropy;

            */
            if(count.incrementAndGet()==myimp.getImageStackSize()){
                double[] imgs=new double[myimp.getImageStackSize()];
                for(int i=0;i<myimp.getImageStackSize();i++) {
                    IJ.log("#" + i + " entropy: " + entropies[i]);
                    imgs[i]=i+1;
                }
                double[] entropiesSorted= entropies.clone();
                Arrays.sort(entropiesSorted);
                //double thresholdEntropies=computeKm(imgs, entropiesSorted );
                double thresholdEntropies=(entropiesSorted[0]+entropiesSorted[entropiesSorted.length-1])/2.0;
                IJ.log("threshold (entropies) : "+thresholdEntropies);
                double[] threshold=new double[entropies.length];
                Arrays.fill(threshold,thresholdEntropies);

                Trace hist=histogram(entropies,16);
                hist.setColor(Color.BLACK);
                PlotWindow2 histo=new PlotWindow2();
                histo.removeAllPlots();
                histo.addPlot(hist);
                histo.resetMinMax();
                histo.setVisible(true);

                PlotWindow2 pw = new PlotWindow2();
                pw.removeAllPlots();
                pw.addPlot(imgs, entropies, Color.BLACK, "entropies");
                pw.addPlot(imgs, entropiesSorted, Color.RED, "entropies sorted");
                pw.addPlot(imgs, threshold, Color.MAGENTA, "entropies threshold");
                pw.resetMinMax();
                pw.setVisible(true);

                ImageStack result=new ImageStack(myimp.getWidth(),myimp.getHeight());
                for(int i=0;i<myimp.getImageStackSize();i++) {
                    if(entropies[i]>thresholdEntropies){
                        result.addSlice(myimp.getImageStack().getSliceLabel(i+1),myimp.getImageStack().getProcessor(i+1));
                    }
                }
                new ImagePlus("in focus images(entropy)",result).show();

                double[] avgSorted= avg.clone();
                Arrays.sort(avgSorted);
                double[] maxSorted= max.clone();
                Arrays.sort(maxSorted);
                pw = new PlotWindow2();
                pw.removeAllPlots();
                pw.addPlot(imgs, min, Color.BLUE, "minimum value");
                pw.addPlot(imgs,max,Color.ORANGE,"maximum value");
                pw.addPlot(imgs,avg,Color.GREEN,"average value");
                pw.addPlot(imgs,avgSorted,Color.CYAN,"average value Sorted");
                //pw.addPlot(imgs,thresholdlineAvg,Color.CYAN,"threshold avg");
                pw.addPlot(imgs,maxSorted,Color.MAGENTA,"max value Sorted");
                pw.addPlot(imgs,stddev,Color.RED,"stdDev");
                pw.resetMinMax();
                pw.setVisible(true);


                double[] dog1sorted=blur(dog1,2);
                double[] dog2sorted=blur(dog2,2);
                double[] dog3sorted=blur(dog3,2);
//                double[] dog1sorted= dog1.clone();
//                Arrays.sort(dog1sorted);
//                double[] dog2sorted= dog2.clone();
//                Arrays.sort(dog2sorted);
//                double[] dog3sorted= dog3.clone();
//                Arrays.sort(dog3sorted);
                double[] ratio12sorted= ratio12.clone();
                Arrays.sort(ratio12sorted);
                double[] deriveeRatio12= ratio12sorted.clone();
                for(int i=deriveeRatio12.length-1;i>=0;i--){
                    deriveeRatio12[i]-=deriveeRatio12[Math.max(i-1,0)];
                }

                double[] ratio23sorted= ratio23.clone();
                Arrays.sort(ratio23sorted);
                double[] deriveeRatio23= ratio23sorted.clone();
                for(int i=deriveeRatio23.length-1;i>=0;i--){
                    deriveeRatio23[i]-=deriveeRatio23[Math.max(i-1,0)];
                }
                PlotWindow2 dogPlot=new PlotWindow2();
                dogPlot.removeAllPlots();
                dogPlot.addPlot(imgs, dog1, Color.BLUE, "dog1");
                dogPlot.addPlot(imgs, dog1sorted, Color.BLUE, "dog1 sorted");
                dogPlot.addPlot(imgs,dog2,Color.RED,"dog2");
                dogPlot.addPlot(imgs,dog2sorted,Color.RED,"dog2 sorted");
                dogPlot.addPlot(imgs,dog3,Color.GREEN,"dog3");
                dogPlot.addPlot(imgs,dog3sorted,Color.GREEN,"dog3 sorted");
                dogPlot.addPlot(imgs,ratio12,Color.MAGENTA,"ratio12");
                dogPlot.addPlot(imgs,ratio12sorted,Color.MAGENTA,"ratio12 sorted");
                dogPlot.addPlot(imgs,deriveeRatio12,Color.MAGENTA,"ratio12 derived");
                dogPlot.addPlot(imgs,ratio23,Color.ORANGE,"ratio23");
                dogPlot.addPlot(imgs,ratio23sorted,Color.ORANGE,"ratio23 sorted");
                dogPlot.addPlot(imgs,deriveeRatio23,Color.ORANGE,"ratio23 derived");
                dogPlot.resetMinMax();
                dogPlot.setVisible(true);
                double threshold1 = findThreshold(dog1,1);
                double threshold2 = findThreshold(dog2,1);
                double threshold3 = findThreshold(dog3,1);

                System.out.println("threshold1 : "+threshold1);
                System.out.println("threshold2 : "+threshold2);
                System.out.println("threshold3 : "+threshold3);
                ImageStack resultDOG=new ImageStack(ip.getWidth(),ip.getHeight());
                        System.out.println("so keep:");
                        for(int i=0;i<dog1.length;i++){
                            if(dog1[i]>threshold1&&dog2[i]>threshold2&&dog3[i]>threshold3){
                                resultDOG.addSlice(myimp.getImageStack().getSliceLabel(i+1),myimp.getImageStack().getProcessor(i+1));
                                System.out.println((i+1));
                            }
                        }
                new ImagePlus("selected images via dog", resultDOG).show();


            }



        }

    public Trace histogram(double[] data,int sizeHist){
        double[] dataSorted=data.clone();
        Arrays.sort(dataSorted);
        double [] hist=new double[sizeHist];
        double [] x=new double[sizeHist];
        double min=dataSorted[0];
        double max=dataSorted[dataSorted.length-1]*1.01;
        double scale=sizeHist/(max-min);
        for(int index=0;index<dataSorted.length;index++){
            double val=dataSorted[index];
            int indexHist=(int) (scale*(val-min));
            hist[indexHist]++;
        }
        for(int i=0;i<x.length;i++){
            x[i]=i/scale+min;

        }
        Trace t=new Trace(x,hist);
        return t;

    }

        /**
         * Description of the Method
         *
         * @param imp     Description of the Parameter
         * @param command Description of the Parameter
         * @param pfr     Description of the Parameter
         * @return Description of the Return Value
         */
        public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
            this.pfr = pfr;
            preview = true;
            GenericDialog gd = new GenericDialog("entropy calculation");
            gd.addCheckbox("use variance",true);
            gd.addNumericField("radius of neightborhood for variance", radius, 0);
            gd.addPreviewCheckbox(pfr);
            gd.addDialogListener(this);
            gd.showDialog();
            if (gd.wasCanceled()) {
                return DONE;
            }
            preview = false;
            doVariance=gd.getNextBoolean();
            radius = (int) gd.getNextNumber();
            showimages=false;
            return IJ.setupDialog(imp, flags);
        }

        /**
         * Sets the nPasses attribute of the HotSpot_Detection object
         *
         * @param nPasses The new nPasses value
         */
        public void setNPasses(int nPasses) {
        }

        /**
         * Listener to modifications of the input fields of the dialog
         *
         * @param gd Description of the Parameter
         * @param e  Description of the Parameter
         * @return Description of the Return Value
         */
        public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
            radius = (int) gd.getNextNumber();
            doVariance = gd.getNextBoolean();
            showimages=true;
            return true;
        }

    public double variance(ImageProcessor ip, int x, int y, int radius){
        int startx=Math.max(0,x-radius);
        int starty=Math.max(0, y - radius);
        int endx=Math.min(ip.getWidth()-1,x+radius);
        int endy=Math.min(ip.getHeight() - 1, y + radius);
        double sum=0;
        double sum2=0;
        float pix;
        int N=0;
        for(int j=starty;j<endy;j++){
            for(int i=startx;i<endx;i++){
                pix=ip.getf(i,j);
                sum+=pix;
                sum2+=pix*pix;
                N++;
            }
        }
        sum /=N;
        return sum2/N-sum*sum;
    }

    private double computeKm(double[]x, double[] y){
        double[] iy=new double[y.length];
        double[] ix=new double[x.length];
        for (int i=0;i<y.length;i++) {
            //iy[i]=Math.log(y[i]);
            //ix[i]=x[i];
            iy[i]=1.0/y[i];
            ix[i]=1.0/x[i];
        }
        double[] coeff=linearFit(ix,iy);
        System.out.println("y="+coeff[0]+" + "+coeff[1]+"X with r="+coeff[2]);
        double[] fity=new double[y.length];
        for (int i=0;i<y.length;i++) {
            fity[i]=coeff[0]+coeff[1]*ix[i];
        }

        PlotWindow2 pw = new PlotWindow2();
        pw.removeAllPlots();
        pw.addPlot(ix, iy, Color.BLACK, "Km");
        pw.addPlot(ix, fity, Color.RED, "fit");
        pw.resetMinMax();
        pw.setVisible(true);
        return coeff[0]/coeff[1];
        //return coeff[0];
    }

    private double[] linearFit(double[] x, double[] y) {
        double xm, ym, sxy, sxx, syy;

        // Calculate parameters by least squares
        xm = 0.0;
        ym = 0.0;
        for (int i = 0; i < x.length; i++) {
            xm += x[i];
            ym += y[i];
        }
        xm /= x.length;
        ym /= x.length;

        sxy = 0.0;
        sxx = 0.0;
        syy = 0.0;
        for (int i = 0; i < x.length; i++) {
            sxy += (x[i] - xm) * (y[i] - ym);
            sxx += (x[i] - xm) * (x[i] - xm);
            syy += (y[i] - ym) * (y[i] - ym);
        }
        double[] coef=new double[3];
        coef[1] = sxy / sxx;
        coef[0] = ym - coef[1] * xm;
        coef[2] = (sxy / Math.sqrt(sxx * syy)) * (sxy / Math.sqrt(sxx * syy));
        return coef;
    }

    ImageProcessor pad(ImageProcessor ip) {
            int originalWidth = ip.getWidth();
            int originalHeight = ip.getHeight();
            int maxN = Math.max(originalWidth, originalHeight);
            int i = 2;
            while(i<maxN) i *= 2;
            if (i==maxN && originalWidth==originalHeight) {
                return ip;
            }
            maxN = i;
            ImageStatistics stats = ImageStatistics.getStatistics(ip, ImageStatistics.MEAN, null);
            ImageProcessor ip2 = ip.createProcessor(maxN, maxN);
            ip2.setValue(stats.mean);
            ip2.fill();
            ip2.insert(ip, 0, 0);
            //new ImagePlus("padded", ip2.duplicate()).show();
            return ip2;
        }

    ImageProcessor doG(ImageProcessor ip){
        ImageProcessor ip2=ip.duplicate();
        ImageProcessor ip4=ip.duplicate();
        GaussianBlur gb=new GaussianBlur();
        gb.blurGaussian(ip2,2,2,0.0001);
        gb.blurGaussian(ip4,4,4,0.0001);

        ip2.copyBits(ip4,0,0,Blitter.SUBTRACT);
        return ip2;
    }

    double[] doG2(ImageProcessor ip){
        ImageProcessor ip0=ip.duplicate();
        ImageProcessor ip2=ip.duplicate();
        ImageProcessor ip4=ip.duplicate();
        ImageProcessor ip6=ip.duplicate();
        ImageProcessor ip8=ip.duplicate();
        GaussianBlur gb=new GaussianBlur();
        gb.blurGaussian(ip2,2,2,0.0001);
        gb.blurGaussian(ip4,4,4,0.0001);
        gb.blurGaussian(ip6,6,6,0.0001);
        gb.blurGaussian(ip8,8,8,0.0001);

        ip0.copyBits(ip2,0,0,Blitter.SUBTRACT);
        ip2.copyBits(ip4,0,0,Blitter.SUBTRACT);
        ip4.copyBits(ip6,0,0,Blitter.SUBTRACT);
        ip6.copyBits(ip8,0,0,Blitter.SUBTRACT);

        ImageStatistics statDoG02=new FloatStatistics(ip0);
        ImageStatistics statDoG24=new FloatStatistics(ip2);
        ImageStatistics statDoG46=new FloatStatistics(ip4);
        ImageStatistics statDoG68=new FloatStatistics(ip6);

        double[] result=new double[5];
        result[0]=statDoG24.max-statDoG24.min;
        result[1]=statDoG46.max-statDoG46.min;
        result[2]=statDoG68.max-statDoG68.min;
        result[3]=result[0]/result[1];
        result[4]=result[1]/result[2];

        return result;
    }

    double[] blur(double[] array, int radius){
        double[] result=new double[array.length];
        for(int i=0;i<array.length;i++){
            for(int v=-radius;v<=radius;v++){
                result[i]+=array[Math.max(0,Math.min(array.length-1,i+v))];
            }
            result[i]/=2*radius+1;
        }
        return result;
    }

    double findThreshold(double[] array,int radius){
        ArrayList<Double> localMax=new ArrayList<Double>();
        ArrayList<Double> localMin=new ArrayList<Double>();
        for(int i=0;i<array.length;i++){
            boolean max=true;
            boolean min=true;
            for(int v=-radius;v<=radius;v++){
                if(array[Math.max(0,Math.min(array.length-1,i+v))]>array[i]) max=false;
                if(array[Math.max(0,Math.min(array.length-1,i+v))]<array[i]) min=false;
            }
            if(max) localMax.add(array[i]);
            if(min) localMin.add(array[i]);
        }

        double[] max=StudentStatisitics.getStatistics(localMax,0.05);
        double[] min=StudentStatisitics.getStatistics(localMin,0.05);

       // double max=localMax.get(0);
        //double max=0;
        //for(Double d:localMax){
            //max= Math.min(max,d);
        //    max+=d;
        //}
       // max/=localMax.size();

        //double min=localMin.get(0);
        //double min=0;
       // for(Double d:localMin){
            //min= Math.max(min,d);
        //    min+=d;
        //}
        //min/=localMin.size();

        //double threshold=min+(max-min)/2;
        double threshold=min[4]+(max[3]-min[4])/2;
        //System.out.println("min of local maxima: "+max);
        //System.out.println("max of local minima: "+min);
//        System.out.println("threshold : "+threshold);
//        System.out.println("so keep:");
//        for(int i=0;i<array.length;i++){
//            if(array[i]>threshold) System.out.println((i+1));
//        }

        return threshold;

    }
}
