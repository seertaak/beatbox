/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audiosrvr;

import com.mp.bb.audio.AudioHost;
import com.mp.bb.audio.AudioProcessor;
import com.mp.bb.audio.Connection;
import com.mp.bb.audio.msg.Message;
import com.mp.bb.audio.msg.MessageReconstructor;
import com.mp.bb.audio.msg.MessageRingBuffer;
import java.util.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jrtaudio.DeviceInfo;
import org.jrtaudio.JRtAudio;
import org.jrtaudio.StreamParameters;

/**
 *
 * @author Martin Percossi
 */
public class ServerAudioEngine implements AudioHost {
    public static final int PROC_NS_HASHMAP_ARRAY_SIZE = 4096;
    
    private static class FastPriorityQueue {
        private static int N = 1024;
        private static int MASK = N-1;
        
        private double[] times;
        private long[][] buffers;
        private int[] limits;

        public FastPriorityQueue() {
            times = new double[N];
            buffers = new long[N][MessageRingBuffer.POS_BUF_SIZE];
            limits = new int[N];
        }
        
        public void offer(long cursor, double time) {
            
            int ix = Arrays.binarySearch(times, time);
            if (ix >= 0) {
                
            } else {
                int insertIx = -ix-1;
                
            }
        }
    }
    
    private double bpm;
    private double time;
    private int samplePos;
    private boolean playing;
    private int latency;
    private int frameSize;
    private double sampleRate;
    
    private SortedSet<Long> timeOrderedMessageIxs;
    private long lastMsgSeen;
    
    private Map<String, AudioProcessor> procNamespace;
    private DirectedAcyclicGraph<AudioProcessor, Connection> procGraph;
    
    private JRtAudio audio;
    private AudioServer server;
    
    private Map<Double, Queue<Long>> timeToMsgs;
    private FastPriorityQueue msgPriorityQueue;
    
    
    private MessageReconstructor msg;
    private MessageReconstructor msg2;
    
    public ServerAudioEngine(AudioServer server) {
        bpm = 120;
        time = 0;
        samplePos = 0;
        playing = false;
        latency = 0;
        frameSize = 512;
        sampleRate = 44100;
        timeToMsgs = new TreeMap<>();
        
        msg = new MessageReconstructor();
        msg2 = new MessageReconstructor();
        this.server = server;
        
        final MessageRingBuffer rbuf = server.getMessageRingBuffer();
                
        timeOrderedMessageIxs = new TreeSet<>(new Comparator<Long>() {
            
            private MessageReconstructor x = new MessageReconstructor();
            private MessageReconstructor y = new MessageReconstructor();
            @Override
            public int compare(Long l, Long r) {
               rbuf.reconstruct(l, x);
               rbuf.reconstruct(r, y);
               
               double diff = y.time() - y.time();
               
               x.clear();
               y.clear();
               
               if (Math.abs(diff) < 1e-6) {
                   return 0;
               } else if (diff < 0) {
                   return -1;
               } else {
                   return 1;
               }
            }
        });
    }
    
    public void init() {
        audio = new JRtAudio();
        audio.showWarnings();
        
        procNamespace = new HashMap<>(PROC_NS_HASHMAP_ARRAY_SIZE);
        
        for (int i = 0; i < audio.getDeviceCount(); i++) {
            System.out.println("Device " + i + ":"
                    + ToStringBuilder.reflectionToString(
                    audio.getDeviceInfo(i),
                    ToStringStyle.SHORT_PREFIX_STYLE));
        }
        
        int out = 2; //HACK! was: audio.getDefaultOutputDevice();
        int in = 2; //audio.getDefaultInputDevice();

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
        
        audio.openStream(oParams, inParams, (int) sampleRate, frameSize, this);
        audio.startStream();
        latency = 2 * frameSize;    // highly suspect.
        //System.out.println("Latency: " + latency);
    }
    
    @Override
    public ServerAudioEngine bpm(double bpm) {
        this.bpm = bpm;
        return this;
    }
    
    @Override
    public double bpm() {
        return bpm;
    }
    
    @Override
    public ServerAudioEngine time(double time) {
        this.time = time;
        return this;
    }
    
    @Override
    public double time() {
        return time;
    }
    
    public void fakeCallback() {
        MessageRingBuffer inbox = server.getMessageRingBuffer();
        
        
    }
    
    public int callback(float[] outputBuffer, float[] inputBuffer,
            int nBufferFrames, double streamTime, int status) 
    {
        time = samplesToBeats(samplePos);
        double frameEnd = samplesToBeats(samplePos + nBufferFrames);
        
        MessageRingBuffer inbox = server.getMessageRingBuffer();
        
        // now we need to zip through the inbox, adding new elements
        // to our sorted structure.
        
        inbox.getNewMsgs(lastMsgSeen, timeOrderedMessageIxs);
        
        //inbox.peek(null)

        inbox.peek(msg);      
        
        
        samplePos += nBufferFrames;
        
        return 0;
    }

    public int callbackOld(float[] outputBuffer, float[] inputBuffer,
            int nBufferFrames, double streamTime, int status) 
    {
        time = samplesToBeats(samplePos);
        double frameEnd = samplesToBeats(samplePos + nBufferFrames);
        
        Message msg = server.inbox().peek();
        if (msg != null && (Double.isNaN(msg.time()) || 
                (msg.time() >= time && msg.time() < frameEnd))) 
        {
            // this message occurs within our frame, so we need to dispatch it.
            dispatch(msg);
            
            server.inbox().remove();
        }
        
        samplePos += nBufferFrames;
        
        return 0;
    }
    
    private double beatsToSamples(double beats) {
        return beats * sampleRate / bpm * 60.0;
    }
    
    private double samplesToBeats(int spos) {
        return (double) spos / sampleRate * bpm / 60.0;
    }

    private void dispatch(Message msg) {
        AudioProcessor target = procNamespace.get(msg.address());
        if (target != null) {
            //target.handleMsg(msg);
        }
    }

    @Override
    public boolean playing() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AudioHost play() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AudioHost stop() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int samplePos() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int frame() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double sampleRate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int frameSize() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AudioHost sendMsg(Message msg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    

}
