package fr.curie.inpainting;

import cern.colt.function.tdouble.Double9Function;
import cern.colt.function.tdouble.DoubleProcedure;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.*;


/**
 * Implementation Java du code écrit par Simone Parisotto et Carola-Bibiane Schoenlieb
 * Schoenlieb, Carola-Bibiane
 * Partial Differential Equation Methods for Image Inpainting.
 * Cambridge Monographs on Applied and Computational Mathematics,
 * Cambridge University Press, 2015
 * doi:10.1017/CBO9780511734304
 */

public class AMLE_InpaintParallelColt implements ExtendedPlugInFilter, DialogListener {
    PlugInFilterRunner pfr;
    boolean preview;
    int flags = DOES_32 + DOES_8G + DOES_16 + PARALLELIZE_STACKS;
    ImagePlus myimp;
    boolean zerosToNaN = false;


    double fidelity = 10^2;
    double tol = 1e-8;
    int maxiter = 300;
    float dt = 0.1f;
    boolean showIntermediates = false;
    ImageStack videoOutput;

    public void displayMatrixAsImage(DoubleMatrix2D mat, String title){
        FloatProcessor fp= new FloatProcessor(mat.columns(),mat.rows());
        int h = fp.getHeight();
        int w = fp.getWidth();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                fp.setf(x, y, (float)mat.getQuick(y,x));
            }
        }
        new ImagePlus(title,fp).show();

    }

    public void addToVideo(DoubleMatrix2D mat, String title){
        FloatProcessor fp= new FloatProcessor(mat.columns(),mat.rows());
        int h = fp.getHeight();
        int w = fp.getWidth();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                fp.setf(x, y, (float)mat.getQuick(y,x));
            }
        }
        if (videoOutput==null) videoOutput=new ImageStack(fp.getWidth(),fp.getHeight());
        videoOutput.addSlice(title,fp);
    }

    public void displayVideo(String title){
        new ImagePlus(title,videoOutput).show();
    }

    public DoubleMatrix2D inpaint(DoubleMatrix2D input, DoubleMatrix2D mask, double fidelity, double tol, int maxiter, float dt) {
        int M = input.rows();
        int N = input.columns();
//        displayMatrixAsImage(input,"input");

        if(showIntermediates) addToVideo(input,"input");
        //Kernels

        Kernels k = create_kernel_derivates();

        final Double9Function Kfi = k.getKfi();
        final Double9Function Kfj = k.getKfj();
        final Double9Function Kbi = k.getKbi();
        final Double9Function Kbj = k.getKbj();
        final Double9Function Kci = k.getKci();
        final Double9Function Kcj = k.getKcj();


        //Init

//        Mat u = new Mat();
//        input.copyTo(u);
        DoubleMatrix2D u=input.copy();
//        Mat v0 = Mat.zeros(M, N, CV_32F).asMat();
//        Mat v1 = Mat.zeros(M, N, CV_32F).asMat();

        DoubleMatrix2D v0= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D v1= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D ux= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D uy= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D uxx= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D uxy= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D uyx= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D uyy= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D dennormal_tmp= new DenseDoubleMatrix2D(M,N);
        DoubleMatrix2D tmp;
        DoubleMatrix2D unew;
//        displayMatrixAsImage(ux,"ux init");

//        Mat ux = new Mat();
//        Mat uy = new Mat();
//        Mat uxx = new Mat();
//        Mat uxy = new Mat();
//        Mat uyx = new Mat();
//        Mat uyy = new Mat();
//        Mat dennormal_tmp = new Mat();
//        Mat tmp = new Mat();
//        Mat B = new Mat();
//        Mat unew = new Mat();
        //iterations
        for (int iter = 0; iter < maxiter; iter++) {

//            opencv_imgproc.filter2D(u, ux, -1, Kfi); //forward differences along i
//            opencv_imgproc.filter2D(u, uy, -1, Kfj); //forward differences along j
            u.zAssign8Neighbors(ux,Kfi);
            u.zAssign8Neighbors(uy,Kfj);

//            displayMatrixAsImage(ux,"ux "+iter);
//            displayMatrixAsImage(uy,"uy "+iter);
            // second derivatives
//            opencv_imgproc.filter2D(ux, uxx, -1, Kbi);
//            opencv_imgproc.filter2D(ux, uxy, -1, Kbj);
//            opencv_imgproc.filter2D(uy, uyx, -1, Kbi);
//            opencv_imgproc.filter2D(uy, uyy, -1, Kbj);
            ux.zAssign8Neighbors(uxx,Kbi);
            ux.zAssign8Neighbors(uxy,Kbj);
            uy.zAssign8Neighbors(uyx,Kbi);
            uy.zAssign8Neighbors(uyy,Kbj);

//            displayMatrixAsImage(uxx,"uxx "+iter);
//            displayMatrixAsImage(uxy,"uxy "+iter);
//            displayMatrixAsImage(uyx,"uyx "+iter);
//            displayMatrixAsImage(uyy,"uyy "+iter);

            // create direction field Du/|Du| with central differences
//            opencv_imgproc.filter2D(u, v0, -1, Kci);
//            opencv_imgproc.filter2D(u, v1, -1, Kcj);
            u.zAssign8Neighbors(v0,Kci);
            u.zAssign8Neighbors(v1,Kcj);
//
//            displayMatrixAsImage(v0.copy(),"v0 "+iter);
//            displayMatrixAsImage(v1.copy(),"v1 "+iter);

            //Mat dennormal0 = v0.mul(v0).asMat();
            //Mat dennormal1 = v1.mul(v1).asMat();
            dennormal_tmp.assign(v0).assign(DoubleFunctions.square);
            DoubleMatrix2D dennormal1=v1.copy().assign(DoubleFunctions.square);
            dennormal_tmp.assign(dennormal1,DoubleFunctions.plus).assign(DoubleFunctions.sqrt);
            dennormal_tmp.assign(DoubleFunctions.plus(0.000000000000001));
            v0.assign(dennormal_tmp,DoubleFunctions.div);
            v1.assign(dennormal_tmp,DoubleFunctions.div);

//            displayMatrixAsImage(v0,"v0norm "+iter);
//            displayMatrixAsImage(v1,"v1norm "+iter);
//            displayMatrixAsImage(dennormal_tmp,"denormal_tmp "+iter);
//            opencv_core.add(dennormal0, dennormal1, dennormal_tmp);
//            opencv_core.sqrt(dennormal_tmp, dennormal_tmp);
//            opencv_core.divide(v0, dennormal_tmp, v0);
//            opencv_core.divide(v1, dennormal_tmp, v1);

            //core itération : unew = u + dt*( uxx*v[:,:,0]**2 + uyy*v[:,:,1]**2 + (uxy+uyx)*(v[:,:,0]*v[:,:,1]) + fidelity*mask[:,:,c]*( input[:,:,c]-u[:,:,c] ) );
            //                 unew = u +         D            +    E            +          F                    +              G
            DoubleMatrix2D G=input.copy().assign(u,DoubleFunctions.minus).assign(mask,DoubleFunctions.mult).assign(DoubleFunctions.mult(fidelity));
//            opencv_core.subtract(input, u, tmp);
//            opencv_core.multiply(tmp, mask, tmp);
//            Mat G = opencv_core.multiply(tmp, fidelity).asMat();
//            opencv_core.add(uxy, uyx, tmp);
            DoubleMatrix2D F=uxy.copy().assign(uyx,DoubleFunctions.plus).assign(v0.copy().assign(v1,DoubleFunctions.mult),DoubleFunctions.mult);// F = (uxy+uyx)*(v[:,:,0]*v[:,:,1])

//            Mat v0xv1 = v0.mul(v1).asMat();
//            Mat F = tmp.mul(v0xv1).asMat();// F = (uxy+uyx)*(v[:,:,0]*v[:,:,1])
            DoubleMatrix2D E=uyy.copy().assign(v1.copy().assign(DoubleFunctions.square),DoubleFunctions.mult);
//            Mat v1_2 = v1.mul(v1).asMat();
//            Mat E = uyy.mul(v1_2).asMat();
            DoubleMatrix2D D= uxx.copy().assign(v0.copy().assign(DoubleFunctions.square),DoubleFunctions.mult);
//            Mat v0_2 = v0.mul(v0).asMat();
//            Mat D = uxx.mul(v0_2).asMat();// D = uxx*v[:,:,0]**2

//            displayMatrixAsImage(G,"G "+iter);
//            displayMatrixAsImage(F,"F "+iter);
//            displayMatrixAsImage(E,"E "+iter);
//            displayMatrixAsImage(D,"D "+iter);

             // B = D + E + F + G
            tmp=D.assign(E,DoubleFunctions.plus).assign(F,DoubleFunctions.plus).assign(G,DoubleFunctions.plus);
//            opencv_core.add(D, E, B);
//            opencv_core.add(B, F, B);
//            opencv_core.add(B, G, B);

            tmp.assign(DoubleFunctions.mult(dt));
            //displayMatrixAsImage(tmp,"correction "+iter);
            unew=u.copy().assign(tmp,DoubleFunctions.plus);
//            Mat A = new Mat();
//            A = opencv_core.multiply(B, dt).asMat(); // A = dt * ( B )
//            opencv_core.add(u, A, unew);     // u[:,:,c] + A

            //exit condition
            double diff_u = Math.sqrt(unew.copy().assign(u,DoubleFunctions.minus).assign(DoubleFunctions.square).zSum());
            //double diff_u = opencv_core.norm(unew, u);
            System.out.println("iter "+iter+" diff="+diff_u);

            u.assign(unew);
            //unew.copyTo(u);
            if(showIntermediates) addToVideo(u,"u "+iter);


            if (diff_u < tol) {
                break;
            }

            IJ.showProgress((iter+1.0)/this.maxiter);
        }


        return u;
    }

    public Kernels create_kernel_derivates() {
//        float[] tmp = new float[]{0, 0, 0,
//                0, -1, 0,
//                0, 1, 0};
//        DenseDoubleMatrix2D Kfi = new DenseDoubleMatrix2D(3, 3);
//        Kfi.assign((float[]) tmp);
        Double9Function Kfi=new Double9Function() {
            @Override
            public double apply(double a00, double a01, double a02,
                                double a10, double a11, double a12,
                                double a20, double a21, double a22) {
                return a21-a11;
            }
        } ;

//        tmp = new float[]{0, 0, 0,
//                0, -1, 1,
//                0, 0, 0};
//        //Mat Kfj = new Mat(tmp).reshape(1, 3);
//        DenseDoubleMatrix2D Kfj = new DenseDoubleMatrix2D(3, 3);
//        Kfj.assign((float[]) tmp);
        Double9Function Kfj=new Double9Function() {
            @Override
            public double apply(double a00, double a01, double a02,
                                double a10, double a11, double a12,
                                double a20, double a21, double a22) {
                return a12-a11;
            }
        } ;

//        tmp = new float[]{0, -1, 0,
//                0, 1, 0,
//                0, 0, 0};
//        //Mat Kbi = new Mat(tmp).reshape(1, 3);
//        DenseDoubleMatrix2D Kbi = new DenseDoubleMatrix2D(3, 3);
//        Kbi.assign((float[]) tmp);
        Double9Function Kbi=new Double9Function() {
            @Override
            public double apply(double a00, double a01, double a02,
                                double a10, double a11, double a12,
                                double a20, double a21, double a22) {
                return a11-a01;
            }
        } ;

//        tmp = new float[]{0, 0, 0,
//                -1, 1, 0,
//                0, 0, 0};
//        //Mat Kbj = new Mat(tmp).reshape(1, 3);
//        DenseDoubleMatrix2D Kbj = new DenseDoubleMatrix2D(3, 3);
//        Kbj.assign((float[]) tmp);
        Double9Function Kbj=new Double9Function() {
            @Override
            public double apply(double a00, double a01, double a02,
                                double a10, double a11, double a12,
                                double a20, double a21, double a22) {
                return a11-a10;
            }
        } ;


//        tmp = new float[]{0, -0.5f, 0,
//                0, 0, 0,
//                0, 0.5f, 0};
////        Mat Kci = new Mat(tmp).reshape(1, 3);
//        DenseDoubleMatrix2D Kci = new DenseDoubleMatrix2D(3, 3);
//        Kci.assign((float[]) tmp);
        Double9Function Kci=new Double9Function() {
            @Override
            public double apply(double a00, double a01, double a02,
                                double a10, double a11, double a12,
                                double a20, double a21, double a22) {
                return 0.5*a21-0.5*a01;
            }
        } ;


//        tmp = new float[]{0, 0, 0,
//                -0.5f, 0, 0.5f,
//                0, 0, 0};
//        //Mat Kcj = new Mat(tmp).reshape(1, 3);
//        DenseDoubleMatrix2D Kcj = new DenseDoubleMatrix2D(3, 3);
//        Kcj.assign((float[]) tmp);
        Double9Function Kcj=new Double9Function() {
            @Override
            public double apply(double a00, double a01, double a02,
                                double a10, double a11, double a12,
                                double a20, double a21, double a22) {
                return 0.5*a12-0.5*a10;
            }
        } ;


        return new Kernels(Kfi, Kfj, Kbi, Kbj, Kci, Kcj);

    }


    @Override
    public boolean dialogItemChanged(GenericDialog genericDialog, AWTEvent awtEvent) {
        zerosToNaN = genericDialog.getNextBoolean();
        fidelity = genericDialog.getNextNumber();
        tol = genericDialog.getNextNumber();
        maxiter = (int) genericDialog.getNextNumber();
        dt = (float) genericDialog.getNextNumber();
        showIntermediates = genericDialog.getNextBoolean();
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
        gd.addCheckbox("show intermediate images",showIntermediates);
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
        showIntermediates = gd.getNextBoolean();

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
        //new ImagePlus("mask",mask).show();
        DenseDoubleMatrix2D matMask = new DenseDoubleMatrix2D(ip.getHeight(), ip.getWidth());
        matMask.assign((float[]) mask.getPixels());
        //Mat matMask = new Mat((float[]) mask.getPixels()).reshape(1, ip.getHeight());


        DenseDoubleMatrix2D matInput = new DenseDoubleMatrix2D(ip.getHeight(), ip.getWidth());
        matInput.assign((float[]) result.getPixels());
        //int xtmp=100,ytmp=200;
        //System.out.println("("+xtmp+","+ytmp+") = ip:"+ip.getf(xtmp,ytmp)+" result:"+result.getf(xtmp,ytmp)+" matInput:"+matInput.getQuick(ytmp,xtmp));
        //Mat matInput = new Mat((float[]) result.getPixels()).reshape(1, ip.getHeight());

        DoubleMatrix2D matResult=inpaint(matInput, matMask, fidelity, tol, maxiter, dt);
        //Mat matResult = inpaint(matInput, matMask, fidelity, tol, maxiter, dt);
        int h = ip.getHeight();
        int w = ip.getWidth();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ip.setf(x, y, (float)matResult.getQuick(y,x));
            }
        }

        if(showIntermediates) displayVideo(myimp.getTitle()+"intermediates");



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
        private final Double9Function Kfi;
        private final Double9Function Kfj;
        private final Double9Function Kbi;
        private final Double9Function Kbj;
        private final Double9Function Kci;
        private final Double9Function Kcj;

        public Kernels(Double9Function Kfi, Double9Function Kfj, Double9Function Kbi, Double9Function Kbj, Double9Function Kci, Double9Function Kcj) {
            this.Kfi = Kfi;
            this.Kfj = Kfj;
            this.Kbi = Kbi;
            this.Kbj = Kbj;
            this.Kci = Kci;
            this.Kcj = Kcj;
        }

        public Double9Function getKfi() {
            return Kfi;
        }

        public Double9Function getKfj() {
            return Kfj;
        }

        public Double9Function getKbi() {
            return Kbi;
        }

        public Double9Function getKbj() {
            return Kbj;
        }

        public Double9Function getKci() {
            return Kci;
        }

        public Double9Function getKcj() {
            return Kcj;
        }
    }
}

