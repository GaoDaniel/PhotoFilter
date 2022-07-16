import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import utils.CORSFilter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class SparkServer {

    public static final Set<String> filters = Set.of("invert");

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
                String base64 = request.queryParams("uri");
                String filter = request.queryParams("filter");

                if (base64 == null || filter == null) {
                    Spark.halt(400, "missing one of uri or filter");
                }
                filter = filter.toLowerCase();
                if (!filters.contains(filter)){
                    Spark.halt(400, "filter does not exist");
                }

                // make base64 string
                String encodingPrefix = "base64,";
                int index = base64.indexOf(encodingPrefix);
                if (index == -1) {
                    Spark.halt(400, "must be base64 image");
                }
                int contentStartIndex = base64.indexOf(encodingPrefix) + encodingPrefix.length();

                // get bytes from base64
                byte[] imageData = new byte[0];
                try {
                    imageData = Base64.getDecoder().decode(base64.substring(contentStartIndex));
                } catch (IllegalArgumentException e) {
                    Spark.halt(400, "invalid base64 scheme");
                }
                if (imageData.length == 0) {
                    Spark.halt(400, "invalid base64 scheme");
                }

                // get BufferedImage
                BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(imageData));
                if (inputImage == null){
                    Spark.halt(400, "base64 could not be read");
                }

                // filter
                switch (filter){
                    case "invert":
                        invert(inputImage);
                        break;
                }

                // convert back to base64 uri
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(inputImage, "png", out);
                imageData = out.toByteArray();

                String base64bytes = Base64.getEncoder().encodeToString(imageData);
                return "data:image/png;base64," + base64bytes;
            }
        });
    }

    // filters represented by private methods
    private static void invert(BufferedImage image){
        for (int i = 0; i < image.getWidth(); i++){
            for (int j = 0; j < image.getHeight(); j++){
                // bitwise not
                image.setRGB(i, j, ~image.getRGB(i, j));
            }
        }
    }
}
