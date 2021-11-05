package fr.curie.eftemtomoj;/*
 * Copyright 2010 Nick Aschman.
 */



import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.DataFormatException;

/**
 * @author Nick Aschman
 */
public class Mapper extends ObservableTask implements TaskObserver {

    /**
     * Regression models supported for background subtraction.
     * <p/>
     * Linear:		y = A + Bx
     * Quadratic:	y = A + Bx + Cx^2
     * Power:		y = A * x^B
     * Exponential:	y = A * e^(Bx)
     * Logarithmic:	y = A + B * ln(x)
     * Log-polynomial:	y = A + B*ln(x) + C*ln(x)^2
     * log-log-polynomial: log(y)= A + B*ln(x) + C*ln(x)^2
     */
    public static enum Model {
        Linear, Quadratic, Exponential, Power, Logarithmic, LogPolynomial, LogLogPolynomial, Ratio, Subtract
    }


    public static enum ImageType {
        Background, Signal, Disabled
    }

    private FilteredImage[] images;
    private final ImageType[] imageTypes;
    private final int width, height;

    private double progressChunkSize = 0.0;
    private double progress = 0.0;

    private Model model = Model.Power;

    public Mapper(EftemDataset dataSet) {
        images = dataSet.getMappingImages();
        width = dataSet.getWidth();
        height = dataSet.getHeight();
        imageTypes = new ImageType[images.length];
        Arrays.fill(imageTypes, ImageType.Disabled);
        for (int i = 0; i < images.length; i++) {
            if (images[i].isSignal()) {
                imageTypes[i] = ImageType.Signal;
            } else if (images[i].isDisabled()) {
                imageTypes[i] = ImageType.Disabled;
            } else {
                imageTypes[i] = ImageType.Background;
            }

        }
        setModel(dataSet.getLaw());
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public FilteredImage[] getImages() {
        return images;
    }

    public void setImages(FilteredImage[] imgs) {
        images = imgs;
    }

    public void setSignal(int i) {
        imageTypes[i] = ImageType.Signal;
    }

    public void setBackground(int i) {
        imageTypes[i] = ImageType.Background;
    }

    public void setDisabled(int i) {
        imageTypes[i] = ImageType.Disabled;
    }

    public boolean isSignal(int i) {
        return (imageTypes[i] == ImageType.Signal);
    }

    public boolean isBackground(int i) {
        return (imageTypes[i] == ImageType.Background);
    }

    public boolean isDisabled(int i) {
        return (imageTypes[i] == ImageType.Disabled);
    }

    public int getSignalCount() {
        int count = 0;
        for (ImageType imageType : imageTypes) {
            if (imageType == ImageType.Signal)
                count++;
        }

        return count;
    }

    public int getBackgroundCount() {
        int count = 0;
        for (ImageType imageType : imageTypes) {
            if (imageType == ImageType.Background)
                count++;
        }

        return count;
    }

    public int getDisabledCount() {
        int count = 0;
        for (ImageType imageType : imageTypes) {
            if (imageType == ImageType.Disabled)
                count++;
        }

        return count;
    }

    public ImageType[] getImageTypes() {
        return imageTypes;
    }

    public void setImageTypes(ImageType[] types) {
        System.arraycopy(types, 0, imageTypes, 0, imageTypes.length);
    }

    public ImageType getImageType(int i) {
        return imageTypes[i];
    }

    public Regression computeRegressionCurve(int npoints, Roi roi) throws Exception {
        int sigCount = getSignalCount();
        int bgdCount = getBackgroundCount();

        if (sigCount < 1 || bgdCount < 2) {
            return null;
        }

        double[] bX, bY;
        float[] rX, rY;
        float dX, dY, dW;
        float minX, maxX, stepX, val;

        minX = Float.NaN;
        maxX = Float.NaN;

        // Get data points
        bX = new double[bgdCount];
        bY = new double[bgdCount];

        int j = 0;
        for (int i = 0; i < images.length; i++) {
            dX = images[i].getEnergyShift();
            dW = images[i].getSlitWidth();
            dY = (float) images[i].calculateMean(roi);

            val = dX - dW;
            if (Float.isNaN(minX) || val < minX) minX = val;

            val = dX + dW;
            if (Float.isNaN(maxX) || val > maxX) maxX = val;

            if (imageTypes[i] == ImageType.Background && j < bgdCount) {
                bX[j] = dX;
                bY[j] = dY;
                j++;
            }
        }

        stepX = (maxX - minX) / npoints;

        // Fit data
        Fit fit = new Fit(bX, bY, model);

        // Calculate regression points
        rX = new float[npoints];
        rY = new float[npoints];
        for (int i = 0; i < npoints; i++) {
            rX[i] = i * stepX + minX;
            rY[i] = (float) fit.predict(rX[i]);
        }

        return new Regression(rX, rY);
    }

    public String usedEnergyAsString() {
        String result = "using ";
        for (int i = 0; i < images.length; i++) {
            if (imageTypes[i] == ImageType.Background) {
                result += images[i].getEnergyShift();
            } else if (imageTypes[i] == ImageType.Signal) {
                result += images[i].getEnergyShift() + "s";
            }
            if (i < images.length - 1) result += "_";
        }
        result += "eV";
        return result;
    }

    public ImageProcessor[] computeMap() throws Exception {
        return computeMap(images);
    }

    public ImageProcessor[] computeMap(FilteredImage[] images) throws Exception {
        int sigCount = getSignalCount();
        int bgdCount = getBackgroundCount();

        if ((sigCount < 1 || bgdCount < 2) && (model != Model.Ratio && model != Model.Subtract)) {
            throw new Exception("At least one signal and two background images are required for mapping");
        }

        ImageProcessor[] result;
        List<FilteredImage> bgdSet, sigSet;

        progressChunkSize = 1.0;
        progress = 0.0;
        setProgress(progress);

        // Create image subsets
        sigSet = new ArrayList<FilteredImage>();
        bgdSet = new ArrayList<FilteredImage>();
        for (int i = 0; i < images.length; i++) {
            if (imageTypes[i] == ImageType.Background) {
                bgdSet.add(images[i]);
            } else if (imageTypes[i] == ImageType.Signal) {
                sigSet.add(images[i]);
            }
        }

        // Call worker (same thread)
        MapperTask mt = new MapperTask(sigSet.toArray(new FilteredImage[]{}), bgdSet.toArray(new FilteredImage[]{}), model, this);
//	MapperTask mt = new MapperTask(signalImages, backgroundImages, model, this);
        result = mt.call();

        return result;
    }

    public ImageStack[] computeMaps(EftemDataset ds) throws Exception {
        int sigCount = getSignalCount();
        int bgdCount = getBackgroundCount();
        System.out.println("model: " + model + " signal: " + sigCount + " bg: " + bgdCount);


        if ((sigCount < 1 || bgdCount < 2) && (model != Model.Ratio && model != Model.Subtract)) {
            throw new Exception("At least one signal and two background images are required for mapping");
        }
        if (bgdCount < 3 && (model == Model.Quadratic || model == Model.LogPolynomial || model == Model.LogLogPolynomial)) {
            throw new Exception("At least three background images are required for mapping with this law(" + model + ")");
        }

        ImageStack stack, r2stack;
        ImageProcessor[] maps;
        FilteredImage[] allImages;
        List<FilteredImage> bgdSet, sigSet;

        // Init thread pool
        int cpus = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(cpus);

        stack = new ImageStack(width, height);
        r2stack = new ImageStack(width, height);

        // Set up worker threads
        progressChunkSize = 1.0 / ds.getTiltCount();
        progress = 0.0;
        setProgress(progress);

        List<Future<ImageProcessor[]>> futures = new ArrayList<Future<ImageProcessor[]>>();
        for (int i = 0; i < ds.getTiltCount(); i++) {

            // Create image subsets
            allImages = ds.getMappingImages(i);
            sigSet = new ArrayList<FilteredImage>();
            bgdSet = new ArrayList<FilteredImage>();
            for (int j = 0; j < allImages.length; j++) {
                if (imageTypes[j] == ImageType.Background) {
                    bgdSet.add(allImages[j]);
                } else if (imageTypes[j] == ImageType.Signal) {
                    sigSet.add(allImages[j]);
                }
            }

            // Launch worker thread
            futures.add(pool.submit(new MapperTask(sigSet.toArray(new FilteredImage[]{}), bgdSet.toArray(new FilteredImage[]{}), model, this)));
//	    futures.add(pool.submit(new MapperTask(ds.getSignalImages(i), ds.getBackgroundImages(i), model, this)));
        }

        pool.shutdown();
//	pool.awaitTermination(10, TimeUnit.DAYS);

        // Retrieve results
        for (int i = 0; i < futures.size(); i++) {
            maps = futures.get(i).get();
            stack.addSlice("" + ds.getTiltAngle(i), maps[0]);
            r2stack.addSlice("" + ds.getTiltAngle(i), maps[1]);
        }

        return new ImageStack[]{stack, r2stack};
    }

    public void updateProgress(double p) {
        // Ignore
    }

    public void updateProgressBy(double p) {
        if (progress >= 0.0) {
            progress += p * progressChunkSize;
            setProgress(progress);
        }
    }

    public void updateMessage(String msg) {
        // Ignore
    }

    public static class Fit {
        private final int dim;
        private final double[] x, y;
        private final Model fitType;

        private double r2;
        private double coef[];
        private double xori = 0;
        private double yoffset = 0;

        public Fit(double[] dx, double[] dy, Model type) throws Exception {
            if (dx.length != dy.length) {
                throw new Exception("X and Y lengths differ");
            }

            dim = dx.length;
            x = dx;
            y = dy;

            fitType = type;
            switch (fitType) {
                case Power:
                    coef = new double[2];
                    for (int i = 0; i < dim; i++) {
                        x[i] = Math.log(x[i]);
                        y[i] = Math.log(y[i]);
                    }
                    linearFit();
                    break;

                case Exponential:
                default:
                    coef = new double[2];
                    for (int i = 0; i < dim; i++) {
                        y[i] = Math.log(y[i]);
                    }
                    linearFit();
                    break;

                case Logarithmic:
                    coef = new double[2];
                    for (int i = 0; i < dim; i++) {
                        x[i] = Math.log(x[i]);
                    }
                    linearFit();
                    break;

                case Linear:
                    coef = new double[2];
                    linearFit();
                    break;

                case Quadratic:
                    coef = new double[3];
                    polynomialFit(2);
                    break;

                case LogPolynomial:
                    coef = new double[3];
                    for (int i = 0; i < dim; i++) {
                        x[i] = Math.log(x[i]);
                    }
                    polynomialFit(2);
                    break;
                case LogLogPolynomial:
                    coef = new double[3];
                    //xori=x[0]-200;
                    for (int i = 0; i < dim; i++) {
                        x[i] = Math.log(x[i]);
                        y[i] = Math.log(y[i]);
                    }
                    polynomialFit(2);
                    break;
                case Ratio:
                case Subtract:
                    coef = new double[2];
                    coef[1] = 0;
                    coef[0] = y[0];
                    break;
            }
            computeScore(dx, dy);
        }

        private void computeScore(double[] dx, double[] dy) {
            r2 = 0;
            double sxy = 0.0;
            double sxx = 0.0;
            double syy = 0.0;
            for (int i = 0; i < dim; i++) {
                double yt = predict(dx[i]);
                sxy += (yt) * (dy[i]);
                sxx += (yt) * (yt);
                syy += (dy[i]) * (dy[i]);
            }
            r2 = (sxy / Math.sqrt(sxx * syy)) * (sxy / Math.sqrt(sxx * syy));
        }

        private void linearFit() {
            double xm, ym, sxy, sxx, syy;

            // Calculate parameters by least squares
            xm = 0.0;
            ym = 0.0;
            for (int i = 0; i < dim; i++) {
                xm += x[i];
                ym += y[i];
            }
            xm /= dim;
            ym /= dim;

            sxy = 0.0;
            sxx = 0.0;
            syy = 0.0;
            for (int i = 0; i < dim; i++) {
                sxy += (x[i] - xm) * (y[i] - ym);
                sxx += (x[i] - xm) * (x[i] - xm);
                syy += (y[i] - ym) * (y[i] - ym);
            }

            coef[1] = sxy / sxx;
            coef[0] = ym - coef[1] * xm;
            r2 = (sxy / Math.sqrt(sxx * syy)) * (sxy / Math.sqrt(sxx * syy));
        }

        private void polynomialFit(int d) throws Exception {
            if (x.length < 3)
                throw new DataFormatException("At least three background energies needed for polynomial fittting");
            DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
            DoubleMatrix1D Y = DoubleFactory1D.dense.make(y);

            // Initialise design matrix
            DoubleMatrix2D X = DoubleFactory2D.dense.make(x.length, d + 1, 1.0);
            for (int i = 0; i < X.rows(); i++) {
                for (int j = 1; j < X.columns(); j++) {
                    X.set(i, j, Math.pow(x[i], j));
                }
            }
            //System.out.println("polyfit X="+X);
            // Calculate hat matrix and coefficients
            DoubleMatrix2D Xt = alg.transpose(X);
            DoubleMatrix2D hat = alg.inverse(alg.mult(Xt, X));
            DoubleMatrix1D A = alg.mult(alg.mult(hat, Xt), Y);

            coef = A.toArray();
            r2 = Double.NaN;
        }

        public double predict(double xp) {
            double yp;

            switch (fitType) {
                case Power:
                    yp = Math.exp(coef[0] + coef[1] * Math.log(xp));
                    break;

                case Logarithmic:
                    yp = coef[0] + coef[1] * Math.log(xp);
                    break;

                case Exponential:
                    yp = Math.exp(coef[0] + coef[1] * xp);
                    break;

                case Linear:
                default:
                    yp = coef[0] + coef[1] * xp;
                    break;

                case Quadratic:
                    yp = coef[0] + coef[1] * xp + coef[2] * xp * xp;
                    break;

                case LogPolynomial:
                    double logxp = Math.log(xp);
                    yp = coef[0] + coef[1] * logxp + coef[2] * logxp * logxp;
                    break;

                case LogLogPolynomial:
                    double logxp2 = Math.log(xp);
                    // System.out.println("xp="+xp+" xori="+xori);
                    yp = Math.exp(coef[0] + coef[1] * logxp2 + coef[2] * logxp2 * logxp2);
                    break;
                case Ratio:
                case Subtract:
                    yp = coef[0] + coef[1] * xp;
                    break;
            }

            return yp;
        }
    }

    public static class Regression {
        public final float[] rX, rY;

        public Regression(float[] rX, float[] rY) {
            this.rX = rX;
            this.rY = rY;
        }
    }

    public static class MapperTask extends ObservableTask implements Callable<ImageProcessor[]> {

        private final FilteredImage[] signalImages, backgroundImages;
        private final Model model;
        private final int width, height;

        public MapperTask(FilteredImage[] signalImages, FilteredImage[] backgroundImages, Model model, TaskObserver observer) {
            this.signalImages = signalImages;
            this.backgroundImages = backgroundImages;
            this.model = model;
            this.width = signalImages[0].getImage().getWidth();
            this.height = signalImages[0].getImage().getHeight();

            Arrays.sort(signalImages, new FIComparator());
            Arrays.sort(backgroundImages, new FIComparator());

            addObserver(observer);
        }

        public ImageProcessor[] call() throws Exception {
            final int dim = width * height;
            double[] bX, bY;
            double sX, sY, sW, sX2, sW2, pY, rY, R2, progStep, ol;

            Fit fit;
            ImageProcessor map, r2map;
            int lessThanZeroCount = 0;

            bX = new double[backgroundImages.length];
            bY = new double[backgroundImages.length];

            map = new FloatProcessor(width, height);
            r2map = new FloatProcessor(width, height);

            progStep = 1.0 / dim;
            setProgress(0);

            for (int j = 0; j < dim; j++) {
                progressBy(progStep);

                // Fetch background data points
                for (int b = 0; b < backgroundImages.length; b++) {
                    bX[b] = backgroundImages[b].getEnergyShift();
                    bY[b] = backgroundImages[b].getImage().getf(j);
                }

                rY = 0.0;

                // Fit background curve
                fit = new Fit(bX, bY, model);

                // For each signal point, calculate predicted value and residual. Integrate signal data points.
                for (int s = 0; s < signalImages.length; s++) {
                    sX = signalImages[s].getEnergyShift();
                    sW = signalImages[s].getSlitWidth();
                    sY = signalImages[s].getImage().getf(j);

                    // Correct for energy window overlap with previous image
                    if (s > 0) {
                        sX2 = signalImages[s - 1].getEnergyShift();
                        sW2 = signalImages[s - 1].getSlitWidth();
                        ol = (sX2 + sW2 / 2) - (sX - sW / 2);

                        if (ol > 0) {
                            sY /= (sW - sW / ol);
                        }
                    }

                    // Predict background point
                    pY = fit.predict(sX);

                    // Add net signal
                    if (sY > pY) {
                        lessThanZeroCount++;
                        if (model == Model.Ratio) rY += sY / pY;
                        else rY += sY - pY;
                    }
                    //}
                }

                R2 = fit.r2;

                // Store residual and R2
                map.setf(j, (float) rY);
                r2map.setf(j, (float) R2);
            }

            // System.out.println("there are " + lessThanZeroCount + " points with a value less than 0!");

            return new ImageProcessor[]{map, r2map};
        }

        static class FIComparator implements Comparator<FilteredImage> {

            public int compare(FilteredImage t, FilteredImage t1) {
                return Float.compare(t.getEnergyShift(), t1.getEnergyShift());
            }

        }
    }
}
