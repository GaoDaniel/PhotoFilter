package filters.copy;

import filters.Filter;
import filters.Parallelize;

import java.awt.image.BufferedImage;

public abstract class CopyFilter extends Filter {

    /**
     * based matrix to apply multiplications on
     */
    double[][] matrix;

    /**
     * copy to write filtered image to
     */
    int[] copy;

    public CopyFilter(double[][] matrix){
        this.matrix = matrix;
    }

    @Override
    public void applyFilter(BufferedImage bi, int intensity) {
        this.bi = bi;
        this.copy = new int[bi.getHeight() * bi.getWidth()];
        fjpool.invoke(new Parallelize(0, bi.getWidth(), 0, bi.getHeight(), this, intensity));
        bi.setRGB(0, 0, bi.getWidth(), bi.getHeight(), copy, 0, bi.getWidth());
    }

    @Override
    protected void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int j = ylow; j < yhi; j++) {
            for (int i = xlow; i < xhi; i++) {
                int[][][] mtx = getMatrix(matrix.length, i, j);
                double r = Math.min(0.0 + 0xFF, Math.max(0.0, mult(matrix.length, mtx[0], matrix)));
                double g = Math.min(0.0 + 0xFF, Math.max(0.0, mult(matrix.length, mtx[1], matrix)));
                double b = Math.min(0.0 + 0xFF, Math.max(0.0, mult(matrix.length, mtx[2], matrix)));

                // keep same alpha val, round rgb value
                int rgb = ((int) (Math.round(r) << 16) + ((int) Math.round(g) << 8) + (int) Math.round(b));
                copy[j * bi.getWidth() + i] = (ALPHA_MASK & bi.getRGB(i, j)) | rgb;
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
                if ((i + x <= -1 || i + x >= bi.getWidth()) && (j + y <= -1 || j + y >= bi.getHeight())) {
                    rgb = bi.getRGB(i, j) & RGB_MASK;
                } else if (i + x <= -1 || i + x >= bi.getWidth()) {
                    rgb = bi.getRGB(i, j + y) & RGB_MASK;
                } else if (j + y <= -1 || j + y >= bi.getHeight()) {
                    rgb = bi.getRGB(i + x, j) & RGB_MASK;
                } else {
                    rgb = bi.getRGB(i + x, j + y) & RGB_MASK;
                }
                res[0][x - low][y - low] = (rgb >> 16) & COLOR;
                res[1][x - low][y - low] = (rgb >> 8) & COLOR;
                res[2][x - low][y - low] = rgb & COLOR;
            }
        }
        return res;
    }

}
