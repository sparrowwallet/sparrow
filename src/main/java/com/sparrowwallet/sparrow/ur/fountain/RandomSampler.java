package com.sparrowwallet.sparrow.ur.fountain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Random-number sampling using the Walker-Vose alias method,
 * as described by Keith Schwarz (2011)
 * http://www.keithschwarz.com/darts-dice-coins
 *
 * Based on C implementation:
 * https://jugit.fz-juelich.de/mlz/ransampl
 *
 * Ported from https://github.com/BlockchainCommons/URKit
 */
public class RandomSampler {
    /* The probability and alias tables. */
    private final double[] probs;
    private final int[] aliases;

    public RandomSampler(List<Double> probabilities) {
        if(probabilities.stream().anyMatch(prob -> prob < 0)) {
            throw new IllegalArgumentException("Probabilties must be > 0");
        }

        // Normalize given probabilities
        double sum = probabilities.stream().reduce(0d, Double::sum);

        int n = probabilities.size();
        List<Double> P = probabilities.stream().map(prob -> prob * (double)n / sum).collect(Collectors.toList());

        List<Integer> S = new ArrayList<>();
        List<Integer> L = new ArrayList<>();

        // Set separate index lists for small and large probabilities:
        for(int i = n - 1; i >= 0; i--) {
            // at variance from Schwarz, we reverse the index order
            if(P.get(i) < 1d) {
                S.add(i);
            } else {
                L.add(i);
            }
        }

        // Work through index lists
        double[] probs = new double[n];
        int[] aliases = new int[n];

        while(!S.isEmpty() && !L.isEmpty()) {
            int a = S.remove(S.size() - 1);
            int g = L.remove(L.size() - 1);
            probs[a] = P.get(a);
            aliases[a] = g;
            P.set(g, P.get(g) + P.get(a) - 1);
            if(P.get(g) < 1) {
                S.add(g);
            } else {
                L.add(g);
            }
        }

        while(!L.isEmpty()) {
            probs[L.remove(L.size() - 1)] = 1;
        }

        while(!S.isEmpty()) {
            // can only happen through numeric instability
            probs[S.remove(S.size() - 1)] = 1;
        }

        this.probs = probs;
        this.aliases = aliases;
    }

    public int next(Random random) {
        double r1 = random.nextDouble();
        double r2 = random.nextDouble();
        int n = probs.length;
        int i = (int)((double)n * r1);
        return r2 < probs[i] ? i : aliases[i];
    }
}
