package filters.inPlace;

import filters.Filter;
import filters.Parallelize;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class Emojify extends Filter {

    /**
     * length of an emoji block
     */
    public static final int BLOCK_LENGTH = 16;

    /**
     * map of reference emojis to number of opaque pixels in the image
     */
    private final Map<BufferedImage, Integer> emojis;

    public Emojify(Map<BufferedImage, Integer> emojis){
        this.emojis = emojis;
    }

    @Override
    public void applyFilter(BufferedImage bi) {
        this.bi = bi;
        fjpool.invoke(new Parallelize(0, (bi.getWidth() + 15) / 16, 0, (bi.getHeight() + 15) / 16, this, 4));
    }

    public void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int x = xlow; x < xhi; x++) {
            for (int y = ylow; y < yhi; y++) {
                Map<BufferedImage, Double> distance = new HashMap<>();
                for (BufferedImage image : emojis.keySet()) {
                    distance.put(image, 0.0);
                }

                // get distances by comparing pixel values for every emoji, add all up
                for (int i = x * BLOCK_LENGTH; i < (x + 1) * BLOCK_LENGTH && i < bi.getWidth(); i++) {
                    for (int j = y * BLOCK_LENGTH; j < (y + 1) * BLOCK_LENGTH && j < bi.getHeight(); j++) {
                        int argb = bi.getRGB(i, j);
                        int red = COLOR & (argb >> 16);
                        int green = COLOR & (argb >> 8);
                        int blue = COLOR & argb;

                        for (BufferedImage image : emojis.keySet()) {
                            int color = image.getRGB(i % BLOCK_LENGTH, j % BLOCK_LENGTH);
                            if ((color & ALPHA_MASK) != 0) {
                                int cr = COLOR & (color >> 16);
                                int cg = COLOR & (color >> 8);
                                int cb = COLOR & color;
                                double sumDist = (double) Math.abs(cr - red) + Math.abs(cg - green) + Math.abs(cb - blue);
                                distance.put(image, distance.get(image) + sumDist / emojis.get(image));
                            }
                        }
                    }
                }
                BufferedImage emoji = null;
                double minDist = 0xFF * 3.0;
                for (BufferedImage image : distance.keySet()) {
                    if (distance.get(image) < minDist) {
                        minDist = distance.get(image);
                        emoji = image;
                    }
                }

                assert emoji != null;
                // convert 16x16 block into emoji
                for (int i = x * BLOCK_LENGTH; i < (x + 1) * BLOCK_LENGTH && i < bi.getWidth(); i++) {
                    for (int j = y * BLOCK_LENGTH; j < (y + 1) * BLOCK_LENGTH && j < bi.getHeight(); j++) {
                        int newColor = emoji.getRGB(i % BLOCK_LENGTH, j % BLOCK_LENGTH);

                        // change background to white if emoji background is transparent
                        if ((newColor & ALPHA_MASK) == 0) {
                            newColor = 0xFFFFFFFF;
                        }
                        bi.setRGB(i, j, newColor);
                    }
                }
            }
        }
    }
}
