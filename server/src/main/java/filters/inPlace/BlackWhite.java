package filters.inPlace;

import filters.Filter;

public class BlackWhite extends InPlaceFilter{

    public BlackWhite(){}

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                int red = Filter.COLOR & (argb >> 16);
                int green = Filter.COLOR & (argb >> 8);
                int blue = Filter.COLOR & argb;
                // TODO: could add intensity changing 128 (threshold of black vs white)
                bi.setRGB(i, j, ((argb & Filter.ALPHA_MASK) | ((red + green + blue) / 3 >= 128 ? 0xFFFFFF : 0x0)));
            }
        }
    }
}
