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
    public static final Set<String> filters = Set.of("invert", "gray", "box", "gauss", "emoji", "detail", "sharp");
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
        // filter
        // Spark.threadPool(8);
        // fjpool.invoke(new Parallelize(inputImage, 0, inputImage.getWidth(),0, inputImage.getHeight(), filter));
        if (filter.equals("box") || filter.equals("gauss")) {
            int[] copy = new int[inputImage.getWidth() * inputImage.getHeight()];
            fjpool.invoke(new ParallelizeBlur(inputImage, copy,
                    0, inputImage.getWidth(), 0, inputImage.getHeight(), filter));
            inputImage.setRGB(0, 0, inputImage.getWidth(), inputImage.getHeight(), copy,
                    0, inputImage.getWidth());

        } 
        else if (filter.equals("detail")) {
            // note: can clean up by making a separate copy method
            ColorModel cm = inputImage.getColorModel();
            boolean isAlpha = inputImage.isAlphaPremultiplied();
            WritableRaster raster = inputImage.copyData(null);
            BufferedImage temp = new BufferedImage(cm, raster, isAlpha, null)
                .getSubimage(0, 0, inputImage.getWidth(), inputImage.getHeight());
            int[] copy = new int[inputImage.getWidth() * inputImage.getHeight()];
            fjpool.invoke(new ParallelizeBlur(inputImage, copy,
                    0, inputImage.getWidth(), 0, inputImage.getHeight(), "gauss"));
            // then loop through and update inputImage with rgb values of (temp - copy)
        } 
        else if (filter.equals("sharpen")){
            // get the detail
            // add detail onto inputImage
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
                }
            } else {
                System.out.print("did the threading thing");
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
    private static class ParallelizeBlur extends RecursiveAction {
        final int SEQUENTIAL_CUTOFF = 10000;
        int xlow, xhi, ylow, yhi;
        String filter;
        int[] copy;
        BufferedImage image;

        public ParallelizeBlur(BufferedImage image, int[] copy, int xlow, int xhi, int ylow, int yhi, String filter) {
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
                        box();
                        break;
                    case "gauss":
                        gauss();
                        break;
                    case "detail":

                }
            } else {
                ParallelizeBlur left, right;
                if ((xhi - xlow) > (yhi - ylow)) {
                    left = new ParallelizeBlur(image, copy, xlow, (xhi + xlow) / 2, ylow, yhi, filter);
                    right = new ParallelizeBlur(image, copy, (xhi + xlow) / 2, xhi, ylow, yhi, filter);
                } else {
                    // left is smaller, right is bigger
                    left = new ParallelizeBlur(image, copy, xlow, xhi, ylow, (yhi + ylow) / 2, filter);
                    right = new ParallelizeBlur(image, copy, xlow, xhi, (yhi + ylow) / 2, yhi, filter);
                }
                left.fork();
                right.compute();
                left.join();
            }
        }

        // applies the box filter in a 3x3 box
        private void box() {
            for (int j = ylow; j < yhi; j++) {
                for (int i = xlow; i < xhi; i++) {
                    double[] avg = new double[3];
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            int val = getVal(i, j, x, y);
                            /*
                            1/9 1/9 1/9
                            1/9 1/9 1/9
                            1/9 1/9 1/9
                            */
                            avg[0] += (0xFF & (val >> 16)) * 0.111;
                            avg[1] += (0XFF & (val >> 8)) * 0.111;
                            avg[2] += (0xFF & val) * 0.111;
                        }
                    }
                    // keep same a val, round rgb value
                    int rgb = ((int) (Math.round(avg[0]) << 16) + ((int) Math.round(avg[1]) << 8) + (int) Math.round(avg[2]));
                    copy[j * image.getWidth() + i] = (0xFF000000 & image.getRGB(i, j)) + rgb;
                }
            }
        }

        // applies the gaussian filter in a 3x3 box
        private void gauss() {
            for (int j = ylow; j < yhi; j++) {
                for (int i = xlow; i < xhi; i++) {
                    double[] avg = new double[3];
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            int val = getVal(i, j, x, y);
                            /*
                            1/16 1/8 1/16
                            1/8  1/4 1/8
                            1/16 1/8 1/16
                            */
                            double gaussDistr = 0.25 * Math.pow(0.5, Math.abs(x) + Math.abs(y));
                            avg[0] += (0xFF & (val >> 16)) * gaussDistr;
                            avg[1] += (0xFF & (val >> 8)) * gaussDistr;
                            avg[2] += (0xFF & val) * gaussDistr;
                        }
                    }
                    // keep same argb val, round rgb value
                    int rgb = ((int) (Math.round(avg[0]) << 16) + ((int) Math.round(avg[1]) << 8) + (int) Math.round(avg[2]));
                    copy[j * image.getWidth() + i] = (0xFF000000 & image.getRGB(i, j)) + rgb;
                }
            }
        }

        // gets detail outline of image
        private void detail() {
            for(int j = ylow; j < yhi; j++){
                for(int i = xlow; i < xhi; i++){

                }
            }
        }

        // helper getter method of neighbors
        private int getVal(int i, int j, int x, int y){
            if ((i + x == -1 || i + x == image.getWidth()) && (j + y == -1 || j + y == image.getHeight())) {
                return image.getRGB(i, j) & 0xFFFFFF;
            } else if (i + x == -1 || i + x == image.getWidth()) {
                return image.getRGB(i, j + y) & 0xFFFFFF;
            } else if (j + y == -1 || j + y == image.getHeight()) {
                return image.getRGB(i + x, j) & 0xFFFFFF;
            } else {
                return image.getRGB(i + x, j + y) & 0xFFFFFF;
            }
        }
    }
}
