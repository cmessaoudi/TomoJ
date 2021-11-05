package fr.curie.eftemtomoj.gui;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.eftemtomoj.EftemDataset;
import fr.curie.eftemtomoj.FilteredImage;
import fr.curie.eftemtomoj.ImageRegistration;
import fr.curie.eftemtomoj.utils.SubImage;
import ij.ImagePlus;
import ij.gui.HistogramWindow;
import ij.process.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;

public class ManualAlignment extends JDialog implements KeyListener, MouseInputListener, MouseWheelListener {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JSpinner spinnerTranslationIncrement;
    private JButton txLeftButton;
    private JButton txRightButton;
    private JButton tyUpButton;
    private JButton tyDownButton;
    private JSpinner spinnerRotationIncrement;
    private JButton rot1Button;
    private JButton rot2Button;
    private JSpinner spinnerTiltImage;
    private JButton previousImageButton;
    private JButton NextImageButton;
    private JPanel jPanelImageI;
    private JPanel jPanelImageHR;
    private JPanel jPanelImageHB;
    private JPanel jPanelImageComb;
    private JLabel imageILabel;
    private JLabel imageJRightLabel;
    private JLabel imageJBottomLabel;
    private JLabel combinedImageLabel;
    private JSplitPane splitPaneMajor;
    private JPanel menuPanel;
    private JSplitPane splitPaneTop;
    private JSplitPane splitPaneBottom;
    private JSplitPane splitPaneMinor;
    private JSpinner spinnerBining;
    private JPanel rightPanel;
    private JPanel okCancelMainPanel;
    private JSpinner spinnerTx;
    private JPanel translationPanel;
    private JSpinner spinnerTy;
    private JPanel rotationPanel;
    private JSpinner spinnerRotationValue;
    private JPanel downsamplingPanel;
    private JPanel tiltImagePanel;
    private JPanel EnergyImagePanel;
    private JSpinner EnergyImageSpinner;
    private JButton EnergyImagePreviousButton;
    private JButton EnergyImageNextButton;
    private JCheckBox normalizeImagesCheckBox;
    private JCheckBox invertReferenceCheckBox;
    private JRadioButton radioButtonSuperpose;
    private JRadioButton radioButtonDifference;
    private JLabel histogramLabel;
    private JButton applySameOnAllButton;
    private JPanel histogramPanel;
    private int refindex;
    private double[][] tx;
    private double[][] ty;
    private double[][] rot;
    private int displayWidth = 256;
    private int displayHeight = 256;
    private Double[] possibleIncrements = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0};
    private ImageProcessor imgRef;
    private Point mouseOrigin = null;
    private Point2D.Double ImageOrigin = new Point2D.Double(0, 0);
    private Point2D.Double bkpImgOri = null;
    private Point pointV = new Point(128, 128);
    private Point pointH = new Point(128, 128);
    private EftemDataset ds;
    private FilteredImage[] mappingImages;
    private int tiltindex;
    private ArrayList<String> energies;
    private ArrayList<String> tilts;
    private boolean wasCanceled = true;
    HistogramWindow hw;

    public ManualAlignment(EftemDataset dataset, int refIndex) {
        this.ds = dataset;
        refindex = refIndex;
        tiltindex = 0;
        mappingImages = ds.getImages(tiltindex);
        energies = new ArrayList<String>();
        for (int i = 0; i < mappingImages.length; i++) {
            String tmp = mappingImages[i].getEnergyShift() + "eV";
            if (i == refindex) tmp = "*" + tmp + "*";
            energies.add(tmp);
        }

        tilts = new ArrayList<String>();
        boolean angle = (ds.getTiltAngle(0) != ds.getTiltAngle(1));
        for (int i = 0; i < ds.getTiltCount(); i++) {
            String tmp = (angle) ? ds.getTiltAngle(i) + "�" : "" + i;
            tilts.add(tmp);
        }

        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        spinnerTiltImage.getModel().setValue(tilts.get(tiltindex));
        //((SpinnerNumberModel) spinnerTiltImage.getModel()).setMaximum(ds.getTiltCount()-1);
        //((SpinnerNumberModel) spinnerTiltImage.getModel()).setMinimum(0);
        EnergyImageSpinner.getModel().setValue(energies.get(0));
        // ((SpinnerNumberModel) EnergyImageSpinner.getModel()).setMaximum(mappingImages.length-1);
        // ((SpinnerNumberModel) EnergyImageSpinner.getModel()).setMinimum(0);
        tx = new double[ds.getWindowCount()][ds.getTiltCount()];
        ty = new double[ds.getWindowCount()][ds.getTiltCount()];
        rot = new double[ds.getWindowCount()][ds.getTiltCount()];
        setImages(0, refindex);
        pack();
        splitPaneMajor.setResizeWeight(0);
        splitPaneMinor.setResizeWeight(0.5);
        splitPaneTop.setResizeWeight(0.5);
        splitPaneBottom.setResizeWeight(0.5);
        spinnerTranslationIncrement.setValue(possibleIncrements[3]);
        spinnerRotationIncrement.setValue(possibleIncrements[2]);


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


        spinnerTiltImage.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent ce) {
                tiltindex = tilts.indexOf(spinnerTiltImage.getValue());
                mappingImages = ds.getImages(tiltindex);
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                setImages(nrjindex, refindex);
                spinnerTx.setValue(tx[nrjindex][tiltindex]);
                spinnerTy.setValue(ty[nrjindex][tiltindex]);
            }
        });
        NextImageButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                if (index + 1 < ds.getTiltCount())
                    spinnerTiltImage.setValue(tilts.get(index + 1));
            }
        });
        previousImageButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                if (index - 1 >= 0)
                    spinnerTiltImage.setValue(tilts.get(index - 1));
            }
        });
        EnergyImageSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent ce) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                setImages(nrjindex, refindex);
                spinnerTx.setValue(tx[nrjindex][index]);
                spinnerTy.setValue(ty[nrjindex][index]);
            }
        });
        EnergyImageNextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                if (index + 1 < mappingImages.length)
                    EnergyImageSpinner.setValue(energies.get(index + 1));
            }
        });
        EnergyImagePreviousButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                if (index - 1 >= 0)
                    EnergyImageSpinner.setValue(energies.get(index - 1));
            }
        });


        txRightButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                addTranslation(index, (Double) ((SpinnerListModel) spinnerTranslationIncrement.getModel()).getValue(), 0);
                //addTranslation(index, ((SpinnerNumberModel) spinnerTranslationIncrement.getModel()).getNumber().doubleValue(), 0);
                //tx[index] += ((SpinnerNumberModel) spinnerTranslationIncrement.getModel()).getNumber().doubleValue();
                //setImages(index, index - refindex);
            }
        });
        txLeftButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                addTranslation(index, -(Double) ((SpinnerListModel) spinnerTranslationIncrement.getModel()).getValue(), 0);

                //addTranslation(index, -((SpinnerNumberModel) spinnerTranslationIncrement.getModel()).getNumber().doubleValue(), 0);
                //tx[index] -= ((SpinnerNumberModel) spinnerTranslationIncrement.getModel()).getNumber().doubleValue();
                //setImages(index, index - refindex);
            }
        });
        tyUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                addTranslation(index, 0, -(Double) ((SpinnerListModel) spinnerTranslationIncrement.getModel()).getValue());
                //addTranslation(index, 0, -((SpinnerNumberModel) spinnerTranslationIncrement.getModel()).getNumber().doubleValue());
                //ty[index] -= ((SpinnerNumberModel) spinnerTranslationIncrement.getModel()).getNumber().doubleValue();
                //setImages(index, index - refindex);
            }
        });
        tyDownButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                addTranslation(index, 0, (Double) spinnerTranslationIncrement.getModel().getValue());
                //addTranslation(index, 0, ((SpinnerNumberModel) spinnerTranslationIncrement.getModel()).getNumber().doubleValue());
                // ty[index] += ((SpinnerNumberModel) spinnerTranslationIncrement.getModel()).getNumber().doubleValue();
                //setImages(index, index - refindex);
            }
        });
        menuPanel.setFocusable(true);
        menuPanel.addKeyListener(this);

        imageILabel.addMouseListener(this);
        imageILabel.addMouseMotionListener(this);
        imageILabel.addMouseWheelListener(this);
        imageILabel.addKeyListener(this);
        imageILabel.setFocusable(true);
        // imageILabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        imageJBottomLabel.addMouseListener(this);
        imageJBottomLabel.addMouseMotionListener(this);
        imageJBottomLabel.addMouseWheelListener(this);
        imageJBottomLabel.addKeyListener(this);
        imageJBottomLabel.setFocusable(true);
        // imageJBottomLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        imageJRightLabel.addMouseListener(this);
        imageJRightLabel.addMouseMotionListener(this);
        imageJRightLabel.addMouseWheelListener(this);
        imageJRightLabel.addKeyListener(this);
        imageJRightLabel.setFocusable(true);
        //imageJRightLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        combinedImageLabel.addMouseListener(this);
        combinedImageLabel.addMouseMotionListener(this);
        combinedImageLabel.addMouseWheelListener(this);
        combinedImageLabel.addKeyListener(this);
        combinedImageLabel.setFocusable(true);
        //imageILabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        splitPaneTop.setDividerLocation(0.5);
        splitPaneBottom.setDividerLocation(0.5);
        splitPaneMinor.setDividerLocation(0.5);


        spinnerBining.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                setImages(index, refindex);
            }
        });

        rot1Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                addRotation(index, -(Double) spinnerRotationIncrement.getValue());
                //updateImageI(index);
            }

        });
        rot2Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                addRotation(index, (Double) spinnerRotationIncrement.getValue());
                //updateImageI(index);
            }
        });
        spinnerTx.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                tx[nrjindex][index] = (Double) spinnerTx.getValue();
                updateImageI(nrjindex);
            }
        });
        spinnerTy.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                ty[nrjindex][index] = (Double) spinnerTy.getValue();
                updateImageI(nrjindex);
            }
        });
        spinnerRotationValue.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                rot[nrjindex][index] = (Double) spinnerRotationValue.getValue();
                updateImageI(nrjindex);
            }
        });


        invertReferenceCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                //int index=(Integer) spinnerTiltImage.getValue();
                setImages(nrjindex, refindex);
            }
        });
        normalizeImagesCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                //int index=(Integer) spinnerTiltImage.getValue();
                setImages(nrjindex, refindex);
            }
        });
        radioButtonSuperpose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                //int index=(Integer) spinnerTiltImage.getValue();
                setImages(nrjindex, refindex);
            }
        });
        radioButtonDifference.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                //int index=(Integer) spinnerTiltImage.getValue();
                setImages(nrjindex, refindex);
            }
        });
        applySameOnAllButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                double dx = (Double) spinnerTx.getValue();
                double dy = (Double) spinnerTy.getValue();
                double r = (Double) spinnerRotationValue.getValue();
                for (int i = 0; i < tx[nrjindex].length; i++) {
                    tx[nrjindex][i] = dx;
                    ty[nrjindex][i] = dy;
                    rot[nrjindex][i] = r;
                }
            }
        });
    }

    private void onOK() {
// add your code here
        wasCanceled = false;
        /*for(int i=0;i<ds.getTiltCount();i++){
            eftemtomoj.FilteredImage[] map=ds.getImages(i);
            for(int j=0;j<map.length;j++){
                map[j].applyTransform(new eftemtomoj.ImageRegistration.Transform(tx[j][i],ty[j][i],rot[j][i]));
            }
        } */
        dispose();
    }

    private void onCancel() {
// add your code here if necessary
        wasCanceled = true;
        dispose();
    }

    public boolean wasCanceled() {
        return wasCanceled;
    }

    public ImageRegistration.Transform[][] getTransforms() {
        ImageRegistration.Transform[][] transfs = new ImageRegistration.Transform[ds.getTiltCount()][ds.getWindowCount()];
        for (int i = 0; i < ds.getTiltCount(); i++) {
            for (int j = 0; j < ds.getWindowCount(); j++) {
                transfs[i][j] = new ImageRegistration.Transform(tx[j][i], ty[j][i], rot[j][i]);
            }
        }
        return transfs;
    }

    public void keyTyped(KeyEvent e) {
        System.out.println("key typed:" + e.getKeyCode());
        System.out.println("down:" + KeyEvent.VK_DOWN);

    }

    public void keyPressed(KeyEvent e) {
        //System.out.println("key pressed:" + e.getKeyCode());
        //System.out.println("down:" + KeyEvent.VK_DOWN);
    }

    public void keyReleased(KeyEvent e) {
        System.out.println("key released:" + e.getKeyCode());
        int index = tilts.indexOf(spinnerTiltImage.getValue());
        //System.out.println("down:" + KeyEvent.VK_DOWN);
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            addTranslation(index, 0, (Double) spinnerTranslationIncrement.getModel().getValue());
        } else if (e.getKeyCode() == KeyEvent.VK_UP) {
            addTranslation(index, 0, -(Double) spinnerTranslationIncrement.getModel().getValue());
        } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            addTranslation(index, -(Double) spinnerTranslationIncrement.getModel().getValue(), 0);
        } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            addTranslation(index, (Double) spinnerTranslationIncrement.getModel().getValue(), 0);
        } else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
            /*if (index - 1 >= refindex) {
                spinnerTiltImage.getModel().setValue(index - 1);
                setImages(index, index - refindex);
            } */
            previousImageButton.doClick();
        } else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
            /*if (index + 1 < ts.getImageStackSize()) {
                spinnerTiltImage.getModel().setValue(index + 1);
                setImages(index, index - refindex);
            } */
            NextImageButton.doClick();
        } else return;
        updateImageI(index);

    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
        if (e.getSource() == imageILabel) {
            imageILabel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
        } else if (e.getSource() == combinedImageLabel) {
            combinedImageLabel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
        } else if (e.getSource() == imageJBottomLabel) {
            imageJBottomLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        } else if (e.getSource() == imageJRightLabel) {
            imageJRightLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        }

    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
        mouseOrigin = e.getPoint();
        bkpImgOri = (Point2D.Double) ImageOrigin.clone();
        if (e.getButton() == MouseEvent.BUTTON3) {
            if (e.getSource() == imageJBottomLabel) {
                imageJBottomLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            if (e.getSource() == imageJRightLabel) {
                imageJRightLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            if (e.getSource() == imageILabel) {
                imageILabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            if (e.getSource() == combinedImageLabel) {
                combinedImageLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        }

    }

    public void mouseReleased(MouseEvent e) {
        if (e.getModifiers() == MouseEvent.BUTTON3_MASK) {
            if (e.getSource() == imageJBottomLabel) {
                imageJBottomLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            }
            if (e.getSource() == imageJRightLabel) {
                imageJRightLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            }
            if (e.getSource() == imageILabel) {
                imageILabel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
            }
            if (e.getSource() == combinedImageLabel) {
                combinedImageLabel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
            }
        } else if (e.getModifiers() == MouseEvent.BUTTON1_MASK) {
            if (e.getSource() == imageJBottomLabel) {
                pointV = e.getPoint();
                //System.out.println("pointV=" + pointV);
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                setImages(index, refindex);
            }
            if (e.getSource() == imageJRightLabel) {
                pointH = e.getPoint();
                int index = energies.indexOf(EnergyImageSpinner.getValue());
                setImages(index, refindex);
            }
        }
    }

    public void mouseDragged(MouseEvent e) {

        if (e.getSource() == imageILabel || e.getSource() == combinedImageLabel) {
            Point actualPosition = e.getPoint();
            int index = energies.indexOf((String) EnergyImageSpinner.getValue());
            if (e.getModifiers() == MouseEvent.BUTTON1_MASK) {
                //Point diff = actualPosition - mouseOrigin;
                double dx = (actualPosition.getX() - mouseOrigin.getX()) * (Double) spinnerTranslationIncrement.getValue();
                double dy = (actualPosition.getY() - mouseOrigin.getY()) * (Double) spinnerTranslationIncrement.getValue();
                mouseOrigin = actualPosition;
                addTranslation(index, dx, dy);
            } else if (e.getModifiers() == MouseEvent.BUTTON3_MASK) {
                double dist = actualPosition.distance(mouseOrigin);
                int direction = (actualPosition.getX() > mouseOrigin.getX()) ? 1 : -1;
                dist *= direction;
                mouseOrigin = actualPosition;
                //System.out.println("rot " + dist);
                addRotation(index, dist * (Double) spinnerRotationIncrement.getValue());
            }
            updateImageI(index);
        }
        if (e.getSource() == imageJBottomLabel || e.getSource() == imageJRightLabel) {
            //System.out.println("button " + e.getButton() + " mod " + e.getModifiers() + " modEX " + e.getModifiersEx());
            //System.out.println("button 1 is: " + MouseEvent.BUTTON1 + " mask:" + MouseEvent.BUTTON1_MASK + " downmask:" + MouseEvent.BUTTON1_DOWN_MASK);
            //System.out.println("button 2 is: " + MouseEvent.BUTTON2 + " mask:" + MouseEvent.BUTTON2_MASK + " downmask:" + MouseEvent.BUTTON2_DOWN_MASK);
            //System.out.println("button 3 is: " + MouseEvent.BUTTON3 + " mask:" + MouseEvent.BUTTON3_MASK + " downmask:" + MouseEvent.BUTTON3_DOWN_MASK);
            if (e.getModifiers() == MouseEvent.BUTTON3_MASK) {
                Point actualPosition = e.getPoint();
                //Point diff = actualPosition - mouseOrigin;
                double dx = actualPosition.getX() - mouseOrigin.getX();
                double dy = actualPosition.getY() - mouseOrigin.getY();
                ImageOrigin = new Point2D.Double(bkpImgOri.getX() - dx, bkpImgOri.getY() - dy);
                int index = energies.indexOf((String) EnergyImageSpinner.getValue());
                setImages(index, refindex);
            }
        }

    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        int tmp = e.getWheelRotation();
        System.out.println("mouse wheel " + tmp);
        if (tmp < 0) previousImageButton.doClick();
        if (tmp > 0) NextImageButton.doClick();
    }

    public void paint(Graphics g) {
        super.paint(g);
        System.out.println("paint");
        displayWidth = contentPane.getWidth() - 30 - menuPanel.getWidth() - splitPaneTop.getDividerSize();
        displayWidth /= 2;
        //displayWidth = (splitPaneTop.getWidth() - splitPaneTop.getDividerSize()) / 2;
        displayHeight = (contentPane.getHeight() - 30 - okCancelMainPanel.getHeight() - splitPaneTop.getDividerSize()) / 2;
        updateSpinners();
        int index = energies.indexOf(EnergyImageSpinner.getModel().getValue());
        //int index = ((SpinnerNumberModel) EnergyImageSpinner.getModel()).getNumber().intValue();
        setImages(index, refindex);
    }

    public void setImages(int index, int indexRef) {
        /*if (indexRef < 0) indexRef = 0;
        if (indexRef > ts.getImageStackSize() - 1) indexRef = ts.getImageStackSize() - 1;
        if (indexRef == index) indexRef = ts.getZeroIndex(); */
        double bin = ((SpinnerNumberModel) spinnerBining.getModel()).getNumber().doubleValue();
        //expandT.setToIdentity();
        /*double expandingFactor = 1 / Math.cos(Math.toRadians(ts.getTiltAngle(indexRef)));
        expandT.rotate(Math.toRadians((Double) spinnerTiltAxis.getValue()));
        expandT.scale(expandingFactor, 1);
        expandT.rotate(-Math.toRadians((Double) spinnerTiltAxis.getValue()));*/
        imgRef = getImageForDisplay(indexRef, bin, false);
        if (invertReferenceCheckBox.isSelected()) imgRef.invert();
        if (normalizeImagesCheckBox.isSelected()) autoAdjust(imgRef);

        //expandT.setToIdentity();

        //System.out.println("expanding factor: " + expandingFactor);
        updateImageI(index);

        ImageProcessor imgJR = imgRef.duplicate();
        ImageProcessor imgJB = imgRef.duplicate();
        if (pointH != null) {
            imgJR = imgJR.convertToRGB();
            imgJR.setColor(Color.RED);
            imgJR.drawLine(0, (int) pointH.getY(), imgJR.getWidth(), (int) pointH.getY());
        }
        if (pointV != null) {
            imgJB = imgJB.convertToRGB();
            imgJB.setColor(Color.GREEN);
            imgJB.drawLine((int) pointV.getX(), 0, (int) pointV.getX(), imgJR.getHeight());
        }
        imageJRightLabel.setIcon(new ImageIcon(imgJR.getBufferedImage(), "image Ref"));
        imageJBottomLabel.setIcon(new ImageIcon(imgJB.getBufferedImage(), "image Ref"));


    }

    public void updateImageI(int index) {
        double bin = ((SpinnerNumberModel) spinnerBining.getModel()).getNumber().doubleValue();

        ImageProcessor toto = getImageForDisplay(index, bin, true);
        if (normalizeImagesCheckBox.isSelected()) autoAdjust(toto);

        combinedImageLabel.setIcon(new ImageIcon(createCombinedImage(toto, imgRef).getBufferedImage(), "combined"));
        if (pointH != null) {
            toto = toto.convertToRGB();
            toto.setColor(Color.RED);
            toto.drawLine(0, (int) pointH.getY(), toto.getWidth(), (int) pointH.getY());
        }
        if (pointV != null) {
            toto = toto.convertToRGB();
            toto.setColor(Color.GREEN);
            toto.drawLine((int) pointV.getX(), 0, (int) pointV.getX(), toto.getHeight());
        }
        imageILabel.setIcon(new ImageIcon(toto.getBufferedImage(), "image I"));

    }

    public FloatProcessor getImageForDisplay(int nrjindex, double binning, boolean applyTransform) {
        ImageProcessor ip = mappingImages[nrjindex].getImage();

        float[] pixs = (float[]) ip.getPixels();
        int sx = ip.getWidth();
        int sy = ip.getHeight();
        int nsx = (int) (sx / binning);
        int nsy = (int) (sy / binning);
        AffineTransform T = new AffineTransform();


        if (binning <= 1) {
            if (applyTransform) {
                T.rotate(Math.toRadians(rot[nrjindex][tiltindex]));
                T.translate(tx[nrjindex][tiltindex], ty[nrjindex][tiltindex]);
            }

            try {
                T = T.createInverse();
            } catch (Exception e) {
                e.printStackTrace();
            }
            nsx = (int) (displayWidth * binning);
            nsy = (int) (displayHeight * binning);
            pixs = SubImage.getSubImagePixels(pixs, T, ip.getStatistics(), sx, sy, nsx, nsy, ImageOrigin, true, false);
            sx = nsx;
            sy = nsy;
            nsx = displayWidth;
            nsy = displayHeight;

        }
        int nsize = nsx * nsy;


        //downsampling in fourier
        if (binning != 1) {
            DenseFloatMatrix2D H1 = new DenseFloatMatrix2D(sy, sx);
            H1.assign(pixs);
            DenseFComplexMatrix2D fft = H1.getFft2();
            float[] fft1 = fft.elements();


            double ncx = (nsx - 1.0) / 2;
            double ncy = (nsy - 1.0) / 2;
            int ind1, ind2;
            //downsampling in fourier
            float[] bfft = new float[nsize * 2];
            for (int y = 0; y < nsy; y++) {
                int yy = y * nsx;
                int posy = (y > ncy) ? (y + sy - nsy) * sx : y * sx;
                for (int x = 0; x < nsx; x++) {
                    int posx = (x > ncx) ? (x - nsx + sx) : x;
                    ind1 = (yy + x) * 2;
                    ind2 = (posy + posx) * 2;
                    if (ind1 > 0 && ind1 < bfft.length && ind2 > 0 && ind2 < fft1.length) {
                        bfft[ind1] = fft1[ind2];
                        bfft[ind1 + 1] = fft1[ind2 + 1];
                    }
                }
            }

            fft = new DenseFComplexMatrix2D(nsy, nsx);
            fft.assign(bfft);
            fft.ifft2(true);
            bfft = fft.elements();
            pixs = new float[nsize];
            for (int j = 0; j < pixs.length; j++) {
                pixs[j] = bfft[j * 2];
            }
        }
        if (binning > 1) {
            if (applyTransform) {
                T.rotate(Math.toRadians(rot[nrjindex][tiltindex]));
                T.translate(tx[nrjindex][tiltindex] / binning, ty[nrjindex][tiltindex] / binning);
            }

            try {
                T = T.createInverse();
            } catch (Exception e) {
                e.printStackTrace();
            }
            pixs = SubImage.getSubImagePixels(pixs, T, ip.getStatistics(), nsx, nsy, displayWidth, displayHeight, ImageOrigin, true, false);
        }
        return new FloatProcessor(displayWidth, displayHeight, pixs, ip.getCurrentColorModel());
    }

    public ImageProcessor createCombinedImage(ImageProcessor moving, ImageProcessor reference) {
        ImageProcessor result = null;
        if (radioButtonSuperpose.isSelected()) {
            ByteProcessor mB = (ByteProcessor) moving.convertToByte(true);
            ByteProcessor rB = (ByteProcessor) reference.convertToByte(true);
            result = new ColorProcessor(moving.getWidth(), moving.getHeight());
            ((ColorProcessor) result).setRGB((byte[]) mB.getPixels(), (byte[]) rB.getPixels(), (byte[]) mB.getPixels());

        } else {
            result = moving.convertToByte(true);
            result.copyBits(reference.convertToByte(true), 0, 0, Blitter.DIFFERENCE);

        }
        if (hw == null) {
            hw = new HistogramWindow(new ImagePlus("", result));
            hw.setVisible(false);
        } else {
            hw.getImagePlus().getProcessor().setValue(255);
            hw.getImagePlus().getProcessor().fill();
            hw.showHistogram(new ImagePlus("", result), 256);
        }
        histogramLabel.setIcon(new ImageIcon(hw.getImagePlus().getBufferedImage(), "histogram"));
        return result;

    }

    public void addTranslation(int index, double dx, double dy) {
        tx[index][tiltindex] += dx;
        ty[index][tiltindex] += dy;
        spinnerTx.setValue(tx[index][tiltindex]);
        spinnerTy.setValue(ty[index][tiltindex]);
    }

    public void addRotation(int index, double angleDeg) {
        rot[index][tiltindex] += angleDeg;
        spinnerRotationValue.setValue(rot[index][tiltindex]);
    }

    protected void updateSpinners() {
        double actualValue = ((SpinnerNumberModel) spinnerBining.getModel()).getNumber().doubleValue();
        double min = Math.min(ds.getWidth() / (double) displayWidth, ds.getHeight() / (double) displayHeight);
        if (actualValue > min) {
            spinnerBining.getModel().setValue(min);
        }

    }

    void autoAdjust(ImageProcessor ip) {

        ImageStatistics stats = ip.getStatistics(); // get uncalibrated stats
        int limit = stats.pixelCount / 10;
        int[] histogram = stats.histogram;
        int threshold = stats.pixelCount / 5000;
        int i = -1;
        boolean found = false;
        int count;
        do {
            i++;
            count = histogram[i];
            if (count > limit) count = 0;
            found = count > threshold;
        } while (!found && i < 255);
        int hmin = i;
        i = 256;
        do {
            i--;
            count = histogram[i];
            if (count > limit) count = 0;
            found = count > threshold;
        } while (!found && i > 0);
        int hmax = i;
        if (hmax >= hmin) {
            double min = stats.histMin + hmin * stats.binSize;
            double max = stats.histMin + hmax * stats.binSize;
            if (min == max) {
                min = stats.min;
                max = stats.max;
            }
            ip.setMinAndMax(min, max);
        } else {
            ip.resetMinAndMax();
        }

    }

    private void createUIComponents() {

        spinnerTranslationIncrement = new JSpinner(new SpinnerListModel(possibleIncrements));
        spinnerTranslationIncrement.setValue(possibleIncrements[possibleIncrements.length - 1]);
        spinnerRotationIncrement = new JSpinner(new SpinnerListModel(possibleIncrements));
        spinnerRotationIncrement.setValue(possibleIncrements[possibleIncrements.length - 1]);
        spinnerBining = new JSpinner(new SpinnerNumberModel(ds.getWidth() / 256.0, 0.5, ds.getWidth() / 256.0 * 2, 0.1));
        spinnerTx = new JSpinner(new SpinnerNumberModel(0, -ds.getWidth(), ds.getWidth(), 1.0));
        spinnerTy = new JSpinner(new SpinnerNumberModel(0, -ds.getHeight(), ds.getHeight(), 1.0));
        spinnerRotationValue = new JSpinner(new SpinnerNumberModel(0, -360.0, 360.0, 0.5));

        EnergyImageSpinner = new JSpinner(new SpinnerListModel(energies));
        spinnerTiltImage = new JSpinner(new SpinnerListModel(tilts));
        hw = null;
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
        okCancelMainPanel = new JPanel();
        okCancelMainPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(okCancelMainPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        okCancelMainPanel.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        okCancelMainPanel.add(panel1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel1.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel1.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        splitPaneMajor = new JSplitPane();
        splitPaneMajor.setDividerSize(5);
        panel3.add(splitPaneMajor, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        menuPanel = new JPanel();
        menuPanel.setLayout(new GridLayoutManager(11, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneMajor.setLeftComponent(menuPanel);
        downsamplingPanel = new JPanel();
        downsamplingPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(downsamplingPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("down sampling");
        downsamplingPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        downsamplingPanel.add(spinnerBining, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        translationPanel = new JPanel();
        translationPanel.setLayout(new GridLayoutManager(4, 3, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(translationPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Translation");
        translationPanel.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
        translationPanel.add(txLeftButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tyDownButton = new JButton();
        tyDownButton.setIcon(new ImageIcon(getClass().getResource("/arrow_down.png")));
        tyDownButton.setText("");
        translationPanel.add(tyDownButton, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rotationPanel = new JPanel();
        rotationPanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(rotationPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("rotation");
        rotationPanel.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
        tiltImagePanel = new JPanel();
        tiltImagePanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(tiltImagePanel, new GridConstraints(9, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        final JLabel label4 = new JLabel();
        label4.setText("Tilt Image");
        tiltImagePanel.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tiltImagePanel.add(spinnerTiltImage, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        previousImageButton = new JButton();
        previousImageButton.setText("Prev");
        tiltImagePanel.add(previousImageButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        NextImageButton = new JButton();
        NextImageButton.setText("Next");
        tiltImagePanel.add(NextImageButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        menuPanel.add(spacer2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        EnergyImagePanel = new JPanel();
        EnergyImagePanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(EnergyImagePanel, new GridConstraints(7, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        final JLabel label5 = new JLabel();
        label5.setText("Energy Image");
        EnergyImagePanel.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImagePanel.add(EnergyImageSpinner, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImagePreviousButton = new JButton();
        EnergyImagePreviousButton.setText("Prev");
        EnergyImagePanel.add(EnergyImagePreviousButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImageNextButton = new JButton();
        EnergyImageNextButton.setText("Next");
        EnergyImagePanel.add(EnergyImageNextButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(panel4, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        normalizeImagesCheckBox = new JCheckBox();
        normalizeImagesCheckBox.setText("normalize images");
        panel4.add(normalizeImagesCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        invertReferenceCheckBox = new JCheckBox();
        invertReferenceCheckBox.setText("invert reference");
        panel4.add(invertReferenceCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(panel5, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("combination");
        panel5.add(label6, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radioButtonSuperpose = new JRadioButton();
        radioButtonSuperpose.setSelected(true);
        radioButtonSuperpose.setText("superimpose");
        panel5.add(radioButtonSuperpose, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radioButtonDifference = new JRadioButton();
        radioButtonDifference.setText("difference");
        panel5.add(radioButtonDifference, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        histogramLabel = new JLabel();
        histogramLabel.setText("");
        menuPanel.add(histogramLabel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(panel6, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        applySameOnAllButton = new JButton();
        applySameOnAllButton.setText("apply same on all tilts angles");
        panel6.add(applySameOnAllButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rightPanel = new JPanel();
        rightPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneMajor.setRightComponent(rightPanel);
        splitPaneMinor = new JSplitPane();
        splitPaneMinor.setDividerLocation(279);
        splitPaneMinor.setDividerSize(5);
        splitPaneMinor.setOrientation(0);
        splitPaneMinor.setResizeWeight(0.5);
        rightPanel.add(splitPaneMinor, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        splitPaneBottom = new JSplitPane();
        splitPaneBottom.setDividerSize(5);
        splitPaneBottom.setResizeWeight(0.5);
        splitPaneMinor.setRightComponent(splitPaneBottom);
        jPanelImageHB = new JPanel();
        jPanelImageHB.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneBottom.setLeftComponent(jPanelImageHB);
        imageJBottomLabel = new JLabel();
        imageJBottomLabel.setText("");
        jPanelImageHB.add(imageJBottomLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        jPanelImageComb = new JPanel();
        jPanelImageComb.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneBottom.setRightComponent(jPanelImageComb);
        combinedImageLabel = new JLabel();
        combinedImageLabel.setText("");
        jPanelImageComb.add(combinedImageLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        splitPaneTop = new JSplitPane();
        splitPaneTop.setDividerSize(5);
        splitPaneTop.setResizeWeight(0.5);
        splitPaneMinor.setLeftComponent(splitPaneTop);
        jPanelImageI = new JPanel();
        jPanelImageI.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneTop.setLeftComponent(jPanelImageI);
        imageILabel = new JLabel();
        imageILabel.setText("");
        jPanelImageI.add(imageILabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        jPanelImageHR = new JPanel();
        jPanelImageHR.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneTop.setRightComponent(jPanelImageHR);
        imageJRightLabel = new JLabel();
        imageJRightLabel.setText("");
        jPanelImageHR.add(imageJRightLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(radioButtonSuperpose);
        buttonGroup.add(radioButtonDifference);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
