package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import ij.IJ;
import ij.io.FileInfo;
import fr.curie.tomoj.SuperTomoJPoints;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.landmarks.AlignmentLandmarksOptions;
import fr.curie.tomoj.landmarks.Landmarks3DDualAlign;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * Application for dual-axes landmarks-based alignment.
 *
 * @author by Antoine Cossa on 22/03/2019.
 */
public class DualAlignWithLandmarks implements Application {
    protected boolean firstDisplay = true;
    private boolean oldAlgo = false;

    protected Landmarks3DDualAlign alignator;

    private SuperTomoJPoints stp;

    private TiltSeries ts1;
    private TiltSeries ts2;
    private TomoJPoints tp1;
    private TomoJPoints tp2;

    private double oldRotation = 0;
    private boolean oldCorrectHeight = true;


    private boolean newExhaustiveSearch = true;
    private double newMahalanobisWeight = 0;
    private boolean newShift = true;
    private boolean newMag = false;
    private boolean newShrink = false;
    private boolean newScaleX = false;
    private boolean newShear = false;
    private boolean newRotation = false;
    private int newCycleNumber = -1;
    private double newSelectionThreshold = 8;
    //    private double newSelectionThreshold = 3; // TEST Antoine
    private boolean newCorrectHeight = true;


    private JPanel basePanel;
    private JComboBox comboBoxAlgo;
    private JPanel panelOld;
    private JSpinner spinnerOldRotationMax;
    private JCheckBox correctOldHeightCheckBox;
    private JPanel panelDeform;
    private JCheckBox exhaustiveSearchCheckBox;
    private JSpinner spinnerNewMahalanobisWeight;
    private JCheckBox shiftsCheckBox;
    private JCheckBox magnificationCheckBox;
    private JCheckBox shrinkageCheckBox;
    private JCheckBox rotationInPlaneCheckBox;
    private JCheckBox scaleXCheckBox;
    private JCheckBox shearCheckBox;
    private JSpinner spinnerNewCycleNumber;
    private JSpinner spinnerNewSelectionThreshold;
    private JCheckBox correctNewHeightCheckBox;
    private JPanel basePane;
    protected Chrono time;
    String resultString;

//    public DualAlignWithLandmarks(TiltSeries ts1, TomoJPoints tp1, TiltSeries ts2, TomoJPoints tp2) {
//        this.ts1 = ts1;
//        this.ts2 = ts2;
//        this.tp1 = tp1;
//        this.tp2 = tp2;
//
//        time = new Chrono();
//        $$$setupUI$$$();
//        correctNewHeightCheckBox.setVisible(false);
//        if (alignator == null) {
//            alignator = new Landmarks3DDualAlign(tp1, tp2);
//            System.out.println("Create align");
//        }
//        newExhaustiveSearch = exhaustiveSearchCheckBox.isSelected();
//        newMahalanobisWeight = ((Number) spinnerNewMahalanobisWeight.getValue()).doubleValue();
//        newShift = shiftsCheckBox.isSelected();
//         newMag = magnificationCheckBox.isSelected();
//        newShrink = shrinkageCheckBox.isSelected();
//        newScaleX = scaleXCheckBox.isSelected();
//        newShear = shearCheckBox.isSelected();
//        newRotation = rotationInPlaneCheckBox.isSelected();
//        newCycleNumber = ((Number) spinnerNewCycleNumber.getValue()).intValue();
//        newSelectionThreshold = ((Number) spinnerNewSelectionThreshold.getValue()).doubleValue();
//        newCorrectHeight = correctNewHeightCheckBox.isSelected();
//
//        oldCorrectHeight = correctOldHeightCheckBox.isSelected();
//        oldRotation = ((Number) spinnerOldRotationMax.getValue()).doubleValue();
//        tp1.setLandmarks3DDual(alignator);
//        tp2.setLandmarks3DDual(alignator);
//
//    }

    public DualAlignWithLandmarks(SuperTomoJPoints stp) {
        this.stp = stp;
        tp1 = stp.getTomoJPoints(0);
        tp2 = stp.getTomoJPoints(1);
        ts1 = tp1.getTiltSeries();
        ts2 = tp2.getTiltSeries();

        time = new Chrono();
        $$$setupUI$$$();
        correctNewHeightCheckBox.setVisible(false);
        if (alignator == null) {
            alignator = new Landmarks3DDualAlign(stp);
            System.out.println("Create align");
        }
        newExhaustiveSearch = exhaustiveSearchCheckBox.isSelected();
        newMahalanobisWeight = ((Number) spinnerNewMahalanobisWeight.getValue()).doubleValue();
        newShift = shiftsCheckBox.isSelected();
        newMag = magnificationCheckBox.isSelected();
        newShrink = shrinkageCheckBox.isSelected();
        newScaleX = scaleXCheckBox.isSelected();
        newShear = shearCheckBox.isSelected();
        newRotation = rotationInPlaneCheckBox.isSelected();
        newCycleNumber = ((Number) spinnerNewCycleNumber.getValue()).intValue();
        newSelectionThreshold = ((Number) spinnerNewSelectionThreshold.getValue()).doubleValue();
        newCorrectHeight = correctNewHeightCheckBox.isSelected();

        oldCorrectHeight = correctOldHeightCheckBox.isSelected();
        oldRotation = ((Number) spinnerOldRotationMax.getValue()).doubleValue();
        //tp1.setLandmarks3DDual(alignator);
        //tp2.setLandmarks3DDual(alignator);

    }

    protected void addListeners() {
        comboBoxAlgo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                switch (comboBoxAlgo.getSelectedIndex()) {
                    case 0:
                        panelOld.setVisible(true);
                        panelDeform.setVisible(false);
                        oldAlgo = true;
                        break;
                    case 1:
                    default:
                        panelOld.setVisible(false);
                        panelDeform.setVisible(true);
                        oldAlgo = false;
                }
            }
        });
        comboBoxAlgo.setSelectedIndex(1);
        spinnerOldRotationMax.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                oldRotation = ((Number) spinnerOldRotationMax.getValue()).doubleValue();
                System.out.println("change old rotation to : " + oldRotation);
            }
        });
        correctOldHeightCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                oldCorrectHeight = correctOldHeightCheckBox.isSelected();
            }
        });
        exhaustiveSearchCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newExhaustiveSearch = exhaustiveSearchCheckBox.isSelected();
            }
        });
        shiftsCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newShift = shiftsCheckBox.isSelected();
            }
        });
        magnificationCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newMag = magnificationCheckBox.isSelected();
            }
        });
        shrinkageCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newShrink = shrinkageCheckBox.isSelected();
            }
        });
        scaleXCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newScaleX = scaleXCheckBox.isSelected();
            }
        });
        shearCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newShear = shearCheckBox.isSelected();
            }
        });
        rotationInPlaneCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newRotation = rotationInPlaneCheckBox.isSelected();
            }
        });
        correctNewHeightCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newCorrectHeight = correctNewHeightCheckBox.isSelected();
            }
        });
        spinnerNewMahalanobisWeight.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                newMahalanobisWeight = ((Number) spinnerNewMahalanobisWeight.getValue()).doubleValue();
            }
        });
        spinnerNewCycleNumber.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                newCycleNumber = ((Number) spinnerNewCycleNumber.getValue()).intValue();
            }
        });
        spinnerNewSelectionThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                newSelectionThreshold = ((Number) spinnerNewSelectionThreshold.getValue()).doubleValue();
            }
        });
    }

    public boolean run() {
//        ts1.updatePoint();
//        ts2.updatePoint();
//
//        for (int j = tp1.getNumberOfPoints() - 1; j >= 0; j--) {
//            Point2D[] tmppts = tp1.getPoints(j);
//            int count = 0;
//            for (Point2D tmppt : tmppts) {
//                if (tmppt != null) count++;
//            }
//            if (count == 0) {
//                System.out.println("remove landmark at index " + j + " from tp1: it has no defined points");
//                tp1.removeSetOfPoints(j);
//            }
//        }
//
//        for (int j = tp2.getNumberOfPoints() - 1; j >= 0; j--) {
//            Point2D[] tmppts = tp2.getPoints(j);
//            int count = 0;
//            for (Point2D tmppt : tmppts) {
//                if (tmppt != null) count++;
//            }
//            if (count == 0) {
//                System.out.println("remove landmark at index " + j + " from tp2: it has no defined points");
//                tp2.removeSetOfPoints(j);
//            }
//        }

        int i = 1; // TomoJPoints counter
        for (TomoJPoints tp : stp.getAllTP()) {
            tp.getTiltSeries().updatePoint();
            for (int j = tp.getNumberOfPoints() - 1; j >= 0; j--) {
                Point2D[] tmppts = tp.getPoints(j);
                int count = 0;
                for (Point2D tmppt : tmppts) {
                    if (tmppt != null) count++;
                }
                if (count == 0) {
                    System.out.println("remove landmark at index " + j + " from tp" + i + " : it has no defined points");
                    tp.removeSetOfPoints(j);
                }
            }
            i++;
        }

        final AlignmentLandmarksOptions options = new AlignmentLandmarksOptions();
        options.setExhaustiveSearch(newExhaustiveSearch);
        options.setMahalanobisWeight(newMahalanobisWeight);
        options.setAllowShifts(newShift);
        options.setDeformShrinkage(newShrink);
        options.setDeformMagnification(newMag);
        options.setDeformScalingX(newScaleX);
        options.setDeformDelta(newShear);
        options.setAllowInPlaneRotation(newRotation);
        options.setNumberOfCycles(newCycleNumber);
        options.setK(newSelectionThreshold);
        options.setCorrectForHeight(newCorrectHeight);

        // Debug options
//        final AlignmentLandmarksOptions options = new AlignmentLandmarksOptions();
//        options.setExhaustiveSearch(true);
//        options.setMahalanobisWeight(0);
//        options.setAllowShifts(true);
//        options.setDeformShrinkage(true);
//        options.setDeformMagnification(true);
//        options.setDeformScalingX(true);
//        options.setDeformDelta(true);
//        options.setAllowInPlaneRotation(true);
//        options.setNumberOfCycles(-1);
//        options.setK(8);
//        options.setCorrectForHeight(false);


        OutputStreamCapturer outputCapture = new OutputStreamCapturer();
        final Chrono time = new Chrono(1);
        time.start();


//        double score = (oldAlgo) ? alignator.Align3DLandmarks(0, 5, true, oldRotation, oldCorrectHeight) : alignator.dualAlign3DLandmarksWithDeformation(options);
        double score = alignator.dualAlign3DLandmarksWithDeformation(options);
        if (!oldAlgo) {
            double[] errors = alignator.getBestAlignment().computeErrors();
            System.out.println("average worst error is : " + errors[0]);
            System.out.println("average of average error is : " + errors[1]);
        }
        time.stop();
        System.out.println("total time: " + time.delayString());
        resultString = outputCapture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();

        if (tp1.getCurrentIndex() >= tp1.getNumberOfPoints()) {
            tp1.setCurrentIndex(tp1.getNumberOfPoints() - 1);
        }
        if (tp2.getCurrentIndex() >= tp2.getNumberOfPoints()) {
            tp2.setCurrentIndex(tp2.getNumberOfPoints() - 1);
        }

        ts1.setRoi(tp1.getRoi(ts1.getCurrentSlice() - 1));
        ts1.setSlice(ts1.getCurrentSlice());
        ts1.updateAndDraw();
        ts1.threadStats();
        FileInfo fi1 = ts1.getOriginalFileInfo();
        ts2.setRoi(tp2.getRoi(ts2.getCurrentSlice() - 1));
        ts2.setSlice(ts2.getCurrentSlice());
        ts2.updateAndDraw();
        ts2.threadStats();
//        FileInfo fi2 = ts2.getOriginalFileInfo();
        if (fi1 == null) {
            //System.out.println("original File Info null");
            fi1 = ts1.getFileInfo();
        }
        String imageDir = (fi1 != null && fi1.directory != null && !fi1.directory.equalsIgnoreCase("")) ? fi1.directory : IJ.getDirectory("current");
        if (imageDir == null) imageDir = ".";
        if (!imageDir.endsWith(System.getProperty("file.separator")))
            imageDir += System.getProperty("file.separator");
        if (oldAlgo) {
            alignator.getAlignmentOld().saveRjs(imageDir + ts1.getTitle() + "_Old_score_" + score + "_rjs.txt");
        } else {
            alignator.getBestAlignment().saveRjs(imageDir + ts1.getTitle() + "_score_" + score + "_rjs.txt");
        }
        IJ.showStatus("align 3D landmarks finished");
        //String param = "maxInPlaneRotation=" + maxrot + " correctHeight=" + correctHeight;
        //ts1.getTomoJPoints().setLandmarks3DDual(alignator);
        //ts2.getTomoJPoints().setLandmarks3DDual(alignator);

        return true;
    }

    public void setParameters(Object... parameters) {
        for (int index = 0; index < parameters.length; index++) {
            if (((String) parameters[index]).toLowerCase().equals("shifts")) {
                if (parameters[index + 1] instanceof String)
                    newShift = Boolean.parseBoolean((String) parameters[index + 1]);
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("magnification")) {
                if (parameters[index + 1] instanceof String)
                    newMag = Boolean.parseBoolean((String) parameters[index + 1]);
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("shrink")) {
                if (parameters[index + 1] instanceof String)
                    newShrink = Boolean.parseBoolean((String) parameters[index + 1]);
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("shear")) {
                if (parameters[index + 1] instanceof String)
                    newShear = Boolean.parseBoolean((String) parameters[index + 1]);
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("rotation")) {
                if (parameters[index + 1] instanceof String)
                    newRotation = Boolean.parseBoolean((String) parameters[index + 1]);
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("scalex")) {
                if (parameters[index + 1] instanceof String)
                    newScaleX = Boolean.parseBoolean((String) parameters[index + 1]);
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("exhaustive")) {
                if (parameters[index + 1] instanceof String)
                    newExhaustiveSearch = Boolean.parseBoolean((String) parameters[index + 1]);
                index += 1;
            }
        }
    }

    public String name() {
        return "Align using landmarks";
    }

    public String help() {
        return "";
    }

    public ArrayList<Object> getResults() {
        ArrayList<Object> result = new ArrayList<>();
        result.add(resultString);
        result.add(alignator);
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
            //initValues();
            addListeners();
            //updatePreview();
            firstDisplay = false;
        }
        return this.basePanel;
    }

    public void interrupt() {
        alignator.interrupt();
    }

    public double getCompletion() {
        return alignator.getCompletion() * 100;
    }

    public String getParametersValuesAsString() {
        String params = "";
        if (oldAlgo) {
            params += "old algorithm:\nmax rotation: " + oldRotation + "\ncorrect height: " + oldCorrectHeight + "\n";
        } else {
            params += "new algorithm:\nexhautive search: " + newExhaustiveSearch +
                    "\nmahalanobis weight: " + newMahalanobisWeight +
                    "\ncorrect \nshifts: " + newShift +
                    "\nmagnification: " + newMag +
                    "\nshrinkage: " + newShrink +
                    "\nscale X:" + newScaleX +
                    "\nshear: " + newShear + "\nrotation: " + newRotation +
                    "\nnumber of cycles: " + newCycleNumber + "\tthreshold: " + newSelectionThreshold +
                    "\ncorrect height: " + newCorrectHeight;
        }
        return params;
    }

    public void setDisplayPreview(boolean display) {

    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        spinnerOldRotationMax = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 360.0, 0.1));
        spinnerNewMahalanobisWeight = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 2.0, 0.01));
        spinnerNewSelectionThreshold = new JSpinner(new SpinnerNumberModel(8.0, 0.0, 360.0, 0.1));
        spinnerNewCycleNumber = new JSpinner(new SpinnerNumberModel(-1, -1, 500, 1));


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
        basePanel.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(-4473925)), "Align Using Landmarks", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        comboBoxAlgo = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("classic");
        defaultComboBoxModel1.addElement("with deformation");
        comboBoxAlgo.setModel(defaultComboBoxModel1);
        basePanel.add(comboBoxAlgo, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelOld = new JPanel();
        panelOld.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panelOld, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Maximum in plane rotation (degrees)");
        panelOld.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelOld.add(spinnerOldRotationMax, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        correctOldHeightCheckBox = new JCheckBox();
        correctOldHeightCheckBox.setSelected(true);
        correctOldHeightCheckBox.setText("Correct height");
        panelOld.add(correctOldHeightCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelDeform = new JPanel();
        panelDeform.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panelDeform, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        exhaustiveSearchCheckBox = new JCheckBox();
        exhaustiveSearchCheckBox.setSelected(true);
        exhaustiveSearchCheckBox.setText("Exhaustive search");
        panelDeform.add(exhaustiveSearchCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Mahalanobis weight");
        panelDeform.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Correct:");
        panelDeform.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rotationInPlaneCheckBox = new JCheckBox();
        rotationInPlaneCheckBox.setSelected(true);
        rotationInPlaneCheckBox.setText("Rotation in plane");
        panelDeform.add(rotationInPlaneCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panelDeform.add(panel1, new GridConstraints(5, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        final JLabel label4 = new JLabel();
        label4.setText("Bad Landmarks selection ");
        panel1.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Cycles number");
        panel1.add(label5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerNewCycleNumber, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Threshold");
        panel1.add(label6, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerNewSelectionThreshold, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelDeform.add(panel2, new GridConstraints(3, 1, 2, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        magnificationCheckBox = new JCheckBox();
        magnificationCheckBox.setSelected(true);
        magnificationCheckBox.setText("Magnification");
        panel2.add(magnificationCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        shrinkageCheckBox = new JCheckBox();
        shrinkageCheckBox.setSelected(true);
        shrinkageCheckBox.setText("Shrinkage");
        panel2.add(shrinkageCheckBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scaleXCheckBox = new JCheckBox();
        scaleXCheckBox.setSelected(true);
        scaleXCheckBox.setText("Scale X");
        panel2.add(scaleXCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        shearCheckBox = new JCheckBox();
        shearCheckBox.setSelected(true);
        shearCheckBox.setText("Shear");
        panel2.add(shearCheckBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        shiftsCheckBox = new JCheckBox();
        shiftsCheckBox.setSelected(true);
        shiftsCheckBox.setText("Shifts");
        panelDeform.add(shiftsCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelDeform.add(spinnerNewMahalanobisWeight, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        correctNewHeightCheckBox = new JCheckBox();
        correctNewHeightCheckBox.setEnabled(false);
        correctNewHeightCheckBox.setSelected(false);
        correctNewHeightCheckBox.setText("Correct height of sample");
        panelDeform.add(correctNewHeightCheckBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panelDeform.add(spacer2, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        basePanel.add(spacer3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

}
