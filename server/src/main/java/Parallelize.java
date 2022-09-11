import java.awt.image.*;
import java.util.*;

import java.util.concurrent.*;

// xlow, xhi, ylow, yhi are in terms of 16x16 blocks
public class Parallelize extends RecursiveAction {
    final int SEQUENTIAL_CUTOFF = 4;
    final int BLOCK_LENGTH = 16;
    final int ASCII_LENGTH = 8;
    int xlow, xhi, ylow, yhi, intensity;
    String filter;
    BufferedImage image;

    public Parallelize(BufferedImage image, int xlow, int xhi, int ylow, int yhi, String filter, int intensity) {
        this.image = image;
        this.xlow = xlow;
        this.xhi = xhi;
        this.ylow = ylow;
        this.yhi = yhi;
        this.filter = filter;
        this.intensity = intensity;
    }

    @Override
    public void compute() {
        if ((xhi - xlow) * (yhi - ylow) <= SEQUENTIAL_CUTOFF) {
            switch (filter) {
                case "invert":
                    invert();
                    break;
                case "gray":
                    grayscale();
                    break;
                case "bw":
                    blackWhite();
                    break;

                case "emoji":
                    emojify();
                    break;
                case "ascii":
                    asciify(false);
                    break;
                case "ansi":
                    asciify(true);
                    break;

                case "bright":
                    colorMod(intensity, 0xFFFFFF);
                    break;
                case "sat":
                    saturate(intensity);
                    break;

                case "red":
                    colorMod(intensity, 0xFF0000);
                    break;
                case "green":
                    colorMod(intensity, 0x00FF00);
                    break;
                case "blue":
                    colorMod(intensity, 0x0000FF);
                    break;
                case "cyan":
                    colorMod(intensity, 0x00FFFF);
                    break;
                case "magenta":
                    colorMod(intensity, 0xFF00FF);
                    break;
                case "yellow":
                    colorMod(intensity, 0xFFFF00);
                    break;
            }
        } else {
            Parallelize left, right;
            if ((xhi - xlow) > (yhi - ylow)) {
                left = new Parallelize(image, xlow, (xhi + xlow) / 2, ylow, yhi, filter, intensity);
                right = new Parallelize(image, (xhi + xlow) / 2, xhi, ylow, yhi, filter, intensity);
            } else {
                // left is smaller, right is bigger
                left = new Parallelize(image, xlow, xhi, ylow, (yhi + ylow) / 2, filter, intensity);
                right = new Parallelize(image, xlow, xhi, (yhi + ylow) / 2, yhi, filter, intensity);
            }
            left.fork();
            right.compute();
            left.join();
        }
    }

    // filters represented by private methods
    private void invert() {
        for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
            for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                int argb = image.getRGB(i, j);
                image.setRGB(i, j, (argb & SparkServer.ALPHA_MASK) + (SparkServer.RGB_MASK & ~argb));
            }
        }
    }

    private void grayscale() {
        for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
            for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                int argb = image.getRGB(i, j);
                int red = SparkServer.COLOR & (argb >> 16);
                int green = SparkServer.COLOR & (argb >> 8);
                int blue = SparkServer.COLOR & argb;
                int gray = (red + green + blue) / 3;
                image.setRGB(i, j, ((argb & SparkServer.ALPHA_MASK) | (gray << 16) | (gray << 8) | gray));
            }
        }
    }

    private void blackWhite() {
        grayscale();
        for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
            for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                int argb = image.getRGB(i, j);
                image.setRGB(i, j, ((argb & SparkServer.ALPHA_MASK) | ((argb & SparkServer.COLOR) >= 128 ? 0xFFFFFF : 0x0)));
            }
        }
    }

    // how far away color is from 0xF0F0F0 (gray)
    private void saturate(int intensity) {
        double mult = Math.pow(2, intensity / 100.0);
        for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
            for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                int argb = image.getRGB(i, j);
                int red = SparkServer.COLOR & (argb >> 16);
                int green = SparkServer.COLOR & (argb >> 8);
                int blue = SparkServer.COLOR & argb;
                red = Math.min(0xFF, Math.max(0, 128 + (int) ((red - 128) * mult)));
                green = Math.min(0xFF, Math.max(0, 128 + (int) ((green - 128) * mult)));
                blue = Math.min(0xFF, Math.max(0, 128 + (int) ((blue - 128) * mult)));
                image.setRGB(i, j, ((argb & SparkServer.ALPHA_MASK) | (red << 16) | (green << 8) | blue));
            }
        }
    }

    // color filters based on color; brightness when color = 0xFFFFFF
    private void colorMod(int intensity, int color) {
        double mult = Math.pow(2, intensity / 50.0);
        for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
            for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                int argb = image.getRGB(i, j);
                double rmod = ((SparkServer.COLOR & (color >> 16)) == 0) ? 1 : mult;
                double gmod = ((SparkServer.COLOR & (color >> 8)) == 0) ? 1 : mult;
                double bmod = ((SparkServer.COLOR & color) == 0) ? 1 : mult;
                int red = Math.min(0xFF, (int) ((SparkServer.COLOR & (argb >> 16)) * rmod));
                int green = Math.min(0xFF, (int) ((SparkServer.COLOR & (argb >> 8)) * gmod));
                int blue = Math.min(0xFF, (int) ((SparkServer.COLOR & argb) * bmod));
                image.setRGB(i, j, ((argb & SparkServer.ALPHA_MASK) | (red << 16) | (green << 8) | blue));
            }
        }
    }

    private void asciify(boolean ansi) {
        for (int x = 2 * xlow; x < 2 * xhi; x++) {
            for (int y = 2 * ylow; y < 2 * yhi; y++) {
                Map<BufferedImage, Integer> distance = new HashMap<>();
                for (BufferedImage image : SparkServer.asciis) {
                    distance.put(image, 0);
                }

                // get distances by comparing number of differing b/w pixel values for every ascii
                int[] colors = new int[3];
                for (int i = x * ASCII_LENGTH; i < (x + 1) * ASCII_LENGTH && i < image.getWidth(); i++) {
                    for (int j = y * ASCII_LENGTH; j < (y + 1) * ASCII_LENGTH && j < image.getHeight(); j++) {
                        int argb = image.getRGB(i, j);
                        int red = SparkServer.COLOR & (argb >> 16);
                        int green = SparkServer.COLOR & (argb >> 8);
                        int blue = SparkServer.COLOR & argb;
                        int newColor = (red + green + blue) / 3 >= 128 ? 0xFFFFFF : 0;

                        if (ansi) {
                            colors[0] += red;
                            colors[1] += green;
                            colors[2] += blue;
                        }
                        for (BufferedImage image : SparkServer.asciis) {
                            int color = image.getRGB(i % (ASCII_LENGTH), j % (ASCII_LENGTH));
                            if ((color & SparkServer.RGB_MASK) != newColor) {
                                distance.put(image, distance.get(image) + 1);
                            }
                        }
                    }
                }
                BufferedImage ascii = null;
                int blocks = ASCII_LENGTH * ASCII_LENGTH;
                int minDist = blocks + 1;
                for (BufferedImage image : distance.keySet()) {
                    if (distance.get(image) < minDist) {
                        minDist = distance.get(image);
                        ascii = image;
                    }
                }
                int ansiColor = (0xFF << 24) | ((colors[0] / blocks) << 16) | ((colors[1] / blocks) << 8) | (colors[2] / blocks);
                assert ascii != null;
                // convert 8x8 block into ascii
                for (int i = x * ASCII_LENGTH; i < (x + 1) * ASCII_LENGTH && i < image.getWidth(); i++) {
                    for (int j = y * ASCII_LENGTH; j < (y + 1) * ASCII_LENGTH && j < image.getHeight(); j++) {
                        int newColor = ascii.getRGB(i % (ASCII_LENGTH), j % (ASCII_LENGTH));
                        if (ansi && (newColor & SparkServer.RGB_MASK) == 0x0) {
                            newColor = ansiColor;
                        }
                        image.setRGB(i, j, newColor);
                    }
                }
            }
        }
    }

    private void emojify() {
        for (int x = xlow; x < xhi; x++) {
            for (int y = ylow; y < yhi; y++) {
                Map<BufferedImage, Double> distance = new HashMap<>();
                for (BufferedImage image : SparkServer.emojis) {
                    distance.put(image, 0.0);
                }

                // get distances by comparing pixel values for every emoji, add all up
                for (int i = x * BLOCK_LENGTH; i < (x + 1) * BLOCK_LENGTH && i < image.getWidth(); i++) {
                    for (int j = y * BLOCK_LENGTH; j < (y + 1) * BLOCK_LENGTH && j < image.getHeight(); j++) {
                        int argb = image.getRGB(i, j);
                        int red = SparkServer.COLOR & (argb >> 16);
                        int green = SparkServer.COLOR & (argb >> 8);
                        int blue = SparkServer.COLOR & argb;

                        for (BufferedImage image : SparkServer.emojis) {
                            int color = image.getRGB(i % BLOCK_LENGTH, j % BLOCK_LENGTH);
                            if ((color & SparkServer.ALPHA_MASK) != 0) {
                                int cr = SparkServer.COLOR & (color >> 16);
                                int cg = SparkServer.COLOR & (color >> 8);
                                int cb = SparkServer.COLOR & color;
                                double sumDist = (double) Math.abs(cr - red) + Math.abs(cg - green) + Math.abs(cb - blue);
                                distance.put(image, distance.get(image) + sumDist / SparkServer.numOpaque.get(image));
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
                for (int i = x * BLOCK_LENGTH; i < (x + 1) * BLOCK_LENGTH && i < image.getWidth(); i++) {
                    for (int j = y * BLOCK_LENGTH; j < (y + 1) * BLOCK_LENGTH && j < image.getHeight(); j++) {
                        int newColor = emoji.getRGB(i % BLOCK_LENGTH, j % BLOCK_LENGTH);
                        // change background to white if emoji background is transparent
                        if ((newColor & SparkServer.ALPHA_MASK) == 0) {
                            newColor = 0xFFFFFFFF;
                        }
                        image.setRGB(i, j, newColor);
                    }
                }
            }
        }
    }
}