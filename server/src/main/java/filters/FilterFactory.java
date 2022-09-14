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
    public Filter createFilter(String name){
        switch(name) {
            case "invert":
                return new Invert();
            case "gray":
                return new Grayscale();
            case "bw":
                return new BlackWhite();
            case "emoji":
                return new Emojify(SparkServer.emojis);
            case "ascii":
                return new Asciify(SparkServer.asciis, false);
            case "ansi":
                return new Asciify(SparkServer.asciis, true);
            case "bright":
                return new ColorMod(0xFFFFFF);
            case "sat":
                return new Saturate();

            case "red":
                return new ColorMod(0xFF0000);
            case "green":
                return new ColorMod(0x00FF00);
            case "blue":
                return new ColorMod(0x0000FF);
            case "cyan":
                return new ColorMod(0x00FFFF);
            case "magenta":
                return new ColorMod(0xFF00FF);
            case "yellow":
                return new ColorMod(0xFFFF00);

            case "box":
                return new BoxBlur();
            case "gauss":
                return new GaussBlur();
            case "outline":
                return new Outline();
            case "sharp":
                return new Sharp();
            case "noise":
                return new Noise();

            case "dom":
                return new DominantHue();

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
