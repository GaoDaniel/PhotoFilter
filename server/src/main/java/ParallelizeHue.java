import java.awt.image.*;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

public class ParallelizeHue extends RecursiveAction {
    final int SEQUENTIAL_CUTOFF = 1024;
    int xlow, xhi, ylow, yhi, intensity;
    BufferedImage image;

    int[] hues;
    double[] sats, brights;
    Lock[] locks;
    HSV dom;

    public ParallelizeHue(BufferedImage image, int[] hues, double[] sats, double[] brights, Lock[] locks, int xlow, int xhi, int ylow, int yhi, int intensity, HSV dom) {
        this.image = image;
        this.xlow = xlow;
        this.xhi = xhi;
        this.ylow = ylow;
        this.yhi = yhi;
        this.intensity = intensity;
        this.hues = hues;
        this.sats = sats;
        this.brights = brights;
        this.locks = locks;
        this.dom = dom;
    }

    @Override
    public void compute() {
        if ((xhi - xlow) * (yhi - ylow) <= SEQUENTIAL_CUTOFF) {
            if (dom == null){
                dominantHue();
            } else {
                processHue();
            }
        } else {
            ParallelizeHue left, right;
            if ((xhi - xlow) > (yhi - ylow)) {
                left = new ParallelizeHue(image, hues, sats, brights, locks, xlow, (xhi + xlow) / 2, ylow, yhi, intensity, dom);
                right = new ParallelizeHue(image, hues, sats, brights, locks, (xhi + xlow) / 2, xhi, ylow, yhi, intensity, dom);
            } else {
                // left is smaller, right is bigger
                left = new ParallelizeHue(image, hues, sats, brights, locks, xlow, xhi, ylow, (yhi + ylow) / 2, intensity, dom);
                right = new ParallelizeHue(image, hues, sats, brights, locks, xlow, xhi, (yhi + ylow) / 2, yhi, intensity, dom);
            }
            left.fork();
            right.compute();
            left.join();
        }

    }

    private void dominantHue(){
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                HSV hsv = new HSV(SparkServer.RGB_MASK & image.getRGB(i, j));

                locks[hsv.h].lock();
                hues[hsv.h]++;
                sats[hsv.h] += hsv.s;
                brights[hsv.h] += hsv.v;
                locks[hsv.h].unlock();
            }
        }
    }

    private void processHue(){
        boolean remove = intensity < 0;
        int tolerance = (179 - (int) (intensity * 1.80)) % 180;
        if(intensity > 0) tolerance++;
        int lower = (dom.h - tolerance + 360) % 360;
        int upper = (dom.h + tolerance) % 360;
        for (int i = xlow; i < xhi; i++) {
            for (int j = ylow; j < yhi; j++) {
                int argb = image.getRGB(i, j);
                HSV hsv = new HSV(SparkServer.RGB_MASK & argb);
                if ((remove && ((lower <= upper && lower <= hsv.h && hsv.h < upper) || (upper < lower && !(upper <= hsv.h && hsv.h < lower)))) 
                || (!remove && ((lower <= upper && !(lower <= hsv.h && hsv.h < upper)) || (upper < lower && upper <= hsv.h && hsv.h < lower)))){
                    int red = SparkServer.COLOR & (argb >> 16);
                    int green = SparkServer.COLOR & (argb >> 8);
                    int blue = SparkServer.COLOR & argb;
                    int gray = (red + green + blue) / 3;
                    image.setRGB(i, j, ((argb & SparkServer.ALPHA_MASK) | (gray << 16) | (gray << 8) | gray));
                }
            }
        }
    }
}