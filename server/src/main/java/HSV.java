public class HSV{
    int r, g, b;
    int h;
    double s, v;
    int COLOR = SparkServer.COLOR;

    private final int max, min;

    public HSV(int rgb){
        r = COLOR & (rgb >> 16);
        g = COLOR & (rgb >> 8);
        b = COLOR & rgb;

        max = Math.max(r, Math.max(g, b));
        min = Math.min(r, Math.min(g, b));

        v = max/255.0;
        s = max > 0 ? 1 - min/(0.0 + max) : 0;
        h = (int) Math.round(180.0/Math.PI * Math.acos((r - g/2.0 - b/2.0)/(Math.sqrt(r*r + g*g + b*b - r*g - r*b - g*b))));
        if (b > g){
            h = 360 - h;
        }
        h %= 360;
    }

    public HSV(int h, double s, double v) {
        this.h = h;
        this.s = s;
        this.v = v;

        max = (int) (255 * v);
        min = (int) (max * (1 - s));

        int z = (int) ((max - min) * (1.0 - Math.abs((h / 60.0) % 2 - 1)));
        if (h < 60) {
            r = max;
            g = z + min;
            b = min;
        } else if (h < 120) {
            r = z + min;
            g = max;
            b = min;
        } else if (h < 180) {
            r = min;
            g = max;
            b = z + min;
        } else if (h < 240) {
            r = min;
            g = z + min;
            b = max;
        } else if (h < 300) {
            r = z + min;
            g = min;
            b = max;
        } else {
            r = max;
            g = min;
            b = z + min;
        }
    }
}
