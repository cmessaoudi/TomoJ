package fr.curie.tomoj.landmarks;

import java.awt.geom.Point2D;

/**
 * Created by cedric on 13/04/2015.
 */
public class LandmarksChain {
            private Point2D.Double[] landmarkchain;
            private double corr;
            private int imgIndex;

            public LandmarksChain(Point2D.Double[] landmarkchain, double corr, int imgIndex) {
                this.landmarkchain = landmarkchain;
                this.corr = corr;
                this.imgIndex = imgIndex;
            }

            public double getCorrelation() {
                return corr;
            }

            public void setCorrelation(double corr) {
                this.corr = corr;
            }

            public int getImgIndex() {
                return imgIndex;
            }

            public Point2D.Double[] getLandmarkchain() {
                return landmarkchain;
            }

            public void setLandmarkchain(Point2D.Double[] landmarkchain) {
                this.landmarkchain = landmarkchain;
            }
        }
