package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.tomoj.align.AffineAlignment;
import fr.curie.tomoj.align.Alignment;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import ij.process.ImageStatistics;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.align.SingleTiltAlign;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * Created by cedric on 10/01/2017.
 */
public class CrossCorrelationParameters implements Application {
    protected TiltSeries ts;
    SingleTiltAlign align;
    boolean cumulativeReference;
    boolean expandImage;
    boolean loop;
    boolean integerTranslation = false;
    boolean variance;
    int varianceRadius;
    int downSampling;
    boolean bandpassFilter;
    double bandpassFilterMin, bandpassFilterMax, bandpassFilterDecrease;
    boolean roi;
    int roiX, roiY;
    boolean multiScale;
    int multiScaleLevels;
    double tiltAxis;

    boolean firstDisplay = true;
    private ImagePlus preview = null;
    int previewIndex = 0;
    boolean displayPreview = false;
    String resultString;

    private JCheckBox multiscaleCheckBox;
    private JSpinner spinnerMulticaleLevels;
    private JCheckBox loopUntilStabilizationCheckBox;
    private JCheckBox integerTranslationCheckBox;
    private JCheckBox roiCenteredCheckBox;
    private JSpinner spinnerRoiX;
    private JSpinner spinnerRoiY;
    private JCheckBox bandpassFilterCheckBox;
    private JSpinner spinnerCutMinRadius;
    private JSpinner spinnerCutMaxRadius;
    private JSpinner spinnerBandpassDropDown;
    private JCheckBox downsamplingCheckBox;
    private JComboBox comboBoxDownSampling;
    private JCheckBox varianceFilterCheckBox;
    private JSpinner spinnerVariance;
    private JCheckBox expandImagesCheckBox;
    private JSpinner spinnerExpandTiltAxis;
    private JCheckBox cumulativeReferenceCheckBox;
    private JPanel rootPanel;

    public CrossCorrelationParameters(TiltSeries ts) {
        this.ts = ts;
        align = new SingleTiltAlign(ts);
    }

    public void interrupt() {
        align.interrupt();
    }

    public double getCompletion() {
        return align.getCompletion();
    }

    private void initValues() {
        roiX = -1;
        roiY = -1;
        downSampling = 1;
        previewIndex = ts.getCurrentSlice() - 1;


        multiScale = false;
        multiscaleCheckBox.setSelected(multiScale);
        loopUntilStabilizationCheckBox.setSelected(false);
        integerTranslation = true;
        integerTranslationCheckBox.setSelected(integerTranslation);
        roiCenteredCheckBox.setSelected(false);
        bandpassFilterCheckBox.setSelected(false);
        downsamplingCheckBox.setSelected(false);
        varianceFilterCheckBox.setSelected(false);
        expandImagesCheckBox.setSelected(false);
        cumulativeReferenceCheckBox.setSelected(false);

        double radius = ts.getWidth() / 64;
        ((SpinnerNumberModel) spinnerCutMinRadius.getModel()).setMinimum(0);
        spinnerCutMinRadius.setValue(2 * radius);
        ((SpinnerNumberModel) spinnerCutMaxRadius.getModel()).setMinimum(0);
        spinnerCutMaxRadius.setValue(8 * radius);
        ((SpinnerNumberModel) spinnerBandpassDropDown.getModel()).setMinimum(0);
        spinnerBandpassDropDown.setValue(3 * radius);

        ((SpinnerNumberModel) spinnerMulticaleLevels.getModel()).setMinimum(2);
        ((SpinnerNumberModel) spinnerMulticaleLevels.getModel()).setValue(2);
        ((SpinnerNumberModel) spinnerMulticaleLevels.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerRoiX.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerRoiX.getModel()).setMaximum(ts.getWidth());
        ((SpinnerNumberModel) spinnerRoiX.getModel()).setValue(ts.getWidth() / 2);
        ((SpinnerNumberModel) spinnerRoiX.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerRoiY.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerRoiY.getModel()).setMaximum(ts.getHeight());
        ((SpinnerNumberModel) spinnerRoiY.getModel()).setValue(ts.getHeight() / 2);
        ((SpinnerNumberModel) spinnerRoiY.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerVariance.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerVariance.getModel()).setMaximum(ts.getWidth());
        ((SpinnerNumberModel) spinnerVariance.getModel()).setValue(1);
        ((SpinnerNumberModel) spinnerVariance.getModel()).setStepSize(1);

        spinnerExpandTiltAxis.setEnabled(false);
        ((SpinnerNumberModel) spinnerExpandTiltAxis.getModel()).setMinimum(-180);
        ((SpinnerNumberModel) spinnerExpandTiltAxis.getModel()).setMaximum(180);
        ((SpinnerNumberModel) spinnerExpandTiltAxis.getModel()).setValue(ts.getTiltAxis());
        ((SpinnerNumberModel) spinnerExpandTiltAxis.getModel()).setStepSize(0.1);
        tiltAxis = ts.getTiltAxis();

    }

    private void addListeners() {
        integerTranslationCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                integerTranslation = integerTranslationCheckBox.isSelected();
            }
        });
        multiscaleCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                multiScale = multiscaleCheckBox.isSelected();
                spinnerMulticaleLevels.setEnabled(multiScale);
                multiScaleLevels = ((SpinnerNumberModel) spinnerMulticaleLevels.getModel()).getNumber().intValue();

                updatePreview();
            }
        });
        roiCenteredCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                roi = roiCenteredCheckBox.isSelected();
                spinnerRoiX.setEnabled(roi);
                spinnerRoiY.setEnabled(roi);
                if (roi) {
                    roiX = ((SpinnerNumberModel) spinnerRoiX.getModel()).getNumber().intValue();
                    roiY = ((SpinnerNumberModel) spinnerRoiY.getModel()).getNumber().intValue();
                } else {
                    roiX = ts.getWidth();
                    roiY = ts.getHeight();
                }
                updatePreview();
            }
        });
        bandpassFilterCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                bandpassFilter = bandpassFilterCheckBox.isSelected();
                spinnerCutMinRadius.setEnabled(bandpassFilter);
                spinnerCutMaxRadius.setEnabled(bandpassFilter);
                spinnerBandpassDropDown.setEnabled(bandpassFilter);
                bandpassFilterMin = ((SpinnerNumberModel) spinnerCutMinRadius.getModel()).getNumber().intValue();
                bandpassFilterMax = ((SpinnerNumberModel) spinnerCutMaxRadius.getModel()).getNumber().intValue();
                bandpassFilterDecrease = ((SpinnerNumberModel) spinnerBandpassDropDown.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        downsamplingCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                comboBoxDownSampling.setEnabled(downsamplingCheckBox.isSelected());
                if (downsamplingCheckBox.isSelected()) {
                    downSampling = (comboBoxDownSampling.getSelectedIndex() + 1) * 2;
                } else {
                    downSampling = 1;
                }
                updatePreview();
            }
        });
        varianceFilterCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                variance = varianceFilterCheckBox.isSelected();
                spinnerVariance.setEnabled(variance);
                if (variance) {
                    varianceRadius = ((SpinnerNumberModel) spinnerVariance.getModel()).getNumber().intValue();
                }
                updatePreview();
            }
        });
        expandImagesCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                expandImage = expandImagesCheckBox.isSelected();
                spinnerExpandTiltAxis.setEnabled(expandImage);
                updatePreview();
            }
        });

        spinnerBandpassDropDown.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                bandpassFilterDecrease = ((SpinnerNumberModel) spinnerBandpassDropDown.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerCutMinRadius.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                bandpassFilterMin = ((SpinnerNumberModel) spinnerCutMinRadius.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerCutMaxRadius.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                bandpassFilterMax = ((SpinnerNumberModel) spinnerCutMaxRadius.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerVariance.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                varianceRadius = ((SpinnerNumberModel) spinnerVariance.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerMulticaleLevels.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                multiScaleLevels = ((SpinnerNumberModel) spinnerMulticaleLevels.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerExpandTiltAxis.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                tiltAxis = ((SpinnerNumberModel) spinnerExpandTiltAxis.getModel()).getNumber().doubleValue();
                updatePreview();
            }
        });
        spinnerRoiX.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                roiX = ((SpinnerNumberModel) spinnerRoiX.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        spinnerRoiY.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                roiY = ((SpinnerNumberModel) spinnerRoiY.getModel()).getNumber().intValue();
                updatePreview();
            }
        });
        comboBoxDownSampling.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                downSampling = (comboBoxDownSampling.getSelectedIndex() + 1) * 2;
                updatePreview();
            }
        });
        rootPanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        rootPanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        rootPanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        rootPanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        rootPanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        rootPanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
                ts.setSlice(previewIndex + 1);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    }

    public void setDisplayPreview(boolean display) {
        displayPreview = display;
        if (!displayPreview && preview != null && preview.isVisible()) {
            preview.close();
            preview = null;
        }
    }

    public void updatePreview() {
        if (!displayPreview) return;
        int sx = (roi) ? roiX : ts.getWidth();
        int sy = (roi) ? roiY : ts.getHeight();
        sx /= downSampling;
        sy /= downSampling;

        ts.setAlignmentRoi(roiX, roiY);
        ts.setBinning(downSampling);
        if (bandpassFilter) {
            ts.setBandpassFilter(bandpassFilterMin - bandpassFilterDecrease, bandpassFilterMin, bandpassFilterMax, bandpassFilterMax + bandpassFilterDecrease);
        } else {
            ts.setBandpassFilter(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        if (variance) {
            ts.setVarianceFilterSize(varianceRadius);
        } else {
            ts.setVarianceFilterSize(-1);
        }
        if (expandImage) ts.setTiltAxis(tiltAxis);

        float[] toto = ts.getPixelsForAlignment(previewIndex);
        if (preview == null || sx != preview.getWidth() || sy != preview.getHeight() || !preview.isVisible()) {
            if (preview != null) {
                preview.hide();
            }
            preview = new ImagePlus("what will be used", new FloatProcessor(sx, sy, toto, null));
            preview.show();
            preview.getWindow().setLocationRelativeTo(ts.getWindow());
            //preview.getWindow().setLocationByPlatform(true);
        } else {
            System.out.println("update existing preview");
            preview.getProcessor().setPixels(toto);
            autoAdjust(preview);
            //preview.resetDisplayRange();
            preview.updateAndRepaintWindow();
        }
    }


    /**
     * run the application with previously defined parameters
     *
     * @return true if the application finished successfully
     */
    public boolean run() {
        ts.setIntegerTranslation(integerTranslation);
        if (roiX <= 0) roiX = ts.getWidth();
        if (roiY <= 0) roiY = ts.getHeight();
        ts.setAlignmentRoi(roiX, roiY);
        ts.setBinning(downSampling);
        if (bandpassFilter) {
            double lowcut=Math.max(0,bandpassFilterMin - bandpassFilterDecrease);
            double highcut=Math.min(bandpassFilterMax + bandpassFilterDecrease,ts.getWidth()/2);
            ts.setBandpassFilter(lowcut, bandpassFilterMin, bandpassFilterMax, highcut);
        } else {
            ts.setBandpassFilter(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        if (variance) {
            ts.setVarianceFilterSize(varianceRadius);
        } else {
            ts.setVarianceFilterSize(-1);
        }
        if (expandImage) ts.setTiltAxis(tiltAxis);

        //SingleTiltAlign align = new SingleTiltAlign(ts);
        OutputStreamCapturer capture = new OutputStreamCapturer();

        Chrono time = new Chrono();
        time.start();
        if (!multiScale) multiScaleLevels = 1;
        for (int level = multiScaleLevels; level > 0; level--) {
            ts.setBinning(downSampling * (int) Math.pow(2, level - 1));
            int tmp = (int) Math.pow(2, multiScaleLevels - level);
            ts.setAlignmentRoi(roiX / tmp, roiY / tmp);
            System.out.println("level:" + level + " binning:" + ts.getBinningFactor() + " Roi(" + (roiX / tmp) + "," + (roiY / tmp) + ")");
            if (cumulativeReference) {
                System.out.println("cumulative reference");
                align.computeCumulativeReferenceCrossCorrelation(expandImage);
            } else {
                //ts.crossCorrelationDHT();
                align.crossCorrelationFFT(loop ? 25 : 1);
            }
        }
        time.stop();
        resultString = capture.stop();
        resultString += "\ntotal time to compute:" + time.delayString();
        Alignment ali = ts.getAlignment();
        if (!(ali instanceof AffineAlignment)) ts.setAlignment(new AffineAlignment(ts));
        ((AffineAlignment) ts.getAlignment()).expandForAlignment(false);
        String param = "";
        Rectangle r = ts.getAlignmentRoi();
        if (r.getWidth() > 0) param += " roix=" + r.getWidth() + " roiy=" + r.getHeight();
        double[] bpp = ts.getBandpassParameters();
        if (!Double.isNaN(bpp[0]))
            param += " bandpasslowCut=" + bpp[0] + " bandpasslowKepp=" + bpp[1] + " bandpassHighKeep=" + bpp[2] + " bandpassHighCut=" + bpp[3];
        int bin = ts.getBinningFactor();
        if (bin > 0) param += " binning=" + bin;
        int var = ts.getVarianceFilterSize();
        if (var > 0) param += " variance=" + var;
        if (ts.isIntegerTranslation()) param += " intTranslation";
        if (expandImage) param += " expand";
        if (cumulativeReference) param += "cumulativeReference";
        if (loop) param += " loop";
        System.out.println(param + "\n finished! in " + time.delayString());
//        UserAction ua = new UserAction("translation correction", param, "xcorr", false);
//        ((CommandWorkflow) historyTree).addCommandToHistory(ua, true, true, false, null);
        //((CommandWorkflow) historyTree).autoSave(false, ua);

        ts.setRoi(ts.getTomoJPoints().getRoi(ts.getCurrentSlice() - 1));
        ts.setSlice(ts.getCurrentSlice());
        ts.updateAndDraw();
        ts.threadStats();
        //ts.setVarianceFilterSize(-1);
        IJ.showStatus("correct shift finished");
        return false;
    }

    /**
     * set all the parameters for the application <BR>
     * the order of parameters can be found using getParameterType and getParametersName
     *
     * @param parameters
     */
    public void setParameters(Object... parameters) {
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index] instanceof String) {
                if (((String) parameters[index]).toLowerCase().equals("integertranslation")) {
                    integerTranslation = true;
                } else if (((String) parameters[index]).toLowerCase().equals("roi")) {
                    roi = true;
                    if (parameters[index + 1] instanceof String)
                        roiX = Integer.parseInt((String) parameters[index + 1]);
                    else roiX = (Integer) parameters[index + 1];
                    if (parameters[index + 2] instanceof String)
                        roiY = Integer.parseInt((String) parameters[index + 2]);
                    else roiY = (Integer) parameters[index + 2];
                    index += 2;

                } else if (((String) parameters[index]).toLowerCase().equals("downsampling") || ((String) parameters[index]).toLowerCase().equals("binning")) {
                    if (parameters[index + 1] instanceof String)
                        downSampling = Integer.parseInt((String) parameters[index + 1]);
                    else downSampling = (Integer) parameters[index + 1];
                    index += 1;
                } else if (((String) parameters[index]).toLowerCase().equals("bandpassfilter")) {
                    bandpassFilter = true;
                    if (parameters[index + 1] instanceof String)
                        bandpassFilterMin = Double.parseDouble((String) parameters[index + 1]);
                    else bandpassFilterMin = (Double) parameters[index + 1];
                    if (parameters[index + 2] instanceof String)
                        bandpassFilterMax = Double.parseDouble((String) parameters[index + 2]);
                    else bandpassFilterMax = (Double) parameters[index + 2];
                    if (parameters[index + 3] instanceof String)
                        bandpassFilterDecrease = Double.parseDouble((String) parameters[index + 3]);
                    else bandpassFilterDecrease = (Double) parameters[index + 3];
                    index += 3;
                } else if (((String) parameters[index]).toLowerCase().equals("variancefilter")) {
                    variance = true;
                    if (parameters[index + 1] instanceof String)
                        varianceRadius = Integer.parseInt((String) parameters[index + 1]);
                    else varianceRadius = (Integer) parameters[index + 1];
                    index += 1;
                } else if (((String) parameters[index]).toLowerCase().equals("expandimage")) {
                    expandImage = true;
                    if (parameters[index + 1] instanceof String)
                        tiltAxis = Double.parseDouble((String) parameters[index + 1]);
                    else tiltAxis = (Double) parameters[index + 1];
                    index += 1;
                } else if (((String) parameters[index]).toLowerCase().equals("multiscale")) {
                    multiScale = true;
                    if (parameters[index + 1] instanceof String)
                        multiScaleLevels = Integer.parseInt((String) parameters[index + 1]);
                    else multiScaleLevels = (Integer) parameters[index + 1];
                    index += 1;
                } else if (((String) parameters[index]).toLowerCase().equals("cumulativereference")) {
                    cumulativeReference = true;
                } else if (((String) parameters[index]).toLowerCase().equals("loop")) {
                    loop = true;
                }
            }
        }
    }

    /**
     * text to display the help / man of the application
     *
     * @return a String containing the help of the application
     */
    public static String help() {
        //@TODO
        return "automatic alignment by cross-correlation\n" +
                "parameters that can be given\n" +
                "integertranslation : the resulting translation will be integer value instead of looking for floating point position of peak summit\n" +
                "roi rwidth rheight : take the central part of images of size rwidth rheight (integers) to compute cross-correlation\n" +
                "downsampling value : reduce the size of images for computation. The value (integer) corresponds to the factor of reduction, usually 2,4,8...\n" +
                "bandpassfilter min max deacrease : apply a bandpassfilter on images (after roi and downsampling if any). The 2 first values (double) correspond to the radius of the band in pixels [minimum maximum]. The third value corresponds (double) to the sinusoidal decrease radius (in pixels) to prevent artifacts. \n" +
                "variancefilter radius : apply a variance filter on images with the given radius (integer). It results in contours images\n" +
                "expandimage tiltaxisvalue : expands the image to correct the streching due to tilt. To do this correctly the tilt axis needs to be given as angle (double) from vertical axis.\n" +
                "multiscale levels : apply a multiscale approach with the given number of level.\n" +
                "cumulativereference : the processing is not between consecutive images but using central image as reference to which is added the newly aligned images.\n" +
                "loop : refine alignements by doing the cross-correlation as many times as needed to stabilize.\n" +
                "";
    }

    public String name() {
        return "automatic alignment by cross-correlation";
    }

    /**
     * if the application gives some results, use this function to get all of them
     *
     * @return the results of the application
     */
    public ArrayList<Object> getResults() {
        ArrayList<Object> result = new ArrayList<Object>();
        result.add(resultString);
        return result;
    }

    /**
     * get the type of parameters
     *
     * @return an ArrayList containing Objects of the same type as the parameters in the correct order
     */
    public ArrayList<Object> getParametersType() {
        //@TODO
        return null;
    }

    /**
     * get the names of the parameters
     *
     * @return an arrayList containing a string for each parameter corresponding to its name in the correct order
     */
    public ArrayList<String> getParametersName() {
        //@TODO
        return null;
    }

    /**
     * converts the parameters as a string to display informations
     *
     * @return
     */
    public String getParametersValuesAsString() {
        //@TODO
        String param = "";
        Rectangle r = ts.getAlignmentRoi();
        if (r.getWidth() > 0) param += " roix=" + r.getWidth() + " roiy=" + r.getHeight();
        double[] bpp = ts.getBandpassParameters();
        if (!Double.isNaN(bpp[0]))
            param += " bandpasslowCut=" + bpp[0] + " bandpasslowKepp=" + bpp[1] + " bandpassHighKeep=" + bpp[2] + " bandpassHighCut=" + bpp[3];
        int bin = ts.getBinningFactor();
        if (bin > 0) param += " binning=" + bin;
        int var = ts.getVarianceFilterSize();
        if (var > 0) param += " variance=" + var;
        if (ts.isIntegerTranslation()) param += " intTranslation";
        if (expandImage) param += " expand";
        if (cumulativeReference) param += "cumulativeReference";
        if (loop) param += " loop";
        if (multiScale) param += " multiscale=" + multiScaleLevels;
        return param;
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

    void autoAdjust(ImagePlus imp) {

        Calibration cal = imp.getCalibration();
        imp.setCalibration(null);
        ImageStatistics stats = imp.getStatistics(); // get uncalibrated stats
        imp.setCalibration(cal);
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
            imp.setDisplayRange(min, max);
        } else {
            imp.resetDisplayRange();
        }

    }

    private void createUIComponents() {
        spinnerExpandTiltAxis = new JSpinner(new SpinnerNumberModel(0, -180, 180, 0.001));
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
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(8, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Fast Alignment Using Cross-Correlation"));
        loopUntilStabilizationCheckBox = new JCheckBox();
        loopUntilStabilizationCheckBox.setText("Loop until stabilization");
        rootPanel.add(loopUntilStabilizationCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("levels");
        panel1.add(label1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerMulticaleLevels = new JSpinner();
        spinnerMulticaleLevels.setEnabled(false);
        panel1.add(spinnerMulticaleLevels, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        multiscaleCheckBox = new JCheckBox();
        multiscaleCheckBox.setText("Multiscale");
        panel1.add(multiscaleCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel2, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        spinnerRoiX = new JSpinner();
        spinnerRoiX.setEnabled(false);
        panel2.add(spinnerRoiX, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerRoiY = new JSpinner();
        spinnerRoiY.setEnabled(false);
        panel2.add(spinnerRoiY, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        roiCenteredCheckBox = new JCheckBox();
        roiCenteredCheckBox.setText("Roi centered");
        panel2.add(roiCenteredCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel2.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 8, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel3, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        spinnerCutMinRadius = new JSpinner();
        spinnerCutMinRadius.setEnabled(false);
        panel3.add(spinnerCutMinRadius, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerCutMaxRadius = new JSpinner();
        spinnerCutMaxRadius.setEnabled(false);
        panel3.add(spinnerCutMaxRadius, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        bandpassFilterCheckBox = new JCheckBox();
        bandpassFilterCheckBox.setText("Bandpass filter");
        panel3.add(bandpassFilterCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel3.add(spacer3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("min");
        panel3.add(label2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("max");
        panel3.add(label3, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("drop down");
        panel3.add(label4, new GridConstraints(0, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerBandpassDropDown = new JSpinner();
        spinnerBandpassDropDown.setEnabled(false);
        panel3.add(spinnerBandpassDropDown, new GridConstraints(0, 7, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel4, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        comboBoxDownSampling = new JComboBox();
        comboBoxDownSampling.setEnabled(false);
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("2");
        defaultComboBoxModel1.addElement("4");
        defaultComboBoxModel1.addElement("8");
        defaultComboBoxModel1.addElement("16");
        comboBoxDownSampling.setModel(defaultComboBoxModel1);
        panel4.add(comboBoxDownSampling, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        downsamplingCheckBox = new JCheckBox();
        downsamplingCheckBox.setText("Downsampling");
        panel4.add(downsamplingCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel5, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        varianceFilterCheckBox = new JCheckBox();
        varianceFilterCheckBox.setText("Variance filter");
        panel5.add(varianceFilterCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel5.add(spacer4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        spinnerVariance = new JSpinner();
        spinnerVariance.setEnabled(false);
        panel5.add(spinnerVariance, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        integerTranslationCheckBox = new JCheckBox();
        integerTranslationCheckBox.setText("Integer translation");
        rootPanel.add(integerTranslationCheckBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel6, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        expandImagesCheckBox = new JCheckBox();
        expandImagesCheckBox.setText("Expand images");
        panel6.add(expandImagesCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerExpandTiltAxis.setEnabled(false);
        panel6.add(spinnerExpandTiltAxis, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cumulativeReferenceCheckBox = new JCheckBox();
        cumulativeReferenceCheckBox.setText("Cumulative reference");
        panel6.add(cumulativeReferenceCheckBox, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel6.add(spacer5, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        rootPanel.add(spacer6, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }

}
