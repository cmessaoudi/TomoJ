package fr.curie.plotj;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import ij.IJ;
import ij.io.SaveDialog;
import ij.measure.ResultsTable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Arrays;


/**
 * This class is used to plot graphs dynamically<BR>
 * To use it you can do as follow:<BR>
 * PlotWindow2 pw=new PlotWindow2();<BR>
 * pw.addPlot(xs,ys, COLOR.RED, "test");<BR>
 * <p/>
 * this will display the graph window with the trace named "test" in RED with the array of double xs as abscissas and the array of double ys as ordinates.
 * User: MESSAOUDI C�dric
 * Date: 21 mai 2008
 * Time: 15:08:04
 */
public class PlotWindow2 extends JFrame implements ItemListener, MouseInputListener, ActionListener {
    private JPanel panel1;
    private JCheckBox blackBackgroundCheckBox;
    private JCheckBox showAxesCheckBox;
    private JTextPane status;
    private Plot graph;
    JMenu menuView;
    private int xpressed, ypressed;

    /**
     * create a new window and put it directly to visible
     */
    public PlotWindow2() {
        this("PlotJ");
    }

    public PlotWindow2(String title) {
        super(title);
        setContentPane(panel1);
        setJMenuBar(createMenu());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        setVisible(true);
        blackBackgroundCheckBox.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (blackBackgroundCheckBox.isSelected()) {
                    graph.setBackgroundColor(Color.BLACK);
                } else {
                    graph.setBackgroundColor(Color.WHITE);
                }
                graph.repaint();
            }
        });
        showAxesCheckBox.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (showAxesCheckBox.isSelected()) {
                    graph.showAxes(true);
                } else {
                    graph.showAxes(false);
                }
                graph.repaint();
            }
        });
        graph.addMouseListener(this);
        graph.addMouseMotionListener(this);
    }

    /**
     * main function intended for testing interface
     *
     * @param args open a file given in parameter
     */
    public static void main(String[] args) {
        PlotWindow2 toto = new PlotWindow2();
        if (args != null) {
            try {
                toto.parse(new File(args[0]));
            } catch (Exception e) {
                System.out.println("" + e);
            }
        } else {
            Double[] xtest = {-15d, 5d, 10d, 15d, 17.5, 21.5};
            Double[] ytest = {20d, 50d, 30d, 45.8, 37.5, 13.2};
            toto.addPlot(xtest, ytest, Color.RED);
            Double[] xtest2 = {7.2, 8.3, 12.5, 15d, 16d, 21.5};
            Double[] ytest2 = {36.5, 24.1, 15.2, -7.6, 60.7, 13.2};
            toto.addPlot(xtest2, ytest2, Color.BLUE);
        }

    }

    /**
     * create the menu
     *
     * @return
     */
    private JMenuBar createMenu() {
        JMenuBar men = new JMenuBar();
        //first menu
        JMenu menu1 = new JMenu("File");
        menu1.setMnemonic(KeyEvent.VK_F);
        JMenuItem openResultMI = new JMenuItem("Load from result window...", KeyEvent.VK_L);
        openResultMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, ActionEvent.CTRL_MASK));
        openResultMI.addActionListener(this);
        menu1.add(openResultMI);
        JMenuItem openMI = new JMenuItem("Open new trace...", KeyEvent.VK_O);
        openMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        openMI.addActionListener(this);
        menu1.add(openMI);
        JMenuItem removeMI = new JMenuItem("Delete trace...", KeyEvent.VK_D);
        removeMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, ActionEvent.CTRL_MASK));
        removeMI.addActionListener(this);
        menu1.add(removeMI);

        JMenuItem abscisseMI = new JMenuItem("Define trace as abscissa...", KeyEvent.VK_A);
        abscisseMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
        abscisseMI.addActionListener(this);
        menu1.add(abscisseMI);

        JMenuItem constantMI = new JMenuItem("Create constant trace...", KeyEvent.VK_C);
        constantMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        constantMI.addActionListener(this);
        menu1.add(constantMI);

        JMenuItem saveMI = new JMenuItem("Save traces in File...", KeyEvent.VK_S);
        saveMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        saveMI.addActionListener(this);
        menu1.add(saveMI);
        men.add(menu1);
        //second menu
        menuView = new JMenu("View");
        updateMenu();
        men.add(menuView);


        //help menu
        JMenu menu3 = new JMenu("?");
        JMenuItem aboutMI = new JMenuItem("About", KeyEvent.VK_F1);
        aboutMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, ActionEvent.CTRL_MASK));
        aboutMI.addActionListener(this);
        menu3.add(aboutMI);
        men.add(menu3);
        return men;

    }

    /**
     * update the parts of the menu where the traces are shown
     */
    public void updateMenu() {
        menuView.removeAll();
        final Trace[] traces = graph.getTraces();
        if (graph.getNumberOfTraces() > 0) {
            JMenuItem jmi = new JMenuItem("change colors of traces", KeyEvent.VK_C);
            jmi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.ALT_MASK));
            jmi.addActionListener(this);
            menuView.add(jmi);
            JMenuItem resetMI = new JMenuItem("reset graphics", KeyEvent.VK_R);
            resetMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));
            resetMI.addActionListener(this);
            menuView.add(resetMI);

        }
        for (int i = 0; i < graph.getNumberOfTraces(); i++) {
            JCheckBoxMenuItem tmp = new JCheckBoxMenuItem("trace " + i + " " + traces[i].getName(), traces[i].isVisible());
            tmp.setForeground(traces[i].getColor());
            tmp.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, ActionEvent.ALT_MASK));
            tmp.addItemListener(this);
            menuView.add(tmp);
        }
    }

    /**
     * add a plot to the graph
     *
     * @param t Trace to plot
     */
    public void addPlot(Trace t) {
        graph.addPlot(t);
        updateMenu();
        repaint();
    }

    /**
     * add a plot to the graph
     *
     * @param x   abscissas
     * @param y   ordinates
     * @param col color of the graph
     */
    public void addPlot(Double[] x, Double[] y, Color col) {
        addPlot(x, y, col, "");
    }

    /**
     * add a plot to the graph
     *
     * @param x    abscissas
     * @param y    ordinates
     * @param col  color of the graph
     * @param name a name to give to the trace
     */
    public void addPlot(Double[] x, Double[] y, Color col, String name) {
        graph.addPlot(x, y, col, name);
        updateMenu();
        repaint();
    }

    /**
     * add a plot to the graph
     *
     * @param x    abscissas
     * @param y    ordinates
     * @param col  color of the graph
     * @param name a name to give to the trace
     */
    public void addPlot(double[] x, double[] y, Color col, String name) {
        graph.addPlot(x, y, col, name);
        updateMenu();
        repaint();
    }

    public void addPlot(int[] x, float[] y, Color col, String name) {
        graph.addPlot(x, y, col, name);
        updateMenu();
        repaint();
    }

    /**
     * remove every traces
     */
    public void removeAllPlots() {
        graph.removeAllPlots();
    }

    /**
     * reset the range of the displayed graph to the minimum and maximum values in data
     */
    public void resetMinMax() {
        graph.resetMinMax();
    }

    /**
     * toggle on/off the visibility of a Trace
     *
     * @param e
     */
    public void itemStateChanged(ItemEvent e) {
        JCheckBoxMenuItem jcbmi = (JCheckBoxMenuItem) (e.getSource());
        String text = jcbmi.getText();
        if (text.startsWith("trace")) {
            int index = Integer.parseInt(text.split(" ")[1]);
            graph.getTraces()[index].setVisible(jcbmi.isSelected());
            graph.resetMinMax();
            graph.repaint();
        }
    }

    /**
     * open trace from file/ delete one trace/ call about window/ change color of traces/ reset min max
     * <BR>
     * called from the corresponding menu
     *
     * @param ae
     */
    public void actionPerformed(ActionEvent ae) {
        JMenuItem jmi = (JMenuItem) ae.getSource();
        String text = jmi.getText();
        if (text.startsWith("Load")) {
            parse(ResultsTable.getResultsTable());
            graph.repaint();
        } else if (text.startsWith("Open")) {
            JFileChooser jfc = new JFileChooser();
            int returnval = jfc.showOpenDialog(this);
            if (returnval == JFileChooser.APPROVE_OPTION) {
                File tmp = jfc.getSelectedFile();
                status.setText("open " + tmp.getPath());
                //parse file
                parse(tmp);
                graph.repaint();
            }
        } else if (text.startsWith("Delete")) {
            TraceChooser tc = new TraceChooser(graph);
            tc.pack();
            tc.setVisible(true);
            if (!tc.wasCanceled()) {
                Trace[] tmp = tc.getSelectedTraces();
                for (Trace aTmp : tmp) {
                    graph.removePlot(aTmp);
                }
                graph.resetMinMax();
                graph.repaint();
                updateMenu();
            }
        } else if (text.startsWith("About")) {
            AboutWindow aw = new AboutWindow();
            aw.pack();
            aw.setVisible(true);
        } else if (text.startsWith("change")) {
            TraceChooser tc = new TraceChooser(graph, true);
            tc.pack();
            tc.setVisible(true);
            if (tc.wasCanceled()) {
                tc.applyColorChange();
            } else {
                tc.applyColorChange();
                boolean[] sel = tc.getSelection();
                for (int i = 0; i < graph.getNumberOfTraces(); i++) {
                    graph.getPlot(i).setVisible(sel[i]);
                }
            }
            graph.repaint();
            updateMenu();
        } else if (text.startsWith("reset")) {
            graph.resetMinMax();
            graph.repaint();
        } else if (text.startsWith("Define")) {
            TraceChooser tc = new TraceChooser(graph, false, false);
            tc.pack();
            tc.setVisible(true);
            if (!tc.wasCanceled()) {
                Trace[] tmp = tc.getSelectedTraces();
                if (tmp.length > 0) {
                    Double[] xs = tmp[0].getYs();
                    graph.removePlot(tmp[0]);
                    tmp = graph.getTraces();
                    for (Trace aTmp : tmp) {
                        aTmp.setXs(xs);
                    }
                    graph.resetMinMax();
                    graph.repaint();
                    updateMenu();
                }

            }
        } else if (text.startsWith("Create")) {
            double cstValue = IJ.getNumber("constant value", 0.5);
            Trace tmp = graph.getTraces()[0];
            Double[] ys = new Double[tmp.getYs().length];
            Arrays.fill(ys, cstValue);

            Trace cst = new Trace(tmp.getXs(), ys, Color.BLACK, "constant value " + cstValue);
            graph.addPlot(cst);
            graph.resetMinMax();
            graph.repaint();
            updateMenu();
        } else if (text.startsWith("Save")) {
            SaveDialog sd = new SaveDialog("save traces as...", "traces", ".csv");
            saveInFile(sd.getDirectory() + sd.getFileName());
        }
    }

    public void parse(ResultsTable rt) {
        String[] headings = rt.getHeadings();
        System.out.println("nb trace = " + headings.length);
        Double[] xs = null;
        Color[] col = {Color.DARK_GRAY, Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.YELLOW, Color.GRAY, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};

        for (int i = 0; i < headings.length; i++) {
            double[] column = rt.getColumnAsDoubles(i);
            if (xs == null) {
                xs = new Double[column.length];
                for (int x = 0; x < xs.length; x++) xs[x] = new Double(x);
            }
            Double[] ys = new Double[column.length];
            for (int y = 0; y < ys.length; y++) ys[y] = new Double(column[y]);
            //System.arraycopy(column, 0, ys, 0, column.length);
            addPlot(xs, ys, col[i % col.length], headings[i]);
        }

    }

    /**
     * parse a file to display it<BR>
     * <LI>if first line starts with # it is no considered</LI>
     * <LI>One line per entry</LI>
     * <LI>if more than one value (separated by whitespace character), first column is abscissa and 1 trace ordinate per additionnal value</LI>
     * <LI>if one value per line, abscissas will be the line number (starting at 0)</LI>
     *
     * @param tmp the file to parse
     */
    public void parse(File tmp) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(tmp));
            String line = br.readLine();
            System.out.println(line);
            if (line.startsWith("#")) {
                line = br.readLine();
                System.out.println(line);
            }
            String[] token = line.split("\\s+");
            int ntraces = token.length;
            System.out.println("ntraces=" + ntraces);
            int npoints = 1;
            while (br.readLine() != null) npoints++;
            br.close();
            System.out.println("npoints=" + npoints);
            br = new BufferedReader(new FileReader(tmp));

            Double[][] points = null;
            if (ntraces == 1) {
                points = new Double[2][npoints];
            } else {
                points = new Double[ntraces][npoints];
            }
            int count = 0;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                if (line.startsWith("#")) {
                    line = br.readLine();
                    System.out.println(line);
                }
                if (ntraces == 1) {
                    points[0][count] = new Double(count);
                    points[1][count] = Double.parseDouble(line);
                    //System.out.println("count" + count + " point" + points[1][count]);
                } else {
                    token = line.split("\\s+");
                    for (int i = 0; i < token.length; i++) {
                        points[i][count] = Double.parseDouble(token[i]);
                        //System.out.println("count" + count + " point" + i + " : " + points[i][count]);
                    }
                }
                count++;
            }
            br.close();
            Double[] xs = new Double[npoints];

            System.arraycopy(points[0], 0, xs, 0, npoints);
            if (ntraces == 1) {
                Double[] ys = new Double[npoints];
                System.arraycopy(points[1], 0, ys, 0, npoints);
                addPlot(xs, ys, Color.BLACK);
            } else {
                Color[] col = {Color.DARK_GRAY, Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.YELLOW, Color.GRAY, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};
                for (int i = 1; i < ntraces; i++) {
                    Double[] ys = new Double[npoints];
                    System.arraycopy(points[i], 0, ys, 0, npoints);
                    addPlot(xs, ys, col[i % col.length]);
                }
            }
        } catch (Exception e) {
            System.out.println("error while reading file " + tmp.getPath() + "\n" + e);
        }
        graph.resetMinMax();
    }

    public void saveInFile(String path) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(path));
            Trace[] traces = graph.getTraces();
            String line = "#x\t";
            for (int i = 0; i < traces.length; i++) {
                line += traces[i].getName() + "\t";
            }
            bw.write(line + "\n");

            for (int x = 0; x < traces[0].getXs().length; x++) {
                line = traces[0].getXs()[x] + "\t";
                for (int i = 0; i < traces.length; i++) {
                    line += traces[i].getYs()[x] + "\t";
                }
                bw.write(line + "\n");
            }
            bw.close();

        } catch (Exception e) {
            System.out.println("error while reading file " + path + "\n" + e);
        }
    }

    public void appendToStatus(Color c, String s) { // better implementation--uses StyleContext
        StyleContext sc = StyleContext.getDefaultStyleContext();
        AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY,
                StyleConstants.Foreground, c);

        int len = status.getDocument().getLength(); // same value as getText().length();
        status.setCaretPosition(len);  // place caret at the end (with no selection)
        status.setCharacterAttributes(aset, false);
        status.replaceSelection(s); // there is no selection, so inserts at caret
    }

    /**
     * when clicking if first button then display the abscissas and ordinate of the points nearest to the position clicked for all displayed traces<BR>
     * if second button the reset displayed range
     *
     * @param e
     */
    public void mouseClicked(MouseEvent e) {
        //System.out.println("mouse clicked");

        Trace[] t = graph.getTraces();
        String st = "";
        double x = graph.pixelToValue(e.getX(), Plot.XAXIS);
        double y = graph.pixelToValue(e.getY(), Plot.YAXIS);
        int index;
        double err = Double.MAX_VALUE;
        double errtmp;
        int tnb = 0;
        status.selectAll();
        status.replaceSelection(st);
        for (int i = 0; i < t.length; i++) {
            if (t[i].isVisible()) {
                index = t[i].getXIndexNearestTo(x);
                if (e.getButton() == MouseEvent.BUTTON1) {
                    st += "trace " + i + "(" + t[i].getXs()[index] + "," + t[i].getYs()[index] + ") <BR>";
                    //status.replaceSelection(st);
                    //appendToStatus(t[i].getColor(), "trace " + i + "(" + t[i].getXs()[index] + "," + t[i].getYs()[index] + ") <BR>");
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    errtmp = (x - t[i].getXs()[index]) * (x - t[i].getXs()[index]) + (y - t[i].getYs()[index]) * (y - t[i].getYs()[index]);
                    if (errtmp < err) {
                        //tnb = i;
                        err = errtmp;
                        st = "trace " + i + "(" + t[i].getXs()[index] + "," + t[i].getYs()[index] + ") <BR>";
                    }
                }
            }
        }

        status.setText(st);
        status.validate();

    }

    /**
     * When pressing mouse position if kept
     *
     * @param e
     */
    public void mousePressed(MouseEvent e) {
        //System.out.println("mouse pressed");
        xpressed = e.getX();
        ypressed = e.getY();
    }

    /**
     * when releasing button 1, the displayed range is change to the rectangle between the position kept in memory when button pressed and current position
     *
     * @param e
     */
    public void mouseReleased(MouseEvent e) {
        // System.out.println("mouse realeased");
        if (xpressed == e.getX() && ypressed == e.getY()) return;
        if (e.getButton() != MouseEvent.BUTTON1) return;
        graph.resetSelection();
        int xmin = xpressed;
        int xmax = e.getX();
        if (xmin > xmax) {
            xmin = xmax;
            xmax = xpressed;
        }
        int ymin = ypressed;
        int ymax = e.getY();
        if (ymin < ymax) {
            ymin = ymax;
            ymax = ypressed;
        }
        graph.pixelToValue(e.getX(), Plot.XAXIS);
        graph.setMinMax(graph.pixelToValue(xmin, Plot.XAXIS), graph.pixelToValue(xmax, Plot.XAXIS), graph.pixelToValue(ymin, Plot.YAXIS), graph.pixelToValue(ymax, Plot.YAXIS));
        graph.repaint();
    }

    /**
     * do notthing
     *
     * @param e
     */
    public void mouseEntered(MouseEvent e) {

    }

    /**
     * do nothing
     *
     * @param e
     */
    public void mouseExited(MouseEvent e) {

    }

    /**
     * show a rectangle coresponding to the displayed range that will be used when button 1 will be released
     *
     * @param e
     */
    public void mouseDragged(MouseEvent e) {
        //if (e.getButton() != MouseEvent.BUTTON1) return;
        int xmin = xpressed;
        int xmax = e.getX();
        if (xmin > xmax) {
            xmin = xmax;
            xmax = xpressed;
        }
        int ymin = ypressed;
        int ymax = e.getY();
        if (ymin > ymax) {
            ymin = ymax;
            ymax = ypressed;
        }
        //
        graph.showSelection(xmin, ymin, xmax - xmin, ymax - ymin);
        graph.repaint();
    }

    /**
     * do nothing
     *
     * @param e
     */
    public void mouseMoved(MouseEvent e) {

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
        panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JSplitPane splitPane1 = new JSplitPane();
        splitPane1.setOneTouchExpandable(false);
        splitPane1.setOrientation(0);
        splitPane1.setResizeWeight(1.0);
        panel1.add(splitPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        splitPane1.setRightComponent(panel2);
        blackBackgroundCheckBox = new JCheckBox();
        blackBackgroundCheckBox.setText("black background");
        blackBackgroundCheckBox.setMnemonic('B');
        blackBackgroundCheckBox.setDisplayedMnemonicIndex(0);
        panel2.add(blackBackgroundCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showAxesCheckBox = new JCheckBox();
        showAxesCheckBox.setSelected(true);
        showAxesCheckBox.setText("show axes");
        showAxesCheckBox.setMnemonic('S');
        showAxesCheckBox.setDisplayedMnemonicIndex(0);
        panel2.add(showAxesCheckBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel2.add(scrollPane1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        status = new JTextPane();
        status.setContentType("text/html");
        status.setEditable(false);
        status.setText("<html>\r\n  <head>\r\n    \r\n  </head>\r\n  <body>\r\n  </body>\r\n</html>\r\n");
        scrollPane1.setViewportView(status);
        graph = new Plot();
        graph.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPane1.setLeftComponent(graph);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel1;
    }


}
