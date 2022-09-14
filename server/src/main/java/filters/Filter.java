package filters;

import java.awt.image.BufferedImage;
import java.util.concurrent.ForkJoinPool;

public abstract class Filter {

    /**
     * useful constants
     */
    public static final int ALPHA_MASK = 0xFF000000;
    public static final int RGB_MASK = 0xFFFFFF;
    public static final int COLOR = 0xFF;

    /**
     * pool used to parallelize filter
     */
    public static final ForkJoinPool fjpool = new ForkJoinPool();

    /**
     * stores reference to image to apply filter on
     */
    protected BufferedImage bi;

    /**
     * invokes filtering of image
     *
     * @param bi the image to apply the filter on
     */
    public abstract void applyFilter(BufferedImage bi);

    /**
     * Applies filter in range
     * Called from Parallelized class
     *
     * @param xlow lower x bound
     * @param xhi higher x bound
     * @param ylow lower y bound
     * @param yhi higher y bound
     */
    protected abstract void filter(int xlow, int xhi, int ylow, int yhi);
}
