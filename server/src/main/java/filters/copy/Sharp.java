package filters.copy;

public class Sharp extends CopyFilter {
    public Sharp(int intensity){
        double mult = intensity / 50.0 + 0.25;
        this.matrix = new double[][]{{0, -mult, 0}, {-mult, 4 * mult + 1, -mult}, {0, -mult, 0}};
    }
}
