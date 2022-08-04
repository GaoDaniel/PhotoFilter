import spark.Spark;
import utils.CORSFilter;
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class SparkServer {

    public static final Set<String> filters = Set.of("invert", "hflip", "vflip", "gray", "blur", "emoji");
    private static final Map<Integer, BufferedImage> emojis = new HashMap<>();

    /*
     * Server
     * Format of URLs: http://localhost:4567/filtering?uri=base64,randombase64stuff&filter=invert
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
            switch (filter) {
                case "invert":
                    invert(inputImage);
                    break;
                case "hflip":
                    hflip(inputImage);
                    break;
                case "vflip":
                    vflip(inputImage);
                    break;
                case "gray":
                    grayscale(inputImage);
                    break;
                case "blur":
                    blur(inputImage);
                    break;
                case "emoji":
                    emojify(inputImage);
                    break;
            }

            File outputfile = new File("image.png");
            ImageIO.write(inputImage, "png", outputfile);
            
            // convert back to base64 uri
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(inputImage, "png", out);
            imageData = out.toByteArray();
            
            String base64bytes = Base64.getEncoder().encodeToString(imageData);
            Gson gson = new Gson();
            // now just returns bse64 representation
            return gson.toJson(base64bytes);
        });
    }

    // filters represented by private methods
    private static void invert(BufferedImage image) {
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                int argb = image.getRGB(i, j);
                int alpha = 0xFF & (argb >> 24);
                int red = 255 - (0xFF & (argb >> 16));
                int green = 255 - (0xFF & (argb >> 8));
                int blue = 255 - (0xFF & argb);
                image.setRGB(i, j, ((alpha << 24) | (red << 16) | (green << 8) | blue));
            }
        }
    }

    private static void hflip(BufferedImage image) {
        for (int i = 0; i < image.getWidth() / 2; i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                int temp = image.getRGB(i, j);
                image.setRGB(i, j, image.getRGB(image.getWidth() - i - 1, j));
                image.setRGB(image.getWidth() - i - 1, j, temp);
            }
        }
    }

    private static void vflip(BufferedImage image) {
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight() / 2; j++) {
                int temp = image.getRGB(i, j);
                image.setRGB(i, j, image.getRGB(i, image.getHeight() - j - 1));
                image.setRGB(i, image.getHeight() - j - 1, temp);
            }
        }
    }

    private static void grayscale(BufferedImage image) {
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
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

    private static void blur(BufferedImage image) {
        if (image.getWidth() < 3 || image.getHeight() < 3) {
            return;
        }
        int[][] copy = new int[image.getWidth() - 2][image.getHeight() - 2];

        for (int i = 1; i < image.getWidth() - 1; i++) {
            for (int j = 1; j < image.getHeight() - 1; j++) {
                int[] sum = new int[3];
                for (int x = -1; x <= 1; x++){
                    for (int y = -1; y <= 1; y++){
                        int argb = image.getRGB(i+x, j+y);
                        sum[0] += 0xFF & (argb >> 16);
                        sum[1] += 0xFF & (argb >> 8);
                        sum[2] += 0xFF & argb;
                    }
                }
                int alpha = 0xFF & (image.getRGB(i,j) >> 24);
                // floored rgb values
                copy[i-1][j-1] = (alpha << 24) | (sum[0]/9 << 16) | (sum[1]/9 << 8) | (sum[2]/9);
            }
        }

        for (int i = 0; i < copy.length; i++) {
            for (int j = 0; j < copy[0].length; j++) {
                image.setRGB(i + 1, j + 1, copy[i][j]);
            }
        }
    }

    private static void populateEmojis(){
        try {
            String location = "src/main/resources/";
            emojis.put(0xFFFFFF, ImageIO.read(new File(location + "white.png")));
            emojis.put(0xFFFF00, ImageIO.read(new File(location + "yellow.png")));
            emojis.put(0xC0C0C0, ImageIO.read(new File(location + "light_gray.png")));
            emojis.put(0x00FFFF, ImageIO.read(new File(location + "sky_blue.png")));
            emojis.put(0x00FF00, ImageIO.read(new File(location + "yellow_green.png")));
            emojis.put(0x808080, ImageIO.read(new File(location + "gray.png")));
            emojis.put(0x808000, ImageIO.read(new File(location + "dark_yellow.png")));
            emojis.put(0xFF00FF, ImageIO.read(new File(location + "dark_pink.png")));
            emojis.put(0x008080, ImageIO.read(new File(location + "blue-green.png")));
            emojis.put(0xFF0000, ImageIO.read(new File(location + "red.png")));
            emojis.put(0x008000, ImageIO.read(new File(location + "green.png")));
            emojis.put(0x800080, ImageIO.read(new File(location + "purple.png")));
            emojis.put(0x964B00, ImageIO.read(new File(location + "brown.png")));
            emojis.put(0x0000FF, ImageIO.read(new File(location + "blue.png")));
            emojis.put(0x000080, ImageIO.read(new File(location + "dark_blue.png")));
            emojis.put(0x000000, ImageIO.read(new File(location + "black.png")));
        } catch (IOException e){
            System.out.println("Input error: " + e);
        }

    }

    private static void emojify(BufferedImage image){
        if (image.getWidth() < 16 || image.getHeight() < 16){
            return;
        }
        int[][] copy = new int[(image.getWidth() / 16)*16][(image.getHeight()/16)*16];

        for (int i = 15; i < image.getWidth(); i+=16) {
            for (int j = 15; j < image.getHeight(); j+=16) {
                Map<Integer, Integer> counts = new HashMap<>();
                for (int k = i - 15; k <= i; k++){
                    for (int l = j - 15; l <= j; l++){
                        int argb = image.getRGB(k, l);
                        int red = 0xFF & (argb >> 16);
                        int green = 0xFF & (argb >> 8);
                        int blue = 0xFF & argb;

                        int smallestVal = 255*3;
                        int pixColor = 0;
                        for (int color : emojis.keySet()){
                            int cr = 0xFF & (color >> 16);
                            int cg = 0xFF & (color >> 8);
                            int cb = 0xFF & color;
                            int tempVal = Math.abs(cr - red) + Math.abs(cg - green) + Math.abs(cb - blue);
                            if (tempVal < smallestVal){
                                smallestVal = tempVal;
                                pixColor = color;
                            }
                        }
                        counts.put(pixColor, counts.containsKey(pixColor) ? counts.get(pixColor) + 1 : 1);
                    }
                }
                int maxCount = 0;
                int color = -1;
                for (int key : counts.keySet()){
                    if (counts.get(key) > maxCount){
                        color = key;
                        maxCount = counts.get(key);
                    }
                }
                BufferedImage emoji = emojis.get(color);
                for (int k = i - 15; k <= i; k++) {
                    for (int l = j - 15; l <= j; l++) {
                        copy[k][l] = emoji.getRGB(k % 16, l % 16);
                    }
                }
            }
        }
        for (int i = 0; i < copy.length; i++) {
            for (int j = 0; j < copy[0].length; j++) {
                image.setRGB(i, j, copy[i][j]);
            }
        }
    }
}
