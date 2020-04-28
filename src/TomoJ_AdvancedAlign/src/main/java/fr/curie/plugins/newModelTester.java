package fr.curie.plugins;

import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.jet.math.tdouble.DoubleFunctions;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import junit.framework.TestCase;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.landmarks.AlignmentLandmarkImproved;
import fr.curie.tomoj.landmarks.AlignmentLandmarksOptions;
import fr.curie.tomoj.landmarks.Landmarks3DAlign;
import fr.curie.utils.Chrono;
import fr.curie.utils.MatrixUtils;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.workflow.CommandWorkflow;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;

import static java.lang.Math.toRadians;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 17 mars 2011
 * Time: 14:44:51
 * To change this template use File | Settings | File Templates.
 */
public class newModelTester extends TestCase implements PlugIn {
    double tilt_axis = 12;
    double tilt_angleStart = -60;
    double tilt_angleEnd = 60;
    double tilt_angleIncrement = 2;
    ImagePlus rec;
    int numberOfPoints;
    double maxShift;
    double maxpsii;
    double modmi;
    double modti;
    double modsi;
    double maxdeltai;
    double factor;
    String path="";

    int size =256;
    double center;

    ArrayList<DoubleMatrix2D> DiSTrue;
    

    public void run(String arg) {
        GenericDialog gd = new GenericDialog("projection parameters");
        gd.addStringField("path", "D:\\images_test\\phantom\\testAlignDebug\\");
        gd.addNumericField("size",size,0);
        gd.addNumericField("position in z factor (0=all central plane, 1 in all volume)",1.0,2);
        gd.addNumericField("tilt axis", tilt_axis, 1);
        gd.addNumericField("start tilt", tilt_angleStart, 1);
        gd.addNumericField("end tilt", tilt_angleEnd, 1);
        gd.addNumericField("increment tilt", tilt_angleIncrement, 1);
        gd.addNumericField("number of points", 100, 0);
        gd.addNumericField("max shift", 0, 1);
        gd.addNumericField("max psii", 0, 2);
        gd.addNumericField("max mod mi", 0, 2);
        gd.addNumericField("max mod ti", 0, 2);
        gd.addNumericField("max mod si", 0, 2);
        gd.addNumericField("max deltai", 0, 2);
        gd.addCheckbox("dual tilt", false);
        gd.addNumericField("tilt axis", tilt_axis, 1);
        gd.addNumericField("phi", 90, 2);
        gd.addNumericField("theta", 0, 2);
        gd.addNumericField("psi", 0, 2);
        gd.addNumericField("Tx", 0, 2);
        gd.addNumericField("Ty", 0, 2);
        gd.addNumericField("Tz", 0, 2);
        gd.addNumericField("global mag", 1, 2);
        gd.addNumericField("scale X", 1, 2);
        gd.addNumericField("scale Z", 1, 2);
        gd.addNumericField("shearing angle", 0, 2);

        gd.showDialog();
        if (gd.wasCanceled()) return;
        path=gd.getNextString();
        if(!(path.endsWith("/")||path.endsWith("\\")) ) path+="/";
        size=(int) gd.getNextNumber();
        factor=gd.getNextNumber();
        center=(size-1.0)/2.0;
        tilt_axis = gd.getNextNumber();
        tilt_angleStart = gd.getNextNumber();
        tilt_angleEnd = gd.getNextNumber();
        tilt_angleIncrement = gd.getNextNumber();
        numberOfPoints = (int) gd.getNextNumber();
        maxShift = gd.getNextNumber();
        maxpsii = gd.getNextNumber();
        modmi = gd.getNextNumber();
        modti = gd.getNextNumber();
        modsi = gd.getNextNumber();
        maxdeltai = gd.getNextNumber();
        double uaxis_alpha = 90 + tilt_axis;
        double uaxis_beta = 90;
        DoubleMatrix1D axis = MatrixUtils.eulerDirection(toRadians(uaxis_alpha), toRadians(uaxis_beta));

        double[] angles = new double[(int) ((tilt_angleEnd - tilt_angleStart) / tilt_angleIncrement + 1)];
        angles[0] = tilt_angleStart;
        for (int i = 1; i < angles.length; i++) {
            angles[i] = angles[i - 1] + tilt_angleIncrement;
        }

        ArrayList<DoubleMatrix2D> rjs=createRjs();
        TiltSeries ts=createTiltSeries(rjs,angles,uaxis_alpha,uaxis_beta,null,null);
        //rec.show();
        FileSaver fs = new FileSaver(rec);
        fs.saveAsTiffStack(path+"rjs.tif");

        fs = new FileSaver(ts);
        fs.saveAsTiffStack(path+"tiltseries1.tif");


        CommandWorkflow.saveLandmarks(path, "landmarks1.txt", ts);
        ts.show();

        if (gd.getNextBoolean()) {
            double tilt_axis2 = gd.getNextNumber();
            double phi = gd.getNextNumber();
            double theta = gd.getNextNumber();
            double psi = gd.getNextNumber();
            double tx = gd.getNextNumber();
            double ty = gd.getNextNumber();
            double tz = gd.getNextNumber();
            double mag = gd.getNextNumber();
            double scalex = gd.getNextNumber();
            double scalez = gd.getNextNumber();
            double delta = gd.getNextNumber();
            double uaxis_alpha2 = 90 + tilt_axis2;
            DoubleMatrix1D axis2 = MatrixUtils.eulerDirection(toRadians(uaxis_alpha2), toRadians(uaxis_beta));

            DoubleMatrix2D globalShift=DoubleFactory2D.dense.make(3, 1);
            globalShift.setQuick(0, 0, tx);
            globalShift.setQuick(1, 0, ty);
            globalShift.setQuick(2, 0, tz);

            DoubleMatrix2D R=MatrixUtils.eulerAngles2Matrix(phi,theta,psi);
            DoubleMatrix2D D= DoubleFactory2D.dense.make(3,3);
            D.setQuick(0,0,mag*scalex*Math.cos(Math.toRadians(delta)));
            D.setQuick(1,0,mag*scalex*Math.sin(Math.toRadians(delta)));
            D.setQuick(1,1,mag);
            D.setQuick(2,2,mag*scalez);
            DoubleMatrix2D globalDeform=R.zMult(D,null);
            TiltSeries ts2=createTiltSeries(rjs,angles,uaxis_alpha2,uaxis_beta,globalDeform, globalShift);
            fs = new FileSaver(ts2);
            fs.saveAsTiffStack(path + "tiltseries2.tif");


            CommandWorkflow.saveLandmarks(path, "landmarks2.txt", ts2);
            ts2.show();
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(path+"dualParameters.txt"));
                out.write("tiltaxis1:\t"+tilt_axis);
                out.write("tiltaxis2:\t"+tilt_axis2);
                out.write("phi:\t"+phi);
                out.write("theta:\t"+theta);
                out.write("psi:\t"+psi);
                out.write("tx:\t"+tx);
                out.write("ty:\t"+ty);
                out.write("tz:\t"+tz);
                out.write("mag:\t"+mag);
                out.write("scalex:\t"+scalex);
                out.write("scalez:\t"+scalez);
                out.write("shear angle:\t"+delta);
                out.flush();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }


    }

    public void setParameters(double tilt_angleStart,double tilt_angleEnd,double tilt_angleIncrement,
                              double maxShift, double maxpsii, double maxmi, double maxti, double maxsi, double maxdeltai){
        this.tilt_angleStart=tilt_angleStart;
        this.tilt_angleEnd=tilt_angleEnd;
        this.tilt_angleIncrement=tilt_angleIncrement;
        this.maxShift=maxShift;
        this.maxpsii=maxpsii;
        this.modmi=maxmi;
        this.modti=maxti;
        this.modsi=maxsi;
        this.maxdeltai=maxdeltai;
    }

    public void setPath(String path){
        this.path=path;
    }

    protected ArrayList<DoubleMatrix2D> createRjs(){
        rec = new TomoReconstruction2(size, size, size);
        return createRjs(rec,numberOfPoints, factor);

    }

    public ArrayList<DoubleMatrix2D> createRjs(ImagePlus rec, int numberOfPoints, double factor) {
        ArrayList<DoubleMatrix2D> rjs = new ArrayList<DoubleMatrix2D>(numberOfPoints);
        int size=rec.getWidth();
        center=(size-1.0)/2.0;

        try {
            BufferedWriter outrj = new BufferedWriter(new FileWriter(path+"rjs.txt"));
            for (int j = 0; j < numberOfPoints; j++) {
                DoubleMatrix2D rj = DoubleFactory2D.dense.make(3, 1);
                rj.setQuick(0, 0, Math.random() * size - center);
                rj.setQuick(1, 0, Math.random() * size - center);
                //rj.setQuick(2, 0, 0); //for tests only of central plane where correction should be perfect
                rj.setQuick(2, 0, Math.random() * (size*factor) - (center*factor));
                rjs.add(rj);
                //rec.putPixel((int) (rj.getQuick(0, 0) + center), (int) (rj.getQuick(1, 0) + center), (int) (rj.getQuick(2, 0) + center), 1);
                rec.getImageStack().getProcessor((int)(rj.getQuick(2,0)+center)).putPixel((int) (rj.getQuick(0, 0) + center), (int) (rj.getQuick(1, 0) + center), 1);
                outrj.write("#" + j + "\t" + rj.getQuick(0, 0) + "\t" + rj.getQuick(1, 0) + "\t" + rj.getQuick(2, 0) + "\n");
            }
            outrj.flush();
            outrj.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rjs;
    }

    public TiltSeries createTiltSeries(ArrayList<DoubleMatrix2D> rjs, double[] angles, double axis_alpha, double axis_beta,DoubleMatrix2D globalDeform, DoubleMatrix2D globalShift) {

        DiSTrue =new ArrayList<DoubleMatrix2D>(angles.length);
        DoubleMatrix1D axis = MatrixUtils.eulerDirection(toRadians(axis_alpha), toRadians(axis_beta));
        ImageStack is = new ImageStack(size, size);
        for (int i = 0; i < angles.length; i++) {
            is.addSlice(new FloatProcessor(size, size));
        }
        ImagePlus imp = new ImagePlus("tilt series"+((globalDeform!=null)?2:1), is);
        TiltSeries ts = new TiltSeries(imp, angles);
        TomoJPoints tp = new TomoJPoints(ts);
        for (int j = 0; j < rjs.size(); j++) tp.addNewSetOfPoints(true);
        try {

            BufferedWriter out = new BufferedWriter(new FileWriter(path+"parameters"+((globalDeform!=null)?2:1)+".xls"));
            out.write(" \timage\ttilt axis (rot)\ttilt axis (tilt)\ttilt angle\tdi.x\tdi.y\tpsii\tmi\tti\tsi\tdeltai\n");
            BufferedWriter debug=  new BufferedWriter(new FileWriter(path+"debugCreation"+((globalDeform!=null)?2:1)+".txt"));
            for (int t = 0; t < angles.length; t++) {
                DoubleMatrix2D RthetaUaxis = MatrixUtils.rotation3DMatrix(angles[t], axis);
                debug.write("\n\n##################################\n#" + angles[t] + " \nRthetaUaxis=" + RthetaUaxis);
                DoubleMatrix2D di = DoubleFactory2D.dense.make(2, 1);
                di.setQuick(0, 0, Math.cos(2 * Math.PI * (angles[t]) / (tilt_angleEnd - tilt_angleStart + 1)) * maxShift);
                di.setQuick(1, 0, -Math.cos(2 * Math.PI * (angles[t]) / (tilt_angleEnd - tilt_angleStart + 1)) * maxShift);
                //di.setQuick(0,0,angles[t]);
                //di.setQuick(1,0,-angles[t]/2.0);
                double mi = 1 + (Math.cos(2 * Math.PI * angles[t] / (tilt_angleEnd - tilt_angleStart + 1)) * modmi);
                //double ti = 1 - (Math.cos(2*Math.PI * angles[t] / (tilt_angleEnd - tilt_angleStart + 1) + Math.PI) * modti);
                double ti = 1+Math.cos(Math.PI * t / angles.length) * modti;
                double si = 1 + (Math.cos(2 * Math.PI * angles[t] / (tilt_angleEnd - tilt_angleStart + 1)) * modsi);
                double psii = Math.cos(2 * Math.PI * angles[t] / (tilt_angleEnd - tilt_angleStart + 1)) * maxpsii;
                double deltai = Math.cos(2 * Math.PI * angles[t] / (tilt_angleEnd - tilt_angleStart + 1)) * maxdeltai;
                DoubleMatrix2D Di = DoubleFactory2D.dense.make(3, 3);
                Di.assign(0);
                Di.setQuick(0, 0, mi * si * Math.cos(Math.toRadians(deltai)));
                Di.setQuick(1, 0, mi * si * Math.sin(Math.toRadians(deltai)));
                Di.setQuick(1, 1, mi);
                Di.setQuick(2, 2, mi * ti);
                System.out.println("Di:"+Di);
                out.write("" + (t+1) + "\t" +t+"\t"+axis_alpha+"\t"+axis_beta+"\t"+angles[t]+"\t"+ di.getQuick(0, 0) + "\t" + di.getQuick(1, 0) + "\t" + psii + "\t" + mi + "\t" + ti + "\t" + si + "\t" + deltai + "\n");

                DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
                DoubleMatrix2D Ai = Rpsi.zMult(RthetaUaxis.zMult(Di, null), null).viewPart(0, 0, 2, 3);
                DiSTrue.add(Di);
                double tiltangle=angles[t];
                for (int j = 0; j < rjs.size(); j++) {
                    DoubleMatrix2D rj = rjs.get(j).copy();   //@TODO removed for debug
//                    DoubleMatrix2D rj=new DenseDoubleMatrix2D(3,1);
//                    rj.setQuick(0,0,Math.cos(Math.toRadians(78))*50+10);
//                    rj.setQuick(1,0, -Math.sin(Math.toRadians(78))*50);
                    if(globalDeform!=null){
                        rj=globalDeform.zMult(rj,null);
                        rj.assign(globalShift,DoubleFunctions.minus);
                    }

                    DoubleMatrix2D pij = Ai.zMult(rj, null).assign(di, DoubleFunctions.plus);
                    is.getProcessor(t + 1).putPixelValue((int) Math.round(pij.getQuick(0, 0) + center), (int) Math.round(pij.getQuick(1, 0) + center), 1);
                    tp.setPoint(j, t, new Point2D.Double(pij.getQuick(0, 0) + ts.getProjectionCenterX(), pij.getQuick(1, 0) + ts.getProjectionCenterY()), true);
                    if (j == 0) {
                        debug.write("\n#0 rj=" + rj + "\nAi=" + Ai + "\nPij=" + pij);
                    }
                }


            }
            out.flush();
            out.close();
            debug.flush();
            debug.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        ts.setTomoJPoints(tp);
        return ts;
    }

    public void testCorrection(){
        path="F:\\test_paper_COSS\\phantom_compute_error_correction";
        if(!(path.endsWith("/")||path.endsWith("\\")) ) path+="/";
        size=(int) 512;
        factor=1;
        center=(size-1.0)/2.0;
        tilt_axis = 0;
        tilt_angleStart = -60;
        tilt_angleEnd = 60;
        tilt_angleIncrement = 2;
        numberOfPoints = (int) 200;
        maxShift = 50;
        maxpsii = 2;
        modmi = 0.02;
        modti = 0.05;
        modsi = 0.1;
        maxdeltai = 0.5;
        double uaxis_alpha = 90 + tilt_axis;
        double uaxis_beta = 90;
        DoubleMatrix1D axis = MatrixUtils.eulerDirection(toRadians(uaxis_alpha), toRadians(uaxis_beta));

        double[] angles = new double[(int) ((tilt_angleEnd - tilt_angleStart) / tilt_angleIncrement + 1)];
        angles[0] = tilt_angleStart;
        for (int i = 1; i < angles.length; i++) {
            angles[i] = angles[i - 1] + tilt_angleIncrement;
        }

        ArrayList<DoubleMatrix2D> rjs=createRjs();
        TiltSeries ts=createTiltSeries(rjs,angles,uaxis_alpha,uaxis_beta,null,null);
        //rec.show();
        FileSaver fs = new FileSaver(rec);
        fs.saveAsTiffStack(path+"rjs.tif");

        fs = new FileSaver(ts);
        fs.saveAsTiffStack(path+"tiltseries1.tif");
        TomoJPoints tp=ts.getTomoJPoints();
        for (int j = tp.getNumberOfPoints() - 1; j >= 0; j--) {
            Point2D[] tmppts = tp.getPoints(j);
            int count = 0;
            for (Point2D tmppt : tmppts) {
                if (tmppt != null) count++;
            }
            if (count == 0) {
                System.out.println("remove landmark at index " + j + " : it has no defined points");
                tp.removeSetOfPoints(j);
            }
        }

        final AlignmentLandmarksOptions options = new AlignmentLandmarksOptions();
        options.setExhaustiveSearch(true);
        options.setMahalanobisWeight(0);
        options.setAllowShifts(maxShift!=0);
        options.setDeformShrinkage(true);//(modti!=0);
        options.setDeformMagnification(true);//(modmi!=0);
        options.setDeformScalingX(true);//(modsi!=0);
        options.setDeformDelta(true);//(maxdeltai!=0);
        options.setAllowInPlaneRotation(maxpsii!=0);
        options.setNumberOfCycles(0);
        options.setK(8);
        options.setCorrectForHeight(false);

        OutputStreamCapturer outputCapture = new OutputStreamCapturer();
        final Chrono time = new Chrono(1);
        time.start();
        boolean oldAlgo=false;
        Landmarks3DAlign alignator = new Landmarks3DAlign(tp);

        double score = (oldAlgo) ? alignator.align3DLandmarks(0, 5, true, 5, false) : alignator.align3DLandmarksWithDeformation(options);
        //                double score = ts.align3DLandmarks(maxrot, correctHeight);
        time.stop();
        System.out.println("total time: " + time.delayString());


        //tp.loadAlignmentLandmark("D:\\images_test\\test TomoJ\\ForCedric\\TomoJ\\scaled_alignment.txt");
        //Roi toto = null;
        //ts.setRoi(toto);
        if (tp.getCurrentIndex() >= tp.getNumberOfPoints()) {
            tp.setCurrentIndex(tp.getNumberOfPoints() - 1);
        }
        //updatePointSpinner();
        ts.setRoi(tp.getRoi(ts.getCurrentSlice() - 1));
        ts.setSlice(ts.getCurrentSlice());
        ts.updateAndDraw();
        ts.threadStats();
        FileInfo fi = ts.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts.getFileInfo();
        }
        //String imageDir = (fi != null && fi.directory != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");

        if (oldAlgo) {
            alignator.getAlignmentOld().saveRjs(path + ts.getTitle() + "_Old_score_" + score + "_rjs.txt");
        } else {
            CommandWorkflow.saveTransform(path,"test_5_transform1.csv",ts,true);
            alignator.getBestAlignment().saveRjs(path + ts.getTitle() + "_score_" + score + "_rjs.txt");
            ArrayList<DoubleMatrix2D>foundRjs = alignator.getBestAlignment().getCurrent_rj();
            double scoreRjs = computeScoreRjs(rjs,foundRjs);
            System.out.println("score rjs = "+scoreRjs);
            double scoreAiAiinv=computeScore(rjs,alignator.getBestAlignment());
            System.out.println("final score = " +scoreAiAiinv);
        }

        String resultString = outputCapture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(path + "log.txt"));
            writer.write(resultString);
            writer.flush();
            writer.close();
        }catch( Exception e){
            e.printStackTrace();
        }

    }

    protected double computeScoreRjs(ArrayList<DoubleMatrix2D> original, ArrayList<DoubleMatrix2D> found){
        double score=0;
        for(int i=0;i<original.size();i++){
            DoubleMatrix2D tmp=original.get(i).copy();
            tmp.assign(found.get(i),DoubleFunctions.minus);
            tmp.assign(DoubleFunctions.abs);
           score+= tmp.zSum();
        }
        return score/original.size();
    }

    protected double computeScore(ArrayList<DoubleMatrix2D> originalRjs, AlignmentLandmarkImproved align){
        double score=0;
        DoubleMatrix2D rj= DoubleFactory2D.dense.make(3,1);
        //for(int j=0;j<originalRjs.size();j++){
        for(int z=0;z<size;z+=10){
            for(int y=0;y<size;y+=10){
                for(int x=0;x<size;x+=10){
                    for(int i = 0; i< DiSTrue.size(); i++){
                        //DoubleMatrix2D rj=originalRjs.get(j).copy();
                        rj.setQuick(0,0,x-center);
                        rj.setQuick(1,0,y-center);
                        rj.setQuick(2,0,z-center);
                        DoubleMatrix2D pij= DiSTrue.get(i).zMult(rj,null);
                        DoubleMatrix2D rjBack=new DenseDoubleAlgebra().inverse(align.getCurrentAi().get(i).getDi()).zMult(pij,null);
                        //System.out.println("Di true="+DiSTrue.get(i));
                        //System.out.println("Di computed="+align.getCurrentAi().get(i).getDi());
                        rj.assign(rjBack,DoubleFunctions.minus);
                        rj.assign(DoubleFunctions.square);
                        score+= Math.sqrt(rj.zSum());
                    }
                }
            }

        }
        //return score/(originalRjs.size()* DiSTrue.size());
        return score /(size*size*size*0.001* DiSTrue.size());
    }
}
