/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj.gui;

import fr.curie.eftemtomoj.EftemDataset;
import fr.curie.eftemtomoj.EftemDataset.InvalidInputException;
import fr.curie.eftemtomoj.JeolBracketIO;
import fr.curie.eftemtomoj.TiltSeries;
import fr.curie.eftemtomoj.testing.Phantoms;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.plugin.FolderOpener;
import ij.process.ImageProcessor;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author Nick Aschman
 */
public class DatasetPanel extends WizardPage {
    private JTable acquisitionTable;
    private JPanel panel1;
    private JButton addButton;
    private JButton removeButton;
    private JButton setButton;
    private JComboBox tiltAnglesComboBox;
    private JButton saveButton;

    private JPopupMenu addPopupMenu;
    private JMenu addImageMenu;
    private final DatasetTableModel tableModel = new DatasetTableModel();

    // Tomography parameters
    private float[] tomoTiltAngles;

    public DatasetPanel(WizardDialog dlg) {
        super(dlg, "DATASET_PAGE", "Load dataset");

        $$$setupUI$$$();
        addButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                // Initialise addImageMenu
                int[] idlist = WindowManager.getIDList();

                if (idlist != null) {
                    addImageMenu.removeAll();
                    JMenuItem mi;
                    for (final int id : idlist) {
                        mi = new JMenuItem(WindowManager.getImage(id).getShortTitle());
                        //mi.setActionCommand(Integer.toString(id));
                        mi.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent ae) {
                                IJ.selectWindow(id);
                                ImagePlus imptmp = IJ.getImage();
                                ImageStack tmp = imptmp.getImageStack();
                                TiltSeries series = new TiltSeries(tmp.getWidth(), tmp.getHeight());
                                series.setTitle(imptmp.getTitle());
                                for (int i = 1; i <= tmp.getSize(); i++) {
                                    series.addSlice(tmp.getSliceLabel(i), tmp.getProcessor(i).convertToFloat());
                                }
                                tableModel.addItem(series);
                                //imptmp.hide();
                            }
                        });
                        addImageMenu.add(mi);
                    }
                }

                // Pop up popup
                int x = addButton.getX();
                int y = addButton.getY() + addButton.getHeight();
                addPopupMenu.show(panel1, x, y);
            }
        });
        removeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (acquisitionTable.getSelectedRows().length > 0) {
                    tableModel.removeItems(acquisitionTable.getSelectedRows());
                    dialog.setCurrentDataset(null);// Invalidate dataset
                }
            }
        });
        setButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                onSetTomographyParameters();
            }
        });

        setTomoTiltAngles(null);

        saveButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                onSaveProject();

            }
        });
    }

    private void createUIComponents() {
        acquisitionTable = new JTable(tableModel);
        addPopupMenu = new JPopupMenu();
        addImageMenu = new JMenu("Image or Stack");

        // Initialise Add-Menu
        JMenuItem menuItem;

        menuItem = new JMenuItem("EFTEM TomoJ project");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                onLoadProject();
            }
        });
        addPopupMenu.add(menuItem);

        menuItem = new JMenuItem("Jeol Bracket Folder...");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                onAddFolder("jeolBracketFolder");
            }
        });
        addPopupMenu.add(menuItem);

        menuItem = new JMenuItem("Image Stack (2D EFTEM)...");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                onAddFile();
            }
        });
        addPopupMenu.add(menuItem);

        menuItem = new JMenuItem("Image Stacks (3D EFTEM)...");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                onAddFiles();
            }
        });
        addPopupMenu.add(menuItem);

        addPopupMenu.addSeparator();

        addPopupMenu.add(addImageMenu);

        addPopupMenu.addSeparator();

        menuItem = new JMenuItem("Generate Phantoms (Power func.; Sharp peak)...");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                onGeneratePhantomSeries("spower");
            }
        });
        addPopupMenu.add(menuItem);

        menuItem = new JMenuItem("Generate Phantoms (Power func.; Broad peak)...");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                onGeneratePhantomSeries("bpower");
            }
        });
        addPopupMenu.add(menuItem);

        menuItem = new JMenuItem("Generate Phantoms (Exp. func.; Sharp peak)...");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                onGeneratePhantomSeries("sexp");
            }
        });
        addPopupMenu.add(menuItem);

        menuItem = new JMenuItem("Generate Phantoms (Exp. func.; Broad peak)...");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                onGeneratePhantomSeries("bexp");
            }
        });
        addPopupMenu.add(menuItem);
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
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(6, 6, new Insets(0, 0, 0, 0), -1, -1));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, new com.intellij.uiDesigner.core.GridConstraints(3, 0, 1, 6, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane1.setViewportView(acquisitionTable);
        addButton = new JButton();
        addButton.setIcon(new ImageIcon(getClass().getResource("/list-add.png")));
        addButton.setText("");
        panel1.add(addButton, new com.intellij.uiDesigner.core.GridConstraints(4, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setFont(new Font(label1.getFont().getName(), Font.BOLD, label1.getFont().getSize()));
        label1.setText("Energy windows and acquisition parameters:");
        panel1.add(label1, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 6, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        removeButton = new JButton();
        removeButton.setIcon(new ImageIcon(getClass().getResource("/list-remove.png")));
        removeButton.setText("");
        panel1.add(removeButton, new com.intellij.uiDesigner.core.GridConstraints(4, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

        saveButton = new JButton();
        saveButton.setText("save project");
        panel1.add(saveButton, new com.intellij.uiDesigner.core.GridConstraints(4, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panel1.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(4, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer2 = new com.intellij.uiDesigner.core.Spacer();
        panel1.add(spacer2, new com.intellij.uiDesigner.core.GridConstraints(5, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        setButton = new JButton();
        setButton.setText("Edit...");
        panel1.add(setButton, new com.intellij.uiDesigner.core.GridConstraints(4, 5, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tiltAnglesComboBox = new JComboBox();
        panel1.add(tiltAnglesComboBox, new com.intellij.uiDesigner.core.GridConstraints(4, 4, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Preview tilt angle:");
        panel1.add(label2, new com.intellij.uiDesigner.core.GridConstraints(4, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setBackground(UIManager.getColor("Button.background"));
        label3.setFont(new Font("Helvetica Neue", Font.BOLD, 14));
        label3.setForeground(new Color(-8874943));
        label3.setOpaque(true);
        label3.setText("Welcome to EFTEM-TomoJ 1.02 ");
        panel1.add(label3, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 6, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JSeparator separator1 = new JSeparator();
        panel1.add(separator1, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 6, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        label2.setLabelFor(tiltAnglesComboBox);
    }

    public void setTomoTiltAngles(float[] tomoTiltAngles) {
        this.tomoTiltAngles = tomoTiltAngles;

        if (tomoTiltAngles == null) {
            tiltAnglesComboBox.setModel(new DefaultComboBoxModel(new String[]{"(N/A)"}));
        } else {
            int minIndex = 0;
            float value, min = Float.NaN;
            String[] tilts = new String[tomoTiltAngles.length];

            for (int i = 0; i < tilts.length; i++) {
                tilts[i] = Float.toString(tomoTiltAngles[i]) + "°";

                value = Math.abs(tomoTiltAngles[i]);
                if (Float.isNaN(min) || value < min) {
                    min = value;
                    minIndex = i;
                }
            }
            tiltAnglesComboBox.setModel(new DefaultComboBoxModel(tilts));
            tiltAnglesComboBox.setSelectedIndex(minIndex);
        }
    }

    @Override
    public JComponent getComponent() {
        return panel1;
    }

    @Override
    public boolean validate() {

        // Get image data
        TiltSeries[] data = tableModel.getData();

        if (data.length == 0) {
            JOptionPane.showMessageDialog(dialog, "Table contains no data.", "Validation error", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // Create dataset, set tomography parameters & report validation errors
        EftemDataset ds;
        try {
            ds = new EftemDataset(data);

            if (tomoTiltAngles != null) {
                ds.setTiltAngles(tomoTiltAngles);
            }
        } catch (InvalidInputException ex) {
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Validation error", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // Store reference to dataset in WizardDialog
        dialog.setCurrentDataset(ds);

        // Save original stack
        dialog.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        ds.save("original");
        dialog.setCursor(Cursor.getDefaultCursor());

        return true;
    }

    @Override
    public void activate() {
        // Do nothing
    }

    private void onSetTomographyParameters() {

        // Validate tilt series dimensions
        TiltSeries[] data = tableModel.getData();

        if (data.length == 0) {
            JOptionPane.showMessageDialog(dialog, "Table contains no data.", "Validation error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            EftemDataset.checkTiltSeries(data);
        } catch (InvalidInputException ex) {
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Validation error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get tilt series size
        TiltParameterDialog tpDlg = new TiltParameterDialog(dialog, data[0].getSize());
        tpDlg.setTiltAngles(tomoTiltAngles);

        if (tpDlg.showModal() == TiltParameterDialog.OK_OPTION) {
            setTomoTiltAngles(tpDlg.getTiltAngles());
        }
    }

    private void onGeneratePhantomSeries(String type) {
        if (type.equalsIgnoreCase("spower"))
            tableModel.addItems(Phantoms.generateSeries(Phantoms.POWER_FUNCTION, Phantoms.SHARP_PEAK, 10)[1]);
        else if (type.equalsIgnoreCase("bpower"))
            tableModel.addItems(Phantoms.generateSeries(Phantoms.POWER_FUNCTION, Phantoms.BROAD_PEAK, 10)[1]);
        else if (type.equalsIgnoreCase("sexp"))
            tableModel.addItems(Phantoms.generateSeries(Phantoms.EXP_FUNCTION, Phantoms.SHARP_PEAK, 10)[1]);
        else if (type.equalsIgnoreCase("bexp"))
            tableModel.addItems(Phantoms.generateSeries(Phantoms.EXP_FUNCTION, Phantoms.BROAD_PEAK, 10)[1]);
    }

    private void onAddFile() {
        JFileChooser chooser = new JFileChooser(OpenDialog.getDefaultDirectory());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(dialog) == JFileChooser.CANCEL_OPTION || chooser.getSelectedFile() == null) {
            return;

        }
        String dir = chooser.getCurrentDirectory().getAbsolutePath() + System.getProperty("file.separator");
        OpenDialog.setDefaultDirectory(dir);
        OpenDialog.setLastDirectory(dir);
        ImagePlus imp = IJ.openImage(chooser.getSelectedFile().getAbsolutePath());
        ImageStack stack = imp.getImageStack();
        TiltSeries[] series = new TiltSeries[stack.getSize()];
        for (int i = 0; i < stack.getSize(); i++) {
            series[i] = new TiltSeries(stack.getWidth(), stack.getHeight());
            series[i].addSlice("", stack.getProcessor(i + 1).convertToFloat());
            String label = stack.getSliceLabel(i + 1);
            if (label != null) {
                series[i].setTitle(label);
                int indexEV = label.toLowerCase().lastIndexOf("ev");
                if (indexEV > 0) {
                    String sub = label.substring(0, indexEV - 1);
                    series[i].setEnergyShift(Float.parseFloat(sub.replace(',', '.')));
                }
            } else {
                series[i].setTitle(imp.getTitle());
            }

        }

        tableModel.addItems(series);
    }

    /**
     * 3D EFTEM load all tif stack files from a directory and assign each one to a different energy
     */
    private void onAddFiles() {
        JFileChooser chooser = new JFileChooser(OpenDialog.getDefaultDirectory());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(dialog) == JFileChooser.CANCEL_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        File folder = chooser.getSelectedFile();

        if (!folder.isDirectory()) {
            System.out.println("Not a directory");
            return;
        }
        String dir = chooser.getCurrentDirectory().getAbsolutePath() + System.getProperty("file.separator");
        OpenDialog.setDefaultDirectory(dir);
        OpenDialog.setLastDirectory(dir);
        File[] files = folder.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return file.getName().endsWith("tif");
            }
        });

        TiltSeries[] series = new TiltSeries[files.length];

        for (int f = 0; f < files.length; f++) {
            ImageStack stack = IJ.openImage(files[f].getAbsolutePath()).getImageStack();
            series[f] = new TiltSeries(stack.getWidth(), stack.getHeight());
            series[f].setTitle(files[f].getName());
            for (int i = 0; i < stack.getSize(); i++) {
                series[f].addSlice("", stack.getProcessor(i + 1).convertToFloat());
            }
        }

        tableModel.addItems(series);
    }


    private void onAddFolder(String type) {
        JFileChooser chooser = new JFileChooser(OpenDialog.getDefaultDirectory());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(dialog) == JFileChooser.CANCEL_OPTION || chooser.getSelectedFile() == null) {
            return;
        }
        String dir = chooser.getCurrentDirectory().getAbsolutePath() + System.getProperty("file.separator");
        OpenDialog.setDefaultDirectory(dir);
        OpenDialog.setLastDirectory(dir);
        final File selectedFolder = chooser.getSelectedFile();
        TiltSeries[] images = null;

        // Read Jeol bracket folder
        if (type.equalsIgnoreCase("jeolBracketFolder")) {

            final JeolBracketIO reader;

            try {
                reader = new JeolBracketIO(selectedFolder);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Reading Jeol Data", JOptionPane.ERROR_MESSAGE);
                return;
            }

            final WizardApprentice worker = new WizardApprentice<TiltSeries[]>(dialog, "Loading image files...") {
                @Override
                protected TiltSeries[] doInBackground() throws Exception {
                    reader.addObserver(this);
                    return reader.readBracketFolders();
                }
            };
            worker.go();

            try {
                images = (TiltSeries[]) worker.get();
            } catch (InterruptedException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
                return;
            } catch (ExecutionException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
                return;
            }
            /*if(images==null){
                System.out.println("images are null");
            }else{
                System.out.println("there is "+images.length+" images");
                for(int i=0;i<images.length;i++){
                    System.out.println(images[i].getEnergyShift());
                }
            }*/

            setTomoTiltAngles(reader.getTiltAngles());
        }

        // Add images to data table
        if (images == null || images.length == 0) {
            JOptionPane.showMessageDialog(dialog, "No image files found in " + selectedFolder.getPath() + ".");
        } else {
            tableModel.addItems(images);
        }

    }

    private void onLoadProject() {
        JFileChooser chooser = new JFileChooser(OpenDialog.getDefaultDirectory());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(dialog) == JFileChooser.CANCEL_OPTION || chooser.getSelectedFile() == null) {
            return;

        }
        String dir = chooser.getCurrentDirectory().getAbsolutePath() + System.getProperty("file.separator");
        String name = chooser.getSelectedFile().getName();
        OpenDialog.setDefaultDirectory(dir);
        OpenDialog.setLastDirectory(dir);

        SAXBuilder saxb = new SAXBuilder();
        try {
            System.out.println("open xml file " + dir + name);
            Document doc = saxb.build(new File(dir + name));
            Element root = doc.getRootElement();
            List energies = root.getChildren("EnergyImage");
            System.out.println("there are " + energies.size() + " energies");

            for (Object energy : energies) {
                Element nrj = (Element) energy;
                Element imgs = nrj.getChild("Images");
                TiltSeries ts = null;
                System.out.println("images:" + imgs.getText());
                if (imgs.getText().trim().compareToIgnoreCase("undefined") == 0) {
                    OpenDialog od = new OpenDialog("open image corresponding to " + nrj.getChild("EnergyShift").getText() + " eV", "");
                    dir = od.getDirectory();
                    name = od.getFileName();
                    if (dir == null || name == null) {
                        return;
                    }
                    ImagePlus imp = IJ.openImage(dir + name);
                    ts = new TiltSeries(imp.getWidth(), imp.getHeight());
                    if (imp.getNSlices() > 1) {
                        ImageStack is = imp.getImageStack();
                        for (int i = 1; i <= is.getSize(); i++) {
                            ts.addSlice(is.getSliceLabel(i), is.getProcessor(i).convertToFloat());
                        }
                    } else {
                        ts.addSlice("", imp.getProcessor().convertToFloat());
                        //IJ.showMessage("should check if more than one image");
                        final String nameWithoutExtension = name.substring(0, name.lastIndexOf('.'));
                        final String extension = name.substring(name.lastIndexOf('.', name.length() - 1));
                        System.out.println(name + " --> " + nameWithoutExtension);
                        System.out.println("extension : " + extension);
                        char[] tmp = nameWithoutExtension.toCharArray();
                        if (Character.isDigit(tmp[tmp.length - 1])) {
                            System.out.println("name finishes with digit open all images");
                            int offset = 1;
                            while (Character.isDigit(tmp[tmp.length - offset - 1])) {
                                offset++;
                            }
                            System.out.println("there is " + offset + " digits");
                            File directory = new File(dir);
                            final String filter = nameWithoutExtension.substring(0, nameWithoutExtension.length() - offset - 1);
                            System.out.println("filter=" + filter);
                            String[] list = directory.list(new FilenameFilter() {
                                public boolean accept(File dir, String name) {
                                    return name.startsWith(filter) && name.endsWith(extension);
                                }

                            });
                            FolderOpener fo = new FolderOpener();
                            list = fo.trimFileList(list);
                            list = fo.sortFileList(list);
                            for (String st : list) {
                                System.out.println(st);
                                imp = IJ.openImage(dir + st);
                                ts.addSlice("", imp.getProcessor());
                            }

                        }


                    }


                } else {
                    List img = imgs.getChildren("Image");
                    for (Object anImg : img) {
                        Element image = (Element) anImg;
                        String path = image.getText();
                        if (path.startsWith(".")) path = dir + path;
                        System.out.println("open image " + path);
                        ImagePlus imp = IJ.openImage(path);
                        if (ts == null) ts = new TiltSeries(imp.getWidth(), imp.getHeight());
                        ts.addSlice(imp.getShortTitle(), imp.getProcessor());
                    }
                }
                ts.setTitle(nrj.getChild("Title").getText());
                ts.setEnergyShift(Float.parseFloat(nrj.getChild("EnergyShift").getText()));
                ts.setSlitWidth(Float.parseFloat(nrj.getChild("SlitWidth").getText()));
                ts.setExposureTime(Float.parseFloat(nrj.getChild("ExposureTime").getText()));
                ts.setComment(nrj.getChild("Comment").getText());
                ts.setUseForMapping(Boolean.parseBoolean(nrj.getChild("UseForMapping").getText()));
                tableModel.addItem(ts);

            }

            Element tilts = root.getChild("TiltAngles");
            if (tilts != null) {
                System.out.println("tilt angles are defined: reading...");
                String[] angles = tilts.getText().trim().split("\\s");
                tomoTiltAngles = new float[angles.length];
                for (int i = 0; i < angles.length; i++) {
                    tomoTiltAngles[i] = Float.parseFloat(angles[i]);
                }
                setTomoTiltAngles(tomoTiltAngles);
            }
        } catch (Exception e) {
            System.out.println("error while opening xml file : " + e);
        }

    }

    private void onSaveProject() {
        SaveDialog sd = new SaveDialog("save project as...", "eftem_project", ".xml");
        String dir = sd.getDirectory();
        String name = sd.getFileName();
        if (dir == null || name == null) {
            return;
        }
        //int ok = JOptionPane.showConfirmDialog(saveButton, "do you want to saves the images?", "save images?", JOptionPane.YES_NO_OPTION);
        //boolean saveImages = (ok == JOptionPane.OK_OPTION);
        boolean saveImages = true;
        String nameWithoutExtension = name.substring(0, name.lastIndexOf('.'));
        if (saveImages) {
            //IJ.showMessage("saving of images not yet implemented");
            System.out.println("saving images");
        }

        TiltSeries[] data = tableModel.getData();
        if (data == null || data.length == 0) {
            IJ.showMessage("there is nothing to save!");
            return;
        }
        Element root = new Element("EnergiesImages");
        Document document = new Document(root);

        for (int i = 0; i < data.length; i++) {
            Element nrj = new Element("EnergyImage");
            root.addContent(nrj);

            Element title = new Element("Title");
            title.setText(data[i].getTitle());
            nrj.addContent(title);

            Element nrjshift = new Element("EnergyShift");
            nrjshift.setText("" + data[i].getEnergyShift());
            nrj.addContent(nrjshift);

            Element slitwidth = new Element("SlitWidth");
            slitwidth.setText("" + data[i].getSlitWidth());
            nrj.addContent(slitwidth);

            Element exposure = new Element("ExposureTime");
            exposure.setText("" + data[i].getExposureTime());
            nrj.addContent(exposure);

            Element mapping = new Element("UseForMapping");
            mapping.setText("" + data[i].isUsedForMapping());
            nrj.addContent(mapping);

            Element comment = new Element("Comment");
            comment.setText(data[i].getComment());
            nrj.addContent(comment);

            Element images = new Element("Images");
            if (saveImages) {
                File f = new File(dir + i);
                if (!f.exists() && !f.mkdirs()) {
                    IJ.error("could not create the directory");
                    return;
                }
                for (int j = 1; j <= data[i].getSize(); j++) {
                    Element img = new Element("Image");
                    ImageProcessor ip = data[i].getProcessor(j);
                    ImagePlus imp = new ImagePlus(data[i].getSliceLabel(j), ip);

                    String localpath = "." + System.getProperty("file.separator") + i + System.getProperty("file.separator") + nameWithoutExtension + "_" + i + "_" + j + ".tif";
                    System.out.println("saving image: " + localpath);
                    IJ.save(imp, dir + localpath);
                    img.setText(localpath);
                    images.addContent(img);
                }
            } else {
                images.setText("undefined");
            }
            nrj.addContent(images);
        }
        if (tomoTiltAngles != null) {
            Element tilts = new Element("TiltAngles");
            String angles = "";
            for (float tomoTiltAngle : tomoTiltAngles) {
                angles += tomoTiltAngle + " ";
            }
            tilts.setText(angles);
            root.addContent(tilts);
        }

        try {
            XMLOutputter output = new XMLOutputter(Format.getPrettyFormat());
            output.output(document, new FileOutputStream(dir + name));
        } catch (IOException e) {
            System.out.println("error while saving xml file : " + e);
        }


    }

    public static class DatasetTableModel extends AbstractTableModel {

        private static final int TITLE = 0;
        private static final int ENERGYSHIFT = 1;
        private static final int SLITWIDTH = 2;
        private static final int EXPOSURETIME = 3;
        private static final int USEFORMAPPING = 4;
        private static final int SERIESSIZE = 5;
        private static final int COMMENT = 6;

        private static final String[] COLUMN_NAMES = new String[7];
        private static final Class[] COLUMN_CLASSES = new Class[7];

        static {
            COLUMN_NAMES[TITLE] = "Title";
            COLUMN_NAMES[ENERGYSHIFT] = "Energy shift (eV)";
            COLUMN_NAMES[SLITWIDTH] = "Slit width (eV)";
            COLUMN_NAMES[EXPOSURETIME] = "Exposure time (s)";
            COLUMN_NAMES[USEFORMAPPING] = "Mapping";
            COLUMN_NAMES[SERIESSIZE] = "#Tilts";
            COLUMN_NAMES[COMMENT] = "Comment";

            COLUMN_CLASSES[TITLE] = String.class;
            COLUMN_CLASSES[ENERGYSHIFT] = Float.class;
            COLUMN_CLASSES[SLITWIDTH] = Float.class;
            COLUMN_CLASSES[EXPOSURETIME] = Float.class;
            COLUMN_CLASSES[USEFORMAPPING] = Boolean.class;
            COLUMN_CLASSES[SERIESSIZE] = Integer.class;
            COLUMN_CLASSES[COMMENT] = String.class;
        }

        private List<TiltSeries> data = new ArrayList<TiltSeries>();

        // User methods

        public void addItem(TiltSeries series) {
            //System.out.println("adding "+series.getEnergyShift());
            data.add(series);
            this.fireTableDataChanged();
        }

        public void addItems(TiltSeries[] series) {
            for (TiltSeries s : series) {
                //System.out.println("adding all"+s.getEnergyShift());
                data.add(s);
            }
            this.fireTableDataChanged();
        }

        public void removeItems(int[] rows) {
            List<TiltSeries> items = new ArrayList<TiltSeries>();
            for (int row : rows) {
                items.add(data.get(row));
            }
            data.removeAll(items);

            this.fireTableDataChanged();
        }

        public void clear() {
            int size = data.size();
            data.clear();
            this.fireTableRowsDeleted(0, size);
        }

        public TiltSeries[] getData() {
            return data.toArray(new TiltSeries[]{});
        }

        // Implemented & overriden methods from TableModel

        public int getRowCount() {
            return data.size();
        }

        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        public Object getValueAt(int row, int col) {
            switch (col) {
                case TITLE:
                    return data.get(row).getTitle();
                case ENERGYSHIFT:
                    return data.get(row).getEnergyShift();
                case SLITWIDTH:
                    return data.get(row).getSlitWidth();
                case EXPOSURETIME:
                    return data.get(row).getExposureTime();
                case USEFORMAPPING:
                    return data.get(row).isUsedForMapping();
                case SERIESSIZE:
                    return data.get(row).getSize();
                case COMMENT:
                    return data.get(row).getComment();
                default:
                    return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return COLUMN_CLASSES[col];
        }

        @Override
        public String getColumnName(int col) {
            return COLUMN_NAMES[col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            switch (col) {
                case ENERGYSHIFT:
                case SLITWIDTH:
                case EXPOSURETIME:
                case USEFORMAPPING:
                case COMMENT:
                    return true;

                case TITLE:
                case SERIESSIZE:
                default:
                    return false;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            switch (col) {
                case ENERGYSHIFT:
                    data.get(row).setEnergyShift((Float) value);
                    return;
                case SLITWIDTH:
                    data.get(row).setSlitWidth((Float) value);
                    return;
                case EXPOSURETIME:
                    data.get(row).setExposureTime((Float) value);
                    return;
                case USEFORMAPPING:
                    data.get(row).setUseForMapping((Boolean) value);
                    return;
                case COMMENT:
                    data.get(row).setComment((String) value);
            }
        }
    }
}
