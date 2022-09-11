import spark.Spark;
import utils.CORSFilter;
import com.google.gson.Gson;

import javax.imageio.ImageIO;
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
    public static final Set<BufferedImage> emojis = new HashSet<>();
    public static final Set<BufferedImage> asciis = new HashSet<>();
    public static final Map<BufferedImage, Integer> numOpaque = new HashMap<>();
    private static final ForkJoinPool fjpool = new ForkJoinPool();

    /*
     * Server
     * Format of URLs: http://localhost:4567/filtering?filter=invert&int=intensity
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
        // System.out.println(numOpaque.values());
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
}