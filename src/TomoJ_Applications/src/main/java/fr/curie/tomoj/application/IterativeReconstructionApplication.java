package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.gpu.tomoj.tomography.ResolutionEstimationGPU;
import fr.curie.plotj.PlotWindow2;
import ij.IJ;
import ij.Prefs;
import ij.measure.ResultsTable;
import fr.curie.tomoj.tomography.*;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.workflow.UserAction;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by cedric on 02/03/2017.
 */
public class IterativeReconstructionApplication extends ReconstructionApplication {
    protected boolean firstDisplay;
    protected TiltSeries ts;
    protected int nbIteration = 10;
    protected double relaxationCoefficient = 0.1;
    protected int update = 1;
    protected boolean longObjectCompensation = true;
    protected boolean positivityConstraint = false;
    protected boolean saveErrorVolume = false;
    protected boolean saveErrorVolumeAll = false;
    protected int reconstructionType = 0;
    final static int REC_ART = 0;
    final static int REC_SIRT = 1;
    final static int REC_OSSART = 2;
    //protected boolean computeOnGPU;
    //ResolutionEstimation resolutionComputation;


    private JRadioButton ARTRadioButton;
    private JRadioButton SIRTRadioButton;
    private JRadioButton OSSARTRadioButton;
    private JSpinner spinnerIterations;
    private JSpinner spinnerRelaxationCoefficient;
    private JSpinner spinnerUpdate;
    private JCheckBox resinOrCryoSampleCheckBox;
    private JCheckBox positivityConstraintCheckBox;
    private JCheckBox saveErrorVolumesCheckBox;
    private JCheckBox saveErrorVolumeAllIterationsCheckBox;
    private JPanel basePanel;

    public IterativeReconstructionApplication(TiltSeries series) {
        this.ts = series;
        firstDisplay = true;
        resultString = "iterative reconstruction";
    }

    public void initValues() {
        /*nbIteration = 10;
        relaxationCoefficient = 0.1;
        update = 1;
        computeOnGPU = true;
        longObjectCompensation = true;
        positivityConstraint = false;
        saveErrorVolume = false;
        saveErrorVolumeAll = false;*/
        nbIteration = (int) Prefs.get("TOMOJ_IterationNumber.int", nbIteration);
        relaxationCoefficient = Prefs.get("TOMOJ_relaxationCoefficient.double", relaxationCoefficient);
        update = (int) Prefs.get("TOMOJ_updateOSART.int", update);
        longObjectCompensation = Prefs.get("TOMOJ_SampleType.bool", longObjectCompensation);
        switch (reconstructionType) {
            case REC_ART:
                ARTRadioButton.doClick();
                break;
            case REC_SIRT:
                SIRTRadioButton.doClick();
                break;
            case REC_OSSART:
                OSSARTRadioButton.doClick();
                break;
        }

        spinnerIterations.setValue(nbIteration);
        spinnerRelaxationCoefficient.setValue(relaxationCoefficient);
        spinnerUpdate.setValue(update);
        resinOrCryoSampleCheckBox.setSelected(longObjectCompensation);
        positivityConstraintCheckBox.setSelected(positivityConstraint);
        saveErrorVolumesCheckBox.setSelected(saveErrorVolume);
        saveErrorVolumeAllIterationsCheckBox.setSelected(saveErrorVolumeAll);

    }

    public void addListeners() {
        resinOrCryoSampleCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                longObjectCompensation = resinOrCryoSampleCheckBox.isSelected();
            }
        });
        positivityConstraintCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (positivityConstraintCheckBox.isEnabled())
                    positivityConstraint = positivityConstraintCheckBox.isSelected();
            }
        });
        saveErrorVolumesCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveErrorVolume = saveErrorVolumesCheckBox.isSelected();
                saveErrorVolumeAllIterationsCheckBox.setEnabled(saveErrorVolume);
            }
        });
        saveErrorVolumeAllIterationsCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (saveErrorVolumeAllIterationsCheckBox.isEnabled())
                    saveErrorVolumeAll = saveErrorVolumeAllIterationsCheckBox.isSelected();
            }
        });
        spinnerIterations.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                nbIteration = ((Number) spinnerIterations.getValue()).intValue();
            }
        });
        spinnerRelaxationCoefficient.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                relaxationCoefficient = ((Number) spinnerRelaxationCoefficient.getValue()).doubleValue();
            }
        });
        spinnerUpdate.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                update = ((Number) spinnerUpdate.getValue()).intValue();
            }
        });
        ARTRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                spinnerUpdate.setValue(1);
                spinnerUpdate.setEnabled(false);
                spinnerRelaxationCoefficient.setEnabled(true);
                reconstructionType = REC_ART;
            }
        });
        SIRTRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                spinnerUpdate.setValue(ts.getImageStackSize());
                spinnerRelaxationCoefficient.setValue(1.0);
                relaxationCoefficient = 1.0;
                update = ts.getImageStackSize();
                spinnerUpdate.setEnabled(false);
                spinnerRelaxationCoefficient.setEnabled(false);
                reconstructionType = REC_SIRT;
            }
        });
        OSSARTRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                spinnerRelaxationCoefficient.setEnabled(true);
                spinnerUpdate.setEnabled(true);
                reconstructionType = REC_OSSART;
            }
        });

    }

    public boolean run() {
        if (width == 0) width = ts.getWidth();
        if (height == 0) height = ts.getHeight();
        if (depth == 0) depth = ts.getWidth();
        if (centerx == 0) centerx = (width - 1.0) / 2.0;
        if (centery == 0) centery = (height - 1.0) / 2.0;
        if (centerz == 0) centerz = (depth - 1.0) / 2.0;

        final OutputStreamCapturer capture = new OutputStreamCapturer();
        if (relaxationCoefficient == 0) {
            IJ.showMessage("TomoJ reconstruction", "the relaxation coefficient is put to zero.\n" +
                    "This value is forbidden, as it will produce no modification.\n" +
                    "The relaxation coefficient should be in the range ]0,1].\n" +
                    "Please check the relaxation coefficient value. \n" +
                    "Then run again the reconstruction");
            return false;
        }
        final int fillingType = ts.getFillType();
        ts.setFillType(TiltSeries.FILL_NaN);

        String title = (rec != null) ? rec.getTitle() : ts.getTitle();
        UserAction ua;
        final ReconstructionParameters recParams;
        switch (reconstructionType) {
            case REC_ART:
                title += (computeOnGPU) ? "OS-SART(ART)_GPU_" : "ART_";
                title += "ite" + nbIteration + "_rel" + IJ.d2s(relaxationCoefficient);
                ua = new UserAction("ART", "relaxationCoef=" + relaxationCoefficient + " iterations=" + nbIteration, "ART", false);
                ua.setCurrentReconstruction("ART");
                break;
            case REC_SIRT:
                title += (computeOnGPU) ? "OS-SART(SIRT)_GPU_" : "SIRT_";
                title += "ite" + nbIteration + "_rel" + IJ.d2s(relaxationCoefficient);
                ua = new UserAction("SIRT", "relaxationCoef=" + relaxationCoefficient + " iterations=" + nbIteration, "SIRT", false);
                ua.setCurrentReconstruction("SIRT");
                break;
            case REC_OSSART:
            default:
                title += (computeOnGPU) ? "OS-SART_GPU_" : "OS-SART_";
                title += "ite" + nbIteration + "_rel" + IJ.d2s(relaxationCoefficient) + "_update" + update;
                ua = new UserAction("OS-SART_GPU", "relaxationCoef=" + relaxationCoefficient + " iterations=" + nbIteration + " updateEvery=" + update, "OS-SART_GPU", false);
                ua.setCurrentReconstruction("OS-SART");
        }
        double[] modifiers = new double[]{(width - 1.0) / 2.0 - centerx, (height - 1.0) / 2.0 - centery, (depth - 1.0) / 2.0 - centerz};
        recParams = ReconstructionParameters.createOSSARTParameters(width, height, depth, nbIteration, relaxationCoefficient, update, modifiers);
        recParams.setRescaleData(rescaleData);
        recParams.setLongObjectCompensation(longObjectCompensation);
        recParams.setPositivityConstraint(positivityConstraintCheckBox.isSelected());

        final String ftitle = title;
        if (computeOnGPU) {
            ResolutionEstimationGPU resolutionComputation = new ResolutionEstimationGPU(ts, recParams);
            resolutionComputation.setUse(use);
            resolutionComputation.setDevices(gpuDevices);
            this.resolutionComputation = resolutionComputation;
        } else {
            resolutionComputation = new ResolutionEstimation(ts, recParams);
        }
        if (rec != null) resolutionComputation.setReconstructionSignal(rec);

        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
        final Chrono time = new Chrono();

        if (computeSSNR) {
            System.out.println("compute SSNR (with reconstruction)");
            double[][] result = resolutionComputation.SSNR(computeVSSNR);
            double[] x = new double[ts.getWidth() / 2];
            double[] y = new double[ts.getWidth() / 2];
            ResultsTable rt = new ResultsTable();
            for (int i = 0; i < ts.getWidth() / 2; i++) {
                x[i] = result[1][i];
                y[i] = result[2][i];
                System.out.println(result[0][i] + " " + result[1][i] + " " + result[2][i] + " " + result[3][i] + " " + result[4][i] + " " + result[5][i] + " " + result[6][i] + " " + result[7][i] + " " + result[8][i] + " ");
                rt.incrementCounter();
                rt.addValue("index", result[0][i]);
                rt.addValue("frequence", result[1][i]);
                rt.addValue("SSNR", result[2][i]);
            }
            rt.setPrecision(5);
            rt.showRowNumbers(true);
            rt.show("SSNR");

            PlotWindow2 pw = new PlotWindow2();
            pw.removeAllPlots();
            pw.addPlot(x, y, Color.RED, "SSNR");
            pw.resetMinMax();
            pw.setVisible(true);
            rec = resolutionComputation.getReconstructionSignal();

        } else if (!fscOnly) {
            System.out.println("compute reconstruction");
            /*final ReconstructionThreadManager recthman = new ReconstructionThreadManager(ts.getWindow(), ts);
            recthman.setUse(use);
            if (rec2 != null) recthman.setRec2(rec2);
            recthman.reconstruct(recParams);
            rec2 = recthman.getRec2();*/
            resolutionComputation.doSignalReconstruction();
            rec = resolutionComputation.getReconstructionSignal();
        }
        if (computeFSC) {
            System.out.println("compute FSC");
            double[][] resultfsc = resolutionComputation.fsc();
            System.out.println("fsc formating results");
            double[] x = resultfsc[0];
            double[] y = resultfsc[1];
            double[] half = new double[x.length];
            Arrays.fill(half, 0.5);
            ResultsTable rt = new ResultsTable();
            for (int i = 0; i < x.length; i++) {
                System.out.println("" + x[i] + "\t" + y[i]);
                rt.incrementCounter();
                rt.addValue("index", i);
                rt.addValue("frequence", x[i]);
                rt.addValue("FSC", y[i]);
            }
            rt.setPrecision(5);
            rt.showRowNumbers(true);
            rt.show("FSC");

            PlotWindow2 pw = new PlotWindow2();
            pw.removeAllPlots();
            pw.addPlot(x, y, Color.RED, "FSC");
            pw.addPlot(x, resultfsc[2], Color.BLUE, "FSC_noise");
            pw.addPlot(x, resultfsc[3], Color.GREEN, "error L2");
            pw.addPlot(x, half, Color.BLACK, "0.5 threshold");
            pw.resetMinMax();
            pw.setVisible(true);
        }

        //finalisation
        time.stop();
        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        if (rec != null) {
            rec.show();
            rec.setTitle(ftitle);
            rec.setSlice(rec.getImageStackSize() / 2);
        }
        Prefs.set("TOMOJ_Thickness.int", depth);
        int recChoiceIndex = (computeOnGPU) ? 10 : 0;
        switch (reconstructionType) {
            case REC_ART:
                recChoiceIndex += 2;
                break;
            case REC_SIRT:
                recChoiceIndex += 3;
                break;
            case REC_OSSART:
            default:
                recChoiceIndex += 4;


        }
        Prefs.set("TOMOJ_ReconstructionType.int", recChoiceIndex);
        Prefs.set("TOMOJ_SampleType.bool", longObjectCompensation);
        Prefs.set("TOMOJ_IterationNumber.int", nbIteration);
        Prefs.set("TOMOJ_updateOSART.int", update);
        Prefs.set("TOMOJ_relaxationCoefficient.double", relaxationCoefficient);

        ts.setThresholdHisteresis(Double.MIN_VALUE, Double.MIN_VALUE, false);
        ts.setFillType(fillingType);


        return true;
    }

    public void setParameters(Object... parameters) {
        super.setParameters(parameters);
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index] instanceof String) {
                if (((String) parameters[index]).toLowerCase().equals("compensation")) {
                    longObjectCompensation = true;
                } else if (((String) parameters[index]).toLowerCase().equals("nbiteration")) {
                    if (parameters[index + 1] instanceof String)
                        nbIteration = Integer.parseInt((String) parameters[index + 1]);
                    else nbIteration = (Integer) parameters[index + 1];
                    index += 1;

                } else if (((String) parameters[index]).toLowerCase().equals("relaxationcoeff")) {
                    if (parameters[index + 1] instanceof String)
                        relaxationCoefficient = Double.parseDouble((String) parameters[index + 1]);
                    else relaxationCoefficient = (Double) parameters[index + 1];
                    index += 1;

                } else if (((String) parameters[index]).toLowerCase().equals("update")) {
                    if (parameters[index + 1] instanceof String)
                        update = Integer.parseInt((String) parameters[index + 1]);
                    else update = (Integer) parameters[index + 1];
                    index += 1;

                } else if (((String) parameters[index]).toLowerCase().equals("positivity")) {
                    positivityConstraint = true;
                }
            }
        }
    }

    public static String help() {
        return ReconstructionApplication.help() + "### os-sart algorithm (ART/SIRT...) ###\n" +
                "nbiteration value: number of iterations\n" +
                "relaxationcoeff value: relaxation coefficient (1 for SIRT)\n" +
                "update value: updates volume every updatevalue projection comparison (1 for ART, nbImagesInTiltSeries for SIRT)\n" +
                "compensation: activates long object compensation (cryo/resin samples)\n" +
                "positivity: forces the values in reconstruction to be positive\n" +
                "";
    }

    public String name() {
        return null;
    }


    public ArrayList<Object> getParametersType() {
        return null;
    }

    public ArrayList<String> getParametersName() {
        return null;
    }

    public String getParametersValuesAsString() {
        String text = super.getParametersValuesAsString();
        switch (reconstructionType) {
            case REC_ART:
                text += "\nART " + nbIteration + " iterations, relaxation:" + relaxationCoefficient;
                break;
            case REC_OSSART:
                text += "\nOS-SART " + nbIteration + " iterations, relaxation:" + relaxationCoefficient + ", update: " + update;
                break;
            case REC_SIRT:
                text += "\nSIRT " + nbIteration + " iterations";
                break;
        }
        if (longObjectCompensation) text += "\nlong object compensation activated";
        if (positivityConstraint) text += "\npositivity constraint activated";

        return text;
    }

    public JPanel getJPanel() {
        if (firstDisplay) {
            addListeners();
            initValues();
        }
        return basePanel;
    }


    private void createUIComponents() {
        spinnerRelaxationCoefficient = new JSpinner(new SpinnerNumberModel(0.1, 0.0001, 2, 0.01));
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
        createUIComponents();
        basePanel = new JPanel();
        basePanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        ARTRadioButton = new JRadioButton();
        ARTRadioButton.setText("ART");
        basePanel.add(ARTRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        SIRTRadioButton = new JRadioButton();
        SIRTRadioButton.setText("SIRT");
        basePanel.add(SIRTRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        OSSARTRadioButton = new JRadioButton();
        OSSARTRadioButton.setText("OS-SART");
        basePanel.add(OSSARTRadioButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panel1, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Iterative reconstruction Options", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label1 = new JLabel();
        label1.setText("number of iterations");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("relaxation coefficient");
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("number of projection before updating volume");
        panel1.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerIterations = new JSpinner();
        panel1.add(spinnerIterations, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerRelaxationCoefficient, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerUpdate = new JSpinner();
        panel1.add(spinnerUpdate, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resinOrCryoSampleCheckBox = new JCheckBox();
        resinOrCryoSampleCheckBox.setText("resin or cryo sample");
        panel1.add(resinOrCryoSampleCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        positivityConstraintCheckBox = new JCheckBox();
        positivityConstraintCheckBox.setText("positivity constraint");
        panel1.add(positivityConstraintCheckBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveErrorVolumesCheckBox = new JCheckBox();
        saveErrorVolumesCheckBox.setText("save error volumes");
        panel1.add(saveErrorVolumesCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveErrorVolumeAllIterationsCheckBox = new JCheckBox();
        saveErrorVolumeAllIterationsCheckBox.setEnabled(false);
        saveErrorVolumeAllIterationsCheckBox.setText("for all iterations");
        panel1.add(saveErrorVolumeAllIterationsCheckBox, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(ARTRadioButton);
        buttonGroup.add(SIRTRadioButton);
        buttonGroup.add(OSSARTRadioButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

}
