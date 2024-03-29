package SparkServer;

import filters.Filter;
import filters.FilterFactory;
import spark.Spark;
import utils.CORSFilter;
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.*;
import java.util.*;


public class SparkServer {

    public static final Set<BufferedImage> asciis = new HashSet<>();
    public static final Map<BufferedImage, Integer> emojis = new HashMap<>();

    private static final FilterFactory factory = new FilterFactory();
    private static final int ALPHA_MASK = 0xFF000000;

    /*
     * Server
     * Format of URLs: http://localhost:4567/filtering?filter=filter&int=intensity&c=wColor
     */
    public static void main(String[] args) {
        // population of funny filter data
        setup();

        CORSFilter corsFilter = new CORSFilter();
        corsFilter.apply();

        // filter request
        Spark.post("/filtering", (request, response) -> {
            // get params
            String base64 = request.body();
            String filter = request.queryParams("filter");
            int intensity = 0;
            int color = 0xFF0000;
            try {
                intensity = Integer.parseInt(request.queryParams("int"));
            } catch (NumberFormatException e) {
                Spark.halt(501, "bad int format");
            }
            try {
                color = Integer.valueOf(request.queryParams("c"), 16);
            } catch (Exception e) {
                Spark.halt(501, "bad color format");
            }

            if (base64 == null || filter == null) Spark.halt(502, "missing one of base64 or filter");
            // create filter

            Filter f = factory.createFilter(filter.toLowerCase(), intensity, color);
            if (f == null) Spark.halt(503, "filter does not exist");

            // get bytes from base64
            byte[] imageData = new byte[0];
            try {
                imageData = Base64.getDecoder().decode(base64.replace(' ', '+'));
            } catch (IllegalArgumentException e) {
                Spark.halt(504, "invalid base64 scheme");
            }
            if (imageData.length == 0) Spark.halt(505, "invalid base64 scheme");

            // get BufferedImage
            BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (inputImage == null) Spark.halt(506, "base64 could not be read");

            // filter
            f.applyFilter(inputImage);

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
        Set<BufferedImage> tempE = new HashSet<>();
        populate("emojis", tempE);
        for (BufferedImage emoji : tempE) {
            int count = 0;
            for (int i = 0; i < emoji.getWidth(); i++) {
                for (int j = 0; j < emoji.getHeight(); j++) {
                    // count number of opaque pixels
                    if ((emoji.getRGB(i, j) & ALPHA_MASK) != 0) {
                        count++;
                    }
                }
            }
            emojis.put(emoji, count);
        }
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
}