package fr.curie.inpainting;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgproc;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static org.bytedeco.javacpp.opencv_core.CV_32F;
import static org.bytedeco.javacpp.opencv_core.Mat;


/**
 * Implementation Java du code écrit par Simone Parisotto et Carola-Bibiane Schoenlieb
 * Schoenlieb, Carola-Bibiane
 * Partial Differential Equation Methods for Image Inpainting.
 * Cambridge Monographs on Applied and Computational Mathematics,
 * Cambridge University Press, 2015
 * doi:10.1017/CBO9780511734304
 */

public class AMLE_Inpaint implements ExtendedPlugInFilter, DialogListener {
    PlugInFilterRunner pfr;
    boolean preview;
    int flags = DOES_32 + DOES_8G + DOES_16 + PARALLELIZE_STACKS;
    ImagePlus myimp;
    boolean zerosToNaN = false;


    double fidelity = 10^2;
    double tol = 1e-8;
    int maxiter = 300;
    float dt = 0.1f;


    public Mat inpaint(Mat input, Mat mask, double fidelity, double tol, int maxiter, float dt) {
        int M;
        int N;
        int C;

        if (input.dims() == 3) {

            M = input.size().height();
            N = input.size().width();
            C = input.channels();
        } else {
            M = input.size().height();
            N = input.size().width();
            C = 1;
        }

        //Kernels

        Kernels k = create_kernel_derivates();

        Mat Kfi = k.getKfi();
        Mat Kfj = k.getKfj();
        Mat Kbi = k.getKbi();
        Mat Kbj = k.getKbj();
        Mat Kci = k.getKci();
        Mat Kcj = k.getKcj();


        //Init

        Mat u = new Mat();
        input.copyTo(u);
        Mat v0 = Mat.zeros(M, N, CV_32F).asMat();
        Mat v1 = Mat.zeros(M, N, CV_32F).asMat();

        Mat ux = new Mat();
        Mat uy = new Mat();
        Mat uxx = new Mat();
        Mat uxy = new Mat();
        Mat uyx = new Mat();
        Mat uyy = new Mat();
        Mat dennormal_tmp = new Mat();
        Mat tmp = new Mat();
        Mat B = new Mat();
        Mat unew = new Mat();
        //iterations
        for (int iter = 0; iter < maxiter; iter++) {

            opencv_imgproc.filter2D(u, ux, -1, Kfi); //forward differences along i
            opencv_imgproc.filter2D(u, uy, -1, Kfj); //forward differences along j
            // second derivatives
            opencv_imgproc.filter2D(ux, uxx, -1, Kbi);
            opencv_imgproc.filter2D(ux, uxy, -1, Kbj);
            opencv_imgproc.filter2D(uy, uyx, -1, Kbi);
            opencv_imgproc.filter2D(uy, uyy, -1, Kbj);

            // create direction field Du/|Du| with central differences
            opencv_imgproc.filter2D(u, v0, -1, Kci);
            opencv_imgproc.filter2D(u, v1, -1, Kcj);


            Mat dennormal0 = v0.mul(v0).asMat();
            Mat dennormal1 = v1.mul(v1).asMat();
            opencv_core.add(dennormal0, dennormal1, dennormal_tmp);
            opencv_core.sqrt(dennormal_tmp, dennormal_tmp);
            opencv_core.divide(v0, dennormal_tmp, v0);
            opencv_core.divide(v1, dennormal_tmp, v1);

            //core itération : unew = u + dt*( uxx*v[:,:,0]**2 + uyy*v[:,:,1]**2 + (uxy+uyx)*(v[:,:,0]*v[:,:,1]) + fidelity*mask[:,:,c]*( input[:,:,c]-u[:,:,c] ) );
            opencv_core.subtract(input, u, tmp);
            opencv_core.multiply(tmp, mask, tmp);
            Mat G = opencv_core.multiply(tmp, fidelity).asMat();
            opencv_core.add(uxy, uyx, tmp);
            Mat v0xv1 = v0.mul(v1).asMat();
            Mat F = tmp.mul(v0xv1).asMat();// F = (uxy+uyx)*(v[:,:,0]*v[:,:,1])
            Mat v1_2 = v1.mul(v1).asMat();
            Mat E = uyy.mul(v1_2).asMat();
            Mat v0_2 = v0.mul(v0).asMat();
            Mat D = uxx.mul(v0_2).asMat();// D = uxx*v[:,:,0]**2
             // B = D + E + F + G
            opencv_core.add(D, E, B);
            opencv_core.add(B, F, B);
            opencv_core.add(B, G, B);

            Mat A = new Mat();
            A = opencv_core.multiply(B, dt).asMat(); // A = dt * ( B )
            opencv_core.add(u, A, unew);     // u[:,:,c] + A

            //exit condition
            double diff_u = opencv_core.norm(unew, u);


            unew.copyTo(u);

            //Libérer la mémoire
            dennormal0.release();
            dennormal1.release();
            v1_2.release();
            v0_2.release();
            v0xv1.release();
            G.release();
            F.release();
            D.release();
            E.release();
            A.release();
            unew.release();

            if (diff_u < tol) {
                break;
            }

            IJ.showProgress((iter+1.0)/this.maxiter);
        }

        tmp.release();
        dennormal_tmp.release();
        ux.release();
        uxx.release();
        uxy.release();
        uyx.release();
        uyy.release();
        B.release();
        return u;
    }

    public Kernels create_kernel_derivates() {
        float[] tmp = new float[]{0, 0, 0,
                0, -1, 0,
                0, 1, 0};
        Mat Kfi = new Mat(tmp).reshape(1, 3);
        tmp = new float[]{0, 0, 0,
                0, -1, 1,
                0, 0, 0};
        Mat Kfj = new Mat(tmp).reshape(1, 3);

        tmp = new float[]{0, -1, 0,
                0, 1, 0,
                0, 0, 0};
        Mat Kbi = new Mat(tmp).reshape(1, 3);

        tmp = new float[]{0, 0, 0,
                -1, 1, 0,
                0, 0, 0};
        Mat Kbj = new Mat(tmp).reshape(1, 3);

        tmp = new float[]{0, -0.5f, 0,
                0, 0, 0,
                0, 0.5f, 0};
        Mat Kci = new Mat(tmp).reshape(1, 3);

        tmp = new float[]{0, 0, 0,
                -0.5f, 0, 0.5f,
                0, 0, 0};
        Mat Kcj = new Mat(tmp).reshape(1, 3);


        return new Kernels(Kfi, Kfj, Kbi, Kbj, Kci, Kcj);

    }


    @Override
    public boolean dialogItemChanged(GenericDialog genericDialog, AWTEvent awtEvent) {
        zerosToNaN = genericDialog.getNextBoolean();
        fidelity = genericDialog.getNextNumber();
        tol = genericDialog.getNextNumber();
        maxiter = (int) genericDialog.getNextNumber();
        dt = (float) genericDialog.getNextNumber();
        return true;
    }

    @Override
    public int showDialog(ImagePlus imagePlus, String s, PlugInFilterRunner plugInFilterRunner) {
        this.pfr = plugInFilterRunner;
        preview = true;
        GenericDialog gd = new GenericDialog(s);
        gd.addCheckbox("inpaint zeros", zerosToNaN);
        gd.addNumericField("Fidelity", fidelity, 0);
        gd.addNumericField("Tolerance", tol, 10);
        gd.addNumericField("Max iteration", maxiter,0);
        gd.addNumericField("dt", dt, 3);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        zerosToNaN = gd.getNextBoolean();
        fidelity = gd.getNextNumber();
        tol = gd.getNextNumber();
        maxiter = (int) gd.getNextNumber();
        dt = (float) gd.getNextNumber();

        return IJ.setupDialog(imagePlus, flags);
    }

    @Override
    public void setNPasses(int i) {

    }
    @Override
    public int setup(String s, ImagePlus imagePlus) {
        myimp = imagePlus;
        return flags;
    }

    @Override
    public void run(ImageProcessor ip) {
        ImageProcessor result = ip.convertToFloat();
        FloatProcessor mask = createMask(ip);
        new ImagePlus("mask",mask).show();
        Mat matMask = new Mat((float[]) mask.getPixels()).reshape(1, ip.getHeight());
        Mat matInput;
        Mat matResult;
        matInput = new Mat((float[]) result.getPixels()).reshape(1, ip.getHeight());
        matResult = inpaint(matInput, matMask, fidelity, tol, maxiter, dt);
        FloatBuffer bb = matResult.createBuffer();
        int h = ip.getHeight();
        int w = ip.getWidth();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ip.setf(x, y, bb.get(y * w + x));
            }
        }

        matInput.release();
        matMask.release();
        matResult.release();


    }


    public FloatProcessor createMask(ImageProcessor ip) {
        FloatProcessor mask = new FloatProcessor(ip.getWidth(), ip.getHeight());
        for (int y = 0; y < ip.getHeight(); y++) {
            for (int x = 0; x < ip.getWidth(); x++) {
                float val = ip.getf(x, y);
                if (Float.isNaN(val) || (zerosToNaN && val == 0)) {
                    mask.setf(x, y, 0);
                } else {
                    mask.setf(x, y, 1);
                }
            }
        }
        mask.filter(ImageProcessor.MAX);
        return mask;
    }



    class Kernels {
        private final opencv_core.Mat Kfi;
        private final opencv_core.Mat Kfj;
        private final opencv_core.Mat Kbi;
        private final opencv_core.Mat Kbj;
        private final opencv_core.Mat Kci;
        private final opencv_core.Mat Kcj;

        public Kernels(opencv_core.Mat Kfi, opencv_core.Mat Kfj, opencv_core.Mat Kbi, opencv_core.Mat Kbj, opencv_core.Mat Kci, opencv_core.Mat Kcj) {
            this.Kfi = Kfi;
            this.Kfj = Kfj;
            this.Kbi = Kbi;
            this.Kbj = Kbj;
            this.Kci = Kci;
            this.Kcj = Kcj;
        }

        public opencv_core.Mat getKfi() {
            return Kfi;
        }

        public opencv_core.Mat getKfj() {
            return Kfj;
        }

        public opencv_core.Mat getKbi() {
            return Kbi;
        }

        public opencv_core.Mat getKbj() {
            return Kbj;
        }

        public opencv_core.Mat getKci() {
            return Kci;
        }

        public opencv_core.Mat getKcj() {
            return Kcj;
        }
    }
}

