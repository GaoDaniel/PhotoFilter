import spark.Spark;
import utils.CORSFilter;
import com.google.gson.Gson;

import javax.imageio.ImageIO;

//import org.eclipse.jetty.util.thread.ThreadPool;

import java.awt.image.*;
import java.io.*;
import java.util.*;

import java.util.concurrent.*;

public class SparkServer {
    public static final Set<String> filters = Set.of("invert", "gray", "box", 
                                                    "gauss", "emoji", "outline", 
                                                    "sharp", "bright", "dim");
    private static final Set<String> matrixFilters = Set.of("gauss", "box", "sharp", "outline");
    private static final Map<Integer, BufferedImage> emojis = new HashMap<>();
    private static final ForkJoinPool fjpool = new ForkJoinPool();

    /*
     * Server
     * Format of URLs: http://localhost:4567/filtering?filter=invert
     */
    public static void main(String[] args) {
        populateEmojis();

        CORSFilter corsFilter = new CORSFilter();
        corsFilter.apply();

        // filter request
        Spark.post("/filtering", (request, response) -> {
            String base64 = request.body();
            String filter = request.queryParams("filter");

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
            filter(inputImage, filter);

            // convert back to base64 uri
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(inputImage, "png", out);
            imageData = out.toByteArray();

            String base64bytes = Base64.getEncoder().encodeToString(imageData);
            Gson gson = new Gson();
            // now just returns base64 representation
            return gson.toJson(base64bytes);
        });
    }

    private static void populateEmojis() {
        try {
            String location = "src/main/resources/";
            emojis.put(0x6f6a6d, ImageIO.read(new File(location + "white.png")));
            emojis.put(0x93752a, ImageIO.read(new File(location + "yellow.png")));
            emojis.put(0x323332, ImageIO.read(new File(location + "light_gray.png")));
            emojis.put(0x3d635d, ImageIO.read(new File(location + "sky_blue.png")));
            emojis.put(0x3d533f, ImageIO.read(new File(location + "yellow_green.png")));
            emojis.put(0x383c48, ImageIO.read(new File(location + "gray.png")));
            emojis.put(0xd1ac5b, ImageIO.read(new File(location + "dark_yellow.png")));
            emojis.put(0xb0738d, ImageIO.read(new File(location + "dark_pink.png")));
            emojis.put(0x3b5773, ImageIO.read(new File(location + "blue_green.png")));
            emojis.put(0xa02638, ImageIO.read(new File(location + "red.png")));
            emojis.put(0x5f6f12, ImageIO.read(new File(location + "green.png")));
            emojis.put(0x632c6f, ImageIO.read(new File(location + "purple.png")));
            emojis.put(0x3c2a1f, ImageIO.read(new File(location + "brown.png")));
            emojis.put(0x3584d8, ImageIO.read(new File(location + "blue.png")));
            emojis.put(0x212c47, ImageIO.read(new File(location + "dark_blue.png")));
            emojis.put(0x393a37, ImageIO.read(new File(location + "black.png")));
        } catch (IOException e) {
            System.out.println("Input error: " + e);
        }
    }

    private static void filter(BufferedImage inputImage, String filter) {
        if (matrixFilters.contains(filter)) {
            int[] copy = new int[inputImage.getWidth() * inputImage.getHeight()];
            fjpool.invoke(new ParallelizeCopy(inputImage, copy,
                    0, inputImage.getWidth(), 0, inputImage.getHeight(), filter));
            inputImage.setRGB(0, 0, inputImage.getWidth(), inputImage.getHeight(), copy,
                    0, inputImage.getWidth());
        } else {
            fjpool.invoke(new Parallelize(inputImage, 0, (inputImage.getWidth() + 15)/16,
                    0, (inputImage.getHeight() + 15)/16, filter));
        }
    }

    // xlow, xhi, ylow, yhi are in terms of 16x16 blocks
    private static class Parallelize extends RecursiveAction {
        // final int SEQUENTIAL_CUTOFF = 10000;
        final int SEQUENTIAL_CUTOFF = 4;
        int xlow, xhi, ylow, yhi;
        String filter;
        BufferedImage image;

        public Parallelize(BufferedImage image, int xlow, int xhi, int ylow, int yhi, String filter) {
            this.image = image;
            this.xlow = xlow;
            this.xhi = xhi;
            this.ylow = ylow;
            this.yhi = yhi;
            this.filter = filter;
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
                    case "emoji":
                        emojify();
                        break;
                    case "bright":
                        bright();
                        break;
                    case "dim":
                        dim();
                        break;
                }
            } else {
                Parallelize left, right;
                if ((xhi - xlow) > (yhi - ylow)) {
                    left = new Parallelize(image, xlow, (xhi + xlow) / 2, ylow, yhi, filter);
                    right = new Parallelize(image, (xhi + xlow) / 2, xhi, ylow, yhi, filter);
                } else {
                    // left is smaller, right is bigger
                    left = new Parallelize(image, xlow, xhi, ylow, (yhi + ylow) / 2, filter);
                    right = new Parallelize(image, xlow, xhi, (yhi + ylow) / 2, yhi, filter);
                }
                left.fork();
                right.compute();
                left.join();
            }
        }

        // filters represented by private methods
        private void invert() {
            for (int i = xlow * 16; i < xhi * 16 && i < image.getWidth(); i++) {
                for (int j = ylow * 16; j < yhi * 16 && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    image.setRGB(i, j, (argb & 0xFF000000) + (0xFFFFFF & ~argb));
                }
            }
        }

        private void grayscale() {
            for (int i = xlow * 16; i < xhi * 16 && i < image.getWidth(); i++) {
                for (int j = ylow * 16; j < yhi * 16 && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    int alpha = 0xFF & (argb >> 24);
                    int red = 0xFF & (argb >> 16);
                    int green = 0xFF & (argb >> 8);
                    int blue = 0xFF & argb;
                    int gray = (red + green + blue) / 3;
                    image.setRGB(i, j, ((alpha << 24) | (gray << 16) | (gray << 8) | gray));
                }
            }
        }

        private void bright() {
            for (int i = xlow * 16; i < xhi * 16 && i < image.getWidth(); i++) {
                for (int j = ylow * 16; j < yhi * 16 && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    int alpha = 0xFF & (argb >> 24);
                    int red = Math.min(255, (int)((0xFF & (argb >> 16)) * 1.5));
                    int green = Math.min(255, (int)((0xFF & (argb >> 8)) * 1.5));
                    int blue = Math.min(255, (int)((0xFF & argb) * 1.5));
                    image.setRGB(i, j, ((alpha << 24) | (red << 16) | (green << 8) | blue));
                }
            }
        }

        private void dim() {
            for (int i = xlow * 16; i < xhi * 16 && i < image.getWidth(); i++) {
                for (int j = ylow * 16; j < yhi * 16 && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    int alpha = 0xFF & (argb >> 24);
                    int red = 0xFF & (argb >> 16);
                    int green = 0xFF & (argb >> 8);
                    int blue = 0xFF & argb;
                    image.setRGB(i, j, ((alpha << 24) | (red / 2 << 16) | (green / 2 << 8) | blue / 2));
                }
            }
        }

        private void emojify() {
            for (int x = xlow; x < xhi; x++){
                for (int y = ylow; y < yhi; y++){
                    // int with highest count = most popular color
                    Map<Integer, Integer> counts = new HashMap<>();
                    int maxCount = 0;
                    int emojiColor = 0;
                    for (int i = x * 16; i < x * 16 + 15 && i < image.getWidth(); i++){
                        for (int j = y * 16; j < y * 16 + 15 && j < image.getHeight(); j++){
                            int argb = image.getRGB(i, j);
                            int red = 0xFF & (argb >> 16);
                            int green = 0xFF & (argb >> 8);
                            int blue = 0xFF & argb;
                            int smallestDiff = 255 * 3;

                            int pixColor = -1;
                            for (int color : emojis.keySet()) {
                                int cr = 0xFF & (color >> 16);
                                int cg = 0xFF & (color >> 8);
                                int cb = 0xFF & color;
                                int tempVal = Math.abs(cr - red) + Math.abs(cg - green) + Math.abs(cb - blue);
                                if (tempVal < smallestDiff) {
                                    smallestDiff = tempVal;
                                    pixColor = color;
                                }
                            }
                            int newVal = counts.containsKey(pixColor) ? counts.get(pixColor) + 1 : 1;
                            counts.put(pixColor, newVal);
                            if (newVal > maxCount) {
                                emojiColor = pixColor;
                                maxCount = newVal;
                            }
                        }
                    }
                    // convert 16x16 block into emoji
                    BufferedImage emoji = emojis.get(emojiColor);
                    for (int i = x * 16; i < x * 16 + 15 && i < image.getWidth(); i++){
                        for (int j = y * 16; j < y * 16 + 15 && j < image.getHeight(); j++){
                            image.setRGB(i, j, emoji.getRGB(i % 16, j % 16));
                        }
                    }
                }
            }
        }
    }

    // xlow, xhi, ylow, yhi are in terms of pixels
    private static class ParallelizeCopy extends RecursiveAction {
        public static final int ALPHA_MASK = 0xFF000000;
        public static final int RGB_MASK = 0xFFFFFF;
        final int SEQUENTIAL_CUTOFF = 10000;
        int xlow, xhi, ylow, yhi;
        String filter;
        int[] copy;
        BufferedImage image;

        public ParallelizeCopy(BufferedImage image, int[] copy, int xlow, int xhi, int ylow, int yhi, String filter) {
            this.image = image;
            this.copy = copy;
            this.xlow = xlow;
            this.xhi = xhi;
            this.ylow = ylow;
            this.yhi = yhi;
            this.filter = filter;
        }

        @Override
        public void compute() {
            if ((xhi - xlow) * (yhi - ylow) <= SEQUENTIAL_CUTOFF) {
                switch (filter) {
                    case "box":
                        matrix(new double[][]{{1.0/9, 1.0/9, 1.0/9}, {1.0/9, 1.0/9, 1.0/9}, {1.0/9, 1.0/9, 1.0/9}});
                        break;
                    case "gauss":
                        matrix(new double[][]{{0.0625, 0.125, 0.0625}, {0.125, 0.25, 0.125}, {0.0625, 0.125, 0.0625}});
                        break;
                    case "outline":
                        matrix(new double[][]{{-1.0, -1.0, -1.0}, {-1.0, 8.0, -1.0}, {-1.0, -1.0, -1.0}});
                        break;
                    case "sharp":
//                        matrix(new double[][]{{-1.0/9, -1.0/9, -1.0/9}, {-1.0/9, 17.0/9, -1.0/9}, {-1.0/9, -1.0/9, -1.0/9}});
                        matrix(new double[][]{{0, -0.75, 0}, {-0.75, 4, -0.75}, {0, -0.75, 0}});
                        break;

                }
            } else {
                ParallelizeCopy left, right;
                if ((xhi - xlow) > (yhi - ylow)) {
                    left = new ParallelizeCopy(image, copy, xlow, (xhi + xlow) / 2, ylow, yhi, filter);
                    right = new ParallelizeCopy(image, copy, (xhi + xlow) / 2, xhi, ylow, yhi, filter);
                } else {
                    // left is smaller, right is bigger
                    left = new ParallelizeCopy(image, copy, xlow, xhi, ylow, (yhi + ylow) / 2, filter);
                    right = new ParallelizeCopy(image, copy, xlow, xhi, (yhi + ylow) / 2, yhi, filter);
                }
                left.fork();
                right.compute();
                left.join();
            }
        }

        // applies modified matrix multiplication on input image
        private void matrix(double[][] m){
            for (int j = ylow; j < yhi; j++) {
                for (int i = xlow; i < xhi; i++) {
                    int[][][] mtx = getMatrix(i, j);
                    double r = Math.min(255.0, Math.max(0.0, mult(mtx[0], m)));
                    double g = Math.min(255.0, Math.max(0.0, mult(mtx[1], m)));
                    double b = Math.min(255.0, Math.max(0.0, mult(mtx[2], m)));


                    // keep same a val, round rgb value
                    int rgb = ((int) (Math.round(r) << 16) + ((int) Math.round(g) << 8) + (int) Math.round(b));
                    copy[j * image.getWidth() + i] = (ALPHA_MASK & image.getRGB(i, j)) + rgb;
                }
            }
        }

//        private void edgeDetection(){
//
//            // assume everything is in grayscale
//            for (int j = ylow; j < yhi; j++) {
//                for (int i = xlow; i < xhi; i++) {
//                    double[] K_x = new double[3];
//                    double[] K_y = new double[3];
//                    for (int x = -1; x <= 1; x++) {
//                        for (int y = -1; y <= 1; y++) {
//                            int val = getVal(i, j, x, y);
//                            // Sobel Operator
//                            /*
//                            K_x = [-1 0 1] [-2 0 2] [-1 0 1]
//                            K_y = [-1 -2 -1] [0 0 0] [1 2 1]
//                            px_val = sqrt((mag_x)^2 + (mag_y)^2)
//                             */
//                            double baseX = x - 1;
//                            if (y == 0){
//                                baseX *= 2;
//                            }
//                            double baseY = y - 1;
//                            if (x == 0) {
//                                baseY *= 2;
//                            }
//
//                            K_x[0] += (0xFF & (val >> 16)) * baseX;
//                            K_y[0] += (0xFF & (val >> 16)) * baseY;
//                            K_x[1] += (0XFF & (val >> 8)) * baseX;
//                            K_y[1] += (0xFF & (val >> 8)) * baseY;
//                            K_x[2] += (0xFF & val) * baseX;
//                            K_y[2] += (0xFF & val) * baseY;
//                        }
//                    }
//                    // keep same a val, round rgb value
//                    double[] sqrtVals = new double[3];
//                    sqrtVals[0] = Math.sqrt(K_x[0] * K_x[0] + K_y[0] + K_y[0]);
//                    sqrtVals[1] = Math.sqrt(K_x[1] * K_x[1] + K_y[1] + K_y[1]);
//                    sqrtVals[2] = Math.sqrt(K_x[2] * K_x[2] + K_y[2] + K_y[2]);
//                    int rgb = ((int) (Math.round(sqrtVals[0]) << 16) + ((int) Math.round(sqrtVals[1]) << 8) + (int) Math.round(sqrtVals[2]));
//                    copy[j * image.getWidth() + i] = (ALPHA_MASK & image.getRGB(i, j)) + rgb;
//                }
//            }
//        }

        // helper multiplication method
        // multiplies corresponding indices and adds them
        private double mult(int[][] a, double[][] b){
            double res = 0;
            for (int i = 0; i < 3; i++){
                for (int j = 0; j < 3; j++){
                    res += a[i][j] * b[i][j];
                }
            }
            return res;
        }
        // helper getter method of neighbors
        // returns three 3x3 matrices of r g b values of pixels around i, j
        private int[][][] getMatrix(int i, int j){
            int[][][] res = new int[3][3][3];
            for (int x = -1; x <= 1; x++){
                for (int y = -1; y <= 1; y++){
                    int rgb;
                    if ((i + x == -1 || i + x == image.getWidth()) && (j + y == -1 || j + y == image.getHeight())) {
                        rgb = image.getRGB(i, j) & RGB_MASK;
                    } else if (i + x == -1 || i + x == image.getWidth()) {
                        rgb = image.getRGB(i, j + y) & RGB_MASK;
                    } else if (j + y == -1 || j + y == image.getHeight()) {
                        rgb =  image.getRGB(i + x, j) & RGB_MASK;
                    } else {
                        rgb = image.getRGB(i + x, j + y) & RGB_MASK;
                    }
                    res[0][x + 1][y + 1] = (rgb >> 16) & 0xFF;
                    res[1][x + 1][y + 1] = (rgb >> 8) & 0xFF;
                    res[2][x + 1][y + 1] = rgb & 0xFF;
                }
            }
            return res;
        }
    }
}


