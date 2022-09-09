import spark.Spark;
import utils.CORSFilter;
import com.google.gson.Gson;

import javax.imageio.ImageIO;

//import org.eclipse.jetty.util.thread.ThreadPool;

import java.awt.image.*;
import java.io.*;
import java.util.*;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SparkServer {
    public static final int ALPHA_MASK = 0xFF000000;
    public static final int RGB_MASK = 0xFFFFFF;
    public static final int COLOR = 0xFF;

    public static final Set<String> filters =
            Set.of("invert", "gray", "box", "gauss", "emoji", "ascii", "ansi", "outline", "sharp",
            "bright", "test1", "test2", "test3", "noise", "sat",
            "red", "green", "blue", "cyan", "yellow", "magenta", "bw", "dom");
    private static final Set<String> matrixFilters = Set.of("gauss", "box", "sharp", "outline",
            "test1", "test2", "test3", "noise");
    // intFilters: "box", "gauss", "sharp", "bright", "sat", "red", "green", "blue"
    private static final Set<BufferedImage> emojis = new HashSet<>();
    private static final Set<BufferedImage> asciis = new HashSet<>();
    private static final Map<BufferedImage, Integer> numOpaque = new HashMap<>();
    private static final ForkJoinPool fjpool = new ForkJoinPool();

    /*
     * Server
     * Format of URLs: http://localhost:4567/filtering?filter=invert
     */
    public static void main(String[] args) {
        // population of funny filter data
        setup();

        CORSFilter corsFilter = new CORSFilter();
        corsFilter.apply();

        // filter request
        Spark.post("/filtering", (request, response) -> {
            String base64 = request.body();
            String filter = request.queryParams("filter");
            int intensity = 0;
            try {
                intensity = Integer.parseInt(request.queryParams("int"));
            } catch (NumberFormatException e) {
                Spark.halt(402, "bad int format");
            }

            if (base64 == null || filter == null) {
                Spark.halt(400, "missing one of base64 or filter");
            }
            filter = filter.toLowerCase();
            if (!filters.contains(filter)) {
                Spark.halt(401, "filter does not exist");
            }
            base64 = base64.replace(' ', '+');

            // get bytes from base64
            byte[] imageData = new byte[0];
            try {
                imageData = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                Spark.halt(403, "invalid base64 scheme");
            }
            if (imageData.length == 0) {
                Spark.halt(404, "invalid base64 scheme");
            }

            // get BufferedImage
            BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (inputImage == null) {
                Spark.halt(405, "base64 could not be read");
            }

            // filter
            filter(inputImage, filter, intensity);

            // convert back to base64 uri
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(inputImage, "png", out);
            imageData = out.toByteArray();
            String base64bytes = Base64.getEncoder().encodeToString(imageData);

            // returns base64 representation
            Gson gson = new Gson();
            return gson.toJson(base64bytes);
        });
    }

    private static void setup(){
        populate("emojis", emojis);
        for (BufferedImage emoji : emojis) {
            int count = 0;
            for (int i = 0; i < emoji.getWidth(); i++) {
                for (int j = 0; j < emoji.getHeight(); j++) {
                    // count number of opaque pixels
                    if ((emoji.getRGB(i, j) & ALPHA_MASK) != 0) {
                        count++;
                    }
                }
            }
            numOpaque.put(emoji, count);
        }
        System.out.println(numOpaque.values());
        populate("ascii", asciis);
    }

    // adds BufferedImages of emojis to set
    private static void populate(String dir, Set<BufferedImage> converts) {
        try {
            String location = "src/main/resources/" + dir;
            File[] images = new File(location).listFiles();
            assert images != null;
            for (File image : images) {
                converts.add(ImageIO.read(image));
            }
        } catch (IOException e) {
            System.out.println("Input error: " + e);
        }
    }

    // does parallelized filter based on the filter type
    private static void filter(BufferedImage inputImage, String filter, int intensity) {
        if (filter.equals("dom")){
            int[] hues = new int[360];
            double[] sats = new double[360];
            double[] brights = new double[360];
            Lock[] locks = new Lock[360];
            for (int i = 0; i < 360; i++){
                locks[i] = new ReentrantLock();
            }
            // find dominant hue 0-359, ave S of the dominant, ave B of the dominant
            fjpool.invoke(new ParallelizeHue(inputImage, hues, sats, brights, locks, 0, inputImage.getWidth(), 0, inputImage.getHeight(), intensity, null));

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

            System.out.printf("%d, %f, %f\n", hue, sat, bright);

            // hue tolerance, if not in range, set to grayscale, or grayscale dominant on inverse
            fjpool.invoke(new ParallelizeHue(inputImage, null, null, null, null, 0, inputImage.getWidth(), 0, inputImage.getHeight(), intensity, new HSV(hue, sat, bright)));
        } else if (matrixFilters.contains(filter)) {
            int[] copy = new int[inputImage.getWidth() * inputImage.getHeight()];
            fjpool.invoke(new ParallelizeCopy(inputImage, copy, 0, inputImage.getWidth(), 0, inputImage.getHeight(), filter, intensity));
            inputImage.setRGB(0, 0, inputImage.getWidth(), inputImage.getHeight(), copy, 0, inputImage.getWidth());
        } else {
            fjpool.invoke(new Parallelize(inputImage, 0, (inputImage.getWidth() + 15) / 16, 0, (inputImage.getHeight() + 15) / 16, filter, intensity));
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

    private static class ParallelizeHue extends RecursiveAction {
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
                    HSV hsv = new HSV(RGB_MASK & image.getRGB(i, j));

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
            int tolerance = (360 - (int) (intensity * 3.59)) % 360;
            int lower = dom.h - tolerance;
            int upper = dom.h + tolerance;
            for (int i = xlow; i < xhi; i++) {
                for (int j = ylow; j < yhi; j++) {
                    int argb = image.getRGB(i, j);
                    HSV hsv = new HSV(RGB_MASK & argb);
                    if ((remove && lower <= hsv.h && hsv.h < upper) || (!remove && (lower > hsv.h || hsv.h >= upper))){
                        int red = COLOR & (argb >> 16);
                        int green = COLOR & (argb >> 8);
                        int blue = COLOR & argb;
                        int gray = (red + green + blue) / 3;
                        image.setRGB(i, j, ((argb & ALPHA_MASK) | (gray << 16) | (gray << 8) | gray));
                    }
                }
            }
        }
    }


    // xlow, xhi, ylow, yhi are in terms of 16x16 blocks
    private static class Parallelize extends RecursiveAction {
        final int SEQUENTIAL_CUTOFF = 4;
        final int BLOCK_LENGTH = 16;
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
                    image.setRGB(i, j, (argb & ALPHA_MASK) + (RGB_MASK & ~argb));
                }
            }
        }

        private void grayscale() {
            for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
                for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    int red = COLOR & (argb >> 16);
                    int green = COLOR & (argb >> 8);
                    int blue = COLOR & argb;
                    int gray = (red + green + blue) / 3;
                    image.setRGB(i, j, ((argb & ALPHA_MASK) | (gray << 16) | (gray << 8) | gray));
                }
            }
        }

        private void blackWhite() {
            grayscale();
            for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
                for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    image.setRGB(i, j, ((argb & ALPHA_MASK) | ((argb & COLOR) >= 128 ? 0xFFFFFF : 0x0)));
                }
            }
        }

        // how far away color is from 0xF0F0F0 (gray)
        private void saturate(int intensity) {
            double mult = Math.pow(2, intensity / 100.0);
            for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
                for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    int red = COLOR & (argb >> 16);
                    int green = COLOR & (argb >> 8);
                    int blue = COLOR & argb;
                    red = Math.min(0xFF, Math.max(0, 128 + (int) ((red - 128) * mult)));
                    green = Math.min(0xFF, Math.max(0, 128 + (int) ((green - 128) * mult)));
                    blue = Math.min(0xFF, Math.max(0, 128 + (int) ((blue - 128) * mult)));
                    image.setRGB(i, j, ((argb & ALPHA_MASK) | (red << 16) | (green << 8) | blue));
                }
            }
        }

        // color filters based on color; brightness when color = 0xFFFFFF
        private void colorMod(int intensity, int color) {
            double mult = Math.pow(2, intensity / 50.0);
            for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
                for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    double rmod = ((COLOR & (color >> 16)) == 0) ? 1 : mult;
                    double gmod = ((COLOR & (color >> 8)) == 0) ? 1 : mult;
                    double bmod = ((COLOR & color) == 0) ? 1 : mult;
                    int red = Math.min(0xFF, (int) ((COLOR & (argb >> 16)) * rmod));
                    int green = Math.min(0xFF, (int) ((COLOR & (argb >> 8)) * gmod));
                    int blue = Math.min(0xFF, (int) ((COLOR & argb) * bmod));
                    image.setRGB(i, j, ((argb & ALPHA_MASK) | (red << 16) | (green << 8) | blue));
                }
            }
        }

        private void asciify(boolean ansi) {
            for (int x = 2 * xlow; x < 2 * xhi; x++) {
                for (int y = 2 * ylow; y < 2 * yhi; y++) {
                    Map<BufferedImage, Integer> distance = new HashMap<>();
                    for (BufferedImage image : asciis) {
                        distance.put(image, 0);
                    }

                    // get distances by comparing number of differing b/w pixel values for every ascii
                    int[] colors = new int[3];
                    for (int i = x * BLOCK_LENGTH / 2; i < (x + 1) * BLOCK_LENGTH / 2 && i < image.getWidth(); i++) {
                        for (int j = y * BLOCK_LENGTH / 2; j < (y + 1) * BLOCK_LENGTH / 2 && j < image.getHeight(); j++) {
                            int argb = image.getRGB(i, j);
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
                                int color = image.getRGB(i % (BLOCK_LENGTH / 2), j % (BLOCK_LENGTH / 2));
                                if ((color & RGB_MASK) != newColor) {
                                    distance.put(image, distance.get(image) + 1);
                                }
                            }
                        }
                    }
                    BufferedImage ascii = null;
                    int blocks = BLOCK_LENGTH * BLOCK_LENGTH / 4;
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
                    for (int i = x * BLOCK_LENGTH / 2; i < (x + 1) * BLOCK_LENGTH / 2 && i < image.getWidth(); i++) {
                        for (int j = y * BLOCK_LENGTH / 2; j < (y + 1) * BLOCK_LENGTH / 2 && j < image.getHeight(); j++) {
                            int newColor = ascii.getRGB(i % (BLOCK_LENGTH / 2), j % (BLOCK_LENGTH / 2));
                            if (ansi && (newColor & RGB_MASK) == 0x0) {
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
                    for (BufferedImage image : emojis) {
                        distance.put(image, 0.0);
                    }

                    // get distances by comparing pixel values for every emoji, add all up
                    for (int i = x * BLOCK_LENGTH; i < (x + 1) * BLOCK_LENGTH && i < image.getWidth(); i++) {
                        for (int j = y * BLOCK_LENGTH; j < (y + 1) * BLOCK_LENGTH && j < image.getHeight(); j++) {
                            int argb = image.getRGB(i, j);
                            int red = COLOR & (argb >> 16);
                            int green = COLOR & (argb >> 8);
                            int blue = COLOR & argb;

                            for (BufferedImage image : emojis) {
                                int color = image.getRGB(i % BLOCK_LENGTH, j % BLOCK_LENGTH);
                                if ((color & ALPHA_MASK) != 0) {
                                    int cr = COLOR & (color >> 16);
                                    int cg = COLOR & (color >> 8);
                                    int cb = COLOR & color;
                                    double sumDist = (double) Math.abs(cr - red) + Math.abs(cg - green) + Math.abs(cb - blue);
                                    distance.put(image, distance.get(image) + sumDist / numOpaque.get(image));
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
                            if ((newColor & ALPHA_MASK) == 0) {
                                newColor = 0xFFFFFFFF;
                            }
                            image.setRGB(i, j, newColor);
                        }
                    }
                }
            }
        }
    }

    // xlow, xhi, ylow, yhi are in terms of pixels
    private static class ParallelizeCopy extends RecursiveAction {
        final int SEQUENTIAL_CUTOFF = 10000;
        int xlow, xhi, ylow, yhi, intensity;
        String filter;
        int[] copy;
        BufferedImage image;

        public ParallelizeCopy(BufferedImage image, int[] copy, int xlow, int xhi, int ylow, int yhi, String filter, int intensity) {
            this.image = image;
            this.copy = copy;
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
                    case "box":
                        int dimension = (int)(intensity / 10) + 1;
                        System.out.println("dimension: " + dimension);
                        matrix(dimension, boxBuilder(dimension));
                        break;
                    case "gauss":
                        matrix(3, new double[][]{{0.0625, 0.125, 0.0625}, {0.125, 0.25, 0.125}, {0.0625, 0.125, 0.0625}});
                        break;
                    case "outline":
                        matrix(3, new double[][]{{-1, -1, -1}, {-1, 8, -1}, {-1, -1, -1}});
                        break;
                    case "sharp":
                        double mult = intensity / 50.0 + 0.25;
                        matrix(3, new double[][]{{0, -mult, 0}, {-mult, 4 * mult + 1, -mult}, {0, -mult, 0}});
                        break;
                    case "test1":
                        mult = intensity / 50.0 + 0.25;
                        matrix(3, new double[][]{{-mult / 2, -mult / 2, -mult / 2}, {-mult / 2, 4 * mult + 1, -mult / 2}, {-mult / 2, -mult / 2, -mult / 2}});
                        break;
                    case "test2":
                        matrix(3, new double[][]{{225.0 / 1024, 15.0 / 512, 225.0 / 1024},
                                {15.0 / 512, 1.0 / 256, 15.0 / 512},
                                {225.0 / 1024, 15.0 / 512, 225.0 / 1024}});
                        break;
                    case "test3":
                        matrix(3, new double[][]{{0, -1, 0}, {-1, 5, -1}, {0, -1, 0}});
                        break;
                    case "noise":
                        median();
                        break;
                }
            } else {
                ParallelizeCopy left, right;
                if ((xhi - xlow) > (yhi - ylow)) {
                    left = new ParallelizeCopy(image, copy, xlow, (xhi + xlow) / 2, ylow, yhi, filter, intensity);
                    right = new ParallelizeCopy(image, copy, (xhi + xlow) / 2, xhi, ylow, yhi, filter, intensity);
                } else {
                    // left is smaller, right is bigger
                    left = new ParallelizeCopy(image, copy, xlow, xhi, ylow, (yhi + ylow) / 2, filter, intensity);
                    right = new ParallelizeCopy(image, copy, xlow, xhi, (yhi + ylow) / 2, yhi, filter, intensity);
                }
                left.fork();
                right.compute();
                left.join();
            }
        }

        // applies modified matrix multiplication on input image
        private void matrix(int dimension, double[][] m) {
            for (int j = ylow; j < yhi; j++) {
                for (int i = xlow; i < xhi; i++) {
                    int[][][] mtx = getMatrix(dimension, i, j);
                    double r = Math.min(0.0 + 0xFF, Math.max(0.0, mult(dimension, mtx[0], m)));
                    double g = Math.min(0.0 + 0xFF, Math.max(0.0, mult(dimension, mtx[1], m)));
                    double b = Math.min(0.0 + 0xFF, Math.max(0.0, mult(dimension, mtx[2], m)));

                    // keep same alpha val, round rgb value
                    int rgb = ((int) (Math.round(r) << 16) + ((int) Math.round(g) << 8) + (int) Math.round(b));
                    copy[j * image.getWidth() + i] = (ALPHA_MASK & image.getRGB(i, j)) | rgb;
                }
            }
        }

        private double[][] boxBuilder(int dimension) {
            double[][] matrix = new double[dimension][dimension];
            for(double[] row : matrix){
                Arrays.fill(row, 1.0/(dimension * dimension));
            }
            return matrix;
        }

        private void median() {
            for (int j = ylow; j < yhi; j++) {
                for (int i = xlow; i < xhi; i++) {
                    List<Integer> r = new ArrayList<>();
                    List<Integer> g = new ArrayList<>();
                    List<Integer> b = new ArrayList<>();
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            if (i + x != -1 && i + x != image.getWidth() && j + y != -1 && j + y != image.getHeight()) {
                                int rgb = image.getRGB(i + x, j) & RGB_MASK;
                                r.add((rgb >> 16) & COLOR);
                                g.add((rgb >> 8) & COLOR);
                                b.add((rgb & COLOR));
                            }
                        }
                    }
                    Collections.sort(r);
                    Collections.sort(g);
                    Collections.sort(b);
                    int med = r.size() / 2;
                    int rmed = r.get(med);
                    int gmed = g.get(med);
                    int bmed = b.get(med);
                    if (r.size() % 2 == 0) {
                        rmed = (rmed + r.get(med - 1)) / 2;
                        gmed = (gmed + g.get(med - 1)) / 2;
                        bmed = (bmed + b.get(med - 1)) / 2;
                    }
                    // keep same alpha val, round rgb value
                    int rgb = (rmed << 16) + (gmed << 8) + bmed;
                    copy[j * image.getWidth() + i] = (ALPHA_MASK & image.getRGB(i, j)) | rgb;
                }
            }
        }

        // helper multiplication method
        // multiplies corresponding indices and adds them
        private double mult(int dimension, int[][] a, double[][] b) {
            double res = 0;
            for (int i = 0; i < dimension; i++) {
                for (int j = 0; j < dimension; j++) {
                    res += a[i][j] * b[i][j];
                }
            }
            return res;
        }

        // helper getter method of neighbors
        // returns three 3x3 matrices of r g b values of pixels around i, j
        private int[][][] getMatrix(int dimension, int i, int j) {
            int[][][] res = new int[3][dimension][dimension];
            int low = -1 * dimension / 2;
            int high = (dimension - 1) / 2;
            for (int x = low; x <= high; x++) {
                for (int y = low; y <= high; y++) {
                    int rgb;
                    if ((i + x <= -1 || i + x >= image.getWidth()) && (j + y <= -1 || j + y >= image.getHeight())) {
                        rgb = image.getRGB(i, j) & RGB_MASK;
                    } else if (i + x <= -1 || i + x >= image.getWidth()) {
                        rgb = image.getRGB(i, j + y) & RGB_MASK;
                    } else if (j + y <= -1 || j + y >= image.getHeight()) {
                        rgb = image.getRGB(i + x, j) & RGB_MASK;
                    } else {
                        rgb = image.getRGB(i + x, j + y) & RGB_MASK;
                    }
                    res[0][x - low][y - low] = (rgb >> 16) & COLOR;
                    res[1][x - low][y - low] = (rgb >> 8) & COLOR;
                    res[2][x - low][y - low] = rgb & COLOR;
                }
            }
            return res;
        }
    }
}
