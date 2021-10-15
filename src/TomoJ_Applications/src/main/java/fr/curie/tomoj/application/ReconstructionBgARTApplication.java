package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import fr.curie.gpu.tomoj.tomography.ResolutionEstimationGPU;
import fr.curie.tomoj.tomography.AdvancedReconstructionParameters;
import fr.curie.tomoj.tomography.ReconstructionParameters;
import fr.curie.tomoj.tomography.ResolutionEstimation;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class ReconstructionBgARTApplication extends ReconstructionApplication {
    private JPanel basePanel;
    private JCheckBox resinOrCryoSampleCheckBox;
    private JCheckBox fistaOptimizationCheckBox;
    private JSpinner spinnerNbIterations;
    private JSpinner spinnerRelaxationCoeff;
    private JSpinner spinnerUpdateNb;
    private JSpinner spinnerNbInitialIterations;
    private JSpinner spinnerK;
    private JCheckBox medianFilterCheckBox;
    private JCheckBox darkBackgroundCheckBox;

    /*
    BgARTVoxelProjector3D projector = new BgARTVoxelProjector3D(ts, rec2, null, k, darkBG, median);
                        rec2.OSSART(ts, projector, iteration, ARTrelaxationCoeff, ARTupdate);
     */
    protected TiltSeries ts;
    protected boolean firstDisplay;

    protected int nbiterations = 20;
    protected double relaxationCoefficient = 0.1;
    protected int updateNb = 1;
    protected boolean longObjectCompensation = true;
    protected boolean fista = true;

    protected int bgartInitialIteration = 5;
    protected double bgartK = 1;
    protected boolean bgartMedian = true;
    protected boolean bgartDarkBG = true;

    public ReconstructionBgARTApplication(TiltSeries ts) {
        this.ts = ts;
        firstDisplay = true;
    }

    public void initValues() {

        spinnerNbIterations.setValue(nbiterations);
        spinnerRelaxationCoeff.setValue(relaxationCoefficient);
        spinnerUpdateNb.setValue(updateNb);
        resinOrCryoSampleCheckBox.setSelected(longObjectCompensation);
        fistaOptimizationCheckBox.setSelected(fista);

        spinnerNbInitialIterations.setValue(bgartInitialIteration);
        spinnerK.setValue(bgartK);
        medianFilterCheckBox.setSelected(bgartMedian);
        darkBackgroundCheckBox.setSelected(bgartDarkBG);
    }

    private void addListeners() {
        spinnerNbIterations.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                nbiterations = ((Number) spinnerNbIterations.getValue()).intValue();
            }
        });
        spinnerRelaxationCoeff.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                relaxationCoefficient = ((Number) spinnerRelaxationCoeff.getValue()).doubleValue();
            }
        });
        spinnerUpdateNb.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateNb = ((Number) spinnerUpdateNb.getValue()).intValue();
            }
        });
        resinOrCryoSampleCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                longObjectCompensation = resinOrCryoSampleCheckBox.isSelected();

            }
        });
        fistaOptimizationCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fista = fistaOptimizationCheckBox.isSelected();
            }
        });
        spinnerNbInitialIterations.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                bgartInitialIteration = ((Number) spinnerNbInitialIterations.getValue()).intValue();
            }
        });
        spinnerK.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                bgartK = ((Number) spinnerK.getValue()).doubleValue();
            }
        });
        medianFilterCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                bgartMedian = medianFilterCheckBox.isSelected();

            }
        });
        darkBackgroundCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                bgartDarkBG = darkBackgroundCheckBox.isSelected();

            }
        });

    }

    public boolean run() {
        if (width == 0) width = ts.getWidth();
        if (height == 0) height = ts.getHeight();
        if (depth == 0) depth = ts.getWidth();

        final Chrono time = new Chrono();
        final OutputStreamCapturer capture = new OutputStreamCapturer();
        final ReconstructionParameters paramsInit = ReconstructionParameters.createOSSARTParameters(width, height, depth, bgartInitialIteration, relaxationCoefficient, updateNb);
        final ReconstructionParameters paramsBgART = AdvancedReconstructionParameters.createBgARTParameters(width, height, depth, (nbiterations - bgartInitialIteration), relaxationCoefficient, updateNb, bgartK, bgartMedian, bgartDarkBG);
        paramsInit.setLongObjectCompensation(longObjectCompensation);
        paramsInit.setFista(fista);
        paramsBgART.setLongObjectCompensation(longObjectCompensation);
        paramsBgART.setFista(fista);
        System.out.println(getParametersValuesAsString());
        System.out.println("******* first \n" + paramsInit.asString());
        System.out.println("******* second \n" + paramsBgART.asString());

        /*if (computeOnGPU) {
            ResolutionEstimationGPU resolutionComputation = new ResolutionEstimationGPU(ts, params);
            resolutionComputation.setUse(use);
            resolutionComputation.setDevices(gpuDevices);
            this.resolutionComputation = resolutionComputation;
        } else {
            resolutionComputation = new ResolutionEstimation(ts, params);
        }
        if (ts.isShowInIJ()) {
            IJ.log(getParametersValuesAsString());
        }*/

        ResolutionEstimation computeInit = new ResolutionEstimation(ts, paramsInit);
        ResolutionEstimation computeBgART = new ResolutionEstimation(ts, paramsBgART);

        resolutionComputation = computeInit;
        resolutionComputation.doSignalReconstruction();
        rec = resolutionComputation.getReconstructionSignal();

        computeBgART.setReconstructionSignal(rec);
        resolutionComputation = computeBgART;
        resolutionComputation.doSignalReconstruction();
        rec = resolutionComputation.getReconstructionSignal();

        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();

        if (ts.isShowInIJ()) IJ.log("total time to compute : " + time.delayString());


        if (rec != null) {
            rec.show();
            String title = (rec != null) ? rec.getTitle() : ts.getTitle();
            rec.setTitle(title + getParametersValuesAsShortString());
            rec.setSlice(rec.getImageStackSize() / 2);
        }
        return true;
    }

    public void setParameters(Object... parameters) {

    }

    public static String help() {
        return null;
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
        String text = "###   reconstruction   ###";
        text += "\nBgART " + nbiterations + " iterations, relaxation: " + relaxationCoefficient + ", update: " + updateNb;
        text += "\nBgART K=" + bgartK + ", initial iterations=" + bgartInitialIteration + ", median filter=" + bgartMedian + ", dark background=" + bgartDarkBG;

        text += super.getParametersValuesAsString();
        if (longObjectCompensation) text += "\nlong object compensation activated";
        if (fista) text += "\nfista activated";
        String type = "";
        switch (ts.getAlignMethodForReconstruction()) {
            case TiltSeries.ALIGN_AFFINE2D:
                type = "Affine 2D";
                break;
            case TiltSeries.ALIGN_NONLINEAR:
                type = "2D Non-linear";
                break;
            case TiltSeries.ALIGN_PROJECTOR:
            default:
                type = "3D projector";
                break;
        }
        text += "\napply alignment as : " + type;
        return text;
    }

    public String getParametersValuesAsShortString() {
        String text = "RecBgART_" + nbiterations + "ite_" + relaxationCoefficient + "rel_update_" + updateNb + "_initIte_" + bgartInitialIteration + "_K_" + bgartK + "_median_" + bgartMedian + "_darkBG_" + bgartDarkBG;
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
        spinnerRelaxationCoeff = new JSpinner(new SpinnerNumberModel(relaxationCoefficient, 0.001, 2, 0.01));
        spinnerK = new JSpinner(new SpinnerNumberModel(bgartK, 0.0001, 1, 0.001));

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
        basePanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "reconstruction parameters", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label1 = new JLabel();
        label1.setText("number of iterations");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("relaxation coefficient");
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("update number");
        panel1.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resinOrCryoSampleCheckBox = new JCheckBox();
        resinOrCryoSampleCheckBox.setText("resin or cryo sample");
        panel1.add(resinOrCryoSampleCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fistaOptimizationCheckBox = new JCheckBox();
        fistaOptimizationCheckBox.setText("fista optimization");
        panel1.add(fistaOptimizationCheckBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerNbIterations = new JSpinner();
        panel1.add(spinnerNbIterations, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerRelaxationCoeff, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerUpdateNb = new JSpinner();
        panel1.add(spinnerUpdateNb, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "BgART parameters", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label4 = new JLabel();
        label4.setText("number of initial iterations (without filtering)");
        panel2.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerNbInitialIterations = new JSpinner();
        panel2.add(spinnerNbInitialIterations, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("filter K (threshold limit= K*sigma)");
        panel2.add(label5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(spinnerK, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        medianFilterCheckBox = new JCheckBox();
        medianFilterCheckBox.setText("median filter");
        panel2.add(medianFilterCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        darkBackgroundCheckBox = new JCheckBox();
        darkBackgroundCheckBox.setText("dark background");
        panel2.add(darkBackgroundCheckBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }
}
