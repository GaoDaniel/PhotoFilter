import java.awt.image.*;
import java.util.*;

import java.util.concurrent.*;

// xlow, xhi, ylow, yhi are in terms of pixels
public class ParallelizeCopy extends RecursiveAction {
    final int SEQUENTIAL_CUTOFF = 10000;
    int xlow, xhi, ylow, yhi, intensity;
    String filter;
    int[] copy;
    BufferedImage image;

    public ParallelizeCopy(BufferedImage image, int[] copy, int xlow, int xhi, int ylow, int yhi, String filter, int intensity) {
        this.image = image;
        this.copy = copy;
        this.xlow = xlow;
        this.xhi = xhi;
        this.ylow = ylow;
        this.yhi = yhi;
        this.filter = filter;
        this.intensity = intensity;
    }

    @Override
    public void compute() {
        if ((xhi - xlow) * (yhi - ylow) <= SEQUENTIAL_CUTOFF) {
            switch (filter) {
                case "box":
                    int dimension = (int)(intensity / 10) + 1;
                    matrix(dimension, boxBuilder(dimension));
                    break;
                case "gauss":
                    dimension = (int)(intensity / 10) + 1;
                    matrix(dimension, gaussBuilder(dimension));
                    break;
                case "outline":
                    matrix(3, new double[][]{{-1, -1, -1}, {-1, 8, -1}, {-1, -1, -1}});
                    break;
                case "sharp":
                    double mult = intensity / 50.0 + 0.25;
                    matrix(3, new double[][]{{0, -mult, 0}, {-mult, 4 * mult + 1, -mult}, {0, -mult, 0}});
                    break;
                case "test1":
                    mult = intensity / 50.0 + 0.25;
                    matrix(3, new double[][]{{-mult / 2, -mult / 2, -mult / 2}, {-mult / 2, 4 * mult + 1, -mult / 2}, {-mult / 2, -mult / 2, -mult / 2}});
                    break;
                case "test2":
                    dimension = (int)(intensity / 10) + 1;
                    matrix(dimension, gaussBuilder(dimension));
                    break;
                case "test3":
                    matrix(3, new double[][]{{1/3.0, 0, 0}, {0, 1/3.0, 0}, {0, 0, 1/3.0}});
                    break;
                case "noise":
                    median();
                    break;
            }
        } else {
            ParallelizeCopy left, right;
            if ((xhi - xlow) > (yhi - ylow)) {
                left = new ParallelizeCopy(image, copy, xlow, (xhi + xlow) / 2, ylow, yhi, filter, intensity);
                right = new ParallelizeCopy(image, copy, (xhi + xlow) / 2, xhi, ylow, yhi, filter, intensity);
            } else {
                // left is smaller, right is bigger
                left = new ParallelizeCopy(image, copy, xlow, xhi, ylow, (yhi + ylow) / 2, filter, intensity);
                right = new ParallelizeCopy(image, copy, xlow, xhi, (yhi + ylow) / 2, yhi, filter, intensity);
            }
            left.fork();
            right.compute();
            left.join();
        }
    }

    // applies modified matrix multiplication on input image
    private void matrix(int dimension, double[][] m) {
        for (int j = ylow; j < yhi; j++) {
            for (int i = xlow; i < xhi; i++) {
                int[][][] mtx = getMatrix(dimension, i, j);
                double r = Math.min(0.0 + 0xFF, Math.max(0.0, mult(dimension, mtx[0], m)));
                double g = Math.min(0.0 + 0xFF, Math.max(0.0, mult(dimension, mtx[1], m)));
                double b = Math.min(0.0 + 0xFF, Math.max(0.0, mult(dimension, mtx[2], m)));

                // keep same alpha val, round rgb value
                int rgb = ((int) (Math.round(r) << 16) + ((int) Math.round(g) << 8) + (int) Math.round(b));
                copy[j * image.getWidth() + i] = (SparkServer.ALPHA_MASK & image.getRGB(i, j)) | rgb;
            }
        }
    }

    private double[][] boxBuilder(int dimension) {
        double[][] matrix = new double[dimension][dimension];
        for(double[] row : matrix){
            Arrays.fill(row, 1.0/(dimension * dimension));
        }
        return matrix;
    }

    private double[][] gaussBuilder(int dimension) {
        int[] temp = new int[dimension];
        temp[0] = 1;
        for(int i = 1; i < dimension; i++){
            temp[i] = (temp[i - 1] * (dimension - i)) / i;
        }
        double[][] matrix = new double[dimension][dimension];
        double mult = Math.pow(2, 2 * dimension - 2);
        for(int i = 0; i < dimension; i++){
            for(int j = 0; j < dimension; j++){
                matrix[i][j] = temp[i] * temp[j] / mult;
            }
        }
        return matrix;
    }

    private void median() {
        for (int j = ylow; j < yhi; j++) {
            for (int i = xlow; i < xhi; i++) {
                List<Integer> r = new ArrayList<>();
                List<Integer> g = new ArrayList<>();
                List<Integer> b = new ArrayList<>();
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        if (i + x != -1 && i + x != image.getWidth() && j + y != -1 && j + y != image.getHeight()) {
                            int rgb = image.getRGB(i + x, j) & SparkServer.RGB_MASK;
                            r.add((rgb >> 16) & SparkServer.COLOR);
                            g.add((rgb >> 8) & SparkServer.COLOR);
                            b.add((rgb & SparkServer.COLOR));
                        }
                    }
                }
                Collections.sort(r);
                Collections.sort(g);
                Collections.sort(b);
                int med = r.size() / 2;
                int rmed = r.get(med);
                int gmed = g.get(med);
                int bmed = b.get(med);
                if (r.size() % 2 == 0) {
                    rmed = (rmed + r.get(med - 1)) / 2;
                    gmed = (gmed + g.get(med - 1)) / 2;
                    bmed = (bmed + b.get(med - 1)) / 2;
                }
                // keep same alpha val, round rgb value
                int rgb = (rmed << 16) + (gmed << 8) + bmed;
                copy[j * image.getWidth() + i] = (SparkServer.ALPHA_MASK & image.getRGB(i, j)) | rgb;
            }
        }
    }

    // helper multiplication method
    // multiplies corresponding indices and adds them
    private double mult(int dimension, int[][] a, double[][] b) {
        double res = 0;
        for (int i = 0; i < dimension; i++) {
            for (int j = 0; j < dimension; j++) {
                res += a[i][j] * b[i][j];
            }
        }
        return res;
    }

    // helper getter method of neighbors
    // returns three 3x3 matrices of r g b values of pixels around i, j
    private int[][][] getMatrix(int dimension, int i, int j) {
        int[][][] res = new int[3][dimension][dimension];
        int low = -1 * dimension / 2;
        int high = (dimension - 1) / 2;
        for (int x = low; x <= high; x++) {
            for (int y = low; y <= high; y++) {
                int rgb;
                if ((i + x <= -1 || i + x >= image.getWidth()) && (j + y <= -1 || j + y >= image.getHeight())) {
                    rgb = image.getRGB(i, j) & SparkServer.RGB_MASK;
                } else if (i + x <= -1 || i + x >= image.getWidth()) {
                    rgb = image.getRGB(i, j + y) & SparkServer.RGB_MASK;
                } else if (j + y <= -1 || j + y >= image.getHeight()) {
                    rgb = image.getRGB(i + x, j) & SparkServer.RGB_MASK;
                } else {
                    rgb = image.getRGB(i + x, j + y) & SparkServer.RGB_MASK;
                }
                res[0][x - low][y - low] = (rgb >> 16) & SparkServer.COLOR;
                res[1][x - low][y - low] = (rgb >> 8) & SparkServer.COLOR;
                res[2][x - low][y - low] = rgb & SparkServer.COLOR;
            }
        }
        return res;
    }
}