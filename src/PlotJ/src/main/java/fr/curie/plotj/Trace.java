package fr.curie.plotj;

import java.awt.*;

/**
 * Class containing points to plot
 * User: MESSAOUDI C�dric
 * Date: 16 mai 2008
 * Time: 14:58:40
 *
 */
public class Trace {
	Double[] x;
	Double[] y;
	Color col;
	boolean visible;
	String name;

	/**
	 * constructor
	 *
	 * @param x       array containing absisses of points
	 * @param y       array containing ordinate of points
	 * @param color   drawing color
	 * @param name name of the trace
 * @param visible true if visible
	 */
	public Trace(double[] x, double[] y, Color color,String name, boolean visible) {
		this.x = new Double[x.length];
		this.y = new Double[x.length];
		for(int i=0;i<x.length;i++){
			this.x[i]=x[i];
			this.y[i]=y[i];
		}
		this.col = color;
		this.visible = visible;
		this.name=name;
	}
	/**
	 * constructor
	 *
	 * @param x       array containing absisses of points
	 * @param y       array containing ordinate of points
	 * @param color   drawing color
	 * @param name name of the trace
	 * @param visible true if visible
	 */
	public Trace(int[] x, float[] y, Color color,String name, boolean visible) {
		this.x = new Double[x.length];
		this.y = new Double[x.length];
		for(int i=0;i<x.length;i++){
			this.x[i]=new Double(x[i]);
			this.y[i]=new Double(y[i]);
		}
		this.col = color;
		this.visible = visible;
		this.name=name;
	}
	public Trace(Double[] x, Double[] y, Color color, String name, boolean visible) {
		this.x = x;
		this.y = y;
		this.col = color;
		this.visible = visible;
		this.name=name;
	}

	/**
	 * constructor
	 *
	 * @param x     array containing abscissas of points
	 * @param y     array containing ordinate of points
	 * @param color drawing color
	 */
	public Trace(double[] x, double[] y, Color color) {
		this(x, y, color, "",true);
	}
	/**
	 * constructor
	 *
	 * @param x     array containing abscissas of points
	 * @param y     array containing ordinate of points
	 * @param color drawing color
	 */
	public Trace(Double[] x, Double[] y, Color color) {
		this(x, y, color, "",true);
	}
	/**
		 * constructor
		 *
		 * @param x     array containing abscissas of points
		 * @param y     array containing ordinate of points
		 * @param color drawing color
		 * @param name name of this trace
		 */
		public Trace(double[] x, double[] y, Color color,String name) {
			this(x, y, color, name,true);
		}
	/**
			* constructor
	*
			* @param x     array containing abscissas of points
	* @param y     array containing ordinate of points
	* @param color drawing color
	* @param name name of this trace
	*/
	public Trace(int[] x, float[] y, Color color,String name) {
		this(x, y, color, name,true);
	}
	/**
		 * constructor
		 *
		 * @param x     array containing abscissas of points
		 * @param y     array containing ordinate of points
		 * @param color drawing color
		 * @param name name of this trace
		 */
		public Trace(Double[] x, Double[] y, Color color,String name) {
			this(x, y, color, name,true);
		}

	/**
	 * constructor the drawing will be in black
	 *
	 * @param x array containing abscissas of points
	 * @param y array containing ordinate of points
	 */
	public Trace(double[] x, double[] y) {
		this(x, y, Color.BLACK,"", true);
	}
	/**
	 * constructor the drawing will be in black
	 *
	 * @param x array containing abscissas of points
	 * @param y array containing ordinate of points
	 */
	public Trace(Double[] x, Double[] y) {
		this(x, y, Color.BLACK,"", true);
	}

	/**
	 * constructor will create automatically abscissas as {0,1,2,3....} <BR>
	 * drawing will be in Black
	 *
	 * @param y array containing ordinate of points
	 */
	public Trace(double[] y) {
		x = new Double[y.length];
		this.y=new Double[y.length];
		for (int i = 0; i < x.length; i++) {
			x[i] = new Double(i);
			this.y[i]=y[i];
		}
		col = Color.BLACK;
		visible = true;
		name="";
	}

	/**
	 * change the visibility of Trace when plotting with Plot
	 *
	 * @param show new value for the visibility
	 *             <LI>true to show trace</LI>
	 *             <LI>false to hide this trace </LI>
	 */
	public void setVisible(boolean show) {
		visible = show;
	}

	/**
	 * is the trace visible ?
	 *
	 * @return true if visible, false otherwise
	 */
	public boolean isVisible() {
		return visible;
	}

	/**
	 * changes the abscissas
	 *
	 * @param xs the abscissas of points
	 */
	public void setXs(double[] xs) {
		x=new Double[xs.length];
		for(int i=0;i<xs.length;i++){
		x[i] = xs[i];
		}
	}
	/**
	 * changes the abscissas
	 *
	 * @param xs the abscissas of points
	 */
	public void setXs(Double[] xs) {
		x=xs;
	}

	/**
	 * gives the currently used abscissas
	 *
	 * @return the ascissas
	 */
	public Double[] getXs() {
		return x;
	}

	/**
		 * changes the ordinates
		 *
		 * @param ys the ordinates of points
		 */
		public void setYs(double[] ys) {
			y=new Double[ys.length];
		for(int i=0;i<ys.length;i++){
		y[i] = ys[i];
		}
		}
	/**
		 * changes the ordinates
		 *
		 * @param ys the ordinates of points
		 */
		public void setYs(Double[] ys) {
			y = ys;
		}


	/**
		 * gives the currently used ordinates
		 *
		 * @return the ordinates
		 */
		public Double[] getYs() {
			return y;
		}

	/**
	 * change the drawing color
	 *
	 * @param color the color to be used
	 */
	public void setColor(Color color) {
		col = color;
	}

	/**
	 * gives the currently used drawing color for this Trace
	 *
	 * @return drawing color
	 */
	public Color getColor() {
		return col;
	}

	/**
	 * gives the name of this trace
	 * @return the name of this trace
	 */
	public String getName() {
		return name;
	}

	/**
	 * sets the name of this trace
	 * @param name the name of this trace
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * gives the array index of the abscissa nearest to the given value
	 *
	 * @param value an abscissa
	 *
	 * @return the array index of the  abscissa in the trace nearest to the given value
	 */
	public int getXIndexNearestTo(double value) {
		int index = 0;
		double dist = Double.MAX_VALUE;
		double score;
		for (int i = 0; i < x.length; i++) {
			score = Math.abs(x[i] - value);
			if (score < dist) {
				dist = score;
				index = i;
			}
		}
		return index;
	}
	
}
