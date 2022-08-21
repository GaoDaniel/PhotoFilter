import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GetEmojiColor {

    /*
    This class helps determine the actual average colors of every emoji image
    NOTE: This class is no longer needed because emojis are chosen based on comparisons with the pixel
        data themselves, rather than an average value
     */

    private static final Map<Integer, BufferedImage> emojis = new HashMap<>();

    public static void main(String[] args) throws IOException {
        String location = "src/main/resources/";

        try {
            emojis.put(0x6f6a6d, ImageIO.read(new File(location + "white.png")));
//            emojis.put(0x93752a, ImageIO.read(new File(location + "yellow.png")));
//            emojis.put(0x323332, ImageIO.read(new File(location + "light_gray.png")));
//            emojis.put(0x3d635d, ImageIO.read(new File(location + "sky_blue.png")));
//            emojis.put(0x3d533f, ImageIO.read(new File(location + "yellow_green.png")));
//            emojis.put(0x383c48, ImageIO.read(new File(location + "gray.png")));
//            emojis.put(0xd1ac5b, ImageIO.read(new File(location + "dark_yellow.png")));
//            emojis.put(0xb0738d, ImageIO.read(new File(location + "dark_pink.png")));
//            emojis.put(0x3b5773, ImageIO.read(new File(location + "blue_green.png")));
//            emojis.put(0xa02638, ImageIO.read(new File(location + "red.png")));
//            emojis.put(0x5f6f12, ImageIO.read(new File(location + "green.png")));
//            emojis.put(0x632c6f, ImageIO.read(new File(location + "purple.png")));
//            emojis.put(0x3c2a1f, ImageIO.read(new File(location + "brown.png")));
//            emojis.put(0x3584d8, ImageIO.read(new File(location + "blue.png")));
//            emojis.put(0x212c47, ImageIO.read(new File(location + "dark_blue.png")));
//            emojis.put(0x393a37, ImageIO.read(new File(location + "black.png")));
        } catch (IOException e) {
            System.out.println("Input error: " + e);
        }
        for (int color : emojis.keySet()){
            int actual = averageColor(emojis.get(color));
            if (color != actual) {
                System.out.printf("emojis.put(%x, ImageIO.read(new File(location + \"black.png\")));\n", actual);
            }
        }
    }

    private static int averageColor(BufferedImage image){
        double[] avg = new double[3];
        for (int i = 0; i < image.getWidth(); i++){
            for (int j = 0; j < image.getHeight(); j++){
                int val = image.getRGB(i, j);
                avg[0] += (0xFF & (val >> 16)) / (0.0 + image.getWidth()*image.getHeight());
                avg[1] += (0XFF & (val >> 8)) / (0.0 + image.getWidth()*image.getHeight());
                avg[2] += (0xFF & val) / (0.0 + image.getWidth()*image.getHeight());
            }
        }
        return ((int) (Math.round(avg[0]) << 16) + ((int) Math.round(avg[1]) << 8) + (int) Math.round(avg[2]));
    }
}
