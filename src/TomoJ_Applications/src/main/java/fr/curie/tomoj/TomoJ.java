package fr.curie.tomoj;

import fr.curie.InputOutput.MRC_Reader;
import fr.curie.InputOutput.MRC_Writer;
import fr.curie.InputOutput.Spider_Reader;
import fr.curie.tomoj.application.*;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.workflow.CommandWorkflow;
import fr.curie.tomoj.workflow.UserAction;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import org.jocl.Sizeof;

import java.awt.geom.Point2D;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class TomoJ {
    ArrayList<UserAction>log;
    TiltSeries ts;
    TomoJPoints tp;

    public TomoJ(String[] args) {
        if (args.length < 2) {
            help();
            return;
        }
        log = new ArrayList<UserAction>();
        String path=args[args.length - 1];
        ImagePlus imp;
        if(path.endsWith(".mrc")||path.endsWith(".st")){
            MRC_Reader reader=new MRC_Reader();
            imp=reader.load(path);
        }else if(path.endsWith(".xmp")||path.endsWith(".spi")){
            Spider_Reader reader=new Spider_Reader();
            imp=reader.load(path);

        }else imp=new ImagePlus(path);
        double[] angles = labels2Angles(imp.getImageStack());
        if (imp.getType() != ImagePlus.GRAY32) new StackConverter(imp).convertToGray32();
        ts = new TiltSeries(imp);
        ts.setFileInfo(imp.getOriginalFileInfo());
        if (angles != null) ts.setTiltAngles(angles);
        tp = new TomoJPoints(ts);
        ts.setTomoJPoints(tp);
        macro(args, ts);
        System.out.println("end of computation");

        String logname=path+"_log_"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))+".txt";
        System.out.println("saving log in "+logname);
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(logname));
            for (UserAction ua : log) {
                out.write(ua.toStringComplete() + "\n");
            }
            out.flush();
            out.close();
        } catch (Exception ioe) {
            ioe.printStackTrace();
        }
        System.exit(0);
    }

    /**
     * start point from command line.
     *
     * @param args arguments for processing.
     * @see TomoJ#macro(String[], TiltSeries)
     */
    public static void main(String[] args) {
        TomoJ tj=new TomoJ(args);
    }

    /**
     * show options for command line execution
     */
    protected void help() {
        System.out.println("usage:\nTomoJ [options] tiltseriesFileName\noptions can be:");
        System.out.println("-noNormalisation: to force the non normalisation of aligned images\n" +
                "-settiltaxis realvalue: set tilt axis to the given value (rotation to put tilt axis vertical)\n" +
                "-center cx cy use of these center coordinates for alignment (instead of width/2 and height/2, real value)\n" +
                "-settiltanglesregular realStartAngle realIncrement angle: set tilt angles with the starting angle and increment\n" +
                "-loadAngles filepath: load the angles from a file\n" +
                "-loadTransforms filepath binning: load transforms from a file (the transforms are from one image to the next)\n" +
                "-loadfinaltransforms filepath binning: load transforms from a file\n" +
                "-loadlandmarks file path binning: load landmarks from a file\n" +
                "-savealignedimages: save the images after application of current alignment\n"+
                "-xcorr options: correct shift using cross-correlation (see options below)\n" +
                "-generatelandmarks options: generates landmarks chains automatically using local minima/maxima detection and patch tracking (see options below)\n" +
                "-alignlandmarks options: align images using landmarks chains (see options below)\n" +
                "-wbp options: reconstruction using WBP algorithm\n" +
                "-ossart options: reconstruction using os-sart algorithm (ART/SART/SIRT...)" );

        System.out.println("----------------------------------\nparameters for xcorr \n"+CrossCorrelationParameters.help()+"----------------------------------");
        System.out.println("----------------------------------\nparameters for landmarks chain generation \n"+CriticalLandmarksGenerator.help()+"----------------------------------");
        System.out.println("---------------------------------\nparameters for alignment of landmarks \n"+AlignWithLandmarks.help()+"----------------------------------");
        System.out.println("----------------------------------\nparameters for reconstruction wbp \n"+WBPReconstructionApplication.help()+"----------------------------------");
        System.out.println("---------------------------------\nparameters for reconstruction ossart \n"+IterativeReconstructionApplication.help()+"----------------------------------");

    }

    /**
     * gets the labels of slices in the given stackImage<BR>
     * tries to convert them to tilt angles information
     *
     * @param stack ImageStack containing the tilt series
     * @return the tilt angles for each image in the tilt series, null if
     * labels are not tilt angles
     */
    static public double[] labels2Angles(ImageStack stack) {
        double[] angles = new double[stack.getSize()];
        int i = 0;
        while (i < stack.getSize()) {
            String str = stack.getSliceLabel(i + 1);
            try {

                double tmp = Double.parseDouble(str);
                angles[i] = tmp;
                System.out.print(" " + tmp);
            } catch (Exception e) {
                return null;
            }
            i++;
        }
        System.out.println();

        return angles;
    }


    /**
     * macro mode of TomoJ
     *
     * @param args the paramaters for processing
     *             <ul>
     *             <li><code>noNormalisation</code> to force the non normalisation of aligned images</li>
     *             <li><code>settiltaxis realvalue</code> set tilt axis to the given value (rotation to put tilt axis vertical)</li>
     *             <li><code>center cx cy</code> use of these center coordinates for alignment (instead of width/2 and height/2, real value)</li>
     *             <li><code>settiltanglesregular realStartAngle realIncrement angle</code> set tilt angles with the starting angle and increment</li>
     *             <li><code>loadAngles filepath</code> load the angles from a file</li>
     *             <li><code>loadTransforms filepath binning</code> load transforms from a file</li>
     *             <li><code>loadlandmarks file path binning</code> load landmarks from a file</li>
     *             <li><code>xcorr</code> perform an alignment by crosscorrelation (on entire image)</li>
     *             <li><code>automaticalignment nbGridPoints minSeqLength localSize corrThreshold</code> do automatic alignment that is :
     *             <ul><li>cross correlation</li>
     *             <li>affine transform</li>
     *             <li>generates landmarks (see manual for explanations on parameters)</li>
     *             <li>align using 3D landmarks</li></ul>
     *             </li>
     *             <li><code>art nbite relcoeff thickness recNorm</code> perform art reconstruction</li>
     *             <li><code>sirt nbite relcoeff thickness recNorm</code> perform sirt reconstruction</li>
     *             </ul>
     * @param ts   TiltSeries on which executing commands
     * @see TomoJ#readAnglesFromFile(String, String, TiltSeries)
     * @see CommandWorkflow#loadLandmarks(String, String, TomoJPoints, double)
     */
    public void macro(String[] args, TiltSeries ts) {
        System.out.println("enter macro mode number of arguments: " + args.length);
        boolean loadedT = false;

        TomoJPoints tp = ts.getTomoJPoints();
        TomoReconstruction2 rec2=null;
        //String[] args = arg.toLowerCase().split("\\s+");
        for (int a = 0; a < args.length; a++) {
            if (args[a].startsWith("-nbcpu")) {

                int nbcpu= Integer.parseInt(args[a + 1]);
                Prefs.setThreads(nbcpu);
                System.out.println("fix number of thread to : "+Prefs.getThreads());
                UserAction ua = new UserAction("setCPU", "nbcpu="+nbcpu,
                        "", false);
                log.add(ua);
                a+=1;
            }
            if (args[a].toLowerCase().startsWith("-nonormalisation")) {
                ts.setNormalize(false);
                System.out.println("no normalisation");
                UserAction ua = new UserAction("no normalisation", "",
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-settiltaxis")) {
                double ta = Double.parseDouble(args[a + 1]);
                ts.setTiltAxis(ta);
                a += 1;
                UserAction ua = new UserAction("set tilt axis", "tiltaxis="+ta,
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-center")) {
                double cx = Double.parseDouble(args[a + 1]);
                double cy = Double.parseDouble(args[a + 2]);
                ts.setCenter(cx, cy);
                System.out.println("set the center to (" + cx + ", " + cy + ")");
                a += 2;
                UserAction ua = new UserAction("set tilt axis center to ", "(="+cx+", "+cy+")",
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-settiltanglesregular")) {
                double start = Double.parseDouble(args[a + 1]);
                double inc = Double.parseDouble(args[a + 2]);
                double[] angles = ts.getTiltAngles();
                angles[0] = start;
                for (int i = 1; i < angles.length; i++) {
                    angles[i] = angles[i - 1] + inc;
                }
                ts.setTiltAngles(angles);
                a += 2;
                UserAction ua = new UserAction("set tilt angles regular", "start="+start+" increment="+inc,
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-loadangles")) {
                String path = args[a + 1];
                File f = new File(path);
                if (f.exists()) {
                    double[] angles = readAnglesFromFile("", path, ts);
                    ts.setTiltAngles(angles);
                } else {
                    System.out.println("Transformations file " + path + " does not exist!");
                    IJ.error("file " + path + " does not exist!");
                    return;
                }
                a += 1;
                UserAction ua = new UserAction("load angles", "filepath="+path,
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-loadtransforms")) {
                String path = args[a + 1];
                double binning = Double.parseDouble(args[a + 2]);
                File f = new File(path);
                try {
                    if (f.exists() && f.canRead()) {
                        System.out.println(path);
                        CommandWorkflow.loadTransforms("", path, ts, binning, false, false);
                        loadedT = true;
                    } else {
                        System.out.println("Transformations file " + path + " does not exist!");
                        IJ.error("file " + path + " does not exist!");
                        return;
                    }
                } catch (Exception e) {
                    System.out.println(e);
                    System.out.println(path);
                }
                a += 2;
                UserAction ua = new UserAction("load transform", "filepath="+path+" binning="+binning,
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-loadfinaltransforms")) {
                String path = args[a + 1];
                double binning = Double.parseDouble(args[a + 2]);
                File f = new File(path);
                try {
                    if (f.exists() && f.canRead()) {
                        System.out.println(path);
                        CommandWorkflow.loadTransforms("", path, ts, binning, true, false);
                        loadedT = true;
                    } else {
                        System.out.println("Transformations file " + path + " does not exist!");
                        IJ.error("file " + path + " does not exist!");
                        return;
                    }
                } catch (Exception e) {
                    System.out.println(e);
                    System.out.println(path);
                }
                a += 2;
                UserAction ua = new UserAction("load transform ", "filepath="+path+" binning="+binning,
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-loadlandmarks")) {
                String path = args[a + 1];
                double binning = Double.parseDouble(args[a + 2]);
                File f = new File(path);
                if (f.exists()) {
                    CommandWorkflow.loadLandmarks("", path, ts.getTomoJPoints(), binning);
                } else {
                    System.out.println("landmarks file " + path + " does not exist!");
                    IJ.error("file " + path + " does not exist!");
                    return;
                }
                a += 2;
                UserAction ua = new UserAction("load landmarks ", "filepath="+path+" binning="+binning,
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-loadtomogram")) {
                String path = args[a + 1];
                File f = new File(path);
                if (f.exists()) {
                    Opener op = new Opener();
                    ImagePlus tmp= op.openImage(path);
                    if (tmp.getWidth() == ts.getWidth() && tmp.getHeight() == ts.getHeight()) {
                        System.out.println("the tomogram was sucessfully loaded");
                        rec2 = new TomoReconstruction2(tmp);
                    } else {
                        System.err.println("ERROR LOADING TOMOGRAM : tomogram size is not compatible with tilt series");
                        continue;
                    }
                }else{
                    System.err.println("ERROR LOADING TOMOGRAM : tomogram file not found");
                    continue;
                }
                a += 1;
                UserAction ua = new UserAction("load tomogram", "filepath="+path,
                        "", false);
                log.add(ua);
            }
            if (args[a].toLowerCase().startsWith("-xcorr")) {
                ArrayList<String> params=new ArrayList<String>();
                while (a+1<args.length && !args[a+1].toLowerCase().startsWith("-")){
                    params.add(args[a+1]);
                    a++;
                }
                CrossCorrelationParameters cc=new CrossCorrelationParameters(ts);
                cc.setParameters(params.toArray());
                cc.run();
                String savedir = ts.getOriginalFileInfo().directory;
                String imgname = ts.getShortTitle();
                System.out.println("saving in directory: " + savedir);
                System.out.println("saving prefix: " + imgname);
                CommandWorkflow.saveTransform(savedir, imgname + "_xcorr.txt", ts, true);
                storeAction(cc);
            }
            if (args[a].toLowerCase().startsWith("-generatelandmarks")) {
                ArrayList<String> params=new ArrayList<String>();
                while (a+1<args.length && !args[a+1].toLowerCase().startsWith("-")){
                    params.add(args[a+1]);
                    a++;
                }
                CriticalLandmarksGenerator clg=new CriticalLandmarksGenerator(ts,tp);
                clg.setParameters(params.toArray());
                clg.run();
                String savedir = ts.getOriginalFileInfo().directory;
                String imgname = ts.getTitle();
                System.out.println("saving in directory: " + savedir);
                System.out.println("saving prefix: " + imgname);

                CommandWorkflow.saveLandmarks(savedir, imgname + "_landmarks.txt", ts);
                storeAction(clg);
            }
            if (args[a].startsWith("-alignlandmarks")) {
                ArrayList<String> params=new ArrayList<String>();
                while (a+1<args.length && !args[a+1].toLowerCase().startsWith("-")){
                    params.add(args[a+1]);
                    a++;
                }
                AlignWithLandmarks al=new AlignWithLandmarks(ts,tp);
                al.setParameters(params.toArray());
                al.run();
                String savedir = ts.getOriginalFileInfo().directory;
                String imgname = ts.getShortTitle();
                System.out.println("saving in directory: " + savedir);
                System.out.println("saving prefix: " + imgname);
                CommandWorkflow.saveLandmarks(savedir, imgname + "_landmarksAfterAlignment.txt", ts);
                CommandWorkflow.saveTransform(savedir,imgname+"_alignment.csv",ts,false);
                storeAction(al);
            }
            if (args[a].startsWith("-savealignedimages")) {
                String savedir = ts.getOriginalFileInfo().directory;
                if (savedir==null) savedir="";
                String imgname = ts.getShortTitle();
                System.out.println("saving in directory: " + savedir);
                exportAlignedImages(ts, savedir, imgname+"_ali.mrc");
            }
            if (args[a].startsWith("-wbp")) {
                ArrayList<String> params=new ArrayList<String>();
                while (a+1<args.length && !args[a+1].toLowerCase().startsWith("-")){
                    params.add(args[a+1]);
                    a++;
                }
                WBPReconstructionApplication wbp=new WBPReconstructionApplication(ts);
                wbp.setParameters(params.toArray());
                wbp.run();
                String savedir = ts.getOriginalFileInfo().directory;
                System.out.println("saving in: " + savedir+wbp.getReconstruction().getTitle());
                FileSaver fs=new FileSaver(wbp.getReconstruction());
                fs.saveAsTiff(savedir+wbp.getReconstruction().getTitle());
                storeAction(wbp);
            }
            if (args[a].startsWith("-ossart")) {
                ArrayList<String> params=new ArrayList<String>();
                while (a+1<args.length && !args[a+1].toLowerCase().startsWith("-")){
                    params.add(args[a+1]);
                    a++;
                }
                IterativeReconstructionApplication ir=new IterativeReconstructionApplication(ts);
                ir.setParameters(params.toArray());
                ir.run();
                String savedir = ts.getOriginalFileInfo().directory;
                System.out.println("saving in: " + savedir+ir.getReconstruction().getTitle());
                FileSaver fs=new FileSaver(ir.getReconstruction());
                fs.saveAsTiff(savedir+ir.getReconstruction().getTitle());
                storeAction(ir);
            }
        }
        ts.close();
    }

    public void storeAction(Application currentAppli){
        String params = currentAppli.getParametersValuesAsString();
        ArrayList<Object> results = currentAppli.getResults();
        if (results != null) {
            params += "\n" + results.get(0);

        } else {
            System.err.println("results is null!!!");
        }
        UserAction ua = new UserAction(currentAppli.name(), params,
                currentAppli.name(), false);
        log.add(ua);
    }

    /**
     * read the angle from a file
     *
     * @param directory directory of the file
     * @param filename  name of the file. It may be a parameter file from JEOL or a rawtlt file (text file with one value of tilt angle per image separated by space or one per line)
     * @param ts        TiltSeries where angles will be put
     * @return array containing the angles read in the file
     */
    public static double[] readAnglesFromFile(String directory, String filename, TiltSeries ts) {
        double[] angles = null;
        try {
            BufferedReader bf = new BufferedReader(new FileReader(directory + filename));
            if (filename.endsWith("dm3")) {
                System.out.println("dm3 angle file");
                angles = readFromDm3(directory+filename, ts);
            } else if (filename.endsWith("RecParam.txt")) {
                System.out.println("Jeol angle file");
                angles = readFromJeol(bf, ts);
            } else {
                System.out.println("rawtlt angle file");
                angles = readFromRawtlt(bf, ts);

            }
            bf.close();
        } catch (Exception ex) {
            IJ.error("" + ex);
        }
        //System.out.println("nb angles"+angles.length);
        ts.setTiltAngles(angles);
        return angles;
    }

    /**
     * reads angles from a dm3 image (S. Trepout acquisition script)
     *
     * @param path path to dma3 file
     * @param ts   the TiltSeries where Tilt Axis will be updated (not Tilt Angles)
     * @return the angles inside the file
     * @throws IOException if errors occur while parsing
     */
    protected static double[] readFromDm3(String path, TiltSeries ts) throws IOException {
        double[] angles = null;
        ImagePlus tmp=IJ.openImage(path);
        ImageProcessor ip=tmp.getProcessor();
        angles=new double[tmp.getWidth()];
        for(int i=0;i<ip.getWidth();i++){
            angles[i]=ip.getf(i);
        }
        return angles;
    }

    /**
     * reads angles from a jeol parameter file
     *
     * @param bf the already opened file
     * @param ts the TiltSeries where Tilt Axis will be updated (not Tilt Angles)
     * @return the angles inside the file
     * @throws IOException if errors occur while parsing
     */
    protected static double[] readFromJeol(BufferedReader bf, TiltSeries ts) throws IOException {
        double[] angles = null;
        String line = bf.readLine();
        do {
            if (line.startsWith("AxisRotation")) {
                System.out.println("tilt axis detected");
                double tiltaxis = Double.parseDouble(line.split(":")[1]);
                ts.setTiltAxis(tiltaxis);
            }
            if (line.startsWith("TiltXSerise")) {
                System.out.println("tilt angles detected");
                String[] split = line.split(" ");
                angles = new double[split.length - 1];
                for (int i = 0; i < angles.length; i++) {
                    angles[i] = -Double.parseDouble(split[i + 1]);
                }
            }
        } while ((line = bf.readLine()) != null);
        return angles;
    }

    /**
     * reads angles from a rawtlt file
     *
     * @param bf the already opened file
     * @param ts TiltSeries needed to know the number of file to read
     * @return the angles contained in the file
     * @throws IOException if error occurs while parsing the file
     */
    protected static double[] readFromRawtlt(BufferedReader bf, TiltSeries ts) throws IOException {
        //System.out.println("enter read from rawtlt");
        double[] angles = new double[ts.getStackSize()];
        String line = bf.readLine();
        //System.out.println("first line is :" + line + ":");
        String[] split = line.trim().split("\\s+");
        boolean l = split.length > 2;
        //System.out.println("the split length is " + split.length);
        if (!l) angles[0] = Double.parseDouble(line);
        else angles[0] = Double.parseDouble(split[0]);
        for (int i = 1; i < angles.length; i++) {
            if (!l) {
                line = bf.readLine();
                angles[i] = Double.parseDouble(line.trim());
            } else {
                angles[i] = Double.parseDouble(split[i]);
            }
        }
        /*System.out.println("angles read ");
          for (int i = 0; i < angles.length; i++) {
              System.out.print(" / " + angles[i]);
          } */
        return angles;
    }

    public static void exportAlignedImages(TiltSeries ts, String dir, String filename) {
        String nameLC = filename.toLowerCase();
        if (nameLC.endsWith(".sel")) {
            IJ.runPlugIn(ts, "Sel_Writer", dir + filename);
        } else {
            ImageStack is = new ImageStack(ts.getWidth(), ts.getHeight());
            for (int i = 0; i < ts.getStackSize(); i++) {
                is.addSlice("" + ts.getTiltAngle(i), ts.getPixels(i));
                IJ.showStatus("creating aligned stack " + (i + 1) + "/" + ts.getStackSize());
            }
            ImagePlus imp = new ImagePlus(filename, is);

            if (nameLC.endsWith(".mrc")) {
                MRC_Writer writer=new MRC_Writer();
                String path=(dir==null)?filename:dir+filename;
                writer.setup(dir+filename, imp);
                writer.run(imp.getProcessor());
                //IJ.runPlugIn(imp, "MRC_Writer", dir + filename);
            } else if (nameLC.endsWith(".xmp") || nameLC.endsWith(".spi")) {
                IJ.runPlugIn(imp, "Spider_Writer", dir + filename);
            } else {
                imp.show();
                IJ.selectWindow(filename);
                IJ.save(dir + filename);
                imp.close();
            }
        }
    }
}
