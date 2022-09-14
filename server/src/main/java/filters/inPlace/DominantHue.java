package filters.inPlace;

import filters.Filter;
import filters.Parallelize;

import java.awt.image.BufferedImage;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DominantHue extends Filter {

    /**
     * arrays holding hsv counts of all pixels
     */
    int[] hues;
    double[] sats, brights;

    /**
     * locks for hsv counts
     */
    Lock[] locks;

    /**
     * the dominant Hue HSV
     */
    HSV dom;

    /**
     * true if removing inside dominant hue, false if removing outside of dominant hue
     * based on intensity
     */
    boolean remove;

    /**
     * tolerance range determining are to remove/not remove
     * based on intensity
     */
    int tolerance;

    public DominantHue(){
        hues = new int[360];
        sats = new double[360];
        brights = new double[360];
        locks = new Lock[360];
        for (int i = 0; i < 360; i++){
            locks[i] = new ReentrantLock();
        }
        dom = null;
    }

    @Override
    public void applyFilter(BufferedImage bi, int intensity) {
        super.bi = bi;
        // find dominant hue 0-359, ave S of the dominant, ave B of the dominant
        fjpool.invoke(new Parallelize(0, bi.getWidth(), 0, bi.getHeight(), this, intensity));

        int hueCount = hues[0];
        int hue = 0;
        for (int i = 1; i < hues.length; i++) {
            if (hues[i] > hueCount) {
                hueCount = hues[i];
                hue = i;
            }
        }
        double sat = sats[hue]/hueCount;
        double bright = brights[hue]/hueCount;
        
        dom = new HSV(hue, sat, bright);
        remove = intensity < 0;
        tolerance = (179 - (int) (intensity * 1.80)) % 180;
        if(intensity > 0) tolerance++;
        
        // hue tolerance, if not in range, set to grayscale, or grayscale dominant on inverse
        fjpool.invoke(new Parallelize(0, bi.getWidth(), 0, bi.getHeight(), this, intensity));
    }

    @Override
    protected void filter(int xlow, int xhi, int ylow, int yhi) {
        if (dom == null){
            for (int i = xlow; i < xhi; i++) {
                for (int j = ylow; j < yhi; j++) {
                    HSV hsv = new HSV(RGB_MASK & bi.getRGB(i, j));

                    locks[hsv.h].lock();
                    hues[hsv.h]++;
                    sats[hsv.h] += hsv.s;
                    brights[hsv.h] += hsv.v;
                    locks[hsv.h].unlock();
                }
            }
        } else {
            int lower = (dom.h - tolerance + 360) % 360;
            int upper = (dom.h + tolerance) % 360;
            
            for (int i = xlow; i < xhi; i++) {
                for (int j = ylow; j < yhi; j++) {
                    int argb = bi.getRGB(i, j);
                    HSV hsv = new HSV(RGB_MASK & argb);
                    if ((remove && ((lower <= upper && lower <= hsv.h && hsv.h < upper) || (upper < lower && !(upper <= hsv.h && hsv.h < lower))))
                            || (!remove && ((lower <= upper && !(lower <= hsv.h && hsv.h < upper)) || (upper < lower && upper <= hsv.h && hsv.h < lower)))){
                        int red = COLOR & (argb >> 16);
                        int green = COLOR & (argb >> 8);
                        int blue = COLOR & argb;
                        bi.setRGB(i, j, ((argb & ALPHA_MASK) | ((red + green + blue) / 3 * 0x010101)));
                    }
                }
            }
        }
    }

    private static class HSV{
        int r, g, b;
        int h;
        double s, v;

        private final int max, min;

        public HSV(int rgb){
            r = COLOR & (rgb >> 16);
            g = COLOR & (rgb >> 8);
            b = COLOR & rgb;

            max = Math.max(r, Math.max(g, b));
            min = Math.min(r, Math.min(g, b));

            v = max/255.0;
            s = max > 0 ? 1 - min/(0.0 + max) : 0;
            h = (int) Math.round(180.0/Math.PI * Math.acos((r - g/2.0 - b/2.0)/(Math.sqrt(r*r + g*g + b*b - r*g - r*b - g*b))));
            if (b > g){
                h = 360 - h;
            }
            h %= 360;
        }

        public HSV(int h, double s, double v) {
            this.h = h;
            this.s = s;
            this.v = v;

            max = (int) (255 * v);
            min = (int) (max * (1 - s));

            int z = (int) ((max - min) * (1.0 - Math.abs((h / 60.0) % 2 - 1)));
            if (h < 60) {
                r = max;
                g = z + min;
                b = min;
            } else if (h < 120) {
                r = z + min;
                g = max;
                b = min;
            } else if (h < 180) {
                r = min;
                g = max;
                b = z + min;
            } else if (h < 240) {
                r = min;
                g = z + min;
                b = max;
            } else if (h < 300) {
                r = z + min;
                g = min;
                b = max;
            } else {
                r = max;
                g = min;
                b = z + min;
            }
        }
    }

}
