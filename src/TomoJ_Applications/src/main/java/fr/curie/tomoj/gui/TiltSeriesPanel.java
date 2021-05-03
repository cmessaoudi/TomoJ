package fr.curie.tomoj.gui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.io.FileSaver;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.utils.Chrono;
import fr.curie.gpu.utils.GPUDevice;
import fr.curie.tomoj.workflow.UserAction;
import fr.curie.tomoj.application.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class TiltSeriesPanel {
    private JPanel panelroot;
    private JButton prealignButton;
    private JButton crossCorrelationFastAlignmentButton;
    private JButton generateLandmarksButton;
    private JRadioButton patchTrackingRadioButton;
    private JRadioButton featureTrackingRadioButton;
    private JButton alignUsingLandmarksButton;
    private JButton manualCorrectionButton;
    private JSpinner spinnerLandmarks;
    private JButton buttonLandmarksRemove;
    private JCheckBox showAllCheckBox;
    private JButton buttonLandmarksAdd;
    private JButton runButton;
    private JPanel applicationOptionPanel;
    private JPanel alignmentPanel;
    private JPanel runPanel;
    private JPanel reconstructionPanel;
    private JSpinner spinnerWidth;
    private JSpinner spinnerHeight;
    private JSpinner spinnerDepth;
    private JCheckBox rescaleCheckBox;
    private JSpinner spinnerTiltAxisValue;
    private JSpinner spinnerCenterX;
    private JSpinner spinnerCenterY;
    private JSpinner spinnerCenterZ;
    private JCheckBox useGPUCheckBox;
    private JTable tableGPU;
    private JComboBox correctionMethodComboBox;
    private JCheckBox updateCurrentReconstructionCheckBox;
    private JCheckBox autosaveFinalIterationCheckBox;
    private JRadioButton WBPRadioButton;
    private JRadioButton iterativeRadioButton;
    private JRadioButton TVMRadioButton;
    private JRadioButton CSRadioButton;
    private JCheckBox FSCCheckBox;
    private JCheckBox SSNRCheckBox;
    private JCheckBox VSSNRCheckBox;
    private JRadioButton alignmentRadioButton;
    private JRadioButton reconstructionRadioButton;
    private JPanel applicationChoicePanel;
    private JPanel tiltAxisPanel;
    private JButton button1;
    private JCheckBox expertModeCheckBox;
    private JPanel volDimPanel;

    GridConstraints constraints;

    protected TiltSeries ts;
    protected JPanel imagePanel;
    CustomStackWindowTemp window;
    int width, height, depth;
    double volCenterX, volCenterY, volCenterZ;
    boolean rescaleData = true;
    boolean computeOnGpu = false;
    boolean ssnr = false;
    boolean vssnr = false;
    boolean fsc = false;
    protected ArrayList<UserAction> log;
    boolean fscOnly = false;
    boolean updatePreviousRec = false;
    TomoReconstruction2 reconstruction = null;
    boolean reconstructionAutomaticSaving = false;

    public static boolean gpuAvailable = true;

    Application currentApplication;
    Application currentApplicationAlign;
    Application currentApplicationReconstruction;

    Application preali;
    Application xcorr;
    Application criticalLandmarks;
    Application featureTracking;
    Application alignLandmarks;
    Application manualAlign;

    Application wbp;
    Application iterativeRec;
    Application tvm;
    Application compressedSensing;


    public TiltSeriesPanel(TiltSeries ts) {
        System.out.println("tiltSeriesPanel creation");
        System.out.flush();
        this.ts = ts;
        $$$setupUI$$$();
        System.out.println("tiltSeriesPanel creation");
        System.out.flush();
        if (!gpuAvailable) {
            System.out.println("no Gpu available from tiltSeriespanel");
            System.out.flush();
            tableGPU.setEnabled(false);
            tableGPU.setVisible(false);
            useGPUCheckBox.setEnabled(false);
        }
        System.out.println("setupui done");
        System.out.flush();
        window = new CustomStackWindowTemp(ts, ts.getCanvas());
        System.out.println("custom windows done");
        reconstructionPanel.setVisible(false);
        constraints = new GridConstraints();
        constraints.setFill(GridConstraints.FILL_BOTH);
        log = new ArrayList<UserAction>();

        System.out.println("creating applications");
        preali = new CenterImagesApplication(ts);
        System.out.println("preali OK");
        xcorr = new CrossCorrelationParameters(ts);
        System.out.println("cross correlation OK");
        criticalLandmarks = new CriticalLandmarksGenerator(ts, ts.getTomoJPoints());
        System.out.println("critical OK");
        try {
            Class tmp = Class.forName("org.bytedeco.javacpp.opencv_features2d");
            if (tmp != null) {
                featureTracking = new FeatureTrackingGenerator(ts, ts.getTomoJPoints());
                System.out.println("feature tracking OK");
            }
        } catch (Exception e) {
            System.out.println("javaccp-presets for opencv platform is not installed : feature based alignment procedures using OpenCV are unavailable!");
            featureTrackingRadioButton.setVisible(false);
        }
        alignLandmarks = new AlignWithLandmarks(ts, ts.getTomoJPoints());
        System.out.println("align landmarks OK");
        manualAlign = new ManualAlignmentApplication(ts);
        System.out.println("manual align OK");
        System.out.flush();

        currentApplication = currentApplicationAlign = xcorr;

        wbp = new WBPReconstructionApplication(ts);
        System.out.println("wbp OK");
        iterativeRec = new IterativeReconstructionApplication(ts);
        System.out.println("art OK");
        tvm = new ReconstructionTVMApplication(ts);
        System.out.println("tvm OK");
        try {
            Class tmp = Class.forName("fractsplinewavelets.BasisFunctions");
            if (tmp != null) {
                compressedSensing = new ReconstructionCompressedSensingApplication(ts);
                System.out.println("cs OK");
            }
        } catch (Exception e) {
            System.out.println("Fractional Spline Wavelet plugin is not installed : CS reconstruction unvailable!");
            CSRadioButton.setVisible(false);
        }

        currentApplicationReconstruction = iterativeRec;

        width = ts.getWidth();
        height = ts.getHeight();
        depth = (int) Prefs.get("TOMOJ_Thickness.int", ts.getWidth());

        addListeners();

        crossCorrelationFastAlignmentButton.doClick();

    }

    public void addListeners() {
        System.out.println("init Listeners");
        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentApplication.setDisplayPreview(false);
                final Chrono time = new Chrono(100);
                final Thread T = new Thread() {
                    public void run() {
                        if (currentApplication instanceof ReconstructionApplication) {
                            if (reconstruction != null && updatePreviousRec) {
                                ((ReconstructionApplication) currentApplication).setReconstruction(reconstruction);
                                System.out.println(" update reconstruction ");
                            } else {
                                if (((ReconstructionApplication) currentApplication).getReconstruction() != null) {
                                    ((ReconstructionApplication) currentApplication).setReconstruction(null);
                                }
                            }
                        }
                        currentApplication.run();
                        updatePointSpinner();
                        ts.setRoi(ts.getTomoJPoints().getRoi(ts.getCurrentSlice() - 1));
                        ts.setSlice(ts.getCurrentSlice());
                        ts.updateAndDraw();
                        ts.threadStats();
                        String params = currentApplication.getParametersValuesAsString();
                        ArrayList<Object> results = currentApplication.getResults();
                        if (results != null) {
                            params += "\n" + results.get(0);
                            if (currentApplication instanceof ReconstructionApplication) {
                                reconstruction = (TomoReconstruction2) results.get(1);
                                if (reconstructionAutomaticSaving) {
                                    FileSaver fs = new FileSaver(reconstruction);
                                    fs.saveAsTiffStack(IJ.getDirectory("current") + reconstruction.getTitle() + ".tif");
                                    Prefs.set("TOMOJ_AutoSave.bool", reconstructionAutomaticSaving);
                                }
                            }
                        } else {
                            System.err.println("results is null!!!");
                        }
                        UserAction ua = new UserAction(currentApplication.name(), params,
                                currentApplication.name(), false);
                        log.add(ua);
                    }
                };
                T.start();

                final Component dia = panelroot;
                final Thread progress = new
                        Thread() {
                            public void run() {
                                ProgressMonitor toto = new ProgressMonitor(dia, currentApplication.name(), "", 0, 100);
                                while (T.isAlive()) {
                                    if (toto.isCanceled()) {
                                        currentApplication.interrupt();
                                        ts.combineTransforms(true);
                                        ts.applyTransforms(true);
                                        //T.stop();
                                        toto.close();
                                        System.out.println("process interrupted");
                                        IJ.showStatus("process interrupted");
                                    } else {
                                        time.stop();
                                        toto.setProgress((int) (currentApplication.getCompletion()));
                                        String note = "" + (int) currentApplication.getCompletion();
                                        if (currentApplication.getCompletion() > 0)
                                            note += "% approximately " + time.remainString(currentApplication.getCompletion()) + " left";
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

        addListenersAlignment();
        initReconstructionListeners();
    }

    public void addListenersAlignment() {
        System.out.println("init Listeners for alignment");
        showAllCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ts.getTomoJPoints().showAll(showAllCheckBox.isSelected());
                ts.setSlice(ts.getCurrentSlice());
                ts.updateAndDraw();
            }
        });
        buttonLandmarksAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ts.updatePoint();
                Roi toto = null;
                ts.setRoi(toto);
                spinnerLandmarks.getModel().setValue(ts.getTomoJPoints().addNewSetOfPoints(false));
                updatePointSpinner();
//                ((SpinnerNumberModel) spinner1.getModel()).setMaximum(tp.getNumberOfPoints() - 1);
//                ((SpinnerNumberModel) spinner1.getModel()).setMinimum(0);
                ts.getTomoJPoints().updateRoiOnTiltSeries();
            }
        });
        buttonLandmarksRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Roi toto = null;
                ts.setRoi(toto);
                if ((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
                    spinnerLandmarks.getModel().setValue(ts.getTomoJPoints().removeAllSetsOfPoints());
                } else {
                    spinnerLandmarks.getModel().setValue(ts.getTomoJPoints().removeCurrentSetOfPoints());
                }
                updatePointSpinner();
                ts.getTomoJPoints().updateRoiOnTiltSeries();
            }
        });
        spinnerLandmarks.addChangeListener(new ChangeListener() {
            /**
             * Invoked when the target of the listener has changed its state.
             *
             * @param e a ChangeEvent object
             */
            public void stateChanged(ChangeEvent e) {
                ts.updatePoint();
                updatePointSpinner();
                ts.getTomoJPoints().setCurrentIndex(((SpinnerNumberModel) spinnerLandmarks.getModel()).getNumber().intValue());
                ts.getTomoJPoints().updateRoiOnTiltSeries();
            }
        });

        alignmentRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean selected = alignmentRadioButton.isSelected();
                alignmentPanel.setVisible(selected);
                reconstructionPanel.setVisible(!selected);
                setApplication(currentApplicationAlign);


            }
        });
        reconstructionRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean selected = reconstructionRadioButton.isSelected();
                alignmentPanel.setVisible(!selected);
                reconstructionPanel.setVisible(selected);
                setApplication(currentApplicationReconstruction);

            }
        });

        prealignButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetButtons();
                setApplication(preali);
                prealignButton.setEnabled(false);
            }
        });
        manualCorrectionButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetButtons();
                setApplication(manualAlign);
                manualCorrectionButton.setEnabled(false);
            }
        });
        crossCorrelationFastAlignmentButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetButtons();
                setApplication(xcorr);
                crossCorrelationFastAlignmentButton.setEnabled(false);
            }
        });
        patchTrackingRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setApplication(criticalLandmarks);
            }
        });
        if (featureTracking != null) {
            featureTrackingRadioButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    setApplication(featureTracking);
                }
            });
        }
        generateLandmarksButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetButtons();
                patchTrackingRadioButton.setVisible(true);
                if (featureTracking != null) {
                    featureTrackingRadioButton.setVisible(true);
                }
                if (patchTrackingRadioButton.isSelected()) setApplication(criticalLandmarks);
                else setApplication(featureTracking);
                generateLandmarksButton.setEnabled(false);
            }
        });
        alignUsingLandmarksButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetButtons();
                setApplication(alignLandmarks);
                alignUsingLandmarksButton.setEnabled(false);
            }
        });

    }


    public void initReconstructionListeners() {
        System.out.println("init Listeners for Reconstruction");

        WBPRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setApplication(wbp);
                SSNRCheckBox.setEnabled(true);
                VSSNRCheckBox.setEnabled(SSNRCheckBox.isEnabled() && SSNRCheckBox.isSelected());
                FSCCheckBox.setEnabled(true);
            }
        });
        iterativeRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setApplication(iterativeRec);
                SSNRCheckBox.setEnabled(true);
                VSSNRCheckBox.setEnabled(SSNRCheckBox.isEnabled() && SSNRCheckBox.isSelected());
                FSCCheckBox.setEnabled(true);
            }
        });
        TVMRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setApplication(tvm);
                SSNRCheckBox.setSelected(false);
                SSNRCheckBox.setEnabled(false);
                VSSNRCheckBox.setEnabled(SSNRCheckBox.isEnabled() && SSNRCheckBox.isSelected());
                FSCCheckBox.setSelected(false);
                FSCCheckBox.setEnabled(false);
                fscOnly = false;
            }
        });
        if (compressedSensing != null) {
            CSRadioButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    setApplication(compressedSensing);
                    SSNRCheckBox.setSelected(false);
                    SSNRCheckBox.setEnabled(false);
                    VSSNRCheckBox.setEnabled(SSNRCheckBox.isEnabled() && SSNRCheckBox.isSelected());
                    FSCCheckBox.setSelected(false);
                    FSCCheckBox.setEnabled(false);
                    fscOnly = false;
                }
            });
        }

        useGPUCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gpuAvailable) return;
                computeOnGpu = useGPUCheckBox.isSelected();
                updateReconstructionValues();
                //tableGPU.setVisible(computeOnGpu);
                tableGPU.setEnabled(computeOnGpu);
                if (computeOnGpu) {
                    tableGPU.setBackground(Color.WHITE);
                } else {
                    tableGPU.setBackground(panelroot.getBackground());
                }
                if (currentApplication instanceof ReconstructionApplication) {
                    ((ReconstructionApplication) currentApplication).setComputeOnGPU(useGPUCheckBox.isSelected(), ((GPUDevicesTableModel) tableGPU.getModel()).getDevices(), ((GPUDevicesTableModel) tableGPU.getModel()).getUse());
                }
            }
        });
        rescaleCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                rescaleData = rescaleCheckBox.isSelected();
                System.out.println(rescaleData);
                updateReconstructionValues();
                UserAction ua = new UserAction("set rescale data for reconstruction", "" + rescaleData,
                        currentApplication.name(), false);
                log.add(ua);
                System.out.println("set rescale data for reconstruction" + rescaleData);

            }
        });

        expertModeCheckBox.addActionListener((new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tiltAxisPanel.setVisible(expertModeCheckBox.isSelected());
                volDimPanel.setVisible(expertModeCheckBox.isSelected());
                updateCurrentReconstructionCheckBox.setVisible(expertModeCheckBox.isSelected());
                autosaveFinalIterationCheckBox.setVisible(expertModeCheckBox.isSelected());
            }
        }));

        spinnerWidth.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                width = ((Number) spinnerWidth.getValue()).intValue();
                if (gpuAvailable) ((GPUDevicesTableModel) tableGPU.getModel()).updateValues(width, height, depth);
                volCenterX = (width - 1.0) / 2.0;
                spinnerCenterX.setValue(volCenterX);
                ((SpinnerNumberModel) spinnerCenterX.getModel()).setMaximum((double) width);
                updateReconstructionValues();
                UserAction ua = new UserAction("set reconstruction width", "" + width,
                        currentApplication.name(), false);
                log.add(ua);
                System.out.println("set reconstruction width to " + width);
            }
        });
        spinnerHeight.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                height = ((Number) spinnerHeight.getValue()).intValue();
                if (gpuAvailable) ((GPUDevicesTableModel) tableGPU.getModel()).updateValues(width, height, depth);
                volCenterY = (height - 1.0) / 2.0;
                spinnerCenterY.setValue(volCenterY);
                ((SpinnerNumberModel) spinnerCenterY.getModel()).setMaximum((double) height);
                updateReconstructionValues();
                UserAction ua = new UserAction("set reconstruction height", "" + height,
                        currentApplication.name(), false);
                log.add(ua);
                System.out.println("set reconstruction height to " + height);
            }
        });
        spinnerDepth.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                depth = ((Number) spinnerDepth.getValue()).intValue();
                if (gpuAvailable) ((GPUDevicesTableModel) tableGPU.getModel()).updateValues(width, height, depth);
                volCenterZ = (depth - 1.0) / 2.0;
                spinnerCenterZ.setValue(volCenterZ);
                ((SpinnerNumberModel) spinnerCenterZ.getModel()).setMaximum((double) depth);
                updateReconstructionValues();
                UserAction ua = new UserAction("set reconstruction depth", "" + depth,
                        currentApplication.name(), false);
                log.add(ua);
                System.out.println("set reconstruction depth to " + depth);
            }
        });

        spinnerCenterX.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                System.out.println("test");
                volCenterX = ((Number) spinnerCenterX.getValue()).doubleValue();
                updateReconstructionValues();
                UserAction ua = new UserAction("set reconstruction center X", "" + volCenterX,
                        currentApplication.name(), false);
                log.add(ua);
                System.out.println("set reconstruction center X to " + volCenterX);
            }
        });
        spinnerCenterY.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                volCenterY = ((Number) spinnerCenterY.getValue()).doubleValue();
                updateReconstructionValues();
                UserAction ua = new UserAction("set reconstruction center Y", "" + volCenterY,
                        currentApplication.name(), false);
                log.add(ua);
                System.out.println("set reconstruction center Y to " + volCenterY);
            }
        });
        spinnerCenterZ.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                volCenterZ = ((Number) spinnerCenterZ.getValue()).doubleValue();
                updateReconstructionValues();
                UserAction ua = new UserAction("set reconstruction center Z", "" + volCenterZ,
                        currentApplication.name(), false);
                log.add(ua);
                System.out.println("set reconstruction center Z to " + volCenterZ);
            }
        });

        spinnerTiltAxisValue.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                double tiltAxisValue = ((Number) spinnerTiltAxisValue.getValue()).doubleValue();
                ts.setTiltAxis(tiltAxisValue);

                ts.setSlice(ts.getCurrentSlice());
                ts.updateAndDraw();
                UserAction ua = new UserAction("set tilt axis", "" + tiltAxisValue,
                        currentApplication.name(), false);
                log.add(ua);
                System.out.println("set tilt axis to " + tiltAxisValue);
            }
        });

        SSNRCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                VSSNRCheckBox.setEnabled(SSNRCheckBox.isSelected());
                ssnr = SSNRCheckBox.isSelected();
                updateReconstructionValues();
            }
        });
        VSSNRCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                vssnr = VSSNRCheckBox.isSelected();
                updateReconstructionValues();
            }
        });
        FSCCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fsc = FSCCheckBox.isSelected();
                fscOnly = ((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK);
                updateReconstructionValues();
            }
        });
        correctionMethodComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = correctionMethodComboBox.getSelectedIndex();
                switch (index) {
                    case 0:
                        ts.setAlignMethodForReconstruction(TiltSeries.ALIGN_AFFINE2D);
                        break;
                    case 1:
                    default:
                        ts.setAlignMethodForReconstruction(TiltSeries.ALIGN_PROJECTOR);
                        break;
                }
                System.out.println("change correction of alignment to " + correctionMethodComboBox.getItemAt(index));
                UserAction ua = new UserAction("change correction of alignment ", correctionMethodComboBox.getItemAt(index).toString(),
                        "change correction of alignment ", false);
                log.add(ua);
            }
        });
        int index = 0;
        switch (ts.getAlignMethodForReconstruction()) {
            case TiltSeries.ALIGN_AFFINE2D:
                index = 0;
                break;
            case TiltSeries.ALIGN_PROJECTOR:
            default:
                index = 1;
                break;
        }
        //correctionMethodComboBox.setSelectedIndex(index);


        updateCurrentReconstructionCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updatePreviousRec = updateCurrentReconstructionCheckBox.isSelected();
            }
        });
        autosaveFinalIterationCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reconstructionAutomaticSaving = autosaveFinalIterationCheckBox.isSelected();

            }
        });


        width = ts.getWidth();
        spinnerWidth.setValue(width);
        height = ts.getHeight();
        spinnerHeight.setValue(height);
        depth = (int) Prefs.get("TOMOJ_Thickness.int", ts.getWidth());
        spinnerDepth.setValue(depth);
        reconstructionAutomaticSaving = Prefs.get("TOMOJ_AutoSave.bool", false);
        autosaveFinalIterationCheckBox.setSelected(reconstructionAutomaticSaving);

        volCenterX = (width - 1.0) / 2.0;
        ((SpinnerNumberModel) spinnerCenterX.getModel()).setMaximum((double) width);
        spinnerCenterX.setValue(volCenterX);
        volCenterY = (height - 1.0) / 2.0;
        ((SpinnerNumberModel) spinnerCenterY.getModel()).setMaximum((double) height);
        spinnerCenterY.setValue(volCenterY);
        ((SpinnerNumberModel) spinnerCenterZ.getModel()).setMaximum((double) depth);
        volCenterZ = (depth - 1.0) / 2.0;
        spinnerCenterZ.setValue(volCenterZ);

        tiltAxisPanel.setVisible(expertModeCheckBox.isSelected());
        volDimPanel.setVisible(expertModeCheckBox.isSelected());
        updateCurrentReconstructionCheckBox.setVisible(expertModeCheckBox.isSelected());
        autosaveFinalIterationCheckBox.setVisible(expertModeCheckBox.isSelected());


    }

    public void updateReconstructionValues() {
        ((ReconstructionApplication) iterativeRec).setSize(width, height, depth);
        ((ReconstructionApplication) iterativeRec).setCenter(volCenterX, volCenterY, volCenterZ);
        ((ReconstructionApplication) iterativeRec).setRescaleData(rescaleData);
        if (gpuAvailable)
            ((ReconstructionApplication) iterativeRec).setComputeOnGPU(computeOnGpu, GPUDevice.getGPUDevices(), ((GPUDevicesTableModel) tableGPU.getModel()).getUse());
        ((ReconstructionApplication) iterativeRec).setResolutionEstimation(ssnr, (ssnr) ? vssnr : false, fsc, fscOnly);

        ((ReconstructionApplication) wbp).setSize(width, height, depth);
        ((ReconstructionApplication) wbp).setRescaleData(rescaleData);
        if (gpuAvailable)
            ((ReconstructionApplication) wbp).setComputeOnGPU(computeOnGpu, GPUDevice.getGPUDevices(), ((GPUDevicesTableModel) tableGPU.getModel()).getUse());
        ((ReconstructionApplication) wbp).setResolutionEstimation(ssnr, (ssnr) ? vssnr : false, fsc, fscOnly);
        if (compressedSensing != null) {
            ((ReconstructionApplication) compressedSensing).setSize(width, height, depth);
            ((ReconstructionApplication) compressedSensing).setRescaleData(rescaleData);
            if (gpuAvailable)
                ((ReconstructionApplication) compressedSensing).setComputeOnGPU(computeOnGpu, GPUDevice.getGPUDevices(), ((GPUDevicesTableModel) tableGPU.getModel()).getUse());
            ((ReconstructionApplication) compressedSensing).setResolutionEstimation(ssnr, (ssnr) ? vssnr : false, fsc, fscOnly);
        }

        ((ReconstructionApplication) tvm).setSize(width, height, depth);
        ((ReconstructionApplication) tvm).setRescaleData(rescaleData);
        if (gpuAvailable)
            ((ReconstructionApplication) tvm).setComputeOnGPU(computeOnGpu, GPUDevice.getGPUDevices(), ((GPUDevicesTableModel) tableGPU.getModel()).getUse());
        ((ReconstructionApplication) tvm).setResolutionEstimation(ssnr, (ssnr) ? vssnr : false, fsc, fscOnly);

    }

    public Application getCurrentApplication() {
        return currentApplication;
    }

    protected void updatePointSpinner() {
        try {

            if (((SpinnerNumberModel) spinnerLandmarks.getModel()).getMaximum() == null || ts.getTomoJPoints().getNumberOfPoints() - 1 != (Integer) ((SpinnerNumberModel) spinnerLandmarks.getModel()).getMaximum()) {
                System.out.println("update point spinner!");
                spinnerLandmarks.getModel().setValue(0);
                ((SpinnerNumberModel) spinnerLandmarks.getModel()).setMaximum(ts.getTomoJPoints().getNumberOfPoints() - 1);
                ((SpinnerNumberModel) spinnerLandmarks.getModel()).setMinimum(0);
                spinnerLandmarks.getModel().setValue(ts.getTomoJPoints().getNumberOfPoints() - 1);
            }
        } catch (Exception e) {
        }
    }

    void setApplication(Application appli) {
        currentApplication.setDisplayPreview(false);
        currentApplication = appli;
        currentApplication.setDisplayPreview(true);

        if (appli instanceof ReconstructionApplication) currentApplicationReconstruction = appli;
        else currentApplicationAlign = appli;


        JPanel appliP = currentApplication.getJPanel();
        if (appliP == null) System.out.println("appli null!!!!");
        applicationOptionPanel.removeAll();
        applicationOptionPanel.add(appliP, constraints);

        panelroot.revalidate();
        panelroot.repaint();
    }

    public void resetButtons() {
        crossCorrelationFastAlignmentButton.setEnabled(true);
        generateLandmarksButton.setEnabled(true);
        alignUsingLandmarksButton.setEnabled(true);
        prealignButton.setEnabled(true);
        manualCorrectionButton.setEnabled(true);

        patchTrackingRadioButton.setVisible(false);
        featureTrackingRadioButton.setVisible(false);
    }

    public JPanel getProcessingPanel() {
        return panelroot;
    }

    public JPanel getImagePanel() {
        if (imagePanel == null) imagePanel = createImagePanel();
        return imagePanel;
    }

    protected JPanel createImagePanel() {
        //CustomStackWindowTemp tmp = new CustomStackWindowTemp(ts, ts.getCanvas());
        return window.getImagePanel();
    }

    public ScrollbarWithLabel getScrollBar() {
        return window.getScrollbar();
    }

    public TiltSeries getTiltSeries() {
        return ts;
    }

    public ArrayList<UserAction> getLog() {
        return log;
    }

    private void createUIComponents() {
        tableGPU = new JTable();
        try {
            Class tmp = Class.forName("org.jocl.CLException");
            if (tmp != null) {
                //gpuAvailable = true;
                System.out.println("opencl java files detected");
                String javaLibraryPath = System.getProperty("java.library.path");
                String systemLibraryPath = System.getProperty("sun.boot.library.path");
                System.out.println("library path" + javaLibraryPath);
                System.out.println("system path:" + systemLibraryPath);
                if (IJ.isLinux()) {
                    System.out.println("linux: checking that libOpenCL.so is the path");

                    Field field = ClassLoader.class.getDeclaredField("sys_paths");
                    System.out.println(field.toGenericString());

                    String[] libdirs = systemLibraryPath.split(":");
                    boolean openclExist = false;
                    for (String name : libdirs) {
                        File tmpfile = new File(name + "/libOpenCL.so");
                        openclExist = (tmpfile.exists() || openclExist);
                    }
                    File tmpfile = new File("/usr/lib/x86_64-linux-gnu/libOpenCL.so");
                    openclExist = (tmpfile.exists() || openclExist);

                    System.out.println("opencl library found (libOpenCL.so):" + openclExist);
                    gpuAvailable = openclExist;
                }
            } else {
                gpuAvailable = false;
                System.out.println("opencl library not found : reconstruction on GPU is unavailable!");
            }
        } catch (Exception e) {
            System.out.println("opencl library not found : reconstruction on GPU is unavailable!");
            gpuAvailable = false;
        }
        if (gpuAvailable) {
            tableGPU.setModel(new GPUDevicesTableModel(width, height, depth));

            tableGPU.setFillsViewportHeight(true);
            tableGPU.setEnabled(false);
            JPanel tmp = new JPanel();
            tableGPU.setBackground(tmp.getBackground());
            //tableGPU.setPreferredSize(new Dimension(500, 200));
            JTableHeader jth = tableGPU.getTableHeader();
            //panelGPU.add(jth, BorderLayout.PAGE_START);
            jth.setResizingAllowed(true);
        }
        spinnerCenterX = new JSpinner(new SpinnerNumberModel(100, 0.5, 20000, 0.5));
        spinnerCenterY = new JSpinner(new SpinnerNumberModel(100, 0.5, 20000, 0.5));
        spinnerCenterZ = new JSpinner(new SpinnerNumberModel(100, 0.5, 20000, 0.5));
        spinnerTiltAxisValue = new JSpinner(new SpinnerNumberModel(0, -180, 180, 0.001));
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
        panelroot = new JPanel();
        panelroot.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panelroot.setPreferredSize(new Dimension(745, 327));
        final JScrollPane scrollPane1 = new JScrollPane();
        panelroot.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        scrollPane1.setViewportView(panel1);
        applicationChoicePanel = new JPanel();
        applicationChoicePanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(applicationChoicePanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        alignmentRadioButton = new JRadioButton();
        alignmentRadioButton.setSelected(true);
        alignmentRadioButton.setText("Alignment");
        applicationChoicePanel.add(alignmentRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        reconstructionRadioButton = new JRadioButton();
        reconstructionRadioButton.setText("Reconstruction");
        applicationChoicePanel.add(reconstructionRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        alignmentPanel = new JPanel();
        alignmentPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        applicationChoicePanel.add(alignmentPanel, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
        alignmentPanel.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "alignment processing", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        manualCorrectionButton = new JButton();
        manualCorrectionButton.setText("Manual correction");
        panel2.add(manualCorrectionButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        prealignButton = new JButton();
        prealignButton.setText("prealign");
        panel2.add(prealignButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(0, 2, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "landmarks", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        spinnerLandmarks = new JSpinner();
        panel3.add(spinnerLandmarks, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonLandmarksRemove = new JButton();
        buttonLandmarksRemove.setText("-");
        panel3.add(buttonLandmarksRemove, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showAllCheckBox = new JCheckBox();
        showAllCheckBox.setText("show all");
        panel3.add(showAllCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonLandmarksAdd = new JButton();
        buttonLandmarksAdd.setText("+");
        panel3.add(buttonLandmarksAdd, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        crossCorrelationFastAlignmentButton = new JButton();
        crossCorrelationFastAlignmentButton.setText("Cross-correlation (fast alignment) ");
        panel2.add(crossCorrelationFastAlignmentButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        generateLandmarksButton = new JButton();
        generateLandmarksButton.setText("Generate Landmarks");
        panel2.add(generateLandmarksButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        alignUsingLandmarksButton = new JButton();
        alignUsingLandmarksButton.setText("Align using landmarks");
        panel2.add(alignUsingLandmarksButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel4, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        patchTrackingRadioButton = new JRadioButton();
        patchTrackingRadioButton.setSelected(true);
        patchTrackingRadioButton.setText("patch tracking");
        panel4.add(patchTrackingRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        featureTrackingRadioButton = new JRadioButton();
        featureTrackingRadioButton.setText("feature tracking");
        panel4.add(featureTrackingRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel2.add(spacer1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        reconstructionPanel = new JPanel();
        reconstructionPanel.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
        reconstructionPanel.setVisible(true);
        applicationChoicePanel.add(reconstructionPanel, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        reconstructionPanel.add(panel5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel5.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "volume", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        volDimPanel = new JPanel();
        volDimPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(volDimPanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        volDimPanel.add(panel6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("X");
        panel6.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerWidth = new JSpinner();
        spinnerWidth.setEnabled(true);
        panel6.add(spinnerWidth, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        volDimPanel.add(panel7, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Y");
        panel7.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerHeight = new JSpinner();
        spinnerHeight.setEnabled(true);
        panel7.add(spinnerHeight, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rescaleCheckBox = new JCheckBox();
        rescaleCheckBox.setEnabled(true);
        rescaleCheckBox.setText("rescale");
        volDimPanel.add(rescaleCheckBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("dimensions");
        panel5.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(panel8, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Z");
        panel8.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerDepth = new JSpinner();
        panel8.add(spinnerDepth, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tiltAxisPanel = new JPanel();
        tiltAxisPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        tiltAxisPanel.setEnabled(true);
        reconstructionPanel.add(tiltAxisPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        tiltAxisPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "tilt axis", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        tiltAxisPanel.add(panel9, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("value (°)");
        panel9.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel9.add(spinnerTiltAxisValue, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(1, 7, new Insets(0, 0, 0, 0), -1, -1));
        tiltAxisPanel.add(panel10, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("position in volume: ");
        panel10.add(label6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel10.add(spinnerCenterX, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel10.add(spinnerCenterY, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel10.add(spinnerCenterZ, new GridConstraints(0, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("X");
        panel10.add(label7, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("Y");
        panel10.add(label8, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("Z");
        panel10.add(label9, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        reconstructionPanel.add(panel11, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel11.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "computation options", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        useGPUCheckBox = new JCheckBox();
        useGPUCheckBox.setText("use GPU");
        panel11.add(useGPUCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel11.add(tableGPU, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel11.add(panel12, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        label10.setText("alignment correction");
        panel12.add(label10, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        correctionMethodComboBox = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("Affine 2D");
        defaultComboBoxModel1.addElement("Projector 3D");
        correctionMethodComboBox.setModel(defaultComboBoxModel1);
        panel12.add(correctionMethodComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        updateCurrentReconstructionCheckBox = new JCheckBox();
        updateCurrentReconstructionCheckBox.setEnabled(true);
        updateCurrentReconstructionCheckBox.setText("update current Reconstruction");
        panel12.add(updateCurrentReconstructionCheckBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        autosaveFinalIterationCheckBox = new JCheckBox();
        autosaveFinalIterationCheckBox.setEnabled(true);
        autosaveFinalIterationCheckBox.setText("autosave final iteration");
        panel12.add(autosaveFinalIterationCheckBox, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        reconstructionPanel.add(panel13, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel13.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Reconstruction algorithm", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        WBPRadioButton = new JRadioButton();
        WBPRadioButton.setText("WBP");
        panel13.add(WBPRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        iterativeRadioButton = new JRadioButton();
        iterativeRadioButton.setSelected(true);
        iterativeRadioButton.setText("Iterative");
        panel13.add(iterativeRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        TVMRadioButton = new JRadioButton();
        TVMRadioButton.setText("TVM");
        panel13.add(TVMRadioButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        CSRadioButton = new JRadioButton();
        CSRadioButton.setText("CS");
        panel13.add(CSRadioButton, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        reconstructionPanel.add(panel14, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel14.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Resolution estimation", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        VSSNRCheckBox = new JCheckBox();
        VSSNRCheckBox.setEnabled(false);
        VSSNRCheckBox.setText("VSSNR");
        panel14.add(VSSNRCheckBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        SSNRCheckBox = new JCheckBox();
        SSNRCheckBox.setText("SSNR");
        panel14.add(SSNRCheckBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        FSCCheckBox = new JCheckBox();
        FSCCheckBox.setText("FSC");
        panel14.add(FSCCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        expertModeCheckBox = new JCheckBox();
        expertModeCheckBox.setText("expert mode");
        applicationChoicePanel.add(expertModeCheckBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        runPanel = new JPanel();
        runPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(runPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        runButton = new JButton();
        runButton.setText("Run");
        runPanel.add(runButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        applicationOptionPanel = new JPanel();
        applicationOptionPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(applicationOptionPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        button1 = new JButton();
        button1.setText("Button");
        applicationOptionPanel.add(button1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(patchTrackingRadioButton);
        buttonGroup.add(featureTrackingRadioButton);
        buttonGroup = new ButtonGroup();
        buttonGroup.add(alignmentRadioButton);
        buttonGroup.add(reconstructionRadioButton);
        buttonGroup = new ButtonGroup();
        buttonGroup.add(WBPRadioButton);
        buttonGroup.add(iterativeRadioButton);
        buttonGroup.add(TVMRadioButton);
        buttonGroup.add(CSRadioButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panelroot;
    }


    class CustomStackWindowTemp extends StackWindow {
        JPanel panel;

        CustomStackWindowTemp(ImagePlus imp, final ImageCanvas fic) {
            super(imp, fic);

        }

        public JPanel getImagePanel() {
            if (panel == null) {
                panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.add(ic);
                panel.add(zSelector);

            }
            return panel;
        }

        public ScrollbarWithLabel getScrollbar() {
            return zSelector;
        }
    }
}
