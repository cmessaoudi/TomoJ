package fr.curie.tomoj.tomography.filters;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix1D;
import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix1D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;

/**
 * allow the weighting as desribed in Radermacher 1992
 */
public class FFTWeighting {
    protected double[] cost, sint, cosp, sinp;
    protected double diameter;
    protected float[] mask;

    public FFTWeighting() {
    }

    public FFTWeighting(TiltSeries ts, double diameter) {
        mask = createWeightingMask(ts, diameter);
    }

    /** create the mask
     *
     * @param ts    tilts series to get information from it (tilat axis orientation, existing tilt angles, size...
     * @param diameter     distance of improvement weighting  to prevent improvement of noise
     * @return    the mask 2D as a 1D array
     */
    protected float[] createWeightingMask(TiltSeries ts, double diameter) {
        float[] mask = new float[ts.getWidth() * ts.getHeight()];
        int sx = ts.getWidth();
        int sy = ts.getHeight();
        double cx = (sx) / 2.0;
        double cy = (sy) / 2.0;
        initWeighting(ts);
        //double cosp=Math.cos(Math.toRadians(ts.getTiltAxis()));
        //double sinp=Math.sin(Math.toRadians(ts.getTiltAxis()));
        for (int j = 0; j < sy; j++) {
            int jj = j * sx;
            double cj = (j > cy) ? j - sy : j;
            for (int i = 0; i < sx; i++) {
                double ci = (i > cx) ? i - sx : i;
                double weight = 0;
                for (int w = 0; w < ts.getImageStackSize(); w++) {
                    double zj = (ci * sint[w] * cosp[w] + cj * sint[w] * sinp[w]) / sx;
                    double value = diameter * Math.PI * zj;
                    if (value == 0) {
                        value = 1;
                    } else {
                        value = Math.sin(value) / value;
                    }
                    weight += value;
                }
                if (weight < 0.005) {
                    weight = 0.005f;
                }
                weight *= diameter;
                mask[jj + i] = (float) (1 / weight);

            }
        }
        return mask;
    }

    private void initWeighting(TiltSeries ts) {
        System.out.println("initialising weighting");
        int nbproj = ts.getImageStackSize();
        cost = new double[nbproj];
        sint = new double[nbproj];
        cosp = new double[nbproj];
        sinp = new double[nbproj];
        for (int i = 0; i < nbproj; i++) {
            //cost[i] = Math.cos(Math.toRadians(ts.getTiltAngle(i)));
            sint[i] = Math.sin(Math.toRadians(ts.getTiltAngle(i)));
            cosp[i] = Math.cos(Math.toRadians(ts.getTiltAxis(i)));
            sinp[i] = Math.sin(Math.toRadians(ts.getTiltAxis(i)));
        }

    }

    /**
     * weight the image using weighting mask. Do FFT, weighting of frequencies and inverse FFT
     * @param ip    the image to be weighted
     */
    public void weighting(ImageProcessor ip) {
        if(ip.getHeight()==1) {
            weighting1D(ip);
            return;
        }
        float[] pixs = (float[]) ip.getPixels();
        int sx = ip.getWidth();
        int sy = ip.getHeight();
        DenseFloatMatrix2D H1 = new DenseFloatMatrix2D(sy, sx);
        H1.assign(pixs);
        DenseFComplexMatrix2D fft = H1.getFft2();
        float[] fft1 = fft.elements();
        for (int i = 0; i < mask.length; i++) {
            fft1[(i) * 2] *= mask[i];
            fft1[(i) * 2 + 1] *= mask[i];
        }


        fft.ifft2(true);
        fft1 = fft.elements();

        for (int t = 0; t < pixs.length; t++) {
            pixs[t] = fft1[t * 2];
        }

    }

    public void weighting1D(ImageProcessor ip){

        float[] pixs = (float[]) ip.getPixels();
        int sx = ip.getWidth();
        DenseFloatMatrix1D H1 = new DenseFloatMatrix1D(sx);
        H1.assign(pixs);
        DenseFComplexMatrix1D fft = H1.getFft();
        float[] fft1 = fft.elements();
        for (int i = 0; i < mask.length; i++) {
            fft1[(i) * 2] *= mask[i];
            fft1[(i) * 2 + 1] *= mask[i];
        }


        fft.ifft(true);
        fft1 = fft.elements();

        for (int t = 0; t < pixs.length; t++) {
            pixs[t] = fft1[t * 2];
        }
    }
}
