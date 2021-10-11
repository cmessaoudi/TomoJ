package fr.curie.eftemtomoj;/*
 * Copyright 2010 Nick Aschman.
 */

import fr.curie.eftemtomoj.*;
import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleEigenvalueDecomposition;
import cern.jet.math.tdouble.DoubleFunctions;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Nick Aschman
 */
public class MSANoiseFilter {

    public static PCA decompose(FilteredImage[] series, Rectangle mask) {
        int n = series.length;
        int w = series[0].getImage().getWidth();
        int h = series[0].getImage().getHeight();
        int m = w * h;
        int x0 = (mask == null) ? 0 : mask.x;
        int y0 = (mask == null) ? 0 : mask.y;
        int x1 = x0 + ((mask == null) ? w : mask.width);
        int y1 = y0 + ((mask == null) ? h : mask.height);

        DenseDoubleAlgebra algebra;
        DenseDoubleEigenvalueDecomposition eig;
        DoubleMatrix2D vcov;
        DoubleMatrix2D data;
        DoubleMatrix1D datameans;
        DoubleMatrix1D datadevs;
        ImageProcessor ip;
        double value;
        double[] margin;

        algebra = new DenseDoubleAlgebra();

        // Parse images into data matrix and calculate column margin
        data = DoubleFactory2D.dense.make(n, m);
        margin = new double[m];

        for (int i = 0; i < n; i++) {
            ip = series[i].getImage();

            int j = 0;
            for (int x = x0; x < x1; x++) {
                for (int y = y0; y < y1; y++) {
                    value = ip.getf(x, y);
                    data.set(i, j, value);
                    margin[j] += value;
                    j++;
                }
            }
        }

        // Subtract mean image from data
        datameans = DoubleFactory1D.dense.make(margin);
        datameans.assign(DoubleFunctions.div(m));
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                data.set(i, j, data.get(i, j) - datameans.get(j));
            }
        }

        // Calculate covariance matrix and standard deviations
        vcov = DoubleFactory2D.dense.make(n, n);
        datadevs = DoubleFactory1D.dense.make(n);
        for (int i1 = 0; i1 < n; i1++) {
            for (int i2 = 0; i2 < n; i2++) {
                value = 0.0;
                for (int j = 0; j < m; j++) {
                    value += data.get(i1, j) * data.get(i2, j);
                }
                value /= (m - 1);

                vcov.set(i1, i2, value);
                if (i1 == i2) {
                    datadevs.set(i1, Math.sqrt(value));
                }
            }
        }

        // Normalise data
        for (int i = 0; i < n; i++) {
            value = datadevs.get(i);

            for (int j = 0; j < m; j++) {
                data.set(i, j, data.get(i, j) / value);
            }
        }

        // Covariance matrix eigendecomposition
        eig = algebra.eig(vcov);

        // Create and return PCA object
        PCA pca = new PCA(data, w, h, x0, y0, x1, y1, datameans, datadevs, eig.getRealEigenvalues(), eig.getV());
        return pca;
    }

    public static boolean reconstruct(FilteredImage[] series, Rectangle mask, PCA pca) {
        int n = series.length;
        int w = (mask == null) ? series[0].getImage().getWidth() : mask.width;
        int h = (mask == null) ? series[0].getImage().getHeight() : mask.height;
        int m = w * h;
        int x0 = (mask == null) ? 0 : mask.x;
        int y0 = (mask == null) ? 0 : mask.y;
        int x1 = x0 + w;
        int y1 = y0 + h;

        //TODO: check dimensions

        ImageProcessor ip;
        DoubleMatrix2D basis, pc, rec;
        double value, dev;

        // Create basis from selected eigenvectors
        basis = pca.eigenvectors.viewSelection(null, pca.getSelectedIndices());

        // Project normalised data onto the new basis and reconstruct data
        pc = basis.zMult(pca.data, null, 1, 0, true, false);
        rec = basis.zMult(pc, null, 1, 0, false, false);

        // Re-create images
        for (int i = 0; i < n; i++) {
            ip = series[i].getImage();
            dev = pca.datadevs.get(i);

            int j = 0;
            for (int x = x0; x < x1; x++) {
                for (int y = y0; y < y1; y++) {
                    value = rec.get(i, j) * dev + pca.datameans.get(j);
                    ip.setf(x, y, (float) value);
                    j++;
                }
            }
        }

        return true;
    }

    public static PCA[] batchDecompose(EftemDataset ds) throws InterruptedException, ExecutionException {
        int tiltCount = ds.getTiltCount();
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Future[] futures = new Future[tiltCount];
        PCA[] result = new PCA[tiltCount];

        for (int i = 0; i < tiltCount; i++) {
            futures[i] = pool.submit(new DecompositionTask(ds.getMappingImages(i), ds.getMask(i)));
        }

        pool.shutdown();

        // Wait for all tasks to finish
        for (int i = 0; i < tiltCount; i++) {
            result[i] = (PCA) futures[i].get();
        }

        return result;
    }

    public static boolean[] batchReconstruct(EftemDataset ds, PCA[] pca) throws InterruptedException, ExecutionException {
        int tiltCount = ds.getTiltCount();
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Future[] futures = new Future[tiltCount];
        boolean[] result = new boolean[tiltCount];

        for (int i = 0; i < tiltCount; i++) {
            futures[i] = pool.submit(new ReconstructionTask(ds.getMappingImages(i), ds.getMask(i), pca[i]));
        }

        pool.shutdown();

        // Wait for all tasks to finish
        for (int i = 0; i < tiltCount; i++) {
            result[i] = (Boolean) futures[i].get();
        }

        return result;
    }

    /**
     * Results of PCA decomposition, including user axes selection
     */
    public static class PCA {

        private final int n;
        private final int width;
        private final int height;

        private final DoubleMatrix2D data;
        private final DoubleMatrix1D datameans;
        private final DoubleMatrix1D datadevs;
        private final DoubleMatrix1D eigenvalues;
        private final DoubleMatrix2D eigenvectors;

        private final ImageStack eigenimages;
        private final double[] energies;
        private final double[] cumulativeEnergies;
        private final boolean[] selection;

        public PCA(DoubleMatrix2D data, int width, int height, int x0, int y0, int x1, int y1, DoubleMatrix1D datameans, DoubleMatrix1D datadevs, DoubleMatrix1D evals, DoubleMatrix2D evecs) {
            this.data = data;
            this.datameans = datameans;
            this.datadevs = datadevs;
            this.width = width;
            this.height = height;

            eigenvalues = evals;
            eigenvectors = evecs;

            n = (int) evals.size();

            // Calculate energies
            energies = new double[n];
            cumulativeEnergies = new double[n];

            double trace = eigenvalues.zSum();


            for (int i = 0; i < n; i++) {
                energies[i] = eigenvalues.get(i) / trace;

            }

            double cum = 0;
            for (int i = n - 1; i >= 0; i--) {
                cum += energies[i];
                cumulativeEnergies[i] = cum;
            }

            // Get principal components by projecting data on all eigenvectors and create eigenimages
            ImageProcessor ip;
            DoubleMatrix2D pc;

            eigenimages = new ImageStack(width, height);
            pc = eigenvectors.zMult(data, null, 1, 0, true, false);

            for (int i = 0; i < n; i++) {
                ip = new FloatProcessor(width, height);

                int j = 0;
                for (int x = x0; x < x1; x++) {
                    for (int y = y0; y < y1; y++) {
                        ip.setf(x, y, (float) pc.get(i, j));
                        j++;
                    }
                }

                eigenimages.addSlice(String.format("PC%1$d, %2$.2f%%", (i + 1), (100 * energies[i])), ip);
            }

            // Initialise selection
            selection = new boolean[n];
            Arrays.fill(selection, true);
        }

        public int size() {
            return n;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public double getEigenvalue(int i) {
            return eigenvalues.get(i);
        }

        public double[] getEigenvalues() {
            return eigenvalues.toArray();
        }

        public ImageProcessor getEigenimage(int i) {
            return eigenimages.getProcessor(i + 1);
        }

        public double getEnergy(int i) {
            return energies[i];
        }

        public double getCumulativeEnergy(int i) {
            return cumulativeEnergies[i];
        }

        public boolean isSelected(int i) {
            return selection[i];
        }

        public void setSelected(int i, boolean s) {
            if (i >= 0) {
                selection[i] = s;
            }
        }

        public int[] getSelectedIndices() {
            List<Integer> indices = new ArrayList<Integer>();
            for (int i = 0; i < n; i++) {
                if (selection[i]) {
                    indices.add(i);
                }
            }

            // Transform to array
            int[] indices2 = new int[indices.size()];
            for (int i = 0; i < indices.size(); i++) indices2[i] = indices.get(i);

            return indices2;
        }

        public void printData() {
            System.out.println("Eigenvalues");
            System.out.println(eigenvalues);

            System.out.println("Eigenvectors");
            System.out.println(eigenvectors);

            System.out.println("Selection");
            System.out.println(Arrays.toString(getSelectedIndices()));
        }
    }

    /**
     * Wrapper class for concurrent MSA decomposition
     */
    private static class DecompositionTask implements Callable<PCA> {

        private final FilteredImage[] series;
        private final Rectangle mask;

        private DecompositionTask(FilteredImage[] series, Rectangle mask) {
            this.series = series;
            this.mask = mask;
        }

        public PCA call() throws Exception {
            return MSANoiseFilter.decompose(series, mask);
        }
    }

    /**
     * Wrapper class for concurrent MSA reconstruction
     */
    private static class ReconstructionTask implements Callable<Boolean> {

        private final FilteredImage[] series;
        private final Rectangle mask;
        private final PCA pca;

        private ReconstructionTask(FilteredImage[] series, Rectangle mask, PCA pca) {
            this.series = series;
            this.mask = mask;
            this.pca = pca;
        }

        public Boolean call() throws Exception {
            return MSANoiseFilter.reconstruct(series, mask, pca);
        }
    }
}
