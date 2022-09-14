package filters.inPlace;

import filters.Filter;
import filters.Parallelize;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Asciify extends Filter {

    /**
     * length of an ascii block
     */
    public static final int LENGTH = 8;

    /**
     * set of reference asciis
     */
    private final Set<BufferedImage> asciis;

    /**
     * true for color, false for black+white
     */
    private final boolean ansi;

    public Asciify(Set<BufferedImage> asciis, boolean ansi){
        this.asciis = asciis;
        this.ansi = ansi;
    }

    @Override
    public void applyFilter(BufferedImage bi) {
        this.bi = bi;
        fjpool.invoke(new Parallelize(0, (bi.getWidth() + 7) / 8, 0, (bi.getHeight() + 7) / 8, this, 16));
    }

    @Override
    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int x = xlow; x < xhi; x++) {
            for (int y = ylow; y < yhi; y++) {
                Map<BufferedImage, Integer> distance = new HashMap<>();
                for (BufferedImage image : asciis) {
                    distance.put(image, 0);
                }

                // get distances by comparing number of differing b/w pixel values for every ascii
                int[] colors = new int[3];
                for (int i = x * LENGTH; i < (x + 1) * LENGTH && i < bi.getWidth(); i++) {
                    for (int j = y * LENGTH; j < (y + 1) * LENGTH && j < bi.getHeight(); j++) {
                        int argb = bi.getRGB(i, j);
                        int red = COLOR & (argb >> 16);
                        int green = COLOR & (argb >> 8);
                        int blue = COLOR & argb;
                        int newColor = (red + green + blue) / 3 >= 128 ? 0xFFFFFF : 0;

                        if (ansi) {
                            colors[0] += red;
                            colors[1] += green;
                            colors[2] += blue;
                        }
                        for (BufferedImage image : asciis) {
                            int color = image.getRGB(i % (LENGTH), j % (LENGTH));
                            if ((color & RGB_MASK) != newColor) {
                                distance.put(image, distance.get(image) + 1);
                            }
                        }
                    }
                }
                BufferedImage ascii = null;
                int blocks = LENGTH * LENGTH;
                int minDist = blocks + 1;
                for (BufferedImage image : distance.keySet()) {
                    if (distance.get(image) < minDist) {
                        minDist = distance.get(image);
                        ascii = image;
                    }
                }

                assert ascii != null;
                // convert 8x8 block into ascii
                int ansiColor = (0xFF << 24) | ((colors[0] / blocks) << 16) | ((colors[1] / blocks) << 8) | (colors[2] / blocks);
                for (int i = x * LENGTH; i < (x + 1) * LENGTH && i < bi.getWidth(); i++) {
                    for (int j = y * LENGTH; j < (y + 1) * LENGTH && j < bi.getHeight(); j++) {
                        int newColor = ascii.getRGB(i % (LENGTH), j % (LENGTH));
                        if (ansi && (newColor & RGB_MASK) == 0x0) {
                            newColor = ansiColor;
                        }
                        bi.setRGB(i, j, newColor);
                    }
                }
            }
        }
    }
}
