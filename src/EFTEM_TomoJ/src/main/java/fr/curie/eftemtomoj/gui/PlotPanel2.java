package fr.curie.eftemtomoj.gui;

import fr.curie.eftemtomoj.FilteredImage;
import fr.curie.eftemtomoj.Mapper;
import fr.curie.eftemtomoj.Mapper.ImageType;
import ij.gui.Roi;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

public class PlotPanel2 extends JPanel {
    public static final Color SIG_COLOR = new Color(255, 209, 84, 180);
    public static final Color BGD_COLOR = new Color(84, 178, 255, 180);
    public static final Color INACTIVE_COLOR = new Color(230, 230, 230, 180);
    public static final Color REG_COLOR = Color.BLACK;
    public static final String SELECTION_PROPERTY = "selectionProperty";
    public static final int MIN_WINDOW_WIDTH = 10;
    private static final Insets MARGINS = new Insets(10, 10, 5, 10);

    private final Font checkBoxFont = getFont().deriveFont(10.0f);

    private float[] dataLimitsX = new float[2];
    private float[] dataLimitsY = new float[2];

    private JCheckBox[] bgdCheckBoxes;
    private JCheckBox[] sigCheckBoxes;
    private JCheckBox[] oldBG = null;

    private Rectangle plotArea;
    private float plotScaleX;
    private float plotScaleY;

    private final int checkBoxHeight = 22;
    private final int labelHeight = 20;

    // Data
    private float[] dX, dY, dW;
    private ImageType[] dT;

    // Regression curve
    private float[] rX, rY;

    public PlotPanel2() {
        setBackground(Color.WHITE);
        setOpaque(true);

        dX = new float[]{};
        dY = new float[]{};
        dW = new float[]{};
        dT = new ImageType[]{};
        rX = new float[]{};
        rY = new float[]{};
    }

    public void setData(Mapper mp, Roi roi) {
        FilteredImage[] images = mp.getImages();

        float minX, maxX, minY, maxY, val;

        minX = Float.NaN;
        maxX = Float.NaN;
        minY = Float.NaN;
        maxY = Float.NaN;

        // Get data points
        dX = new float[images.length];
        dW = new float[images.length];
        dY = new float[images.length];
        dT = new ImageType[images.length];
        for (int i = 0; i < images.length; i++) {
            dX[i] = images[i].getEnergyShift();
            dW[i] = images[i].getSlitWidth();
            dY[i] = (float) images[i].calculateMean(roi);
            dT[i] = mp.getImageType(i);

            val = dX[i] - dW[i];
            if (Float.isNaN(minX) || val < minX) minX = val;

            val = dX[i] + dW[i];
            if (Float.isNaN(maxX) || val > maxX) maxX = val;

            val = dY[i];
            if (Float.isNaN(minY) || val < minY) minY = val;
            if (Float.isNaN(maxY) || val > maxY) maxY = val;
        }

        // Adjust data limits with 5% padding
        float xpad = Math.abs(maxX - minX) / 20;
        float ypad = Math.abs(maxY - minY) / 20;
        dataLimitsX = new float[]{minX - xpad, maxX + xpad};
//	    dataLimitsY = new float[]{minY - ypad, maxY + ypad};
        dataLimitsY = new float[]{0, maxY + ypad};

        // Clean up and create new checkboxes


        if (bgdCheckBoxes == null) {
            bgdCheckBoxes = new JCheckBox[images.length];
            oldBG = new JCheckBox[images.length];
            for (int i = 0; i < images.length; i++) {
                bgdCheckBoxes[i] = new JCheckBox("");
                bgdCheckBoxes[i].setFont(checkBoxFont);
                bgdCheckBoxes[i].addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ae) {
                        firePropertyChange(SELECTION_PROPERTY, true, false);
                    }
                });
                bgdCheckBoxes[i].setSelected(dT[i] == ImageType.Background);
                oldBG[i] = new JCheckBox("");
                oldBG[i].setSelected(bgdCheckBoxes[i].isSelected());

                add(bgdCheckBoxes[i]);
            }
        }
        if (sigCheckBoxes == null) {
            sigCheckBoxes = new JCheckBox[images.length];
            for (int i = 0; i < images.length; i++) {
                sigCheckBoxes[i] = new JCheckBox("");
                sigCheckBoxes[i].setFont(checkBoxFont);
                sigCheckBoxes[i].addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ae) {
                        firePropertyChange(SELECTION_PROPERTY, true, false);
                    }
                });

                sigCheckBoxes[i].setSelected(dT[i] == ImageType.Signal);
                add(sigCheckBoxes[i]);
            }
        }

        revalidate();
        repaint();
    }

    public void clear() {
        removeAll();
        sigCheckBoxes = null;
        bgdCheckBoxes = null;
    }

    public void setRegression(Mapper.Regression reg) {
        if (reg != null) {
            rX = reg.rX;
            rY = reg.rY;
        } else {
            rX = new float[]{};
            rY = new float[]{};
        }

        revalidate();
        repaint();
    }

    public ImageType[] getSelection() {
        if (bgdCheckBoxes != null && sigCheckBoxes != null) {
            for (int i = 0; i < bgdCheckBoxes.length; i++) {
                if (bgdCheckBoxes[i].isSelected() && sigCheckBoxes[i].isSelected()) {
                    if (oldBG[i].isSelected()) {
                        bgdCheckBoxes[i].setSelected(false);
                    } else {
                        sigCheckBoxes[i].setSelected(false);
                    }
                    oldBG[i].setSelected(bgdCheckBoxes[i].isSelected());
                }
                if (bgdCheckBoxes[i].isSelected()) dT[i] = ImageType.Background;
                else if (sigCheckBoxes[i].isSelected()) dT[i] = ImageType.Signal;
                else dT[i] = ImageType.Disabled;

            }

        }
        ImageType[] selectedTypes = new ImageType[dT.length];
        for (int i = 0; i < selectedTypes.length; i++) {
            if (bgdCheckBoxes[i].isSelected()) {
                selectedTypes[i] = ImageType.Background;
            } else if (sigCheckBoxes[i].isSelected()) {
                selectedTypes[i] = ImageType.Signal;
            } else {
                selectedTypes[i] = ImageType.Disabled;
            }
        }

        return selectedTypes;
    }

    public void setSelection(ImageType[] selectedTypes) {
        for (int i = 0; i < selectedTypes.length; i++) {
            if (selectedTypes[i] == ImageType.Background) {
                bgdCheckBoxes[i].setSelected(true);
                sigCheckBoxes[i].setSelected(false);
            } else if (selectedTypes[i] == ImageType.Signal) {
                sigCheckBoxes[i].setSelected(true);
                bgdCheckBoxes[i].setSelected(false);
            } else {
                sigCheckBoxes[i].setSelected(false);
                bgdCheckBoxes[i].setSelected(false);
            }
        }
    }

    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g2 = (Graphics2D) graphics;

        // Calculate plot metrics
        int plotWidth = getWidth() - MARGINS.left - MARGINS.right;// - labelWidth;
        int plotHeight = getHeight() - MARGINS.top - MARGINS.bottom - checkBoxHeight - 2 * labelHeight;

        plotArea = new Rectangle(MARGINS.left, MARGINS.top, plotWidth, plotHeight);
        plotScaleX = plotArea.width / (dataLimitsX[1] - dataLimitsX[0]);
        plotScaleY = plotArea.height / (dataLimitsY[1] - dataLimitsY[0]);

        // Set attributes
        g2.setFont(getFont().deriveFont(10.0f));

        // Draw plot
        drawAxes(g2);
        drawData(g2);
        drawRegressionCurve(g2);
    }

    private void drawData(Graphics2D g2) {
        float x, y, y0, X0, Y0, bw;
        FontMetrics fm;
        String label;

        fm = g2.getFontMetrics();

        X0 = dataLimitsX[0];
        Y0 = dataLimitsY[0];
        y0 = plotArea.y + plotArea.height;

        // Draw data bars
        for (int i = 0; i < dX.length; i++) {
            x = plotArea.x + plotScaleX * (dX[i] - X0);
            y = plotArea.y + plotArea.height - plotScaleY * (dY[i] - Y0);
            bw = plotScaleX * (dW[i] > MIN_WINDOW_WIDTH ? dW[i] : MIN_WINDOW_WIDTH);

            // Draw bar
            if (dT[i] == ImageType.Signal)
                g2.setPaint(SIG_COLOR);
            else if (dT[i] == ImageType.Background)
                g2.setPaint(BGD_COLOR);
            else
                g2.setPaint(INACTIVE_COLOR);

            g2.fill(new Rectangle2D.Float(x - (bw / 2), y, bw, y0 - y));

            g2.setPaint(g2.getColor().darker());
            g2.drawLine((int) x, (int) y, (int) x, (int) y0);

            // Draw label
            y = plotArea.y + plotArea.height + labelHeight - 5;

            label = String.format("%1$.0f", dX[i]);
            g2.setPaint(Color.BLACK);
            g2.drawString(label, x - (fm.stringWidth(label) / 2), y);

            // Position checkbox
            x -= sigCheckBoxes[i].getWidth() / 2;
            y = plotArea.y + plotArea.height + labelHeight;
            sigCheckBoxes[i].setLocation((int) x, (int) y);

            y += labelHeight;
            bgdCheckBoxes[i].setLocation((int) x, (int) y);
        }

        // Draw check box labels
        if (sigCheckBoxes.length > 0 && bgdCheckBoxes.length > 0) {
            g2.setPaint(Color.BLACK);

            y = sigCheckBoxes[0].getLocation().y + sigCheckBoxes[0].getSize().height - 5;
            g2.drawString("Edge", plotArea.x, y);

            y = bgdCheckBoxes[0].getLocation().y + bgdCheckBoxes[0].getSize().height - 5;
            g2.drawString("Background", plotArea.x, y);
        }
    }

    private void drawRegressionCurve(Graphics2D g2) {
        float x, y, px, py, X0, Y0;
        boolean firstSegment = true;

        X0 = dataLimitsX[0];
        Y0 = dataLimitsY[0];
        px = 0;
        py = 0;

        for (int i = 0; i < rX.length; i++) {
            x = plotArea.x + plotScaleX * (rX[i] - X0);
            y = plotArea.y + plotArea.height - plotScaleY * (rY[i] - Y0);

            // Draw point
            g2.setPaint(REG_COLOR);
            if (!firstSegment) {
                g2.draw(new Line2D.Double(px, py, x, y));
            }

            px = x;
            py = y;
            firstSegment = false;
        }
    }

    private void drawAxes(Graphics2D g2) {
        double x1, x2, y1, y2;

        // Horizontal axis
        x1 = plotArea.x;
        x2 = plotArea.x + plotArea.width;
        y1 = plotArea.y + plotArea.height;
        y2 = y1;
        g2.draw(new Line2D.Double(x1, y1, x2, y2));

        y1 += labelHeight - 5;
        x1 = x2 - 10;
        g2.setPaint(Color.BLACK);
        g2.drawString("eV", (int) x1, (int) y1);

        // Vertical axis
        x1 = plotArea.x;
        x2 = x1;
        y1 = plotArea.y + plotArea.height;
        y2 = plotArea.y;
        g2.draw(new Line2D.Double(x1, y1, x2, y2));
    }

}