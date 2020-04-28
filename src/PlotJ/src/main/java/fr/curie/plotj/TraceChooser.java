package fr.curie.plotj;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import ij.IJ;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * class used in the PlotWindow2 to select traces to remove or to change displayed color, can be used for other purposes
 */
public class TraceChooser extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel pan;
    private JScrollPane jsp;
    private JCheckBox[] traceCB;
    private Plot graph;
    private boolean canceled = true;
    private Color[] colors;
    boolean defaultSelection = true;

    /**
     * constructor
     *
     * @param traces the traces available
     */
    public TraceChooser(Plot traces) {
        this(traces, false, true);
    }

    public TraceChooser(Plot traces, boolean showColor) {
        this(traces, showColor, true);
    }

    /**
     * constructor
     *
     * @param traces    traces available
     * @param showColor if true show a colored button showing displayed color of the trace  and allowing the change of color
     */
    public TraceChooser(Plot traces, boolean showColor, boolean defaultSelection) {
        graph = traces;
        this.defaultSelection = defaultSelection;
        if (showColor) {
            colors = new Color[graph.getNumberOfTraces()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = graph.getPlot(i).getColor();
            }
        } else colors = null;
        traceCB = new JCheckBox[graph.getNumberOfTraces()];

        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(true);
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
    }

    private void onOK() {
// add your code here
        dispose();
        canceled = false;
    }

    private void onCancel() {
// add your code here if necessary
        dispose();
        canceled = true;
    }

    /**
     * was the dialog canceled?
     *
     * @return true if canceled, false if OKed
     */
    public boolean wasCanceled() {
        return canceled;
    }

    /**
     * gives the selected traces
     *
     * @return the selected traces
     */
    public Trace[] getSelectedTraces() {
        Trace[] all = graph.getTraces();
        if (canceled) {
            return all;
        }
        int size = 0;
        for (JCheckBox aTraceCB : traceCB) {
            if (aTraceCB.isSelected()) {
                size++;
            }
        }
        Trace[] selection = new Trace[size];
        int index = 0;
        for (int i = 0; i < traceCB.length; i++) {
            if (traceCB[i].isSelected()) {
                selection[index] = all[i];
                index++;
            }
        }
        return selection;
    }

    /**
     * gives the selection of all traces
     *
     * @return an array containg true if trace corresponding to index is selected
     */
    public boolean[] getSelection() {
        boolean[] result = new boolean[traceCB.length];
        for (int i = 0; i < traceCB.length; i++) {
            result[i] = traceCB[i].isSelected();
        }
        return result;
    }

    /**
     * apply the change of color asked if dialog was not canceled
     */
    public void applyColorChange() {
        if (colors != null) {
            if (canceled) {
                for (int i = 0; i < colors.length; i++) {
                    graph.getPlot(i).setColor(colors[i]);
                }
            }
        }
    }

    private void createUIComponents() {
        pan = new JPanel();
        pan.setLayout(new BoxLayout(pan, BoxLayout.Y_AXIS));
        for (int i = 0; i < traceCB.length; i++) {
            JPanel pantmp = new JPanel();
            final Trace plottmp = graph.getPlot(i);
            traceCB[i] = new JCheckBox("trace " + i + " " + plottmp.getName(), defaultSelection);
            traceCB[i].setForeground(plottmp.getColor());
            pantmp.add(traceCB[i]);
            final JButton butRename = new JButton("rename");
            final int ii = i;
            butRename.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    plottmp.setName(IJ.getString("enter new name", plottmp.getName()));
                    traceCB[ii].setText("trace " + ii + " " + plottmp.getName());
                }
            });
            pantmp.add(butRename);
            if (colors != null) {
                traceCB[i].setSelected(plottmp.isVisible());
                final JButton but = new JButton("change color");
                but.setBackground(plottmp.getColor());
                but.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        TraceColorChooser tcc = new TraceColorChooser(but);
                        tcc.pack();
                        tcc.setVisible(true);
                        if (!tcc.wasCanceled()) {
                            but.setBackground(tcc.getColor());
                            plottmp.setColor(tcc.getColor());
                        }
                    }
                });
                pantmp.add(but);
            }
            pan.add(pantmp);
        }
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
        jsp = new JScrollPane();
        contentPane.add(jsp, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        jsp.setViewportView(pan);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
