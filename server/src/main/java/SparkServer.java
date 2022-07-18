import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import utils.CORSFilter;
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class SparkServer {

    public static final Set<String> filters = Set.of("invert", "hflip", "vflip");

    /*
     * Server
     * Format of URLs: http://localhost:4567/filtering?uri=base64,randombase64stuff&filter=invert
     */
    public static void main(String[] args) {
        CORSFilter corsFilter = new CORSFilter();
        corsFilter.apply();

        // request for sorted building names
        Spark.get("/", new Route() {
            @Override
            public Object handle(Request request, Response response) throws Exception {
                return "Test";
            }
        });

        // request for calculating a route given start and end buildings
        Spark.get("/filtering", new Route() {
            @Override
            public Object handle(Request request, Response response) throws Exception {
                String uri = request.queryParams("uri");
                String filter = request.queryParams("filter");

                if (uri == null || filter == null) {
                    Spark.halt(409, "missing one of uri or filter");
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
                    Spark.halt(405, "invalid base64 scheme");
                }

                // get BufferedImage
                BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(imageData));
                if (inputImage == null){
                    Spark.halt(406, "base64 could not be read");
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
                }

                // convert back to base64 uri
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(inputImage, "png", out);
                imageData = out.toByteArray();

                String base64bytes = Base64.getEncoder().encodeToString(imageData);
                Gson gson = new Gson();
                return gson.toJson("data:image/png;base64," + base64bytes);
            }
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
                int blue = 255 - (0xFF & (argb >> 0 ));
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
}
