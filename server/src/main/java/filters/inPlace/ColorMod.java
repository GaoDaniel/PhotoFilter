package filters.inPlace;

import filters.Filter;

public class ColorMod extends InPlaceFilter {

    /**
     * color of the filter
     */
    private final int color;

    public ColorMod(int color) {
        this.color = color;
    }

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                int red = Math.max(0, Math.min(0xFF, -128 + (Filter.COLOR & (argb >> 16)) + (Filter.COLOR & (color >> 16))));
                int green = Math.max(0, Math.min(0xFF, -128 + (Filter.COLOR & (argb >> 8)) + (Filter.COLOR & (color >> 8))));
                int blue = Math.max(0, Math.min(0xFF, -128 + (Filter.COLOR & (argb)) + (Filter.COLOR & (color))));
                bi.setRGB(i, j, ((argb & Filter.ALPHA_MASK) | (red << 16) | (green << 8) | blue));
            }
        }
    }
}
