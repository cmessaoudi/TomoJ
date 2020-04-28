package fr.curie.plugins;

//import cern.colt.matrix.tdouble.DoubleMatrix2D;
import fr.curie.tomoj.tomography.projectors.VoxelProjector3D;
//import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
//import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
//import fr.curie.utils.MatrixUtils;
//
//import java.util.ArrayList;
//import java.util.concurrent.Callable;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 17 mars 2011
 * Time: 14:44:51
 * To change this template use File | Settings | File Templates.
 */
public class Project_Volume implements PlugInFilter {
    double tilt_axis;
    double tilt_angleStart;
    double tilt_angleEnd;
    double tilt_angleIncrement;
    boolean electronTomoNorm = true;
    TomoReconstruction2 rec;

    public int setup(String arg, ImagePlus imp) {
        rec = new TomoReconstruction2(imp);
        GenericDialog gd = new GenericDialog("projection parameters");
        gd.addNumericField("tilt axis", 0, 2);
        gd.addNumericField("tilt angle start", -60, 2);
        gd.addNumericField("tilt angle end", 60, 2);
        gd.addNumericField("tilt angle increment", 2, 2);
        gd.addNumericField("tilt axis centerx", (imp.getWidth()-1.0) / 2.0, 2);
        gd.addNumericField("tilt axis centery", (imp.getHeight() -1.0) / 2.0, 2);
        gd.addNumericField("tilt axis centerz", (imp.getNSlices()-1.0) / 2.0, 2);
        gd.addCheckbox("cryo/resin", false);
        gd.showDialog();
        if (gd.wasCanceled()) return DONE;
        tilt_axis = gd.getNextNumber();
        tilt_angleStart = gd.getNextNumber();
        tilt_angleEnd = gd.getNextNumber();
        tilt_angleIncrement = gd.getNextNumber();
        electronTomoNorm = gd.getNextBoolean();
        rec.setCenter(gd.getNextNumber(), gd.getNextNumber(), gd.getNextNumber());
        return DOES_32;
    }

    public void run(ImageProcessor ip) {

        if (rec == null) {
            System.out.println("rec is null");
            return;
        }
        ImageStack is = new ImageStack(rec.getWidth(), rec.getHeight());
        double[] angles=new double[(int)((tilt_angleEnd-tilt_angleStart+1)/tilt_angleIncrement)];
        int index=0;
        for (double angle = tilt_angleStart; angle <= tilt_angleEnd; angle += tilt_angleIncrement) {
            angles[index]=angle;
            index++;
            is.addSlice(new FloatProcessor(is.getWidth(),is.getHeight()));
        }

        TiltSeries ts= new TiltSeries(new ImagePlus("",is),angles);
        VoxelProjector3D projector=new VoxelProjector3D(ts,rec,null);
        for(int i=0;i<angles.length;i++){
            projector.addProjection(i);
        }
        projector.project();
        for(int i=0;i<angles.length;i++){
            System.arraycopy(projector.getProjection(i),0,(float[])is.getPixels(i+1),0, projector.getProjection(i).length);
        }
        ts.show();



        /*ExecutorService exec= Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future<FloatProcessor>> jobs=new ArrayList<Future<FloatProcessor>>();
        final int totalcomputation=(int)((tilt_angleEnd-tilt_angleStart)/tilt_angleIncrement)+1;
        final AtomicInteger completion=new AtomicInteger(0);
        for (double angle = tilt_angleStart; angle <= tilt_angleEnd; angle += tilt_angleIncrement) {
            final double anglef=angle;
            jobs.add(exec.submit(new Callable<FloatProcessor>() {
                public FloatProcessor call() {
                    FloatProcessor fp = new FloatProcessor(rec.getWidth(), rec.getHeight());
                    FloatProcessor norm = new FloatProcessor(rec.getWidth(), rec.getHeight());
                    DoubleMatrix2D euler = MatrixUtils.eulerAngles2Matrix(tilt_axis, anglef, -tilt_axis);
                    rec.projectBasic(euler, fp, norm, 1, true, electronTomoNorm, rec.getWidth() / 2, rec.getHeight() / 2);
                    IJ.showProgress(completion.addAndGet(1),totalcomputation);
                    return fp;
                }
            })) ;
        }
        int index=0;
        try {
            for (double angle = tilt_angleStart; angle <= tilt_angleEnd; angle += tilt_angleIncrement) {
                is.addSlice("" + angle, jobs.get(index).get());
                index++;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        new ImagePlus(rec.getTitle()+"proj", is).show();
        //new ImagePlus("norm",norm);   */

    }
}
