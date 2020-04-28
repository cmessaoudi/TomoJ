package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.tomoj.align.AffineAlignment;
import fr.curie.tomoj.align.Alignment;
import fr.curie.tomoj.gui.TiltSeriesPanel;
import ij.ImagePlus;
import ij.process.*;
import fr.curie.tomoj.tomography.TiltSeries;

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

public class AlignDualZeroTiltManual implements Application {
    private JSpinner spinnerRoiWidth;
    private JSpinner spinnerroiHeight;
    private JSpinner spinnerRotationValue;
    private JButton buttonRotationLeft;
    private JSpinner spinnerRotationIncrement;
    private JButton buttonRotationRight;
    private JCheckBox checkBoxDisplayCombined;
    private JPanel panelOptionDisplay;
    private JRadioButton superimposedMagentaGreenRadioButton;
    private JRadioButton differenceRadioButton;
    private JSpinner spinnerExpandTiltAxis;
    private JButton cancelButton;
    private JSpinner spinnerTranslationX;
    private JSpinner spinnerTranslationY;
    private JButton buttonTranslationUp;
    private JSpinner spinnerTranslationIncrement;
    private JButton buttonTranslationDown;
    private JButton buttonTranslationLeft;
    private JButton buttonTranslationRight;
    private JPanel rootPanel;
    private JButton previousButton;
    private JLabel labelReferenceTiltSeries;
    private JButton nextButton;
    private JLabel labelAligned;


    protected ArrayList<TiltSeriesPanel> tsList;
    protected boolean firstDisplay = true;
    protected boolean displayPreview = false;
    private ImagePlus preview = null;
    int refIndex = 0;
    int alignIndex = 1;
    int roiWidth, roiHeigth;

    static private Double[] possibleIncrements = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0};

    ArrayList<AffineTransform> originalTransforms;
    ArrayList<Double> tx;
    ArrayList<Double> ty;
    ArrayList<Double> rot;
    double translationIncrement = 1;
    double rotationIncrement = 1;

    ArrayList<AffineAlignment> alignments;


    public AlignDualZeroTiltManual(ArrayList<TiltSeriesPanel> tiltseries) {
        initTiltSeries(tiltseries);
        $$$setupUI$$$();

    }

    public void initTiltSeries(ArrayList<TiltSeriesPanel> tiltseries) {
        this.tsList = tiltseries;
        originalTransforms = new ArrayList<AffineTransform>(tsList.size());
        alignments = new ArrayList<>(tsList.size());
        tx = new ArrayList<Double>(tsList.size());
        ty = new ArrayList<Double>(tsList.size());
        rot = new ArrayList<Double>(tsList.size());
        for (int ts = 0; ts < tsList.size(); ts++) {
            AffineTransform tmp = tsList.get(ts).getTiltSeries().getAlignment().getZeroTransform();
            originalTransforms.add(new AffineTransform(tmp));
            tx.add(0.0);
            ty.add(0.0);
            rot.add(0.0);
            Alignment a = tsList.get(ts).getTiltSeries().getAlignment();
            if (!(a instanceof AffineAlignment)) {
                a = new AffineAlignment(tsList.get(ts).getTiltSeries());
                ((AffineAlignment) a).setZeroTransform(originalTransforms.get(ts));
            }
            alignments.add((AffineAlignment) a);
        }
        refIndex = 0;
        alignIndex = 1;
    }

    private void initValues() {
        labelReferenceTiltSeries.setText(refIndex + "_" + tsList.get(refIndex).getTiltSeries().getTitle());
        labelAligned.setText(alignIndex + "_" + tsList.get(alignIndex).getTiltSeries().getTitle());
        roiWidth = tsList.get(0).getTiltSeries().getWidth();
        roiHeigth = tsList.get(0).getTiltSeries().getHeight();
        ((SpinnerNumberModel) spinnerRoiWidth.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerRoiWidth.getModel()).setMaximum(tsList.get(0).getTiltSeries().getWidth());
        ((SpinnerNumberModel) spinnerRoiWidth.getModel()).setValue(tsList.get(0).getTiltSeries().getWidth());
        ((SpinnerNumberModel) spinnerRoiWidth.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerroiHeight.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerroiHeight.getModel()).setMaximum(tsList.get(0).getTiltSeries().getHeight());
        ((SpinnerNumberModel) spinnerroiHeight.getModel()).setValue(tsList.get(0).getTiltSeries().getHeight());
        ((SpinnerNumberModel) spinnerroiHeight.getModel()).setStepSize(1);


    }

    private void addListeners() {
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for (int index = 0; index < tsList.size(); index++) {
                    alignments.get(index).setZeroTransform(originalTransforms.get(index));

                }
                updatePreview();
            }
        });


        previousButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                alignIndex--;
                if (alignIndex < 1) alignIndex = 1;

                labelAligned.setText(alignIndex + "_" + tsList.get(alignIndex).getTiltSeries().getTitle());
                setDisplayPreview(true);
                updatePreview();
            }
        });
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                alignIndex++;
                if (alignIndex >= tsList.size()) alignIndex = tsList.size() - 1;

                labelAligned.setText(alignIndex + "_" + tsList.get(alignIndex).getTiltSeries().getTitle());
                setDisplayPreview(true);
                updatePreview();
            }
        });

        spinnerRoiWidth.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                roiWidth = ((SpinnerNumberModel) spinnerRoiWidth.getModel()).getNumber().intValue();
                setDisplayPreview(true);
                updatePreview();
            }
        });
        spinnerroiHeight.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                roiHeigth = ((SpinnerNumberModel) spinnerroiHeight.getModel()).getNumber().intValue();
                setDisplayPreview(true);
                updatePreview();
            }
        });


        spinnerTranslationX.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                tx.set(alignIndex, ((SpinnerNumberModel) spinnerTranslationX.getModel()).getNumber().doubleValue());
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
                updatePreview();
            }
        });

        spinnerTranslationY.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                ty.set(alignIndex, ((SpinnerNumberModel) spinnerTranslationY.getModel()).getNumber().doubleValue());
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
                updatePreview();
            }
        });
        spinnerRotationValue.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                rot.set(alignIndex, ((SpinnerNumberModel) spinnerRotationValue.getModel()).getNumber().doubleValue());
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
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
                tx.set(alignIndex, tx.get(alignIndex) - translationIncrement);
                spinnerTranslationX.setValue(tx.get(alignIndex));
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
                updatePreview();
            }
        });
        buttonTranslationRight.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tx.set(alignIndex, tx.get(alignIndex) + translationIncrement);
                spinnerTranslationX.setValue(tx.get(alignIndex));
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
                updatePreview();
            }
        });
        buttonTranslationUp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ty.set(alignIndex, ty.get(alignIndex) - translationIncrement);
                spinnerTranslationY.setValue(ty.get(alignIndex));
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
                updatePreview();
            }
        });

        buttonTranslationDown.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ty.set(alignIndex, ty.get(alignIndex) + translationIncrement);
                spinnerTranslationY.setValue(ty.get(alignIndex));
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
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
                rot.set(alignIndex, rot.get(alignIndex) - rotationIncrement);
                spinnerRotationValue.setValue(rot.get(alignIndex));
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
                updatePreview();
            }
        });
        buttonRotationRight.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                rot.set(alignIndex, rot.get(alignIndex) + rotationIncrement);
                spinnerRotationValue.setValue(rot.get(alignIndex));
                AffineTransform tmp = new AffineTransform(originalTransforms.get(alignIndex));
                tmp.translate(tx.get(alignIndex), ty.get(alignIndex));
                tmp.rotate(Math.toRadians(rot.get(alignIndex)));

                alignments.get(alignIndex).setZeroTransform(tmp);
                setDisplayPreview(true);
                updatePreview();
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
                setDisplayPreview(true);
                updatePreview();
            }
        });

        differenceRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setDisplayPreview(true);
                updatePreview();
            }
        });


        rootPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                System.out.println("key released:" + e.getKeyCode());

                //System.out.println("down:" + KeyEvent.VK_DOWN);
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    buttonTranslationDown.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    buttonTranslationUp.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    buttonTranslationLeft.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    buttonTranslationRight.doClick();
                }
                updatePreview();

            }
        });

    }

    public boolean run() {
        return false;
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
            setDisplayPreview(true);
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
        TiltSeries ts1 = tsList.get(refIndex).getTiltSeries();
        TiltSeries ts2 = tsList.get(alignIndex).getTiltSeries();

        ts1.setAlignmentRoi(roiWidth, roiHeigth);
        ts2.setAlignmentRoi(roiWidth, roiHeigth);

        float[] mov = ts2.getPixelsForAlignment(ts2.getZeroIndex());
        float[] ref = ts1.getPixelsForAlignment(ts2.getZeroIndex());

        ImageProcessor result;
        if (differenceRadioButton.isSelected()) {
            result = new FloatProcessor(roiWidth, roiHeigth, mov, null);
            result.copyBits(new FloatProcessor(roiWidth, roiHeigth, ref, null), 0, 0, Blitter.DIFFERENCE);

        } else {
            FloatProcessor fp1 = new FloatProcessor(roiWidth, roiHeigth, mov, null);
            fp1.setMinAndMax(tsList.get(refIndex).getTiltSeries().getProcessor().getMin(), tsList.get(refIndex).getTiltSeries().getProcessor().getMax());
            FloatProcessor fp2 = new FloatProcessor(roiWidth, roiHeigth, ref, null);
            fp2.setMinAndMax(tsList.get(alignIndex).getTiltSeries().getProcessor().getMin(), tsList.get(alignIndex).getTiltSeries().getProcessor().getMax());

            ByteProcessor mB = (ByteProcessor) fp1.convertToByte(true);
            ByteProcessor rB = (ByteProcessor) fp2.convertToByte(true);
            result = new ColorProcessor(roiWidth, roiHeigth);
            ((ColorProcessor) result).setRGB((byte[]) mB.getPixels(), (byte[]) rB.getPixels(), (byte[]) mB.getPixels());
        }
        if (preview == null || roiWidth != preview.getWidth() || roiHeigth != preview.getHeight() || !preview.isVisible()) {
            if (preview != null) {
                preview.hide();
            }
            preview = new ImagePlus("combined image " + refIndex + "---" + alignIndex + ")", result);
            preview.show();
            preview.getWindow().setLocationRelativeTo(ts1.getWindow());
            //preview.getWindow().setLocationByPlatform(true);
            preview.getWindow().addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    System.out.println("key released:" + e.getKeyCode());
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
            System.out.println("update existing preview " + refIndex + " --- " + alignIndex);
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
        rootPanel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
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
        final Spacer spacer1 = new Spacer();
        rootPanel.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1, true, false));
        rootPanel.add(panel2, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Rotation"));
        final JLabel label3 = new JLabel();
        label3.setText("value in degrees");
        panel2.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel2.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        panel2.add(spinnerRotationValue, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonRotationLeft = new JButton();
        buttonRotationLeft.setIcon(new ImageIcon(getClass().getResource("/arrow_rotate_anticlockwise.png")));
        buttonRotationLeft.setText("");
        panel2.add(buttonRotationLeft, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(spinnerRotationIncrement, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonRotationRight = new JButton();
        buttonRotationRight.setIcon(new ImageIcon(getClass().getResource("/arrow_rotate_clockwise.png")));
        buttonRotationRight.setText("");
        panel2.add(buttonRotationRight, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel3, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Combine images"));
        checkBoxDisplayCombined = new JCheckBox();
        checkBoxDisplayCombined.setSelected(true);
        checkBoxDisplayCombined.setText("Display combined image");
        panel3.add(checkBoxDisplayCombined, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelOptionDisplay = new JPanel();
        panelOptionDisplay.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panelOptionDisplay, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        superimposedMagentaGreenRadioButton = new JRadioButton();
        superimposedMagentaGreenRadioButton.setSelected(true);
        superimposedMagentaGreenRadioButton.setText("superimposed (magenta/green)");
        panelOptionDisplay.add(superimposedMagentaGreenRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        differenceRadioButton = new JRadioButton();
        differenceRadioButton.setSelected(false);
        differenceRadioButton.setText("difference");
        panelOptionDisplay.add(differenceRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panelOptionDisplay.add(spacer3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        cancelButton = new JButton();
        cancelButton.setText("Cancel");
        rootPanel.add(cancelButton, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel4.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Translation"));
        final JLabel label4 = new JLabel();
        label4.setText("(TX,TY)");
        panel4.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel4.add(spinnerTranslationX, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel4.add(spinnerTranslationY, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel4.add(panel5, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonTranslationUp = new JButton();
        buttonTranslationUp.setIcon(new ImageIcon(getClass().getResource("/arrow_up.png")));
        buttonTranslationUp.setText("");
        panel5.add(buttonTranslationUp, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel5.add(spinnerTranslationIncrement, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonTranslationDown = new JButton();
        buttonTranslationDown.setIcon(new ImageIcon(getClass().getResource("/arrow_down.png")));
        buttonTranslationDown.setText("");
        panel5.add(buttonTranslationDown, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonTranslationLeft = new JButton();
        buttonTranslationLeft.setIcon(new ImageIcon(getClass().getResource("/arrow_left.png")));
        buttonTranslationLeft.setText("");
        panel5.add(buttonTranslationLeft, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonTranslationRight = new JButton();
        buttonTranslationRight.setIcon(new ImageIcon(getClass().getResource("/arrow_right.png")));
        buttonTranslationRight.setText("");
        panel5.add(buttonTranslationRight, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel6, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel6.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "tilt series choice"));
        previousButton = new JButton();
        previousButton.setText("Previous");
        panel6.add(previousButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        labelReferenceTiltSeries = new JLabel();
        labelReferenceTiltSeries.setText("Label");
        panel6.add(labelReferenceTiltSeries, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Aligned");
        panel6.add(label5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Reference");
        panel6.add(label6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        labelAligned = new JLabel();
        labelAligned.setText("Label");
        panel6.add(labelAligned, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        nextButton = new JButton();
        nextButton.setText("Next");
        panel6.add(nextButton, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
