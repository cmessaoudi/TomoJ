package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import ij.IJ;
import ij.Prefs;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.utils.Chrono;
import fr.curie.tomoj.application.CriticalLandmarksGenerator;
import fr.curie.tomoj.application.FeatureTrackingGenerator;
import fr.curie.tomoj.application.GridLandmarksGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GenerateLandmarksDialog extends JDialog {
    protected TiltSeries ts;
    protected CriticalLandmarksGenerator localExtremaGenerator;
    protected GridLandmarksGenerator gridLandmarksGenerator;
    protected FeatureTrackingGenerator featureTrackingGenerator;

    final int GRID = 0;
    final int CRITICAL = 1;
    final int FEATURE = 2;

    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox comboBoxMethodChoice;
    private JPanel localExtremaPanel;
    private JPanel gridLandmarksPanel;
    private JPanel featureTrackingPanel;


    public GenerateLandmarksDialog(TiltSeries ts) {
        this.ts = ts;
        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(false);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

// call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        comboBoxMethodChoice.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                switch (comboBoxMethodChoice.getSelectedIndex()) {
                    case GRID:
                        gridLandmarksPanel.setVisible(true);
                        localExtremaPanel.setVisible(false);
                        localExtremaGenerator.setDisplayPreview(false);
                        featureTrackingPanel.setVisible(false);
                        featureTrackingGenerator.setDisplayPreview(false);
                        break;
                    case CRITICAL:
                        gridLandmarksPanel.setVisible(false);
                        localExtremaPanel.setVisible(true);
                        localExtremaGenerator.setDisplayPreview(true);
                        featureTrackingPanel.setVisible(false);
                        featureTrackingGenerator.setDisplayPreview(false);
                        break;
                    case FEATURE:
                        gridLandmarksPanel.setVisible(false);
                        localExtremaPanel.setVisible(false);
                        localExtremaGenerator.setDisplayPreview(false);
                        featureTrackingPanel.setVisible(true);
                        featureTrackingGenerator.setDisplayPreview(true);
                        break;
                }
                pack();
            }

        });

        comboBoxMethodChoice.setSelectedIndex(CRITICAL);
    }

    private void onOK() {
        final int totalComputeSteps = ts.getImageStackSize();
        final Chrono time = new Chrono(totalComputeSteps);
        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());

        time.start();
        final int method = comboBoxMethodChoice.getSelectedIndex();
        //gridLandmarksPanel.setVisible(false);
        //localExtremaPanel.setVisible(false);
        //featureTrackingPanel.setVisible(false);
        final Thread T = new Thread() {
            public void run() {
                switch (method) {
                    case GRID:
                        //gridLandmarksPanel.setVisible(false);
                        gridLandmarksGenerator.run();
                        break;
                    case CRITICAL:
                        //localExtremaPanel.setVisible(false);
                        localExtremaGenerator.run();
                        break;
                    case FEATURE:
                        //featureTrackingPanel.setVisible(false);
                        featureTrackingGenerator.run();
                        break;
                }
                time.stop();
                System.out.println("total time to generate landmarks : " + time.delayString());
                dispose();
            }
        };
        //Future f=exec.submit(T);
        T.start();
        final Thread progress = new
                Thread() {
                    public void run() {
                        ProgressMonitor toto = new ProgressMonitor(contentPane, "generate landmarks", "", 0, totalComputeSteps);
                        while (T.isAlive()) {
                            if (toto.isCanceled()) {
                                gridLandmarksGenerator.interrupt();
                                localExtremaGenerator.interrupt();
                                featureTrackingGenerator.interrupt();
                                ts.combineTransforms(true);
                                ts.applyTransforms(true);
                                //T.stop();
                                toto.close();
                                System.out.println("process interrupted");
                                IJ.showStatus("process interrupted");
                                dispose();
                            } else {
                                time.stop();
                                double completion = 0;
                                switch (method) {
                                    case GRID:
                                        completion = gridLandmarksGenerator.getCompletion();
                                        break;
                                    case CRITICAL:
                                        completion = localExtremaGenerator.getCompletion();
                                        break;
                                    case FEATURE:
                                    default:
                                        completion = featureTrackingGenerator.getCompletion();
                                }
                                toto.setProgress((int) completion);
                                String note = IJ.d2s(completion, 2) + "/" + totalComputeSteps;
                                if (completion > 0)
                                    note += " approximately " + time.remainString(completion) + " left";
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
        /*try {
            f.get();
        }catch (Exception e){e.printStackTrace();}  */

    }

    private void onCancel() {

        localExtremaPanel.setVisible(false);
        featureTrackingPanel.setVisible(false);
        dispose();
    }

    public String getParametersValuesAsString() {
        switch (comboBoxMethodChoice.getSelectedIndex()) {
            case GRID:
                return gridLandmarksGenerator.getParametersValuesAsString();
            case CRITICAL:
                return localExtremaGenerator.getParametersValuesAsString();
            case FEATURE:
                return featureTrackingGenerator.getParametersValuesAsString();

        }
        return "";
    }


    private void createUIComponents() {
        System.out.println("createUI generateLandmarksDialog");
        System.out.flush();
        localExtremaGenerator = new CriticalLandmarksGenerator(ts, ts.getTomoJPoints());
        localExtremaPanel = localExtremaGenerator.getJPanel();
        localExtremaPanel.setVisible(false);
        gridLandmarksGenerator = new GridLandmarksGenerator(ts, ts.getTomoJPoints());
        gridLandmarksPanel = gridLandmarksGenerator.getJPanel();
        gridLandmarksPanel.setVisible(false);
        featureTrackingGenerator = new FeatureTrackingGenerator(ts, ts.getTomoJPoints());
        featureTrackingPanel = featureTrackingGenerator.getJPanel();
        featureTrackingPanel.setVisible(false);

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
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        contentPane.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
        scrollPane1.setViewportView(panel3);
        comboBoxMethodChoice = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("Grid");
        defaultComboBoxModel1.addElement("Local Extrema");
        defaultComboBoxModel1.addElement("Feature Tracking");
        comboBoxMethodChoice.setModel(defaultComboBoxModel1);
        panel3.add(comboBoxMethodChoice, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        panel3.add(localExtremaPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel3.add(gridLandmarksPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel3.add(featureTrackingPanel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
