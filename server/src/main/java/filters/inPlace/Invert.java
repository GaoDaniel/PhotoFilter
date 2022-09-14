package filters.inPlace;

import filters.Filter;

public class Invert extends InPlaceFilter{

    public Invert(){}

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                bi.setRGB(i, j, (argb & Filter.ALPHA_MASK) + (Filter.RGB_MASK & ~argb));
            }
        }
    }
}
