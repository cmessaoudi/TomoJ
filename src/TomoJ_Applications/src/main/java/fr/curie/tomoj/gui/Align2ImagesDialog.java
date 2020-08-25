package fr.curie.tomoj.gui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;

public class Align2ImagesDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel translationPanel;
    private JSpinner spinnerTx;
    private JSpinner spinnerTy;
    private JButton tyUpButton;
    private JButton txRightButton;
    private JSpinner spinnerTranslationIncrement;
    private JButton txLeftButton;
    private JButton tyDownButton;
    private JPanel rotationPanel;
    private JSpinner spinnerRotationValue;
    private JButton rot1Button;
    private JSpinner spinnerRotationIncrement;
    private JButton rot2Button;
    private JButton automaticButton;
    private JButton usePointsButton;

    ImagePlus image1, image2, image2Ali, imageCombine;
    boolean wasCanceled = false;
    boolean active=false;


    static private Double[] possibleIncrements = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0};

    public Align2ImagesDialog(ImagePlus ip1, ImagePlus ip2) {
        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(false);
        getRootPane().setDefaultButton(buttonOK);

        image1 = ip1;
        image2 = ip2;
        //image1.show();
        //image2.show();
        image2Ali = new ImagePlus("aligned", image2.getProcessor());
        image2Ali.show();
        imageCombine = new ImagePlus("combined", new ColorProcessor(image1.getWidth(), image1.getHeight()));
        imageCombine.show();
        active=true;
        System.out.println("constructor active:"+active);

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
        spinnerTx.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updatePreviews();
            }
        });
        spinnerTy.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updatePreviews();
            }
        });

        spinnerRotationValue.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updatePreviews();
            }
        });
        txLeftButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((SpinnerNumberModel) spinnerTx.getModel()).setValue(((SpinnerNumberModel) spinnerTx.getModel()).getNumber().doubleValue() - (Double) ((SpinnerListModel) spinnerTranslationIncrement.getModel()).getValue());
            }
        });
        txRightButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((SpinnerNumberModel) spinnerTx.getModel()).setValue(((SpinnerNumberModel) spinnerTx.getModel()).getNumber().doubleValue() + (Double) ((SpinnerListModel) spinnerTranslationIncrement.getModel()).getValue());
            }
        });
        tyUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((SpinnerNumberModel) spinnerTy.getModel()).setValue(((SpinnerNumberModel) spinnerTy.getModel()).getNumber().doubleValue() - (Double) ((SpinnerListModel) spinnerTranslationIncrement.getModel()).getValue());
            }
        });
        tyDownButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((SpinnerNumberModel) spinnerTy.getModel()).setValue(((SpinnerNumberModel) spinnerTy.getModel()).getNumber().doubleValue() + (Double) ((SpinnerListModel) spinnerTranslationIncrement.getModel()).getValue());
            }
        });
        rot1Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((SpinnerNumberModel) spinnerRotationValue.getModel()).setValue(((SpinnerNumberModel) spinnerRotationValue.getModel()).getNumber().doubleValue() - (Double) ((SpinnerListModel) spinnerRotationIncrement.getModel()).getValue());
            }
        });
        rot2Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((SpinnerNumberModel) spinnerRotationValue.getModel()).setValue(((SpinnerNumberModel) spinnerRotationValue.getModel()).getNumber().doubleValue() + (Double) ((SpinnerListModel) spinnerRotationIncrement.getModel()).getValue());
            }
        });
    }


    private void onOK() {
        System.out.println("OK");
        image2Ali.close();
        imageCombine.close();
        setVisible(false);
        active=false;
        dispose();
        wasCanceled = false;
    }

    private void onCancel() {
        System.out.println("Cancel");
        image2Ali.close();
        imageCombine.close();
        setVisible(false);
        dispose();
        wasCanceled = true;
        active=false;
    }

    public boolean wasCanceled() {
        return wasCanceled;
    }

    public boolean isActive(){
        System.out.println("is active:"+active);
        return active;}


    public void updatePreviews() {
        if (image2Ali == null) {
            image2Ali = new ImagePlus("aligned", image2.getProcessor());
            image2Ali.show();
        }

        double tx = ((SpinnerNumberModel) spinnerTx.getModel()).getNumber().doubleValue();
        double ty = ((SpinnerNumberModel) spinnerTy.getModel()).getNumber().doubleValue();
        double rot = ((SpinnerNumberModel) spinnerRotationValue.getModel()).getNumber().doubleValue();
        ImageProcessor ip = image2.getProcessor().duplicate();
        ip.rotate(rot);
        ip.translate(tx, ty);
        image2Ali.setProcessor(ip);
        image2Ali.updateAndRepaintWindow();

        ColorProcessor cp = (ColorProcessor) imageCombine.getProcessor();
        cp.setChannel(1, image1.getProcessor().convertToByteProcessor());
        cp.setChannel(3, image1.getProcessor().convertToByteProcessor());
        cp.setChannel(2, image2Ali.getProcessor().convertToByteProcessor());
        imageCombine.updateAndRepaintWindow();


    }

    public double getTranslationX() {
        return ((SpinnerNumberModel) spinnerTx.getModel()).getNumber().doubleValue();
    }

    public double getTranslationY() {
        return ((SpinnerNumberModel) spinnerTy.getModel()).getNumber().doubleValue();
    }

    public double getRotation() {
        return ((SpinnerNumberModel) spinnerRotationValue.getModel()).getNumber().doubleValue();
    }


    private void createUIComponents() {

        spinnerTranslationIncrement = new JSpinner(new SpinnerListModel(possibleIncrements));
        spinnerTranslationIncrement.setValue(possibleIncrements[possibleIncrements.length - 1]);
        spinnerRotationIncrement = new JSpinner(new SpinnerListModel(possibleIncrements));
        spinnerRotationIncrement.setValue(possibleIncrements[possibleIncrements.length - 1]);
        spinnerTx = new JSpinner(new SpinnerNumberModel(0.0, -512.0, 512.0, 1.0));
        spinnerTy = new JSpinner(new SpinnerNumberModel(0.0, -512.0, 512.0, 1.0));
        spinnerRotationValue = new JSpinner(new SpinnerNumberModel(0, -360.0, 360.0, 0.5));
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
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        translationPanel = new JPanel();
        translationPanel.setLayout(new GridLayoutManager(4, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(translationPanel, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Translation");
        translationPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(134, 21), null, 0, false));
        translationPanel.add(spinnerTx, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        translationPanel.add(spinnerTy, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tyUpButton = new JButton();
        tyUpButton.setIcon(new ImageIcon(getClass().getResource("/arrow_up.png")));
        tyUpButton.setText("");
        translationPanel.add(tyUpButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txRightButton = new JButton();
        txRightButton.setIcon(new ImageIcon(getClass().getResource("/arrow_right.png")));
        txRightButton.setText("");
        translationPanel.add(txRightButton, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        translationPanel.add(spinnerTranslationIncrement, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txLeftButton = new JButton();
        txLeftButton.setIcon(new ImageIcon(getClass().getResource("/arrow_left.png")));
        txLeftButton.setText("");
        translationPanel.add(txLeftButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(134, 32), null, 0, false));
        tyDownButton = new JButton();
        tyDownButton.setIcon(new ImageIcon(getClass().getResource("/arrow_down.png")));
        tyDownButton.setText("");
        translationPanel.add(tyDownButton, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rotationPanel = new JPanel();
        rotationPanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(rotationPanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("rotation");
        rotationPanel.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rotationPanel.add(spinnerRotationValue, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rot1Button = new JButton();
        rot1Button.setIcon(new ImageIcon(getClass().getResource("/arrow_rotate_anticlockwise.png")));
        rot1Button.setText("");
        rotationPanel.add(rot1Button, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rotationPanel.add(spinnerRotationIncrement, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rot2Button = new JButton();
        rot2Button.setIcon(new ImageIcon(getClass().getResource("/arrow_rotate_clockwise.png")));
        rot2Button.setText("");
        rotationPanel.add(rot2Button, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        automaticButton = new JButton();
        automaticButton.setText("automatic");
        panel3.add(automaticButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        usePointsButton = new JButton();
        usePointsButton.setText("use points");
        panel3.add(usePointsButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
