package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.tomoj.gui.TiltSeriesPanel;
import ij.Prefs;
import fr.curie.tomoj.SuperTomoJPoints;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.tomoj.tomography.projectors.Projector;
import fr.curie.tomoj.tomography.projectors.VoxelProjector3D;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class ADOSSARTApplication extends ReconstructionApplication {
    private JSpinner spinnerIterations;
    private JSpinner spinnerRelaxationCoefficient;
    private JSpinner spinnerUpdate;
    private JCheckBox resinOrCryoSampleCheckBox;
    private JCheckBox positivityConstraintCheckBox;
    private JCheckBox saveErrorVolumesCheckBox;
    private JCheckBox saveErrorVolumeAllIterationsCheckBox;
    private JSpinner spinnerAlternate;
    private JPanel basePanel;


    protected boolean firstDisplay;
    protected ArrayList<TiltSeriesPanel> tsList;
    protected int nbIteration = 10;
    protected double relaxationCoefficient = 0.1;
    protected int update = 1;
    protected int alternate = 2;
    protected boolean longObjectCompensation = true;
    protected boolean positivityConstraint = false;
    protected boolean saveErrorVolume = false;
    protected boolean saveErrorVolumeAll = false;
    //protected boolean computeOnGPU;
    String resultString = "iterative reconstruction";
    //ResolutionEstimation resolutionComputation;

    SuperTomoJPoints stp;


    public ADOSSARTApplication(ArrayList<TiltSeriesPanel> tsList) {
        this.tsList = tsList;
        firstDisplay = true;
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
        alternate = (int) Prefs.get("TOMOJ_alternateADOSSART.int", alternate);
        longObjectCompensation = Prefs.get("TOMOJ_SampleType.bool", longObjectCompensation);


        spinnerIterations.setValue(nbIteration);
        spinnerRelaxationCoefficient.setValue(relaxationCoefficient);
        spinnerUpdate.setValue(update);
        spinnerAlternate.setValue(alternate);
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
        spinnerAlternate.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                alternate = ((Number) spinnerAlternate.getValue()).intValue();
            }
        });

    }

    public void setSuperTomoJPoints(SuperTomoJPoints stp) {
        this.stp = stp;
    }

    public boolean run() {
        tsList.get(0).getTiltSeries().setAlignMethodForReconstruction(TiltSeries.ALIGN_PROJECTOR);
        tsList.get(1).getTiltSeries().setAlignMethodForReconstruction(TiltSeries.ALIGN_PROJECTOR);
        TiltSeries ts1 = tsList.get(0).getTiltSeries();
        TiltSeries ts2 = tsList.get(1).getTiltSeries();

        rec = new TomoReconstruction2(ts1.getWidth(), ts1.getHeight(), 300);
        rec.show();

//                ProjectorGPU proj1 = new VoxelProjector3DGPU(ts1, rec, GPUDevice.getBestDevice(), null);
//                proj1.initForIterative(0, ts1.getHeight(), 1, ProjectorGPU.ONE_KERNEL_PROJ_DIFF, false);
//                ProjectorGPU proj2 = new VoxelProjector3DGPU(ts2, rec, GPUDevice.getBestDevice(), null);
//                proj1.initForIterative(0, ts1.getHeight(), 1, ProjectorGPU.ONE_KERNEL_PROJ_DIFF, false, proj1);

        Projector proj1 = new VoxelProjector3D(ts1, rec, null);
        Projector proj2 = new VoxelProjector3D(ts2, rec, null);

        rec.ADOSSART(ts1, proj1, ts2, proj2, nbIteration, relaxationCoefficient, TomoReconstruction2.ALL_PROJECTIONS, 0, ts1.getHeight());
        return true;
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
        basePanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Iterative reconstruction Options", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label1 = new JLabel();
        label1.setText("number of iterations");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
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
        panel1.add(resinOrCryoSampleCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        positivityConstraintCheckBox = new JCheckBox();
        positivityConstraintCheckBox.setText("positivity constraint");
        panel1.add(positivityConstraintCheckBox, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveErrorVolumesCheckBox = new JCheckBox();
        saveErrorVolumesCheckBox.setText("save error volumes");
        panel1.add(saveErrorVolumesCheckBox, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveErrorVolumeAllIterationsCheckBox = new JCheckBox();
        saveErrorVolumeAllIterationsCheckBox.setEnabled(false);
        saveErrorVolumeAllIterationsCheckBox.setText("for all iterations");
        panel1.add(saveErrorVolumeAllIterationsCheckBox, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("number of projection before changing axis");
        panel1.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerAlternate = new JSpinner();
        panel1.add(spinnerAlternate, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        basePanel.add(spacer2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

}
