package fr.curie.tomoj.tomography.projectors;

import fr.curie.gpu.utils.GPUDevice;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.filters.FFTWeighting;

public class FistaProjector3D extends Projector{
    Projector workingProjector;
    TomoReconstruction2 previousIteration;
    double t=0;

    public FistaProjector3D(Projector workingProjector) {
        super(workingProjector.ts,workingProjector.rec,workingProjector.weightingFilter);
        this.workingProjector=workingProjector;
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
        System.out.println("FISTA");
        TomoReconstruction2 tmpRec= workingProjector.rec;
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
    }

    @Override
    public void startOfIteration() {
        if(previousIteration==null) previousIteration= workingProjector.rec.getCopy();
        else previousIteration.copyFrom(workingProjector.rec);
        workingProjector.startOfIteration();
    }

    @Override
    public float[] getProjection(int index) {
        return workingProjector.getProjection(index);
    }
}
