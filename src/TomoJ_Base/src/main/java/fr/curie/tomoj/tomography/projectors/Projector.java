package fr.curie.tomoj.tomography.projectors;

import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.filters.FFTWeighting;
import fr.curie.utils.MatrixUtils;

/**
 * Created by cedric on 18/09/2014.
 */
public abstract class Projector {
    protected double[] projectionCenters;
    protected double[] volumeCenters;
    protected int projectionWidth;
    protected int projectionHeight;
    protected int volumeWidth;
    protected int volumeHeight;
    protected int volumeDepth;
    protected FFTWeighting weightingFilter;
    protected TiltSeries ts;
    protected TomoReconstruction2 rec;
    protected DoubleMatrix2D globalDeformation;
    protected DoubleMatrix2D globalDeformationInv;
    DoubleMatrix2D globalAli;
    protected double completion = 0;

    public Projector(TiltSeries ts, TomoReconstruction2 rec, FFTWeighting weightingFilter) {
        setProjectionCenter(ts);
        setVolumeCenters(rec);
        setProjectionDimensions(ts);
        setVolumeDimensions(rec);
        this.weightingFilter = weightingFilter;
        this.ts = ts;
        this.rec = rec;
        setVolumeCenters(rec);
        //updateVolumeCenters(rec,ts);
    }

    public void setReconstruction(TomoReconstruction2 rec) {
        this.rec = rec;
        setVolumeCenters(rec);
        setVolumeDimensions(rec);
    }

    public void setWeightingFilter(FFTWeighting weightingFilter) {
        this.weightingFilter = weightingFilter;
    }

    public void setProjectionCenter(TiltSeries ts) {
        if (projectionCenters == null) projectionCenters = new double[4];
        projectionCenters[0] = ts.getCenterX();
        projectionCenters[1] = ts.getCenterY();
        projectionCenters[2] = ts.getProjectionCenterX();
        projectionCenters[3] = ts.getProjectionCenterY();
    }

    public void setVolumeCenters(TomoReconstruction2 rec) {
        if (volumeCenters == null) volumeCenters = new double[3];
        volumeCenters[0] = rec.getCenterx();
        volumeCenters[1] = rec.getCentery();
        volumeCenters[2] = rec.getCenterz();
    }

    /*public void updateVolumeCenters(TomoReconstruction2 rec, TiltSeries ts) {
        if (volumeCenters == null) volumeCenters = new double[3];
        double[] globalAli=ts.getGlobalOrientation();
        if(globalAli==null) {
            volumeCenters[0] = rec.getCenterx();
            volumeCenters[1] = rec.getCentery();
            volumeCenters[2] = rec.getCenterz();
        }else{
            System.out.println("there is a shift of volume center");
            volumeCenters[0] = rec.getCenterx() - globalAli[3];
            volumeCenters[1] = rec.getCentery() - globalAli[4];
            volumeCenters[2] = rec.getCenterz() - globalAli[5];
        }
    } */

    public void setProjectionDimensions(TiltSeries ts) {
        projectionWidth = ts.getWidth();
        projectionHeight = ts.getHeight();
    }

    public void setVolumeDimensions(TomoReconstruction2 rec) {
        volumeWidth = rec.getWidth();
        volumeHeight = rec.getHeight();
        volumeDepth = rec.getSizez();
    }

    /**
     * set the position of center of tilt axis on projection
     *
     * @param pcx
     * @param pcy
     */
    public void setProjectionCenter(double pcx, double pcy) {
        projectionCenters[2] = pcx;
        projectionCenters[3] = pcy;
    }

    public void setVolumeCenters(double cx, double cy, double cz) {
        volumeCenters[0] = cx;
        volumeCenters[1] = cy;
        volumeCenters[2] = cz;
    }

    public FFTWeighting getWeightingFilter() {
        return weightingFilter;
    }

    public TiltSeries getTiltSeries() {
        return ts;
    }

    public TomoReconstruction2 getReconstruction() {
        return rec;
    }

    public double getCompletion() {
        return completion;
    }

    public boolean isWeighting() {
        return weightingFilter != null;
    }

    public abstract void addProjection(int index);

    public abstract void clearAllProjections();

    public void backproject() {
        backproject(0, volumeHeight);
    }

    public abstract void backproject(int startY, int endY);

    public void project() {
        project(0, volumeHeight);
    }

    public abstract void project(int startY, int endY);

    public double[] projectAndDifference(double factor) {
        return projectAndDifference(factor, 0, volumeHeight);
    }

    public abstract double[] projectAndDifference(double factor, int startY, int endY);

    public abstract void endOfIteration();

    public abstract void startOfIteration();

    public abstract float[] getProjection(int index);


}
