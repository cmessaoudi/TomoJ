package fr.curie.tomoj.align;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import fr.curie.tomoj.tomography.TiltSeries;

import java.awt.geom.AffineTransform;
import java.io.IOException;

public interface Alignment {
    public AffineTransform getTransform(int index);

    public AffineTransform[] getTransforms();

    public void setZeroIndex(int zeroIndex);

    public void removeTransform(int index);

    public AffineTransform getZeroTransform();

    //public void setZeroTransform(AffineTransform transform);

    public void resetEulerMatrices();

    public DoubleMatrix2D getEulerMatrix(int index);

    public void setEulerMatrix(int index, DoubleMatrix2D eulerMatrix);
    public void loadFromFile(String path, double binning,  boolean... options) throws IOException;

    public void saveToFile(String path, boolean... options) throws IOException;

    public float[] applyTransformOnImage(TiltSeries ts, int index);
}
