package filters.inPlace;

import filters.Filter;
import filters.Parallelize;

import java.awt.image.BufferedImage;

public abstract class InPlaceFilter extends Filter {

    @Override
    public void applyFilter(BufferedImage bi, int intensity) {
        this.bi = bi;
        fjpool.invoke(new Parallelize(0, bi.getWidth(), 0, bi.getHeight(), this, intensity));
    }

}
