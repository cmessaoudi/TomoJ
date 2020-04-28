package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PointRoi;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.landmarks.LandmarksChain;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.landmarks.ChainsGenerator;
import fr.curie.tomoj.landmarks.SeedDetector;
import fr.curie.tomoj.application.Application;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 29/09/2016.
 */
public class CriticalLandmarksGenerator implements Application {
    //protected LandmarksGenerator generator;
    protected ChainsGenerator chainsGenerator;
    double completionSeed = -1000;
    double seedoffset = 0;
    SeedDetector seedDetector;
    protected TiltSeries ts;
    protected TomoJPoints tp;
    int nbSeeds = 200;
    int nbChainsKept = 100;
    int chainLength;
    int patchSize;
    boolean localMinima = true;
    double correlationThreshold = 0.5;
    int extremaNeighborhoodRadius = 8;
    int filterLarge;
    int filterSmall;
    double percentageToExcludeX = 0.1;
    double percentageToExcludeY = 0.1;
    boolean fiducialMarkers = false;
    boolean firstDisplay = true;

    boolean fuseLandmarks = true;

    boolean displayPreview = false;
    double thresholdFitGoldBead = 0.5;

    private ImagePlus previewCritical = null;
    private PreviewCriticalThread workingPreviewThread;
    private int previewIndex;
    String resultString;

    private JPanel basePanel;
    private JRadioButton localMinimaRadioButton;
    private JRadioButton localMaximaRadioButton;
    private JCheckBox expertModeCheckBox;
    private JCheckBox fiducialMarkersCheckBox;
    private JSpinner spinnerNbSeeds;
    private JSpinner spinnerNbChainsKept;
    private JSpinner spinnerChainLength;
    private JSpinner spinnerPatchSize;
    private JSpinner spinnerCorrelationThreshold;
    private JSpinner spinnerExtremaRadius;
    private JSpinner spinnerPercentageToExcludeX;
    private JSpinner spinnerPercentageToExcludeY;
    private JSpinner spinnerFilterSmall;
    private JSpinner spinnerFilterLarge;
    private JSpinner spinnerThresholdFitGoldBead;

    public CriticalLandmarksGenerator(TiltSeries ts, TomoJPoints tp) {
        this.ts = ts;
        this.tp = tp;
        //generator=new LandmarksGenerator(tp,ts);

        seedDetector = new SeedDetector(ts);
        chainsGenerator = new ChainsGenerator(ts);
        filterSmall = 2;
        filterLarge = 125;
        $$$setupUI$$$();
        chainLength = Math.max(ts.getImageStackSize() / 4, 3);
        patchSize = (int) (10 * ts.getWidth() / 256.0 + 1);
    }

    private void addListeners() {
        spinnerNbSeeds.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                nbSeeds = ((SpinnerNumberModel) spinnerNbSeeds.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerNbChainsKept.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                nbChainsKept = ((SpinnerNumberModel) spinnerNbChainsKept.getModel()).getNumber().intValue();
            }
        });
        spinnerChainLength.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                chainLength = ((SpinnerNumberModel) spinnerChainLength.getModel()).getNumber().intValue();
            }
        });
        spinnerPatchSize.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                patchSize = ((SpinnerNumberModel) spinnerPatchSize.getModel()).getNumber().intValue();
            }
        });
        localMinimaRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                localMinima = localMinimaRadioButton.isSelected();
                updatePreview();
                System.out.println("use local minima : " + localMinima);
            }
        });

        localMaximaRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                localMinima = localMinimaRadioButton.isSelected();
                updatePreview();
                System.out.println("use local minima : " + localMinima);
            }
        });
        fiducialMarkersCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fiducialMarkers = fiducialMarkersCheckBox.isSelected();
                spinnerThresholdFitGoldBead.setEnabled(fiducialMarkers);
                if (fiducialMarkersCheckBox.isSelected())
                    ((SpinnerNumberModel) spinnerCorrelationThreshold.getModel()).setValue(correlationThreshold / 2.0);
                else ((SpinnerNumberModel) spinnerCorrelationThreshold.getModel()).setValue(correlationThreshold * 2.0);
            }
        });
        spinnerThresholdFitGoldBead.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                thresholdFitGoldBead = ((SpinnerNumberModel) spinnerThresholdFitGoldBead.getModel()).getNumber().doubleValue();
            }
        });
        spinnerCorrelationThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                correlationThreshold = ((SpinnerNumberModel) spinnerCorrelationThreshold.getModel()).getNumber().doubleValue();
            }
        });
        spinnerExtremaRadius.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                extremaNeighborhoodRadius = ((SpinnerNumberModel) spinnerExtremaRadius.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerPercentageToExcludeX.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                percentageToExcludeX = ((SpinnerNumberModel) spinnerPercentageToExcludeX.getModel()).getNumber().doubleValue();
                updatePreview();
            }
        });
        spinnerPercentageToExcludeY.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                percentageToExcludeY = ((SpinnerNumberModel) spinnerPercentageToExcludeY.getModel()).getNumber().doubleValue();
                updatePreview();
            }
        });
        spinnerFilterSmall.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                filterSmall = ((SpinnerNumberModel) spinnerFilterSmall.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerFilterLarge.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                filterLarge = ((SpinnerNumberModel) spinnerFilterLarge.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        expertModeCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean isSelected = expertModeCheckBox.isSelected();
                spinnerFilterLarge.setEnabled(isSelected);
                spinnerFilterSmall.setEnabled(isSelected);
                spinnerPercentageToExcludeX.setEnabled(isSelected);
                spinnerPercentageToExcludeY.setEnabled(isSelected);
                spinnerCorrelationThreshold.setEnabled(isSelected);
            }
        });
        basePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                super.componentShown(e);
                updatePreview();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                super.componentHidden(e);
                if (previewCritical != null) {
                    previewCritical.close();
                    previewCritical = null;
                }
            }
        });

        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex -= 10;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex += 10;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex = (previewIndex <= ts.getZeroIndex()) ? 0 : ts.getZeroIndex();
                //previewIndex -= ts.getImageStackSize()/2;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex = (previewIndex >= ts.getZeroIndex()) ? ts.getImageStackSize() - 1 : ts.getZeroIndex();
                //previewIndex += ts.getImageStackSize()/2;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


    }

    private void initValues() {
        ((SpinnerNumberModel) spinnerFilterSmall.getModel()).setMinimum(0);
        spinnerFilterSmall.setValue(filterSmall);
        ((SpinnerNumberModel) spinnerFilterLarge.getModel()).setMinimum(0);
        spinnerFilterLarge.setValue(filterLarge);

        ((SpinnerNumberModel) spinnerNbSeeds.getModel()).setMinimum(0);
        ((SpinnerNumberModel) spinnerNbSeeds.getModel()).setValue(nbSeeds);
        ((SpinnerNumberModel) spinnerNbSeeds.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerNbChainsKept.getModel()).setMinimum(0);
        ((SpinnerNumberModel) spinnerNbChainsKept.getModel()).setValue(nbChainsKept);
        ((SpinnerNumberModel) spinnerNbChainsKept.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerChainLength.getModel()).setMinimum(3);
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setMaximum(ts.getImageStackSize());
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setValue(chainLength);
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerPatchSize.getModel()).setMinimum(3);
        ((SpinnerNumberModel) spinnerPatchSize.getModel()).setMaximum(ts.getWidth());
        ((SpinnerNumberModel) spinnerPatchSize.getModel()).setValue(patchSize);
        ((SpinnerNumberModel) spinnerPatchSize.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerExtremaRadius.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerExtremaRadius.getModel()).setMaximum(ts.getWidth());
        ((SpinnerNumberModel) spinnerExtremaRadius.getModel()).setValue(extremaNeighborhoodRadius);
        ((SpinnerNumberModel) spinnerExtremaRadius.getModel()).setStepSize(1);
    }

    public boolean run() {
        if (previewCritical != null) {
            previewCritical.close();
            previewCritical = null;
        }
        //System.out.println(getParametersValuesAsString());
        OutputStreamCapturer capture = new OutputStreamCapturer();
        Chrono time = new Chrono();
        time.start();
//        generator.setGoldBead(fiducialMarkers);
//        generator.setCriticalSeed(nbSeeds);
//        generator.setCriticalFilter(filterLarge, filterSmall);
//        generator.setCriticalMinimaRadius(extremaNeighborhoodRadius);
//        generator.setPercentageExcluded(percentageToExcludeX, percentageToExcludeY);

        //generator.generateLandmarkSetWithBackAndForthValidation(chainLength, nbChainsKept, patchSize,  correlationThreshold, localMinima, true, true);


//        seedDetector=new SeedDetector(ts);
//        chainsGenerator=new ChainsGenerator(ts);
        chainsGenerator.update();
        completionSeed = 0;
        final ArrayList<LandmarksChain> seeds = new ArrayList<LandmarksChain>();
        //= seedDetector.createsSeedsLocalExtrema(localMinima, percentageToExcludeX, percentageToExcludeY, filterSmall, filterLarge, extremaNeighborhoodRadius, nbSeeds);
        if (fiducialMarkers) {
            seedoffset = 50;
            //System.out.println("do bead selection \nbefore: " + seeds.size() + " seeds");
            //double sigma = seedDetector.isGoldBeadPresent(seeds, ts.getZeroIndex(), patchSize, localMinima);
            final double[] sigma = new double[]{0.0};
            ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
            Future[] futures = new Future[ts.getImageStackSize()];
            for (int i = 0; i < ts.getImageStackSize(); i++) {
                final int ii = i;
                futures[i] = exec.submit(new Thread() {
                    public void run() {

                        ArrayList<LandmarksChain> seedstmp = seedDetector.createsSeedsLocalExtrema(ii, localMinima, percentageToExcludeX, percentageToExcludeY, filterSmall, filterLarge, extremaNeighborhoodRadius, nbSeeds);
                        int nbBefore = seedstmp.size();
                        //double s = seedDetector.isGoldBeadPresent(seedstmp, ii, patchSize, localMinima);


                        double s = seedDetector.isGoldBeadPresent(seedstmp, ii, patchSize, localMinima, thresholdFitGoldBead);
                        System.out.println("#" + ii + " do bead selection : " + nbBefore + " --> " + seedstmp.size());

                        completionSeed += 1.0 / ts.getImageStackSize() * seedoffset;
                        if (ii == ts.getZeroIndex()) sigma[0] = s;
                        seeds.addAll(seedstmp);
                    }
                });
            }
            for (Future f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("after: " + seeds.size() + " seeds");
            chainsGenerator.createGaussianImage(patchSize, sigma[0], localMinima);
            //chainsGenerator.createDiscusImage(patchSize, sigma[0], localMinima);
        } else {
            ArrayList<LandmarksChain> seedstmp = seedDetector.createsSeedsLocalExtrema(localMinima, percentageToExcludeX, percentageToExcludeY, filterSmall, filterLarge, extremaNeighborhoodRadius, nbSeeds);
            seeds.addAll(seedstmp);
            seedoffset = 1;
        }
        completionSeed = -1000;
        /*double cx = ts.getCenterX();
        double cy = ts.getCenterY();
        for(LandmarksChain l:seeds){
            Point2D.Double[] centeredchain = l.getLandmarkchain();
            Point2D.Double[] finalChain = new Point2D.Double[centeredchain.length];
            for (int p = 0; p < finalChain.length; p++) {
                Point2D.Double pt = centeredchain[p];
                if (pt != null) {
                    finalChain[p] = new Point2D.Double(pt.getX() + cx, pt.getY() + cy);
                }
            }
            tp.addSetOfPoints(finalChain,true);
        } */
        chainsGenerator.generateLandmarkSetWithBackAndForthValidation(seeds, fiducialMarkers, chainLength, true, patchSize, correlationThreshold, fuseLandmarks, nbChainsKept);
//        if(fiducialMarkers){
//            System.out.println("final refinement of positions");
//            chainsGenerator.refineLandmarksPositionGaussian(tp.getAllLandmarks(),ts,patchSize,localMinima);
//            System.out.println("final refinement of positions finished");
//        }
        /**/
        time.stop();
        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        tp.showAll(true);

        return true;
    }

    public void setParameters(Object... parameters) {
        for (int index = 0; index < parameters.length; index++) {
            if (((String) parameters[index]).toLowerCase().equals("localminima")) {
                localMinima = true;
            } else if (((String) parameters[index]).toLowerCase().equals("localmaxima")) {
                localMinima = false;
            } else if (((String) parameters[index]).toLowerCase().equals("exclude")) {
                if (parameters[index + 1] instanceof String)
                    percentageToExcludeX = Double.parseDouble((String) parameters[index + 1]);
                else percentageToExcludeX = (Double) parameters[index + 1];
                if (parameters[index + 2] instanceof String)
                    percentageToExcludeY = Double.parseDouble((String) parameters[index + 2]);
                else percentageToExcludeY = (Double) parameters[index + 2];
                index += 2;

            } else if (((String) parameters[index]).toLowerCase().equals("filter")) {
                if (parameters[index + 1] instanceof String)
                    filterSmall = Integer.parseInt((String) parameters[index + 1]);
                else filterSmall = (Integer) parameters[index + 1];
                if (parameters[index + 2] instanceof String)
                    filterLarge = Integer.parseInt((String) parameters[index + 2]);
                else filterLarge = (Integer) parameters[index + 2];
                index += 2;

            } else if (((String) parameters[index]).toLowerCase().equals("extremaradius")) {
                if (parameters[index + 1] instanceof String)
                    extremaNeighborhoodRadius = Integer.parseInt((String) parameters[index + 1]);
                else extremaNeighborhoodRadius = (Integer) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("nbseeds")) {
                if (parameters[index + 1] instanceof String)
                    nbSeeds = Integer.parseInt((String) parameters[index + 1]);
                else nbSeeds = (Integer) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("chainlength")) {
                if (parameters[index + 1] instanceof String)
                    chainLength = Integer.parseInt((String) parameters[index + 1]);
                else chainLength = (Integer) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("patchsize")) {
                if (parameters[index + 1] instanceof String)
                    patchSize = Integer.parseInt((String) parameters[index + 1]);
                else patchSize = (Integer) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("mincorrelationthreshold")) {
                if (parameters[index + 1] instanceof String)
                    correlationThreshold = Double.parseDouble((String) parameters[index + 1]);
                else correlationThreshold = (Double) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("fuselandmarks")) {
                fuseLandmarks = true;
            } else if (((String) parameters[index]).toLowerCase().equals("nbchainskept")) {
                if (parameters[index + 1] instanceof String)
                    nbChainsKept = Integer.parseInt((String) parameters[index + 1]);
                else nbChainsKept = (Integer) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("fiducials")) {
                fiducialMarkers = true;
            }
        }
    }

    public String name() {
        return "Landmarks generation using local extrema as seeds";
    }

    public String help() {
        return "generates landmarks using local extrema as seeds\n" +
                "parameters that can be given\n" +
                "fiducialmarkers : will use specific selection of seeds prior to tracking and symmetrization while tracking to optimize tracking of spherical features\n" +
                "localminima : will use local minima as seeds. NOT COMPATIBLE with option localmaxima\n" +
                "localmaxima : will use local maxima as seeds. NOT COMPATIBLE with option localminima\n" +
                "exclude percentageValueXaxis percentageValueYaxis : exclude part of images from local extrema computation.\n" +
                "filter min max : the detection of local extrema is using a bandpass filter. The 2 values (double) correspond to the radius of the band in pixels [minimum maximum].\n" +
                "variancefilter radius : apply a variance filter on images with the given radius (integer). It results in contours images\n" +
                "extremaradius radiusvalue : the detection of extema is done inside a radius given in pixels.\n" +
                "nbseeds value : keep the best extrema until given value number of seeds is obtained.\n" +
                "chainlength value: final length of chains. it corresponds to the number of images on which feature is visible.\n" +
                "patchsize value : the tracking is done using cross-correlation on local patches. this sets the size of patches in pixels.\n" +
                "mincorrelationthreshold value : the tracking is done using cross-correlation on local patches. this sets the threshold bellow which landmarks chains are automatically rejected.\n" +
                "fuselandmarks : try to fuse landmark chains with common coordinates on at least three images.\n" +
                "nbchainskept value : keep the chains with the best tracking score until given value number of chains is obtained (on each image before fusion).\n" +
                "";
    }

    public ArrayList<Object> getResults() {
        ArrayList<Object> result = new ArrayList<Object>();
        result.add(resultString);
        return result;
    }

    public ArrayList<Object> getParametersType() {
        return null;
    }

    public ArrayList<String> getParametersName() {
        return null;
    }

    public JPanel getJPanel() {
        if (firstDisplay) {
            initValues();
            addListeners();
            //updatePreview();
            firstDisplay = false;
        }
        updatePreview();
        return this.basePanel;
    }

    public void interrupt() {
        chainsGenerator.interrupt();
        seedDetector.interrupt();
    }

    public double getCompletion() {
        if (completionSeed >= 0) return completionSeed;
        return seedoffset + chainsGenerator.getCompletion() / 100.0 * (100.0 - seedoffset);
    }

    protected void updatePreview() {
        if (!displayPreview) return;
        if (previewCritical == null) previewIndex = ts.getSlice() - 1;
        if (previewIndex < 0) previewIndex = 0;
        if (previewIndex >= ts.getImageStackSize()) previewIndex = ts.getImageStackSize() - 1;
        if (workingPreviewThread != null) workingPreviewThread.interrupt();
        workingPreviewThread = new PreviewCriticalThread(previewIndex);
        workingPreviewThread.start();

        //previewCritical.show();
        //previewCritical.getWindow().toFront();

    }

    public void setDisplayPreview(boolean display) {
        displayPreview = display;
        if (!displayPreview && previewCritical != null && previewCritical.isVisible()) {
            previewCritical.close();
            previewCritical = null;
        }
    }

    public String getParametersValuesAsString() {
        String params = "local extrema landmarks generation:\n" +
                "local " + ((localMinima) ? "minima " : "maxima ") + "\tneighborhood radius: " + extremaNeighborhoodRadius + "\n" +
                "number of seeds: " + nbSeeds + "\tnumber of chains kept: " + nbChainsKept + "\n" +
                "length of tracked chains: " + chainLength + ((fiducialMarkers) ? "fiducial markers" : "") + "\n" +
                "patch size: " + patchSize + "\tcorrelation threshold: " + correlationThreshold + "\n" +
                "filter size (small: " + filterSmall + ", large: " + filterLarge + ")\n" +
                "excluded part of image (percentage): X: " + percentageToExcludeX + " , Y: " + percentageToExcludeY + "\n";


        return params;
    }


    private void createUIComponents() {
        spinnerCorrelationThreshold = new JSpinner(new SpinnerNumberModel(correlationThreshold, 0.001, 1, 0.01));
        spinnerPercentageToExcludeX = new JSpinner(new SpinnerNumberModel(percentageToExcludeX, 0.001, 1, 0.01));
        spinnerPercentageToExcludeY = new JSpinner(new SpinnerNumberModel(percentageToExcludeY, 0.001, 1, 0.01));
        spinnerThresholdFitGoldBead = new JSpinner(new SpinnerNumberModel(thresholdFitGoldBead, 0.001, 1, 0.01));
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        basePanel = new JPanel();
        basePanel.setLayout(new GridLayoutManager(10, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Landmarks Generation Using Local Extrema As Seeds"));
        localMinimaRadioButton = new JRadioButton();
        localMinimaRadioButton.setSelected(true);
        localMinimaRadioButton.setText("local minima");
        basePanel.add(localMinimaRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        basePanel.add(spacer1, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        localMaximaRadioButton = new JRadioButton();
        localMaximaRadioButton.setText("local maxima");
        basePanel.add(localMaximaRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Number of seeds");
        basePanel.add(label1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Number of (best) chains starting on each image kept");
        basePanel.add(label2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Length of landmarks chain");
        basePanel.add(label3, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Patch size (in pixels)");
        basePanel.add(label4, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Correlation Threshold");
        basePanel.add(label5, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        expertModeCheckBox = new JCheckBox();
        expertModeCheckBox.setText("Expert mode");
        basePanel.add(expertModeCheckBox, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fiducialMarkersCheckBox = new JCheckBox();
        fiducialMarkersCheckBox.setText("Fiducial markers");
        basePanel.add(fiducialMarkersCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panel1, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder("Local extrema filter parameters"));
        final JLabel label6 = new JLabel();
        label6.setText("Filter small structures");
        panel1.add(label6, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(232, 16), null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("Filter large structures");
        panel1.add(label7, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(232, 16), null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("Extrema neighborhood radius");
        panel1.add(label8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(232, 16), null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("Percentage of image to exclude on X");
        panel1.add(label9, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(232, 16), null, 0, false));
        final JLabel label10 = new JLabel();
        label10.setText("Percentage of image to exclude on Y");
        panel1.add(label10, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(232, 16), null, 0, false));
        spinnerExtremaRadius = new JSpinner();
        panel1.add(spinnerExtremaRadius, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerPercentageToExcludeX.setEnabled(false);
        panel1.add(spinnerPercentageToExcludeX, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerPercentageToExcludeY.setEnabled(false);
        panel1.add(spinnerPercentageToExcludeY, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerFilterSmall = new JSpinner();
        spinnerFilterSmall.setEnabled(false);
        panel1.add(spinnerFilterSmall, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerFilterLarge = new JSpinner();
        spinnerFilterLarge.setEnabled(false);
        panel1.add(spinnerFilterLarge, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerNbSeeds = new JSpinner();
        basePanel.add(spinnerNbSeeds, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerNbChainsKept = new JSpinner();
        basePanel.add(spinnerNbChainsKept, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerChainLength = new JSpinner();
        basePanel.add(spinnerChainLength, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerPatchSize = new JSpinner();
        basePanel.add(spinnerPatchSize, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerCorrelationThreshold.setEnabled(false);
        basePanel.add(spinnerCorrelationThreshold, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerThresholdFitGoldBead.setEnabled(false);
        basePanel.add(spinnerThresholdFitGoldBead, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(localMinimaRadioButton);
        buttonGroup.add(localMaximaRadioButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

    class PreviewCriticalThread extends Thread {
        int previewIndex;

        public PreviewCriticalThread(int index) {
            this.previewIndex = index;
        }

        public void run() {
            synchronized (ts) {
                ts.setSlice(previewIndex + 1);
            }
            seedDetector.resetCompletion();
            ImageProcessor ip = new FloatProcessor(ts.getWidth(), ts.getHeight(), ts.getOriginalPixels(previewIndex), null);
//            generator.setGoldBead(fiducialMarkers);
//            generator.setCriticalSeed(nbSeeds);
//            generator.setCriticalFilter(filterLarge, filterSmall);
//            generator.setCriticalMinimaRadius(extremaNeighborhoodRadius);
//            generator.setPercentageExcluded(percentageToExcludeX, percentageToExcludeY);
            String title = (localMinimaRadioButton.isSelected()) ? "local minima : " : "local maxima : ";
            if (previewCritical == null) {
                ImagePlus previewCritical2 = new ImagePlus(title + "seed detected (" + ts.getTiltAngle(previewIndex) + ")", ip);
                System.out.println("create preview critical");
                IJ.showStatus("looking for seeds...");
                seedDetector.resetCompletion();
                seedDetector.getFilteredImageMSD(previewCritical2, localMinima, true, percentageToExcludeX, percentageToExcludeY, filterSmall, filterLarge, extremaNeighborhoodRadius, nbSeeds);
                previewCritical2.setTitle(title + ((PointRoi) previewCritical2.getRoi()).getNCoordinates() + " seed detected(" + ts.getTiltAngle(previewIndex) + ")");
                previewCritical2.updateAndDraw();
                previewCritical = previewCritical2;
               /* synchronized (previewCritical) {
                    if (previewCritical == null) {
                        previewCritical = previewCritical2;
                    } else {
                        previewCritical.setProcessor(previewCritical2.getProcessor());
                    }
                } */
                previewCritical.show();
                previewCritical.getWindow().toFront();

            } else {
                //generator.resetCompletion();
                previewCritical.setProcessor(title + "seed detected(" + ts.getTiltAngle(previewIndex) + ")", ip);
                System.out.println("update preview critical");
                previewCritical.deleteRoi();
                IJ.showStatus("looking for seeds...");
                seedDetector.getFilteredImageMSD(previewCritical, localMinima, true, percentageToExcludeX, percentageToExcludeY, filterSmall, filterLarge, extremaNeighborhoodRadius, nbSeeds);
                if (!previewCritical.isVisible() && basePanel.isVisible() && ts.getCompletion() >= 0) {
                    previewCritical.show();
                    previewCritical.updateAndDraw();
                    previewCritical.getWindow().toFront();
                }
                previewCritical.setTitle(title + ((PointRoi) previewCritical.getRoi()).getNCoordinates() + " seed detected(" + ts.getTiltAngle(previewIndex) + ")");
            }
        }

        public void interrupt() {
            seedDetector.interrupt();
            chainsGenerator.interrupt();
            //previewCritical=null;
            super.interrupt();
        }


    }
}
