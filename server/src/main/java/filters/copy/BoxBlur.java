package filters.copy;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class BoxBlur extends CopyFilter {
    public BoxBlur(){
        super(null);
    }

    @Override
    public void applyFilter(BufferedImage bi, int intensity) {
        this.matrix = boxBuilder(intensity / 10 + 1);
        super.applyFilter(bi, intensity);
    }

    // helper base matrix constructor
    private double[][] boxBuilder(int dimension) {
        double[][] matrix = new double[dimension][dimension];
        for(double[] row : matrix){
            Arrays.fill(row, 1.0/(dimension * dimension));
        }
        return matrix;
    }
}
