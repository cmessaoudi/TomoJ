package fr.curie.gpu.tomoj.tomography.projectors;

import ij.IJ;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.filters.FFTWeighting;
import fr.curie.gpu.tomoj.tomography.filters.FFTWeightingGPU;
import fr.curie.gpu.utils.GPUDevice;

import java.util.Arrays;

/**
 * Created by cedric on 25/11/2014.
 */
public class BgARTVoxelProjector3DGPU extends VoxelProjector3DGPU {
    double k;
    boolean darkBG = true;
    boolean doMedian = true;
    double bgValue = Double.NaN;
    public BgARTVoxelProjector3DGPU(TiltSeries ts, TomoReconstruction2 rec, GPUDevice device, FFTWeightingGPU weightingFilter, double k, boolean darkBG, boolean doMedian){
        super(ts,rec,device,weightingFilter);
        this.k = k;
        this.darkBG = darkBG;
        this.doMedian = doMedian;
        //collapseFilter();
    }

    public double collapseFilter() {
            //create histogram
            ImageStatistics stats = new StackStatistics(rec);

            double avg = stats.mean;
            double sigma = stats.stdDev;
            double min = stats.min;
            double max = stats.max;

            //estimate sigma on "negative" value
            int[] hist = stats.histogram;
            double histmin = stats.histMin;
            double histmax = stats.histMax;
            double binSize = (histmax - histmin) / stats.nBins;
            System.out.println("histmin=" + histmin + " , histmax=" + histmax + " , binsize=" + binSize + " , sigmaIJ=" + sigma);
            IJ.log("histmin=" + histmin + " , histmax=" + histmax + " , binsize=" + binSize + " , sigmaIJ=" + sigma);
            double max_index_value = 0;
            int max_index = 0;
            for (int i = 0; i < hist.length; i++) {
                if (hist[i] > max_index_value) {
                    max_index_value = hist[i];
                    max_index = i;
                }
            }
            if (Double.isNaN(bgValue)) {
                max_index_value = histmin + max_index * binSize;
            } else max_index_value = bgValue;
            //System.out.println("max index="+max_index+" , max="+max_index_value);
            sigma = 0;
            int count = 0;
            for (int i = 0; i < max_index; i++) {
                //for (int i = 0; i < stats.nBins; i++) {
                sigma += (histmin + i * binSize - max_index_value) * (histmin + i * binSize - max_index_value) * hist[i];
                count += hist[i];
            }
            sigma = Math.sqrt(sigma / count);

            double th1 = max_index_value - k * sigma;
            double th2 = max_index_value + k * sigma;
            //double th2=max_index_value;
            //System.out.println("collapsing filter : max_index_value="+max_index_value+", sigma="+sigma+", th1="+th1+", th2="+th2);
            System.out.println("collapsing filter : max_index_value=" + max_index_value + ", sigma=" + sigma + ", th=" + th2);
            IJ.log("collapsing filter : max_index_value=" + max_index_value + ", sigma=" + sigma + ", th=" + th2);
            // System.out.println("hist "+displayHistogram(hist,histmin,binSize));
            for (int z = 0; z < volumeDepth; z++) {
                for (int y = 0; y < volumeHeight; y++) {
                    for (int x = 0; x < volumeWidth; x++) {
                        double val = rec.getPixel(x, y, z);
                        //if(val<=th1)  putPixel(x,y,z,min);
                        //if(val>th1&&val<th2) putPixel(x,y,z,val/3);
                        if (darkBG && val < th2) rec.putPixel(x, y, z, max_index_value);
                        if (!darkBG && val > th1) rec.putPixel(x, y, z, max_index_value);
                        //if(val>=th2) putPixel(x,y,z,max);

                    }
                }
            }
            return max_index_value;
        }
    public void endOfIteration() {
        //updateFromGPU(0,rec.getHeight());
        //collapseFilter();
        //volumeChanged=true;
        }

        public void startOfIteration() {
//            if (doMedian) {
//                medianFilter3Dcross();
//                copyInGPUBuffer(device, currentWorkingBuffer, rec, 0, rec.getHeight());
//                volumeChanged = true;
//            }
        }

        public void medianFilter3Dcross() {
            TomoReconstruction2 tmp = new TomoReconstruction2(rec);
            int radius = 1;
            int count;
            float[] n = new float[7];
            int yy;
            for (int z = 0; z < volumeDepth; z++) {
                float[] pixs = (float[]) tmp.getImageStack().getPixels(z + 1);
                float[] pixsmin = (z > 0) ? (float[]) tmp.getImageStack().getPixels(z) : null;
                float[] pixsmax = (z < volumeDepth - 1) ? (float[]) tmp.getImageStack().getPixels(z + 2) : null;
                for (int y = 0; y < volumeHeight; y++) {
                    yy = y * volumeWidth;
                    for (int x = 0; x < volumeWidth; x++) {
                        n[0] = (pixsmin != null) ? pixsmin[x + yy] : 0;
                        n[1] = (pixsmax != null) ? pixsmax[x + yy] : 0;
                        n[2] = pixs[x + yy];
                        n[3] = (x + yy + 1 < pixs.length) ? pixs[x + yy + 1] : 0;
                        n[4] = (x + yy - 1 > 0) ? pixs[x + yy - 1] : 0;
                        n[5] = (x + yy - volumeWidth > 0) ? pixs[x + yy - volumeWidth] : 0;
                        n[6] = (x + yy + volumeWidth < pixs.length) ? pixs[x + yy + volumeWidth] : 0;
                        Arrays.sort(n);
                        rec.putPixel(x, y, z, n[3]);
                    }
                }
            }
        }
}
