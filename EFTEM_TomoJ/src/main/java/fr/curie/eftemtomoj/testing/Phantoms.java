/*
* Copyright 2010 Nick Aschman.
*/

package fr.curie.eftemtomoj.testing;

import fr.curie.eftemtomoj.TiltSeries;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.Arrays;
import java.util.Random;

/**
 * @author Nick Aschman
 */
public class Phantoms {

    public static final int POWER_FUNCTION = 0;
    public static final int EXP_FUNCTION = 1;
    public static final int QUADRATIC_FUNCTION = 2;
    public static final int LOGPOLY2_FUNCTION = 3;

    public static final int SHARP_PEAK = 100;
    public static final int BROAD_PEAK = 101;

    // Image properties
    public static final int width = 512;
    public static final int height = 512;
    public static final int spotWidth = 32;
    public static final int spotHeight = 32;
    public static final int hMargin = 44;
    public static final int vMargin = 44;
    public static final int hGap = 24;
    public static final int vGap = 24;
    public static final int spotRows = 8;
    public static final int spotCols = 8;

    // Data properties
    final float[] energies;
    final float slitWidth;
    final float exposureTime;
    final int functionType;

    private Phantoms(int funcType) {
        energies = new float[]{10, 20, 30, 40, 50, 60};
        slitWidth = 10;
        exposureTime = 1;
        functionType = funcType;
    }

    private void drawSpots(ImageProcessor ip, int r0, int c0, double[] values) {
        drawSpots(ip, r0, c0, values, false);
    }

    private void drawSpots(ImageProcessor ip, int r0, int c0, double[] values, boolean add) {
        int i, vOff, hOff;
        double val;

        i = 0;
        for (int r = r0; r < spotRows; r++) {
            vOff = vMargin + r * (spotHeight + vGap);

            for (int c = c0; c < spotCols; c++) {
                hOff = hMargin + c * (spotWidth + hGap);

                for (int x = 0; x < spotWidth; x++) {
                    for (int y = 0; y < spotHeight; y++) {
                        val = values[i];

                        if (add)
                            val += ip.getf(hOff + x, vOff + y);

                        ip.setf(hOff + x, vOff + y, (float) val);
                    }
                }
                i++;

                if (i == values.length) {
                    return;
                }
            }
        }
    }

    public static TiltSeries[][] generateSeries(int funcType, int peakType, double noiselevel) {

        Phantoms gen = new Phantoms(funcType);

        ImageStack stack = new ImageStack(width, height);
        ImageStack noisyStack = new ImageStack(width, height);
        FloatProcessor ip, nip, mip;

        Random rand = new Random();

        mip = new FloatProcessor(width, height);
        for (int j = 0; j < mip.getPixelCount(); j++)
            mip.setf(j, 0.0f);

        double b, d, s, step, noise = 0.0, min = Double.NaN;
        double[] gradient = new double[24];

        System.out.println("=== GENERATING PHANTOM SERIES ============================================================");
        System.out.println(String.format("%1$-20s%2$-20s", "Energy shifts: ", Arrays.toString(gen.energies)));
        System.out.println(String.format("%1$-20s%2$-20d", "Image width: ", width));
        System.out.println(String.format("%1$-20s%2$-20d", "Image height: ", height));
        System.out.println("");

        // Generate images
        for (int i = 0; i < gen.energies.length; i++) {
            ip = new FloatProcessor(width, height);

            System.out.println("--- EFI " + (i + 1) + "--------------------------------------------------------------------");

            // Fill background
            b = gen.f(gen.energies[i]);
            for (int j = 0; j < ip.getPixelCount(); j++)
                ip.setf(j, (float) b);
            System.out.println(String.format("%1$-20s%2$-20.2f", "Background intensity: ", b));
            System.out.println("");

            // Draw density spots
            System.out.println(String.format("%1$-40s", "Density gradient intensities:"));
            d = 0.5 * b;
            step = (b - d) / (double) gradient.length;
            for (int g = 0; g < gradient.length; g++) {
                gradient[g] = d + g * step;
                System.out.println(String.format("%1$-20d%2$-20.2f", (g + 1), gradient[g]));
            }
            gen.drawSpots(ip, 0, 0, gradient);
            System.out.println("");

            // Draw sharp peak spots
            if (peakType == SHARP_PEAK && i == 2) {
                System.out.println(String.format("%1$-40s", "Sharp peak gradient intensities:"));
                s = 2.0 * b;
                step = (s - b) / (double) gradient.length;
                noise = s / noiselevel;
                for (int g = 0; g < gradient.length; g++) {
                    gradient[g] = s - g * step;
                    System.out.println(String.format("%1$-20d%2$-20.2f", (g + 1), gradient[g]));
                }
                gen.drawSpots(ip, 4, 0, gradient);
                System.out.println("");

                // Create map
                System.out.println(String.format("%1$-40s", "Sharp peak map gradient intensities:"));
                s -= b;
                for (int g = 0; g < gradient.length; g++) {
                    gradient[g] = s - g * step;
                    System.out.println(String.format("%1$-20d%2$-20.2f", (g + 1), gradient[g]));
                }
                gen.drawSpots(mip, 4, 0, gradient);
                System.out.println("");
            }

            // Draw broad peak spots
            if (peakType == BROAD_PEAK) {
                if (i == 2) {
                    System.out.println(String.format("%1$-40s", "Broad peak (i) gradient intensities:"));
                    s = 1.4 * b;
                    step = (s - b) / (double) gradient.length;
                    noise = s / noiselevel;
                    for (int g = 0; g < gradient.length; g++) {
                        gradient[g] = s - g * step;
                        System.out.println(String.format("%1$-20d%2$-20.2f", (g + 1), gradient[g]));
                    }
                    gen.drawSpots(ip, 4, 0, gradient);
                    System.out.println("");

                    // Create map
                    System.out.println(String.format("%1$-40s", "Broad peak map (i) gradient intensities:"));
                    s -= b;
                    for (int g = 0; g < gradient.length; g++) {
                        gradient[g] = s - g * step;
                        System.out.println(String.format("%1$-20d%2$-20.2f", (g + 1), gradient[g]));
                    }
                    gen.drawSpots(mip, 4, 0, gradient, true);
                } else if (i == 3) {
                    System.out.println(String.format("%1$-40s", "Broad peak (ii) gradient intensities:"));
                    s = 1.3 * b;
                    step = (s - b) / (double) gradient.length;
                    noise = s / noiselevel;
                    for (int g = 0; g < gradient.length; g++) {
                        gradient[g] = s - g * step;
                        System.out.println(String.format("%1$-20d%2$-20.2f", (g + 1), gradient[g]));
                    }
                    gen.drawSpots(ip, 4, 0, gradient);
                    System.out.println("");

                    // Create map
                    System.out.println(String.format("%1$-40s", "Broad peak map (ii) gradient intensities:"));
                    s -= b;
                    for (int g = 0; g < gradient.length; g++) {
                        gradient[g] = s - g * step;
                        System.out.println(String.format("%1$-20d%2$-20.2f", (g + 1), gradient[g]));
                    }
                    gen.drawSpots(mip, 4, 0, gradient, true);
                    System.out.println("");
                }
            }

            stack.addSlice(gen.energies[i] + " eV", ip);
        }
        System.out.println("");

        System.out.println(String.format("%1$-20s%2$-20.2f", "Noise SD: ", noise));
        System.out.println("");

        // Generate noisy versions
        for (int i = 0; i < gen.energies.length; i++) {
            ip = (FloatProcessor) stack.getProcessor(i + 1).duplicate();

            for (int j = 0; j < ip.getPixelCount(); j++) {
                s = ip.getf(j) + noise * rand.nextGaussian();
                ip.setf(j, (float) s);

                if (Double.isNaN(min) || s < min) min = s;
            }

            noisyStack.addSlice(gen.energies[i] + " eV", ip);
        }

        if (min < 0) {

            // Adjust values to >= 0
            for (int i = 0; i < gen.energies.length; i++) {
                ip = (FloatProcessor) stack.getProcessor(i + 1);
                nip = (FloatProcessor) noisyStack.getProcessor(i + 1);

                for (int j = 0; j < ip.getPixelCount(); j++) {
                    ip.setf(j, (float) (ip.getf(j) - min));
                    nip.setf(j, (float) (nip.getf(j) - min));
                }
            }

            System.out.println(String.format("%1$-20s+%2$-19.2f", "Value adjustment: ", -min));
            System.out.println("");

        }

        IJ.save(new ImagePlus("Phantom series + noise (FT " + funcType + ")", noisyStack), "phseries.tif");
        IJ.save(new ImagePlus("Phantom series - noise (FT " + funcType + ")", stack), "phseries-nonoise.tif");

        if (peakType == SHARP_PEAK)
            IJ.save(new ImagePlus("Phantom sharp peak map (FT " + funcType + ")", mip), "phmap-sharp.tif");
        else if (peakType == BROAD_PEAK)
            IJ.save(new ImagePlus("Phantom broad peak map (FT " + funcType + ")", mip), "phmap-broad.tif");

        // Create tilt series
        TiltSeries[] series = new TiltSeries[stack.getSize()];

        for (int i = 0; i < stack.getSize(); i++) {
            series[i] = new TiltSeries(width, height);
            series[i].setEnergyShift(gen.energies[i]);
            series[i].setExposureTime(gen.exposureTime);
            series[i].setSlitWidth(gen.slitWidth);
            series[i].setTitle(gen.energies[i] + "eV");
            series[i].addSlice("", stack.getProcessor(i + 1));
        }

        TiltSeries[] noisySeries = new TiltSeries[noisyStack.getSize()];

        for (int i = 0; i < noisyStack.getSize(); i++) {
            noisySeries[i] = new TiltSeries(width, height);
            noisySeries[i].setEnergyShift(gen.energies[i]);
            noisySeries[i].setExposureTime(gen.exposureTime);
            noisySeries[i].setSlitWidth(gen.slitWidth);
            noisySeries[i].setTitle(gen.energies[i] + "eV");
            noisySeries[i].addSlice("", noisyStack.getProcessor(i + 1));
        }

        return new TiltSeries[][]{series, noisySeries};
    }


    private double f(double x) {
        switch (functionType) {
            case POWER_FUNCTION:
            default:
                return 3200.0 * Math.pow(x, -0.5);

            case EXP_FUNCTION:
                return 3200.0 * Math.exp(-0.05 * x);
        }
    }

    private void create3DPhantoms() {

        int w = 256;
        int h = 256;
        int d = 256;

        double[] bg = new double[]{1940.90, 1177.21, 714.02, 433.07, 262.67, 159.32};
        double[] ds = new double[]{970.45, 588.61, 357.01, 216.54, 131.34, 79.66};
        double[] sp = new double[]{1940.90, 1177.21, 1428.03, 433.07, 262.67, 159.32};

        // Adjust to 8bit (divide by 10)
        for (int i = 0; i < bg.length; i++) {
            bg[i] = bg[i] / 10;
            ds[i] = ds[i] / 10;
            sp[i] = sp[i] / 10;
        }

        ImageStack stack;
        ByteProcessor fp;

        for (int i = 0; i < energies.length; i++) {
            stack = new ImageStack(w, h);

            for (int z = 0; z < d; z++) {
                fp = new ByteProcessor(w, h);

                for (int j = 0; j < fp.getPixelCount(); j++) {
                    fp.setf(j, (float) bg[i]);
                }

                if (z >= 48 && z < 80) {
                    for (int x = 48; x < 80; x++) {
                        for (int y = 48; y < 80; y++) {
                            fp.setf(y, x, (float) ds[i]);
                        }
                    }
                }

                if (z >= 176 && z < 208) {
                    for (int x = 176; x < 208; x++) {
                        for (int y = 176; y < 208; y++) {
                            fp.setf(y, x, (float) ds[i]);
                        }
                    }
                }

                if (z >= 112 && z < 144) {
                    for (int x = 112; x < 144; x++) {
                        for (int y = 112; y < 144; y++) {
                            fp.setf(y, x, (float) sp[i]);
                        }
                    }
                }

                stack.addSlice("", fp);
            }

            IJ.save(new ImagePlus("", stack), "ph3d-" + energies[i] + "eV.tif");
        }
    }
}
