package fr.curie.eftemtomoj;/*
 * Copyright 2010 Nick Aschman.
 */


import fr.curie.eftemtomoj.Metrics.Metric;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Nick Aschman
 */
public class ImageRegistration extends ObservableTask {
    protected ExecutorService exec;
    public static enum Algorithm {
        Multiresolution
    }

    public static class Transform {
        private Metric metric;
        private double maxScore;
        private double translateX;
        private double translateY;
        private double rotate;
        private double scaleX = 1;
        private double scaleY = 1;

        public Transform(Metric metric, double maxScore, double translateX, double translateY, double rotate) {
            this.metric = metric;
            this.maxScore = maxScore;
            this.translateX = translateX;
            this.translateY = translateY;
            this.rotate = rotate;
        }

        public Transform(Metric metric, double maxScore, double translateX, double translateY) {
            this(metric, maxScore, translateX, translateY, 0.0);
        }

        public Transform(double translateX, double translateY, double rotate) {
            this.translateX = translateX;
            this.translateY = translateY;
            this.rotate = rotate;
        }

        public Transform(double translateX, double translateY, double rotate, double scaleX, double scaleY) {
            this.translateX = translateX;
            this.translateY = translateY;
            this.rotate = rotate;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }

        public Transform(double translateX, double translateY) {
            this(translateX, translateY, 0.0);
        }

        public double getRotate() {
            return rotate;
        }

        public double getTranslateX() {
            return translateX;
        }

        public double getTranslateY() {
            return translateY;
        }

        public Metric getMetric() {
            return metric;
        }

        public void setTranslate(double tx, double ty) {
            translateX = tx;
            translateY = ty;
        }

        public void addTranslation(double tx, double ty) {
            translateX += tx;
            translateY += ty;
        }

        public void setRotate(double rot) {
            rotate = rot;
        }

        public void setTransform(Transform other2copyFrom) {
            this.translateX = other2copyFrom.translateX;
            this.translateY = other2copyFrom.translateY;
            this.rotate = other2copyFrom.rotate;
            this.scaleY = other2copyFrom.scaleY;
            this.scaleX = other2copyFrom.scaleX;
        }

        public void setScale(double scalex, double scaley) {
            this.scaleX = scalex;
            this.scaleY = scaley;
        }

        public double getScaleX() {
            return scaleX;
        }

        public double getScaleY() {
            return scaleY;
        }

        public AffineTransform getAffineTransform() {
            AffineTransform T = new AffineTransform();
            T.scale(getScaleX(), getScaleY());
            T.rotate(Math.toRadians(getRotate()));
            T.translate(getTranslateX(), getTranslateY());
            return T;
        }

    }

    private int multiResMinWidth = 16;
    private int multiResMaxShift = 3;

    private final Algorithm algorithm;
    private final Metric metric;

    public ImageRegistration(Algorithm algorithm, Metric metric) {
        this.algorithm = algorithm;
        this.metric = metric;
    }

    public void setExecutorService(ExecutorService exec) {
        this.exec = exec;
    }

    public ExecutorService getExecutorService() {
        return exec;
    }

    public int getMultiResMaxShift() {
        return multiResMaxShift;
    }

    public void setMultiResMaxShift(int multiResMaxShift) {
        this.multiResMaxShift = multiResMaxShift;
    }

    public int getMultiResMinWidth() {
        return multiResMinWidth;
    }

    public void setMultiResMinWidth(int multiResMinWidth) {
        this.multiResMinWidth = multiResMinWidth;
    }

    public Transform align(ImageProcessor fip, ImageProcessor mip) throws Exception {
        switch (algorithm) {
            case Multiresolution:
            default:
                return alignMultires(fip, mip);
        }
    }

    public Transform[] alignSeries(ImageProcessor fip, FilteredImage[] mips, double radius) throws Exception {
        switch (algorithm) {
            case Multiresolution:
            default:
                return alignMultiresSeries(fip, mips, radius);
        }
    }

    /**
     * Align moving image with fixed image using multi-resolution rigid transforms.
     *
     * @param fip
     * @param mip
     * @return
     * @throws Exception
     */
    private Transform alignMultires(ImageProcessor fip, ImageProcessor mip) throws Exception {
        ImageProcessor sfip, smip;
        Transform transform;
        Rectangle fcrop, mcrop;
        double maxScore;
        int shiftX, shiftY, sexp, smax, swidth;

        // Find minimum scale
        sexp = 0;
        swidth = fip.getWidth();
        do {
            sexp++;
            swidth /= Math.pow(2, sexp);
        } while (swidth >= multiResMinWidth);
        smax = sexp;

        // Set initial shifts
        shiftX = 0;
        shiftY = 0;

        maxScore = Double.NaN;
        fcrop = new Rectangle();
        mcrop = new Rectangle();

        for (sexp = smax; sexp >= 0; sexp--) {
            swidth = (int) (fip.getWidth() / Math.pow(2, sexp));

            // Create scaled-down image
            sfip = fip.resize(swidth);
            smip = mip.resize(swidth);

            // Re-scale initial shifts
            shiftX *= 2;
            shiftY *= 2;

            // Crop according to initial shift
            if (shiftX >= 0) {
                fcrop.x = shiftX;
                fcrop.width = sfip.getWidth();
                mcrop.x = 0;
                mcrop.width = fcrop.width;
            } else {
                fcrop.x = 0;
                fcrop.width = sfip.getWidth() + shiftX;
                mcrop.x = 0 - shiftX;
                mcrop.width = fcrop.width;
            }

            if (shiftY >= 0) {
                fcrop.y = shiftY;
                fcrop.height = sfip.getHeight();
                mcrop.y = 0;
                mcrop.height = fcrop.height;
            } else {
                fcrop.y = 0;
                fcrop.height = sfip.getHeight() + shiftY;
                mcrop.y = 0 - shiftY;
                mcrop.height = fcrop.height;
            }

            sfip.setRoi(fcrop);
            sfip = sfip.crop();
            smip.setRoi(mcrop);
            smip = smip.crop();

//	    System.out.println("Scale " + swidth + " | Starting offset " + shiftX + ", " + shiftY);
            transform = alignRigid(sfip, smip, multiResMaxShift, multiResMaxShift, swidth);
//	    System.out.println(String.format("Inter transl %1$5d, %2$5d | Score %3$10.4f", ir.translateX, ir.translateY, ir.maxScore));
//	    System.out.println("");

            // Set initial shift for next scale
            shiftX += transform.translateX;
            shiftY += transform.translateY;

            if (Double.isNaN(maxScore) || transform.maxScore > maxScore)
                maxScore = transform.maxScore;
        }

//	System.out.println("Shift (" + shiftX + ", " + shiftY + ") for reduced dimension w=" + swidth);

//	System.out.println(String.format("Final transl %1$5d, %2$5d", shiftX, shiftY));
//	System.out.println("");
        Transform T = new Transform(metric, maxScore, shiftX, shiftY);

        return T;
    }

    /**
     * Concurrently align several moving images with a fixed image using multi-resolution rigid transforms.
     *
     * @param fip
     * @param mips
     * @return
     * @throws Exception
     */
    private Transform[] alignMultiresSeries(ImageProcessor fip, FilteredImage[] mips, double radius) throws Exception {
        ImageProcessor[] sfips;
        Transform[] transforms;
        int sexp, smax, swidth;

        // Init thread pool
        int cpus = Runtime.getRuntime().availableProcessors();
        if (exec == null) exec = Executors.newFixedThreadPool(cpus);

        transforms = new Transform[mips.length];

        // Find minimum scale
        sexp = 0;
        //System.out.println("fip roi:"+fip.getRoi());
        //System.out.println("mip roi:"+mips[0].getImage().getRoi());
        //System.out.println("mip ali roi:" + mips[0].getImageForAlignment(0).getRoi());
        //System.out.println("before:"+fip.getWidth());
        fip = fip.crop();

        //System.out.println("after:"+fip.getWidth());
        swidth = fip.getWidth();
        do {
            sexp++;
            swidth /= Math.pow(2, sexp);
        } while (swidth >= multiResMinWidth);
        smax = sexp;

        // Rescale fixed image
        sfips = new ImageProcessor[smax + 1];
        for (sexp = smax; sexp >= 0; sexp--) {
            swidth = (int) (fip.getWidth() / Math.pow(2, sexp));
            sfips[sexp] = fip.resize(swidth);
        }

        // Launch worker threads
        List<Future<Transform>> futures = new ArrayList<Future<Transform>>();
        for (int i = 0; i < mips.length; i++) {
            futures.add(exec.submit(new StackRegistrationTask(this, sfips, mips[i].getImageForAlignment(radius), smax)));
        }
        for (Future f : futures) {
            f.get();
        }
        //exec.shutdown();
//	pool.awaitTermination(10, TimeUnit.DAYS);

        // Retrieve results
        for (int i = 0; i < futures.size(); i++) {
            transforms[i] = futures.get(i).get();
        }

        return transforms;
    }

    /**
     * Align moving image with fixed image using rigid transforms (translation + rotation).
     * NOTE: rotations are not yet implemented
     *
     * @param fip
     * @param mip
     * @param maxShiftX
     * @param maxShiftY
     * @param param
     * @return
     * @throws Exception
     */
    private Transform alignRigid(ImageProcessor fip, ImageProcessor mip, int maxShiftX, int maxShiftY, int... param) throws Exception {
        ImageProcessor tfip, tmip;
        Rectangle fcrop, mcrop;
        double score, maxScore;
        int maxScoreShiftX, maxScoreShiftY;

        fcrop = new Rectangle();
        mcrop = new Rectangle();

        maxScore = Double.NaN;
        maxScoreShiftX = 0;
        maxScoreShiftY = 0;

        // Crop fixed image
        fcrop.x = maxShiftX;
        fcrop.y = maxShiftY;
        fcrop.width = fip.getWidth() - 2 * maxShiftX;
        fcrop.height = fip.getHeight() - 2 * maxShiftY;
        fip.setRoi(fcrop);
        tfip = fip.crop();

        mcrop.width = fcrop.width;
        mcrop.height = fcrop.height;

        for (int shiftX = -maxShiftX; shiftX <= maxShiftX; shiftX++) {
            for (int shiftY = -maxShiftY; shiftY <= maxShiftY; shiftY++) {

                // Crop moving image
                mcrop.x = maxShiftX - shiftX;
                mcrop.y = maxShiftY - shiftY;
                mip.setRoi(mcrop);
                tmip = mip.crop();

                // Calculate score
                switch (metric) {
                    case NMI:
                        score = Metrics.nmi(tfip, tmip, param[0]);
                        break;

                    case MS:
                        score = Metrics.ms(tfip, tmip);
                        break;

                    case NCC:
                    default:
                        score = Metrics.ncc(tfip, tmip);
                        break;
                }

//		System.out.println(String.format(" Translation %1$5d, %2$5d | Score %3$10.4f", shiftX, shiftY, score));

                if (Double.isNaN(maxScore) || score > maxScore || (score == maxScore && (Math.abs(shiftX) < Math.abs(maxScoreShiftX) || Math.abs(shiftY) < Math.abs(maxScoreShiftY)))) {
                    maxScore = score;
                    maxScoreShiftX = shiftX;
                    maxScoreShiftY = shiftY;
                }

//		System.out.println("Shift (" + shiftX + ", " + shiftY + "), S=" + score);
            }
        }

        return new Transform(metric, maxScore, maxScoreShiftX, maxScoreShiftY);
    }

    private static class StackRegistrationTask implements Callable<Transform> {
        private final ImageProcessor[] sfips;
        private final ImageProcessor mip;
        private final ImageRegistration ir;
        private final int smax;

        public StackRegistrationTask(ImageRegistration ir, ImageProcessor[] sfips, ImageProcessor mip, int smax) {
            this.sfips = sfips;
            this.mip = mip.crop();
            this.ir = ir;
            this.smax = smax;
        }

        public Transform call() throws Exception {
            //System.out.println("fixed:"+sfips[0].getWidth()+"  ,  moving:"+mip.getWidth());
            ImageProcessor sfip, smip;
            Transform transform;
            Rectangle fcrop, mcrop;
            double maxScore;
            int shiftX, shiftY, sexp, swidth;

            // Set initial shifts
            shiftX = 0;
            shiftY = 0;

            maxScore = Double.NaN;
            fcrop = new Rectangle();
            mcrop = new Rectangle();

            for (sexp = smax; sexp >= 0; sexp--) {
                sfip = sfips[sexp];
                swidth = sfip.getWidth();

                // Create scaled-down image
                smip = mip.resize(swidth);

                // Re-scale initial shifts
                shiftX *= 2;
                shiftY *= 2;

                // Crop according to initial shift
                if (shiftX >= 0) {
                    fcrop.x = shiftX;
                    fcrop.width = sfip.getWidth();
                    mcrop.x = 0;
                    mcrop.width = fcrop.width;
                } else {
                    fcrop.x = 0;
                    fcrop.width = sfip.getWidth() + shiftX;
                    mcrop.x = 0 - shiftX;
                    mcrop.width = fcrop.width;
                }

                if (shiftY >= 0) {
                    fcrop.y = shiftY;
                    fcrop.height = sfip.getHeight();
                    mcrop.y = 0;
                    mcrop.height = fcrop.height;
                } else {
                    fcrop.y = 0;
                    fcrop.height = sfip.getHeight() + shiftY;
                    mcrop.y = 0 - shiftY;
                    mcrop.height = fcrop.height;
                }

                synchronized (sfip) {
                    sfip.setRoi(fcrop);
                    sfip = sfip.crop();
                }

                synchronized (smip) {
                    smip.setRoi(mcrop);
                    smip = smip.crop();
                }

                transform = ir.alignRigid(sfip, smip, ir.multiResMaxShift, ir.multiResMaxShift, swidth);

                // Set initial shift for next scale
                shiftX += transform.translateX;
                shiftY += transform.translateY;

                if (Double.isNaN(maxScore) || transform.maxScore > maxScore)
                    maxScore = transform.maxScore;
            }

            return new Transform(ir.metric, maxScore, shiftX, shiftY);
        }

    }
}
