package fr.curie.gpu.tomoj.tomography.projectors;

import fractsplinewavelets.CoefProcessing;
import fractsplinewavelets.Operations;
import imageware.Builder;
import imageware.ImageWare;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.gpu.tomoj.tomography.filters.FFTWeightingGPU;
import fr.curie.gpu.utils.GPUDevice;

/**
 * Created by cmessaoudi on 15/01/2018.
 */
public class CompressedSensingProjector3DGPU extends VoxelProjector3DGPU {
    public final static int WAVELET_ORTHONORMAL = 0;
    public final static int WAVELET_BSPLINE = 3;
    public final static int WAVELET_DUAL = 6;

    protected double waveletDegree = 3;
    protected double waveletShift = 0;
    protected int waveletType=WAVELET_BSPLINE;
    protected double softThresholdingPercentageOfZeros=0.95;
    protected int[] iters= new int[]{1,1,1};


    public CompressedSensingProjector3DGPU(TiltSeries ts, TomoReconstruction2 rec, GPUDevice device, FFTWeightingGPU weightingFilter){
        super(ts,rec, device, weightingFilter);
        iters=findBestWaveletsIteration(rec);
    }

    @Override
    public void endOfIteration() {
        super.endOfIteration();
        updateFromGPU(currentStartY,currentEndY);
        filterInWavelets(rec,softThresholdingPercentageOfZeros);
    }

    @Override
    public void startOfIteration() {
        super.startOfIteration();
        copyInGPUBuffer(device,volMemBufferIndex.get(currentWorkingBuffer),rec,currentStartY,currentEndY);
        volumeChanged=true;
    }


    protected void filterInWavelets(TomoReconstruction2 rec, double softThresholdingPercentageOfZeros){
        ImageWare buffer= Builder.create(rec, 3);

        Operations.doTransform3D(buffer,iters,waveletType, waveletDegree,waveletShift);
        //find threshold
        double softThreshold=findThreshold(buffer,softThresholdingPercentageOfZeros);
        System.out.println("filter in wavelet\npercentage of zero: "+softThresholdingPercentageOfZeros+"\ngives threshold:"+softThreshold);
        CoefProcessing.doSoftThreshold3D(buffer,softThreshold);
        Operations.doInverse3D(buffer,iters,waveletType,waveletDegree,waveletShift);
        copyBufferToRec(buffer,rec);


    }

    protected void copyBufferToRec(ImageWare buffer,TomoReconstruction2 rec){
        for(int z=0;z<buffer.getSizeZ();z++){
            for(int y=0;y<buffer.getSizeY();y++){
                for(int x=0;x<buffer.getSizeX();x++){
                    double value=buffer.getPixel(x,y,z);
                    rec.putPixel(x,y,z,value);
                }
            }
        }
    }

    protected double findThreshold(ImageWare buffer, double percentageOfZeros){
        double[] minmax=buffer.getMinMax();
        double histomax=Math.max(Math.abs(minmax[0]),Math.abs(minmax[1]));
        long sum = 0;
        long nbpixels = (long) (percentageOfZeros * buffer.getSizeX() * buffer.getSizeY() * buffer.getSizeZ());
        int index;
        boolean continueLoop;
        double histoscale;
        do {
            sum=0;
            continueLoop=false;
            histoscale = 256.0 / (histomax);
            int[] histo = computeAbsHistogram(buffer, 0, histomax);
            index = -1;
            while (sum < nbpixels) {
                index++;
                sum += histo[index];
            }
            if (sum / (double) nbpixels > 1.01) {
                histomax=Math.abs((index+1)/histoscale);
                System.out.println("histomax:"+histomax+ ", sum:"+sum+", nbpixels:"+nbpixels+", sum/nbpixels:"+(sum / (double) nbpixels));
                continueLoop=true;
            }
        }while(continueLoop);

        return index/histoscale;


    }

    protected int[] computeAbsHistogram(ImageWare buffer, double min, double max){
        int[] histo=new int[256];
        double scale = 256.0/( max-min);
        for(int z=0;z<buffer.getSizeZ();z++){
            for(int y=0;y<buffer.getSizeY();y++){
                for(int x=0;x<buffer.getSizeX();x++){
                    double value=Math.abs(buffer.getPixel(x,y,z));
                    int index=(int)(scale*value);
                    if(index>=histo.length)index=histo.length-1;
                    histo[index]++;
                }
            }
        }
        return histo;
    }

    protected int[] findBestWaveletsIteration(TomoReconstruction2 rec){
        int nbiterX=1;
        int startsize=64;
        while(startsize<rec.getWidth()) {
            startsize*=2;
            nbiterX++;
        }

        int nbiterY=1;
        startsize=64;
        while(startsize<rec.getWidth()) {
            startsize*=2;
            nbiterY++;
        }

        int nbiterZ=1;
        startsize=64;
        while(startsize<rec.getWidth()) {
            startsize*=2;
            nbiterZ++;
        }
        int[] result={nbiterX,nbiterY,nbiterZ};
        return result;
    }

    public double getWaveletDegree() {
        return waveletDegree;
    }

    public void setWaveletDegree(double waveletDegree) {
        this.waveletDegree = waveletDegree;
    }

    public double getWaveletShift() {
        return waveletShift;
    }

    public void setWaveletShift(double waveletShift) {
        this.waveletShift = waveletShift;
    }

    public int getWaveletType() {
        return waveletType;
    }

    public void setWaveletType(int waveletType) {
        this.waveletType = waveletType;
    }

    public double getSoftThresholdingPercentageOfZeros() {
        return softThresholdingPercentageOfZeros;
    }

    public void setSoftThresholdingPercentageOfZeros(double softThresholdingPercentageOfZeros) {
        this.softThresholdingPercentageOfZeros = softThresholdingPercentageOfZeros;
    }

    public int[] getWaveletsIterations() {
        return iters;
    }

    public void setWaveletsIterations(int[] iters) {
        this.iters = iters;
    }

    public void setWaveletsIterations(int onXaxis, int onYaxis, int onZaxis) {
        iters[0]=onXaxis;
        iters[1]=onYaxis;
        iters[2]=onZaxis;
    }
}
