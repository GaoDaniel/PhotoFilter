package filters.copy;

public class GaussBlur extends CopyFilter {
    public GaussBlur(int intensity){
        this.matrix = gaussBuilder(intensity / 10 + 1);
    }

    // helper base matrix constructor
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
}
