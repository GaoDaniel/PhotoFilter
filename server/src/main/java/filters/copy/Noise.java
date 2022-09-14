package filters.copy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Noise extends CopyFilter {
    public Noise(){
        super(null);
    }

    @Override
    protected void filter(int xlow, int xhi, int ylow, int yhi) {
        for (int j = ylow; j < yhi; j++) {
            for (int i = xlow; i < xhi; i++) {
                List<Integer> r = new ArrayList<>();
                List<Integer> g = new ArrayList<>();
                List<Integer> b = new ArrayList<>();
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        if (i + x != -1 && i + x != bi.getWidth() && j + y != -1 && j + y != bi.getHeight()) {
                            int rgb = bi.getRGB(i + x, j) & RGB_MASK;
                            r.add((rgb >> 16) & COLOR);
                            g.add((rgb >> 8) & COLOR);
                            b.add((rgb & COLOR));
                        }
                    }
                }
                Collections.sort(r);
                Collections.sort(g);
                Collections.sort(b);
                int med = r.size() / 2;
                int rmed = r.get(med);
                int gmed = g.get(med);
                int bmed = b.get(med);
                if (r.size() % 2 == 0) {
                    rmed = (rmed + r.get(med - 1)) / 2;
                    gmed = (gmed + g.get(med - 1)) / 2;
                    bmed = (bmed + b.get(med - 1)) / 2;
                }
                // keep same alpha val, round rgb value
                int rgb = (rmed << 16) + (gmed << 8) + bmed;
                copy[j * bi.getWidth() + i] = (ALPHA_MASK & bi.getRGB(i, j)) | rgb;
            }
        }
    }
}
