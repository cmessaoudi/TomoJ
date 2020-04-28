package fr.curie.inpainting;

import cern.colt.function.tfloat.Float9Function;
import cern.colt.function.tfloat.FloatFloatFunction;
import cern.colt.function.tfloat.FloatFunction;
import cern.colt.matrix.tfloat.FloatFactory2D;
import cern.colt.matrix.tfloat.FloatFactory3D;
import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.FloatMatrix3D;
import cern.jet.math.tfloat.FloatFunctions;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import junit.framework.TestCase;

import java.awt.*;

/**
 * This blind inpainting method is based on total-variation, originally proposed by Manya V. Afonso and Joao Miguel Raposo Sanches in
 * Blind Inpainting Using ℓ0 and Total Variation Regularization. IEEE Transactions on Image Processing ( Volume: 24, Issue: 7, July 2015 )
 *
 * Based on the Matlab code by Manya Afonso:
 * https://github.com/manyaafonso/Blind-Inpainting-l0-TV
 */
public class Blind_Inpainting_l0TV extends TestCase implements ExtendedPlugInFilter, DialogListener {
    protected ImagePlus image;
    int flags = DOES_ALL + PARALLELIZE_STACKS;
    PlugInFilterRunner pfr;
    boolean preview;
    float theta = 25;
    float g = 1;
    float dt = 0.25f;
    int ite = 5;
    boolean version = true;
    double bias=0.001;

    double lambda1=0.01;
    double lambda2= 0.1;
    int maxiter=1000;
    boolean softThresholding=false;

    static Float9Function fh = new Float9Function() {
        public float apply(float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8) {
            return v4-v5;
        }
    };
    static Float9Function fh2 = new Float9Function() {
        public float apply(float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8) {
            return v3-v4;
        }
    };


    static Float9Function fv=new Float9Function() {
        public float apply(float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8) {
            return v4-v7;
        }
    };
    static Float9Function fv2=new Float9Function() {
        public float apply(float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8) {
            return v1-v4;
        }
    };

    /*public static void denoise(ImageProcessor ip, float theta) {
        denoise(ip, theta, 1, 0.25f, 5);
    }*/

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

    public void correctData(ImageProcessor ip){
        ImageStatistics stats=ImageStatistics.getStatistics(ip);
        float[] pixs=(float[])ip.getPixels();

        for(int i=0;i<pixs.length;i++) {
            if(Float.isNaN(pixs[i])) pixs[i]=0;
            else pixs[i]-=stats.min;
        }
    }

    /**
     * This method is run when the current image was accepted.
     *
     * @param ip is the current slice (typically, plugins use
     *           the ImagePlus set above instead).
     */
    public void run(ImageProcessor ip) {
        correctData(ip);
        FloatMatrix2D y= FloatFactory2D.dense.make((float[])ip.convertToFloatProcessor().getPixels(),ip.getWidth());
        y.assign(FloatFunctions.plus((float)bias));
        y=y.viewDice().copy();
        blindInpainting(y,(float)lambda1,(float)lambda2,maxiter,ite,(float)bias,softThresholding);
        image.updateAndDraw();
    }
    public static FloatMatrix2D blindInpainting(FloatMatrix2D y, float lambda1, float lambda2, int maxiteration, int iterChambolle,float bias, boolean softThresholding){
        FloatMatrix2D xhat = y.copy();
        FloatProcessor fp= new FloatProcessor(y.columns(),y.rows(),(float[])xhat.elements());
        ImageStack is=new ImageStack(fp.getWidth(),fp.getHeight());
        is.addSlice("initial",fp.duplicate());
        ImagePlus preview=new ImagePlus("blind denoised",fp);
        preview.show();
        FloatMatrix2D g=y.copy();
        g.assign(FloatFunctions.log);

        FloatMatrix2D u = g.like();
        FloatMatrix2D v = FloatFactory2D.dense.make(y.rows(),y.columns(),(float)Math.log10(bias)-1);


        double score = normSquare(g,u,v) +lambda1*normTV(u) + lambda2 * sumNonZero(v);
        System.out.println("initial score : "+score);

        for(int iter=0; iter<maxiteration;iter++){
            FloatMatrix2D gcopy=g.copy();
            gcopy.assign(v,FloatFunctions.minus);
            u.assign(projk(gcopy,(float)lambda1,iterChambolle));
            //System.out.println("ite "+iter+" u="+u);

            gcopy.assign(g);
            gcopy.assign(u,FloatFunctions.minus);
            if(softThresholding)v.assign(softThreshold(gcopy,(float)lambda2));
            else v.assign(hardThreshold(gcopy,(float)lambda2));
            //System.out.println("ite "+iter+" v="+v);
            double normsq=normSquare(g,u,v);
            double normTV=normTV(u);
            double sumzero=sumNonZero(v);
//            System.out.println("normsq="+normsq);
//            System.out.println("normTv="+normTV);
//            System.out.println("sum diff Zero="+sumzero);
            score = normsq +lambda1* normTV + lambda2 * sumzero;
            System.out.println("iteration "+iter+", normsq="+IJ.d2s(normsq,5)+", normTv="+IJ.d2s(normTV,5)+", sum diff Zero="+IJ.d2s(sumzero,5)+" total score : "+score);

            xhat = u.copy();
            xhat.assign(FloatFunctions.exp);
            fp.setPixels((float[])xhat.elements());
            is.addSlice("iteration "+(iter+1),fp.duplicate());
            preview.setTitle("blind denoised_iteration_"+(iter+1));
            preview.updateAndDraw();
        }
        //System.out.println("xhat="+xhat);

        FloatMatrix2D mask_est = v.copy();
        v.assign(FloatFunctions.exp);

        new ImagePlus("blind denoised movie",is).show();
        //new ImagePlus("blind denoised_mask",new FloatProcessor(y.columns(),y.rows(),(float[])mask_est.elements())).show();
        return xhat;
    }



    public static double normSquare(FloatMatrix2D g, FloatMatrix2D u, FloatMatrix2D v){

        FloatMatrix2D tmp=g.copy();
        tmp.assign(u, FloatFunctions.minus);
        tmp.assign(v,FloatFunctions.minus);
        tmp.assign(FloatFunctions.square);
        return tmp.zSum();

    }

    public static double normTV(FloatMatrix2D u){
        /*FloatMatrix2D diffh= u.like();
        FloatMatrix2D diffv= u.like();
        u.zAssign8Neighbors(diffh,fh);
        u.zAssign8Neighbors(diffv,fv);
        diffh.assign(FloatFunctions.square);
        diffv.assign(FloatFunctions.square);*/
        FloatMatrix2D diffh=diffh(u);
        FloatMatrix2D diffv=diffv(u);
        diffh.assign(FloatFunctions.square);
        diffv.assign(FloatFunctions.square);
        diffh.assign(diffv,FloatFunctions.plus);
        diffh.assign(FloatFunctions.sqrt);
        return diffh.zSum();
    }

    public static FloatMatrix2D diffh2(FloatMatrix2D data){
        FloatMatrix2D result=data.like();
        for(int y=0;y<data.columns();y++){
            for(int x=0;x<data.rows();x++){
                result.setQuick(x,y,data.getQuick((x+1<data.rows())?x+1:0,y)-data.getQuick(x,y));
            }
        }
        return result;
    }
    public static FloatMatrix2D diffv2(FloatMatrix2D data){
        FloatMatrix2D result=data.like();
        for(int y=0;y<data.columns();y++){
            for(int x=0;x<data.rows();x++){
                result.setQuick(x,y,data.getQuick(x,(y+1<data.columns())?y+1:0)-data.getQuick(x,y));
            }
        }
        return result;
    }
    public static FloatMatrix2D diffh(FloatMatrix2D data){
        FloatMatrix2D result=data.like();
        for(int y=0;y<data.columns();y++){
            for(int x=0;x<data.rows();x++){
                result.setQuick(x,y,-data.getQuick((x-1>=0)?x-1:data.rows()-1,y)+data.getQuick(x,y));
            }
        }
        return result;
    }
    public static FloatMatrix2D diffv(FloatMatrix2D data){
        FloatMatrix2D result=data.like();
        for(int y=0;y<data.columns();y++){
            for(int x=0;x<data.rows();x++){
                result.setQuick(x,y,-data.getQuick(x,(y-1>=0)?y-1:data.columns()-1)+ data.getQuick(x,y));
            }
        }
        return result;
    }


    public static double sumNonZero(FloatMatrix2D v){
        FloatMatrix2D tmp=v.like();
        tmp.assign(v, new FloatFloatFunction() {
            public float apply(float v, float v1) {
                return (v1!=0)?1:0;
            }
        });
        return tmp.zSum();
    }

    public static FloatMatrix2D projk(FloatMatrix2D g, float lambda, int niter){
        final float tau = 0.25f;
        FloatMatrix3D pn= FloatFactory3D.dense.make(2, g.rows(),g.columns());
        FloatMatrix2D glambda=g.copy().assign(FloatFunctions.div(lambda));
        for(int i=0;i<niter;i++){
            FloatMatrix3D qn=Q(Qstar(pn).assign(glambda,FloatFunctions.minus));
            //System.out.println("ite "+i+" qn= "+qn);
            FloatMatrix3D qntmp=qn.like();
            qntmp.assign(qn,new FloatFloatFunction(){
                public float apply(float v, float v1) {
                    return 1+tau*StrictMath.abs(v1);
                }
            });

            //System.out.println("ite "+i+" qntmp= "+qntmp);
            pn=(pn.copy().assign(qn.assign(FloatFunctions.mult(tau))).assign(qntmp,FloatFunctions.div));

            //System.out.println("ite "+i+" pn= "+pn);
        }
        FloatMatrix2D tmp = Qstar(pn).assign(FloatFunctions.mult(lambda));
        //System.out.println("projk tmp"+tmp);
        g.assign(tmp,FloatFunctions.minus);
        return g;

    }

    public static FloatMatrix3D Q(FloatMatrix2D x){
        FloatMatrix3D y=FloatFactory3D.dense.make(2,x.rows(),x.columns());

        /*FloatMatrix2D diffh= x.like();
        FloatMatrix2D diffv= x.like();
        x.zAssign8Neighbors(diffh,fh);
        x.zAssign8Neighbors(diffv,fv);*/
        FloatMatrix2D diffh=diffh(x);
        FloatMatrix2D diffv=diffv(x);

        for(int zz=0;zz<y.slices();zz++){
            FloatMatrix2D tmp=y.viewSlice(zz);
            if(zz==0) tmp.assign(diffh);
            else tmp.assign(diffv);
        }
        return y;
    }

    public static FloatMatrix2D Qstar(FloatMatrix3D x){
        FloatMatrix2D y=FloatFactory2D.dense.make(x.rows(),x.columns());

        /*FloatMatrix2D diffh2= y.like();
        FloatMatrix2D diffv2= y.like();
        FloatMatrix2D tmp=x.viewSlice(0);
        tmp.zAssign8Neighbors(diffh2,fh2);
        tmp =x.viewSlice(0);
        tmp.zAssign8Neighbors(diffv2,fv2);*/

        FloatMatrix2D diffh2=diffh2(x.viewSlice(0));
        FloatMatrix2D diffv2=diffv2(x.viewSlice(1));
        y.assign(diffh2);
        y.assign(diffv2,FloatFunctions.plus);
        return y;
    }

    public static FloatMatrix2D hardThreshold(FloatMatrix2D x, final float lambda){
        x.assign(new FloatFunction() {
            public float apply(float v) {
                return (Math.abs(v)>lambda)? v : 0;
            }
        });
        return x;
    }

    public static FloatMatrix2D softThreshold(FloatMatrix2D x, final float lambda){
        x.assign(new FloatFunction() {
            public float apply(float v) {
                float result=v;
                if (Math.abs(v)<lambda) {
                    result = 0;
                }else{
                    if(v>0) result-=lambda;
                    else result+=lambda;
                }
                return result;
            }
        });
        return x;
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
        GenericDialog gd = new GenericDialog("Blind inpainting");
        gd.addNumericField("lambda1", lambda1, 2);
        gd.addNumericField("lambda1", lambda2, 2);
        gd.addNumericField("number of iteration", maxiter, 0);
        gd.addNumericField("iteration of TV", ite, 0);
        gd.addNumericField("bias", bias, 0);
        gd.addCheckbox("softThresholding",softThresholding);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        lambda1 = (float) gd.getNextNumber();
        lambda2 = (float) gd.getNextNumber();
        maxiter = (int) gd.getNextNumber();
        ite = (int) gd.getNextNumber();
        bias=gd.getNextNumber();
        softThresholding=gd.getNextBoolean();
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

        lambda1 = (float) gd.getNextNumber();
        lambda2 = (float) gd.getNextNumber();
        maxiter = (int) gd.getNextNumber();
        ite = (int) gd.getNextNumber();
        bias=gd.getNextNumber();
        softThresholding=gd.getNextBoolean();
        return true;
    }

    public void testfunctions() {
        FloatMatrix2D data=FloatFactory2D.dense.make(new float[]{1,2,3,4,5,
                                                                6,7,8,9,10,
                                                                11,12,0.001f,14,15,
                                                                16,17,18,19,20,
                                                                21,22,23,24,25},  5);

       // blindInpainting(data,0.1f,0.1f,1,1,0.001f);
        FloatMatrix2D datadiffh=diffh(data);
        System.out.println("data "+data);
        System.out.println("diffh "+datadiffh);
        FloatMatrix2D datadiffv=diffv(data);
        //System.out.println("data "+data);
        System.out.println("diffv "+datadiffv);

        FloatMatrix2D datadiffh2=diffh2(data);
        System.out.println("diffh2 "+datadiffh2);
        FloatMatrix2D datadiffv2=diffv2(data);
        System.out.println("diffv2 "+datadiffv2);

        FloatMatrix2D projk=projk(data,0.01f,5);
        System.out.println("projk = "+projk);
//        data.assign(FloatFunctions.log);
//        FloatMatrix2D v=hardThreshold(data, 0.1f);
//        System.out.println("v="+v);

        FloatMatrix2D xhat=blindInpainting(data,0.1f,0.1f,1,1,0.001f,false);
        System.out.println("xhat="+xhat);
        /*FloatMatrix2D diffh= data.like();
        FloatMatrix2D diffv= data.like();
        data.zAssign8Neighbors(diffh,fh);
        data.zAssign8Neighbors(diffv,fv);

        System.out.println(diffh);
        System.out.println(diffv);
        /*diffh.assign(FloatFunctions.square);
        diffv.assign(FloatFunctions.square);

        diffh.assign(diffv,FloatFunctions.plus);
        diffh.assign(FloatFunctions.sqrt);
        return diffh.zSum();*/

    }
}