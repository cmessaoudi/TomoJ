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
import org.bytedeco.javacpp.*;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static org.bytedeco.javacpp.opencv_core.*;

public class Inpainting_OpenCV implements ExtendedPlugInFilter, DialogListener {
	PlugInFilterRunner pfr;
	boolean preview;
	int flags = DOES_32 + DOES_8G + DOES_16 + PARALLELIZE_STACKS ;
	ImagePlus myimp;
	int niterations = 100;
	boolean dimension3D = false;
	String[] algosChoice=new String[]{"Navier-Stokes","Telea"};
	boolean zerosToNaN=false;
	int algoIndex=1;
	double radius=20;

	@Override
	public boolean dialogItemChanged(GenericDialog genericDialog, AWTEvent awtEvent) {
		dimension3D = genericDialog.getNextBoolean();
		zerosToNaN = genericDialog.getNextBoolean();
		algoIndex=genericDialog.getNextChoiceIndex();
		radius = genericDialog.getNextNumber();
		return true;
	}

	@Override
	public int showDialog(ImagePlus imagePlus, String s, PlugInFilterRunner plugInFilterRunner) {
		this.pfr = plugInFilterRunner;
		preview = true;
		GenericDialog gd = new GenericDialog(s);
		gd.addCheckbox("3D", dimension3D);
		gd.addCheckbox("inpaint zeros",zerosToNaN);
		gd.addChoice("algorithm",algosChoice,algosChoice[1]);
		gd.addNumericField("radius", 20,0);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return DONE;
		}
		preview = false;
		dimension3D = gd.getNextBoolean();
		zerosToNaN = gd.getNextBoolean();
		algoIndex=gd.getNextChoiceIndex();
		radius = gd.getNextNumber();
		return (dimension3D) ? flags : IJ.setupDialog(myimp, flags);
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
		ImageProcessor iptmp=ip.convertToByte(true);
		ImageProcessor result=ip.duplicate();
		result.setColor(0);
		result.fill();
		Pointer pixsIp;
		Pointer pixsResult;
		Mat matIp;
		int type;
		Mat matResult;
		if(ip instanceof ByteProcessor)	{
			matIp=new Mat((byte[]) ip.getPixels(),false);
			matResult=new Mat((byte[]) result.getPixels());
//			pixsIp= new BytePointer(ByteBuffer.wrap((byte[]) ip.getPixels()));
//			pixsResult = new BytePointer(ByteBuffer.wrap((byte[]) result.getPixels()));
//			type=CV_8U;
		}
		else if(ip instanceof ShortProcessor){
			matIp=new Mat((short[]) ip.getPixels());
			matResult=new Mat((short[]) result.getPixels());
//			pixsIp = new ShortPointer(ShortBuffer.wrap((short[])ip.getPixels()));
//			pixsResult = new ShortPointer(ShortBuffer.wrap((short[])result.getPixels()));
//			type=CV_16U;
		}
		else if(ip instanceof FloatProcessor){
			matIp=new Mat((float[]) ip.getPixels());
			matResult=new Mat((float[]) result.getPixels());
			//pixsIp = new FloatPointer(FloatBuffer.wrap((float[])ip.getPixels()));
			//pixsResult = new FloatPointer(FloatBuffer.wrap((float[])result.getPixels()));
			//type=CV_32F;
		}
		else return;

		ByteProcessor mask = createMask(ip);
		new ImagePlus("mask",mask).show();
		Mat matMask = new Mat((byte[]) mask.getPixels());
//		Pointer pixsMask = new BytePointer(ByteBuffer.wrap((byte[]) mask.getPixels()));
//		opencv_core.Mat matMask = new opencv_core.Mat(mask.getWidth(), mask.getHeight(), CV_8U, pixsMask);

		//opencv_imgproc.GaussianBlur(matIp, matIp, new Size(7,7), 1.5);
		//opencv_imgproc.GaussianBlur(matIp, matResult, new Size(7,7), 1.5);



		opencv_photo.inpaint(matIp, matMask, matResult, radius, (algoIndex==0)?opencv_photo.INPAINT_NS:opencv_photo.INPAINT_TELEA);
		//opencv_photo.fastNlMeansDenoising(matIp,matResult,5,7,21);
		//result.setPixels(matResult.asBuffer().array());
		if(ip instanceof ByteProcessor)	{
			ByteBuffer bb=matResult.createBuffer();
			byte[] pixs=(byte[]) result.getPixels();
			for(int i=0;i<pixs.length;i++) pixs[i]=bb.get(i);
		}
		else if(ip instanceof ShortProcessor){
			ShortBuffer bb=matResult.createBuffer();
			short[] pixs=(short[]) result.getPixels();
			for(int i=0;i<pixs.length;i++) pixs[i]=bb.get(i);

		}
		else if(ip instanceof FloatProcessor){
			FloatBuffer bb=matResult.createBuffer();
			float[] pixs=(float[]) result.getPixels();
			for(int i=0;i<pixs.length;i++) pixs[i]=bb.get(i);

		}
		else return;
		//matResult.push_back_(pixsResult);
		new ImagePlus(myimp.getTitle()+"_inpainted_"+algosChoice[algoIndex],result).show();


	}

	public ByteProcessor createMask(ImageProcessor ip){
		ByteProcessor mask=new ByteProcessor(ip.getWidth(),ip.getHeight());
		for(int y=0;y<ip.getHeight();y++){
			for(int x=0;x<ip.getWidth();x++){
				float val=ip.getf(x,y);
				if(Float.isNaN(val)|| (zerosToNaN && val==0)){
					mask.set(x,y,255);
				}
			}
		}
		mask.filter(ImageProcessor.MAX);
		return mask;
	}
}
