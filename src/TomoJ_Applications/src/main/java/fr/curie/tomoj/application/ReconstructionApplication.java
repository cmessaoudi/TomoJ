package fr.curie.tomoj.application;

import fr.curie.gpu.tomoj.tomography.ResolutionEstimationGPU;
import fr.curie.tomoj.tomography.ResolutionEstimation;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.gpu.utils.GPUDevice;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by cedric on 14/03/2017.
 */
public class ReconstructionApplication implements Application{
    protected int width, height, depth;
    protected double centerx,centery,centerz;
    protected boolean computeOnGPU=false;
    protected boolean rescaleData=true;
    protected boolean computeSSNR=false;
    protected boolean computeVSSNR=false;
    protected boolean computeFSC=false;
    protected Boolean[] use;
    protected GPUDevice[] gpuDevices;
    protected boolean fscOnly=false;
    protected TomoReconstruction2 rec;
    protected ResolutionEstimation resolutionComputation;
    protected String resultString = "";

    public void setSize(int width,int height,int depth){
        this.width=width;
        this.height=height;
        this.depth=depth;
    }

    public void setCenter(double centerx, double centery, double centerz){
        this.centerx=centerx;
        this.centery=centery;
        this.centerz=centerz;

    }

    public void setRescaleData(boolean value){ rescaleData=value;}
    public void setComputeOnGPU(boolean value, GPUDevice[] devices, Boolean[] use){
        computeOnGPU=value;
        this.use=use;
        this.gpuDevices=devices;
    }

     public void setResolutionEstimation(boolean ssnr, boolean vssnr, boolean fsc, boolean fscOnly){
        computeSSNR=ssnr;
        computeVSSNR=vssnr;
        computeFSC=fsc;
        this.fscOnly=fscOnly;
     }

     public void setReconstruction(TomoReconstruction2 rec){
        this.rec=rec;
     }

    public TomoReconstruction2 getReconstruction() {
        return rec;
    }

    public boolean run() {
        return false;
    }

    public void setParameters(Object... parameters) {

    }

    public String help() {
        return null;
    }

    public String name() {
        return null;
    }

    public ArrayList<Object> getParametersType() {
        return null;
    }

    public ArrayList<String> getParametersName() {
        return null;
    }

    public String getParametersValuesAsString() {
         String text="Reconstruction \nwidth:"+width+", height:"+height+", depth"+depth;
         text+="\ncenter ("+centerx+", "+centery+", "+centerz+")";
         if(rescaleData)text +="\nrescale data to fit size";
        if(computeSSNR) text+="\ncompute SSNR";
        if(computeSSNR && computeVSSNR) text+="\ncompute VSSNR";
        if(computeFSC)text+="\ncompute FSC";
        if(computeOnGPU) text +="\ncompute on GPU";

        return text;
    }

    public JPanel getJPanel() {
        return null;
    }

    public void setDisplayPreview(boolean display) {

    }


    public ArrayList<Object> getResults() {
        ArrayList<Object> result = new ArrayList<Object>();
        result.add(resultString);
        result.add(rec);
        if (computeSSNR) result.add(resolutionComputation.getSsnr());
        if (computeSSNR && computeVSSNR) result.add(resolutionComputation.getVSSNR());
        if (computeFSC) result.add(resolutionComputation.getFsc());
        return result;
    }
    public void interrupt() {
        if (resolutionComputation != null) resolutionComputation.interrupt();

    }

    public double getCompletion() {
        if (resolutionComputation != null) return resolutionComputation.getCompletion();
        return 0;
    }
}
