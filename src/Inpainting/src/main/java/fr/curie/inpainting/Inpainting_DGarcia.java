package fr.curie.inpainting;

import fr.curie.utils.powell.Brent;
import fr.curie.utils.powell.Function;
import cern.colt.function.tdouble.DoubleFunction;
import cern.colt.function.tdouble.DoubleProcedure;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.DoubleMatrix3D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix3D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.*;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class Inpainting_DGarcia implements ExtendedPlugInFilter, DialogListener {
	PlugInFilterRunner pfr;
	boolean preview;
	int flags = DOES_32 + DOES_16 + DOES_8G + PARALLELIZE_STACKS;
	ImagePlus myimp;
	int niterations=100;
	boolean dimension3D=false;
	boolean zerosToNaN=false;
	boolean showIntermediates = false;
	ImageStack videoOutput;

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

	public void addToVideo(DoubleMatrix2D x, DoubleMatrix2D y, DoubleMatrix2D w, String title){
		FloatProcessor fp= new FloatProcessor(x.columns(),x.rows());
		int heigth = fp.getHeight();
		int width = fp.getWidth();
		for (int j = 0; j < heigth; j++) {
			for (int i = 0; i < width; i++) {
				fp.setf(i, j, (w.getQuick(j,i)==0)?(float)y.getQuick(j,i):(float)x.getQuick(j,i));
			}
		}
		if (videoOutput==null) videoOutput=new ImageStack(fp.getWidth(),fp.getHeight());
		videoOutput.addSlice(title,fp);
	}

	public void displayVideo(String title){
		new ImagePlus(title,videoOutput).show();
	}


	@Override
	public boolean dialogItemChanged(GenericDialog genericDialog, AWTEvent awtEvent) {
		niterations=(int)genericDialog.getNextNumber();
		dimension3D = genericDialog.getNextBoolean();
		zerosToNaN = genericDialog.getNextBoolean();
		showIntermediates=genericDialog.getNextBoolean();
		return true;
	}

	@Override
	public int showDialog(ImagePlus imagePlus, String s, PlugInFilterRunner plugInFilterRunner) {
		this.pfr = plugInFilterRunner;
		preview = true;
		GenericDialog gd = new GenericDialog(s);
		gd.addNumericField("iterations",niterations,0);
		gd.addCheckbox("3D", dimension3D);
		gd.addCheckbox("Inpaint zeros", false);
		gd.addCheckbox("show intermediate images", showIntermediates);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return DONE;
		}
		preview = false;
		niterations=(int)gd.getNextNumber();
		dimension3D = gd.getNextBoolean();
		zerosToNaN = gd.getNextBoolean();
		showIntermediates=gd.getNextBoolean();
		return (dimension3D)?flags:IJ.setupDialog(myimp, flags);
	}

	@Override
	public void setNPasses(int i) {

	}

	@Override
	public int setup(String s, ImagePlus imagePlus) {
		myimp=imagePlus;
		return flags;
	}

	@Override
	public void run(ImageProcessor ipOriginal) {
		ImageProcessor ip=ipOriginal.convertToFloat();
		float[] pixels=(float[]) ip.getPixels();
		if(ipOriginal instanceof ByteProcessor || ipOriginal instanceof ShortProcessor || (ipOriginal instanceof FloatProcessor && zerosToNaN)) {
			for (int index = 0; index < pixels.length; index++) {
				if (pixels[index] == 0) pixels[index] = Float.NaN;
			}
		}
		if(myimp.getImageStackSize()>1&&dimension3D){
			inpainting3D(myimp.getImageStack());
			return;
		}
		if(getNumberOfNaN((float[])ip.getPixels())==0) {
			ImageProcessor ipduplicate = ip.duplicate();
			smoothGarcia(ipduplicate);
			new ImagePlus("smooth", ipduplicate).show();

			ImageProcessor ipduplicate2 = ip.duplicate();
			rsmoothGarcia(ipduplicate2);
			new ImagePlus("rsmooth", ipduplicate2).show();
		}else {
			ImageProcessor ipduplicate3 = ip;
			inpainting(ipduplicate3);
			//new ImagePlus("inpainting", ipduplicate3).show();
			for(int y=0;y<ip.getHeight();y++){
				for(int x=0;x<ip.getWidth();x++){
					ipOriginal.setf(x,y,ipduplicate3.getf(x,y));
				}
			}
			displayVideo(myimp.getTitle()+"_inpaintingSteps");
		}
	}

	public float[] initialGuess(ImageProcessor ip, float[] NaNsPositions){
		//compute EDM map with coordinates of nearest point

		//basic inpainting version
		return (float[])basicInpainting(ip,true,true,true,false).getPixels();
		//return (float[]) Basic_InPainting.basicInpainting(ip,true,true,true,false).getPixels();
	}

	public double[] logspace(double d1, double d2, int nbPoints){
		double[] y = new double[nbPoints];
		double dy = (d2 - d1) / (nbPoints - 1);
		for(int i = 0; i < y.length; i++) {
			y[i] = Math.pow(10,   d1 + (dy * i) );
		}
		y[y.length - 1] = Math.pow(10, d2);
		return y;
	}

	public static ImageProcessor basicInpainting(ImageProcessor ipNaN, boolean xDirection,boolean yDirection, boolean meanfilter, boolean externalFilter){
		ArrayList<ImageProcessor> list=new ArrayList<ImageProcessor>(10);

		int[] kernelFilterXY={1,2,1,2,4,2,1,2,1};
		int[] kernelFilterX={0,0,0,1,2,1,0,0,0};
		int[] KernelFilterY={0,1,0,0,2,0,0,1,0};

		float[] pixels=(float[]) ipNaN.getPixels();
		list.add(ipNaN);
		int currentIndex=0;
		System.out.println("starting image : nb NaN="+getNumberOfNaN((float[])ipNaN.getPixels()));
		//new ImagePlus("NaNs",ipNaN.duplicate()).show();
		while(stillNaN((float[])list.get(currentIndex).getPixels())){
			ImageProcessor reduced=reduceSize(list.get(currentIndex),xDirection,yDirection);
			currentIndex++;
			if(externalFilter){
				ImagePlus tmpImp=new ImagePlus("reduced "+currentIndex,reduced.duplicate());
				IJ.saveAsTiff(tmpImp,IJ.getDirectory("image")+ File.separator+"_reduced"+currentIndex+".tif");
			}
			list.add(reduced);
		}
		System.out.println("number of scale down : "+currentIndex);
		int[] kernelFilter=kernelFilterXY;
		if(xDirection&&!yDirection) {
			kernelFilter=kernelFilterX;
			System.out.println("using X kernel");
		}
		if(!xDirection&&yDirection){
			kernelFilter=KernelFilterY;
			System.out.println("using Y kernel");
		}
		while (currentIndex>0){
			currentIndex--;
			fillNaN(list.get(currentIndex),list.get(currentIndex+1),xDirection,yDirection);
			if(currentIndex!=0&&meanfilter){
				System.out.println("filtering");
				if(externalFilter){
					ImagePlus tmpImp=new ImagePlus("reduced "+currentIndex,list.get(currentIndex).duplicate());
					IJ.saveAsTiff(tmpImp,IJ.getDirectory("current")+ File.separator+"_reduced"+currentIndex+"Filled.tif");
					IJ.runPlugIn(tmpImp, "MRC_Writer", IJ.getDirectory("current")+ File.separator+"_reduced"+currentIndex+"Filled.mrc");
					ImagePlus resultImp=IJ.openImage();
					list.get(currentIndex).copyBits(resultImp.getProcessor(),0,0,Blitter.COPY);
				}else list.get(currentIndex).convolve3x3(kernelFilter);
			}
			//new ImagePlus("reduced inpainted"+currentIndex,list.get(currentIndex)).show();
		}
		//imageProcessor.copyBits(list.get(0), 0, 0, Blitter.COPY);
	   return ipNaN;
	}

	public static ImageStack basicInpating3D(ImageStack isNaN, boolean xDirection,boolean yDirection,boolean zDirection, boolean meanfilter, boolean externalFilter){
		ArrayList<ImageStack> list = new ArrayList<ImageStack>(10);
		list.add(isNaN);
		int currentIndex=0;
		System.out.println("starting image : nb NaN="+getNumberOfNaN(isNaN));
		//new ImagePlus("NaNs",ipNaN.duplicate()).show();
		while(stillNaN(list.get(currentIndex))){
			ImageStack reduced=reduceSize(list.get(currentIndex),xDirection,yDirection,zDirection);
			currentIndex++;
			//new ImagePlus("reduced "+currentIndex,reduced.duplicate()).show();
			list.add(reduced);
		}
		System.out.println("number of scale down : "+currentIndex);
		int[] kernelFilterXY={1,2,1,2,4,2,1,2,1};
		int[] kernelFilterX={0,0,0,1,2,1,0,0,0};
		int[] KernelFilterY={0,1,0,0,2,0,0,1,0};
		int[] kernelFilter=kernelFilterXY;
		if(xDirection&&!yDirection) kernelFilter=kernelFilterX;
		if(!xDirection&&yDirection) kernelFilter=KernelFilterY;
		while (currentIndex>0){
			currentIndex--;
			fillNaN(list.get(currentIndex),list.get(currentIndex+1),xDirection,yDirection,zDirection);
			if(currentIndex!=0&&meanfilter){
				ImageStack is=list.get(currentIndex);
				for(int z=1;z<=is.getSize();z++){
					is.getProcessor(z).convolve3x3(kernelFilter);
				}
			}
			//new ImagePlus("reduced inpainted"+currentIndex,list.get(currentIndex)).show();
		}
		return isNaN;



	}

	static int getNumberOfNaN(float[] data){
		int count=0;
		for(float f:data){
			if (Float.isNaN(f))count++;
		}
		return count;
	}

	static int getNumberOfNaN(ImageStack is){
		int count=0;
		for(int z=1;z<is.getSize();z++){
			count+=getNumberOfNaN((float[])is.getPixels(z));
		}
		return count;
	}

	static boolean stillNaN(float[] data){
		for(float f:data){
			if(Float.isNaN(f))return true;
		}
		return false;
	}

	static boolean stillNaN(ImageStack is){
		for(int z=1;z<=is.getSize();z++){
			if(stillNaN((float[])is.getPixels(z))) return true;
		}
		return false;
	}

	static ImageStack reduceSize(ImageStack is, boolean xDirection, boolean yDirection, boolean zDirection){
		int newWidth=(xDirection)?is.getWidth()/2:is.getWidth();
		int newHeight=(yDirection)?is.getHeight()/2:is.getHeight();
		int newDepth=(zDirection)?is.getSize()/2:is.getSize();
		ImageStack reduced = new ImageStack(newWidth,newHeight);
		for(int z=0;z<newDepth;z++) {
			ImageProcessor reducedip = is.getProcessor(1).createProcessor(newWidth, newHeight);
			int correspondingZ=(zDirection)?z*2:z;
			ImageProcessor ip1=is.getProcessor(correspondingZ+1); //+1 for the change of base in imageStack (not starting at 0)
			ImageProcessor ip2=(zDirection)? is.getProcessor(correspondingZ+2):ip1;
			for (int y = 0; y < newHeight; y++) {
				for (int x = 0; x < newWidth; x++) {
					int count = 0;
					float sum = 0;
					int xreduced=(xDirection)?x*2:x;
					int yreduced=(yDirection)?y*2:y;
					float val = ip1.getf(xreduced, yreduced);
					if (!Float.isNaN(val)) {
						sum += val;
						count++;
					}
					if(xDirection) {
						val = ip1.getf(xreduced + 1, yreduced);
						if (!Float.isNaN(val)) {
							sum += val;
							count++;
						}
					}
					if(yDirection) {
						val = ip1.getf(xreduced, yreduced + 1);
						if (!Float.isNaN(val)) {
							sum += val;
							count++;
						}
					}
					if(xDirection&&yDirection) {
						val = ip1.getf(xreduced + 1, yreduced + 1);
						if (!Float.isNaN(val)) {
							sum += val;
							count++;
						}
					}
					if(zDirection){
						val = ip2.getf(xreduced, yreduced);
						if (!Float.isNaN(val)) {
							sum += val;
							count++;
						}
						if(xDirection) {
							val = ip2.getf(xreduced + 1, yreduced);
							if (!Float.isNaN(val)) {
								sum += val;
								count++;
							}
						}
						if(yDirection) {
							val = ip2.getf(xreduced, yreduced + 1);
							if (!Float.isNaN(val)) {
								sum += val;
								count++;
							}
						}
						if(xDirection&&yDirection) {
							val = ip2.getf(xreduced + 1, yreduced + 1);
							if (!Float.isNaN(val)) {
								sum += val;
								count++;
							}
						}
					}
					sum = (count == 0) ? Float.NaN : sum / count;
					reducedip.setf(x, y, sum);

				}
			}
			reduced.addSlice(is.getSliceLabel(z+1),reducedip);
		}
		return reduced;
	}

	static ImageProcessor reduceSize(ImageProcessor ip, boolean xDirection,boolean yDirection){
		int newWidth=(xDirection)?ip.getWidth()/2:ip.getWidth();
		int newHeight=(yDirection)?ip.getHeight()/2:ip.getHeight();
		ImageProcessor reduced=ip.createProcessor(newWidth,newHeight);
		for(int y=0;y<reduced.getHeight();y++){
			for(int x=0;x<reduced.getWidth();x++){
				int count=0;
				float sum=0;
				int xreduced=(xDirection)?x*2:x;
				int yreduced=(yDirection)?y*2:y;
				float val=ip.getf(xreduced,yreduced);
				if(!Float.isNaN(val)){
					sum+=val;
					count++;
				}
				if(xDirection) {
					val = ip.getf(xreduced + 1, yreduced);
					if (!Float.isNaN(val)) {
						sum += val;
						count++;
					}
				}
				if(yDirection) {
					val = ip.getf(xreduced, yreduced + 1);
					if (!Float.isNaN(val)) {
						sum += val;
						count++;
					}
				}
				if(xDirection&&yDirection) {
					val = ip.getf(xreduced + 1, yreduced + 1);
					if (!Float.isNaN(val)) {
						sum += val;
						count++;
					}
				}
				sum=(count==0)?Float.NaN:sum/count;
				reduced.setf(x,y,sum);

			}
		}
		return reduced;
	}

	static float[] reduceSize(float[] data){
		int newSize=data.length/2;
		float[] reduced=new float[newSize];
		for(int i=0;i<reduced.length;i++) {
			int count = 0;
			float sum = 0;
			int indexBig = i * 2;
			if (!Float.isNaN(data[indexBig])) {
				sum += data[indexBig];
				count++;
			}
			indexBig=Math.min(indexBig+1, data.length-1);
			if (!Float.isNaN(data[indexBig])) {
				sum += data[indexBig];
				count++;
			}
			sum = (count == 0) ? Float.NaN : sum / count;
			reduced[i] = sum;
		}

		return reduced;
	}

	static void fillNaN(float[] dataNaN, float[] dataReduced){
		for(int i=0;i<dataNaN.length;i++){
			if(Float.isNaN(dataNaN[i])){
				dataNaN[i]=dataReduced[i/2];
			}
		}
	}

	static void fillNaN(ImageProcessor ipNaN, ImageProcessor ipReduced, boolean xDirection,boolean yDirection){
		for(int y=0;y<ipNaN.getHeight();y++){
			for(int x=0;x<ipNaN.getWidth();x++){
				if(Double.isNaN(ipNaN.getPixelValue(x,y))){
					int reducedX=(xDirection)?x/2:x;
					int reducedY=(yDirection)?y/2:y;
					ipNaN.setf(x,y,(float)ipReduced.getInterpolatedPixel(reducedX,reducedY));
				}
			}
		}

	}

	static void fillNaN(ImageStack isNaN, ImageStack isReduced, boolean xDirection,boolean yDirection,boolean zDirection){
		for (int z=0;z<isNaN.getSize();z++){
			ImageProcessor ipNaN=isNaN.getProcessor(z+1);
			int reducedZ=(zDirection)?z/2:z;
			ImageProcessor ipReduced=isReduced.getProcessor(Math.min(reducedZ+1,isReduced.getSize()));
			for(int y=0;y<ipNaN.getHeight();y++){
				for(int x=0;x<ipNaN.getWidth();x++){
					if(Double.isNaN(ipNaN.getPixelValue(x,y))){
						int reducedX=(xDirection)?x/2:x;
						int reducedY=(yDirection)?y/2:y;
						ipNaN.setf(x,y,(float)ipReduced.getInterpolatedPixel(reducedX,reducedY));
					}
				}
			}
		}
	}


	public void smoothGarcia(ImageProcessor ip){
		DoubleMatrix2D lambda=createLambda(ip.getWidth(),ip.getHeight());
		float[] originalPixels=(float[])ip.getPixels();
		DenseDoubleMatrix2D y = new DenseDoubleMatrix2D(ip.getHeight(), ip.getWidth());
		y.assign(originalPixels);

		y.dct2(true);
		GCVs gvcs=new GCVs(lambda,y);
		Brent brentoptimizer=new Brent(gvcs, -15,0, 38);
		double p=brentoptimizer.getXmin();
		System.out.println("p="+p+" fmin="+brentoptimizer.getFmin()+" with iteration="+brentoptimizer.getIter());
		DoubleMatrix2D gamma=computeGamma(lambda,Math.pow(10,p));
		y.assign(gamma,DoubleFunctions.mult);
		y.idct2(true);
		//put back the original points
		Arrays.fill(originalPixels,0);
		double[] result=((DenseDoubleMatrix2D) y).elements();
		for(int i=0;i<originalPixels.length;i++){
			originalPixels[i]=(float)result[i];
		}
	}

	public void rsmoothGarcia(ImageProcessor ip){
		DoubleMatrix2D lambda=createLambda(ip.getWidth(),ip.getHeight());

		float[] originalPixels=(float[])ip.getPixels();
		DenseDoubleMatrix2D y = new DenseDoubleMatrix2D(ip.getHeight(), ip.getWidth());
		y.assign(originalPixels);

		DoubleMatrix2D w = new DenseDoubleMatrix2D(ip.getHeight(), ip.getWidth());
		w.assign(1);

		DoubleMatrix2D zz= y.copy();
		for(int k=1; k<=6;k++){
			System.out.println("k="+k);
			double tol=Double.POSITIVE_INFINITY;
			double p=3;
			DoubleMatrix2D z=zz;
			while(tol>0.00001){
				GCVs2 gvcs=new GCVs2(lambda, y,w,zz);
				Brent brentoptimizer=new Brent(gvcs, -15,0, 38);
				p=brentoptimizer.getXmin();
				System.out.println("p="+p+" fmin="+brentoptimizer.getFmin()+" with iteration="+brentoptimizer.getIter());
				z=gvcs.computez(p);
				DoubleMatrix2D tmp=zz.copy();
				tmp.assign(z,DoubleFunctions.minus);
				tol=Math.sqrt(tmp.aggregate(DoubleFunctions.plus,DoubleFunctions.square))/Math.sqrt(z.aggregate(DoubleFunctions.plus,DoubleFunctions.square));
				System.out.println("tol="+tol);
				zz=z;
			}
			double s=Math.pow(10,p);
			double tmp2=Math.sqrt(1+16*s);
			double h=Math.pow(Math.sqrt(1+tmp2)/Math.sqrt(2)/tmp2,lambda.size());
			w=bisquare(y.copy().assign(z,DoubleFunctions.minus),h);
		}
		Arrays.fill(originalPixels,0);
		double[] result=((DenseDoubleMatrix2D) zz).elements();
		for(int i=0;i<originalPixels.length;i++){
			originalPixels[i]=(float)result[i];
		}
	}

	public void inpainting(ImageProcessor ip){
		//create lambda
		DoubleMatrix2D lambda=createLambda(ip.getWidth(),ip.getHeight());


		float[] originalPixels=(float[])ip.getPixels();
		DenseDoubleMatrix2D x = new DenseDoubleMatrix2D(ip.getHeight(), ip.getWidth());
		x.assign(originalPixels);
		addToVideo(x,"input");
		//init w
		float[]W=new float[originalPixels.length];
		for(int i=0;i<W.length;i++){
			W[i]=(Float.isNaN(originalPixels[i]))?(byte)0:(byte)1;
		}
		DoubleMatrix2D w = new DenseDoubleMatrix2D(ip.getHeight(), ip.getWidth());
		w.assign(W);

		//create initial guess
		float[] ytmp= initialGuess(ip.duplicate(),W);
		DoubleMatrix2D y = new DenseDoubleMatrix2D(ip.getHeight(), ip.getWidth());
		y.assign(ytmp);
		int s0=3;
		addToVideo(y,"initial guess (basic inpainting)");
		//new ImagePlus("initial guess", new FloatProcessor(ip.getWidth(),ip.getHeight(),ytmp)).show();

		//replace NaN par zeros
		x.assign(new DoubleFunction(){
			@Override
			public double apply(double v) {
				if(Double.isNaN(v))return 0;
				return v;
			}
		});

		//smoothness parameters
		double[] s = logspace(s0,-6,niterations);
		double rf=2;
		double m=2;

		lambda.assign(DoubleFunctions.pow(m));
		//smoothing
		DoubleMatrix2D gamma;
		for(int i=0;i<niterations;i++){
			final double si=s[i];
			gamma=computeGamma(lambda,s[i]) ;
			y=iterate(x,y,w,gamma,rf);
			addToVideo(x,y,w,"iteration "+i);
		} 
		//put back the original points
		double[] result=((DenseDoubleMatrix2D) y).elements();
		for(int i=0;i<originalPixels.length;i++){
			if(W[i]==0) originalPixels[i]=(float)result[i];
		}

	}

	public DoubleMatrix2D createLambda(int nx,int ny){
		//init lambda
		double[]lambda = new double[nx*ny];
		double l;
		for(int j=0;j<ny;j++){
			int yy=j*nx;
			for(int i=0;i<nx;i++){
				l= (-2+2 * Math.cos(j*Math.PI/ny)) + (-2 + 2*Math.cos(i*Math.PI/nx));
				lambda[yy+i]= l;
			}
		}
		DenseDoubleMatrix2D lambdaMatrix = new DenseDoubleMatrix2D(ny, nx);
		lambdaMatrix.assign(lambda);

		System.out.println(lambdaMatrix.viewRow(0));

		return lambdaMatrix;
	}
	DoubleMatrix2D computeGamma(DoubleMatrix2D lambda, double s){
		DoubleMatrix2D gamma=lambda.copy();
		gamma.assign(DoubleFunctions.square);
		gamma.assign(DoubleFunctions.mult(s));
		gamma.assign(DoubleFunctions.plus(1));
		gamma.assign(new DoubleFunction() {
			@Override
			public double apply(double v) {
				return 1/v;
			}
		});
		return gamma;
	}
	DoubleMatrix2D bisquare(DoubleMatrix2D r,double h){
		DoubleMatrix1D rv=r.copy().vectorize().viewSorted();
		double median=rv.getQuick((int) (rv.size()/2));
		rv.assign(DoubleFunctions.minus(median));
		rv.assign(DoubleFunctions.abs);
		rv=rv.viewSorted();

		double mad=rv.getQuick((int)(rv.size()/2));
		DoubleMatrix2D u=r.copy();
		u.assign(DoubleFunctions.div(1.4826*mad));
		u.assign(DoubleFunctions.div(Math.sqrt(1-h)));
		u.assign(DoubleFunctions.abs);
		u.assign(DoubleFunctions.div(4.685));
		u.assign( new DoubleFunction() {
			@Override
			public double apply(double v) {
				if(v<1) {
					double tmp = 1 - (v * v);
					return tmp * tmp;
				}else return 0;
			}
		});
		return u;
	}

	DoubleMatrix2D iterate(DoubleMatrix2D x, DoubleMatrix2D y, DoubleMatrix2D w, DoubleMatrix2D gamma, double rf){
		DenseDoubleMatrix2D tmp=(DenseDoubleMatrix2D)x.copy();
		tmp.assign(y,DoubleFunctions.minus).assign(w,DoubleFunctions.mult).assign(y,DoubleFunctions.plus);
		tmp.dct2(true);
		tmp.assign(gamma,DoubleFunctions.mult);
		tmp.idct2(true);
		tmp.assign(DoubleFunctions.mult(rf));
		tmp.assign(y.copy().assign(DoubleFunctions.mult(1-rf)),DoubleFunctions.plus);
		return tmp;


	}

	public void inpainting3D(ImageStack stack){
		//create lambda
		DoubleMatrix3D lambda=createLambda3D(stack.getWidth(),stack.getHeight(),stack.getSize());

		int sxy=stack.getWidth()*stack.getHeight();
		double[] originalPixels= new double[sxy*stack.getSize()];
		for(int z=0;z<stack.size();z++){
			float[] tmp= (float[])stack.getPixels(z+1);
			int zz=z*sxy;
			for(int index=0;index <tmp.length;index++){
				originalPixels[zz+index]=tmp[index];
			}
			//System.arraycopy(tmp,0,originalPixels,z*sxy,sxy);

		}
		DenseDoubleMatrix3D x = new DenseDoubleMatrix3D(stack.size(),stack.getHeight(), stack.getWidth());
		x.assign(originalPixels);
		//init w
		double[]W=new double[originalPixels.length];
		for(int i=0;i<W.length;i++){
			W[i]=(Double.isNaN(originalPixels[i]))?(byte)0:(byte)1;
		}
		DoubleMatrix3D w = new DenseDoubleMatrix3D(stack.size(),stack.getHeight(), stack.getWidth());
		w.assign(W);

		//create initial guess
		ImageStack ytmp= basicInpating3D(stack.duplicate(),true,true,true,true,false);
		double[] yPixels= new double[sxy*stack.getSize()];
		for(int z=0;z<stack.size();z++){
			float[] tmp= (float[])ytmp.getPixels(z+1);
			int zz=z*sxy;
			for(int index=0;index <tmp.length;index++){
				yPixels[zz+index]=tmp[index];
			}
			//System.arraycopy(tmp,0,yPixels,z*sxy,sxy);

		}
		DoubleMatrix3D y = new DenseDoubleMatrix3D(stack.size(),stack.getHeight(), stack.getWidth());
		y.assign(yPixels);

		int s0=3;
		//new ImagePlus("initial guess", new FloatProcessor(stack.getWidth(),stack.getHeight(),ytmp)).show();

		//replace NaN par zeros
		x.assign(new DoubleFunction(){
			@Override
			public double apply(double v) {
				if(Double.isNaN(v))return 0;
				return v;
			}
		});

		//smoothness parameters
		double[] s = logspace(s0,-6,niterations);
		double rf=2;
		double m=2;

		lambda.assign(DoubleFunctions.pow(m));
		//smoothing
		DoubleMatrix3D gamma;
		for(int i=0;i<niterations;i++){
			final double si=s[i];
			gamma=computeGamma3D(lambda,s[i]) ;
			y=iterate3D(x,y,w,gamma,rf);
		}
		//put back the original points
		double[] result=((DenseDoubleMatrix3D) y).elements();
//		for(int i=0;i<originalPixels.length;i++){
//			if(W[i]==0) originalPixels[i]=(float)result[i];
//		}
		for(int z=0;z<stack.size();z++){
			float[] tmp= (float[])stack.getPixels(z+1);
			int zz=z*sxy;
			for(int index=0;index <tmp.length;index++){
				if(W[zz+index]==0) tmp[index]=(float)result[zz+index];
			}
			//System.arraycopy(result,z*sxy,tmp,0,sxy);
		}

	}

	public DoubleMatrix3D createLambda3D(int nx,int ny, int nz){
		//init lambda
		double[]lambda = new double[nx*ny*nz];
		double l;
		for(int k=0;k<nz;k++) {
			int zz= k*nx*ny;
			for (int j = 0; j < ny; j++) {
				int yy = j * nx;
				for (int i = 0; i < nx; i++) {
					l = (-2 + 2 * Math.cos(j * Math.PI / ny)) + (-2 + 2 * Math.cos(i * Math.PI / nx))+ (-2 + 2 * Math.cos(k * Math.PI / nz));
					lambda[zz+yy + i] = l;
				}
			}
		}
		DenseDoubleMatrix3D lambdaMatrix = new DenseDoubleMatrix3D(nz,ny, nx);
		lambdaMatrix.assign(lambda);

		System.out.println(lambdaMatrix.viewRow(0));

		return lambdaMatrix;
	}
	DoubleMatrix3D computeGamma3D(DoubleMatrix3D lambda, double s){
		DoubleMatrix3D gamma=lambda.copy();
		gamma.assign(DoubleFunctions.square);
		gamma.assign(DoubleFunctions.mult(s));
		gamma.assign(DoubleFunctions.plus(1));
		gamma.assign(new DoubleFunction() {
			@Override
			public double apply(double v) {
				return 1/v;
			}
		});
		return gamma;
	}

	DoubleMatrix3D iterate3D(DoubleMatrix3D x, DoubleMatrix3D y, DoubleMatrix3D w, DoubleMatrix3D gamma, double rf){
		DenseDoubleMatrix3D tmp=(DenseDoubleMatrix3D)x.copy();
		tmp.assign(y,DoubleFunctions.minus).assign(w,DoubleFunctions.mult).assign(y,DoubleFunctions.plus);
		tmp.dct3(true);
		tmp.assign(gamma,DoubleFunctions.mult);
		tmp.idct3(true);
		tmp.assign(DoubleFunctions.mult(rf));
		tmp.assign(y.copy().assign(DoubleFunctions.mult(1-rf)),DoubleFunctions.plus);
		return tmp;


	}



/*################################################################################################*/
/*#                                                                                              #*/
/*#        internal classes for finding minimum of function                                      #*/
/*#                                                                                              #*/
/*#                                                                                              #*/
/*#                                                                                              #*/
/*#                                                                                              #*/
/*#                                                                                              #*/
/*#                                                                                              #*/
/*#                                                                                              #*/
/*################################################################################################*/
	class GCVs2 extends Function {
		DoubleMatrix2D y;
		DoubleMatrix2D lambda;
		DenseDoubleMatrix2D DCTy;
		DoubleMatrix2D gamma;
		DoubleMatrix2D w;
		DoubleMatrix2D result;

		public GCVs2(DoubleMatrix2D lambda, DoubleMatrix2D y, DoubleMatrix2D w, DoubleMatrix2D zz){
			this.lambda=lambda.copy();
			this.y=y.copy();
			DCTy=(DenseDoubleMatrix2D)y.copy();
			DCTy.assign(zz,DoubleFunctions.minus).assign(w,DoubleFunctions.mult).assign(zz,DoubleFunctions.plus);
			DCTy.dct2(true);
			this.w=w.copy();
		}

		@Override
		protected int length() {
			return 1;
		}

		@Override
		protected double eval(double[] xt) {
			DoubleMatrix2D z=computez(xt[0]);
			DoubleMatrix2D tmp=y.copy();
			tmp.assign(z,DoubleFunctions.minus);
			tmp.assign(w.copy().assign(DoubleFunctions.sqrt),DoubleFunctions.mult);
			double rss = tmp.aggregate(DoubleFunctions.plus,DoubleFunctions.square);
			double trh= gamma.zSum();
			double score = rss/lambda.size()/((1-trh/lambda.size())*(1-trh/lambda.size()));
			//System.out.println("p="+xt[0]+" score="+score);
			return score;
		}

		public DenseDoubleMatrix2D computez(double p){
			double s=Math.pow(10,p);
			gamma=computeGamma(lambda,s);
			DenseDoubleMatrix2D z= (DenseDoubleMatrix2D)DCTy.copy();
			z.assign(gamma,DoubleFunctions.mult);
			z.idct2(true);
			return z;
		}

		public DenseDoubleMatrix2D getDCTy() {
			return DCTy;
		}

		protected double eval1(double[] xt) {
			double s=Math.pow(10,xt[0]);
			DoubleMatrix2D gamma=computeGamma(lambda,s);

			DenseDoubleMatrix2D tmp= (DenseDoubleMatrix2D)DCTy.copy();
			tmp.assign(gamma.copy().assign(DoubleFunctions.minus(1)),DoubleFunctions.mult);
			//double rss = new DenseDoubleAlgebra().vectorNorm2(tmp);
			//rss*=rss;
			double rss = tmp.aggregate(DoubleFunctions.plus,DoubleFunctions.square);
			double trh= gamma.aggregate(DoubleFunctions.plus, DoubleFunctions.identity);
			double score = rss/lambda.size()/((1-trh/lambda.size())*(1-trh/lambda.size()));
			System.out.println("p="+xt[0]+" score="+score);
			return score;
		}
		protected double eval2(double[] xt) {
			double s=Math.pow(10,xt[0]);
			DoubleMatrix2D gamma=computeGamma(lambda,s);

			DenseDoubleMatrix2D tmp= (DenseDoubleMatrix2D)DCTy.assign(gamma,DoubleFunctions.mult);
			tmp.idct2(true);
			result=tmp.copy();

			w.assign(DoubleFunctions.sqrt);

			double rss = new DenseDoubleAlgebra().vectorNorm2(tmp);
			rss*=rss;
			double trh= gamma.aggregate(DoubleFunctions.plus, DoubleFunctions.identity);
			double score = rss/lambda.size()/((1-trh/lambda.size())*(1-trh/lambda.size()));
			System.out.println("p="+xt[0]+" score="+score);
			return score;
		}
	}
	class GCVs extends Function {
		DoubleMatrix2D lambda;
		DoubleMatrix2D DCTy;
		DoubleMatrix2D gamma;
		DoubleMatrix2D w;
		DoubleMatrix2D result;

		public GCVs(DoubleMatrix2D lambda, DoubleMatrix2D DCTy){
			this.lambda=lambda.copy();
			this.DCTy=DCTy.copy();
		}

		public GCVs(DoubleMatrix2D lambda, DoubleMatrix2D DCTy, DoubleMatrix2D w){
			this.lambda=lambda.copy();
			this.DCTy=DCTy.copy();
			this.w=w.copy();
		}
		@Override
		protected int length() {
			return 1;
		}

		@Override
		protected double eval(double[] xt) {
			if(w==null) return eval1(xt);
			return eval2(xt);
		}

		protected double eval1(double[] xt) {
			double s=Math.pow(10,xt[0]);
			DoubleMatrix2D gamma=computeGamma(lambda,s);

			DenseDoubleMatrix2D tmp= (DenseDoubleMatrix2D)DCTy.copy();
			tmp.assign(gamma.copy().assign(DoubleFunctions.minus(1)),DoubleFunctions.mult);
			//double rss = new DenseDoubleAlgebra().vectorNorm2(tmp);
			//rss*=rss;
			double rss = tmp.aggregate(DoubleFunctions.plus,DoubleFunctions.square);
			double trh= gamma.aggregate(DoubleFunctions.plus, DoubleFunctions.identity);
			double score = rss/lambda.size()/((1-trh/lambda.size())*(1-trh/lambda.size()));
			System.out.println("p="+xt[0]+" score="+score);
			return score;
		}
		protected double eval2(double[] xt) {
			double s=Math.pow(10,xt[0]);
			DoubleMatrix2D gamma=computeGamma(lambda,s);

			DenseDoubleMatrix2D tmp= (DenseDoubleMatrix2D)DCTy.assign(gamma,DoubleFunctions.mult);
			tmp.idct2(true);
			result=tmp.copy();

			w.assign(DoubleFunctions.sqrt);
			
			double rss = new DenseDoubleAlgebra().vectorNorm2(tmp);
			rss*=rss;
			double trh= gamma.aggregate(DoubleFunctions.plus, DoubleFunctions.identity);
			double score = rss/lambda.size()/((1-trh/lambda.size())*(1-trh/lambda.size()));
			System.out.println("p="+xt[0]+" score="+score);
			return score;
		}
	}

}
