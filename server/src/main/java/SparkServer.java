import spark.Spark;
import utils.CORSFilter;
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class SparkServer {

    public static final Set<String> filters = Set.of("invert", "hflip", "vflip", "gray", "blur");

    /*
     * Server
     * Format of URLs: http://localhost:4567/filtering?uri=base64,randombase64stuff&filter=invert
     */
    public static void main(String[] args) {
        CORSFilter corsFilter = new CORSFilter();
        corsFilter.apply();

        // filter request
        Spark.post("/filtering", (request, response) -> {
            System.out.println("got here");
            String uri = request.queryParams("uri");
            String filter = request.queryParams("filter");

            if (uri == null || filter == null) {
                Spark.halt(400, "missing one of uri or filter");
            }
            filter = filter.toLowerCase();
            if (!filters.contains(filter)){
                Spark.halt(401, "filter does not exist");
            }
            uri = uri.replace(' ', '+');
            // make base64 string
            String encodingPrefix = "base64,";
            int index = uri.indexOf(encodingPrefix);
            if (index == -1) {
                Spark.halt(402, "must be base64 image");
            }
            int contentStartIndex = uri.indexOf(encodingPrefix) + encodingPrefix.length();

            // get bytes from base64
            byte[] imageData = new byte[0];
            try {
                System.out.println(uri.substring(contentStartIndex));
                imageData = Base64.getDecoder().decode(uri.substring(contentStartIndex));
            } catch (IllegalArgumentException e) {
                Spark.halt(403, "invalid base64 scheme");
            }
            if (imageData.length == 0) {
                Spark.halt(404, "invalid base64 scheme");
            }

            // get BufferedImage
            BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (inputImage == null){
                Spark.halt(405, "base64 could not be read");
            }

            // filter
            switch (filter){
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
            }

            // convert back to base64 uri
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(inputImage, "png", out);
            imageData = out.toByteArray();

            String base64bytes = Base64.getEncoder().encodeToString(imageData);
            Gson gson = new Gson();
            return gson.toJson("data:image/png;base64," + base64bytes);
        });
    }

    // filters represented by private methods
    private static void invert(BufferedImage image){
        for (int i = 0; i < image.getWidth(); i++){
            for (int j = 0; j < image.getHeight(); j++){
                int argb = image.getRGB(i, j);
                int alpha = 0xFF & (argb >> 24);
                int red = 255 - (0xFF & ( argb >> 16));
                int green = 255 - (0xFF & (argb >> 8 ));
                int blue = 255 - (0xFF & argb);
                image.setRGB(i, j, ((alpha << 24) | (red << 16 ) | (green<<8) | blue));
            }
        }
    }

    private static void hflip(BufferedImage image){
        for(int i = 0; i < image.getWidth()/2; i++){
            for(int j = 0; j < image.getHeight(); j++){
                int temp = image.getRGB(i, j);
                image.setRGB(i, j, image.getRGB(image.getWidth() - i - 1, j));
                image.setRGB(image.getWidth() - i - 1, j, temp);
            }
        }
    }

    private static void vflip(BufferedImage image){
        for(int i = 0; i < image.getWidth(); i++){
            for(int j = 0; j < image.getHeight()/2; j++){
                int temp = image.getRGB(i, j);
                image.setRGB(i, j, image.getRGB(i, image.getHeight() - j - 1));
                image.setRGB(i, image.getHeight() - j - 1, temp);
            }
        }
    }

    private static void grayscale(BufferedImage image){
        for (int i = 0;i < image.getWidth(); i++){
            for (int j = 0; j < image.getHeight(); j++){
                int argb = image.getRGB(i, j);
                int alpha = 0xFF & (argb >> 24);
                int red = 0xFF & (argb >> 16);
                int green = 0xFF & (argb >> 8);
                int blue = 0xFF & argb;
                int gray = (red + green + blue)/3;
                image.setRGB(i, j, ((alpha << 24) | (gray << 16 ) | (gray<<8) | gray));
            }
        }
    }

    private static void blur(BufferedImage image){
        if (image.getWidth() < 3 || image.getHeight() < 3){
            return;
        }
        int[][] copy = new int[image.getHeight() - 2][image.getWidth() - 2];
        // optimized for rows (could probably be further optimized)
        for (int i = 0; i < image.getHeight() - 2; i++){
            int[][] a = new int[3][3]; // sort of like a mini cache
            int[] sum = new int[3]; // sum of rgb
            for (int k = 0; k < 3; k++) {
                for (int j = 0; j < 2; j++) {
                    a[k][j] = image.getRGB(k, j);
                    sum[0] += 0XFF & (a[k][j] >> 16);
                    sum[1] += 0XFF & (a[k][j] >> 8);
                    sum[2] += 0xFF & a[k][j];
                }
            }
            for (int j = 0; j < image.getWidth() - 2; j++){
                a[0][(j+2)%3] = image.getRGB(i + 2, j);
                a[1][(j+2)%3] = image.getRGB(i + 2, j + 1);
                a[2][(j+2)%3] = image.getRGB(i + 2, j + 2);
                sum[0] += (0xFF & (a[0][(j+2)%3] >> 16)) - (0xFF & (a[1][(j+2)%3] >> 16)) - (0xFF & (a[2][(j+2)%3] >> 16));
                sum[1] += (0xFF & (a[0][(j+2)%3] >> 8)) - (0xFF & (a[1][(j+2)%3] >> 8)) - (0xFF & (a[2][(j+2)%3] >> 8));
                sum[2] += (0xFF & a[0][(j+2)%3]) - (0xFF & a[1][(j+2)%3]) - (0xFF & a[2][(j+2)%3]);

                int alpha = 0xFF & (a[1][(j+1)%3] >> 24);
                // values are rounded down
                copy[i][j] = ((alpha << 24) | (sum[0]/9 << 16 ) | (sum[1]/9 <<8) | (sum[2]/9));

                sum[0] -= (0xFF & (a[0][j%3] >> 16)) - (0xFF & (a[1][j%3] >> 16)) - (0xFF & (a[2][j%3] >> 16));
                sum[1] -= (0xFF & (a[0][j%3] >> 8)) - (0xFF & (a[1][j%3] >> 8)) - (0xFF & (a[2][j%3] >> 8));
                sum[2] -= (0xFF & a[0][j%3]) - (0xFF & a[1][j%3]) - (0xFF & a[2][j%3]);
            }
        }
        for (int i = 0; i < copy.length; i++){
            for (int j = 0; j < copy[0].length; j++){
                image.setRGB(i + 1, j + 1, copy[i][j]);
            }
        }
    }
}
