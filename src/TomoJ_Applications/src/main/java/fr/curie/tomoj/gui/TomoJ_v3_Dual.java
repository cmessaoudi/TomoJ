package fr.curie.tomoj.gui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.tomoj.align.AffineAlignment;
import ij.*;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.plugin.BrowserLauncher;
import ij.plugin.PlugIn;
import ij.process.StackConverter;
import fr.curie.tomoj.SuperTomoJPoints;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.tomography.AnglesForTiltSeries;
import fr.curie.utils.Chrono;
import fr.curie.tomoj.workflow.CommandWorkflow;
import fr.curie.tomoj.workflow.UserAction;
import fr.curie.tomoj.application.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static java.lang.Double.MAX_VALUE;

public class TomoJ_v3_Dual implements PlugIn {

    private JPanel panelroot;
    private JButton alignZeroTiltImagesButton;
    private JButton combineLandmarksButton;
    private JButton alignAllButton;
    private JButton reconstructAllButton;
    private JCheckBox showOnlyCommonLandmarksCheckBox;
    private JButton TESTDUALButton;
    private JButton runButton;
    private JPanel applicationPanel;
    private JButton manualAlignmentButton;
    private JTabbedPane tabbedPane1;

    CustomStackWindowDual window;
    ArrayList<TiltSeriesPanel> tsList;
    TiltSeriesPanel currentDisplay;
    SuperTomoJPoints stp;
    Application currentAppli;
    Application alignZeroTilt;
    Application combineLandmarks;
    Application dualAlign;
    AlignDualZeroTiltManual manualAlignZeroTilt;
    ADOSSARTApplication dualReconstruction;
    protected ArrayList<UserAction> log;
    GridConstraints constraints;
    AffineTransform T12 = new AffineTransform();

    public TomoJ_v3_Dual() {
        log = new ArrayList<UserAction>();
        constraints = new GridConstraints();
        constraints.setFill(GridConstraints.FILL_BOTH);


        alignZeroTiltImagesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                T12 = performPreAlignDual();
                System.out.println(T12);

                //tsList.get(0).getTiltSeries().setAlignMethodForReconstruction(TiltSeries.ALIGN_PROJECTOR);
                //tsList.get(1).getTiltSeries().setAlignMethodForReconstruction(TiltSeries.ALIGN_PROJECTOR);
                //TomoJPoints tp1 = tsList.get(0).getTiltSeries().getTomoJPoints();
                //TomoJPoints tp2 = tsList.get(1).getTiltSeries().getTomoJPoints();

                //loadLandmarks("Z:\\images_test\\Data_test_Dual\\", "K1_MTs_1a_points.txt", tp1, 1.0);
                //loadLandmarks("Z:\\images_test\\Data_test_Dual\\", "K1_MTs_1b_points2.txt", tp2, 1.0);
                if (stp == null) initSuperTomoJPoints();
                //CommandWorkflow.loadTransformsDual("Z:\\images_test\\Data_test_Dual\\", new String[]{"K1_MTs_1a_var_transf.csv", "K1_MTs_1b_var_transf.csv"}, stp, 1, false, true);

            }
        });

        combineLandmarksButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (stp == null) initSuperTomoJPoints();
                computeCommonLandmarks0degree(0, 1, 5, T12);
                currentDisplay.getTiltSeries().setSlice(currentDisplay.getTiltSeries().getCurrentSlice());
                currentDisplay.getTiltSeries().updateAndDraw();
            }
        });
        showOnlyCommonLandmarksCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (TiltSeriesPanel tsp : tsList) {
                    tsp.getTiltSeries().getTomoJPoints().showCommon(showOnlyCommonLandmarksCheckBox.isSelected());
                    currentDisplay.getTiltSeries().setSlice(currentDisplay.getTiltSeries().getCurrentSlice());
                    currentDisplay.getTiltSeries().updateAndDraw();
                }
            }
        });
        alignAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (stp == null) initSuperTomoJPoints();
                stp.fuseLandmarks();
                stp.displayChainsInfo();
                if (dualAlign == null) dualAlign = new DualAlignWithLandmarks(stp);
                setApplication(dualAlign);

            }
        });
        reconstructAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (dualReconstruction == null) {
                    dualReconstruction = new ADOSSARTApplication(tsList);
                    dualReconstruction.setSuperTomoJPoints(stp);
                }
                setApplication(dualReconstruction);

            }
        });


        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentAppli.setDisplayPreview(false);
                final Chrono time = new Chrono(100);
                final Thread T = new Thread() {
                    public void run() {
                        currentAppli.run();
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
                };
                T.start();

                final Component dia = panelroot;
                final Thread progress = new
                        Thread() {
                            public void run() {
                                ProgressMonitor toto = new ProgressMonitor(dia, currentAppli.name(), "", 0, 100);
                                while (T.isAlive()) {
                                    if (toto.isCanceled()) {
                                        currentAppli.interrupt();
                                        //T.stop();
                                        toto.close();
                                        System.out.println("process interrupted");
                                        IJ.showStatus("process interrupted");
                                    } else {
                                        time.stop();
                                        toto.setProgress((int) (currentAppli.getCompletion()));
                                        String note = "" + (int) currentAppli.getCompletion();
                                        if (currentAppli.getCompletion() > 0)
                                            note += "% approximately " + time.remainString(currentAppli.getCompletion()) + " left";
                                        toto.setNote(note);
                                        try {
                                            sleep(1000);
                                        } catch (Exception e) {
                                            System.out.println(e);
                                        }
                                    }
                                }
                                toto.close();
                            }
                        };
                //ConcurrencyUtils.submit(T);
                progress.start();

            }
        });


        manualAlignmentButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (manualAlignZeroTilt == null) manualAlignZeroTilt = new AlignDualZeroTiltManual(tsList);
                else manualAlignZeroTilt.initTiltSeries(tsList);
                setApplication(manualAlignZeroTilt);
            }
        });
    }

    void setApplication(Application appli) {
        if (currentAppli != null) currentAppli.setDisplayPreview(false);
        currentAppli = appli;
        currentAppli.setDisplayPreview(true);

        JPanel appliP = currentAppli.getJPanel();
        if (appliP == null) System.out.println("appli null!!!!");
        applicationPanel.removeAll();
        applicationPanel.add(appliP, constraints);

        panelroot.revalidate();
        panelroot.repaint();
    }

    public SuperTomoJPoints initSuperTomoJPoints() {
        ArrayList<TomoJPoints> tplist = new ArrayList<TomoJPoints>();
        for (int i = 0; i < tsList.size(); i++) {
            TomoJPoints tp = tsList.get(i).getTiltSeries().getTomoJPoints();
            tp.removeEmptyChains();
            System.out.println("tp " + i + " has " + tp.getNumberOfPoints() + " points");
            tplist.add(tp);
        }
        stp = new SuperTomoJPoints(tplist, false);
        return stp;
    }

    AffineTransform performPreAlignDual() {
        TiltSeries ts1 = tsList.get(0).getTiltSeries();
        TiltSeries ts2 = tsList.get(1).getTiltSeries();
        ts1.setSlice(ts1.getZeroIndex() + 1);
        ts2.setSlice(ts2.getZeroIndex() + 1);
        AffineTransform zeroTbkp = new AffineTransform(ts2.getAlignment().getZeroTransform());
        System.out.println("zero T before prealign :" + zeroTbkp);


        final Align2ImagesDialog dialog = new Align2ImagesDialog(new ImagePlus("ts1", ts1.getProcessor().duplicate()), new ImagePlus("ts2", ts2.getProcessor().duplicate()));
        dialog.pack();
        dialog.setTitle("prealign");
        dialog.setVisible(true);

        T12 = new AffineTransform();
        Thread T = new Thread() {
            public void run() {
                while (dialog.isActive()) {
                    try {
                        this.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (dialog.wasCanceled()) {
                    System.out.println("canceled");
                    return;
                }
                System.out.println("alignment found");

                T12.translate(dialog.getTranslationX(), dialog.getTranslationY());
                T12.rotate(Math.toRadians(dialog.getRotation()));
                System.out.println("after:" + T12);

            }
        };
        T.start();

        return T12;


    }

    public void computeCommonLandmarks0degree(int firstTs, int secondTs, double threshold, AffineTransform T) {
        Point2D[] points1, points2;
        TiltSeries ts1 = tsList.get(firstTs).getTiltSeries();
        TiltSeries ts2 = tsList.get(secondTs).getTiltSeries();
        computeCommonLandmarks0degree(ts1, ts2, threshold, T);
    }

    public void computeCommonLandmarks0degree(TiltSeries ts1, TiltSeries ts2, double threshold, AffineTransform T) {
        Point2D[] points1, points2;
        for (TiltSeriesPanel tsp : tsList) tsp.getTiltSeries().getTomoJPoints().removeEmptyChains();


        // All 2D points on 0 degree tilt axis images
        points1 = ts1.getTomoJPoints().getCenteredPointsOnImage(ts1.getZeroIndex());
        points2 = ts2.getTomoJPoints().getCenteredPointsOnImage(ts2.getZeroIndex());

        //Hashtable<Integer, Integer> common = getCommonPointsDual0(5);
        // get the zero tilt transform to apply to the 1st series points
        AffineTransform tr1 = new AffineTransform();
        AffineTransform T1 = ts1.getAlignment().getZeroTransform();
        tr1.concatenate(T1);

        // Transform all points1t
        Point2D[] points1t = new Point2D[points1.length];
        for (int i = 0; i < points1.length; i++) {
            if (points1[i] != null) {
                points1t[i] = new Point2D.Double(points1[i].getX(), points1[i].getY());
                tr1.transform(points1t[i], points1t[i]);
            }
        }

        // get the zero tilt transform to apply to the 2nd series points
        AffineTransform tr2 = new AffineTransform();
        AffineTransform T2 = ts2.getAlignment().getZeroTransform();
        tr2.concatenate(T2);
        tr2.concatenate(T);
        System.out.println("T2=" + T2);
        System.out.println("T12=" + T);
        System.out.println("final=" + tr2);

        // Transform all points2t
        Point2D[] points2t = new Point2D[points2.length];
        for (int i = 0; i < points2.length; i++) {
            if (points2[i] != null) {
                points2t[i] = new Point2D.Double(points2[i].getX(), points2[i].getY());
                tr2.transform(points2t[i], points2t[i]);
            }
        }

        // Find common points from ts1 to ts2
        Point2D[][] common_points12 = new Point2D[points1t.length][2];
        for (int i = 0; i < points1t.length; i++) {
            if (points1t[i] != null) {
                double min = MAX_VALUE;
                Point2D best_match = null;
                for (Point2D p2 : points2t) {
                    if (p2 != null) {
                        double d = points1t[i].distanceSq(p2);
                        if (d < min) {
                            min = d;
                            best_match = p2;
                        }
                    }
                }
                if (best_match != null && Math.sqrt(min) <= threshold) {
                    common_points12[i][0] = points1t[i];
                    common_points12[i][1] = best_match;
                }
            }
        }

        // Find common points from ts2 to ts1
        Point2D[][] common_points21 = new Point2D[points2t.length][2];
        for (int i = 0; i < points2t.length; i++) {
            if (points2t[i] != null) {
                double min = MAX_VALUE;
                Point2D best_match = null;
                for (Point2D p1 : points1t) {
                    if (p1 != null) {
                        double d = points2t[i].distanceSq(p1);
                        if (d < min) {
                            min = d;
                            best_match = p1;
                        }
                    }
                }
                if (best_match != null && Math.sqrt(min) <= threshold) {
                    common_points21[i][0] = points2t[i];
                    common_points21[i][1] = best_match;
                }
            }
        }

        Hashtable<Integer, Integer> common_points = new Hashtable<>();

        for (int i = 0; i < common_points12.length; i++) {
            if (common_points12[i][0] != null) {
                for (int j = 0; j < common_points21.length; j++) {
                    if (common_points21[j][0] != null) {
                        // test if the best point correspondence is found in both direction
                        if ((common_points12[i][0] == common_points21[j][1]) &&
                                (common_points12[i][1] == common_points21[j][0])) {
                            // save the chain number (index)
                            common_points.put(i, j);
                        }
                    }
                }
            }
        }

//        Integer[] common1 = common.keySet().toArray(new Integer[0]);
//        Integer[] common2 = common.values().toArray(new Integer[0]);

        // Set common landmarks for each TP
        Hashtable commonPoints1 = new Hashtable<>();
        Hashtable commonPoints2 = new Hashtable<>();

        commonPoints1 = common_points;
        common_points.forEach((k, v) -> commonPoints2.put(v, k));

        System.out.println("\nCommon landmarks: " + common_points.size() + "\n");

        ts1.getTomoJPoints().setCommonLandmarksIndex(commonPoints1);
        ts2.getTomoJPoints().setCommonLandmarksIndex(commonPoints2);

        ts1.getTomoJPoints().setProperLandmarksIndex();
        ts2.getTomoJPoints().setProperLandmarksIndex();

    }

    @Override
    public void run(String s) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        ImagePlus imp = IJ.getImage();
        TiltSeries ts1 = convertToTiltSeries(imp);
        System.out.println("conversion tilt series done");
        TiltSeriesPanel tsPanel1 = new TiltSeriesPanel(ts1);
        System.out.println("create tilt series panel done");

        tsList = new ArrayList<TiltSeriesPanel>();
        tsList.add(tsPanel1);
        //tsList.add(tsPanel2);

        window = new CustomStackWindowDual(tsList);
        System.out.println("create window done");
        addMenu();
        System.out.println("adding menu done");
        window.pack();
    }

    public void addMenu() {
        //add Menu
        MenuBar menuBar = new MenuBar();
        //tilt series menu
        Menu tiltMenu = new Menu("tilt series");
        menuBar.add(tiltMenu);
        MenuItem openTsMI = new MenuItem("open new tilt series...");
        openTsMI.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentDisplay.getCurrentApplication().setDisplayPreview(false);
                ImagePlus imp = IJ.openImage();
                TiltSeries ts = convertToTiltSeries(imp);
                TiltSeriesPanel tsp = new TiltSeriesPanel(ts);
                tsList.add(tsp);
                window.updateGUI();
                window.selectTab(tsList.size() - 1);
                window.setCurrentTiltSeries(tsList.size() - 1);
                imp.hide();

            }
        });
        tiltMenu.add(openTsMI);

        MenuItem addTsMI = new MenuItem("add tilt series...");
        addTsMI.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentDisplay.getCurrentApplication().setDisplayPreview(false);
                int[] ids = WindowManager.getIDList();
                String[] titles = WindowManager.getImageTitles();
                GenericDialog gd = new GenericDialog("choose image");
                gd.addChoice("which image do you want to use : ", titles, titles[0]);
                gd.showDialog();
                if (gd.wasCanceled()) return;

                ImagePlus imp = WindowManager.getImage(ids[gd.getNextChoiceIndex()]);

                TiltSeries ts = convertToTiltSeries(imp);
                TiltSeriesPanel tsp = new TiltSeriesPanel(ts);
                tsList.add(tsp);
                window.updateGUI();
                window.selectTab(tsList.size() - 1);
                window.setCurrentTiltSeries(tsList.size() - 1);

            }
        });
        tiltMenu.add(addTsMI);

        MenuItem exportAlignedTiltSeries = new MenuItem("export aligned Tilt Series");
        exportAlignedTiltSeries.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                SaveDialog sd = new SaveDialog("export aligned images as...", ts.getTitle() + "ali", ".tif");
                String dir = sd.getDirectory();
                String name = sd.getFileName();
                if (dir == null || name == null) {
                    return;
                }
                System.out.println("save as " + dir + " " + name);
                exportAlignedImages(ts, dir, name);
                IJ.showStatus("export finished");
            }
        });
        tiltMenu.add(exportAlignedTiltSeries);
        MenuItem setTiltAngles = new MenuItem("redefine tilt angles");
        setTiltAngles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                AnglesForTiltSeries.ask4Angles(ts);
                ts.sortImages(true);
            }
        });
        tiltMenu.add(setTiltAngles);

        MenuItem sortTilt=new MenuItem("sort tilt images");
        sortTilt.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GenericDialog gd=new GenericDialog("sort tilt images");
                gd.addCheckbox("sort images in ascending order",false);
                gd.showDialog();
                TiltSeries ts = currentDisplay.getTiltSeries();
                ts.sortImages(!gd.getNextBoolean());
            }
        });
        tiltMenu.add(sortTilt);
        MenuItem removeImage = new MenuItem("remove current image");
        removeImage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                YesNoCancelDialog dialog = new YesNoCancelDialog(ts.getWindow(), "remove current image", "merge image alignment (no modification of other images final alignment)? ");

                if (dialog.cancelPressed()) return;
                boolean merge = dialog.yesPressed();
                ts.removeImage(ts.getCurrentSlice() - 1, merge);
                ts.updateZeroIndex();
                ts.setSlice(ts.getCurrentSlice());
                ts.updateAndDraw();
                UserAction ua = new UserAction("remove image", "slice" + ts.getCurrentSlice() + "merge" + merge, "removeImage", false);
                currentDisplay.getLog().add(ua);

            }
        });
        tiltMenu.add(removeImage);


        MenuItem updateStats = new MenuItem("update statistics");
        updateStats.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                ts.threadStats();
                ts.setSlice(ts.getCurrentSlice());
                ts.updateAndDraw();
                //UserAction ua = new UserAction("remove image", "slice" + ts.getCurrentSlice() + "merge" + merge, "removeImage", false);
                //currentDisplay.getLog().add(ua);

            }
        });
        tiltMenu.add(updateStats);

        MenuItem normalizationType = new MenuItem("normalization type");
        normalizationType.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                GenericDialog gd = new GenericDialog("change normalization type");
                String[] choice = new String[]{"none",
                        "Zero - One",
                        "Zero - One (common field)",
                        "Electron Tomo"};
                gd.addRadioButtonGroup("Normalization Type", choice, choice.length, 1, choice[ts.getNormalizationType()]);

                gd.showDialog();
                if (gd.wasCanceled()) return;
                String value = gd.getNextRadioButton();
                if (value.equals(choice[0])) {
                    ts.setNormalize(false);
                    ts.setNormalizationType(0);
                } else if (value.equals(choice[1])) {
                    ts.setNormalize(true);
                    ts.setNormalizationType(TiltSeries.ZERO_ONE);
                } else if (value.equals(choice[2])) {
                    ts.setNormalize(true);
                    ts.setNormalizationType(TiltSeries.ZERO_ONE_COMMONFIELD);
                } else if (value.equals(choice[3])) {
                    ts.setNormalize(true);
                    ts.setNormalizationType(TiltSeries.ELECTRON_TOMO);
                }
                ts.setSlice(ts.getCurrentSlice());
                ts.getProcessor().resetMinAndMax();
                ts.updateAndDraw();
                ts.threadStats();
                Prefs.set("TOMOJ_TiltSeriesNormalizationType.int", ts.getNormalizationType());

            }
        });
        tiltMenu.add(normalizationType);
        MenuItem fill = new MenuItem("Fill blanks");
        fill.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                GenericDialog gd = new GenericDialog("change filling type");
                String[] choice = new String[]{"Zeros",
                        "Average value",
                        "NaN"};
                gd.addRadioButtonGroup("Fill with", choice, choice.length, 1, choice[ts.getFillType()]);

                gd.showDialog();
                if (gd.wasCanceled()) return;
                String value = gd.getNextRadioButton();
                if (value.equals(choice[0])) {
                    ts.setFillType(TiltSeries.FILL_NONE);
                } else if (value.equals(choice[1])) {
                    ts.setFillType(TiltSeries.FILL_AVG);
                } else if (value.equals(choice[2])) {
                    ts.setFillType(TiltSeries.FILL_NaN);
                }
                ts.setSlice(ts.getCurrentSlice());
                ts.getProcessor().resetMinAndMax();
                ts.updateAndDraw();
                ts.threadStats();
                Prefs.set("TOMOJ_TiltSeriesFillBlankType.int", ts.getFillType());
            }
        });
        tiltMenu.add(fill);
        MenuItem setCenter = new MenuItem("set center of projections");
        setCenter.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                GenericDialog gd = new GenericDialog("new center");
                gd.addNumericField("center X", ts.getProjectionCenterX(), 2);
                gd.addNumericField("center Y", ts.getProjectionCenterY(), 2);

                gd.showDialog();
                if (gd.wasCanceled()) return;
                double cx = gd.getNextNumber();
                double cy = gd.getNextNumber();
                System.out.println("change projection center from (" + ts.getProjectionCenterX() + ", " + ts.getProjectionCenterY() + ") to (" + cx + ", " + cy + ")");
                UserAction ua = new UserAction("change projection center", "(" + cx + ", " + cy + ")", "projectionCenterModified", false);
                currentDisplay.getLog().add(ua);
                ts.setProjectionCenter(cx, cy);

            }
        });
        tiltMenu.add(setCenter);

        //transform menu
        Menu transformMenu = new Menu("transform");
        menuBar.add(transformMenu);
        MenuItem saveTFunction = new MenuItem("save...");
        transformMenu.add(saveTFunction);
        saveTFunction.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                String title = ts.getShortTitle();
                boolean finalT = false;
                if ((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
                    title += "_final ";
                    finalT = true;
                }
                //System.out.println("saving as final transforms: "+finalT);
                SaveDialog sd = new SaveDialog(title + "transforms as...", title + "transf.csv", "");
                String dir = sd.getDirectory();
                String name = sd.getFileName();
                if (dir == null || name == null) {
                    return;
                }
                //ts.saveToFile(dir + name);
                CommandWorkflow.saveTransform(dir, name, ts, finalT);
                IJ.showStatus("save finished");
                UserAction ua = new UserAction("save transforms", dir + name + "\nfinal?" + finalT,
                        "saveTransf", false);
                currentDisplay.getLog().add(ua);
            }
        });
        MenuItem loadTFunction = new MenuItem("load...");
        transformMenu.add(loadTFunction);
        loadTFunction.addActionListener(new ActionListener() {
            /** Invoked when an action occurs. */
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                //To change body of implemented methods use File | Settings | File Templates.
                ts.updatePoint();
                OpenDialog od = new OpenDialog("open translation file...", "");
                String dir = od.getDirectory();
                String name = od.getFileName();
                if (dir == null || name == null) {
                    return;
                }
                double binning = 1;
                boolean finaltransforms = false;
                boolean combine = false;
                boolean tiff2mrc = false;
                if ((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
                    GenericDialog gd = new GenericDialog("open options");
                    gd.addNumericField("binning", 1, 2);
                    gd.addCheckbox("final Transform", false);
                    gd.addCheckbox("combine with existing alignment", false);
                    if (name.toLowerCase().endsWith(".xf") || name.toLowerCase().endsWith(".prexf") || name.toLowerCase().endsWith(".prexg"))
                        gd.addCheckbox("mrc created with tiff2mrc", false);
                    gd.showDialog();
                    if (gd.wasCanceled()) return;
                    binning = gd.getNextNumber();
                    finaltransforms = gd.getNextBoolean();
                    combine = gd.getNextBoolean();
                    if (binning == 0) binning = 1;
                    if (binning < 0) binning = -binning;
                    if (name.toLowerCase().endsWith(".xf") || name.toLowerCase().endsWith(".prexf") || name.toLowerCase().endsWith(".prexg"))
                        tiff2mrc = gd.getNextBoolean();
                }
                CommandWorkflow.loadTransforms(dir, name, ts, binning, finaltransforms, combine, tiff2mrc);

                IJ.showStatus("loading finished");
                Roi toto = null;
                ts.setRoi(toto);
                ts.setSlice(ts.getCurrentSlice());
                ts.updateAndDraw();
                //updateDisplayTiltAngles();
                UserAction ua = new UserAction("load transforms",
                        dir + name + "\nfinal?" + finaltransforms + "\nbinning:" + binning + "\ncombine?" + combine,
                        "loadTransf", false);
                currentDisplay.getLog().add(ua);
                //((CommandWorkflow) historyTree).addCommandToHistory(ua, true, true, false, null);
            }
        });
        MenuItem resetTranformFunction = new MenuItem("reset");
        transformMenu.add(resetTranformFunction);
        resetTranformFunction.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                ts.updatePoint();
                Roi toto = null;
                ts.setRoi(toto);
                ts.setAlignment(new AffineAlignment(ts));
                IJ.showStatus("reset of transforms finished");
                ts.combineTransforms(true);
                ts.setSlice(ts.getCurrentSlice());
                ts.updateAndDraw();
                UserAction ua = new UserAction("reset transforms",
                        "",
                        "reset", false);
                currentDisplay.getLog().add(ua);
            }
        });

        Menu landmarksMenu = new Menu("landmarks");
        menuBar.add(landmarksMenu);
        MenuItem saveLFunction = new MenuItem("save...");
        landmarksMenu.add(saveLFunction);
        saveLFunction.addActionListener(new ActionListener() {
            /** Invoked when an action occurs. */
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                ts.updatePoint();
                SaveDialog sd = new SaveDialog("save points as...", ts.getShortTitle() + "_points", ".txt");
                String dir = sd.getDirectory();
                String name = sd.getFileName();
                if (dir == null || name == null) {
                    return;
                }
                CommandWorkflow.saveLandmarks(dir, name, ts);
                //ts.saveToFile(dir + name);
                IJ.showStatus("save finished");
                UserAction ua = new UserAction("save landmarks", dir + name,
                        "saveLandmarks", false);

            }
        });

        MenuItem loadLFunction = new MenuItem("load...");
        landmarksMenu.add(loadLFunction);
        loadLFunction.addActionListener(new ActionListener() {
            /** Invoked when an action occurs. */
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                Roi toto = null;
                ts.setRoi(toto);
                OpenDialog od = new OpenDialog("open Landmarks file...", "");
                String dir = od.getDirectory();
                String name = od.getFileName();
                if (dir == null || name == null) {
                    return;
                }
                double binning = 1;
                int refinementType = 0;
                String[] refChoice = {"none", "local minima", "local maxima"};
                System.out.println("modifiers " + e.getModifiers() + " (ctrl:" + e.CTRL_MASK + ")");
                //if ((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
                GenericDialog gd = new GenericDialog("load landmarks options");
                gd.addNumericField("binning", 1, 2);
                gd.addChoice("refine landmark position", refChoice, refChoice[0]);
                gd.showDialog();
                if (gd.wasCanceled()) return;
                binning = gd.getNextNumber();
                refinementType = gd.getNextChoiceIndex();
                if (binning == 0) binning = 1;
                if (binning < 0) binning = -binning;
                CommandWorkflow.loadLandmarks(dir, name, ts.getTomoJPoints(), binning);
                if (binning > 1 && refinementType > 0) {
                    ts.getTomoJPoints().refineLandmarksToLocalCritical(binning, refinementType == 1);
                }
                //} else {
                //    CommandWorkflow.loadLandmarks(dir, name, ts.getTomoJPoints(), binning);
                //}
                currentDisplay.updatePointSpinner();
                IJ.showStatus("loading finished");
                ts.getTomoJPoints().setCurrentIndex(ts.getTomoJPoints().getNumberOfPoints() - 1);
                ts.setSlice(ts.getCurrentSlice());
                ts.updateAndDraw();
                UserAction ua = new UserAction("load landmarks",
                        dir + name + "\nbinning:" + binning + "\nrefine" + refChoice[refinementType],
                        "loadLandmarks", false);
                currentDisplay.getLog().add(ua);
            }
        });

        MenuItem resetLFunction = new MenuItem("remove landmarks");
        landmarksMenu.add(resetLFunction);
        resetLFunction.addActionListener(new ActionListener() {
            /** Invoked when an action occurs. */
            public void actionPerformed(ActionEvent e) {
                TiltSeries ts = currentDisplay.getTiltSeries();
                ts.getTomoJPoints().removeAllSetsOfPoints();

                currentDisplay.updatePointSpinner();
                IJ.showStatus("loading finished");
                ts.getTomoJPoints().setCurrentIndex(ts.getTomoJPoints().getNumberOfPoints() - 1);
                ts.setSlice(ts.getCurrentSlice());
                ts.updateAndDraw();
                UserAction ua = new UserAction("reset landmarks",
                        "",
                        null, false);
                currentDisplay.getLog().add(ua);
            }
        });

        Menu logMenu = new Menu("log");
        menuBar.add(logMenu);
        MenuItem saveLogFunction = new MenuItem("save...");
        logMenu.add(saveLogFunction);
        saveLogFunction.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                TiltSeries ts = currentDisplay.getTiltSeries();
                Date theDate = new Date();
                String d = "" + (new SimpleDateFormat("yyMMdd_HHmmss").format(theDate));
                SaveDialog sd = new SaveDialog("save log as...", ts.getShortTitle() + "_" + d + "_log", ".txt");
                String dir = sd.getDirectory();
                String name = sd.getFileName();
                if (dir == null || name == null) {
                    return;
                }
                if (!dir.endsWith(System.getProperty("file.separator")))
                    dir += System.getProperty("file.separator");
                try {
                    BufferedWriter out = new BufferedWriter(new FileWriter(dir + name));
                    for (UserAction ua : currentDisplay.getLog()) {
                        out.write(ua.toStringComplete() + "\n");
                    }
                    out.flush();
                    out.close();
                } catch (Exception ioe) {
                    ioe.printStackTrace();
                }

            }
        });
        Menu helpMenu = new Menu("help");
        menuBar.add(helpMenu);

        MenuItem aboutMenu = new MenuItem("about");
        helpMenu.add(aboutMenu);
        aboutMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                GenericDialog gd = new GenericDialog("TomoJ multiple axes");
                //try {
                ImageIcon icon = new ImageIcon(getClass().getResource("/TomoJ_color_small75.png"));
                JButton button = new JButton("", icon);
                button.setBackground(Color.BLACK);
                button.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        final String url = "http://www.cmib.fr/en/download/softwares/TomoJ.html";
                        try {
                            BrowserLauncher.openURL(url);
                        } catch (IOException ioe) {
                            IJ.log("cannot open the url " + url + "\n" + ioe);
                        }
                    }
                });
                Panel panel = new Panel();
                panel.add(button, GridBagConstraints.CENTER, 0);
                gd.addPanel(panel);
                //gd.addImage(new ImagePlus(getClass().getResource("/TomoJ_color_small75.png").getPath()));
                //}catch (Exception ee){ee.printStackTrace();}
                gd.addMessage("TomoJ version 2.6");
                gd.hideCancelButton();
                gd.showDialog();

            }
        });
        window.setMenuBar(menuBar);
        window.pack();
    }

    public static TiltSeries convertToTiltSeries(ImagePlus imp) {
        if (imp.getType() != ImagePlus.GRAY32) new StackConverter(imp).convertToGray32();
        double[] tiltangles = AnglesForTiltSeries.getAngles(imp);
        if (tiltangles == null) {
            return null;
        }
        System.out.println("get angles done");
        TiltSeries ts = new TiltSeries(imp, tiltangles);
        ts.sortImages(true);
        System.out.println("create tilt series done");
        TomoJPoints tp = new TomoJPoints(ts);
        System.out.println("create points done");
        tp.setVisible(true);
        ts.setTomoJPoints(tp);
        Roi emptyRoi = null;
        ts.setRoi(emptyRoi);


        ts.setNormalizationType((int) Prefs.get("TOMOJ_TiltSeriesNormalizationType.int", 1));
        ts.setFillType((int) Prefs.get("TOMOJ_TiltSeriesFillBlankType.int", 0));
        ts.setSlice(imp.getSlice());
        ts.resetDisplayRange();
        ts.updateAndDraw();
        return ts;
    }

    /**
     * exports the aligned image to a file
     *
     * @param ts       the tilt series to export
     * @param dir      directory where to save the images
     * @param filename name of the file <BR>
     *                 <UL>
     *                 <LI>filename finish with mrc: the file is saved in MRC format </LI>
     *                 <LI>filename finish with spi: the file is saved in Spider format </LI>
     *                 <LI>filename finish with xmp: the file is saved in spider format </LI>
     *                 <LI>filename finish with sel: the file is saved in multiple files in spider format and a sel file is created (text file with the list of images files)</LI>
     *                 <LI>filename finish with anything else: the file is saved in tif format </LI>
     *                 </UL>
     */
    public static void exportAlignedImages(TiltSeries ts, String dir, String filename) {
        exportAlignedImages(ts, dir + filename);
    }

    public static void exportAlignedImages(TiltSeries ts, String path) {
        String nameLC = path.toLowerCase();
        System.out.println("save as " + nameLC);
        if (nameLC.endsWith(".sel")) {
            IJ.runPlugIn(ts, "Sel_Writer", path);
        } else {
            ImageStack is = new ImageStack(ts.getWidth(), ts.getHeight());
            for (int i = 0; i < ts.getStackSize(); i++) {
                is.addSlice("" + ts.getTiltAngle(i), ts.getPixels(i));
                IJ.showStatus("creating aligned stack " + (i + 1) + "/" + ts.getStackSize());
            }
            ImagePlus imp = new ImagePlus(path, is);

            if (nameLC.endsWith(".mrc")) {
                IJ.runPlugIn(imp, "MRC_Writer", path);
            } else if (nameLC.endsWith(".xmp") || nameLC.endsWith(".spi")) {
                IJ.runPlugIn(imp, "Spider_Writer", path);
            } else {
                //imp.show();
                //IJ.selectWindow(path);
                IJ.save(imp, path);
                //imp.close();
            }
        }
    }


    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        panelroot = new JPanel();
        panelroot.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelroot.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "multiple-axis align and reconstruction", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panelroot.add(panel1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        runButton = new JButton();
        runButton.setText("Run");
        panel1.add(runButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        applicationPanel = new JPanel();
        applicationPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panelroot.add(applicationPanel, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelroot.add(panel2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        alignZeroTiltImagesButton = new JButton();
        alignZeroTiltImagesButton.setText("align zero tilt images");
        panel2.add(alignZeroTiltImagesButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel2.add(spacer1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        combineLandmarksButton = new JButton();
        combineLandmarksButton.setText("combine landmarks");
        panel2.add(combineLandmarksButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showOnlyCommonLandmarksCheckBox = new JCheckBox();
        showOnlyCommonLandmarksCheckBox.setText("show Only Common Landmarks");
        panel2.add(showOnlyCommonLandmarksCheckBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        alignAllButton = new JButton();
        alignAllButton.setText("align all");
        panel2.add(alignAllButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        reconstructAllButton = new JButton();
        reconstructAllButton.setText("reconstruct all");
        panel2.add(reconstructAllButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        manualAlignmentButton = new JButton();
        manualAlignmentButton.setText("manual alignment");
        panel2.add(manualAlignmentButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panelroot;
    }

    class CustomStackWindowDual extends StackWindow {
        Panel panel;
        int currentTiltSeries = 0;
        JPanel imgPanel;
        JPanel imgPanel2;
        ArrayList<TiltSeriesPanel> tsList;
        ImageCanvas bkpCanvas;
        ScrollbarWithLabel bkpScrollbar;

        CustomStackWindowDual(ArrayList<TiltSeriesPanel> tsList) {
            super(tsList.get(0).getTiltSeries(), tsList.get(0).getTiltSeries().getCanvas());
            this.tsList = tsList;
            //panel = new Panel();
            setLayout(new FlowLayout());
            remove(zSelector);
            remove(ic);
            bkpCanvas = ic;
            bkpScrollbar = zSelector;
//
//            imgPanel = new JPanel();
//            imgPanel.setLayout(new BoxLayout(imgPanel, BoxLayout.Y_AXIS));
//            imgPanel.add(tsList.get(0).getTiltSeries().getCanvas());
//            imgPanel.add(zSelector);
//            //imgPanel = tsList.get(currentTiltSeries).getImagePanel();
//            imgPanel2 = new JPanel();
//            imgPanel2.setLayout(new BoxLayout(imgPanel2, BoxLayout.Y_AXIS));
//            imgPanel2.add(tsList.get(1).getTiltSeries().getCanvas());
//            imgPanel2.add(tsList.get(1).getScrollBar());
//            add(imgPanel);
//            add(imgPanel2);
//            addPanel();
            setCurrentTiltSeries(0);
            this.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    Dimension dic = ic.getPreferredSize();
                    Dimension dp = tsList.get(0).getProcessingPanel().getPreferredSize();
                    panel.setPreferredSize(new Dimension((int) dp.getWidth(), (int) dic.getHeight()));
                    pack();
                }
            });
            WindowListener[] list = tsList.get(0).getTiltSeries().getWindow().getWindowListeners();
            for (WindowListener wl : list) this.addWindowListener(wl);
            this.addKeyListener(IJ.getInstance());
        }

        public void updateGUI() {
            if (tabbedPane1.getTabCount() < tsList.size() + ((tsList.size() < 2) ? 1 : 2)) {
                tabbedPane1.removeChangeListener(tabbedPane1.getChangeListeners()[0]);
                tabbedPane1.removeAll();
                for (int i = 0; i < tsList.size(); i++) {
                    tabbedPane1.addTab("TS " + (i + 1), tsList.get(i).getProcessingPanel());
                }
                if (tsList.size() > 1) tabbedPane1.addTab("Combine", panelroot);
                tabbedPane1.addTab("new", new JPanel());
                Dimension dic = ic.getPreferredSize();
                Dimension dp = tsList.get(0).getProcessingPanel().getPreferredSize();
                panel.setPreferredSize(new Dimension((int) dp.getWidth(), (int) dic.getHeight()));
                pack();
                tabbedPane1.addChangeListener(new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        window.setCurrentTiltSeries(tabbedPane1.getSelectedIndex());
                        if (tabbedPane1.getSelectedIndex() == tabbedPane1.getTabCount() - ((tsList.size() < 2) ? 1 : 2))
                            tsList.get(currentTiltSeries).getTiltSeries().setSlice(tsList.get(currentTiltSeries).getTiltSeries().getZeroIndex() + 1);
                        if (tabbedPane1.getSelectedIndex() == tabbedPane1.getTabCount() - 1) {
                            currentDisplay.getCurrentApplication().setDisplayPreview(false);
                            ImagePlus imp = IJ.openImage();
                            TiltSeries ts = convertToTiltSeries(imp);
                            TiltSeriesPanel tsp = new TiltSeriesPanel(ts);
                            tsList.add(tsp);
                            window.updateGUI();
                            window.selectTab(tsList.size() - 1);
                            window.setCurrentTiltSeries(tsList.size() - 1);
                            imp.hide();
                        }
                    }
                });
            }
        }

        public void selectTab(int index) {
            if (index < tabbedPane1.getTabCount())
                tabbedPane1.setSelectedIndex(index);

        }

        void addPanel() {
            panel = new Panel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            tabbedPane1 = new JTabbedPane(JTabbedPane.TOP);
            //tabbedPane1.setBackground(Color.ORANGE);
            panel.add(tabbedPane1);
            //panel.add(zSelector);
            add(panel);
            for (int i = 0; i < tsList.size(); i++) {
                tabbedPane1.addTab("TS " + (i + 1), tsList.get(i).getProcessingPanel());
            }
            if (tsList.size() > 1) tabbedPane1.addTab("Combine", panelroot);
            tabbedPane1.addTab("new", new JPanel());
            Dimension dic = ic.getPreferredSize();
            Dimension dp = tsList.get(0).getProcessingPanel().getPreferredSize();
            panel.setPreferredSize(new Dimension((int) dp.getWidth(), (int) dic.getHeight()));
            pack();


            tabbedPane1.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    window.setCurrentTiltSeries(tabbedPane1.getSelectedIndex());
                    if (tabbedPane1.getSelectedIndex() == tabbedPane1.getTabCount() - 1) {
                        currentDisplay.getCurrentApplication().setDisplayPreview(false);
                        ImagePlus imp = IJ.openImage();
                        TiltSeries ts = convertToTiltSeries(imp);
                        TiltSeriesPanel tsp = new TiltSeriesPanel(ts);
                        tsList.add(tsp);
                        window.updateGUI();
                        window.selectTab(tsList.size() - 1);
                        window.setCurrentTiltSeries(tsList.size() - 1);
                    }
                }
            });
        }


        public int getImageCanvasHeight() {
            return ic.getHeight();
        }

        public void setCurrentTiltSeries(int index) {
            if (index < tsList.size()) {
                double magnification = ic.getMagnification();
                Rectangle displayArea = ic.getSrcRect();
                Dimension currentCanvasDimension = ic.getPreferredSize();
                this.currentTiltSeries = index;
                //imgPanel = tsList.get(currentTiltSeries).getImagePanel();
                remove(zSelector);
                remove(ic);
                removeAll();
                if (index == 0) {
                    ic = bkpCanvas;
                    zSelector = bkpScrollbar;
                    animationSelector = zSelector;
                    setImage(tsList.get(0).getTiltSeries());
                } else {
                    ic = tsList.get(currentTiltSeries).getTiltSeries().getCanvas();
                    zSelector = tsList.get(currentTiltSeries).getScrollBar();
                    animationSelector = zSelector;
                    setImage(tsList.get(currentTiltSeries).getTiltSeries());
                }
                ic.setSourceRect(displayArea);
                ic.setMagnification(magnification);
                ic.setSize(currentCanvasDimension);
                //ic.hideZoomIndicator(false);
                //repaint();
                JPanel tmp = new JPanel();
                tmp.setLayout(new BoxLayout(tmp, BoxLayout.Y_AXIS));
                tmp.add(ic);
                tmp.add(zSelector);
                add(tmp);
                if (panel != null) add(panel);
                else addPanel();
                panel.setPreferredSize(new Dimension((int) panel.getPreferredSize().getWidth(), (int) currentCanvasDimension.getHeight()));
                currentDisplay = tsList.get(index);

                pack();
            }

        }

        @Override
        public void windowClosed(WindowEvent e) {
            super.windowClosed(e);
            System.out.println("window closed");
            for (TiltSeriesPanel ts : tsList) {
                ts.getTiltSeries().close();
            }
        }

        @Override
        public void windowClosing(WindowEvent e) {
            super.windowClosing(e);
            System.out.println("window closing");
            for (TiltSeriesPanel ts : tsList) {
                ts.getTiltSeries().getWindow().close();
            }

        }
    } // CustomStackWindow inner class

}
