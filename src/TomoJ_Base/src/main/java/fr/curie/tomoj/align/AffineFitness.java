package fr.curie.tomoj.align;

import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.utils.powell.Function;

import java.awt.geom.AffineTransform;
import java.util.Arrays;

/**
 * Description of the Class
 *
 * @author MESSAOUDI C�dric
 * @created 27 octobre 2008
 */
public class AffineFitness extends Function {
    float[] img1;
    float[] img2;
    float[] mask1;
    float[] mask2;
    double[] minAllowed;
    double[] maxAllowed;
    int sizex, sizey;


    /**
     * Constructor for the AffineFitness object
     *
     * @param data1    Description of the Parameter
     * @param data2    Description of the Parameter
     * @param sx       width of image
     * @param sy       height of image
     * @param maxShift Description of the Parameter
     */
    public AffineFitness(float[] data1, float[] data2, int sx, int sy, double maxShift) {
        img1 = data1;
        img2 = data2;
        sizex = sx;
        sizey = sy;
        mask1 = new float[img1.length];
        Arrays.fill(mask1, 1f);
        mask2 = new float[img2.length];
        Arrays.fill(mask2, 1f);
        minAllowed = new double[6];
        maxAllowed = new double[6];
        //scale factors
        minAllowed[0] = minAllowed[3] = 0.5;
        maxAllowed[0] = maxAllowed[3] = 1.5;
        //rotation factors
        minAllowed[1] = minAllowed[2] = -0.5;
        maxAllowed[1] = maxAllowed[2] = 0.5;
        //shifts factors
        minAllowed[4] = minAllowed[5] = -maxShift;
        maxAllowed[4] = maxAllowed[5] = maxShift;
    }

    /**
     * Number of independent variables.
     *
     * @return Description of the Return Value
     */
    protected int length() {
        return 6;
    }

    /**
     * Value of the function for independent variables, xt.
     *
     * @param xt Description of the Parameter
     * @return Description of the Return Value
     */
    protected double eval(double[] xt) {
        double score = 0;
        //check limits
        for (int i = 0; i < xt.length; i++) {
            if (xt[i] < minAllowed[i] || xt[i] > maxAllowed[i]) {
                //System.out.println("out of limit i=" + i + " xt=" + xt[i] + " min=" + minAllowed[i] + " max=" + maxAllowed[i]);
                return Double.MAX_VALUE;
            }
        }
        //System.out.println("test limit ok");

        //create affine matrices from vector;
        AffineTransform A12 = new AffineTransform(xt);
        AffineTransform A21 = null;
        try {
            A21 = A12.createInverse();
        } catch (Exception e) {
            System.out.println("error inside evaluation for Powell " + e);
            return Double.MAX_VALUE;
        }
        //check if approximately rotation
        //TO DO determinant

        //produce the transformed images
        float[] transformed1 = TiltSeries.getPixels(img1, A12, sizex, sizey, false, TiltSeries.FILL_NONE);
        float[] transformed2 = TiltSeries.getPixels(img2, A21, sizex, sizey, false, TiltSeries.FILL_NONE);

        //produce transformed masks
        float[] tMask1 = TiltSeries.getPixels(mask1, A12, sizex, sizey, false, TiltSeries.FILL_NONE);
        float[] tMask2 = TiltSeries.getPixels(mask2, A21, sizex, sizey, false, TiltSeries.FILL_NONE);

        //compute score
        score = 0.5 * (1 - correlation(transformed1, img2, tMask1, sizex * sizey) + (1 - correlation(transformed2, img1, tMask2, sizex * sizey)));
        return score;
    }

    /**
     * Description of the Method
     *
     * @param array1 Description of the Parameter
     * @param array2 Description of the Parameter
     * @param mask   Description of the Parameter
     * @param size   Description of the Parameter
     * @return Description of the Return Value
     */
    public static double correlation(float[] array1, float[] array2, float[] mask, int size) {
        double avg1 = 0;
        double avg2 = 0;
        int tot = 0;
        for (int i = 0; i < size; i++) {
            if (mask[i] > 0) {
                avg1 += array1[i];
                avg2 += array2[i];
                tot++;
            }
        }
        avg1 /= tot;
        avg2 /= tot;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double val1;
        double val2;
        for (int i = 0; i < size; i++) {
            if (mask[i] > 0) {
                val1 = (array1[i] - avg1);
                val2 = (array2[i] - avg2);
                sum1 += val1 * val2;
                sum2 += val1 * val1;
                sum3 += val2 * val2;
                tot++;
            }
        }
        return sum1 / Math.sqrt(sum2 * sum3);
    }

}
