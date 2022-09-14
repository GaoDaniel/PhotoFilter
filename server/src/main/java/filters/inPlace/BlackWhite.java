package filters.inPlace;

import filters.Filter;

public class BlackWhite extends InPlaceFilter{

    /**
     * private threshold based on intensity
     */
    private final int threshold;

    public BlackWhite(int intensity){
        this.threshold = (int) (128 - (intensity * 1.27));
    }

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                int red = Filter.COLOR & (argb >> 16);
                int green = Filter.COLOR & (argb >> 8);
                int blue = Filter.COLOR & argb;
                bi.setRGB(i, j, ((argb & Filter.ALPHA_MASK) | ((red + green + blue) / 3 >= threshold ? 0xFFFFFF : 0x0)));
            }
        }
    }
}
