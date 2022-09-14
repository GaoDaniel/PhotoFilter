package filters.inPlace;

import filters.Filter;

public class Grayscale extends InPlaceFilter {

    public Grayscale(){}

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                int red = Filter.COLOR & (argb >> 16);
                int green = Filter.COLOR & (argb >> 8);
                int blue = Filter.COLOR & argb;
                int gray = (red + green + blue) / 3;
                bi.setRGB(i, j, ((argb & Filter.ALPHA_MASK) | (gray << 16) | (gray << 8) | gray));
            }
        }
    }
}
