package fr.curie.eftemtomoj.utils;

import ij.process.ImageStatistics;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

public class SubImage {
    public static float[] getSubImagePixels(final float[] imgpix, final AffineTransform Tinv, final ImageStatistics stats, final int width, final int height, final int newWidth, final int newHeight, final Point2D coordCentered, final boolean normalize, final boolean fillNaN) {
        double scx = (newWidth) / 2.0;
        double scy = (newHeight) / 2.0;
        double centerx = width / 2;
        double centery = height / 2;
        final float[] piece = new float[newWidth * newHeight];
        int jj;
        double y, yy, xx;
        int ix0, iy0;
        double fac4, fac1, fac2, fac3;
        float value1, value2, value3, value4;
        double dx0, dy0;
        double pix;
        //Point2D tmp;
        //Point2D res;
        //AffineTransform Tinv = getCombinedInverseTransform(index);
        //final float[] imgpix = (float[]) data.getPixels(index + 1);
        /*if (stats[index] == null) {
            //stats[index] = new FloatStatistics(new FloatProcessor(width, height, imgpix, null));
            stats[index] = getImageStatistics(index);
        }*/
        final double[] line = new double[newWidth * 2];
        final double[] res = new double[newWidth * 2];

        for (int i = 0; i < newWidth; i++) {
            line[i * 2] = i + coordCentered.getX() - scx;
        }

        for (int j = 0; j < newHeight; j++) {
            jj = j * newWidth;
            y = j + coordCentered.getY() - scy;
            for (int i = 0; i < newWidth; i++) {
                //x = i + coordCentered.getX() - scx;
                line[i * 2 + 1] = y;
            }
            Tinv.transform(line, 0, res, 0, newWidth);
            for (int i = 0; i < newWidth; i++) {
                xx = res[i * 2] + centerx;
                yy = res[i * 2 + 1] + centery;
                ix0 = (int) xx;
                iy0 = (int) yy;
                dx0 = xx - ix0;
                dy0 = yy - iy0;

                //System.out.println("before T ("+tmp.getX()+", "+tmp.getY()+")  after ("+res.getX()+", "+res.getY()+") ");
                if (ix0 >= 0 && ix0 < width && iy0 >= 0 && iy0 < height) {
                    //en bas a gauche
                    if (ix0 == width - 1 || iy0 == height - 1) {
                        pix = imgpix[ix0 + iy0 * width];

                    } else {
                        fac4 = (dx0 * dy0);
                        fac1 = (1 - dx0 - dy0 + fac4);
                        fac2 = (dx0 - fac4);
                        fac3 = (dy0 - fac4);

                        value1 = imgpix[ix0 + iy0 * width];
                        value2 = imgpix[ix0 + 1 + iy0 * width];
                        value3 = imgpix[ix0 + (iy0 + 1) * width];
                        value4 = imgpix[ix0 + 1 + (iy0 + 1) * width];
                        pix = value1 * fac1 + value2 * fac2 + value3 * fac3 + value4 * fac4;

                    }
                    if (normalize) {
                        pix = (float) ((pix - stats.mean) / stats.stdDev);
                    }
                    //result.putPixelValue(i, j, pix);
                    piece[jj + i] = (float) pix;

                } else {
                    if (fillNaN) piece[jj + i] = Float.NaN;
                    else piece[jj + i] = (normalize) ? 0 : (float) stats.mean;
                }


            }
        }
        return piece;
    }
}
