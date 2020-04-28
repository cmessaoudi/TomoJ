package fr.curie.filters;
 
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
 
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
 
 
/**
 * Harris Corner Detector
 * based on version by Xavier Philippeau
 *
 */
public class HarrisCornerDetection implements PlugInFilter {
 
	// corners list
	List<double[]> corners = new ArrayList<double[]>();
    ImagePlus myimp;
	// Gradient kernel
	GradientVector gradient = new GradientVector();
	// halfwindow size (integration, kernels)
	private int halfwindow = 0;
	// variance of gaussians
	private double gaussiansigma = 0;
	// corner filtering
	private double minDistance = 0;
	private double minMeasure = 0;
    private int borderdistance=0;
 
	//	 About...
	private void showAbout() {
		IJ.showMessage("Harris...","Harris Corner Detector");
	}
 
	public int setup(String arg, ImagePlus imp) {
 
		// about...
		if (arg.equals("about")) {
			showAbout(); 
			return DONE;
		}
       // imp= imp.getProcessor();
		// else...
		if (imp==null) return DONE;
        myimp=imp;
 
		// Configuration dialog.
		GenericDialog gd = new GenericDialog("Parameters");
		gd.addNumericField("Half-Window size",1,0);
		gd.addNumericField("Gaussian sigma",1.4,1);
		gd.addNumericField("Min Harris measure for corners",10,0);
		gd.addNumericField("Min distance between corners",8,0);
		int  halfwindow = 0;
		double gaussiansigma = 0;
		double minMeasure = 0;
		double minDistance = 0;


			gd.showDialog();
			if ( gd.wasCanceled() )	return DONE;
 
			halfwindow     = (int) gd.getNextNumber();
			gaussiansigma  =  gd.getNextNumber();
			minMeasure     =  gd.getNextNumber();
			minDistance    =  gd.getNextNumber();
        int border=(int)gd.getNextNumber();
 
			if (halfwindow<=0||gaussiansigma<=0||minMeasure<0||minDistance<0||border<0) {
                IJ.error("no negative values are allowed!");
                return DONE;
            }


 
		this.halfwindow = halfwindow;
		this.gaussiansigma = gaussiansigma;
		this.minMeasure = minMeasure;
		this.minDistance = minDistance;
        this.borderdistance=border;
 
		return PlugInFilter.DOES_ALL;
	}
 
	public void run(ImageProcessor ip) {

 
		// canny filter
        //System.out.println("harris corner detection with :\nhalf window: "+halfwindow+"\ngaussian sigma: "+gaussiansigma+"\nthreshold: "+minMeasure+"\nmin distance: "+minDistance+"\ndistance to borders: "+borderdistance);
		filter( ip, this.minMeasure, this.minDistance );
        if(myimp != null)
            myimp.setRoi(getCornersAsRoi());
    }



    public PointRoi getCornersAsRoi(){
        PointRoi roi = null;
        for (double[] p:corners) {
            if (roi == null)
                roi = new PointRoi(p[0], p[1]);
            else
                roi = roi.addPoint(p[0], p[1]);
        }
        return roi;
    }
	// -------------------------------------------------------------------------
 
	/**
	 * Gaussian window function
	 * 
	 * @param x x coordinates
	 * @param y y coordinates
	 * @param sigma2 variance
	 * @return value of the function
	 */
	private double gaussian(double x, double y, double sigma2) {
		double t = (x*x+y*y)/(2*sigma2);
		double u = 1.0/(2*Math.PI*sigma2);
		double e = u*Math.exp( -t );
		return e;
	}
 
	/**
	 * compute harris measure for a pixel
	 * 
	 * @param c Image map
	 * @param x x coord of the computation
	 * @param y y coord of the computation
	 * @return harris measure 
	 */
	private double harrisMeasure(ImageProcessor c, int x, int y) {
		double m00=0, m01=0, m10=0, m11=0;
 
		// Harris estimator
		// ----------------
		//
		// k = det(A) - lambda * trace(A)^2
		//
		// Where A is the second-moment matrix 
		//
		//           | Lx�(x+dx,y+dy)    Lx.Ly(x+dx,y+dy) |
		// A =  Sum  |                                    | * Gaussian(dx,dy)
		//     dx,dy | Lx.Ly(x+dx,y+dy)  Ly�(x+dx,y+dy)   |
		//
		// and lambda = 0.06  (totaly empirical :-)
 
		for(int dy=-halfwindow;dy<=halfwindow;dy++) {
			for(int dx=-halfwindow;dx<=halfwindow;dx++) {
				int xk = x + dx;
				int yk = y + dy;
				if (xk<0 || xk>=c.getWidth()) continue;
				if (yk<0 || yk>=c.getHeight()) continue;
 
				// gradient value
				double[] g = gradient.getVector(c,xk,yk);
				double gx = g[0];
				double gy = g[1];
 
				// gaussian window
				double gw = gaussian(dx,dy,gaussiansigma);
 
				// second-moment matrix elements
				m00 += gx * gx * gw;
				m01 += gx * gy * gw;
				m10 = m01;
				m11 += gy * gy * gw;
			}
		}
 
		// Harris corner measure 
		double harris = m00*m11 - m01*m10 - 0.06*(m00+m11)*(m00+m11);
 
		// scaled down
        double range=c.getMax()-c.getMin();
		return harris/(range*range);
	}
 
	/**
	 * return the the measure at pixel (x,y) if the pixel is a spatial Maxima, else return -1
	 * 
	 * @param c original image 
	 * @param x x coordinates of pixel
	 * @param y y coordinates of pixel
	 * @return the measure if the pixel is a spatial Maxima, else -1
	 */
	private double spatialMaximaofHarrisMeasure(ImageProcessor c, int x, int y) {
		int n=8;
		int[] dx = new int[] {-1,0,1,1,1,0,-1,-1};
		int[] dy = new int[] {-1,-1,-1,0,1,1,1,0};
		double w =  harrisMeasure(c,x,y);
		for(int i=0;i<n;i++) {
			double wk = harrisMeasure(c,x+dx[i],y+dy[i]);
			if (wk>=w) return -1;
		}
		return w;
	}
 
	/**
	 * Perfom Harris Corner Detection
	 * 
	 * @param c Image map
	 * @param minMeasure
	 * @param minDistance
	 * @return filtered image map
	 */
	public List<double[]> filter(ImageProcessor c, double minMeasure, double minDistance) {
		int width = c.getWidth();
		int height = c.getHeight();

        // for each tile in the image
        c.resetMinAndMax();
        for (int y=0; y<height; y++){
            for (int x=0; x<width; x++){
				// harris measure
				double h = spatialMaximaofHarrisMeasure(c, x, y);
 
				// add the corner to the list
				if (h>=minMeasure) corners.add( new double[]{x,y,h} );
			}
		}

        for(int i=corners.size()-1;i>=0;i--){
            double[] p=corners.get(i);
            if(p[0]<borderdistance||p[0]>c.getWidth()-borderdistance||p[1]<borderdistance||p[1]>c.getHeight()-borderdistance) corners.remove(p);
        }
 
		// remove corners to close to each other (keep the highest measure)
		Iterator<double[]> iter = corners.iterator();
		while(iter.hasNext()) {
			double[] p = iter.next();
			for(double[] n:corners) {
				if (n==p) continue;
				double dist = Math.sqrt( (p[0]-n[0])*(p[0]-n[0])+(p[1]-n[1])*(p[1]-n[1]) );
				if( dist>minDistance) continue;
				if (n[2]<p[2]) continue;
				iter.remove();
				break;
			}
		}

		return corners;
	}

    public void setBorderdistance(int borderdistance) {
        this.borderdistance = borderdistance;
    }

    public void setHalfwindow(int halfwindow) {
        this.halfwindow = halfwindow;
    }

    public void setGaussiansigma(double gaussiansigma) {
        this.gaussiansigma = gaussiansigma;
    }

    public void setMinDistance(double minDistance) {
        this.minDistance = minDistance;
    }

    public void setMinMeasure(double minMeasure) {
        this.minMeasure = minMeasure;
    }

    public class GradientVector {
 
		int halfwindow = 1; // 3x3 kernel
		double sigma2 = 1.4;
 
		double[][] kernelGx = new double[2*halfwindow+1][2*halfwindow+1];
		double[][] kernelGy = new double[2*halfwindow+1][2*halfwindow+1];
 
		// Constructor
		public GradientVector() {
			for(int y=-halfwindow;y<=halfwindow;y++) {
				for(int x=-halfwindow;x<=halfwindow;x++) {
					kernelGx[halfwindow+y][halfwindow+x] = Gx(x, y);
					kernelGy[halfwindow+y][halfwindow+x] = Gy(x, y);
				}
			}
		}
 
		// Kernel functions (gaussian 1st order partial derivatives)
		private double Gx(int x, int y) {
			double t = (x*x+y*y)/(2*sigma2);
			double d2t = -x / sigma2;
			double e = d2t * Math.exp( -t );
			return e;
		}
 
		private double Gy(int x, int y) {
			double t = (x*x+y*y)/(2*sigma2);
			double d2t = -y / sigma2;
			double e = d2t * Math.exp( -t );
			return e;
		}
 
		// return the Gradient Vector for pixel(x,y) 
		public double[] getVector(ImageProcessor c, int x, int y) {
			double gx=0, gy=0;
			for(int dy=-halfwindow;dy<=halfwindow;dy++) {
				for(int dx=-halfwindow;dx<=halfwindow;dx++) {
					int xk = x + dx;
					int yk = y + dy;
					double vk = c.getPixelValue(xk, yk); // <-- value of the pixel
					gx += kernelGx[halfwindow-dy][halfwindow-dx] * vk;
					gy += kernelGy[halfwindow-dy][halfwindow-dx] * vk;
				}
			}
 
			double[] gradientVector = new double[] { gx, gy };
 
			return gradientVector;
		}	
	}
}