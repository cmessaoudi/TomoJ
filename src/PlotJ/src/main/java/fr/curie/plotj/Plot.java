package fr.curie.plotj;

import fr.curie.plotj.Trace;

import javax.swing.*;
import java.awt.*;
import java.util.Vector;

/**
 * Class that handle the display of traces
 * User: MESSAOUDI C�dric
 * Date: 15 mai 2008
 * Time: 10:59:09
 *
 */
public class Plot extends JComponent {

	protected Vector<Trace> traces;
	private double xmin = Double.MAX_VALUE;
	private double xmax = Double.MIN_VALUE;
	private double ymin = Double.MAX_VALUE;
	private double ymax = Double.MIN_VALUE;
	private double scalex = 1;
	private double scaley = 1;
	private boolean line;
	private boolean axes;
	int offsetx, offsety;
	Color bg;
	boolean initialized;
	private final static double[] falseinit = {1d, 2d, 3d};
	public final static int XAXIS = 0;
	public final static int YAXIS = 1;
	Rectangle r;
	/**
	 * constructor with no traces
	 */
	public Plot() {
		this(falseinit, falseinit, Color.BLACK);
		initialized = false;
	}

	/**
	 * constructor with one trace
	 * @param x abscissas
	 * @param y ordinates
	 * @param col displayed color
	 */
	public Plot(double[] x, double[] y, Color col) {
		traces = new Vector<Trace>();
		line = true;
		bg = Color.WHITE;
		axes = true;
		addPlot(x, y, col);
		this.setDoubleBuffered(true);
	}

	public Plot(Double[] x, Double[] y, Color col){
		traces = new Vector<Trace>();
		line = true;
		bg = Color.WHITE;
		axes = true;
		addPlot(x, y, col);
		this.setDoubleBuffered(true);
	}

	/**
	 * add a trace to the graph
	 * @param t the trace to add
	 */
	public void addPlot(Trace t) {
		if (!initialized && traces.size() > 0) {
			traces.removeAllElements();
		}
		traces.add(t);
		setMinMax(t.getXs(), t.getYs());
		initialized = true;
	}

	/**
	 * add a trace to the graph
	 * @param x abscissas
	 * @param y ordinates
	 * @param col displayed color
	 * @param name name of the trace
	 */
	public void addPlot(double[] x, double[] y, Color col,String name) {
		addPlot(new Trace(x, y, col,name));
	}
		/**
	 * add a trace to the graph
	 * @param x abscissas
	 * @param y ordinates
	 * @param col displayed color
	 * @param name name of the trace
	 */
	public void addPlot(Double[] x, Double[] y, Color col,String name) {
		addPlot(new Trace(x, y, col,name));
	}

	/**
		 * add a trace to the graph
		 * @param x abscissas
		 * @param y ordinates
		 * @param col displayed color
		 */
		public void addPlot(double[] x, double[] y, Color col){
			addPlot(new Trace(x, y, col));
		}
	/**
	 * add a trace to the graph
	 * @param x abscissas
	 * @param y ordinates
	 * @param col displayed color
	 */
	public void addPlot(int[] x, float[] y, Color col, String name){
		addPlot(new Trace(x, y, col, name));
	}
	/**
		 * add a trace to the graph
		 * @param x abscissas
		 * @param y ordinates
		 * @param col displayed color
		 */
		public void addPlot(Double[] x, Double[] y, Color col){
			addPlot(new Trace(x, y, col));
		}

	/**
	 * add a trace to the graph with no abscissas, will put abscissas to 0,1,2,...n-1  where n is the number of points
	 * @param y ordinates
	 * @param col displayed color
	 */
	public void addPlot(double[] y, Color col) {
		double[] x = new double[y.length];
		for (int i = 0; i < y.length; i++) {
			x[i] = i;
		}
		addPlot(x, y, col);
	}

	/**
	 * remove a trace from the graph
	 * @param index trace number (order of addition to the plot)
	 */
	public void removePlot(int index) {
		traces.removeElementAt(index);
	}

	/**
	 * remove a trace from the graph
	 * @param t the trace to remove
	 */
	public void removePlot(Trace t) {
		traces.removeElement(t);
	}

	/**
	 * removes everything
	 */
	public void removeAllPlots(){
		traces = new Vector<Trace>();		
	}

	/**
	 * get a trace from the graph
	 * @param index trace number
	 * @return
	 */
	public Trace getPlot(int index) {
		return traces.elementAt(index);
	}

	/**
	 * set the background color
	 * @param c the color to use as background
	 */
	public void setBackgroundColor(Color c) {
		bg = c;
	}

	/**
	 * show axis or not
	 * @param show true to show axis
	 */
	public void showAxes(boolean show) {
		axes = show;
	}

	/**
	 * gives the number of traces is this plot
	 * @return number of traces
	 */
	public int getNumberOfTraces() {
		return traces.size();
	}

	/**
	 * gives all the traces
	 * @return an arry with all the traces
	 */
	public Trace[] getTraces() {
		if (traces.size() == 0) {
			return null;
		}
		Trace[] t = new Trace[traces.size()];
		return traces.toArray(t);
	}

	/**
	 * display a rectangle on display
	 * @param x abscissa of origin point
	 * @param y ordinate of origin point
	 * @param width width of the rectangle
	 * @param height height of the rectangle
	 */
	public void showSelection(int x,int y,int width,int height){
		r=new Rectangle(x,y,width,height);
		//getGraphics().drawRect(x,y,width,height);
	}

	/**
	 * remove display of rectangle
	 */
	public void resetSelection(){
		r=null;
	}

	/**
	 * update the display
	 * @param g
	 */
	public void update(Graphics g) {
		paint(g);
	}

	/**
	 * method called by update, repaint... methods to display the graph
	 * @param g
	 */
	public void paint(Graphics g) {
		Graphics gtmp = g.create();
		int gx = getWidth();
		int gy = getHeight();
		scalex = gx / (xmax - xmin);
		scalex *= 0.9;
		scaley = gy / (ymax - ymin);
		scaley *= 0.9;
		offsety = (int) (ymax * scaley + 0.5) + (int) (gy * 0.05);
		offsetx = (int) (gx * 0.05);
		//System.out.println("paint width:" + gx + "\theight:" + gy);
		//System.out.println("paint offsetx:" + offsetx + "\toffsety:" + offsety);
		gtmp.setColor(bg);
		gtmp.fillRect(0, 0, gx, gy);
		if (axes) {
			gtmp.setColor(Color.BLACK);
			if (bg == Color.BLACK) {
				gtmp.setColor(Color.WHITE);
			}
			double gx0 = 0;
			double gy0 = 0;
			if (xmin > 0 || xmax < 0) gx0 = xmin;
			if (ymin > 0 || ymax < 0) gy0 = ymin;
			gtmp.drawLine(valueToPixel(xmin, XAXIS), valueToPixel(gy0, YAXIS), valueToPixel(xmax, XAXIS), valueToPixel(gy0, YAXIS));
			gtmp.drawLine(valueToPixel(gx0, XAXIS), valueToPixel(ymin, YAXIS), valueToPixel(gx0, XAXIS), valueToPixel(ymax, YAXIS));
			/*TODO insert numbers for comprhensive interface*/
		}
		int[] xgraph;
		int[] ygraph;
		Double[] x;
		Double[] y;
		Trace ttmp;
		for (int i = 0; i < traces.size(); i++) {
			ttmp = traces.elementAt(i);
			if (ttmp.isVisible()) {
				gtmp.setColor(ttmp.getColor());
				x = ttmp.getXs();
				y = ttmp.getYs();
				xgraph = new int[x.length];
				ygraph = new int[y.length];
				for (int pos = 0; pos < y.length; pos++) {
					//xgraph[pos]=(int)Math.round((x[pos]-xmin)/(xmax-xmin)*(px-offset)+offset);
					//ygraph[pos]=(int)Math.round((y[pos]-ymin)/(ymax-ymin)*(py-offset)+offset);
					//ygraph[pos]=Math.abs(ygraph[pos]-gy);
					ygraph[pos] = valueToPixel(y[pos], YAXIS);
					xgraph[pos] = valueToPixel(x[pos], XAXIS);
				}
				gtmp.drawPolyline(xgraph, ygraph, ygraph.length);
			}
		}
		if(r!=null){
		if (bg == Color.BLACK) {
				gtmp.setColor(Color.WHITE);
			}else{
			gtmp.setColor(Color.BLACK);
		}
			gtmp.drawRect((int)r.getX(),(int)r.getY(),(int)r.getWidth(),(int)r.getHeight());
		}
		g = gtmp;
	}

	/**
	 * check if the current minimum and maximum range are able to display the trace given, if not modify to allow display of trace
	 * @param x abscissas of new trace
	 * @param y ordinates of new trace
	 */
	private void setMinMax(Double[] x, Double[] y) {
		for (int i = 0; i < x.length; i++) {
			if (xmin > x[i]) xmin = x[i];
			if (xmax < x[i]) xmax = x[i];
			if (ymin > y[i]) ymin = y[i];
			if (ymax < y[i]) ymax = y[i];
		}
	}

	/**
	 * reset the ranges of graph displayed to what is able to display all points
	 */
	public void resetMinMax() {
		xmin = Double.MAX_VALUE;
		ymin = Double.MAX_VALUE;
		xmax = Double.MIN_VALUE;
		ymax = Double.MIN_VALUE;
		for (int i = 0; i < traces.size(); i++) {
			Trace tmp = getPlot(i);
			if(tmp.isVisible()) {
				setMinMax(tmp.getXs(), tmp.getYs());
			}
		}

	}

	/**
	 * set the ranges of graph displayed
	 * @param xmin minimum abscissa
	 * @param xmax maximum abscissa
	 * @param ymin minimum ordinate
	 * @param ymax maximum ordinate
	 */
	public void setMinMax(double xmin, double xmax, double ymin, double ymax) {
		this.xmin = xmin;
		this.xmax = xmax;
		this.ymin = ymin;
		this.ymax = ymax;
	}

	/**
	 * converts pixel position on graph to true values on graph
	 * @param value pixel position
	 * @param axis on which axis Plot.XAXIS for abscissas, Plot.YAXIS for ordinate
	 * @return value in graph range
	 */
	public double pixelToValue(int value, int axis) {
		switch (axis) {
			case XAXIS:
				return ((value - offsetx) / scalex)+xmin;
			case YAXIS:
				return (offsety - value) / scaley;
		}
		return Double.NaN;
	}

	/**
	 * convert a value in graph range to pixel position in displayed graph
	 * @param value graph value
	 * @param axis on which axis Plot.XAXIS for abscissas, Plot.YAXIS for ordinate
	 * @return value in displayed graph
	 */
	public int valueToPixel(double value, int axis) {
		switch (axis) {
			case XAXIS:
				return  offsetx+(int) (scalex * (value-xmin) + 0.5) ;
			case YAXIS:
				return offsety - (int) (scaley * value + 0.5);
		}
		return 0;
	}

}
