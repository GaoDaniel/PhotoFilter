package filters.inPlace;

import filters.Filter;

public class ColorMod extends InPlaceFilter{

    /**
     * the color to overlay on the image
     */
    private final int color;

    /**
     * private multiplier based on intensity
     */
    private final double mult;

    public ColorMod(int color, int intensity){
        this.color = color;
        this.mult = Math.pow(2, intensity / 50.0);
    }

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                double rmod = ((Filter.COLOR & (color >> 16)) == 0) ? 1 : mult;
                double gmod = ((Filter.COLOR & (color >> 8)) == 0) ? 1 : mult;
                double bmod = ((Filter.COLOR & color) == 0) ? 1 : mult;
                int red = Math.min(0xFF, (int) ((Filter.COLOR & (argb >> 16)) * rmod));
                int green = Math.min(0xFF, (int) ((Filter.COLOR & (argb >> 8)) * gmod));
                int blue = Math.min(0xFF, (int) ((Filter.COLOR & argb) * bmod));
                bi.setRGB(i, j, ((argb & Filter.ALPHA_MASK) | (red << 16) | (green << 8) | blue));
            }
        }
    }
}
