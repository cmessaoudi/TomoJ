package fr.curie.deconvolution;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import cern.colt.function.tfcomplex.FComplexFComplexFunction;
import cern.colt.matrix.tdcomplex.impl.DenseDComplexMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tfcomplex.FComplexMatrix2D;
import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.jet.math.tfcomplex.FComplexFunctions;
import cern.jet.math.tfloat.FloatFunctions;
import fr.curie.plotj.PlotWindow2;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class EstimatePSFFromOneImage implements ExtendedPlugInFilter {
    public static final int DIAGONAL=0;
    public static final int X_AXIS=1;
    public static final int Y_AXIS=2;
    public static final int BOTH_AXIS=3;
    public static final int ALL=4;


    int flags = DOES_8G + DOES_16 + DOES_32 + PARALLELIZE_STACKS;
    PlugInFilterRunner pfr;
    ImagePlus myimp;
    Integer nbprocessed = 0;
    double[] frcx;
    double[] frcy;
    double[] frciso;
    double[] psf_sigmax;
    double[] psf_sigmay;
    double[] psf_sigmaiso;
    boolean display;
    double translateOffset = -0.5;
    double edgefrac=0.005;
    int frc_lowPassFilter=16;
    boolean correctTranslation=true;
    ImageStack psfs;
    int psfSize=128;
    double frc_threshold=0.5;


    @Override
    public int setup(String arg, ImagePlus imp) {
        myimp = imp;
        frcx = new double[imp.getImageStackSize()];
        frcy = new double[imp.getImageStackSize()];
        frciso = new double[imp.getImageStackSize()];
        psf_sigmax = new double[imp.getImageStackSize()];
        psf_sigmay = new double[imp.getImageStackSize()];
        psf_sigmaiso = new double[imp.getImageStackSize()];
        psfs=new ImageStack(psfSize,psfSize);
        for(int i=0;i<imp.getImageStackSize();i++) psfs.addSlice(new FloatProcessor(psfSize,psfSize));
        return flags;
    }

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        this.pfr = pfr;

        GenericDialog gd = new GenericDialog("Single Image FRC");
        gd.addCheckbox("correct translation", correctTranslation);
        gd.addNumericField("frc threshold",frc_threshold,3);
        gd.addNumericField("psf size",psfSize,0);
        gd.addCheckbox("display intermediate images", false);
        gd.showDialog();

        if (gd.wasOKed()) {
            correctTranslation = gd.getNextBoolean();
            frc_threshold=gd.getNextNumber();
            psfSize=(int)gd.getNextNumber();
            display = gd.getNextBoolean();
        }
        return flags=IJ.setupDialog(imp, flags);
    }

    @Override
    public void setNPasses(int nPasses) {

    }

    @Override
    public void run(ImageProcessor ip) {
        int ind = pfr.getSliceNumber() - 1;
        //create sub images
        ImageProcessor[] subImgs=createSubImages(ip,getTaperMask(ip,edgefrac));
        if(display){
            new ImagePlus(ind+"_xy",subImgs[0]).show();
            new ImagePlus(ind+"_x1y",subImgs[1]).show();
            new ImagePlus(ind+"_xy1",subImgs[2]).show();
            new ImagePlus(ind+"_x1y1",subImgs[3]).show();
        }
        ArrayList<double[]> frcs=computeFRC(subImgs,ALL,true,frc_lowPassFilter);
        if((flags&ExtendedPlugInFilter.DOES_STACKS)==0) displayResults(frcs);

        ArrayList<Double> resolutions =new ArrayList<>(4);
        double[] frcxy=frcs.get(1);
        resolutions.add(frcxy==null?Double.NaN:FRCresolution(frcxy,frc_threshold)*0.5);
        double[] frcx=frcs.get(2);
        resolutions.add(frcx==null?Double.NaN:FRCresolution(frcx,frc_threshold)*0.5);
        double[] frcy=frcs.get(3);
        resolutions.add(frcy==null?Double.NaN:FRCresolution(frcy,frc_threshold)*0.5);

        IJ.log(ind+" resolution XY="+resolutions.get(0));
        IJ.log(ind+" resolution X="+resolutions.get(1));
        IJ.log(ind+" resolution Y="+resolutions.get(2));
        this.frcx[ind]=resolutions.get(1);
        this.frcy[ind]=resolutions.get(2);
        this.frciso[ind]=resolutions.get(0);

        this.psf_sigmax[ind]=CalculatePSFsigma(resolutions.get(1));
        this.psf_sigmay[ind]=CalculatePSFsigma(resolutions.get(2));
        this.psf_sigmaiso[ind]=CalculatePSFsigma(resolutions.get(0));

        IJ.log(ind+" psf sigma XY="+this.psf_sigmaiso[ind]);
        IJ.log(ind+" psf sigma X="+this.psf_sigmax[ind]);
        IJ.log(ind+" psf sigma Y="+this.psf_sigmay[ind]);

        double[] sigmas=new double[]{this.psf_sigmax[ind],this.psf_sigmay[ind]} ;

        ImageProcessor psf= createPSF(psfSize,sigmas);
        if((flags&ExtendedPlugInFilter.DOES_STACKS)==0) {
            new ImagePlus("psf2D", psf).show();
        }
        synchronized (psfs){
            if (psfs==null){
                psfs=new ImageStack(psfSize,psfSize);
                for (int i=0;i<myimp.getImageStackSize();i++) psfs.addSlice(psf.createProcessor(psfSize,psfSize));
            }
            psfs.setProcessor(psf,ind+1);

        }
        synchronized (nbprocessed) {
            nbprocessed++;
            if (nbprocessed == myimp.getImageStackSize()) {
                displayResults();
                new ImagePlus("psfs2D", psfs).show();
            }
        }

    }

    public void displayResults() {
        ResultsTable rt = new ResultsTable();
        for (int i = 0; i < frcx.length; i++) {
            System.out.println("" + i + "\t" + frcx[i] + "\t" + frcy[i] + "\t" + frciso[i]);
            rt.incrementCounter();
            rt.addValue("index", i);
            rt.addValue("FRCx", frcx[i]);
            rt.addValue("FRCy", frcy[i]);
            rt.addValue("FRCiso", frciso[i]);
            rt.addValue("PSF_sigma_x", psf_sigmax[i]);
            rt.addValue("PSF_sigma_y", psf_sigmay[i]);
            rt.addValue("PSF_sigma_iso", psf_sigmaiso[i]);
        }
        rt.setPrecision(5);
        rt.showRowNumbers(true);
        rt.show("FRC" + myimp.getTitle());
        if (frcx.length > 1) {
            double[] x = new double[frcx.length];
            for (int i = 0; i < x.length; i++) x[i] = i + 1;
            Plot p=new Plot("FRCs","image", "resolution limit");
            p.setColor(Color.RED);
            p.add("line",x,frcx);
            p.setColor(Color.GREEN);
            p.add("line",x,frcy);
            p.setColor(Color.BLUE);
            p.add("line",x,frciso);
            p.addLegend("FRCx\nFRCy\nFRCiso");
            p.setLimits(0,myimp.getImageStackSize(),0,0.5);
            p.updateImage();
            p.show();
            Plot p2=new Plot("PSFs","image", "sigma");
            p2.setColor(Color.RED);
            p2.add("line",x,psf_sigmax);
            p2.setColor(Color.GREEN);
            p2.add("line",x,psf_sigmay);
            p2.setColor(Color.BLUE);
            p2.add("line",x,psf_sigmaiso);
            p2.addLegend("PSF sigma_x\nPSF sigma_y\nPSF sigma_iso");
            p2.setLimitsToFit(true);
            p2.show();
            /*PlotWindow2 pw = new PlotWindow2();
            pw.removeAllPlots();
            pw.addPlot(x, frcx, Color.RED, "FRCx");
            pw.addPlot(x, frcy, Color.BLUE, "FRCy");
            pw.addPlot(x, frciso, Color.GREEN, "FRCiso");
            pw.addPlot(x, psf_sigmax, Color.RED, "psf_sigma_x");
            pw.addPlot(x, psf_sigmay, Color.BLUE, "psf_sigma_y");
            pw.addPlot(x, psf_sigmaiso, Color.GREEN, "psf_sigma_iso");
            pw.resetMinMax();
            pw.setVisible(true);  */
        }
    }

    double FRCresolution(double[] frc,  double threshold)
    {
        int i;
        double cutoff_freq;

        for(i=1; i<frc.length; i++)
            if(frc[i]<frc[i-1] && frc[i] < threshold) break;

        cutoff_freq = ((double)i)/((frc.length-1)*2);
        System.out.println("FRCresollution : index found="+i+" resolution="+cutoff_freq);

        return(cutoff_freq);
    }

    double CalculatePSFsigma(double FRCresol)
    {
        double FWHM, sigma;

        FWHM = 1./FRCresol;
        sigma = FWHM / (2 * Math.sqrt(2.0 * Math.log(2.0)));

        return sigma;

    }

    public void displayResults(ArrayList<double[]> frcs){
        boolean frcx=frcs.get(2)!=null;
        boolean frcy=frcs.get(3)!=null;
        boolean frcxy=frcs.get(1)!=null;
        int length=0;
        if(frcx) length=frcs.get(2).length;
        else if(frcy) length=frcs.get(3).length;
        else if(frcxy) length=frcs.get(1).length;

        ResultsTable rt = new ResultsTable();
        for (int i = 0; i < length; i++) {
            rt.incrementCounter();
            rt.addValue("index", i);
            rt.addValue("frequencies", frcs.get(0)[i]);
            if(frcx)rt.addValue("FRCx", frcs.get(2)[i]);
            if(frcy)rt.addValue("FRCy", frcs.get(3)[i]);
            if(frcxy)rt.addValue("FRCiso", frcs.get(1)[i]);
        }
        rt.setPrecision(5);
        rt.showRowNumbers(true);
        rt.show("FRCs" + myimp.getTitle());
        if (length > 1) {
            double[] x = frcs.get(0);
            double[] cte= new double[x.length];
            Arrays.fill(cte,frc_threshold);
            //for (int i = 0; i < x.length; i++) x[i] = i + 1;
            Plot p=new Plot("FRCs","image", "resolution limit");
            p.setColor(Color.RED);
            p.add("line",x,frcs.get(2));
            p.setColor(Color.GREEN);
            p.add("line",x,frcs.get(3));
            p.setColor(Color.BLUE);
            p.add("line",x,frcs.get(1));
            p.add("line",x,cte);
            p.addLegend("FRCx\nFRCy\nFRCiso\nthreshold");
            p.show();
            /*PlotWindow2 pw = new PlotWindow2();
            pw.removeAllPlots();
            if(frcx) pw.addPlot(x, frcs.get(2), Color.RED, "FRCx");
            if(frcy) pw.addPlot(x, frcs.get(3), Color.BLUE, "FRCy");
            if(frcxy) pw.addPlot(x, frcs.get(1), Color.GREEN, "FRCiso");
            pw.addPlot(x, cte, Color.BLACK, "threshold "+frc_threshold);
            pw.resetMinMax();
            pw.setVisible(true);  */
        }
    }

    public ImageProcessor getTaperMask(ImageProcessor ip, double edgefrac) {
        FloatProcessor fp = new FloatProcessor(ip.getWidth(), ip.getHeight());
        int NedgeX = (int) Math.floor(0.5 + ip.getWidth() * edgefrac);
        int NedgeY = (int) Math.floor(0.5 + fp.getHeight() * edgefrac);
        double[] weightsX = new double[ip.getWidth()];
        double[] weightsY = new double[fp.getHeight()];
        double RCfactorX1 = Math.PI / (NedgeX);
        double RCfactorX2 = Math.PI / ((ip.getWidth() - 1) - (ip.getWidth() - 1 - NedgeX));
        double RCfactorY1 = Math.PI / (NedgeY);
        double RCfactorY2 = Math.PI / ((fp.getHeight() - 1) - (fp.getHeight() - 1 - NedgeY));

        for (int y = NedgeY; y <= (fp.getHeight() - 1 - NedgeY); y++) weightsY[y] = 1.0;
        for (int y = 0; y < NedgeY; y++)
            weightsY[y] = 0.5 * (1.0 - Math.cos(RCfactorY1 * y));
        for (int y = fp.getHeight() - 1 - NedgeY + 1; y < fp.getHeight(); y++)
            weightsY[y] = 0.5 * (1.0 - Math.cos(RCfactorY2 * (fp.getHeight() - 1 - y)));

        for (int x = NedgeX; x <= (ip.getWidth() - 1 - NedgeX); x++) weightsX[x] = 1.0;
        for (int x = 0; x < NedgeX; x++)
            weightsX[x] = 0.5 * (1.0 - Math.cos(RCfactorX1 * x));
        for (int x = ip.getWidth() - 1 - NedgeX + 1; x < ip.getWidth(); x++)
            weightsX[x] = 0.5 * (1.0 - Math.cos(RCfactorX2 * (ip.getWidth() - 1 - x)));

        for (int y = 0; y < fp.getHeight(); y++) {
            for (int x = 0; x < ip.getWidth(); x++) {
                fp.setf(x, y, (float) (weightsX[x] * weightsY[y]));
            }
        }
        return fp;
    }
    public ImageProcessor[] createSubImages(ImageProcessor ip, ImageProcessor taperMask){
        FloatProcessor img1 = new FloatProcessor(ip.getWidth() / 2, ip.getHeight() / 2);
        FloatProcessor img2 = new FloatProcessor(ip.getWidth() / 2, ip.getHeight() / 2);
        FloatProcessor img3 = new FloatProcessor(ip.getWidth() / 2, ip.getHeight() / 2);
        FloatProcessor img4 = new FloatProcessor(ip.getWidth() / 2, ip.getHeight() / 2);

        double edgeAvg=0;
        double edgeWeight=0;

        for(int y=0;y<ip.getHeight();y++){
            for(int x=0;x<ip.getWidth();x++){
                edgeAvg+= (1.0-taperMask.getf(x,y))*ip.getf(x,y);
                edgeWeight+= 1.0-taperMask.getf(x,y);
            }
        }
        if(edgeWeight!=0) edgeAvg/= edgeWeight;
        else edgeAvg=0;

        for (int y = 0; y < img1.getHeight(); y++) {
            for (int x = 0; x < img1.getWidth(); x++) {
                img1.setf(x, y, (float)((ip.getPixelValue(x * 2, y * 2)- edgeAvg)*taperMask.getf(x,y) + edgeAvg));
                img2.setf(x, y, (float)((ip.getPixelValue(x * 2+1, y * 2)- edgeAvg)*taperMask.getf(x,y) + edgeAvg));
                img3.setf(x, y, (float)((ip.getPixelValue(x * 2, y * 2+1)- edgeAvg)*taperMask.getf(x,y) + edgeAvg));
                img4.setf(x, y, (float)((ip.getPixelValue(x * 2+1, y * 2+1)- edgeAvg)*taperMask.getf(x,y) + edgeAvg));
            }
        }

        return new ImageProcessor[]{img1,img2,img3,img4};

    }

    /**
     *
     * @param subImages
     * @param orientation
     * @param shiftImages
     * @return   0 list of frequencies, 1 frc Diagonal, 2 frc X-AXIS, 3 frc Y AXIS...
     */
    public static ArrayList<double[]> computeFRC(ImageProcessor[] subImages, int orientation, boolean shiftImages, int frc_lowPassFilter){
        DenseFComplexMatrix2D[] ffts= new DenseFComplexMatrix2D[subImages.length];
        for(int i=0;i<ffts.length;i++){
            ImageProcessor img=subImages[i];
            DenseFloatMatrix2D H = new DenseFloatMatrix2D(img.getHeight(), img.getWidth());
            H.assign((float[]) img.convertToFloatProcessor().getPixels());
            ffts[i] = H.getFft2();
        }
        if(shiftImages){
            shiftImageFFT(ffts[1],0.5,0);
            shiftImageFFT(ffts[2],0,0.5);
            shiftImageFFT(ffts[3],0.5,0.5);
        }
        double[][] tmp1;
        double[][] tmp2;

        ArrayList<double[]> result=new ArrayList<>(2);
        switch(orientation){
            case DIAGONAL:
                tmp1=computeFRC(ffts[0],ffts[3]);
                tmp2=computeFRC(ffts[1],ffts[2]);
                result.add(tmp1[0]);
                result.add(computeGlobalFRC(tmp1[1],tmp2[1],frc_lowPassFilter));
                result.add(null);
                result.add(null);
                break;
            case X_AXIS:
                tmp1=computeFRC(ffts[0],ffts[1]);
                tmp2=computeFRC(ffts[2],ffts[3]);
                result.add(tmp1[0]);
                result.add(null);
                result.add(computeGlobalFRC(tmp1[1],tmp2[1],frc_lowPassFilter));
                result.add(null);
                break;
            case Y_AXIS:
                tmp1=computeFRC(ffts[0],ffts[2]);
                tmp2=computeFRC(ffts[1],ffts[3]);
                result.add(tmp1[0]);
                result.add(null);
                result.add(null);
                result.add(computeGlobalFRC(tmp1[1],tmp2[1],frc_lowPassFilter));
                break;
            case BOTH_AXIS:
                //X axis
                tmp1=computeFRC(ffts[0],ffts[1]);
                tmp2=computeFRC(ffts[2],ffts[3]);
                result.add(tmp1[0]);
                result.add(null);
                result.add(computeGlobalFRC(tmp1[1],tmp2[1],frc_lowPassFilter));
                //YAXIS
                tmp1=computeFRC(ffts[0],ffts[2]);
                tmp2=computeFRC(ffts[1],ffts[3]);
                result.add(computeGlobalFRC(tmp1[1],tmp2[1],frc_lowPassFilter));
                break;
            case ALL:
            default:
                //DIAGONAL
                tmp1=computeFRC(ffts[0],ffts[3]);
                tmp2=computeFRC(ffts[1],ffts[2]);
                result.add(tmp1[0]);
                result.add(computeGlobalFRC(tmp1[1],tmp2[1],frc_lowPassFilter));
                //XAXIS
                tmp1=computeFRC(ffts[0],ffts[1]);
                tmp2=computeFRC(ffts[2],ffts[3]);
                result.add(computeGlobalFRC(tmp1[1],tmp2[1],frc_lowPassFilter));
                //YAXIS
                tmp1=computeFRC(ffts[0],ffts[2]);
                tmp2=computeFRC(ffts[1],ffts[3]);
                result.add(computeGlobalFRC(tmp1[1],tmp2[1],frc_lowPassFilter));
        }
        return result;
    }

    public static double[] computeGlobalFRC(double[] frc1, double[] frc2, int frc_lowPassFilter){
        double[] frcGlobal1=new double[frc1.length];
        for(int i=0;i<frcGlobal1.length;i++){
            frcGlobal1[i]= (frc1[i]+frc2[i])/2;
        }

        if(frc_lowPassFilter<=0){
            double[] tmpcopy= Arrays.copyOf(frcGlobal1,frcGlobal1.length);
            for(int i=0;i<frcGlobal1.length;i++){
                frcGlobal1[i]+=tmpcopy[Math.max(0,Math.min(i-1,frcGlobal1.length-1))];
                frcGlobal1[i]+=tmpcopy[Math.max(0,Math.min(i+1,frcGlobal1.length-1))];
                frcGlobal1[i]/=3;
            }
        }else {
            DenseDoubleMatrix1D data=new DenseDoubleMatrix1D(frcGlobal1);
            DenseDComplexMatrix1D fft1D=data.getFft();
            for(int i=frc_lowPassFilter;i<fft1D.size();i++) fft1D.setQuick(i,0,0);
            fft1D.ifft(true);
            for(int i=0;i<frcGlobal1.length;i++) frcGlobal1[i]=fft1D.getQuick(i)[0];
        }
        return frcGlobal1;
    }

    public static double[][] frc(ImageProcessor img1, ImageProcessor img2) {
        DenseFloatMatrix2D mask = new DenseFloatMatrix2D(img1.getHeight(), img1.getWidth());
        for (int y = 0; y < img1.getHeight(); y++) {
            double dy = y - (img1.getHeight() / 2.0);
            dy *= dy;
            for (int x = 0; x < img1.getWidth(); x++) {
                float value = 0;
                double dist = x - (img1.getWidth() / 2.0);
                dist *= dist;
                dist += dy;
                dist = Math.sqrt(dist);
                if (dist < img1.getWidth()) value = 1;
                else if (dist < img1.getWidth() + 20)
                    value = (float) (1 + Math.cos((dist - img1.getWidth()) * Math.PI / 20)) * .5f;

                mask.setQuick(y, x, value);
            }
        }
        DenseFloatMatrix2D H = new DenseFloatMatrix2D(img1.getHeight(), img1.getWidth());
        H.assign((float[]) img1.convertToFloatProcessor().getPixels());
        H.assign(mask, FloatFunctions.mult);
        DenseFComplexMatrix2D ffteven = H.getFft2();
        H.assign((float[]) img2.convertToFloatProcessor().getPixels());
        H.assign(mask, FloatFunctions.mult);
        DenseFComplexMatrix2D fftodd = H.getFft2();



        return computeFRC(ffteven,fftodd);

    }

    public static double[][] computeFRC(FComplexMatrix2D ffteven, FComplexMatrix2D fftodd){
        //System.out.println("convolution");
        //convolution
        FComplexMatrix2D m=ffteven.copy().assign(fftodd, FComplexFunctions.multConjSecond);
        //power spectrum
        //System.out.println("power spectrum");
        //FloatMatrix3D pseven=ffteven.assign(powerSpectrumFunctionF).getRealPart();
        //FloatMatrix3D psodd=fftodd.assign(powerSpectrumFunctionF).getRealPart();
        //compute frc
        int radiusSize=ffteven.columns()/2+1;
        double[][] frc = new double[4][radiusSize];
        double[] num = new double[radiusSize];
        double[] den1 = new double[radiusSize];
        double[] den2 = new double[radiusSize];
        double[] error_l2 = new double[radiusSize];
        double[] radialCount = new double[radiusSize];


        for (int y = 0, yy = 0; y < ffteven.rows(); y++) {
            double tmpy = convertToFrequency(y, ffteven.rows());
            tmpy *= tmpy;
            for (int x = 0; x < ffteven.columns(); x++) {
                double tmpx = convertToFrequency(x, ffteven.columns());
                tmpx *= tmpx;
                double w = Math.sqrt(tmpx + tmpy );
                int wIndex = (int)Math.round(w * ffteven.columns());
                if(wIndex>=radiusSize) continue;
                float[] valueEven=ffteven.get(y,x);
                float[] valueOdd=fftodd.get(y,x);

                double absEven=Math.sqrt(valueEven[0]*valueEven[0]+valueEven[1]*valueEven[1]);
                double absOdd=Math.sqrt(valueOdd[0]*valueOdd[0]+valueOdd[1]*valueOdd[1]);
                //real(conj(even)*odd
                num[wIndex]+=m.getQuick(y,x)[0];
                den1[wIndex]+=absEven*absEven;
                den2[wIndex]+=absOdd*absOdd;
                double[] tmp={valueEven[0]-valueOdd[0],valueEven[1]-valueOdd[1]};
                error_l2[wIndex]+=Math.sqrt(tmp[0]*tmp[0]+tmp[1]*tmp[1]);
                radialCount[wIndex]++;
            }
            yy += ffteven.columns();

        }

        for(int i=0;i<num.length;i++){
            frc[0][i]=((double)i)/(ffteven.columns());
            frc[1][i]=num[i]/Math.sqrt(den1[i]*den2[i]);
            frc[2][i]=2/Math.sqrt(radialCount[i]);
            frc[3][i]=error_l2[i]/radialCount[i];
        }
        System.out.println("fsc finished ");
        return frc;

    }

    public static double convertToFrequency(double x, int sizeMax){
        return ((x<=(sizeMax>>1))?x:x-sizeMax)/sizeMax;
    }

    public static void shiftImageFFT(DenseFComplexMatrix2D fft, double xshift, double yshift){
        xshift *= 2.0 * Math.PI / fft.columns();
        yshift *= 2.0 * Math.PI / fft.rows();


        int yy,xx;
        double mod,phase;
        for (int y=0; y<fft.rows(); y++ ) {
            yy = y;
            if ( y >fft.rows()/2 ) yy -= fft.rows();
            for (int x=0; x<=fft.columns()/2; x++ ) {
                xx = x;
                float[] val=fft.getQuick(y,x);
                mod = Math.sqrt(val[0] * val[0] +
                        val[1] * val[1]);
                phase = Math.atan2(val[1],val[0]);

                phase -= xx * xshift + yy * yshift;

                val[0] = (float)(mod * Math.cos(phase));
                val[1] = (float)(mod * Math.sin(phase));
                fft.setQuick(y,x,val);
            }
        }
    }

    public static ImageProcessor createPSF(int size, double[] sigma){
        FloatProcessor psf=new FloatProcessor(size,size);
        double sigmax = sigma[0], sigmay = sigma[1];
        double is2x, is2y;
        double scale;
        int i, x, y;
        double[] gaussX = new double[psf.getWidth()];
        double[] gaussY = new double[psf.getHeight()];

        is2x = -0.5 / (sigmax*sigmax);
        is2y = -0.5 / (sigmay*sigmay);

        scale = 1.0;

        for(i=0; i<gaussX.length; i++) {
            double xc = i-gaussX.length/2;
            gaussX[i] = Math.exp(xc*xc*is2x) * scale;
        }
        for(i=0; i<gaussY.length; i++) {
            double yc = i-gaussY.length/2;
            gaussY[i] = Math.exp(yc*yc*is2y);
        }

        for(y=0; y<psf.getHeight(); y++) {
            for (x = 0; x < psf.getWidth(); x++) {
                psf.setf(x, y, (float) (gaussX[x] * gaussY[y]));
            }
        }
        //new ImagePlus("psf",psf).show();
        return psf;
    }
}
