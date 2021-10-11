/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj.gui;

import fr.curie.eftemtomoj.EftemDataset;
import fr.curie.eftemtomoj.FilteredImage;
import fr.curie.eftemtomoj.ImageRegistration;
import fr.curie.eftemtomoj.ImageRegistration.Algorithm;
import fr.curie.eftemtomoj.ImageRegistration.Transform;
import fr.curie.eftemtomoj.Metrics.Metric;
import ij.io.OpenDialog;
import ij.io.SaveDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.concurrent.ExecutionException;

/**
 * @author Nick Aschman
 */
public class AlignmentPanel extends WizardPage {
    JComboBox algorithmComboBox;
    JComboBox metricComboBox;
    JButton autoAlignApplyButton;
    JComboBox referenceWindowComboBox;
    JButton resetButton;
    JButton startButton;
    JList transformList;
    JButton loadButton;
    JButton saveButton;
    JPanel panel1;
    JSpinner radiusSpinner;

    private FilteredImage[][] alignedImages;
    private boolean dataModified = false;
    //private Transform[][] transforms;

    public AlignmentPanel(WizardDialog dlg) {
        super(dlg, "ALIGNMENT_PAGE", "Align Energy-Filtered Images");

        $$$setupUI$$$();
        autoAlignApplyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    onAutoAlign();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });
        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                onDoManualRefinement();
            }
        });
        loadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                onLoadTransforms();
            }
        });
        saveButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                onSaveTransforms();
            }
        });
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                onResetTransforms();
            }
        });

        // Create list of alignment algorithms
        Algorithm[] alg = Algorithm.values();

        String[] algorithms = new String[alg.length];
        for (int i = 0; i < algorithms.length; i++)
            algorithms[i] = alg[i].name();

        // Create list of alignment metrics
        Metric[] mtr = Metric.values();

        String[] metrics = new String[mtr.length];
        for (int i = 0; i < metrics.length; i++)
            metrics[i] = mtr[i].name();

        algorithmComboBox.setModel(new DefaultComboBoxModel(algorithms));
        metricComboBox.setModel(new DefaultComboBoxModel(metrics));

        transformList.setModel(new DefaultListModel());
    }

    private void onAutoAlign() {
        // Get algorithm and metric
        final Metric metric = Metric.valueOf((String) metricComboBox.getSelectedItem());
        final Algorithm algorithm = Algorithm.valueOf((String) algorithmComboBox.getSelectedItem());

        // Get reference window index
        final EftemDataset ds = dialog.getCurrentDataset();
        final int refIndex = referenceWindowComboBox.getSelectedIndex();
        final double radius = (Double) radiusSpinner.getModel().getValue();
        System.out.println("radius used is: " + radius);

        // Align images
        final WizardApprentice worker = new WizardApprentice<Boolean>(dialog, "Aligning images using " + metric) {
            @Override
            protected Boolean doInBackground() throws Exception {
                ImageRegistration alignator = new ImageRegistration(algorithm, metric);
                alignator.addObserver(this);

                Transform[] transforms;
                setProgress(0);
                for (int i = 0; i < alignedImages.length; i++) {
                    transforms = alignator.alignSeries(ds.getImage(i, refIndex).getImageForAlignment(radius), alignedImages[i], radius);
                    updateProgress((i + 1.0) / alignedImages.length);
                    for (int j = 0; j < alignedImages[i].length; j++) {
                        alignedImages[i][j].applyTransform(transforms[j]);
                    }
                }

                return true;
            }
        };
        worker.go();

        try {
            worker.get();
        } catch (InterruptedException ex) {
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        } catch (ExecutionException ex) {
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        }

        // Set modified flag
        dataModified = true;
        updatedisplayOfTransforms();
    }

    private void updatedisplayOfTransforms() {
        final EftemDataset ds = dialog.getCurrentDataset();
        // Update list view and crop area bounds
        dialog.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        Transform transform;
        DefaultListModel model = (DefaultListModel) transformList.getModel();
        model.clear();
        model.addElement(String.format("%1$-20s%2$20s%3$20s", "Image", "X", "Y"));

        int x1, x2, y1, y2, vl, vs;

        for (int i = 0; i < alignedImages.length; i++) {
            x1 = 0;
            x2 = ds.getWidth();
            y1 = 0;
            y2 = ds.getHeight();

            for (int j = 0; j < alignedImages[i].length; j++) {
                transform = alignedImages[i][j].getTransform();
                model.addElement(String.format("%1$8d / %2$-9d%3$20.1f%4$20.1f", (j + 1), (i + 1), transform.getTranslateX(), transform.getTranslateY()));

                vl = (int) transform.getTranslateX();
                if (vl > 0 && vl > x1) x1 = vl;

                vs = vl + ds.getWidth();
                if (vl < 0 && vs < x2) x2 = vs;

                vl = (int) transform.getTranslateY();
                if (vl > 0 && vl > y1) y1 = vl;

                vs = vl + ds.getHeight();
                if (vl < 0 && vs < y2) y2 = vs;
            }

            ds.setMask(i, new Rectangle(x1, y1, x2 - x1, y2 - y1));
        }

        dialog.setCursor(Cursor.getDefaultCursor());
    }

    private void onDoManualRefinement() {
        //JOptionPane.showMessageDialog(dialog, "This feature has not been implemented yet", "Not Yet Implemented", JOptionPane.INFORMATION_MESSAGE);
        final int refIndex = referenceWindowComboBox.getSelectedIndex();
        final EftemDataset ds = dialog.getCurrentDataset();
        ManualAlignment dialog = new ManualAlignment(ds, refIndex);
        dialog.pack();
        dialog.setVisible(true);
        //get the transforms applied
        if (!dialog.wasCanceled()) {
            for (int i = 0; i < ds.getTiltCount(); i++) {
                for (int j = 0; j < ds.getWindowCount(); j++) {
                    Transform[][] transforms = dialog.getTransforms();
                    alignedImages[i][j].applyTransform(transforms[i][j]);
                }
            }
        }

        updatedisplayOfTransforms();
    }

    private void onLoadTransforms() {
        //JOptionPane.showMessageDialog(dialog, "This feature has not been implemented yet", "Not Yet Implemented", JOptionPane.INFORMATION_MESSAGE);
        OpenDialog od = new OpenDialog("open Landmarks file...", "");
        String dir = od.getDirectory();
        String name = od.getFileName();
        if (dir == null || name == null) {
            return;
        }
        try {
            BufferedReader in = new BufferedReader(new FileReader(dir + name));
            for (int i = 0; i < alignedImages.length; i++) {
                for (int j = 0; j < alignedImages[0].length; j++) {
                    String line = in.readLine();
                    String[] words = line.split("\\s+");
                    Transform t = new Transform(Double.valueOf(words[0]), Double.valueOf(words[1]));
                    alignedImages[i][j].resetTransform();
                    alignedImages[i][j].applyTransform(t);
                }
            }

            in.close();
        } catch (IOException ioe) {
            System.out.println("error loading transfomrs: " + ioe);
        }
        dataModified = true;
        updatedisplayOfTransforms();
    }

    private void onSaveTransforms() {
        //JOptionPane.showMessageDialog(dialog, "This feature has not been implemented yet", "Not Yet Implemented", JOptionPane.INFORMATION_MESSAGE);
        SaveDialog sd = new SaveDialog("save points as...", "eftem_transforms", ".txt");
        String dir = sd.getDirectory();
        String name = sd.getFileName();
        if (dir == null || name == null) {
            return;
        }
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(dir + name));
            for (int i = 0; i < alignedImages.length; i++) {
                for (int j = 0; j < alignedImages[0].length; j++) {
                    Transform t = alignedImages[i][j].getTransform();
                    if (t != null)
                        out.write("" + t.getTranslateX() + "\t" + t.getTranslateY() + "\n");
                    else
                        out.write("0\t0\n");
                }
            }
            out.close();
        } catch (Exception e) {
            System.out.println("error while saving transforms: " + e);
        }
    }

    private void onResetTransforms() {
        dialog.setCursor(new Cursor(Cursor.WAIT_CURSOR));

        // Clear list
        ((DefaultListModel) transformList.getModel()).clear();

        // Reset transforms
        for (int i = 0; i < alignedImages.length; i++) {
            for (int j = 0; j < alignedImages[i].length; j++) {
                alignedImages[i][j].resetTransform();
            }
        }

        // Reset masks
        dialog.getCurrentDataset().resetMasks();

        // Set modified flag
        dataModified = true;

        dialog.setCursor(Cursor.getDefaultCursor());
    }

    private void createUIComponents() {
        referenceWindowComboBox = new JComboBox();
        algorithmComboBox = new JComboBox();
        metricComboBox = new JComboBox();


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
        panel1 = new JPanel();
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(11, 4, new Insets(0, 0, 0, 0), -1, -1));
        final JLabel label1 = new JLabel();
        label1.setFont(new Font(label1.getFont().getName(), Font.BOLD, label1.getFont().getSize()));
        label1.setText("Automatic alignment");
        panel1.add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setFont(new Font(label2.getFont().getName(), Font.BOLD, label2.getFont().getSize()));
        label2.setText("Transforms");
        panel1.add(label2, new com.intellij.uiDesigner.core.GridConstraints(6, 0, 1, 4, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JSeparator separator1 = new JSeparator();
        panel1.add(separator1, new com.intellij.uiDesigner.core.GridConstraints(3, 0, 1, 4, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        autoAlignApplyButton = new JButton();
        autoAlignApplyButton.setText("Apply");
        panel1.add(autoAlignApplyButton, new com.intellij.uiDesigner.core.GridConstraints(1, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setFont(new Font(label3.getFont().getName(), Font.BOLD, label3.getFont().getSize()));
        label3.setText("Manual refinement");
        panel1.add(label3, new com.intellij.uiDesigner.core.GridConstraints(4, 0, 1, 3, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JSeparator separator2 = new JSeparator();
        panel1.add(separator2, new com.intellij.uiDesigner.core.GridConstraints(5, 0, 1, 4, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        resetButton = new JButton();
        resetButton.setText("Reset");
        panel1.add(resetButton, new com.intellij.uiDesigner.core.GridConstraints(10, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_SOUTH, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        startButton = new JButton();
        startButton.setText("Start");
        panel1.add(startButton, new com.intellij.uiDesigner.core.GridConstraints(4, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, new com.intellij.uiDesigner.core.GridConstraints(7, 0, 4, 3, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        transformList = new JList();
        transformList.setFont(UIManager.getFont("TextArea.font"));
        scrollPane1.setViewportView(transformList);
        loadButton = new JButton();
        loadButton.setText("Load...");
        panel1.add(loadButton, new com.intellij.uiDesigner.core.GridConstraints(7, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveButton = new JButton();
        saveButton.setText("Save...");
        panel1.add(saveButton, new com.intellij.uiDesigner.core.GridConstraints(8, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panel1.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(9, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new com.intellij.uiDesigner.core.GridConstraints(1, 1, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer2 = new com.intellij.uiDesigner.core.Spacer();
        panel2.add(spacer2, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        panel2.add(referenceWindowComboBox, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel3, new com.intellij.uiDesigner.core.GridConstraints(2, 1, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer3 = new com.intellij.uiDesigner.core.Spacer();
        panel3.add(spacer3, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        panel3.add(algorithmComboBox, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        metricComboBox = new JComboBox();
        panel3.add(metricComboBox, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

        radiusSpinner = new JSpinner(new SpinnerNumberModel(0.0, -50.0, 50.0, 0.10));
        panel3.add(radiusSpinner, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

        final JLabel label4 = new JLabel();
        label4.setText("Reference:");
        panel1.add(label4, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Method:");
        panel1.add(label5, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer4 = new com.intellij.uiDesigner.core.Spacer();
        panel1.add(spacer4, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        label4.setLabelFor(referenceWindowComboBox);
        label5.setLabelFor(algorithmComboBox);

        startButton.setEnabled(true);
        saveButton.setEnabled(true);
        loadButton.setEnabled(true);
    }

    @Override
    public JComponent getComponent() {
        return panel1;
    }

    @Override
    public boolean validate() {
        if (!dataModified) {
            return true;
        }

        // Save aligned stack
        dialog.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        dialog.getCurrentDataset().save("aligned");
        dialog.setCursor(Cursor.getDefaultCursor());

        return true;
    }

    @Override
    public void activate() {
        // Update list of windows to be used as reference for alignment
        EftemDataset ds = dialog.getCurrentDataset();

        if (ds != null) {
            String[] windows = new String[ds.getWindowCount()];
            for (int i = 0; i < windows.length; i++) {
                windows[i] = "Image " + (i + 1) + " (" + ds.getEnergyShift(i) + " eV)";
            }

            referenceWindowComboBox.setModel(new DefaultComboBoxModel(windows));

            // Update array of references to images to be aligned
            alignedImages = new FilteredImage[ds.getTiltCount()][ds.getWindowCount()];

            for (int i = 0; i < ds.getTiltCount(); i++) {
                alignedImages[i] = ds.getImages(i);
            }

        }

        dataModified = false;
    }
}
