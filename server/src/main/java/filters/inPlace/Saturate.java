package filters.inPlace;

import java.awt.image.BufferedImage;

public class Saturate extends InPlaceFilter{

    private double mult;

    public Saturate(int intensity){
        this.mult = Math.pow(2, intensity / 100.0);
    }

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = bi.getRGB(i, j);
                int red = COLOR & (argb >> 16);
                int green = COLOR & (argb >> 8);
                int blue = COLOR & argb;
                red = Math.min(0xFF, Math.max(0, 128 + (int) ((red - 128) * mult)));
                green = Math.min(0xFF, Math.max(0, 128 + (int) ((green - 128) * mult)));
                blue = Math.min(0xFF, Math.max(0, 128 + (int) ((blue - 128) * mult)));
                bi.setRGB(i, j, ((argb & ALPHA_MASK) | (red << 16) | (green << 8) | blue));
            }
        }
    }
}
