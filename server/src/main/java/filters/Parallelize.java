package filters;

import java.util.concurrent.*;

public class Parallelize extends RecursiveAction {
    int cutoff = 1000;
    int xlow, xhi, ylow, yhi;
    Filter filter;

    public Parallelize(int xlow, int xhi, int ylow, int yhi, Filter filter) {
        this.xlow = xlow;
        this.xhi = xhi;
        this.ylow = ylow;
        this.yhi = yhi;
        this.filter = filter;
    }

    public Parallelize(int xlow, int xhi, int ylow, int yhi, Filter filter, int cutoff) {
        this(xlow, xhi, ylow, yhi, filter);
        this.cutoff = cutoff;
    }

    @Override
    public void compute() {
        if ((xhi - xlow) * (yhi - ylow) <= cutoff) {
            filter.filter(xlow, xhi, ylow, yhi);
        } else {
            Parallelize left, right;
            if ((xhi - xlow) > (yhi - ylow)) {
                left = new Parallelize(xlow, (xhi + xlow) / 2, ylow, yhi, filter, cutoff);
                right = new Parallelize((xhi + xlow) / 2, xhi, ylow, yhi, filter, cutoff);
            } else {
                // left is smaller, right is bigger
                left = new Parallelize(xlow, xhi, ylow, (yhi + ylow) / 2, filter, cutoff);
                right = new Parallelize(xlow, xhi, (yhi + ylow) / 2, yhi, filter, cutoff);
            }
            left.fork();
            right.compute();
            left.join();
        }
    }
}