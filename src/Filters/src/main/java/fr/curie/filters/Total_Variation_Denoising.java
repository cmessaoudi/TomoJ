package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import junit.framework.TestCase;

import java.awt.*;
import java.util.Arrays;

/**
 * This denoising method is based on total-variation, originally proposed by
 * Rudin, Osher and Fatemi. In this particular case fixed point iteration
 * is utilized.
 * <p/>
 * For the included image, a fairly good result is obtained by using a
 * theta value around 12-16. A possible addition would be to analyze the
 * residual with an entropy function and add back areas that have a lower
 * entropy, i.e. there are some correlation between the surrounding pixels.
 * <p/>
 * Based on the Matlab code by Philippe Magiera & Carl Löndahl:
 * http://www.mathworks.com/matlabcentral/fileexchange/22410-rof-denoising-algorithm
 */
public class Total_Variation_Denoising extends TestCase implements ExtendedPlugInFilter, DialogListener {
    protected ImagePlus image;
    int flags = DOES_32 + PARALLELIZE_STACKS;
    PlugInFilterRunner pfr;
    boolean preview;
    float theta = 25;
    float g = 1;
    float dt = 0.25f;
    int ite = 5;
    boolean version = true;

    public static void denoise(ImageProcessor ip, float theta) {
        denoise(ip, theta, 1, 0.25f, 5);
    }

    /**
     * This method gets called by ImageJ / Fiji to determine
     * whether the current image is of an appropriate type.
     *
     * @param arg   can be specified in plugins.config
     * @param image is the currently opened image
     */
    public int setup(String arg, ImagePlus image) {
        this.image = image;
        return flags;
    }

    /**
     * This method is run when the current image was accepted.
     *
     * @param ip is the current slice (typically, plugins use
     *           the ImagePlus set above instead).
     */
    public void run(ImageProcessor ip) {
        /*if(image.getImageStack()!=null){
            denoise(image.getImageStack(),theta,g,dt,ite);
            return;
        }*/
        //ImageStack stack = image.getStack();
        //for (int slice = 1; slice <= stack.getSize(); slice++)
        //denoise((FloatProcessor)stack.getProcessor(slice), theta);
        if (version) denoise(ip, theta, g, dt, ite);
        else denoiseTVM(ip, ite, theta);
        image.updateAndDraw();
    }

    public void testDenoise() {
        ImagePlus imp = IJ.openImage("Z:\\Sanofi\\FIB-SEM_inpaintingTest\\Combo1_ali_resized.tif");
        Total_Variation_Denoising.denoise3D(imp.getImageStack(), 25, 1, 0.25f, 5);
        FileSaver fs = new FileSaver(imp);
        fs.saveAsTiffStack("Z:\\Sanofi\\FIB-SEM_inpaintingTest\\Combo1_ali_resized_TVM3D.tif");

    }

    public static void denoiseNotWorking(ImageStack fstack, float theta, float g, float dt, int iterations) {
        int width = fstack.getWidth();
        int height = fstack.getHeight();
        int depth = fstack.getSize();

        ImageStack ustack = fstack.duplicate();
        ImageStack px = new ImageStack(width, height);
        ImageStack py = new ImageStack(width, height);
        ImageStack pz = new ImageStack(width, height);
        ImageStack divp = new ImageStack(width, height);

        for (int z = 0; z < depth; z++) {
            px.addSlice("", new FloatProcessor(width, height));
            py.addSlice("", new FloatProcessor(width, height));
            pz.addSlice("", new FloatProcessor(width, height));
            divp.addSlice("", new FloatProcessor(width, height));
        }

        for (int iteration = 0; iteration < iterations; iteration++) {
            // Iterate
            System.out.println("start iteration " + iteration);
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++) {
                        // Calculate forward derivatives
                        float[] u = (float[]) ustack.getPixels(z + 1);
                        float dux = (x < width - 1) ? u[x + 1 + y * width] - u[x + y * width] : 0;
                        float duy = (y < height - 1) ? u[x + (y + 1) * width] - u[x + y * width] : 0;
                        float duz = (z < depth - 1) ? ustack.getProcessor(z + 2).getf(x, y) - ustack.getProcessor(z + 1).getf(x, y) : 0;
                        float dd1 = 1 + dt / theta / g * Math.abs((float) Math.sqrt(duy * duy + dux * dux + duz * duz));
                        px.getProcessor(z + 1).setf(x, y, (px.getProcessor(z + 1).getf(x, y) - dt / theta * dux) / dd1);
                        py.getProcessor(z + 1).setf(x, y, (py.getProcessor(z + 1).getf(x, y) - dt / theta * duy) / dd1);
                        pz.getProcessor(z + 1).setf(x, y, (pz.getProcessor(z + 1).getf(x, y) - dt / theta * duz) / dd1);
                    }
                System.out.println("du computation plane " + z);
            }
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        float div = 0;
                        if (x == 0) div = px.getProcessor(z + 1).getf(x, y);
                        else if (x == width - 1) div = -px.getProcessor(z + 1).getf(x - 1, y);
                        else div = px.getProcessor(z + 1).getf(x, y) - px.getProcessor(z + 1).getf(x - 1, y);

                        if (y == 0) div += py.getProcessor(z + 1).getf(x, y);
                        else if (y == +height - 1) div += -py.getProcessor(z + 1).getf(x, y - 1);
                        else div += py.getProcessor(z + 1).getf(x, y) - py.getProcessor(z + 1).getf(x, y - 1);

                        if (z == 0) div += pz.getProcessor(z + 1).getf(x, y);
                        else if (z == depth - 1) div += -pz.getProcessor(z).getf(x, y);
                        else div += pz.getProcessor(z + 1).getf(x, y) - pz.getProcessor(z).getf(x, y);


                        divp.getProcessor(z + 1).setf(x, y, div);


                    }
                }
                System.out.println("divp computation plane " + z);
            }
            // Update u
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++)
                        ustack.getProcessor(z + 1).setf(x, y, fstack.getProcessor(z + 1).getf(x, y) - theta * divp.getProcessor(z + 1).getf(x, y));
                System.out.println("update u plane " + z);
            }
        }
        for (int z = 0; z < depth; z++)
            System.arraycopy(ustack.getPixels(z + 1), 0, fstack.getPixels(z + 1), 0, width * height);
    }

    public static void denoise3D(ImageStack fstack, float theta, float g, float dt, int iterations) {
        int width = fstack.getWidth();
        int height = fstack.getHeight();
        int depth = fstack.getSize();

        ImageStack ustack = fstack.duplicate();
        ImageStack pstack = new ImageStack(width, height * 3);
        ImageStack dustack = new ImageStack(width, height * 3);
        //ImageStack divp = new ImageStack(width, height);

        for (int z = 0; z < fstack.size(); z++) {
            pstack.addSlice("", new FloatProcessor(width, height * 3));
            dustack.addSlice("", new FloatProcessor(width, height * 3));
            //divp.addSlice("", new FloatProcessor(width, height));
        }

        //float[] u = new float[width * height];
        //float[] p = new float[width * height * 2];
        //float[] d = new float[width * height * 2];
        //float[] du = new float[width * height * 2];
        //float[] div_p = new float[width * height];
        for (int iteration = 0; iteration < iterations; iteration++) {
            System.out.println("tvm iteration "+iteration);
            for (int z = 1; z <= fstack.getSize(); z++) {
                //System.out.println("processing image " + z);
                //float[] pixels = (float[]) fstack.getPixels(z);
                float[] u = (float[]) ustack.getPixels(z);
                float[] u1 = (float[]) ustack.getPixels(Math.min(z + 1, depth));
                float[] p = (float[]) pstack.getPixels(z);
                //float[] du = (float[]) dustack.getPixels(z);
                //float[] div_p = (float[]) divp.getPixels(z);

            /*System.arraycopy(pixels, 0, u, 0, width * height);
            Arrays.fill(p,0);
            Arrays.fill(du,0);
            Arrays.fill(div_p,0);*/


                // Calculate forward derivatives
                /*for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++) {
                        if (x < width - 1)
                            du[x + width * (y + height)] = u[x + 1 + width * y] - u[x + width * y];
                        if (y < height - 1)
                            du[x + width * y] = u[x + width * (y + 1)] - u[x + width * y];
                        if (z < depth - 1)
                            du[x + width * (y + height * 2)] = u1[x + width * y] - u[x + width * y];
                    }
                */
                // Iterate
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++) {
                        float duy = (y < height - 1)? u[x + width * (y + 1)] - u[x + width * y]:0;//du[x + width * y];
                        float dux = (x < width - 1)?u[x + 1 + width * y] - u[x + width * y]:0;//du[x + width * (y + height)];
                        float duz = (z < depth - 1)? u1[x + width * y] - u[x + width * y]:0;//du[x + width * (y + height * 2)];
                        float dd1 = 1 + dt / theta / g * Math.abs((float) Math.sqrt(duy * duy + dux * dux + duz * duz));
                        p[x + width * y] = (p[x + width * y] - dt / theta * duy) / dd1;
                        p[x + width * (y + height)] = (p[x + width * (y + height)] - dt / theta * dux) / dd1;
                        p[x + width * (y + height * 2)] = (p[x + width * (y + height * 2)] - dt / theta * duz) / dd1;
                    }
            }
            for (int z = 1; z <= fstack.getSize(); z++) {
                float div_p=0;
                //float[] div_p = (float[]) divp.getPixels(z);
                float[] p = (float[]) pstack.getPixels(z);
                float[] p_1 = (float[]) pstack.getPixels(Math.max(z - 1, 1));
                float[] pixels = (float[]) fstack.getPixels(z);
                float[] u = (float[]) ustack.getPixels(z);

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        if (x == 0) div_p = p[x+ width * (y + height)];
                        else if (x == width - 1) div_p = -p[x + width * (y + height)];
                        else div_p = p[x + width * (y + height)] - p[x - 1 + width * (y + height)];
                        if (y == 0) div_p += p[x + width * y];
                        else if (y == height - 1) div_p += -p[x + width * y];
                        else div_p += p[x + width * y] - p[x + width * (y - 1)];
                        if (z == 0) div_p += p[x + width * (y + height * 2)];
                        else if (z == depth) div_p += -p_1[x + width * (y + height * 2)];
                        else
                            div_p += p[x + width * (y + height * 2)] - p_1[x + width * (y + height * 2)];

                        u[x + width * y] = pixels[x + width * y] - theta * div_p;
                    }
                }
            }
            /*for (int z = 1; z <= fstack.getSize(); z++) {
                float[] pixels = (float[]) fstack.getPixels(z);
                float[] div_p = (float[]) divp.getPixels(z);
                float[] u = (float[]) ustack.getPixels(z);
                // Update u
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++)
                        u[x + width * y] = pixels[x + width * y] - theta * div_p[x + width * y];


            }*/

        }
        for(int z=1;z<= fstack.getSize();z++) {
            System.arraycopy(ustack.getPixels(z), 0, fstack.getPixels(z), 0, width * height);
        }
    }

    public static void denoise(ImageProcessor ip, float theta, float g, float dt, int iterations) {
        int w = ip.getWidth();
        int h = ip.getHeight();
        float[] pixels = (float[]) ip.getPixels();

        float[] u = new float[w * h];
        float[] p = new float[w * h * 2];
        float[] d = new float[w * h * 2];
        float[] du = new float[w * h * 2];
        float[] div_p = new float[w * h];

        System.arraycopy(pixels, 0, u, 0, w * h);
        for (int iteration = 0; iteration < iterations; iteration++) {
            // Calculate forward derivatives
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++) {
                    if (i < w - 1)
                        du[i + w * (j + h)] = u[i + 1 + w * j] - u[i + w * j];
                    if (j < h - 1)
                        du[i + w * j] = u[i + w * (j + 1)] - u[i + w * j];
                }

            // Iterate
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++) {
                    float du1 = du[i + w * j], du2 = du[i + w * (j + h)];
                    float dd1 = 1 + dt / theta / g * Math.abs((float) Math.sqrt(du1 * du1 + du2 * du2));
                    p[i + w * j] = (p[i + w * j] - dt / theta * du[i + w * j]) / dd1;
                    p[i + w * (j + h)] = (p[i + w * (j + h)] - dt / theta * du[i + w * (j + h)]) / dd1;
                    //                                            d[i + w * j] = 1 + dt / theta / g * Math.abs((float)Math.sqrt(du1 * du1 + du2 * du2));
                    //                                            d[i + w * (j + h)] = 1 + dt / theta / g * Math.abs((float)Math.sqrt(du1 * du1 + du2 * du2));
                    //                                            p[i + w * j] = (p[i + w * j] - dt / theta * du[i + w * j]) / d[i + w * j];
                    //                                            p[i + w * (j + h)] = (p[i + w * (j + h)] - dt / theta * du[i + w * (j + h)]) / d[i + w * (j + h)];
                }
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    if (i == 0) div_p[w * j] = p[w * (j + h)];
                    else if (i == w - 1) div_p[w - 1 + w * j] = -p[w - 1 + w * (j + h)];
                    else div_p[i + w * j] = p[i + w * (j + h)] - p[i - 1 + w * (j + h)];
                    if (j == 0) div_p[i] += p[i];
                    else if (j == h - 1) div_p[i + w * (h - 1)] += -p[i + w * (h - 1)];
                    else div_p[i + w * j] += p[i + w * j] - p[i + w * (j - 1)];

                }
            }
                            /*for (int i = 0; i < w; i++) {
                                    for (int j = 1; j < h - 1; j++)
                                            div_p[i + w * j] = p[i + w * j] - p[i + w * (j - 1)];
                                    // Handle boundaries
                                    div_p[i] = p[i];
                                    div_p[i + w * (h - 1)] = -p[i + w * (h - 1)];
                            }

                            for (int j = 0; j < h; j++) {
                                    for (int i = 1; i < w - 1; i++)
                                            div_p[i + w * j] += p[i + w * (j + h)] - p[i - 1 + w * (j + h)];
                                    // Handle boundaries
                                    div_p[w * j] = p[w * (j + h)];
                                    div_p[w - 1 + w * j] = -p[w - 1 + w * (j + h)];
                            } */

            // Update u
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++)
                    u[i + w * j] = pixels[i + w * j] - theta * div_p[i + w * j];


        }
        System.arraycopy(u, 0, pixels, 0, w * h);
    }

    public static void denoiseTVM(ImageProcessor f, int nIteration, float dtv) {
        float[] pixels = (float[]) f.getPixels();
        ImageProcessor ff = f.duplicate();
        float[] tabf = (float[]) f.getPixelsCopy();
        ImageProcessor df = new FloatProcessor(f.getWidth(), f.getHeight());
        for (int ite = 0; ite < nIteration; ite++) {
            float norm = 0;
            float d;
            for (int y = 0; y < df.getHeight(); y++) {
                for (int x = 0; x < df.getWidth(); x++) {
                    float tmp = (x + 1 < df.getWidth()) ? (ff.getf(x, y) - ff.getf(x + 1, y)) : 0;
                    norm = tmp * tmp;
                    d = tmp;
                    tmp = (y + 1 < df.getHeight()) ? (ff.getf(x, y) - ff.getf(x, y + 1)) : 0;
                    d += tmp;
                    norm += tmp * tmp;
                    norm /= (float) Math.sqrt(norm);
                    df.setf(x, y, d / norm);
                }
            }
            norm = (float) Math.sqrt(norm);
            float[] tabdf = (float[]) df.getPixels();
            //for (int i = 0; i < tabdf.length; i++) tabdf[i] /= norm;
            //new ImagePlus("tv"+ite,df.duplicate()).show();


            for (int i = 0; i < tabf.length; i++) tabf[i] = pixels[i] - tabdf[i] / dtv;
        }
        System.arraycopy(tabf, 0, pixels, 0, pixels.length);

    }

    /**
     * Description of the Method
     *
     * @param imp     Description of the Parameter
     * @param command Description of the Parameter
     * @param pfr     Description of the Parameter
     * @return Description of the Return Value
     */
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        this.pfr = pfr;
        preview = true;
        GenericDialog gd = new GenericDialog("ROF Denoise");
        gd.addNumericField("Theta", theta, 2);
        gd.addNumericField("g", g, 2);
        gd.addNumericField("dt", dt, 2);
        gd.addNumericField("iteration", ite, 0);
        gd.addCheckbox("version", true);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        theta = (float) gd.getNextNumber();
        g = (float) gd.getNextNumber();
        dt = (float) gd.getNextNumber();
        ite = (int) gd.getNextNumber();
        version = gd.getNextBoolean();
        return IJ.setupDialog(imp, flags);
    }

    /**
     * Sets the nPasses attribute of the HotSpot_Detection object
     *
     * @param nPasses The new nPasses value
     */
    public void setNPasses(int nPasses) {
    }

    /**
     * Listener to modifications of the input fields of the dialog
     *
     * @param gd Description of the Parameter
     * @param e  Description of the Parameter
     * @return Description of the Return Value
     */
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {

        theta = (float) gd.getNextNumber();
        g = (float) gd.getNextNumber();
        dt = (float) gd.getNextNumber();
        ite = (int) gd.getNextNumber();
        version = gd.getNextBoolean();
        return true;
    }

}