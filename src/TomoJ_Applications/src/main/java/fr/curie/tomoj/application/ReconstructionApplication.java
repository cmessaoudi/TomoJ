package fr.curie.tomoj.application;

import fr.curie.gpu.tomoj.tomography.ResolutionEstimationGPU;
import fr.curie.tomoj.tomography.ReconstructionParameters;
import fr.curie.tomoj.tomography.ResolutionEstimation;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.gpu.utils.GPUDevice;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by cedric on 14/03/2017.
 */
public class ReconstructionApplication implements Application{
    protected int width, height, depth;
    protected double centerx,centery,centerz;
    protected boolean computeOnGPU=false;
    protected boolean rescaleData=false;
    protected boolean computeSSNR=false;
    protected boolean computeVSSNR=false;
    protected boolean computeFSC=false;
    protected Boolean[] use;
    protected GPUDevice[] gpuDevices;
    protected boolean fscOnly=false;
    protected TomoReconstruction2 rec;
    protected ResolutionEstimation resolutionComputation;
    protected String resultString = "";
    protected ReconstructionParameters recParams=null;

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
        for (int index = 0; index < parameters.length; index++) {
            if(parameters[index]instanceof String) {
                if (((String) parameters[index]).toLowerCase().equals("width")) {
                    if (parameters[index + 1] instanceof String)
                        width = Integer.parseInt((String) parameters[index + 1]);
                    else width = (Integer) parameters[index + 1];
                    index += 1;
                } else if (((String) parameters[index]).toLowerCase().equals("height")) {
                    if (parameters[index + 1] instanceof String)
                        height = Integer.parseInt((String) parameters[index + 1]);
                    else height = (Integer) parameters[index + 1];
                    index += 1;
                } else if (((String) parameters[index]).toLowerCase().equals("depth")) {
                    if (parameters[index + 1] instanceof String)
                        depth = Integer.parseInt((String) parameters[index + 1]);
                    else depth = (Integer) parameters[index + 1];
                    index += 1;
                } else if (((String) parameters[index]).toLowerCase().equals("size")) {
                    if (parameters[index + 1] instanceof String)
                        width = Integer.parseInt((String) parameters[index + 1]);
                    else width = (Integer) parameters[index + 1];
                    if (parameters[index + 1] instanceof String)
                        height = Integer.parseInt((String) parameters[index + 2]);
                    else height = (Integer) parameters[index + 2];
                    if (parameters[index + 1] instanceof String)
                        depth = Integer.parseInt((String) parameters[index + 3]);
                    else depth = (Integer) parameters[index + 3];
                    index += 3;
                } else if (((String) parameters[index]).toLowerCase().equals("center")) {
                    if (parameters[index + 1] instanceof String)
                        centerx = Integer.parseInt((String) parameters[index + 1]);
                    else centerx = (Integer) parameters[index + 1];
                    if (parameters[index + 1] instanceof String)
                        centery = Integer.parseInt((String) parameters[index + 2]);
                    else centery = (Integer) parameters[index + 2];
                    if (parameters[index + 1] instanceof String)
                        centerz = Integer.parseInt((String) parameters[index + 3]);
                    else centerz = (Integer) parameters[index + 3];
                    index += 3;
                } else if (((String) parameters[index]).toLowerCase().equals("rescale")) {
                    rescaleData = true;
                } else if (((String) parameters[index]).toLowerCase().equals("ssnr")) {
                    computeSSNR = true;
                } else if (((String) parameters[index]).toLowerCase().equals("fsc")) {
                    computeFSC = true;
                } else if (((String) parameters[index]).toLowerCase().equals("gpu")) {
                    computeOnGPU = true;
                    if(gpuDevices==null){
                        gpuDevices=GPUDevice.getGPUDevices();
                        use=new Boolean[gpuDevices.length];
                        Arrays.fill(use,false);
                    }
                    int useIndex=0;
                    if (parameters[index + 1] instanceof String)
                        useIndex = Integer.parseInt((String) parameters[index + 1]);
                    else useIndex = (Integer) parameters[index + 1];
                    use[useIndex]=true;
                    index+=1;
                }
            }
        }
    }

    public static String help() {
        return "reconstruction \n" +
                "parameters that can be given\n" +
                "width value: width of reconstruction\n" +
                "height value: height of reconstruction\n" +
                "depth value: depth of reconstruction\n" +
                "size value value value: give the size of reconstruction in the order (x, y, z)\n" +
                "center value value value: give the centers of reconstruction in the order (x, y, z) by default the value corresponds to (size-1)/2" +
                "rescale: if width or height of reconstruction is different from tilt series, rescales projection images\n" +
                "gpu index: use gpu for computation. index corresponds to gpu number (use graphical version to see what is detected and order). Sets this options many times to use several gpus\n" +
                "ssnr: computes ssnr\n" +
                "fsc: computes fsc" ;
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
         String text="\nwidth:"+width+", height:"+height+", depth:"+depth;
         text+="\ncenter ("+centerx+", "+centery+", "+centerz+")";
         if(rescaleData)text +="\nrescale data to fit size";
        if(computeSSNR) text+="\ncompute SSNR";
        if(computeSSNR && computeVSSNR) text+="\ncompute VSSNR";
        if(computeFSC)text+="\ncompute FSC";
        if(computeOnGPU) text +="\ncompute on GPU";
        //if(recParams!=null) text+= "\n"+recParams.getParametersAsString();

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
