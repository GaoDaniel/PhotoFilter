package filters.inPlace;

import filters.Filter;

public class Brightness extends InPlaceFilter{

    /**
     * multiplier on the brightness
     */
    private final double mult;

    public Brightness(int intensity) {
        this.mult = Math.pow(2, intensity / 50.0);
    }

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                int red = Math.min(0xFF, (int) ((Filter.COLOR & (argb >> 16)) * mult));
                int green = Math.min(0xFF, (int) ((Filter.COLOR & (argb >> 8)) * mult));
                int blue = Math.min(0xFF, (int) ((Filter.COLOR & argb) * mult));

                bi.setRGB(i, j, ((argb & Filter.ALPHA_MASK) | (red << 16) | (green << 8) | blue));
            }
        }
    }
}