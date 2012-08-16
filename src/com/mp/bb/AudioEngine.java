package com.mp.bb;

import java.util.LinkedList;
import java.util.List;

import javafx.application.Platform;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jrtaudio.DeviceInfo;
import org.jrtaudio.JRtAudio;
import org.jrtaudio.StreamOptions;
import org.jrtaudio.StreamParameters;

import com.mp.bb.BeatAnalyzer.AnalyzeInfo;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class AudioEngine {
    
    public static int BUF_SIZE = 1 << 22; // 4MB
    private static float[] buffer = new float[BUF_SIZE];
    public static double SAMPLE_RATE = 44100.0;
    private static float[] WHITE_NOISE_HAT = new float[(int) SAMPLE_RATE / 20];
    
    static {
        for (int i = 0; i < WHITE_NOISE_HAT.length; i++) {
            float x = (1.f - (float) i / (float) WHITE_NOISE_HAT.length);
            WHITE_NOISE_HAT[i] = 0.5f * ((float) Math.random() - 0.5f) * x * x;
        }
    }
    private volatile State state;
    private volatile double bpm;
    private volatile double time;
    private volatile JRtAudio audio;
    private volatile boolean click;
    private volatile int samplePos;
    private volatile int loopLengthBeats;
    private int bufPos;
    private List<AudioEngineListener> listeners;
    private int countinBeatsRemaining;
    private int recStartBeat;
    private int bufferLength;
    private int latency;
    private BeatAnalyzer analyzer;
    
    public AudioEngine() {
        state = State.INIT;
        bpm = 120;
        time = 0;
        click = false;
        samplePos = 0;
        bufPos = 0;
        listeners = new LinkedList<>();
        loopLengthBeats = 4;
    }
    
    public AudioEngine loopLengthBeats(int beats) {
        this.loopLengthBeats = beats;
        return this;
    }
    
    public AudioEngine init() {
        analyzer = new BeatAnalyzer(this);
        
        Thread analyzerThread = new Thread(analyzer);
        analyzerThread.setDaemon(true);
        analyzerThread.start();
        
        audio = new JRtAudio();
        audio.showWarnings();
        
        for (int i = 0; i < audio.getDeviceCount(); i++) {
            System.out.println("Device " + i + ":"
                    + ToStringBuilder.reflectionToString(
                    audio.getDeviceInfo(i),
                    ToStringStyle.SHORT_PREFIX_STYLE));
        }
        
        int out = audio.getDefaultOutputDevice();
        int in = audio.getDefaultInputDevice();

        DeviceInfo devin = audio.getDeviceInfo(in);
        System.out.println("Input device: "
                + ToStringBuilder.reflectionToString(devin,
                ToStringStyle.SHORT_PREFIX_STYLE));
        StreamParameters inParams = new StreamParameters();
        inParams.nChannels = devin.inputChannels;
        inParams.deviceId = in;
        
        DeviceInfo devout = audio.getDeviceInfo(out);
        System.out.println("Output device: "
                + ToStringBuilder.reflectionToString(devout,
                ToStringStyle.SHORT_PREFIX_STYLE));
        StreamParameters oParams = new StreamParameters();
        oParams.nChannels = devout.outputChannels;
        oParams.deviceId = out;
        StreamOptions streamOptions = new StreamOptions();
        streamOptions.flags = 0x2 /* minimize latency */
                | /* schedule realtime */ 0x8 | 0x4; 
        
        audio.openStream(oParams, inParams, DeviceInfo.RTAUDIO_FLOAT32, 44100, 
                512, this, streamOptions);
        audio.startStream();
        latency = 2 * 512;
        System.out.println("Latency: " + latency);
        
        return this;
    }
    
    public AudioEngine bpm(double bpm) {
        this.bpm = bpm;
        return this;
    }
    
    public double bpm() {
        return bpm;
    }
    
    public AudioEngine time(double time) {
        this.time = time;
        return this;
    }
    
    public AudioEngine state(State state) {
        if (this.state == State.INIT && state == State.COUNTIN) {
            countinBeatsRemaining = 8;
            click(true);
        } else {
            throw new UnsupportedOperationException(
                    "Illegal audio state transition.");
        }
        this.state = state;
        return this;
    }
    
    public AudioEngine click(boolean click) {
        this.click = click;
        return this;
    }
    
    public void toggleClick() {
        click = !click;
    }
    
    public boolean click() {
        return click;
    }
    
    public double time() {
        return time;
    }
    
    private float[] outputFloatArray = new float[1024];
    private float[] inputFloatArray = new float[1024];
    
    public int callback(FloatBuffer input, FloatBuffer output,
            int N, double time, int status) {
        input.rewind();
        for (int i = 0; i < N; i++) {
            inputFloatArray[i] = input.get();
            inputFloatArray[i+N] = input.get();
        }
            
        int result = callback(outputFloatArray, inputFloatArray, N,
                time, status);
    
        output.rewind();
        for (int i = 0; i < N*2; i++) {
            output.put(outputFloatArray[i]);
            output.put(outputFloatArray[N+i]);
        }
        
        return result;
    }
    
    public int callback(float[] outputBuffer, float[] inputBuffer,
            int nBufferFrames, double streamTime, int status) {
        for (int i = 0; i < nBufferFrames; i++) {
            outputBuffer[i] = 0f;
            outputBuffer[i + nBufferFrames] = 0f;
        }
        int beat = (int) samplesToBeats(samplePos);
        int beatStart = (int) beatsToSamples(beat);
        int nextBeatStart = (int) beatsToSamples(beat + 1);
        int clickEnd = beatStart + WHITE_NOISE_HAT.length;
        /*
         * System.out.println("Beat : " + beat); System.out.println("Beat Start
         * : " + beatStart); System.out.println("Click Edn : " + clickEnd);
         * System.out.println("Output: " + outputBuffer.length + " samples");
         * System.out.println("Input: " + inputBuffer.length + " samples");
         * System.out.println("Stream Time: " + streamTime);
         * System.out.println("nBufferFrames: " + nBufferFrames);
         * System.out.println("status: " + status);
         */
        if (click) {
            if (samplePos < clickEnd) {
                for (int i = 0; i < nBufferFrames; i++) {
                    int index = i + samplePos - beatStart;
                    if (index >= 0 && index < WHITE_NOISE_HAT.length) {
                        outputBuffer[i] += WHITE_NOISE_HAT[index];
                        outputBuffer[i + nBufferFrames] += WHITE_NOISE_HAT[index];
                    }
                }
            } else if (samplePos + nBufferFrames > nextBeatStart) {
                for (int i = 0; i < nBufferFrames; i++) {
                    int index = i + samplePos - nextBeatStart;
                    if (index >= 0 && index < WHITE_NOISE_HAT.length) {
                        outputBuffer[i] += WHITE_NOISE_HAT[index];
                        outputBuffer[i + nBufferFrames] += WHITE_NOISE_HAT[index];
                    }
                }
            }
        }
        int N;
        switch (state) {
            case INIT:
                // apart from the above, do nothing.
                break;
            case COUNTIN:
                if (nextBeatStart - latency >= samplePos
                        && nextBeatStart - latency < samplePos + nBufferFrames) {
                    if (--countinBeatsRemaining > 0) {
                        for (AudioEngineListener listener : listeners) {
                            listener.onClick(countinBeatsRemaining);
                        }
                    } else {
                        for (AudioEngineListener listener : listeners) {
                            listener.onRecord();
                        }
                        state = State.RECORDING;
                        recStartBeat = beat;
                        // record beginning bit
                        int ix = nextBeatStart - samplePos - latency;
                        N = nBufferFrames - ix;
                        System.arraycopy(inputBuffer, ix, buffer, 0, N);
                        bufPos += N;
                    }
                }
                break;
            case RECORDING:
                int recEndSamples = (int) beatsToSamples(recStartBeat
                        + loopLengthBeats + 1)
                        - latency;
                N = Math.min(nBufferFrames, recEndSamples - samplePos);
                System.arraycopy(inputBuffer, 0, buffer, bufPos, N);
                if (N < nBufferFrames) {
                    state = State.ANALYZING;
                    bufferLength = bufPos + N;
                    
                    float[] tmp = new float[bufferLength];
                    System.arraycopy(buffer, 0, tmp, 0, bufferLength);
                    BeatAnalyzer.hpfilt(300f, tmp);
                    BeatAnalyzer.lpfilt(10000f, tmp);
                    System.arraycopy(tmp, 0, buffer, 0, bufferLength);

                    // System.out.println("BUFFER LENGTH: " + bufferLength);

                    for (AudioEngineListener listener : listeners) {
                        listener.onAnalyze();
                    }
                    
                    synchronized (analyzer) {
                        analyzer.notify();
                    }
                    
                    for (int i = 0; i < nBufferFrames - N; i++) {
                        outputBuffer[i + N] += buffer[bufPos + i];
                    }
                    for (int i = 0; i < N; i++) {
                        outputBuffer[i + N + nBufferFrames] += buffer[bufPos + i];
                    }
                    
                    bufPos = nBufferFrames - N;
                } else {
                    bufPos += N;
                }
                break;
            case ANALYZING:
            case REFINING:
            case PLAYING:
                while (bufPos >= bufferLength) {
                    bufPos -= bufferLength;
                }
                
                for (int i = 0; i < nBufferFrames; i++) {
                    outputBuffer[i] += buffer[(bufPos + i) % bufferLength];
                }
                for (int i = 0; i < nBufferFrames; i++) {
                    outputBuffer[i + nBufferFrames] += buffer[(bufPos + i)
                            % bufferLength];
                }
                
                bufPos += nBufferFrames;
                
                break;
            case STOPPED:
                break;
        }
        
        samplePos += nBufferFrames;
        
        return 0;
    }
    
    private double beatsToSamples(double beats) {
        return beats * SAMPLE_RATE / bpm * 60.0;
    }
    
    private double samplesToBeats(int spos) {
        return spos / SAMPLE_RATE * bpm / 60.0;
    }
    
    public synchronized void registerCountinListener(
            AudioEngineListener listener) {
        listeners.add(listener);
    }
    
    public void setLoopLengthBeats(int beats) {
        loopLengthBeats = beats;
    }
    
    public float[] loop() {
        return buffer;
    }
    
    public int loopLength() {
        return bufferLength;
    }
    
    public void doneAnalyzing(AnalyzeInfo result) {
        final AnalyzeInfo fres = result;
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                for (AudioEngineListener listener : listeners) {
                    listener.doneAnalyzing(fres);
                }
            }
        });
    }
}
