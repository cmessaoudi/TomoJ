package fr.curie.tomoj.workflow;

import fr.curie.tomoj.SuperTiltSeries;
import fr.curie.tomoj.align.AffineAlignment;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.io.FileInfo;
import ij.io.FileSaver;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import fr.curie.tomoj.SuperTomoJPoints;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.landmarks.AlignmentLandmarkDualImproved;
import fr.curie.tomoj.landmarks.AlignmentLandmarkImproved;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 21/05/12
 * Time: 10:38
 * To change this template use File | Settings | File Templates.
 */
public class CommandWorkflow extends JTree {
    String projectDir;
    String imageDir;
    TiltSeries ts;
    String currentImages;
    BufferedWriter bf;
    boolean noUpdate = false;

    public CommandWorkflow() {
        super(new DefaultTreeModel(new UserAction("root", "", "root", false)));
        setDragEnabled(true);
        setDropMode(DropMode.ON);
        setTransferHandler(new TreeTransferHandler());
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    //System.out.println("key listening delete node from tree");
                    TreePath tp = getSelectionPath();
                    UserAction father = (UserAction) tp.getLastPathComponent();
                    removeNode(father);
                    //father.removeAllChildren();
                    saveWorkflow();
                    updateUI();
                }
            }
        });

    }

    /**
     * load transform from a file
     *
     * @param dir                 directory where the file is located. should finish with system separator
     * @param filename            name of the file
     * @param ts                  TiltSeries inside which transform are loaded
     * @param binning             factor to add to the translation. useful when transforms were computed on scaled version of the images.
     * @param options             options for loading (depends on alignment)
     */
    public static void loadTransforms(String dir, String filename, TiltSeries ts, double binning, boolean... options) {
        System.out.println("loading transform from: " + dir + filename);
        try {
            AlignmentLandmarkImproved ali = new AlignmentLandmarkImproved(dir, filename, ts.getTomoJPoints(), binning);
            if (ali.getCurrentAi().size() > 0) {
                ts.setAlignment(ali);
                return;
            }
        } catch (Exception e) {
            System.out.println("this is not a file containing all parameters.");
            e.printStackTrace();
        }/**/
        //System.out.println("load transform:"+dir+filename);
        AffineAlignment aa = (ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);
        try {
            aa.loadFromFile(dir+filename,binning,options);
        } catch (IOException ioe) {
            System.out.println("load Transform error");
            System.out.println(ioe);
            System.out.println(dir);
            System.out.println(filename);
            System.out.println(dir + filename);
            ioe.printStackTrace();
        }
    }


    /**
     * load transform from a file
     *
     * @param dir                 directory where the file is located. should finish with system separator
     * @param filename            name of the file
     * @param sts                 SuperTiltSeries inside which transform are loaded
     * @param binning             factor to add to the translation. useful when transforms were computed on scaled version of the images.
     * @param finalTransforms     true if the transforms as final transforms applied to each image, false ig the transforms are between each consecutive images
     * @param combineWithExisting if true combine the loaded transform with the current one...
     */
    public static void loadTransformsDual(String dir, String[] filename, SuperTiltSeries sts, double binning, boolean finalTransforms, boolean combineWithExisting, boolean... options) {

        for (String f : filename) {
            System.out.println("loading transform from: " + dir + f);
        }
        try {
            AlignmentLandmarkDualImproved ali = new AlignmentLandmarkDualImproved(dir, filename, sts.getSuperTomoJPoints(), binning);
            if (ali.getCurrentAi().size() > 0) {
                sts.setAlignment(ali);
                return;
            }
        } catch (Exception e) {
            System.out.println("this is not a file containing all parameters.");
            e.printStackTrace();
        }/**/
        //System.out.println("load transform:"+dir+filename);
        SuperTomoJPoints stp=sts.getSuperTomoJPoints();
        for (int k = 0; k < filename.length; k++) {
            //stp.getTomoJPoints(k).setLandmarks3DDual(null);
            AffineAlignment aa = (sts.getTiltSeries(k).getAlignment() instanceof AffineAlignment)? (AffineAlignment)sts.getTiltSeries(k).getAlignment():new AffineAlignment(sts.getTiltSeries(k));
            sts.getTiltSeries(k).setAlignment(aa);
            try {
                aa.loadFromFile(dir+filename[k],binning,options);
            } catch(IOException ioe){
                System.out.println("load Transform error");
                System.out.println(ioe);
                System.out.println(dir);
                System.out.println(filename[k]);
                System.out.println(dir + filename[k]);
                ioe.printStackTrace();
            }
        }
    }


    public static void loadTransformsDual(String dir, String[] filename, SuperTomoJPoints stp, double binning, boolean finalTransforms, boolean combineWithExisting, boolean... options) {

        for (String f : filename) {
            System.out.println("loading transform from: " + dir + f);
        }
        try {
            AlignmentLandmarkDualImproved ali = new AlignmentLandmarkDualImproved(dir, filename, stp, binning);
            if (ali.getCurrentAi().size() > 0) {
                ali.computeAis();
                int offset = 0;
                for (int k = 0; k < filename.length; k++) {
                    TiltSeries ts = stp.getTomoJPoints(k).getTiltSeries();
                    if (k > 0) {
                        offset += stp.getTomoJPoints(k - 1).getTiltSeries().getImageStackSize();
                    }
                    AffineAlignment aa=new AffineAlignment(ts);
                    ts.setAlignment(aa);
                    ts.combineTransforms(false);
                    for (int i = 0; i < ts.getImageStackSize(); i++) {
                        try {
                            System.out.println("#" + i + offset + " creating transform");
                            AffineTransform T = ali.getTransform(i + offset);
                            aa.setTransform(i + offset, T);
                        } catch (Exception E) {
                            System.out.println(E);
                        }
                    }
                    aa.convertTolocalTransform();
                    //ts.setNormalize(false);
                    ts.updateAndDraw();

                }
                return;
            }
        } catch (Exception e) {
            System.out.println("this is not a file containing all parameters.");
            e.printStackTrace();
        }/**/
        //System.out.println("load transform:"+dir+filename);
        //SuperTomoJPoints stp=sts.getSuperTomoJPoints();
        for (int k = 0; k < filename.length; k++) {
            //stp.getTomoJPoints(k).setLandmarks3DDual(null);
            try {
                BufferedReader in = new BufferedReader(new FileReader(dir + filename[k]));
                if (filename[k].endsWith(".xf") || filename[k].endsWith(".prexg")) {
                    System.out.println("open imod transform file");
                    for (int i = 0; i < stp.getTomoJPoints(k).getTiltSeries().getStackSize(); i++) {
                        String line = in.readLine().trim();
                        System.out.println(line);
                        while (line.startsWith(";") || line.startsWith("#")) line = in.readLine().trim();
                        String[] words = line.split("\\s+");
                        System.out.println("#words:" + words.length);
                        double[] tmp = new double[6];
                        for (int j = 0; j < tmp.length; j++) {
                            tmp[j] = Double.valueOf(words[j]);
                            if (j > 3) tmp[j] *= binning;
                            if (j == 5 && options != null && options.length > 0 && options[0]) tmp[j] = -tmp[j];
                        }
                        if (combineWithExisting) {
                            ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).addTransform(i, new AffineTransform(tmp));
                        } else ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).setTransform(i, new AffineTransform(tmp));

                    }
                    ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).convertTolocalTransform();
                } else if (filename[k].endsWith(".prexf")) {
                    System.out.println("open imod transform file");
                    for (int i = 0; i < stp.getTomoJPoints(k).getTiltSeries().getStackSize(); i++) {
                        String line = in.readLine().trim();
                        System.out.println(line);
                        while (line.startsWith(";") || line.startsWith("#")) line = in.readLine().trim();
                        String[] words = line.split("\\s+");
                        System.out.println("#words:" + words.length);
                        double[] tmp = new double[6];
                        tmp[0] = Double.valueOf(words[0]);
                        tmp[1] = Double.valueOf(words[1]);
                        tmp[2] = Double.valueOf(words[2]);
                        tmp[3] = Double.valueOf(words[3]);
                        tmp[4] = -Double.valueOf(words[4]) * binning;
                        if (options != null && options.length > 0 && options[0])
                            tmp[5] = Double.valueOf(words[5]) * binning;
                        else tmp[5] = -Double.valueOf(words[5]) * binning;

                        if (i == 0)
                            ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).setTransform(stp.getTomoJPoints(k).getTiltSeries().getStackSize() - 1, new AffineTransform(tmp));
                        else ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).setTransform(i - 1, new AffineTransform(tmp));
                    }
                } else {
                    String separator = filename[k].toLowerCase().endsWith("csv") ? ";" : "\\s+";
                    for (int i = 0; i < stp.getTomoJPoints(k).getTiltSeries().getStackSize(); i++) {
                        String line = in.readLine();
                        while (line.startsWith(";") || line.startsWith("#")) line = in.readLine();
                        String[] words = line.split(separator);
                        stp.getTomoJPoints(k).getTiltSeries().setTiltAngle(i, Double.valueOf(words[0]));
                        stp.getTomoJPoints(k).getTiltSeries().setTiltAxis(Double.valueOf(words[1]));
                        double[] tmp = new double[6];
                        for (int j = 0; j < tmp.length; j++) {
                            tmp[j] = Double.valueOf(words[j + 2]);
                            if (j > 3) tmp[j] *= binning;
                        }
                        if (combineWithExisting) {
                            ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).addTransform(i, new AffineTransform(tmp));
                        } else ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).setTransform(i, new AffineTransform(tmp));
                    }

                    if (finalTransforms) ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).convertTolocalTransform();
                    else {
                        String line = in.readLine();
                        String[] words = line.split(separator);
                        double[] tmp = new double[6];
                        for (int j = 0; j < tmp.length; j++) {
                            tmp[j] = Double.valueOf(words[j]);
                            if (j > 3) tmp[j] *= binning;
                        }
                        if (combineWithExisting)
                            ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).addToZeroTransform(new AffineTransform(tmp));
                        else ((AffineAlignment)stp.getTomoJPoints(k).getTiltSeries().getAlignment()).setZeroTransform(new AffineTransform(tmp));
                    }
                }
                in.close();
            } catch(IOException ioe){
                System.out.println("load Transform error");
                System.out.println(ioe);
                System.out.println(dir);
                System.out.println(filename[k]);
                System.out.println(dir + filename[k]);
                ioe.printStackTrace();
            }
        }
    }


    /**
     * save the transforms to file
     *
     * @param dir             the directory where the file will be saved
     * @param filename        the name of the file
     * @param ts              TiltSeries where the transforms are
     * @param finalTransforms true to save transforms as final transforms applied to each images, false to save transforms as transforms between each consecutive images.
     */
    public static void saveTransform(String dir, String filename, TiltSeries ts, boolean finalTransforms) {
        try {
            ts.getAlignment().saveToFile(dir+filename,finalTransforms, true);
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

    /**
     * load a landmark file
     *
     * @param dir      directory were the file is. the directory should finish with a directory separator.
     * @param filename name of the file.
     * @param tp       the TomoJPoints where the landmarks will be put.
     * @param binning  number by which multiply the positions (in case landmarks were computed on smaller or bigger version of current size)
     */

    public static void loadLandmarks(String dir, String filename, TomoJPoints tp, double binning) {
        try {
            tp.reset();
            BufferedReader in = new BufferedReader(new FileReader(dir + filename));
            String line = in.readLine();
            if (line == null) {
                System.out.println("load landmarks : empty file");
                return;
            }
            System.out.println("Loading landmarks from file: " + dir + filename + "\nbinning: "  + binning);
            boolean pointPickerFormat = false;
            boolean firstline = true;
            int currentLandmark = tp.getCurrentIndex();
            do {
                if (firstline) {
                    firstline = false;
                    if (line.toLowerCase().startsWith("point")) {
                        pointPickerFormat = true;
                        line = in.readLine();
                    }
                }
                if (line.startsWith("#")) continue;
                String[] words = line.split("\\s+");
                if (pointPickerFormat) {
                    //System.out.println(line);
                    int color = Integer.parseInt(words[4]);
                    int landmarksToCreate = color - (tp.getNumberOfPoints() - 1);
                    //System.out.println("color: " + color + " landmarks to create: " + landmarksToCreate);
                    while (landmarksToCreate > 0) {
                        tp.addNewSetOfPoints();
                        landmarksToCreate--;
                        //System.out.println("add new landmark set : new size is " + tp.getNumberOfPoints() + " there is " + landmarksToCreate + " more to create");
                    }
                    tp.setPoint(color, Integer.parseInt(words[3]) - 1, new Point2D.Double(Double.parseDouble(words[1]), Double.parseDouble(words[2])), true);
                } else {
                    int p = Integer.parseInt(words[0]);
                    boolean auto = (Integer.parseInt(words[4]) != 0);
                    while (p > currentLandmark) {
                        tp.addNewSetOfPoints(auto);
                        currentLandmark = p;
                        //System.out.println("adding a new marker chain");
                    }
                    //System.out.println("landmarks length=" + tp.getNumberOfPoints() + " auto=" + tp.getAutoLength());
                    double offset = (binning > 1) ? (binning - 1) / 2 : 0;
                    tp.setPoint(p, Integer.parseInt(words[1]) - 1, new Point2D.Double(Double.parseDouble(words[2]) * binning + offset, Double.parseDouble(words[3]) * binning + offset), auto);
                }
            } while ((line = in.readLine()) != null);
            //ts.setTomoJPoints(tp);

            in.close();
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

    public static void saveLandmarks(String dir, String filename, ArrayList<Point2D[]> landmarks) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(dir + filename));
            int nbpoint = 0;
            for (int i = 0; i < landmarks.size(); i++) {
                Point2D[] tmp = landmarks.get(i);
                int nbpointwritten = 0;
                for (int j = 0; j < tmp.length; j++) {
                    if (tmp[j] != null) {
                        int auto = 1;
                        out.write("" + nbpoint + "\t" + (j + 1) + "\t" + tmp[j].getX() + "\t" + tmp[j].getY() + "\t" + auto + "\r\n");
                        nbpointwritten++;
                    }
                }
                if (nbpointwritten > 0) nbpoint++;
            }
            out.flush();
            out.close();
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

    /**
     * saves the landmarks to file
     *
     * @param dir      directory where to save
     * @param filename the name of the file
     * @param ts       TiltSeries with the landmarks to save
     */

    public static void saveLandmarks(String dir, String filename, TiltSeries ts) {
        boolean save = ts.iscombiningTransforms();
        ts.combineTransforms(false);
        TomoJPoints tp = ts.getTomoJPoints();
        ArrayList<Point2D[]> repro = tp.getReprojectedLandmarks();
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(dir + filename));
            int nbpoint = 0;
            for (int i = 0; i < tp.getNumberOfPoints(); i++) {
                Point2D[] tmp = tp.getPoints(i);
                int nbpointwritten = 0;
                for (int j = 0; j < tmp.length; j++) {
                    if (tmp[j] != null) {
                        int auto = tp.isAutomaticallyGenerated(i) ? 1 : 0;
                        out.write("" + nbpoint + "\t" + (j + 1) + "\t" + tmp[j].getX() + "\t" + tmp[j].getY() + "\t" + auto + "\r\n");
                        nbpointwritten++;
                    }
                }
                if (nbpointwritten > 0) nbpoint++;
            }
            out.flush();
            out.close();
            if (repro != null) {
                BufferedWriter outrepro = new BufferedWriter(new FileWriter(dir + filename + "reprojected"));
                nbpoint = 0;
                for (int i = 0; i < tp.getNumberOfPoints(); i++) {
                    Point2D[] tmp = repro.get(i);
                    int nbpointwritten = 0;
                    for (int j = 0; j < tmp.length; j++) {
                        if (tmp[j] != null) {
                            int auto = tp.isAutomaticallyGenerated(i) ? 1 : 0;
                            outrepro.write("" + nbpoint + "\t" + (j + 1) + "\t" + tmp[j].getX() + "\t" + tmp[j].getY() + "\t" + auto + "\r\n");
                            nbpointwritten++;
                        }
                    }
                    if (nbpointwritten > 0) nbpoint++;
                }
                outrepro.flush();
                outrepro.close();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
        ts.combineTransforms(save);
    }

    public static void saveLandmarksStatistics(String dir, String filename, TiltSeries ts, int maxJump) {
        boolean save = ts.iscombiningTransforms();
        ts.combineTransforms(false);
        TomoJPoints tp = ts.getTomoJPoints();
        ArrayList<int[]> stats=tp.getChainsStatistics(maxJump);
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(dir + filename));
            String str="landmark";
            str+=";first";
            str+=";last";
            str+=";length";
            str+=";nbpoints";
            str+=";percentage defined";
            for(int j=4;j<stats.get(0).length;j++) str+=";jump "+(j-3);
            str+="\r\n";
            out.write(str );
            for (int i = 0; i < tp.getNumberOfPoints(); i++) {
                int[] tmp = stats.get(i);
                str=""+i;
                str+=";"+tmp[0];//first
                str+=";"+tmp[1];//last
                str+=";"+tmp[2];//length
                str+=";"+tmp[3];//nbpoints defined
                str+=";"+(tmp[3]/(double)tmp[2]); //percentage defined
                for(int j=4;j<tmp.length;j++) str+=";"+tmp[j];
                str+="\r\n";
                out.write(str );
            }
            out.flush();
            out.close();
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
        ts.combineTransforms(save);
    }

    public static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            if (is != null) is.close();
            if (os != null) os.close();
        }
    }

    public static void copyTransformFileWithBinning(File source, File dest, double binning) throws IOException {
        BufferedReader in = null;
        BufferedWriter out = null;
        try {
            in = new BufferedReader(new FileReader(source));
            out = new BufferedWriter(new FileWriter(dest));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(";") || line.startsWith("#")) out.write(line);
                else {
                    String[] words = line.split("\\s+");
                    double tmp;
                    int offset = 0;
                    switch (words.length) {
                        case 8:
                            out.write(words[0] + "\t");
                            out.write(words[1] + "\t");
                            offset = 2;
                        case 6:
                            for (int j = 0; j < 6; j++) {
                                tmp = Double.valueOf(words[j + offset]);
                                if (j > 3) tmp *= binning;
                                out.write(tmp + "\t");
                            }
                            break;
                        default:
                            out.write(line);

                    }
                }
                out.write("\r\n");
            }
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
    }

    public static void copyLandmarksFileWithBinning(File source, File dest, double binning) throws IOException {
        BufferedReader in = null;
        BufferedWriter out = null;
        try {
            in = new BufferedReader(new FileReader(source));
            out = new BufferedWriter(new FileWriter(dest));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) out.write(line);
                else {
                    String[] words = line.split("\\s+");
                    //if binning >1 put to center of the square of size binning*binning instead of top-left corner
                    double offset = (binning > 1) ? (binning - 1) / 2 : 0;
                    words[2] = "" + (Double.parseDouble(words[2]) * binning + offset);
                    words[3] = "" + (Double.parseDouble(words[3]) * binning + offset);
                    for (String s : words) out.write(s + "\t");
                }
                out.write("\r\n");
            }
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
    }

    public boolean noUpdate() {
        return noUpdate;
    }

    public TiltSeries initFromXml(String directory, String fileName) {
        projectDir = directory;
        if (!projectDir.endsWith(System.getProperty("file.separator")))
            projectDir += System.getProperty("file.separator");
        imageDir = "";
        loadWorkflow();
        ImagePlus tmp = new ImagePlus(".init.tif");
        ts = new TiltSeries(tmp);
        setRootVisible(false);
        return ts;
    }

    public void init(String projectDirectory, TiltSeries ts) {
        projectDir = projectDirectory;
        this.ts = ts;
        FileInfo fi = ts.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts.getFileInfo();
        }
        imageDir = (fi != null && fi.directory != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
        if (!imageDir.endsWith(System.getProperty("file.separator")))
            imageDir += System.getProperty("file.separator");
        if (projectDir != null) {
            if (!projectDir.endsWith(System.getProperty("file.separator")))
                projectDir += System.getProperty("file.separator");
            File f = new File(imageDir + projectDir);
            String[] files = f.list();
            if (files.length > 0) {
                for (String s : files) {
                    if (s.compareToIgnoreCase("project.TomoJ") == 0) {
                        loadWorkflow();
                    }
                }
            }
        }
        setRootVisible(false);
    }

    public String getCurrentImages() {
        return currentImages;
    }

    public void setCurrentImages(String currentImages) {
        this.currentImages = currentImages;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public String getProjectPath() {
        return imageDir + projectDir;
    }

    public void addCommandToHistory(UserAction ua, boolean createID, boolean saveFiles, boolean saveImage, ImagePlus reconstruction) {
        //System.out.println("comand workfloaw add command:"+saveFiles);
        DefaultMutableTreeNode parentNode = null;
        DefaultTreeModel model = (DefaultTreeModel) this.getModel();
        TreePath parentPath = this.getSelectionPath();
        if (parentPath == null) {
            parentNode = (DefaultMutableTreeNode) model.getRoot();
        } else {
            parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

        }


        DefaultMutableTreeNode child = ua;
        model.insertNodeInto(child, parentNode, parentNode.getChildCount());
        TreePath childPath = new TreePath(child.getPath());

        this.scrollPathToVisible(childPath);
        if (createID) {
            //System.out.println("create ID");
            String nodeNumber = "";
            //System.out.println("autosave");
            //System.out.println(nodeNumber);
            if (parentPath == null) {
                ua.setId(".");
                return;  // if not saving it return because there is nothing to do
            }
            //DefaultMutableTreeNode node = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
            DefaultMutableTreeNode node = child;

            UserAction parent = (UserAction) node.getParent();
            nodeNumber = parent.getId() + model.getIndexOfChild(parent, node) + ".";
            /*while (!parent.isRoot()) {
                nodeNumber = model.getIndexOfChild(parent, node) + "." + nodeNumber;
                //System.out.println(nodeNumber);
                node = parent;
                parent = (UserAction) parent.getParent();
            }
            nodeNumber = model.getIndexOfChild(parent, node) + "." + nodeNumber;
            //System.out.println(nodeNumber);      */
            ua.setId(nodeNumber);
        }
        //System.out.println("savefiles:"+saveFiles);

        if (saveFiles) {
            //System.out.println("call to autosave");
            autoSave(saveImage, ua, reconstruction);
        }

        this.setSelectionPath(childPath);
    }

    /**
     * autosave stuff: the data you want to save will be saved with an automatic name that it created as node_identification.name.extension <BR></BR>
     * extension will be .tif for images, .transf for transform files and .landmarks fo landmarks<BR></BR>
     * the latter two file type are simple text file.
     *
     * @param saveImages true to save the images
     * @param action     the node to save
     */
    public void autoSave(boolean saveImages, UserAction action, ImagePlus rec) {
        System.out.println("command workflow autosave:" + imageDir + projectDir);

        //String savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
        /* if (!projectDir.endsWith(System.getProperty("file.separator")))
   projectDir += System.getProperty("file.separator");
if (!imageDir.endsWith(System.getProperty("file.separator")))
   imageDir += System.getProperty("file.separator");   */
        DefaultTreeModel model = (DefaultTreeModel) this.getModel();
        TreePath parentPath = this.getSelectionPath();

        if (saveImages) {
            FileSaver fs = new FileSaver(ts);
            //System.out.println("file is saved as " + savedir+ "_ite" + i + ".tif");
            fs.saveAsTiffStack(imageDir + projectDir + action.getId() + action.getTransformFileName() + ".tif");
            action.setCurrentImages(action.getId() + action.getTransformFileName() + ".tif");
            currentImages = action.getCurrentImages();


        } else {
            action.setCurrentImages(currentImages);
        }
        saveTransform(imageDir + projectDir, action.getId() + action.getTransformFileName() + ".transf", ts, false);

        saveLandmarks(imageDir + projectDir, action.getId() + action.getTransformFileName() + ".landmarks", ts);
        if (action.getCurrentReconstruction() != null) {
            FileSaver fs = new FileSaver(rec);
            fs.saveAsTiffStack(imageDir + projectDir + action.getId() + action.getCurrentReconstruction() + ".tif");
        }
        saveWorkflow();


    }

    public void saveWorkflow() {
        SAXBuilder builder = new SAXBuilder();
        org.jdom2.Document document = new Document(createElement((UserAction) this.getModel().getRoot()));
        XMLOutputter outputXML = new XMLOutputter(Format.getPrettyFormat());
        try {
            bf = new BufferedWriter(new FileWriter(imageDir + projectDir + "project.TomoJ"));

            outputXML.output(document, bf);
            bf.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadWorkflow() {
        System.out.println("load workflow");
        //On cr�e une instance de SAXBuilder
        SAXBuilder sxb = new SAXBuilder();
        try {
            //On cr�e un nouveau document JDOM avec en argument le fichier XML
            //Le parsing est termin� ;)
            org.jdom2.Document document = sxb.build(new File(imageDir + projectDir + "project.TomoJ"));
            //On initialise un nouvel �l�ment racine avec l'�l�ment racine du document.
            Element racine = document.getRootElement();
            DefaultTreeModel model = (DefaultTreeModel) this.getModel();
            UserAction parentNode = (UserAction) model.getRoot();
            parentNode.removeAllChildren();
            noUpdate = true;
            if (getSelectionPath() == null) setSelectionRow(0);
            readUserActionFromXML(racine, parentNode);
            noUpdate = false;
            setSelectionPath(new TreePath(parentNode.getLastLeaf().getPath()));

        } catch (Exception e) {
        }


    }

    private Element createElement(UserAction ua) {
        Element el = new Element("UserAction");
        Element id = new Element("ID");
        id.setText(ua.getId());
        Element command = new Element("Command");
        command.setText(ua.getCommand());
        Element args = new Element("Arguments");
        args.setText(ua.getArguments());
        Element transform = new Element("Transform");
        transform.setText(ua.getId() + ua.getTransformFileName() + ".transf");
        Element points = new Element("Landmarks");
        points.setText(ua.getId() + ua.getTransformFileName() + ".landmarks");
        Element images = new Element("images");
        images.setText(ua.getCurrentImages());
        Element rec = new Element("reconstruction");
        if (ua.getCurrentReconstruction() != null) rec.setText(ua.getCurrentReconstruction());
        el.addContent(id);
        el.addContent(command);
        el.addContent(args);
        el.addContent(transform);
        el.addContent(points);
        el.addContent(images);
        el.addContent(rec);

        if (ua.getChildCount() > 0) {
            Enumeration u = ua.children();
            while (u.hasMoreElements()) {
                el.addContent(createElement((UserAction) u.nextElement()));

            }
        }
        return el;
    }

    private UserAction readUserActionFromXML(Element elem, UserAction parent) {
        System.out.println("read UserAction");
        if (elem.getName().startsWith("UserAction")) {
            String id = elem.getChild("ID").getText();
            String command = elem.getChild("Command").getText();
            String arg = elem.getChild("Arguments").getText();
            String transform = elem.getChild("Transform").getText();
            String points = elem.getChild("Landmarks").getText();
            String images = elem.getChild("images").getText();
            String rec = elem.getChild("reconstruction").getText();
            String tmp = transform.substring(id.length(), transform.length() - 7);
            UserAction ua = new UserAction(command, arg, tmp, false);
            ua.setId(id);
            ua.setCurrentImages(images);
            ua.setCurrentReconstruction(rec);
            //((DefaultTreeModel)this.getModel()).insertNodeInto(ua,parent,parent.getChildCount());
            if (command.compareTo("root") == 0) {
                ua = parent;
            } else {
                this.addCommandToHistory(ua, false, false, false, null);
            }

            System.out.println("UserAction created: " + ua + " " + ua.getArguments());
            List uas = elem.getChildren("UserAction");
            System.out.println("number of children: " + uas.size());
            for (int i = 0; i < uas.size(); i++) {
                UserAction uachild = readUserActionFromXML((Element) uas.get(i), ua);
                TreePath childPath = new TreePath(ua.getPath());
                this.scrollPathToVisible(childPath);
                this.setSelectionPath(childPath);
                //ua.add(uachild);
            }


            System.out.println();
        }
        return null;
    }

    public void removeNode(UserAction node) {
        int nbChildren = node.getChildCount();
        while (nbChildren > 0) {
            removeNode((UserAction) node.getChildAt(0));
            nbChildren = node.getChildCount();
        }
        try {
            File f = new File(imageDir + projectDir + node.getId() + node.getTransformFileName() + ".transf");
            f.delete();
            f = new File(imageDir + projectDir + node.getId() + node.getTransformFileName() + ".landmarks");
            f.delete();
            f = new File(imageDir + projectDir + node.getId() + node.getTransformFileName() + ".tif");
            if (f.exists()) f.delete();
            f = new File(imageDir + projectDir + node.getId() + node.getTransformFileName() + ".landmarksreprojected");
            if (f.exists()) f.delete();


        } catch (Exception e) {
            e.printStackTrace();
        }
        UserAction parent = (UserAction) node.getParent();
        parent.remove(node);

    }

    class TreeTransferHandler extends TransferHandler {
        DataFlavor nodesFlavor;
        DataFlavor[] flavors = new DataFlavor[1];
        DefaultMutableTreeNode[] nodesToRemove;

        public TreeTransferHandler() {
            try {
                String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + DefaultMutableTreeNode[].class.getName() + "\"";
                nodesFlavor = new DataFlavor(mimeType);
                flavors[0] = nodesFlavor;
            } catch (ClassNotFoundException e) {
                System.out.println("ClassNotFound: " + e.getMessage());
            }
        }

        private boolean haveCompleteNode(JTree tree) {
            //System.out.println("TreeTransfertHandler.haveCompleteNode()");
            int[] selRows = tree.getSelectionRows();
            TreePath path = tree.getPathForRow(selRows[0]);
            DefaultMutableTreeNode first = (DefaultMutableTreeNode) path.getLastPathComponent();
            int childCount = first.getChildCount();
            // first has children and no children are selected.
            if (childCount > 0 && selRows.length == 1)
                return false;
            // first may have children.
            for (int i = 1; i < selRows.length; i++) {
                path = tree.getPathForRow(selRows[i]);
                DefaultMutableTreeNode next = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (first.isNodeChild(next)) {
                    // Found a child of first.
                    if (childCount > selRows.length - 1) {
                        // Not all children of first are selected.
                        return false;
                    }
                }
            }
            return true;
        }

        public boolean importData(TransferSupport support) {
            System.out.println("TreeTransfertHandler.importData()");
            if (!canImport(support)) {
                return false;
            }
            // Extract transfer data.
            UserAction[] nodes = null;
            try {
                Transferable t = support.getTransferable();
                nodes = (UserAction[]) t.getTransferData(nodesFlavor);
            } catch (UnsupportedFlavorException ufe) {
                System.out.println("UnsupportedFlavor: " + ufe.getMessage());
            } catch (IOException ioe) {
                System.out.println("I/O error: " + ioe.getMessage());
            }
            // Get drop location info.
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            int childIndex = dl.getChildIndex();
            TreePath dest = dl.getPath();
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) dest.getLastPathComponent();
            JTree tree = (JTree) support.getComponent();
            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
            // Configure for drop mode.
            int index = childIndex;    // DropMode.INSERT
            if (childIndex == -1) {     // DropMode.ON
                index = parent.getChildCount();
            }
            GenericDialog gd = new GenericDialog("copy node options");
            gd.addNumericField("binning", 1, 1);
            String[] refLand = { "none", "local minima", "local maxima" };
            gd.addChoice("refine landmarks position", refLand, refLand[0]);
            gd.showDialog();
            if (gd.wasCanceled()) return false;
            double bin = gd.getNextNumber();
            int refIndex = gd.getNextChoiceIndex();
            // Add data to model.
            for (int i = 0; i < nodes.length; i++) {
                parent.insert(nodes[i], parent.getChildCount());
                String newID = "";
                String images = ((UserAction) parent).getCurrentImages();

                newID = ((UserAction) parent).getId() + model.getIndexOfChild(parent, nodes[i]) + ".";
                String oldID = nodes[i].getId();
                nodes[i].setId(newID);
                String newName = "copyOf" + nodes[i].getTransformFileName();
                try {
                    if (bin == 1) {
                        copyFileUsingStream(new File(imageDir + projectDir + oldID + nodes[i].getTransformFileName() + ".transf"), new File(imageDir + projectDir + newID + newName + ".transf"));
                        copyFileUsingStream(new File(imageDir + projectDir + oldID + nodes[i].getTransformFileName() + ".landmarks"), new File(imageDir + projectDir + newID + newName + ".landmarks"));
                    } else {
                        copyTransformFileWithBinning(new File(imageDir + projectDir + oldID + nodes[i].getTransformFileName() + ".transf"), new File(imageDir + projectDir + newID + newName + ".transf"), bin);
                        copyLandmarksFileWithBinning(new File(imageDir + projectDir + oldID + nodes[i].getTransformFileName() + ".landmarks"), new File(imageDir + projectDir + newID + newName + ".landmarks"), bin);
                    }
                    nodes[i].setTransformFileName(newName);
                    nodes[i].setCurrentImages(images);
                    tree.scrollPathToVisible(new TreePath(nodes[i].getPath()));
                    tree.setSelectionPath(new TreePath(nodes[i].getPath()));

                    if (bin != 1) ts.getTomoJPoints().refineLandmarksToLocalCritical(bin, refIndex == 1);
                    tree.updateUI();
                    ((CommandWorkflow) tree).saveWorkflow();
                    /*int row = tree.getRowForPath(new TreePath(nodes[i].getPath()));
                    tree.expandRow(row);
                    tree.  */
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            return true;
        }

        public boolean canImport(TransferSupport support) {
            //System.out.println("TreeTransfertHandler.canImport()");
            if (!support.isDrop()) {
                return false;
            }
            support.setShowDropLocation(true);
            if (!support.isDataFlavorSupported(nodesFlavor)) {
                return false;
            }
            // Do not allow a drop on the drag source selections.
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            if (dl == null) return false;
            JTree tree = (JTree) support.getComponent();
            int dropRow = tree.getRowForPath(dl.getPath());

            DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();



            /*
            if( dl.getPath().getPathCount() < 2 ){
				return false;
			}



			//Si la droplocation n'est pas un noeud ( ou ne deviendra pas un noeud )
			if ( dl.getChildIndex() == -1 ){
				return false;
			}


			int[] selRows = tree.getSelectionRows();
			for(int i = 0; i < selRows.length; i++) {
				if(selRows[i] == dropRow) {
					return false;
				}
			}
			// Do not allow MOVE-action drops if a non-leaf node is
			// selected unless all of its children are also selected.
			int action = support.getDropAction();
			if(action == MOVE) {
				return haveCompleteNode(tree);
			}
			// Do not allow a non-leaf node to be copied to a level
			// which is less than its source level.
			TreePath dest = dl.getPath();
			DefaultMutableTreeNode target =(DefaultMutableTreeNode)dest.getLastPathComponent();
			TreePath path = tree.getPathForRow(selRows[0]);
			DefaultMutableTreeNode firstNode = (DefaultMutableTreeNode)path.getLastPathComponent();
			if(firstNode.getChildCount() > 0 &&  target.getLevel() < firstNode.getLevel()) {
				return false;
			} */
            return true;
        }

        public int getSourceActions(JComponent c) {
            //System.out.println("TreeTransfertHandler.getSourceAction()");

            return COPY;
        }

        protected Transferable createTransferable(JComponent c) {
            //System.out.println("TreeTransfertHandler.createTransferable()");
            JTree tree = (JTree) c;
            TreePath[] paths = tree.getSelectionPaths();
            if (paths != null) {
                // Make up a node array of copies for transfer and
                // another for/of the nodes that will be removed in
                // exportDone after a successful drop.
                List<UserAction> copies = new ArrayList<UserAction>();
                List<UserAction> toRemove = new ArrayList<UserAction>();
                UserAction node = (UserAction) paths[0].getLastPathComponent();
                UserAction copy = copy(node);
                copies.add(copy);
                toRemove.add(node);
                for (int i = 1; i < paths.length; i++) {
                    UserAction next = (UserAction) paths[i].getLastPathComponent();
                    // Do not allow higher level nodes to be added to list.
                    if (next.getLevel() < node.getLevel()) {
                        break;
                    } else if (next.getLevel() > node.getLevel()) {  // child node
                        copy.add(copy(next));
                        // node already contains child
                    } else {                                        // sibling
                        copies.add(copy(next));
                        toRemove.add(next);
                    }
                }
                UserAction[] nodes = copies.toArray(new UserAction[copies.size()]);
                nodesToRemove = toRemove.toArray(new UserAction[toRemove.size()]);
                return new NodesTransferable(nodes);
            }
            return null;
        }

        /**
         * Defensive copy used in createTransferable.
         */
        private UserAction copy(UserAction node) {
            //System.out.println("TreeTransfertHandler.copy()");
            UserAction ua = new UserAction("copyinTree", "from " + node.getId() + node.getCommand(), node.getTransformFileName(), false);
            ua.setId(node.getId());
            return ua;
        }

        protected void exportDone(JComponent source, Transferable data, int action) {
            //System.out.println("ExportDone");

            //System.out.println("TreeTransfertHandler.exportDone()");


        }

        public String toString() {
            return getClass().getName();
        }

        public class NodesTransferable implements Transferable {
            UserAction[] nodes;
            // DefaultMutableTreeNode[] nodes;

            public NodesTransferable(UserAction[] nodes) {
                this.nodes = nodes;
            }

            public DataFlavor[] getTransferDataFlavors() {
                return flavors;
            }

            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return nodesFlavor.equals(flavor);
            }

            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor))
                    throw new UnsupportedFlavorException(flavor);
                return nodes;
            }
        }
    }

}

