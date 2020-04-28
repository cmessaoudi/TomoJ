package fr.curie.plotj;

import ij.plugin.PlugIn;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * plugin to plot graphs
 * User: MESSAOUDI Cedric
 * Date: 15 mai 2008
 * Time: 13:58:47
 *
 */
public class PlotJ_ implements PlugIn {
	Plot graph;

	public static void main(String[] args) {
		PlotJ_ pj = new PlotJ_();
		pj.createInterface();

	}

	/**
	 * starting point of the plugin in ImageJ
	 * @param args
	 */
	public void run(String args) {
		createInterface();
		//old();
	}

	/**
	 * create the interface with 2 traces that can be removed
	 */
	public void createInterface() {
		PlotWindow2 pw = new PlotWindow2();
		Double[] xtest = {0d, 5d, 10d, 15d, 17.5, 21.5};
		Double[] ytest = {20d, 50d, 30d, 45.8, 37.5, 13.2};
		pw.addPlot(xtest, ytest, Color.RED);
		Double[] xtest2 = {7.2, 8.3, 12.5, 15d, 16d, 21.5};
		Double[] ytest2 = {36.5, 24.1, 15.2, -7.6, 60.7, 13.2};
		pw.addPlot(xtest2, ytest2, Color.BLUE);
		pw.setSize(500, 500);
		pw.setVisible(true);

	}

	/**
	 * @deprecated old interface
	 */
	public void old() {
		JFrame fram = new JFrame("PlotJ");
		fram.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		JPanel pan = new JPanel();
		pan.setLayout(new BoxLayout(pan, BoxLayout.Y_AXIS));
		graph = new Plot();
		double[] xtest = {0d, 5d, 10d, 15d, 17.5, 21.5};
		double[] ytest = {20d, 50d, 30d, 45.8, 37.5, 13.2};
		graph.addPlot(xtest, ytest, Color.RED);
		double[] xtest2 = {7.2, 8.3, 12.5, 15d, 16d, 21.5};
		double[] ytest2 = {36.5, 24.1, 15.2, 7.6, 60.7, 13.2};
		graph.addPlot(xtest2, ytest2, Color.BLUE);
		pan.add(graph);
		final JPanel bottomP = new JPanel();
		bottomP.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		final JCheckBox bg = new JCheckBox("black background");
		bg.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				if (bg.isSelected()) {
					graph.setBackgroundColor(Color.BLACK);
				} else {
					graph.setBackgroundColor(Color.WHITE);
				}
				graph.repaint();
			}
		});
		bg.setMaximumSize(bg.getMinimumSize());
		bg.setPreferredSize(bg.getMinimumSize());

		bottomP.add(bg);
		JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, pan, bottomP);
		jsp.setResizeWeight(1);
		jsp.setDividerLocation(0.9);
		fram.getContentPane().add(jsp);
		fram.setSize(500, 500);
		fram.setVisible(true);
		fram.setResizable(true);
		//fram.pack();

	}

}
