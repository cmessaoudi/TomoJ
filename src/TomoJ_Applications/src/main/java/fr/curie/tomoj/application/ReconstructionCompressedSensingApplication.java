package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.gpu.tomoj.tomography.ResolutionEstimationGPU;
import fr.curie.tomoj.tomography.*;
import fractsplinewavelets.BasisFunctions;
import fractsplinewavelets_gui.PlotXY;
import fr.curie.tomoj.tomography.projectors.CompressedSensingProjector;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.application.ReconstructionApplication;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * Created by cmessaoudi on 11/01/2018.
 */
public class ReconstructionCompressedSensingApplication extends ReconstructionApplication {
    private JSpinner spinnerNbIteration;
    private JSpinner spinnerRelaxationCoefficent;
    private JCheckBox resinOrCryoSampleCheckBox;
    private JSpinner spinnerCSThreshold;
    private JLabel csLabel;
    private JPanel basePanel;
    private JSpinner spinnerWaveletDegree;
    private JSpinner spinnerWaveletShift;
    private JComboBox comboBoxWaveletType;
    private JPanel panelPlotXY;
    private JCheckBox fistaOptimizationCheckBox;

    protected int nbiterations = 10;
    protected double relaxationCoefficient = 1;
    protected boolean longObjectCompensation = true;
    protected double CSThreshold = 0.95;
    protected double waveletDegree = 3.0;
    protected double waveletShift = 0;
    protected int waveletType = CompressedSensingProjector.WAVELET_BSPLINE;
    protected boolean fista = true;

    protected TiltSeries ts;
    protected boolean firstDisplay;

    PlotXY plot;
    private boolean basisFunction = true;

    private static final int PLOT_XSIZE = 256;
    private static final int PLOT_YSIZE = 128;
    private final int nbIterationsBasisFunction = 4;
    private final int nbPointsBasisFunction = 256;
    private double[][] y = new double[2][256];
    private double[] x = new double[256];
    private double xstart = -5.0D;
    private double xend = 5.0D;


    public ReconstructionCompressedSensingApplication(TiltSeries ts) {
        this.ts = ts;
        firstDisplay = true;

    }

    public void initValues() {
//        nbiterations = 10;
//        relaxationCoefficient = 1;
//        computeOnGPU = false;
//        longObjectCompensation = true;
//        CSThreshold = 0.95;
//        waveletDegree = 3.0;
//        waveletShift = 0;
//        waveletType = CompressedSensingProjector.WAVELET_BSPLINE;

        spinnerNbIteration.setValue(nbiterations);
        spinnerRelaxationCoefficent.setValue(relaxationCoefficient);
        spinnerCSThreshold.setValue(CSThreshold);
        resinOrCryoSampleCheckBox.setSelected(longObjectCompensation);
        fistaOptimizationCheckBox.setSelected(fista);
        spinnerWaveletDegree.setValue(waveletDegree);
        spinnerWaveletShift.setValue(waveletShift);
        switch (waveletType) {
            case CompressedSensingProjector.WAVELET_ORTHONORMAL:
                comboBoxWaveletType.setSelectedIndex(0);
                break;
            case CompressedSensingProjector.WAVELET_BSPLINE:
            default:
                comboBoxWaveletType.setSelectedIndex(1);

        }

        double p = Math.pow(2.0D, 4.0D);
        for (int i = 0; i < 256; ++i) {
            this.x[i] = 1.0D + (double) (i - 128) / p;
        }
        updatePlot();

    }

    private void addListeners() {
        spinnerNbIteration.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                nbiterations = ((Number) spinnerNbIteration.getValue()).intValue();
            }
        });
        spinnerRelaxationCoefficent.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                relaxationCoefficient = ((Number) spinnerRelaxationCoefficent.getValue()).doubleValue();

            }
        });
        spinnerCSThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                CSThreshold = ((Number) spinnerCSThreshold.getValue()).doubleValue();

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

        comboBoxWaveletType.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = comboBoxWaveletType.getSelectedIndex();
                switch (selectedIndex) {
                    case 0:
                        waveletType = CompressedSensingProjector.WAVELET_ORTHONORMAL;
                        break;
                    case 1:
                    default:
                        waveletType = CompressedSensingProjector.WAVELET_BSPLINE;
                }
                updatePlot();
            }
        });
        spinnerWaveletDegree.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                waveletDegree = ((Number) spinnerWaveletDegree.getValue()).doubleValue();
                updatePlot();
            }
        });
        spinnerWaveletShift.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                waveletShift = ((Number) spinnerWaveletShift.getValue()).doubleValue();
                updatePlot();
            }
        });

    }

    public boolean run() {
        if (width == 0) width = ts.getWidth();
        if (height == 0) height = ts.getHeight();
        if (depth == 0) depth = ts.getWidth();

        final Chrono time = new Chrono();
        final OutputStreamCapturer capture = new OutputStreamCapturer();
        final ReconstructionParameters params = AdvancedReconstructionParameters.createCompressedSensingParameters(width, height, depth, nbiterations, relaxationCoefficient, CSThreshold, waveletType, waveletDegree, waveletShift);
        params.setLongObjectCompensation(longObjectCompensation);
        params.setFista(fista);
        System.out.println(getParametersValuesAsString());
        System.out.println("*******\n" + params.asString());

        if (ts.isShowInIJ()) {
            IJ.log(getParametersValuesAsString());
        }

        if (computeOnGPU) {
            ResolutionEstimationGPU resolutionComputation = new ResolutionEstimationGPU(ts, params);
            resolutionComputation.setUse(use);
            resolutionComputation.setDevices(gpuDevices);
            this.resolutionComputation = resolutionComputation;
        } else {
            resolutionComputation = new ResolutionEstimation(ts, params);
        }
        if (rec != null) resolutionComputation.setReconstructionSignal(rec);

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
        text += "\nCompressed Sensing " + nbiterations + " iterations, relaxation" + relaxationCoefficient + ", Percentage of zeros coefficient in wavelet:" + CSThreshold;
        text += "\nWavelet (" + ((waveletType == CompressedSensingProjector.WAVELET_BSPLINE) ? "B-spline" : "Orthogonal") + ") degree:" + waveletDegree + ", shift:" + waveletShift;
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
        String text = "CS_" + nbiterations + "ite_" + relaxationCoefficient + "rel_" + CSThreshold + "percent_(" + ((waveletType == CompressedSensingProjector.WAVELET_BSPLINE) ? "B-spline" : "Orthogonal") + "_" + waveletDegree + "_" + waveletShift + ")";
        return text;
    }

    public JPanel getJPanel() {
        if (firstDisplay) {
            addListeners();
            initValues();
        }
        return basePanel;

    }


    private void updatePlot() {
        this.plot.setColor(Color.blue, new Color(192, 192, 192));
        BasisFunctions basis = new BasisFunctions(256, 4);
        this.y = basis.computeFunction(this.waveletType, this.waveletDegree, this.waveletShift);
        double a = 48.0D;
        double b = -64.0D;
        switch (this.waveletType) {
            case 0:
                if (this.basisFunction) {
                    b = -32.0D;
                }
                break;
            case 3:
                if (this.basisFunction) {
                    b = -32.0D;
                    a = this.waveletDegree > 0.5D ? a * ((this.waveletDegree - 0.5D) / 8.0D + 1.0D) : a;
                } else if (this.waveletDegree < 0.0D) {
                    a = this.waveletDegree < -0.1D ? a * (1.0D + (this.waveletDegree + 0.1D) * 2.0D) : a;
                } else {
                    a = this.waveletDegree > 0.5D ? a * ((this.waveletDegree - 0.5D) / 2.0D + 1.0D) : a;
                }
                break;
            case 6:
                if (this.basisFunction) {
                    b = -32.0D;
                    a = this.waveletDegree > 0.5D ? a * (1.0D - (this.waveletDegree - 0.5D) / 6.0D) : a;
                } else {
                    a = this.waveletDegree > 0.0D ? a * (1.0D - this.waveletDegree / 5.5D) : a;
                }
        }

        this.plot.setScalingModeOnY(0, a, b / a);
        this.plot.setScalingModeOnX(1);
        double p = Math.pow(2.0D, 4.0D);
        int start = (int) Math.round(p * (this.xstart - 1.0D) + 128.0D);
        int end = (int) Math.round(p * (this.xend - 1.0D) + 128.0D);
        int nb = end - start;
        double[] yRange = new double[nb];
        double[] xRange = new double[nb];
        int index = this.basisFunction ? 0 : 1;

        for (int i = 0; i < yRange.length; ++i) {
            xRange[i] = this.x[i + start];
            yRange[i] = this.y[index][i + start];
        }

        this.plot.setXY(xRange, yRange);
    }


    private void createUIComponents() {
        spinnerRelaxationCoefficent = new JSpinner(new SpinnerNumberModel(0.1, 0.0001, 2, 0.01));
        spinnerCSThreshold = new JSpinner(new SpinnerNumberModel(0.95, 0.0001, 1, 0.001));
        spinnerWaveletDegree = new JSpinner(new SpinnerNumberModel(3.0, -0.5, 5, 0.01));
        spinnerWaveletShift = new JSpinner(new SpinnerNumberModel(0, -0.5, 0.5, 0.01));
        panelPlotXY = new JPanel();
        plot = new PlotXY(PLOT_XSIZE, PLOT_YSIZE);
        panelPlotXY.add(plot);
        updatePlot();
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
        basePanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "reconstruction parameters", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label1 = new JLabel();
        label1.setText("number of iterations");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerNbIteration = new JSpinner();
        panel1.add(spinnerNbIteration, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("relaxation coefficient");
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerRelaxationCoefficent, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resinOrCryoSampleCheckBox = new JCheckBox();
        resinOrCryoSampleCheckBox.setText("resin or cryo sample");
        panel1.add(resinOrCryoSampleCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        csLabel = new JLabel();
        csLabel.setText("Percentage of zeros");
        panel1.add(csLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerCSThreshold, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fistaOptimizationCheckBox = new JCheckBox();
        fistaOptimizationCheckBox.setText("Fista optimization");
        panel1.add(fistaOptimizationCheckBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        basePanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "wavelet parameters", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        panel2.add(spinnerWaveletDegree, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("degree");
        panel2.add(label3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("shift");
        panel2.add(label4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(spinnerWaveletShift, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxWaveletType = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("Orthonormal");
        defaultComboBoxModel1.addElement("B-Spline");
        comboBoxWaveletType.setModel(defaultComboBoxModel1);
        panel2.add(comboBoxWaveletType, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("type");
        panel2.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(panelPlotXY, new GridConstraints(0, 2, 3, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

}
