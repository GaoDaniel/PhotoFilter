package filters.inPlace;

import filters.Filter;

public class ColorMod extends InPlaceFilter {

    /**
     * private multiplier based on intensity
     */
    private final double rmult;
    private final double gmult;
    private final double bmult;

    public ColorMod(int color, int intensity) {
        double mult = Math.pow(2, intensity / 100.0);

        this.rmult = ((Filter.COLOR & (color >> 16)) == 0) ? 1 : mult;
        this.gmult = ((Filter.COLOR & (color >> 8)) == 0) ? 1 : mult;
        this.bmult = ((Filter.COLOR & color) == 0) ? 1 : mult;
    }

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                int red = Math.min(0xFF, (int) ((Filter.COLOR & (argb >> 16)) * rmult));
                int green = Math.min(0xFF, (int) ((Filter.COLOR & (argb >> 8)) * gmult));
                int blue = Math.min(0xFF, (int) ((Filter.COLOR & argb) * bmult));
                bi.setRGB(i, j, ((argb & Filter.ALPHA_MASK) | (red << 16) | (green << 8) | blue));
            }
        }
    }
}
