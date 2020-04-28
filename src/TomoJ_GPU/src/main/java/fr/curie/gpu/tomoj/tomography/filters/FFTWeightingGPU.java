package fr.curie.gpu.tomoj.tomography.filters;

import fr.curie.tomoj.tomography.filters.FFTWeighting;
import ij.process.ImageProcessor;
import fr.curie.gpu.plugins.FFT_OCL;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.gpu.utils.GPUDevice;

/**
 * Extension of FFTWeighting to work on GPU
 */
public class FFTWeightingGPU extends FFTWeighting {
    FFT_OCL fft;
    int maskIndex;
    GPUDevice device;
    TiltSeries ts;

    public FFTWeightingGPU(GPUDevice device, TiltSeries ts, double diameter) {
        this.device = device;
        fft = new FFT_OCL();
        this.ts = ts;

        fft.initCL(new GPUDevice[]{device});
        fft.initOnGpu(device, ts.getProcessor());
        maskIndex = fft.createWeightingMask(device, (float) diameter, ts);
    }

    /**
     * change the diameter of weighting
     * @param diameter new diameter limit of wreighting
     */
    public void setDiameter(double diameter){
        device.releaseCL(device.getBuffer(maskIndex));
        maskIndex = fft.createWeightingMask(device, (float) diameter, ts);
    }

    /**
     * get mask index in the GPU (stored as Buffer)
     * @return   buffer index in the GPU device
     */
    public int getMaskIndex() {
        return maskIndex;
    }

    /**
     * perform the weighting on the given image (update pixels from gpu)
     * @param ip    the image to be weighted
     */
    public void weighting(ImageProcessor ip) {
        //System.out.println("weighting ip");
        fft.weighting(device, 0, ip, maskIndex);
        device.readFromImage2D(0, (float[]) ip.getPixels(), ip.getWidth(), ip.getHeight());
    }

    /**
     * perform weighting on given image already put in the gpu (no reading of result from gpu to cpu)
     * @param IndexGPUImage  index of image on gpu (as image2D)
     */
    public void weighting(int IndexGPUImage) {
        //System.out.println("weighting index");
        fft.weighting(device, IndexGPUImage, maskIndex, ts.getWidth(), ts.getHeight());
    }
}
