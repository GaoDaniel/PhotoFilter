package filters.copy;

import java.awt.image.BufferedImage;

public class Sharp extends CopyFilter {
    public Sharp(){
        super(null);
    }

    @Override
    public void applyFilter(BufferedImage bi, int intensity) {
        double mult = intensity / 50.0 + 0.25;
        super.matrix = new double[][]{{0, -mult, 0}, {-mult, 4 * mult + 1, -mult}, {0, -mult, 0}};
        super.applyFilter(bi, intensity);
    }
}
