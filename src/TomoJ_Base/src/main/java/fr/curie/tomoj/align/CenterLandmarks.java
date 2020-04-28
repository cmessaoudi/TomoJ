package fr.curie.tomoj.align;

import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;

public class CenterLandmarks {
    public static void centerLandmarks(TomoJPoints tp) {
        TiltSeries ts= tp.getTiltSeries();
        ArrayList<Point2D[]> landmarks=tp.getAllLandmarks();
        if(!(ts.getAlignment() instanceof AffineAlignment)) ts.setAlignment(new AffineAlignment(ts));
        AffineAlignment alignment=(AffineAlignment)ts.getAlignment();

        int nbpoints = tp.getNumberOfPoints();
        //0� centering
        AffineTransform Tmp = new AffineTransform();
        double avgx = 0;
        double avgy = 0;
        int nb = 0;
        Point2D tmp;
        //System.out.println("nb points="+nbpoints);
        for (int i = 0; i < nbpoints; i++) {
            tmp = landmarks.get(i)[ts.getZeroIndex()];
            //System.out.println("i="+i+" tmp="+tmp);
            if (tmp != null) {
                avgx += tmp.getX();
                avgy += tmp.getY();
                nb++;
            }
        }
        //System.out.println("avgx="+avgx+" avgy="+avgy+" nb="+nb);
        if (nb != 0) {
            avgx /= nb;
            avgy /= nb;
            Tmp.translate(-avgx + ts.getCenterX(), -avgy + ts.getCenterY());
            alignment.setZeroTransform(Tmp);
        }
//forward
        for (int imgnb = ts.getZeroIndex() + 1; imgnb < ts.getStackSize(); imgnb++) {
            avgx = 0;
            avgy = 0;
            nb = 0;
            //System.out.println("nb points="+nbpoints);
            for (int i = 0; i < nbpoints; i++) {
                tmp = landmarks.get(i)[imgnb];
                //System.out.println("i="+i+" tmp="+tmp);
                if (tmp != null) {
                    avgx += tmp.getX();
                    avgy += tmp.getY();
                    nb++;
                }
            }
            //System.out.println("avgx="+avgx+" avgy="+avgy+" nb="+nb);
            if (nb != 0) {
                avgx /= nb;
                avgy /= nb;
                try {
                    Tmp = new AffineTransform();
                    Tmp.translate(avgx - ts.getCenterX(), avgy - ts.getCenterY());
                    Tmp.createInverse();
                    //System.out.println("transform on img "+imgnb+ " "+Tmp);
                    AffineTransform T0 = alignment.getTransform(imgnb - 1);
                    //System.out.println("transform 0 on img "+imgnb+ " "+T0);
                    Tmp.concatenate(T0);
                    //System.out.println("transform on img "+imgnb+ " "+Tmp);
                    alignment.setTransform(imgnb - 1, Tmp);
                } catch (Exception e) {
                    System.out.println("error " + e);
                }
            }
        }

        //backward
        for (int imgnb = ts.getZeroIndex() - 1; imgnb >= 0; imgnb--) {
            avgx = 0;
            avgy = 0;
            nb = 0;
            //System.out.println("nb points="+nbpoints);
            for (int i = 0; i < nbpoints; i++) {
                tmp = landmarks.get(i)[imgnb];
                //System.out.println("i="+i+" tmp="+tmp);
                if (tmp != null) {
                    avgx += tmp.getX();
                    avgy += tmp.getY();
                    nb++;
                }
            }
            //System.out.println("avgx="+avgx+" avgy="+avgy+" nb="+nb);
            if (nb != 0) {
                avgx /= nb;
                avgy /= nb;
                try {
                    Tmp = new AffineTransform();
                    Tmp.translate(-avgx + ts.getCenterX(), -avgy + ts.getCenterY());
                    //System.out.println("transform on img "+imgnb+ " "+Tmp);
                    AffineTransform T0 = alignment.getTransform(imgnb + 1).createInverse();
                    //System.out.println("transform 0 on img "+imgnb+ " "+T0);
                    Tmp.preConcatenate(T0);
                    //System.out.println("transform on img "+imgnb+ " "+Tmp);
                    alignment.setTransform(imgnb, Tmp);
                } catch (Exception e) {
                    System.out.println("" + e);
                }

            }
        }
    }

}
