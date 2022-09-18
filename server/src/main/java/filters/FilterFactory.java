package filters;

import SparkServer.SparkServer;
import filters.copy.*;
import filters.inPlace.*;

public class FilterFactory {

    public FilterFactory(){}

    /**
     * Creates the filter based on the name
     *
     * @param name name of the filter to generate
     * @return Filter object based on name, and null if filter does not exist
     */
    public Filter createFilter(String name, int intensity, int color){
        switch(name) {
            case "invert":
                return new Invert();
            case "gray":
                return new Grayscale();
            case "bw":
                return new BlackWhite(intensity);
            case "emoji":
                return new Emojify(SparkServer.emojis);
            case "ascii":
                return new Asciify(SparkServer.asciis, false);
            case "ansi":
                return new Asciify(SparkServer.asciis, true);
            case "bright":
                return new ColorMod(0xFFFFFF, intensity);
            case "sat":
                return new Saturate(0x808080, intensity);

            case "red":
                return new ColorMod(0xFF0000, intensity);
            case "green":
                return new ColorMod(0x00FF00, intensity);
            case "blue":
                return new ColorMod(0x0000FF, intensity);
            case "cyan":
                return new ColorMod(0x00FFFF, intensity);
            case "magenta":
                return new ColorMod(0xFF00FF, intensity);
            case "yellow":
                return new ColorMod(0xFFFF00, intensity);
            case "c":
                return new Saturate(color, -intensity);

            case "box":
                return new BoxBlur(intensity);
            case "gauss":
                return new GaussBlur(intensity);
            case "outline":
                return new Outline();
            case "sharp":
                return new Sharp(intensity);
            case "noise":
                return new Noise();

            case "dom":
                return new DominantHue(intensity);

//            case "test1":
//                return new Test1();
//            case "test2":
//                return new Test2();
//            case "test3":
//                return new Test3();

            default:
                return null;
        }
    }
}
