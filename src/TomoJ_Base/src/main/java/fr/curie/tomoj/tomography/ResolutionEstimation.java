package fr.curie.tomoj.tomography;

import cern.colt.function.tdcomplex.DComplexRealFunction;
import cern.colt.function.tfcomplex.FComplexRealFunction;
import cern.colt.matrix.tdcomplex.impl.DenseDComplexMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.tfcomplex.FComplexMatrix2D;
import cern.colt.matrix.tfcomplex.FComplexMatrix3D;
import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix3D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix3D;
import cern.jet.math.tfcomplex.FComplexFunctions;
import cern.jet.math.tfloat.FloatFunctions;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.ArrayList;

/**
 * Created by cedric on 16/12/2015.
 */
public class ResolutionEstimation {
    protected TiltSeries tsSignal;
    protected TomoReconstruction2 recSignal;
    ReconstructionThreadManager recThManSignal;
    protected TiltSeries tsNoise;
    protected TomoReconstruction2 recNoise;
    ReconstructionThreadManager recThManNoise;
    protected TiltSeries tsVSSNR;
    protected TomoReconstruction2 recVSSNR;
    ReconstructionThreadManager recThManVSSNR;
    protected ReconstructionParameters recParameters;
    protected int ringWidth=4;
    protected double pixelSize=1;

    protected TiltSeries tsFSCeven;
    protected TiltSeries tsFSCodd;
    protected TomoReconstruction2 recFSCeven;
    protected TomoReconstruction2 recFSCodd;
    ReconstructionThreadManager recManFSCeven;
    ReconstructionThreadManager recManFSCodd;

    static DComplexRealFunction powerSpectrumFunction = new DComplexRealFunction() {
            public double apply(double[] a) {
                return a[0]*a[0] + a[1]*a[1];
            }
        };
    static FComplexRealFunction powerSpectrumFunctionF = new FComplexRealFunction() {
        public float apply(float[] a) {
            return a[0]*a[0] + a[1]*a[1];
        }
    };
    public boolean debug=false;


    ReconstructionThreadManager currentRecThreadManager;

    double numberOfSteps=3;
    double completion=-1000;
    ArrayList<Double> reconstructionSignalDissimilarityError=null;
    double[][]ssnr;
    double[][]fsc;



    public ResolutionEstimation(TiltSeries tsSignal, ReconstructionParameters parameters) {
        this.tsSignal=tsSignal;
        this.recParameters=parameters;
    }

    public ResolutionEstimation(ResolutionEstimation other, TiltSeries ts){
        this.tsSignal=ts;
        this.recParameters=other.recParameters;
        this.recThManSignal=new ReconstructionThreadManager(other.recThManSignal,ts);
    }

    public ReconstructionThreadManager getRecThManSignal() {
        return recThManSignal;
    }

    public void doSignalReconstruction(){
        if(recThManSignal==null) recThManSignal=new ReconstructionThreadManager(tsSignal.getWindow(),tsSignal);
        else recThManSignal.setTiltSeries(tsSignal);
        if(recSignal!=null) recThManSignal.setRec2(recSignal);
        currentRecThreadManager=recThManSignal;
        recSignal=recThManSignal.reconstruct(recParameters,false);
        reconstructionSignalDissimilarityError=recThManSignal.getReconstructionDissimilarities();
    }
    public void generateSSNRNoiseData(){
        ImageStack is=new ImageStack(tsSignal.getWidth(),tsSignal.getHeight());
        for(int i=0;i<tsSignal.getImageStackSize();i++){
            System.out.println("create gaussian noise image "+ i);
            ImageProcessor ip=new FloatProcessor(is.getWidth(),is.getHeight());
            ip.noise(1);
            is.addSlice(""+i,ip);
        }
        tsNoise=new TiltSeries(new ImagePlus("noise",is),tsSignal.getTiltAngles());
        tsNoise.setAlignment(tsSignal.getAlignment());
        tsNoise.setFillType(tsSignal.getFillType());
        tsNoise.updateInternalData();
        tsNoise.setTomoJPoints(tsSignal.getTomoJPoints());
        if(recThManNoise==null)recThManNoise=new ReconstructionThreadManager(recThManSignal,tsNoise);
        else recThManNoise.setTiltSeries(tsNoise);
        if(recNoise!=null) recThManNoise.setRec2(recNoise);

        currentRecThreadManager=recThManNoise;
        recNoise=recThManNoise.reconstruct(recParameters,false);

    }

    public void generateFSCData(){
        tsFSCeven=tsSignal;
        tsFSCodd=tsSignal;

        System.out.println("FSCodd - "+recParameters.getReconstructionType());
        if(recThManSignal!=null) {
            if(recManFSCodd==null)recManFSCodd=new ReconstructionThreadManager(recThManSignal,tsFSCodd);
            else recManFSCodd.setTiltSeries(tsFSCodd);
        }else{
            if(recManFSCodd==null)recManFSCodd=new ReconstructionThreadManager(tsFSCodd.getWindow(),tsFSCodd);
            else recManFSCodd.setTiltSeries(tsFSCodd);
        }
        if(recFSCodd!=null) recManFSCodd.setRec2(recFSCodd);
        recParameters.setFscType(ReconstructionParameters.ODD_PROJECTIONS);
        currentRecThreadManager=recManFSCodd;
        recFSCodd=recManFSCodd.reconstruct(recParameters,false);
        recFSCodd.setTitle("FSCodd");

        if(recThManSignal!=null) {
            if(recManFSCeven==null){
                recManFSCeven=new ReconstructionThreadManager(recThManSignal,tsFSCeven);
            }
            else recManFSCeven.setTiltSeries(tsFSCeven);
        }else{
            if(recManFSCeven==null){
                recManFSCeven=new ReconstructionThreadManager(tsFSCeven.getWindow(),tsFSCeven);
                //IJ.showMessageWithCancel("create FSCMAn","create fsc");
            }
            else recManFSCeven.setTiltSeries(tsFSCeven);
        }
        if(recFSCeven!=null){
            recManFSCeven.setRec2(recFSCeven);
            //IJ.showMessageWithCancel("copy rec","FSC copy rec");
        }
        recParameters.setFscType(ReconstructionParameters.EVEN_PROJECTIONS);

        currentRecThreadManager=recManFSCeven;
        recFSCeven=recManFSCeven.reconstruct(recParameters,false);
        recFSCeven.setTitle("FSCeven");

//        if(recManFSCodd==null)recManFSCodd=new ReconstructionThreadManager(recManFSCeven,tsFSCodd);
//        else recManFSCodd.setTiltSeries(tsFSCodd);
//        if(recFSCodd!=null) recManFSCodd.setRec2(recFSCodd);
//        recParameters.setFscType(TomoReconstruction2.FSC_ODD);
//
//        currentRecThreadManager=recManFSCodd;
//        recFSCodd=recManFSCodd.reconstruct(recParameters,false);
//        recFSCodd.setTitle("FSCodd");

        recParameters.setFscType(ReconstructionParameters.ALL_PROJECTIONS);

    }
    public double[][] SSNR(boolean computeVSSNR){
        completion=0;
        numberOfSteps=(computeVSSNR)?6:4;
        //do reconstruction
        doSignalReconstruction();
        completion++;
        //generate  noise data
        generateSSNRNoiseData();
        completion++;
        if(debug) {
            new FileSaver(tsNoise).saveAsTiffStack("tsnoise.tif");
            new FileSaver(recNoise).saveAsTiffStack("recNoise.tif");
            new FileSaver(tsSignal).saveAsTiffStack("tsSignal.tif");
            new FileSaver(recSignal).saveAsTiffStack("recSignal.tif");
        }

        //for each tilt
        double[] S_S1D=new double[tsSignal.getWidth()];
        double[] S_N1D=new double[tsSignal.getWidth()];
        double[] N_S1D=new double[tsSignal.getWidth()];
        double[] N_N1D=new double[tsSignal.getWidth()];
        double[] K1D=new double[tsSignal.getWidth()];

        if(computeVSSNR){
            ImageStack is=new ImageStack(tsSignal.getWidth(),tsSignal.getHeight());
            for(int i=0;i<tsSignal.getImageStackSize();i++){
                is.addSlice(new FloatProcessor(tsSignal.getWidth(),tsSignal.getHeight()));
            }
            tsVSSNR=new TiltSeries(new ImagePlus("tsVSSNR",is));
            tsVSSNR.setTiltAngles(tsSignal.getTiltAngles());
            completion++;
        }
        System.out.println("compute SSNR");
        for(int tiltIndex=0;tiltIndex<tsSignal.getImageStackSize();tiltIndex++) {
            addToVectors1D(tiltIndex,S_S1D,S_N1D,N_S1D,N_N1D,K1D,computeVSSNR);
            //generate projection from volume (thnoise and thsignal)
            //generate FFT of each projection (expNoise, expSignal, thnoise and thsignal)--> power spectrum
            //projection of creation for VSSNR
            //average over rings
        }
        completion++;
        //compute SSNR
        int imgno=1;
        double[] S_SNR1D = new double[S_S1D.length];
        double[] N_SNR1D = new double[S_S1D.length];
        for(int i=0;i<S_S1D.length;i++){
            S_SNR1D[i] = S_S1D[i]/ S_N1D[i];
            S_S1D[i]/= K1D[i];
            S_N1D[i]/=K1D[i];
            N_SNR1D[i]= N_S1D[i]/N_N1D[i];
            N_S1D[i]/=K1D[i];
            N_N1D[i]/=K1D[i];
            imgno++;
        }
        System.out.println("create output");

        double[][] output=new double[9][S_SNR1D.length];
        int imax=0;
        for(int i=0;i<S_SNR1D.length;i++){
            double w=convertToFrequency(i,S_SNR1D.length);
            if(w<0){
                imax=i;
                break;
            }
            output[0][i]=i;
            output[1][i]=w/pixelSize;
            double SSNR = S_SNR1D[i]/N_SNR1D[i];
            if(SSNR>1) output[2][i]=10 * Math.log10(SSNR -1); //corrected SSNR
            else output[2][i]= -1000;
            output[3][i]= S_SNR1D[i];
            output[4][i]= 10 * Math.log10(S_S1D[i] / imgno);
            output[5][i]= 10 * Math.log10(S_N1D[i] / imgno);
            output[6][i]= N_SNR1D[i];
            output[7][i]= 10 * Math.log10(N_S1D[i] / imgno);
            output[8][i]= 10 * Math.log10(N_N1D[i] / imgno);
            imax++;
        }

        double[][] result=new double[9][imax];
        //System.arraycopy(output,0,result,0,imax);
        for(int i=0;i<imax;i++){
            result[0][i]=output[0][i];
            result[1][i]=output[1][i];
            result[2][i]=output[2][i];
            result[3][i]=output[3][i];
            result[4][i]=output[4][i];
            result[5][i]=output[5][i];
            result[6][i]=output[6][i];
            result[7][i]=output[7][i];
            result[8][i]=output[8][i];
        }
        completion++;

        //produce VSSNR
        if (computeVSSNR){
            System.out.println("compute VSSNR");
            if(debug) new FileSaver(tsVSSNR).saveAsTiffStack("tsVSSNR.tif");
            recThManVSSNR = new ReconstructionThreadManager(recThManSignal,tsVSSNR);

            currentRecThreadManager=recThManVSSNR;
            recVSSNR=recThManVSSNR.reconstruct(recParameters,false);
            if(debug)new FileSaver(recVSSNR).saveAsTiffStack("recVSSNR.tif");
            completion++;
        }
        ssnr=result;
          return result;
    }

    public static double[][][] frcSlices(ImagePlus recFSCeven, ImagePlus recFSCodd){
        System.out.println("createFFT : copy");
        float[] dataeven=new float[recFSCeven.getWidth()*recFSCeven.getHeight()*recFSCeven.getImageStackSize()];
        float[] dataodd=new float[recFSCeven.getWidth()*recFSCeven.getHeight()*recFSCeven.getImageStackSize()];
        int index=0;
        int length=recFSCeven.getWidth()*recFSCeven.getHeight();
        for(int i =0; i<recFSCeven.getImageStackSize();i++){
            float[] pixseven=(float[])recFSCeven.getImageStack().getPixels(i+1) ;
            float[] pixsodd=(float[])recFSCodd.getImageStack().getPixels(i+1) ;
            for(int pos=0; pos<pixseven.length;pos++){
                dataeven[index+pos]=pixseven[pos];
                dataodd[index+pos]=pixsodd[pos];
            }
            index+=length;
        }
        DenseFloatMatrix3D mask=new DenseFloatMatrix3D(recFSCeven.getImageStackSize(),recFSCeven.getHeight(),recFSCeven.getWidth());
        for(int z=0;z<recFSCeven.getImageStackSize();z++){
            //double dz=z-(recFSCeven.getImageStackSize()/2.0);
            //dz*=dz;
            for (int y=0;y<recFSCeven.getHeight();y++){
                double dy=y-(recFSCeven.getHeight()/2.0);
                dy*=dy;
                for(int x=0;x<recFSCeven.getWidth();x++){
                    float value=0;
                    double dist = x-(recFSCeven.getWidth()/2.0);
                    dist*=dist;
                    dist+=dy;
                    dist = Math.sqrt(dist);
                    if(dist<recFSCeven.getWidth()-20) value=1;
                    else if(dist<recFSCeven.getWidth()) value = (float) (1 + Math.cos((dist - recFSCeven.getWidth()-20) * Math.PI/20)) * .5f;

                    mask.setQuick(z,y,x,value);
                }
            }
        }

        System.out.println("createFFT : compute");
        DenseFloatMatrix3D H=new DenseFloatMatrix3D(recFSCeven.getImageStackSize(),recFSCeven.getHeight(),recFSCeven.getWidth());
        H.assign(dataeven);
        H.assign(mask, FloatFunctions.mult);
        DenseFComplexMatrix3D ffteven=H.getFft2Slices();
        H.assign(dataodd);
        H.assign(mask,FloatFunctions.mult);
        DenseFComplexMatrix3D fftodd=H.getFft2Slices();


        System.out.println("convolution");
        //convolution
        FComplexMatrix3D m=ffteven.copy().assign(fftodd, FComplexFunctions.multConjSecond);
        //power spectrum
        System.out.println("power spectrum");
        //FloatMatrix3D pseven=ffteven.assign(powerSpectrumFunctionF).getRealPart();
        //FloatMatrix3D psodd=fftodd.assign(powerSpectrumFunctionF).getRealPart();

        //compute frcs
        int radiusSize=recFSCeven.getWidth()/2+1;
        double[][][] frcs = new double[recFSCeven.getImageStackSize()][4][radiusSize];
        double[] num = new double[radiusSize];
        double[] denEven = new double[radiusSize];
        double[] denOdd = new double[radiusSize];
        double[] error_l2 = new double[radiusSize];
        double[] radialCount = new double[radiusSize];

        for(int z=0;z<recFSCeven.getImageStackSize();z++) {
            //double tmpz= convertToFrequency(z,recFSCeven.getImageStackSize());
            //tmpz*=tmpz;
            for (int y = 0, yy = 0; y < recFSCeven.getHeight(); y++) {
                double tmpy = convertToFrequency(y, recFSCeven.getHeight());
                tmpy *= tmpy;
                for (int x = 0; x < recFSCeven.getWidth(); x++) {
                    double tmpx = convertToFrequency(x, recFSCeven.getWidth());
                    tmpx *= tmpx;
                    double w = Math.sqrt(tmpx + tmpy);
                    int wIndex = (int)Math.round(w * recFSCeven.getWidth());
                    if(wIndex>=radiusSize) continue;
                    float[] valueEven=ffteven.get(z,y,x);
                    float[] valueOdd=fftodd.get(z,y,x);

                    double absEven=Math.sqrt(valueEven[0]*valueEven[0]+valueEven[1]*valueEven[1]);
                    double absOdd=Math.sqrt(valueOdd[0]*valueOdd[0]+valueOdd[1]*valueOdd[1]);
                    //real(conj(even)*odd
                    num[wIndex]+=m.getQuick(z,y,x)[0];
                    denEven[wIndex]+=absEven*absEven;
                    denOdd[wIndex]+=absOdd*absOdd;
                    double[] tmp={valueEven[0]-valueOdd[0],valueEven[1]-valueOdd[1]};
                    error_l2[wIndex]+=Math.sqrt(tmp[0]*tmp[0]+tmp[1]*tmp[1]);
                    radialCount[wIndex]++;
                }
                yy += recFSCeven.getWidth();

            }
            for(int i=0;i<num.length;i++){
                frcs[z][0][i]=((double)i)/(recFSCeven.getWidth());
                frcs[z][1][i]=num[i]/Math.sqrt(denEven[i]*denOdd[i]);
                frcs[z][2][i]=2/Math.sqrt(radialCount[i]);
                frcs[z][3][i]=error_l2[i]/radialCount[i];
            }
        }
        return frcs;
    }

    public static double[][] frc(ImageProcessor img1, ImageProcessor img2){
        DenseFloatMatrix2D mask= new DenseFloatMatrix2D(img1.getHeight(),img1.getWidth());
        for (int y=0;y<img1.getHeight();y++){
            double dy=y-(img1.getHeight()/2.0);
            dy*=dy;
            for(int x=0;x<img1.getWidth();x++){
                float value=0;
                double dist = x-(img1.getWidth()/2.0);
                dist*=dist;
                dist+=dy;
                dist = Math.sqrt(dist);
                if(dist<img1.getWidth()) value=1;
                else if(dist<img1.getWidth()+20) value = (float) (1 + Math.cos((dist - img1.getWidth()) * Math.PI/20)) * .5f;

                mask.setQuick(y,x,value);
            }
        }
        DenseFloatMatrix2D H=new DenseFloatMatrix2D(img1.getHeight(),img1.getWidth());
        H.assign((float[])img1.convertToFloatProcessor().getPixels());
        H.assign(mask, FloatFunctions.mult);
        DenseFComplexMatrix2D ffteven=H.getFft2();
        H.assign((float[])img2.convertToFloatProcessor().getPixels());
        H.assign(mask,FloatFunctions.mult);
        DenseFComplexMatrix2D fftodd=H.getFft2();


        System.out.println("convolution");
        //convolution
        FComplexMatrix2D m=ffteven.copy().assign(fftodd, FComplexFunctions.multConjSecond);
        //power spectrum
        //System.out.println("power spectrum");
        //FloatMatrix3D pseven=ffteven.assign(powerSpectrumFunctionF).getRealPart();
        //FloatMatrix3D psodd=fftodd.assign(powerSpectrumFunctionF).getRealPart();
        //compute frc
        int radiusSize=img1.getWidth()/2+1;
        double[][] fsc = new double[4][radiusSize];
        double[] num = new double[radiusSize];
        double[] den1 = new double[radiusSize];
        double[] den2 = new double[radiusSize];
        double[] error_l2 = new double[radiusSize];
        double[] radialCount = new double[radiusSize];


        for (int y = 0, yy = 0; y < img1.getHeight(); y++) {
            double tmpy = convertToFrequency(y, img1.getHeight());
            tmpy *= tmpy;
            for (int x = 0; x < img1.getWidth(); x++) {
                double tmpx = convertToFrequency(x, img1.getWidth());
                tmpx *= tmpx;
                double w = Math.sqrt(tmpx + tmpy );
                int wIndex = (int)Math.round(w * img1.getWidth());
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
            yy += img1.getWidth();

        }

        for(int i=0;i<num.length;i++){
            fsc[0][i]=((double)i)/(img1.getWidth());
            fsc[1][i]=num[i]/Math.sqrt(den1[i]*den2[i]);
            fsc[2][i]=2/Math.sqrt(radialCount[i]);
            fsc[3][i]=error_l2[i]/radialCount[i];
        }
        System.out.println("fsc finished ");
        return fsc;

    }

    public static double[][] fsc(ImagePlus recFSCeven, ImagePlus recFSCodd){
        System.out.println("createFFT : copy");
        float[] dataeven=new float[recFSCeven.getWidth()*recFSCeven.getHeight()*recFSCeven.getImageStackSize()];
        float[] dataodd=new float[recFSCeven.getWidth()*recFSCeven.getHeight()*recFSCeven.getImageStackSize()];
        int index=0;
        int length=recFSCeven.getWidth()*recFSCeven.getHeight();
        for(int i =0; i<recFSCeven.getImageStackSize();i++){
            float[] pixseven=(float[])recFSCeven.getImageStack().getPixels(i+1) ;
            float[] pixsodd=(float[])recFSCodd.getImageStack().getPixels(i+1) ;
            for(int pos=0; pos<pixseven.length;pos++){
                dataeven[index+pos]=pixseven[pos];
                dataodd[index+pos]=pixsodd[pos];
            }
            index+=length;
        }
        DenseFloatMatrix3D mask=new DenseFloatMatrix3D(recFSCeven.getImageStackSize(),recFSCeven.getHeight(),recFSCeven.getWidth());
        for(int z=0;z<recFSCeven.getImageStackSize();z++){
            double dz=z-(recFSCeven.getImageStackSize()/2.0);
            dz*=dz;
            for (int y=0;y<recFSCeven.getHeight();y++){
                double dy=y-(recFSCeven.getHeight()/2.0);
                dy*=dy;
                for(int x=0;x<recFSCeven.getWidth();x++){
                    float value=0;
                    double dist = x-(recFSCeven.getWidth()/2.0);
                    dist*=dist;
                    dist+=dz+dy;
                    dist = Math.sqrt(dist);
                    if(dist<recFSCeven.getImageStackSize()) value=1;
                    else if(dist<recFSCeven.getImageStackSize()+20) value = (float) (1 + Math.cos((dist - recFSCeven.getImageStackSize()) * Math.PI/20)) * .5f;

                    mask.setQuick(z,y,x,value);
                }
            }
        }

        System.out.println("createFFT : compute");
        DenseFloatMatrix3D H=new DenseFloatMatrix3D(recFSCeven.getImageStackSize(),recFSCeven.getHeight(),recFSCeven.getWidth());
        H.assign(dataeven);
        H.assign(mask, FloatFunctions.mult);
        DenseFComplexMatrix3D ffteven=H.getFft3();
        H.assign(dataodd);
        H.assign(mask,FloatFunctions.mult);
        DenseFComplexMatrix3D fftodd=H.getFft3();


        System.out.println("convolution");
        //convolution
        FComplexMatrix3D m=ffteven.copy().assign(fftodd, FComplexFunctions.multConjSecond);
        //power spectrum
        System.out.println("power spectrum");
        //FloatMatrix3D pseven=ffteven.assign(powerSpectrumFunctionF).getRealPart();
        //FloatMatrix3D psodd=fftodd.assign(powerSpectrumFunctionF).getRealPart();

        //compute fsc
        int radiusSize=recFSCeven.getWidth()/2+1;
        double[][] fsc = new double[4][radiusSize];
        double[] num = new double[radiusSize];
        double[] denEven = new double[radiusSize];
        double[] denOdd = new double[radiusSize];
        double[] error_l2 = new double[radiusSize];
        double[] radialCount = new double[radiusSize];

        for(int z=0;z<recFSCeven.getImageStackSize();z++) {
            double tmpz= convertToFrequency(z,recFSCeven.getImageStackSize());
            tmpz*=tmpz;
            for (int y = 0, yy = 0; y < recFSCeven.getHeight(); y++) {
                double tmpy = convertToFrequency(y, recFSCeven.getHeight());
                tmpy *= tmpy;
                for (int x = 0; x < recFSCeven.getWidth(); x++) {
                    double tmpx = convertToFrequency(x, recFSCeven.getWidth());
                    tmpx *= tmpx;
                    double w = Math.sqrt(tmpx + tmpy + tmpz);
                    int wIndex = (int)Math.round(w * recFSCeven.getWidth());
                    if(wIndex>=radiusSize) continue;
                    float[] valueEven=ffteven.get(z,y,x);
                    float[] valueOdd=fftodd.get(z,y,x);

                    double absEven=Math.sqrt(valueEven[0]*valueEven[0]+valueEven[1]*valueEven[1]);
                    double absOdd=Math.sqrt(valueOdd[0]*valueOdd[0]+valueOdd[1]*valueOdd[1]);
                    //real(conj(even)*odd
                    num[wIndex]+=m.getQuick(z,y,x)[0];
                    denEven[wIndex]+=absEven*absEven;
                    denOdd[wIndex]+=absOdd*absOdd;
                    double[] tmp={valueEven[0]-valueOdd[0],valueEven[1]-valueOdd[1]};
                    error_l2[wIndex]+=Math.sqrt(tmp[0]*tmp[0]+tmp[1]*tmp[1]);
                    radialCount[wIndex]++;
                }
                yy += recFSCeven.getWidth();

            }
        }
        for(int i=0;i<num.length;i++){
            fsc[0][i]=((double)i)/(recFSCeven.getWidth());
            fsc[1][i]=num[i]/Math.sqrt(denEven[i]*denOdd[i]);
            fsc[2][i]=2/Math.sqrt(radialCount[i]);
            fsc[3][i]=error_l2[i]/radialCount[i];
        }
        System.out.println("fsc finished ");
        return fsc;
    }


    public double[][] fsc(){
        generateFSCData();
        if(debug) {
            new FileSaver(tsFSCeven).saveAsTiffStack("tsFSCeven.tif");
            new FileSaver(tsFSCodd).saveAsTiffStack("tsFSCodd.tif");
            new FileSaver(recFSCeven).saveAsTiffStack("recFSCeven.tif");
            new FileSaver(recFSCodd).saveAsTiffStack("recFSCodd.tif");
        }
        return fsc=fsc(recFSCeven,recFSCodd);
    }

    public double[][][] frc2Slices(){
        generateFSCData();
        if(debug) {
            new FileSaver(tsFSCeven).saveAsTiffStack("tsFSCeven.tif");
            new FileSaver(tsFSCodd).saveAsTiffStack("tsFSCodd.tif");
            new FileSaver(recFSCeven).saveAsTiffStack("recFSCeven.tif");
            new FileSaver(recFSCodd).saveAsTiffStack("recFSCodd.tif");
        }
        return frcSlices(recFSCeven,recFSCodd);
    }

    protected void addToVectors1D(int index, double[] S_S1D, double[] S_N1D, double[] N_S1D, double[] N_N1D, double[] K1D, boolean computeVSSNR){
        //generate projection from volume (thnoise and thsignal)
        float[] signal=tsSignal.getPixels(index);
        float[] noise=tsNoise.getPixels(index);

        /*recThManSignal.getProjector().addProjection(index);
        recThManSignal.getProjector().project();
        float[] thSignal=recThManSignal.getProjector().getProjection(0);
        recThManSignal.getProjector().clearAllProjections(); */
        float[] thSignal=recThManSignal.getProjection(index);

        /*recThManNoise.getProjector().addProjection(index);
        recThManNoise.getProjector().project();
        float[] thNoise=recThManNoise.getProjector().getProjection(0);
        recThManNoise.getProjector().clearAllProjections();  */
        float[] thNoise = recThManNoise.getProjection(index);

        convertNaNToZeros(signal);
        convertNaNToZeros(noise);
        convertNaNToZeros(thSignal);
        convertNaNToZeros(thNoise);

//        new FileSaver(new ImagePlus(""+index,new FloatProcessor(tsSignal.getWidth(),tsSignal.getHeight(),signal))).saveAsTiff("signal"+index+".tif");
//        new FileSaver(new ImagePlus(""+index,new FloatProcessor(tsSignal.getWidth(),tsSignal.getHeight(),noise))).saveAsTiff("noise"+index+".tif");
//        new FileSaver(new ImagePlus(""+index,new FloatProcessor(tsSignal.getWidth(),tsSignal.getHeight(),thSignal))).saveAsTiff("thsignal"+index+".tif");
//        new FileSaver(new ImagePlus(""+index,new FloatProcessor(tsSignal.getWidth(),tsSignal.getHeight(),thNoise))).saveAsTiff("thnoise"+index+".tif");


        for(int pos=0;pos<signal.length;pos++){
            signal[pos] -= thSignal[pos];
            noise[pos]  -= thNoise[pos];
        }

//        new FileSaver(new ImagePlus(""+index,new FloatProcessor(tsSignal.getWidth(),tsSignal.getHeight(),signal))).saveAsTiff("signal-thsignal"+index+".tif");
//        new FileSaver(new ImagePlus(""+index,new FloatProcessor(tsSignal.getWidth(),tsSignal.getHeight(),noise))).saveAsTiff("noise-thnoise"+index+".tif");

        //generate FFT of each projection (expNoise, expSignal, thnoise and thsignal)--> power spectrum
        DenseDoubleMatrix2D H1 = new DenseDoubleMatrix2D(tsSignal.getHeight(), tsSignal.getWidth());
        H1.assign(signal);
        DenseDComplexMatrix2D fftSignal = H1.getFft2();
        H1.assign(thSignal);
        DenseDComplexMatrix2D fftThSignal = H1.getFft2();
        H1.assign(noise);
        DenseDComplexMatrix2D fftNoise = H1.getFft2();
        H1.assign(thNoise);
        DenseDComplexMatrix2D fftThNoise = H1.getFft2();

         double[] amplitudeSignal=(double[])fftSignal.assign(powerSpectrumFunction).getRealPart().elements();
        double[] amplitudeThSignal=(double[])fftThSignal.assign(powerSpectrumFunction).getRealPart().elements();
        double[] amplitudeNoise=(double[])fftNoise.assign(powerSpectrumFunction).getRealPart().elements();
        double[] amplitudeThNoise=(double[])fftThNoise.assign(powerSpectrumFunction).getRealPart().elements();

        /*double[] amplitudeSignal=new double[signal.length];
        double[] amplitudeThSignal=new double[signal.length];
        double[] amplitudeNoise=new double[signal.length];
        double[] amplitudeThNoise=new double[signal.length];

        for(int i=0;i<amplitudeSignal.length;i++){
            amplitudeSignal[i]=fftSignal[2*i]*fftSignal[2*i]+fftSignal[2*i+1]*fftSignal[2*i+1];
            amplitudeThSignal[i]=fftThSignal[2*i]*fftThSignal[2*i]+fftThSignal[2*i+1]*fftThSignal[2*i+1];
            amplitudeNoise[i]=fftNoise[2*i]*fftNoise[2*i]+fftNoise[2*i+1]*fftNoise[2*i+1];
            amplitudeThNoise[i]=fftThNoise[2*i]*fftThNoise[2*i]+fftThNoise[2*i+1]*fftThNoise[2*i+1];
        }  */
        //projection of creation for VSSNR
        if(computeVSSNR){
            float[] SSNR2D=tsVSSNR.getOriginalPixels(index);
            for(int i=0;i<SSNR2D.length;i++){
                double ISSNR=0, alpha=0, SSNR=0;
                double aux=amplitudeSignal[i];
                ISSNR= amplitudeThSignal[i]/aux;
                aux = amplitudeNoise[i];
                alpha = amplitudeThNoise[i]/aux;
                aux=ISSNR/alpha -1.0;
                SSNR=Math.max(aux,0.0);
                SSNR2D[i]=(float)(10.0 * Math.log10(SSNR +1.0));
            }
            System.arraycopy(swapQuadrant2D(SSNR2D,tsVSSNR.getWidth(),tsVSSNR.getHeight(),tsVSSNR.getWidth()/2,tsVSSNR.getHeight()/2),0,SSNR2D,0,SSNR2D.length);

        }

        //average over rings
        for(int y=0, yy=0;y<tsSignal.getHeight();y++){
            double tmpy=convertToFrequency(y,tsSignal.getHeight());
            tmpy*=tmpy;
            for(int x=0;x<tsSignal.getWidth();x++){
                double tmpx=convertToFrequency(x,tsSignal.getWidth());
                tmpx*=tmpx;
                double w=Math.sqrt(tmpx+tmpy);
                double wIndex=w*tsSignal.getWidth();

                int l0 = (int)Math.ceil(wIndex - ringWidth);
                l0= Math.max(0,l0);
                int lF = (int) Math.floor(wIndex);

                double S_signal=amplitudeThSignal[yy+x];
                double S_noise=amplitudeSignal[yy+x];
                double N_signal=amplitudeThNoise[yy+x];
                double N_noise=amplitudeNoise[yy+x];

                if(Double.isNaN(S_signal)||Double.isNaN(S_noise)||Double.isNaN(N_signal)||Double.isNaN(N_noise)) {
                    System.out.println("NaN!!!");
                }

                for(int l=l0;l<lF;l++){
                    S_S1D[l]+= S_signal;
                    S_N1D[l] += S_noise;
                    N_S1D[l]+= N_signal;
                    N_N1D[l]+= N_noise;
                    K1D[l]++;
                }
            }
            yy+=tsSignal.getWidth();

        }


    }

    public static double convertToFrequency(double x, int sizeMax){
        return ((x<=(sizeMax>>1))?x:x-sizeMax)/sizeMax;
    }

    void convertNaNToZeros(float[] data){
        for(int i=0;i<data.length;i++){
            if(Float.isNaN(data[i])) data[i]=0;
        }
    }

    private float[] swapQuadrant2D(float[] data, int width, int height, int centerx, int centery){
        float[] result=new float[data.length];
        for(int y=0;y<height;y++){
            int yy=y*width;
            int yyc=(y+centery);
            if(yyc>=height) yyc-=height;
            yyc*=width;
            System.arraycopy(data,yy,result,yyc+centerx,centerx);
            System.arraycopy(data,yy+centerx,result,yyc,centerx);

        }
        return result;
    }

    public void setReconstructionSignal(TomoReconstruction2 rec){
        recSignal=rec;
    }

    public TomoReconstruction2 getReconstructionSignal(){
        return recSignal;
    }
    public TomoReconstruction2 getReconstructionFSCEven(){
        return recFSCeven;
    }
    public TomoReconstruction2 getReconstructionFSCOdd(){
        return recFSCodd;
    }

    public TomoReconstruction2 getVSSNR() {
        return recVSSNR;
    }

    public double[][] getSsnr() {
        return ssnr;
    }

    public double[][] getFsc() {
        return fsc;
    }

    public TomoReconstruction2 getRecNoise() {
        return recNoise;
    }

    public void resetRecs(){
        if(recFSCodd!=null) recFSCodd.resetPixels();
        if(recFSCeven!=null) recFSCeven.resetPixels();
        if(recSignal!=null) recSignal.resetPixels();
        if(recNoise!=null) recNoise.resetPixels();
        reconstructionSignalDissimilarityError=null;

    }

    public void setTiltSeries(TiltSeries ts){
        tsSignal=ts;
        if(recThManSignal!=null)recThManSignal.setTiltSeries(ts);
    }

    public double getNumberofsteps(){
        return numberOfSteps;
    }

    public double getCompletion() {
        if(currentRecThreadManager!=null) return currentRecThreadManager.getCompletion();
        else return -123456789;

    }

    public void interrupt(){
        if(recSignal!=null){
            //System.out.println("resolution estimation interrupted");
            recSignal.interrupt();
        }
        if(recNoise!=null) recNoise.interrupt();
        if(recFSCeven!=null) recFSCeven.interrupt();
        if(recFSCodd!=null) recFSCodd.interrupt();
        if(currentRecThreadManager!=null) currentRecThreadManager.getRec2().interrupt();
    }

    public ArrayList<Double> getReconstructionSignalDissimilarityError() {
        return reconstructionSignalDissimilarityError;
    }
}
