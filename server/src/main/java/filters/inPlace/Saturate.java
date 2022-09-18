package filters.inPlace;

import filters.Filter;

import java.awt.image.BufferedImage;

public class Saturate extends InPlaceFilter{

    private final double mult;
    private final int color;

    public Saturate(int color, int intensity){
        this.mult = Math.pow(2, intensity / 100.0);
        this.color = color;
    }

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                int red = COLOR & (argb >> 16);
                int green = COLOR & (argb >> 8);
                int blue = COLOR & argb;
                int colorR = Filter.COLOR & (color >> 16);
                int colorG = Filter.COLOR & (color >> 8);
                int colorB = Filter.COLOR & color;
                red = Math.min(0xFF, Math.max(0, colorR + (int) ((red - colorR) * mult)));
                green = Math.min(0xFF, Math.max(0, colorG + (int) ((green - colorG) * mult)));
                blue = Math.min(0xFF, Math.max(0, colorB + (int) ((blue - colorB) * mult)));
                bi.setRGB(i, j, ((argb & ALPHA_MASK) | (red << 16) | (green << 8) | blue));
            }
        }
    }
}
