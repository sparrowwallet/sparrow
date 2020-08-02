package com.sparrowwallet.sparrow.ur.fountain;

/*
 * To the extent possible under law, the author has dedicated all copyright
 * and related and neighboring rights to this software to the public domain
 * worldwide. This software is distributed without any warranty.
 *
 * See <http://creativecommons.org/publicdomain/zero/1.0/>
 */

import com.sparrowwallet.drongo.protocol.Sha256Hash;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of Random based on the xoshiro256** RNG. No-dependencies
 * Java port of the <a href="http://xoshiro.di.unimi.it/xoshiro256starstar.c">original C code</a>,
 * which is public domain. This Java port is similarly dedicated to the public
 * domain.
 * <p>
 * Individual instances are not thread-safe. Each thread must have its own
 * instance which is not shared.
 *
 * @author David Blackman and Sebastiano Vigna &lt;vigna@acm.org> (original C code)
 * @author Una Thompson &lt;una@unascribed.com> (Java port)
 * @see <a href="http://xoshiro.di.unimi.it/">http://xoshiro.di.unimi.it/</a>
 */
public class RandomXoshiro256StarStar extends Random {
    private static final long serialVersionUID = -2837799889588687855L;

    private static final AtomicLong uniq = new AtomicLong(System.nanoTime());

    private static final long nextUniq() {
        return splitmix64_2(uniq.addAndGet(SPLITMIX1_MAGIC));
    }

    private long seed;

    public RandomXoshiro256StarStar() {
        this(System.nanoTime() ^ nextUniq());
    }

    public RandomXoshiro256StarStar(long seed) {
        super(seed);
        // super will call setSeed
    }

    public RandomXoshiro256StarStar(String seed) {
        this(seed.getBytes(StandardCharsets.UTF_8));
    }

    public RandomXoshiro256StarStar(byte[] seed) {
        this(Sha256Hash.of(seed));
    }

    public RandomXoshiro256StarStar(Sha256Hash digest) {
        long[] s = new long[4];
        byte[] digestBytes = digest.getBytes();

        for(int i = 0; i < 4; i++) {
            int o = i * 8;
            long v = 0L;
            for(int n = 0; n < 8; n++) {
                v = v << 8;
                v |= digestBytes[o + n] & 0xFF;
            }
            s[i] = v;
        }

        setState(s[0], s[1], s[2], s[3]);
    }

    public RandomXoshiro256StarStar(long s1, long s2, long s3, long s4) {
        setState(s1, s2, s3, s4);
    }

    // used to "stretch" seeds into a full 256-bit state; also makes
    // it safe to pass in zero as a seed
    ////
    // what generator is used here is unimportant, as long as it's
    // from a different family, but splitmix64 happens to be an
    // incredibly simple high-quality generator of a completely
    // different family (and is recommended by the xoshiro authors)

    private static final long SPLITMIX1_MAGIC = 0x9E3779B97F4A7C15L;

    private static long splitmix64_1(long x) {
        return (x + SPLITMIX1_MAGIC);
    }

    private static long splitmix64_2(long z) {
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >> 31);
    }

    @Override
    public void setSeed(long seed) {
        this.seed = seed;

        // update haveNextNextGaussian flag in super
        super.setSeed(seed);
        long sms = splitmix64_1(seed);
        s0 = splitmix64_2(sms);
        sms = splitmix64_1(sms);
        s1 = splitmix64_2(sms);
        sms = splitmix64_1(sms);
        s2 = splitmix64_2(sms);
        sms = splitmix64_1(sms);
        s3 = splitmix64_2(sms);
    }

    public void setState(long s0, long s1, long s2, long s4) {
        if(s0 == 0 && s1 == 0 && s2 == 0 && s4 == 0) {
            throw new IllegalArgumentException("xoshiro256** state cannot be all zeroes");
        }
        this.s0 = s0;
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s4;
    }

    // not called, implemented instead of just throwing for completeness
    @Override
    protected int next(int bits) {
        return (int) (nextLong() & ((1L << bits) - 1));
    }

    @Override
    public int nextInt() {
        return (int) nextLong();
    }

    @Override
    public int nextInt(int bound) {
        return (int) nextLong(bound);
    }

    public long nextLong(long bound) {
        if(bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        // clear sign bit for positive-only, modulo to bound
        return (nextLong() & Long.MAX_VALUE) % bound;
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0P-53;
    }

    @Override
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0P-24f;
    }

    @Override
    public boolean nextBoolean() {
        return (nextLong() & 1) != 0;
    }

    @Override
    public void nextBytes(byte[] buf) {
        nextBytes(buf, 0, buf.length);
    }

    public void nextBytes(byte[] buf, int ofs, int len) {
        if(ofs < 0) {
            throw new ArrayIndexOutOfBoundsException("Offset " + ofs + " is negative");
        }
        if(ofs >= buf.length) {
            throw new ArrayIndexOutOfBoundsException("Offset " + ofs + " is greater than buffer length");
        }
        if(ofs + len > buf.length) {
            throw new ArrayIndexOutOfBoundsException("Length " + len + " with offset " + ofs + " is past end of buffer");
        }
        int j = 8;
        long l = 0;
        for(int i = ofs; i < ofs + len; i++) {
            if(j >= 8) {
                l = nextLong();
                j = 0;
            }
            buf[i] = (byte) (l & 0xFF);
            l = l >>> 8L;
            j++;
        }
    }

    public void nextData(byte[] data) {
        for(int i = 0; i < data.length; i++) {
            data[i] = (byte)(nextInt(0, 256) & 0xFF);
        }
    }

    public int nextInt(int lowerBound, int count) {
        double next = nextDouble();
        double dou = (next * count);
        return (int)(dou) + lowerBound;
    }

	/* This is xoshiro256** 1.0, our all-purpose, rock-solid generator. It has
	   excellent (sub-ns) speed, a state (256 bits) that is large enough for
	   any parallel application, and it passes all tests we are aware of.

	   For generating just floating-point numbers, xoshiro256+ is even faster.

	   The state must be seeded so that it is not everywhere zero. If you have
	   a 64-bit seed, we suggest to seed a splitmix64 generator and use its
	   output to fill s. */

    private static long rotl(long x, int k) {
        return (x << k) | (x >>> (64 - k));
    }


    private long s0;
    private long s1;
    private long s2;
    private long s3;

    @Override
    public long nextLong() {
        long result_starstar = rotl(s1 * 5, 7) * 9;

        long t = s1 << 17;

        s2 ^= s0;
        s3 ^= s1;
        s1 ^= s2;
        s0 ^= s3;

        s2 ^= t;

        s3 = rotl(s3, 45);

        return result_starstar;
    }
}
