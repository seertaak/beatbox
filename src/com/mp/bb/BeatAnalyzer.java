package com.mp.bb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.transform.FastFourierTransformer;

public class BeatAnalyzer implements Runnable {

    public static class AnalyzeInfo {
        public float[] signal;
        public float[] broadbandScore;
        public int[] stftFrameSamplePos;
        public float[][] onsets;
        public float[] transientTimes;
        public int[] transientClassifications;
        public RealMatrix powerSpectrum;
        public float[] stftTimeDomainLocalEnergy;
        public float[] stftLocalEnergy;
        public float[] localEnergyScore;
        public DetectHisto detectHisto;
        public float[] pddh;
        public float[] pdh;
        public float[] spectralDiffScore;
        public float[] avgScore;
        public float[] medianScore;
        public float[] score;
        public int[] onsetStartSamples;
        public int[] onsetEndSamples;
        public int[] onsetSamples;
        public float[] detectFn;
        public float[][] hitFeatVec;
        public int[] clusters;
    }
    private static final float[] FREQ_CUTOFF = {500, 1000, 2000, 3000, 4000, 5000, 6000, 10000};
    private static final int HOP_SIZE_SAMPLES = 441;
    private static final int WINDOW_SIZE_SAMPLES = 882;
    private static final int FRAME_SIZE = 1 << 11;
    private static final double SAMPLE_RATE = 44100f;
    private static final float THRESHOLD = 0.4f;
    private static final int ONSET_DIST_THRESH = 3000;
    private static final int MAX_NUM_ONSETS = 256;
    private static final int MIN_HIT_LENGTH = 2;
    private AudioEngine audio;

    public BeatAnalyzer(AudioEngine audio) {
        this.audio = audio;
    }

    @Override
    public void run() {
        System.out.println("Wait");
        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        AnalyzeInfo analyze = new AnalyzeInfo();

        analyze.signal = new float[audio.loopLength()];
        System.arraycopy(audio.loop(), 0, analyze.signal, 0, analyze.signal.length);
        hpfilt(40f, analyze.signal);

        analyzeSTFT(analyze);
        computeLocalEnergyScore(analyze);
        detectOnsets(analyze);
        classifyHits(analyze);

        audio.doneAnalyzing(analyze);
    }

    private static float nyquistFreq(float sampleRate) {
        return sampleRate / 2f;
    }

    private void classifyHits(AnalyzeInfo ai) {
        if (ai.onsets.length == 0) {
            return;
        }

        ai.hitFeatVec = new float[ai.onsets.length][];
        for (int i = 0; i < ai.onsets.length; i++) {
            float[] nhit = new float[ai.onsets[i].length];
            System.arraycopy(ai.onsets[i], 0, nhit, 0, nhit.length);
            normalize(nhit);

            // find first power of 2 greater than twice the hit length.
            int N = 1;
            while ((N <<= 1) < 2 * nhit.length);

            double[] x = new double[N];
            for (int j = 0; j < nhit.length; j++) {
                x[j] = nhit[j];
            }

            FastFourierTransformer fft = new FastFourierTransformer();
            Complex[] X = fft.transform(x);
            assert 2 * X.length == x.length;

            ai.hitFeatVec[i] = new float[FREQ_CUTOFF.length];

            // now we need to get the classify signal from the FFT.
            // note: start from 1 because we don't care about the DC-level.
            for (int j = 1; j < X.length; j++) {
                float freqHz = (float) j / (float) X.length * nyquistFreq((float) SAMPLE_RATE);
                ///System.out.println("FREQ HERZ:" + freqHz);

                int k;
                for (k = 0; k < FREQ_CUTOFF.length; k++) {
                    if (freqHz < FREQ_CUTOFF[k]) {
                        break;
                    }
                }
                if (k == FREQ_CUTOFF.length) {
                    continue;
                }

                float absXj = (float) X[j].abs();
                ai.hitFeatVec[i][k] += absXj * absXj;
            }
        }

        // now we need to gaussian normalize the variables.

        float[] mean = new float[ai.hitFeatVec[0].length];
        float[] variance = new float[mean.length];
        for (float[] hit : ai.hitFeatVec) {
            for (int i = 0; i < mean.length; i++) {
                mean[i] += hit[i];
                variance[i] += hit[i] * hit[i];
            }
        }
        float N = (float) ai.hitFeatVec.length;
        for (int i = 0; i < variance.length; i++) {
            mean[i] /= N;
            variance[i] /= N;
            variance[i] -= mean[i] * mean[i];
        }

        for (float[] hit : ai.hitFeatVec) {
            for (int i = 0; i < mean.length; i++) {
                hit[i] = (hit[i] - mean[i]) / (float) Math.sqrt(variance[i]);
            }
        }

        ai.clusters = cluster(2, ai.hitFeatVec);
//		for (int i = 0; i < ai.clusters.length; i++) {
//			System.out.println("CLUSTER:" + i + " to " + ai.clusters[i]);
//		}
    }

    private int[] cluster(int N, float[][] X) {
        if (X.length == 1) {
            return new int[]{0};
        } else if (X.length == 0) {
            return new int[]{};
        }

        for (float[] Xi : X) {
            System.out.println("Feature: " + ToStringBuilder.reflectionToString(Xi));
        }

        int M = X[0].length;
        float minError = Float.MAX_VALUE;
        float[][] optMean = null;
        List<Set<Integer>> optClusters = null;

        Random rng = new Random(System.currentTimeMillis());

        /*
         * Set<Integer> ixs = new HashSet<>(); // initial set. for (int i = 0; i
         * < N; i++) { // there's probably a better way... int ix =
         * rng.nextInt(X.length); while (ixs.contains(ix)) ix =
         * rng.nextInt(X.length);
         *
         * ixs.add(ix); mean[i] = X[ix]; }
         */

        for (int run = 0; run < 5000; run++) {
//			System.out.println("Run:" + run);
            float[][] mean = new float[N][M];

            for (int i = 0; i < N; i++) {
                for (int k = 0; k < M; k++) {
                    mean[i][k] = (float) rng.nextGaussian();
                }
//				printVec("Random Mean[" + i + "]=", mean[i]);
            }

            List<Set<Integer>> prevClusters;
            List<Set<Integer>> currClusters = new ArrayList<>();

            float prevError;
            float currError = Float.MAX_VALUE;
            do {
                prevClusters = currClusters;
                currClusters = new ArrayList<>();
                prevError = currError;

                for (int i = 0; i < N; i++) {
                    currClusters.add(new HashSet<Integer>());
                }

                for (int i = 0; i < X.length; i++) {
                    int minIx = -1;
                    float min = Float.MAX_VALUE;
                    for (int cluster = 0; cluster < N; cluster++) {
                        float dist = 0f;
                        for (int k = 0; k < M; k++) {
                            float d = (float) X[i][k] - mean[cluster][k];
                            dist += d * d;
                        }
//						System.out.printf("Feature:%d, Cluster:%d, Distance: %f\n", i, cluster, dist);
                        if (dist < min) {
                            min = dist;
                            minIx = cluster;
                        }
                    }
                    assert minIx > 0 && min < Float.MIN_VALUE && min >= 0;
//					System.out.println("Adding feature " + i + " to cluster " + minIx);
                    currClusters.get(minIx).add(i);
                }

                int n = 0;
                for (int i = 0; i < N; i++) {
                    if (!currClusters.get(i).isEmpty()) {
                        n++;
                    }
                }
                if (n < N) {
//					System.out.println("All features have been assigned to one cluster: skipping.");
                    continue;
                }

                float totError = 0;
                for (int cluster = 0; cluster < N; cluster++) {
                    for (int i = 0; i < M; i++) {
                        mean[cluster][i] = 0;
                    }
//					System.out.println("Updating means:cluster:" + cluster + ":" + currClusters.get(cluster));
//					printVec("Mean for cluster " + cluster + ":", mean[cluster]);
                    float S = currClusters.get(cluster).size();
                    for (int i : currClusters.get(cluster)) {
                        for (int k = 0; k < M; k++) {
                            mean[cluster][k] += X[i][k] / S;
                        }
                    }
//					printVec("Mean for cluster " + cluster + ":", mean[cluster]);
                }

                for (int cluster = 0; cluster < N; cluster++) {
                    for (int i : currClusters.get(cluster)) {
                        for (int k = 0; k < M; k++) {
                            float d = (float) X[i][k] - mean[cluster][k];
                            totError += d * d;
                        }
                    }
                }

                currError = totError;

                if (currError < minError) {
                    minError = currError;
                    optClusters = currClusters;
                    optMean = mean;

//					System.out.println("Fount new minimum:" + optClusters);
//					System.out.println("Fount new minimum:" + minError);
//					for (int k = 0; k < N; k++)
//						printVec("Found new mean minimum:", optMean[k]);
                }
//				System.out.println(run + ":" + prevError + ":" + currError +":" + minError);
//				for (int i = 0; i < N; i++) {
//					printVec("Mean[" + i + "]=", mean[i]);
//				}
            } while (prevError - currError > 0.0001);
        }

        // order the hits by decreasing high-frequency content (should give us the kick as 0).
        int[] order = new int[N];
        float[] hf = new float[N];
        for (int i = 0; i < N; i++) {
            order[i] = i;
            float hfi = 0;
            for (int k = 0; k < M; k++) {
                hfi += (float) k / (float) M * optMean[i][k];
            }
            hf[i] = hfi;
        }

        for (int i = 0; i < N; i++) {
            for (int j = i; j < N; j++) {
                if (hf[order[i]] < hf[order[j]]) {
                    int tmp = order[j];
                    order[j] = order[i];
                    order[i] = tmp;
                }
            }
        }

        int[] result = new int[X.length];
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < N; j++) {
                if (optClusters.contains(i)) {
                    result[i] = order[j];
                    break;
                }
            }
        }

        System.out.println(optClusters);

        return result;
    }

    private void detectOnsets(AnalyzeInfo ai) {
        ai.avgScore = new float[ai.stftFrameSamplePos.length];
        ai.medianScore = new float[ai.stftFrameSamplePos.length];
        ai.score = new float[ai.stftFrameSamplePos.length];

        for (int i = 0; i < ai.stftFrameSamplePos.length; i++) {
            float localEnergyScore = ai.localEnergyScore[ai.stftFrameSamplePos[i]];
            float broadbandEnergyScore = ai.broadbandScore[i];
            float spectralDifferenceScore = ai.spectralDiffScore[i];

            /*
             * System.out.println("les:" + localEnergyScore);
             * System.out.println("bes:" + broadbandEnergyScore);
             * System.out.println("sds:" + spectralDifferenceScore);
             */

            float average = localEnergyScore + broadbandEnergyScore + spectralDifferenceScore;
            average /= 3;

            //System.out.println("Average:" + average);

            if (Float.isNaN(average)) {
                throw new IllegalStateException();
            }

            float median = median(localEnergyScore, broadbandEnergyScore, spectralDifferenceScore);

            ai.avgScore[i] = average;
            ai.medianScore[i] = median;

            float score = Math.min(average, median);
            ai.score[i] = score;
        }

        lpxma(0.8f, ai.score);

        normalize(ai.avgScore);
        normalize(ai.medianScore);
        normalize(ai.score);

        printVec("Average score:", ai.avgScore);
        printVec("Median score:", ai.medianScore);
        printVec("Score:", ai.score);

        int[] tmpOnsets = new int[MAX_NUM_ONSETS];
        int[] tmpOnsetEnds = new int[MAX_NUM_ONSETS];
        ai.detectFn = new float[ai.stftFrameSamplePos.length];
        int numOnsets = 0;
        int i = 0;
        while (i < ai.stftFrameSamplePos.length) {
            float score = ai.score[i];
            if (score > THRESHOLD) {
                System.out.println("Potential hit at " + i);
                // we have a hit.
                int start = i;
                while (i < ai.score.length && ai.score[i] > THRESHOLD) {
                    i++;
                }
                int end = i;
                if (end - start > MIN_HIT_LENGTH) {
                    System.out.println("Hit: " + start + ":" + end);
                    tmpOnsets[numOnsets] = start;
                    tmpOnsetEnds[numOnsets] = end - 1;
                    for (int j = start; j < end; j++) {
                        ai.detectFn[j] = 1;
                    }
                    ++numOnsets;
                    if (numOnsets > MAX_NUM_ONSETS) {
                        throw new IllegalStateException();
                    }
                } else {
                    System.out.println("False alarm.");
                    for (int j = start; j <= end; j++) {
                        ai.detectFn[j] = 0;
                    }
                }
            } else {
                ai.detectFn[i] = 0;
                i++;
            }
        }

        ai.onsets = new float[numOnsets][];
        ai.onsetStartSamples = new int[numOnsets];
        ai.onsetEndSamples = new int[numOnsets];
        ai.onsetSamples = new int[numOnsets];

        for (i = 0; i < numOnsets; i++) {
            ai.onsetStartSamples[i] = ai.stftFrameSamplePos[tmpOnsets[i]];
            ai.onsetEndSamples[i] = ai.stftFrameSamplePos[tmpOnsetEnds[i]];

            // find the maximum score the precise onset.
            ai.onsets[i] = new float[ai.onsetEndSamples[i] - ai.onsetStartSamples[i]];
            System.arraycopy(ai.signal, ai.onsetStartSamples[i],
                    ai.onsets[i], 0, ai.onsets[i].length);

            float[] energy = localEnergy(ai.onsets[i]);
            int onsetTime = ai.onsetStartSamples[i] + maxIx(energy);

            ai.onsetSamples[i] = onsetTime;
        }

        System.out.println("ONSETS:" + ToStringBuilder.reflectionToString(ai.onsetSamples));
    }

    private static float median(float... xs) {
        float[] tmp = new float[xs.length];
        System.arraycopy(xs, 0, tmp, 0, tmp.length);
        Arrays.sort(tmp);
        return tmp[tmp.length / 2];
    }

    private static float[] localEnergy(float[] x) {
        float lambda = 0.95f;	// was hand-tuned.
        float[] xmaE = new float[x.length];

        xmaE[0] = x[0] * x[0];
        for (int i = 1; i < xmaE.length; i++) {
            xmaE[i] = (1f - lambda) * x[i] * x[i] + lambda * xmaE[i - 1];
        }

        float max = 0f;
        for (int i = 0; i < xmaE.length; i++) {
            if (xmaE[i] > max) {
                max = xmaE[i];
            }
        }

        for (int i = 0; i < xmaE.length; i++) {
            xmaE[i] /= max;
        }

        for (int i = 0; i < xmaE.length; i++) {
            xmaE[i] = (float) Math.log(xmaE[i]);
            if (xmaE[i] < -100.0) {
                xmaE[i] = 0;
            }
        }

        max = 0;
        float min = Float.MAX_VALUE;
        for (int i = 0; i < xmaE.length; i++) {
            if (xmaE[i] < min) {
                min = xmaE[i];
            } else if (xmaE[i] > max) {
                max = xmaE[i];
            }
        }

        for (int i = 0; i < xmaE.length; i++) {
            xmaE[i] = (xmaE[i] - min) / (max - min);
        }

        //printVec("xmaE:", xmaE);

        return xmaE;
    }

    private void computeLocalEnergyScore(AnalyzeInfo analyze) {
        // ok, now begins the real work.
        System.out.println("Analyzing (2)!!!!");

        // MPD 20120219: the fancy STFT broadband energy weighting
        // scheme does not yield appreciable advantages over the considerably
        // simpler XMA of instantaneous energy as an onset detection function,
        // at least for beatboxing.

        float[] xmaE = localEnergy(analyze.signal);

        float lambda = 0.90f;
        DetectHisto dh = new DetectHisto(lambda);
        DetectHisto ddh = new DetectHisto(lambda);

        float[] pdh = new float[xmaE.length];
        float[] pddh = new float[xmaE.length];

        int M = (int) SAMPLE_RATE / 10;

        for (int i = 0; i < xmaE.length; i++) {
            float d = xmaE[i];
            dh.add(d);
            pdh[i] = dh.percentileOf(d);

            float dd = 0f;
            if (i > M) {
                dd = d - xmaE[i - M];
                ddh.add(dd);
                pddh[i] = ddh.percentileOf(dd);
            } else {
                dd = d - xmaE[xmaE.length + i - M - 1];
                if (dd >= 0) {
                    ddh.add(dd);
                }
                pddh[i] = ddh.percentileOf(dd);
            }
        }

        analyze.pdh = pdh;
        analyze.pddh = pddh;
        analyze.detectHisto = dh;
        analyze.localEnergyScore = xmaE;
    }

    private AnalyzeInfo analyzeSTFT(AnalyzeInfo ai) {
        // ok, now begins the real work.
        System.out.println("Analyzing!!!!");


        // 1. compute the power spectrum of the signal.
        computePowerSpectrum(ai);

        // 2. for each hop time (each row of pwspec), we obtain compute the 
        // detection function, which will give more weight to the high-frequency
        // component of the power spectrum. this is apparently good for detecting
        // percussive onsets. 

        int N = ai.powerSpectrum.getRowDimension();
        ai.broadbandScore = new float[N];
        ai.stftFrameSamplePos = new int[N];

        for (int i = 0; i < ai.powerSpectrum.getRowDimension(); i++) {
            double[] freqToPower = ai.powerSpectrum.getRow(i);
            double[] freqToPowerOrig = new double[freqToPower.length];
            System.arraycopy(freqToPower, 0, freqToPowerOrig, 0, freqToPowerOrig.length);

            // weight the higher frequencies.
            double bbscore = 0.0;
            for (int j = 0; j < freqToPower.length; j++) {
                bbscore += (double) j / ((double) freqToPower.length) * freqToPowerOrig[j];
            }

            int samplePos = i * HOP_SIZE_SAMPLES; // + FRAME_SIZE/2;

            ai.broadbandScore[i] = (float) bbscore;
            ai.stftFrameSamplePos[i] = samplePos;
        }

        normalize(ai.stftTimeDomainLocalEnergy);
        normalize(ai.broadbandScore);
        dbNormalize(ai.broadbandScore);

        return ai;
    }

    /**
     * Compute the short-time fourier transform.
     */
    private void computePowerSpectrum(AnalyzeInfo analyze) {

        // let's make the buffers be:
        // 20ms chunks.
        // 20ms = 44100 * 20 / 1000 = 882 samples.
        // we can pad that out to 2048 samples (2^11)

        // second arg: -1 is FFT, 1 is inverse FFT.


        FastFourierTransformer fft = new FastFourierTransformer();

        // all sizes are a in samples.

        int hopSize = HOP_SIZE_SAMPLES;	// 10ms

        int numHops = analyze.signal.length / hopSize - 1;

        System.out.println("Num frames:" + numHops);

        double[] tmpbuf = new double[analyze.signal.length];
        double[] framebuf = new double[FRAME_SIZE];

        RealMatrix powSpec = new Array2DRowRealMatrix(numHops, FRAME_SIZE / 2);

        analyze.stftTimeDomainLocalEnergy = new float[numHops];
        analyze.stftLocalEnergy = new float[numHops];
        analyze.spectralDiffScore = new float[numHops];

        Complex[] prevZ = null;

        for (int hop = 0; hop < numHops; hop++) {
            //System.out.println("hop " + hop);
            // 1. copy the snippet of the full audio buffer into the temporary buffer.
            for (int i = 0; i < WINDOW_SIZE_SAMPLES; i++) {
                tmpbuf[i] = analyze.signal[i + hop * hopSize];
            }

            double locEnergy = 0.0;
            // 2. apply the Hann window.
            // Hann window: http://en.wikipedia.org/wiki/Window_function
            for (int i = 0; i < WINDOW_SIZE_SAMPLES; i++) {
                double window = 0.5 * (1 - Math.cos(2 * Math.PI * i / (WINDOW_SIZE_SAMPLES - 1)));
                tmpbuf[i] *= window;
                locEnergy += tmpbuf[i] * tmpbuf[i];
            }

            analyze.stftTimeDomainLocalEnergy[hop] = (float) locEnergy;

            // 3. Set up the framebuf (which will be sent to the FFT) 
            // the right half of the signal goes at the beginning
            // the left half at the end. in between zero-padding.
            // from: https://ccrma.stanford.edu/~jos/sasp/Practical_Computation_STFT.html

            for (int i = 0; i < FRAME_SIZE; i++) {
                if (i < WINDOW_SIZE_SAMPLES) {
                    framebuf[i] = tmpbuf[i];
                } else {
                    framebuf[i] = 0;
                }
            }

            // 4. apply the FFT.

            Complex[] Z = fft.transform(framebuf);
            double[] specCol = new double[FRAME_SIZE / 2];

            assert Z.length == specCol.length;

            // 5. extract the magnitudes (do we need to normalize??)
            double E_fft = 0.0;
            for (int i = 0; i < specCol.length; i++) {
                specCol[i] = Z[i].getReal() * Z[i].getReal() + Z[i].getImaginary() * Z[i].getImaginary();
                E_fft += specCol[i];
            }

            analyze.stftLocalEnergy[hop] = (float) E_fft;

            powSpec.setRow(hop, specCol);

            if (hop > 0) {
                for (int j = 0; j < specCol.length; j++) {
                    double tmp = Z[j].abs() - prevZ[j].abs();
                    if (tmp < 0) {
                        tmp = 0;
                    }
                    analyze.spectralDiffScore[hop] += tmp * tmp;
                }
            }

            prevZ = Z;
        }

        dbNormalize(analyze.spectralDiffScore);

        analyze.powerSpectrum = powSpec;
    }

    public static void lpxma(float decay, float[] y) {
        for (int i = 1; i < y.length; i++) {
            y[i] = decay * y[i] + (1 - decay) * y[i - 1];
        }
    }

    public static float[] xma(float decay, float[] x) {
        float[] y = new float[x.length];

        float xma = x[0];
        y[0] = xma;

        for (int i = 1; i < x.length; i++) {
            y[i] = decay * x[i] + (1 - decay) * y[i - 1];
        }

        return y;
    }

    public static float[] xmasq(float decay, float[] x) {
        float[] y = new float[x.length];

        float xma = x[0];
        y[0] = xma;

        for (int i = 1; i < x.length; i++) {
            y[i] = decay * x[i] * x[i] + (1 - decay) * y[i - 1];
        }

        return y;
    }

    public static void hpfilt(float freq, float[] x) {
        double v = Math.exp(-2.0 * Math.PI * freq / SAMPLE_RATE);
        double a0 = 1f - v;
        double b1 = -v;

        float tmp = x[0];
        for (int i = 1; i < x.length; i++) {
            float out = (float) (a0 * x[i] - b1 * tmp);
            tmp = out;
            x[i] -= out; // = in - out (i.e. signal - low-pass ==> high pass!)
        }
    }

    public static void lpfilt(float freq, float[] x) {
        float v = (float) Math.exp(-2.0 * Math.PI * freq / SAMPLE_RATE);
        float a0 = 1f - v;
        float b1 = -v;

        for (int i = 1; i < x.length; i++) {
            x[i] = a0 * x[i] - b1 * x[i - 1];
        }
    }

    public static void normalize(float[] x) {
        float max = absmax(x);
        for (int i = 0; i < x.length; i++) {
            x[i] /= max;
        }
    }

    public static float absmax(float[] x) {
        float max = 0;
        for (int i = 0; i < x.length; i++) {
            float absx = Math.abs(x[i]);
            if (absx > max) {
                max = absx;
            }
        }
        return max;
    }

    private static int maxIx(float... xs) {
        float max = -Float.MAX_VALUE;
        int maxIx = -1;
        for (int i = 0; i < xs.length; i++) {
            if (xs[i] > max) {
                max = xs[i];
                maxIx = i;
            }
        }
        return maxIx;
    }

    private static void dbNormalize(double[] x) {
        double min = Float.MAX_VALUE;
        double max = -Float.MAX_VALUE;
        for (int i = 0; i < x.length; i++) {
            double xi = Math.log(x[i]);
            if (xi > max) {
                max = xi;
            }
            if (xi < min) {
                min = xi;
            }
        }

        double D = max - min;

        for (int i = 0; i < x.length; i++) {
            x[i] = (x[i] - min) / D;
        }
    }

    private static void dbNormalize(float[] x) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < x.length; i++) {
            double xi = Math.log(x[i]);
            x[i] = (float) xi;

            if (!Double.isInfinite(xi) && !Double.isNaN(xi)) {
                if (xi > max) {
                    max = xi;
                } else if (xi < min) {
                    min = xi;
                }
            }
        }

        float D = (float) (max - min);

        for (int i = 0; i < x.length; i++) {
            float xi = x[i];
            if (Double.isInfinite(xi) && xi < 0) {
                x[i] = 0; // no
            } else if (Double.isNaN(xi)) {
                x[i] = 0;
            } else if (Double.isInfinite(xi)) {
                throw new IllegalStateException();
            } else {
                x[i] = (xi - (float) min) / D;
            }
        }
    }

    public static RealMatrix dbSpectrumNormalize(RealMatrix ps) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        double[][] rows = new double[ps.getRowDimension()][];

        for (int row = 0; row < ps.getRowDimension(); row++) {
            double[] x = ps.getRow(row);
            for (int i = 0; i < x.length; i++) {
                double xi = Math.log(x[i]);
                x[i] = (float) xi;
                if (xi > max) {
                    max = xi;
                }
                if (xi < min && !Double.isInfinite(xi) && !Double.isNaN(xi)) {
                    min = xi;
                }
            }
            rows[row] = x;
        }

        float D = (float) (max - min);

        for (int row = 0; row < ps.getRowDimension(); row++) {
            double[] x = rows[row];
            for (int i = 0; i < x.length; i++) {
                x[i] = (x[i] - (float) min) / D;
            }
        }

        return new Array2DRowRealMatrix(rows);
    }

    private static void printVec(String str, float[] vec) {
        System.out.println(str
                + ToStringBuilder.reflectionToString(vec, ToStringStyle.NO_FIELD_NAMES_STYLE));
    }
}
