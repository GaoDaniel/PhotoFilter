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
    public static final int ALPHA_MASK = 0xFF000000;
    public static final int RGB_MASK = 0xFFFFFF;
    public static final int COLOR = 0xFF;

    public static final Set<String> filters = Set.of("invert", "gray", "box", "gauss", 
                                            "emoji", "outline", "sharp", "bright", "dim", "test1", "test2", "test3");
    private static final Set<String> matrixFilters = Set.of("gauss", "box", "sharp", "outline", "test1", "test2", "test3");
    private static final Set<BufferedImage> emojis = new HashSet<>();
    private static final Map<BufferedImage, Integer> numOpaque = new HashMap<>();
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

    // adds BufferedImages of emojis to set
    private static void populateEmojis() {
        try {
            String location = "src/main/resources";
            File[] images = new File(location).listFiles();
            assert images != null;
            for (File image : images) {
                BufferedImage emoji = ImageIO.read(image);
                int count = 0;
                for (int i = 0; i < emoji.getWidth(); i++){
                    for (int j = 0; j < emoji.getHeight(); j++){
                        // count number of transparent pixels
                        if ((emoji.getRGB(i, j) & ALPHA_MASK) != 0){
                            count++;
                        }
                    }
                }
                emojis.add(emoji);
                numOpaque.put(emoji, count);
            }
            System.out.println(numOpaque.values());
        } catch (IOException e) {
            System.out.println("Input error: " + e);
        }
    }

    // does parallelized filter based on the filter type
    private static void filter(BufferedImage inputImage, String filter) {
        if (matrixFilters.contains(filter)) {
            int[] copy = new int[inputImage.getWidth() * inputImage.getHeight()];
            fjpool.invoke(new ParallelizeCopy(inputImage, copy, 0, inputImage.getWidth(), 0, inputImage.getHeight(), filter));
            inputImage.setRGB(0, 0, inputImage.getWidth(), inputImage.getHeight(), copy, 0, inputImage.getWidth());
        } else {
            fjpool.invoke(new Parallelize(inputImage, 0, (inputImage.getWidth() + 15) / 16, 0, (inputImage.getHeight() + 15) / 16, filter));
        }
    }

    // xlow, xhi, ylow, yhi are in terms of 16x16 blocks
    private static class Parallelize extends RecursiveAction {
        // final int SEQUENTIAL_CUTOFF = 10000;
        final int SEQUENTIAL_CUTOFF = 4;
        final int BLOCK_LENGTH = 16;
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

        private void bright() {
            for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
                for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    int red = Math.min(255, (int) ((COLOR & (argb >> 16)) * 1.5));
                    int green = Math.min(255, (int) ((COLOR & (argb >> 8)) * 1.5));
                    int blue = Math.min(255, (int) ((COLOR & argb) * 1.5));
                    image.setRGB(i, j, ((argb & ALPHA_MASK) | (red << 16) | (green << 8) | blue));
                }
            }
        }

        private void dim() {
            for (int i = xlow * BLOCK_LENGTH; i < xhi * BLOCK_LENGTH && i < image.getWidth(); i++) {
                for (int j = ylow * BLOCK_LENGTH; j < yhi * BLOCK_LENGTH && j < image.getHeight(); j++) {
                    int argb = image.getRGB(i, j);
                    int red = COLOR & (argb >> 16);
                    int green = COLOR & (argb >> 8);
                    int blue = COLOR & argb;
                    image.setRGB(i, j, ((argb & ALPHA_MASK) | (red / 2 << 16) | (green / 2 << 8) | blue / 2));
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
                                    distance.put(image, distance.get(image) + sumDist/numOpaque.get(image));
                                }
                            }
                        }
                    }
                    BufferedImage emoji = null;
//                    System.out.println(distance.values());
                    double minDist = 255 * 3.0;
                    for (BufferedImage image : distance.keySet()){
                        if (distance.get(image) < minDist){
                            minDist = distance.get(image);
                            System.out.println(minDist);
                            emoji = image;
                        }
                    }

                    assert emoji != null;
                    // convert 16x16 block into emoji
                    for (int i = x * BLOCK_LENGTH; i < (x + 1) * BLOCK_LENGTH && i < image.getWidth(); i++) {
                        for (int j = y * BLOCK_LENGTH; j < (y + 1) * BLOCK_LENGTH && j < image.getHeight(); j++) {
                            image.setRGB(i, j, emoji.getRGB(i % BLOCK_LENGTH, j % BLOCK_LENGTH));
                        }
                    }
                }
            }
        }
    }

    // xlow, xhi, ylow, yhi are in terms of pixels
    private static class ParallelizeCopy extends RecursiveAction {
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
                        matrix(new double[][]{{1.0 / 9, 1.0 / 9, 1.0 / 9}, {1.0 / 9, 1.0 / 9, 1.0 / 9}, {1.0 / 9, 1.0 / 9, 1.0 / 9}});
                        break;
                    case "gauss":
                        matrix(new double[][]{{0.0625, 0.125, 0.0625}, {0.125, 0.25, 0.125}, {0.0625, 0.125, 0.0625}});
                        break;
                    case "outline":
                    // matrix(new double[][]{{0, 1, 0}, {1, -4, 1}, {0, 1, 0}});
                    // matrix(new double[][]{{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}});
                    // matrix(new double[][]{{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}});
                        matrix(new double[][]{{-1.0, -1.0, -1.0}, {-1.0, 8.0, -1.0}, {-1.0, -1.0, -1.0}});
                        break;
                    case "sharp":
//                        matrix(new double[][]{{-1.0/9, -1.0/9, -1.0/9}, {-1.0/9, 17.0/9, -1.0/9}, {-1.0/9, -1.0/9, -1.0/9}});
                        matrix(new double[][]{{0, -0.75, 0}, {-0.75, 4, -0.75}, {0, -0.75, 0}});
                        break;
                    case "test1":
                        matrix(new double[][]{{-1, 0, 1}, {-1, 1, 1}, {-1, 0, 1}});
                        break;
                    case "test2":
                        matrix(new double[][]{{1, 1, 1}, {0, 1, 0}, {-1, -1, -1}});
                        break;
                    case "test3":
                        matrix(new double[][]{{-1, -1, -1}, {0, 1, 0}, {1, 1, 1}});
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

                    // keep same alpha val, round rgb value
                    int rgb = ((int) (Math.round(r) << 16) + ((int) Math.round(g) << 8) + (int) Math.round(b));
                    copy[j * image.getWidth() + i] = (ALPHA_MASK & image.getRGB(i, j)) | rgb;
                }
            }
        }

//        private void edgeDetection(){
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
//                            K_x[0] += (PIX_COLOR & (val >> 16)) * baseX;
//                            K_y[0] += (PIX_COLOR & (val >> 16)) * baseY;
//                            K_x[1] += (PIX_COLOR & (val >> 8)) * baseX;
//                            K_y[1] += (PIX_COLOR & (val >> 8)) * baseY;
//                            K_x[2] += (PIX_COLOR & val) * baseX;
//                            K_y[2] += (PIX_COLOR & val) * baseY;
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
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    int rgb;
                    if ((i + x == -1 || i + x == image.getWidth()) && (j + y == -1 || j + y == image.getHeight())) {
                        rgb = image.getRGB(i, j) & RGB_MASK;
                    } else if (i + x == -1 || i + x == image.getWidth()) {
                        rgb = image.getRGB(i, j + y) & RGB_MASK;
                    } else if (j + y == -1 || j + y == image.getHeight()) {
                        rgb = image.getRGB(i + x, j) & RGB_MASK;
                    } else {
                        rgb = image.getRGB(i + x, j + y) & RGB_MASK;
                    }
                    res[0][x + 1][y + 1] = (rgb >> 16) & COLOR;
                    res[1][x + 1][y + 1] = (rgb >> 8) & COLOR;
                    res[2][x + 1][y + 1] = rgb & COLOR;
                }
            }
            return res;
        }
    }
}


