package fr.curie.gpu.tomoj.tomography.projectors;

import fr.curie.gpu.tomoj.tomography.filters.FFTWeightingGPU;
import fr.curie.gpu.utils.GPUDevice;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.projectors.Projector;
import ij.ImagePlus;
import ij.ImageStack;
import org.jocl.cl_image_format;

import static org.jocl.CL.CL_FLOAT;
import static org.jocl.CL.CL_R;

public class FistaProjector3DGPU extends ProjectorGPU {
    ProjectorGPU workingProjector;
    TomoReconstruction2 previousIteration;
    double t=0;

    public FistaProjector3DGPU(ProjectorGPU workingProjector) {
        super(workingProjector.getTiltSeries(),workingProjector.getReconstruction(),(FFTWeightingGPU)workingProjector.getWeightingFilter());

        System.out.println("init super done ");
        System.out.flush();
        this.workingProjector=workingProjector;
    }

    public GPUDevice getDevice() {
        return workingProjector.getDevice();
    }

    public void initForBP(int startY, int endY, int updateNb) {
        workingProjector.initForBP(startY, endY, updateNb);
    }

    public void initForIterative(int startY, int endY, int update, int kernelType, boolean errorVolume) {
        workingProjector.initForIterative(startY, endY, update, kernelType, errorVolume);
    }
    public float[] getProjection(int ystart,int yend){
        return workingProjector.getProjection(ystart, yend);
    }

    public void updateFromGPUBuffer(GPUDevice device, int volIndex, ImagePlus volToBeUpdated, int startY, int endY, int YoffsetStart, int YoffsetEnd) {
        workingProjector.updateFromGPUBuffer(device, volIndex, volToBeUpdated, startY, endY, YoffsetStart, YoffsetEnd);
    }

    public void updateFromGPU(int startY, int endY, int usableStartY, int usableEndY) {
        workingProjector.updateFromGPU(startY, endY, usableStartY, usableEndY);
    }

    public void releaseCL_Memory() {
        workingProjector.releaseCL_Memory();
    }

    @Override
    public void addProjection(int index) {
        workingProjector.addProjection(index);
    }

    @Override
    public void clearAllProjections() {
        workingProjector.clearAllProjections();
    }

    @Override
    public void backproject(int startY, int endY) {
        workingProjector.backproject(startY,endY);
    }

    @Override
    public void project(int startY, int endY) {
        workingProjector.project(startY, endY);
    }

    @Override
    public double[] projectAndDifference(double factor, int startY, int endY) {
        return workingProjector.projectAndDifference(factor, startY, endY);
    }

    @Override
    public void endOfIteration() {
        workingProjector.endOfIteration();
        System.out.println("FISTA GPU");
        workingProjector.updateFromGPU(currentStartY,currentEndY);
        TomoReconstruction2 tmpRec= workingProjector.getReconstruction();
        double t_1=t;
        t=(1+Math.sqrt(1+4*t*t))/2;
        double factor=(t_1-1)/t;
        double val;
        for(int z=0;z<tmpRec.getSizez();z++){
            for(int y=0;y<tmpRec.getHeight();y++){
                for(int x=0;x<tmpRec.getWidth();x++){
                    val=factor*(tmpRec.getPixel(x,y,z)-previousIteration.getPixel(x,y,z));
                    tmpRec.addToPixel(x,y,z,val);
                }
            }
        }
        workingProjector.copyInGPUBuffer(currentStartY,currentEndY);
    }

    @Override
    public void startOfIteration() {
        if(currentStartY>=0) workingProjector.updateFromGPU(currentStartY,currentEndY);
        if(previousIteration==null) previousIteration= workingProjector.getReconstruction().getCopy();
        else previousIteration.copyFrom(workingProjector.getReconstruction());
        workingProjector.startOfIteration();
    }

    @Override
    public float[] getProjection(int index) {
        return workingProjector.getProjection(index);
    }
}
