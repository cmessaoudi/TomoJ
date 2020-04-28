package fr.curie.tomoj.tomography.projectors;

import ij.plugin.GaussianBlur3D;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.filters.FFTWeighting;

/**
 * Created by cedric on 09/10/2014.
 */
public class DARTVoxelProjector3D extends VoxelProjector3D {
    double[] elementsValues;
    int radius;
    double unfixProbability=0.01;

    public DARTVoxelProjector3D(TiltSeries ts, TomoReconstruction2 rec, FFTWeighting filter, double[] elementsValues, int radius, double fixNonBoundary) {
        super(ts, rec, filter);
        this.elementsValues = elementsValues;
        this.radius = radius;
        this.unfixProbability=1.0-fixNonBoundary;
    }

    public void startOfIteration() {
        maskVolume = createBoundariesMask(elementsValues, radius);
        segment(rec, elementsValues, maskVolume);
    }

    public TomoReconstruction2 createBoundariesMask(double[] elementsValues, int radius) {
        TomoReconstruction2 tmp = new TomoReconstruction2(rec);
        segment(tmp, elementsValues, null);
        TomoReconstruction2 mask = new TomoReconstruction2(volumeWidth,volumeHeight,volumeDepth);
        for (int z = 0; z < volumeDepth; z++) {
            for (int y = 0; y < volumeHeight; y++) {
                for (int x = 0; x < volumeWidth; x++) {
                    //is boundary??
                    float[] n = tmp.getCubicNeightborhood(x, y, z, radius);
                    boolean test = true;
                    for (int p = 1; p < n.length; p++) {
                        if (n[p - 1] != n[p]) {
                            test = false;
                            break;
                        }
                    }
                    if(!test) test=Math.random()<unfixProbability;
                    mask.putPixel(x,y,z,(test)?1:0);
                }
            }
        }
        GaussianBlur3D.blur(mask,1,1,1);
        return mask;

    }

    public void segment(TomoReconstruction2 vol, double[] elementsValues, TomoReconstruction2 mask) {
        double[] thresholds = new double[elementsValues.length - 1];
        for (int i = 0; i < thresholds.length; i++) {
            thresholds[i] = (elementsValues[i] + elementsValues[i + 1]) / 2;
        }
        TomoReconstruction2 tmp = new TomoReconstruction2(vol);

        int yy;
        float val;
        for (int z = 0; z < volumeDepth; z++) {
            float[] pixs = (float[]) tmp.getImageStack().getPixels(z + 1);
            for (int y = 0; y < volumeHeight; y++) {
                yy = y * volumeWidth;
                for (int x = 0; x < volumeWidth; x++) {
                    val = pixs[yy + x];
                    for (int t = 0; t < thresholds.length; t++) {
                        if (mask != null && mask.getPixel(x,y,z)>0) {
                            if (val < thresholds[t]) {
                                vol.putPixel(x, y, z, elementsValues[t]);
                                break;
                            } else {
                                vol.putPixel(x, y, z, elementsValues[t + 1]);
                            }
                        }
                    }
                }

            }

        }

    }
}
