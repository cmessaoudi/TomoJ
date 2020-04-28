package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.tomoj.align.AffineAlignment;
import ij.ImagePlus;
import ij.process.*;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

public class ManualAlignmentApplication implements Application {
    private JSpinner spinnerRoiWidth;
    private JSpinner spinnerroiHeight;
    private JSpinner spinnerTranslationX;
    private JSpinner spinnerTranslationY;
    private JButton buttonTranslationUp;
    private JButton buttonTranslationLeft;
    private JButton buttonTranslationRight;
    private JButton buttonTranslationDown;
    private JSpinner spinnerTranslationIncrement;
    private JSpinner spinnerRotationValue;
    private JButton buttonRotationLeft;
    private JSpinner spinnerRotationIncrement;
    private JButton buttonRotationRight;
    private JCheckBox checkBoxDisplayCombined;
    private JRadioButton superimposedMagentaGreenRadioButton;
    private JRadioButton differenceRadioButton;
    private JCheckBox expandImagesCheckBox;
    private JSpinner spinnerExpandTiltAxis;
    private JButton cancelButton;
    private JPanel rootPanel;
    private JPanel panelOptionDisplay;
    private JButton previousButton;
    private JButton nextButton;
    private JSpinner spinnerImageNumber;

    protected TiltSeries ts;
    protected boolean firstDisplay = true;
    protected boolean displayPreview = false;
    private ImagePlus preview = null;
    int previewIndex = 0;
    int roiWidth, roiHeigth;

    static private Double[] possibleIncrements = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0};

    AffineTransform[] originalTransforms;
    double[] tx;
    double[] ty;
    double[] rot;
    double translationIncrement = 1;
    double rotationIncrement = 1;


    public ManualAlignmentApplication(TiltSeries tiltseries) {
        this.ts = tiltseries;
        $$$setupUI$$$();
        AffineTransform[] tmp = ts.getAlignment().getTransforms();
        if (!(ts.getAlignment() instanceof AffineAlignment)) {
            AffineAlignment ali = new AffineAlignment(ts);
            ts.setAlignment(ali);
            for (int i = 0; i < tmp.length; i++) {
                ali.setTransform(i, new AffineTransform(tmp[i]));
            }
            ali.convertTolocalTransform();
        }
        originalTransforms = new AffineTransform[tmp.length];
        for (int i = 0; i < tmp.length; i++) {
            originalTransforms[i] = new AffineTransform(tmp[i]);
        }
        tx = new double[ts.getImageStackSize()];
        ty = new double[ts.getImageStackSize()];
        rot = new double[ts.getImageStackSize()];


    }

    private void initValues() {
        roiWidth = ts.getWidth();
        roiHeigth = ts.getHeight();
        ((SpinnerNumberModel) spinnerRoiWidth.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerRoiWidth.getModel()).setMaximum(ts.getWidth());
        ((SpinnerNumberModel) spinnerRoiWidth.getModel()).setValue(ts.getWidth());
        ((SpinnerNumberModel) spinnerRoiWidth.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerroiHeight.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerroiHeight.getModel()).setMaximum(ts.getHeight());
        ((SpinnerNumberModel) spinnerroiHeight.getModel()).setValue(ts.getHeight());
        ((SpinnerNumberModel) spinnerroiHeight.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerImageNumber.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerImageNumber.getModel()).setMaximum(ts.getWidth());
        ((SpinnerNumberModel) spinnerImageNumber.getModel()).setValue(1);
        ((SpinnerNumberModel) spinnerImageNumber.getModel()).setStepSize(1);


    }

    private void addListeners() {
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                AffineAlignment align = (AffineAlignment) ts.getAlignment();
                for (int i = 0; i < originalTransforms.length; i++) {
                    align.setTransform(i, new AffineTransform(originalTransforms[i]));
                }
                updatePreview();
            }
        });

        spinnerRoiWidth.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                roiWidth = ((SpinnerNumberModel) spinnerRoiWidth.getModel()).getNumber().intValue();
                displayPreview = true;
                updatePreview();
            }
        });
        spinnerroiHeight.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                roiHeigth = ((SpinnerNumberModel) spinnerroiHeight.getModel()).getNumber().intValue();
                displayPreview = true;
                updatePreview();
            }
        });


        spinnerTranslationX.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                tx[previewIndex - 1] = ((SpinnerNumberModel) spinnerTranslationX.getModel()).getNumber().doubleValue();
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });

        spinnerTranslationY.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                ty[previewIndex - 1] = ((SpinnerNumberModel) spinnerTranslationY.getModel()).getNumber().doubleValue();
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });
        spinnerRotationValue.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                rot[previewIndex - 1] = ((SpinnerNumberModel) spinnerRotationValue.getModel()).getNumber().doubleValue();
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });

        spinnerTranslationIncrement.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                translationIncrement = (Double) spinnerTranslationIncrement.getValue();
            }
        });

        buttonTranslationLeft.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tx[previewIndex - 1] -= translationIncrement;
                spinnerTranslationX.setValue(tx[previewIndex - 1]);
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });
        buttonTranslationRight.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tx[previewIndex - 1] += translationIncrement;
                spinnerTranslationX.setValue(tx[previewIndex - 1]);
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });
        buttonTranslationUp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ty[previewIndex - 1] -= translationIncrement;
                spinnerTranslationY.setValue(ty[previewIndex - 1]);
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });

        buttonTranslationDown.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ty[previewIndex - 1] += translationIncrement;
                spinnerTranslationY.setValue(ty[previewIndex - 1]);
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });

        spinnerRotationIncrement.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                rotationIncrement = (Double) spinnerRotationIncrement.getValue();
            }
        });

        buttonRotationLeft.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                rot[previewIndex - 1] -= rotationIncrement;
                spinnerRotationValue.setValue(rot[previewIndex - 1]);
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });
        buttonRotationRight.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                rot[previewIndex - 1] += rotationIncrement;
                spinnerRotationValue.setValue(rot[previewIndex - 1]);
                AffineTransform tmp = new AffineTransform(originalTransforms[previewIndex - 1]);
                tmp.translate(tx[previewIndex - 1], ty[previewIndex - 1]);
                tmp.rotate(Math.toRadians(rot[previewIndex - 1]));

                ((AffineAlignment) ts.getAlignment()).setTransform(previewIndex - 1, tmp);
                displayPreview = true;
                updatePreview();
            }
        });

        spinnerImageNumber.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                previewIndex = ((SpinnerNumberModel) spinnerImageNumber.getModel()).getNumber().intValue();
                spinnerTranslationX.setValue(tx[previewIndex - 1]);
                spinnerTranslationY.setValue(ty[previewIndex - 1]);
                spinnerRotationValue.setValue(rot[previewIndex - 1]);
                displayPreview = true;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        });

        previousButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                if (previewIndex <= 0) previewIndex = 1;
                spinnerImageNumber.setValue(previewIndex);
                spinnerTranslationX.setValue(tx[previewIndex - 1]);
                spinnerTranslationY.setValue(ty[previewIndex - 1]);
                spinnerRotationValue.setValue(rot[previewIndex - 1]);
                displayPreview = true;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        });

        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                if (previewIndex >= ts.getImageStackSize()) previewIndex = ts.getImageStackSize() - 1;
                spinnerImageNumber.setValue(previewIndex);
                spinnerTranslationX.setValue(tx[previewIndex - 1]);
                spinnerTranslationY.setValue(ty[previewIndex - 1]);
                spinnerRotationValue.setValue(rot[previewIndex - 1]);
                displayPreview = true;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        });
        checkBoxDisplayCombined.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setDisplayPreview(checkBoxDisplayCombined.isSelected());
                updatePreview();
                panelOptionDisplay.setVisible(checkBoxDisplayCombined.isSelected());

            }
        });

        superimposedMagentaGreenRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setDisplayPreview(checkBoxDisplayCombined.isSelected());
                displayPreview = true;
                updatePreview();
                panelOptionDisplay.setVisible(checkBoxDisplayCombined.isSelected());
            }
        });

        differenceRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updatePreview();
            }
        });

        spinnerExpandTiltAxis.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                ts.setTiltAxis((Double) spinnerExpandTiltAxis.getValue());
                updatePreview();
            }
        });

        expandImagesCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((AffineAlignment) ts.getAlignment()).expandForAlignment(expandImagesCheckBox.isSelected());
                updatePreview();
            }
        });
        rootPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                System.out.println("key released:" + e.getKeyCode());
                int index = (Integer) spinnerImageNumber.getModel().getValue();
                //System.out.println("down:" + KeyEvent.VK_DOWN);
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    buttonTranslationDown.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    buttonTranslationUp.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    buttonTranslationLeft.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    buttonTranslationRight.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                    previousButton.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                    nextButton.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_1) {
                    previousButton.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_2) {
                    nextButton.doClick();
                }
                updatePreview();
                ts.setSlice(previewIndex);

            }
        });

    }

    public boolean run() {
        return true;
    }

    public void setParameters(Object... parameters) {

    }

    public String help() {
        return null;
    }

    public String name() {
        return null;
    }

    public ArrayList<Object> getResults() {
        return null;
    }

    public ArrayList<Object> getParametersType() {
        return null;
    }

    public ArrayList<String> getParametersName() {
        return null;
    }

    public String getParametersValuesAsString() {
        return null;
    }

    /**
     * get a JPanel with all the parameters accessible for easy creation of GUI
     *
     * @return
     */
    public JPanel getJPanel() {
        if (firstDisplay) {
            System.out.println("first display");
            initValues();
            addListeners();
            updatePreview();
            firstDisplay = false;
        }
        updatePreview();
        return rootPanel;
    }

    public void interrupt() {

    }

    public double getCompletion() {
        return 0;
    }

    public void setDisplayPreview(boolean display) {
        displayPreview = display;
        if (!displayPreview && preview != null && preview.isVisible()) {
            preview.close();
            preview = null;
        }
    }

    public void updatePreview() {
        //System.out.println("update preview");
        if (!displayPreview) return;
        //int sx = (roi) ? roiWidth : ts.getWidth();
        //int sy = (roi) ? roiHeigth : ts.getHeight();

        ts.setAlignmentRoi(roiWidth, roiHeigth);
        if (previewIndex <= 1) previewIndex = 1;


        float[] mov = ts.getPixelsForAlignment(previewIndex);
        float[] ref = ts.getPixelsForAlignment(previewIndex - 1);

        ImageProcessor result;
        if (differenceRadioButton.isSelected()) {
            result = new FloatProcessor(roiWidth, roiHeigth, mov, null);
            result.copyBits(new FloatProcessor(roiWidth, roiHeigth, ref, null), 0, 0, Blitter.DIFFERENCE);

        } else {

            ByteProcessor mB = (ByteProcessor) new FloatProcessor(roiWidth, roiHeigth, mov, null).convertToByte(true);
            ByteProcessor rB = (ByteProcessor) new FloatProcessor(roiWidth, roiHeigth, ref, null).convertToByte(true);
            result = new ColorProcessor(roiWidth, roiHeigth);
            ((ColorProcessor) result).setRGB((byte[]) mB.getPixels(), (byte[]) rB.getPixels(), (byte[]) mB.getPixels());
        }
        if (preview == null || roiWidth != preview.getWidth() || roiHeigth != preview.getHeight() || !preview.isVisible()) {
            if (preview != null) {
                preview.hide();
            }
            preview = new ImagePlus("combined image " + previewIndex + "(" + ts.getTiltAngle(previewIndex) + ")", result);
            preview.show();
            preview.getWindow().setLocationRelativeTo(ts.getWindow());
            //preview.getWindow().setLocationByPlatform(true);
            preview.getWindow().addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    System.out.println("key released:" + e.getKeyCode());
                    int index = (Integer) spinnerImageNumber.getModel().getValue();
                    //System.out.println("down:" + KeyEvent.VK_DOWN);
                    if (e.getKeyCode() == KeyEvent.VK_F6) {
                        buttonTranslationDown.doClick();
                    } else if (e.getKeyCode() == KeyEvent.VK_F5) {
                        buttonTranslationUp.doClick();
                    } else if (e.getKeyCode() == KeyEvent.VK_F7) {
                        buttonTranslationLeft.doClick();
                    } else if (e.getKeyCode() == KeyEvent.VK_F8) {
                        buttonTranslationRight.doClick();
                    } else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                        previousButton.doClick();
                    } else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                        nextButton.doClick();
                    } else if (e.getKeyCode() == KeyEvent.VK_F11) {
                        previousButton.doClick();
                    } else if (e.getKeyCode() == KeyEvent.VK_F12) {
                        nextButton.doClick();
                    }
                    //updatePreview();
                    //ts.setSlice(previewIndex);

                }
            });
        } else {
            System.out.println("update existing preview " + previewIndex);
            preview.setProcessor(result);
            //autoAdjust(preview);
            //preview.resetDisplayRange();
            preview.updateAndRepaintWindow();
        }
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        spinnerTranslationIncrement = new JSpinner(new SpinnerListModel(possibleIncrements));
        spinnerTranslationIncrement.setValue(possibleIncrements[possibleIncrements.length - 1]);
        translationIncrement = possibleIncrements[possibleIncrements.length - 1];
        spinnerRotationIncrement = new JSpinner(new SpinnerListModel(possibleIncrements));
        spinnerRotationIncrement.setValue(possibleIncrements[2]);
        rotationIncrement = possibleIncrements[2];
        spinnerTranslationX = new JSpinner(new SpinnerNumberModel(0.0, -512.0, 512.0, 1.0));
        spinnerTranslationY = new JSpinner(new SpinnerNumberModel(0.0, -512.0, 512.0, 1.0));
        spinnerRotationValue = new JSpinner(new SpinnerNumberModel(0, -360.0, 360.0, 0.5));
        spinnerExpandTiltAxis = new JSpinner(new SpinnerNumberModel(0, -180.0, 180.0, 0.1));
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
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Roi centered"));
        final JLabel label1 = new JLabel();
        label1.setText("width");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("height");
        panel1.add(label2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerRoiWidth = new JSpinner();
        panel1.add(spinnerRoiWidth, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerroiHeight = new JSpinner();
        panel1.add(spinnerroiHeight, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Translation"));
        final JLabel label3 = new JLabel();
        label3.setText("(TX,TY)");
        panel2.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(spinnerTranslationX, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(spinnerTranslationY, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel2.add(panel3, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonTranslationUp = new JButton();
        buttonTranslationUp.setIcon(new ImageIcon(getClass().getResource("/arrow_up.png")));
        buttonTranslationUp.setText("");
        panel3.add(buttonTranslationUp, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel3.add(spinnerTranslationIncrement, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonTranslationDown = new JButton();
        buttonTranslationDown.setIcon(new ImageIcon(getClass().getResource("/arrow_down.png")));
        buttonTranslationDown.setText("");
        panel3.add(buttonTranslationDown, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonTranslationLeft = new JButton();
        buttonTranslationLeft.setIcon(new ImageIcon(getClass().getResource("/arrow_left.png")));
        buttonTranslationLeft.setText("");
        panel3.add(buttonTranslationLeft, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonTranslationRight = new JButton();
        buttonTranslationRight.setIcon(new ImageIcon(getClass().getResource("/arrow_right.png")));
        buttonTranslationRight.setText("");
        panel3.add(buttonTranslationRight, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cancelButton = new JButton();
        cancelButton.setText("Cancel");
        rootPanel.add(cancelButton, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel4, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        panel4.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Combine images"));
        checkBoxDisplayCombined = new JCheckBox();
        checkBoxDisplayCombined.setSelected(true);
        checkBoxDisplayCombined.setText("Display combined image");
        panel4.add(checkBoxDisplayCombined, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelOptionDisplay = new JPanel();
        panelOptionDisplay.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(panelOptionDisplay, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        superimposedMagentaGreenRadioButton = new JRadioButton();
        superimposedMagentaGreenRadioButton.setSelected(true);
        superimposedMagentaGreenRadioButton.setText("superimposed (magenta/green)");
        panelOptionDisplay.add(superimposedMagentaGreenRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        differenceRadioButton = new JRadioButton();
        differenceRadioButton.setSelected(false);
        differenceRadioButton.setText("difference");
        panelOptionDisplay.add(differenceRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        expandImagesCheckBox = new JCheckBox();
        expandImagesCheckBox.setText("expand images");
        panelOptionDisplay.add(expandImagesCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panelOptionDisplay.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        panelOptionDisplay.add(spinnerExpandTiltAxis, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("tilt axis");
        panelOptionDisplay.add(label4, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1, true, false));
        rootPanel.add(panel5, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel5.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Rotation"));
        final JLabel label5 = new JLabel();
        label5.setText("value in degrees");
        panel5.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel5.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        panel5.add(spinnerRotationValue, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonRotationLeft = new JButton();
        buttonRotationLeft.setIcon(new ImageIcon(getClass().getResource("/arrow_rotate_anticlockwise.png")));
        buttonRotationLeft.setText("");
        panel5.add(buttonRotationLeft, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel5.add(spinnerRotationIncrement, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonRotationRight = new JButton();
        buttonRotationRight.setIcon(new ImageIcon(getClass().getResource("/arrow_rotate_clockwise.png")));
        buttonRotationRight.setText("");
        panel5.add(buttonRotationRight, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        rootPanel.add(spacer3, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1, true, false));
        rootPanel.add(panel6, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        previousButton = new JButton();
        previousButton.setText("Previous");
        panel6.add(previousButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerImageNumber = new JSpinner();
        panel6.add(spinnerImageNumber, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        nextButton = new JButton();
        nextButton.setText("Next");
        panel6.add(nextButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(superimposedMagentaGreenRadioButton);
        buttonGroup.add(differenceRadioButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }

}
