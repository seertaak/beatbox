/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audiosrvr;

import com.mp.bb.audio.msg.Message;
import com.mp.bb.audio.msg.MessageRingBuffer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Martin Percossi
 */
public class AudioServer {
    
    public static final int AUDIO_SERVER_PORT = 4242;
    
    /* Thing is, we shouldn't really be creating any garbage at all.
     * We need to check if it's not better to 
     */
    private ConcurrentLinkedQueue<Message> inMsgs 
            = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Message> outMsgs
           = new ConcurrentLinkedQueue<>();
    
    private ServerAudioEngine engine;

    private ExecutorService connectionPool;
    private ServerSocket serverSocket;
    private MessageRingBuffer clientToServerMessages;
    
    public AudioServer() {
        connectionPool = Executors.newCachedThreadPool();
        engine = new ServerAudioEngine(this);
        clientToServerMessages = new MessageRingBuffer();
    }
    
    public void start() {
        engine.init();
        
        try {
            serverSocket = new ServerSocket(AUDIO_SERVER_PORT);
        } catch (IOException ex) {
            throw new AudioServerException(ex);
        }
        
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                connectionPool.execute(new ConnectionHandler(socket, this));
            } catch (IOException ex) {
                throw new AudioServerException(ex);
            }
        }
    }
    
    
    /*
     * 
     * The Design
     * ==========
     * 
     * Audio Server
     * ------------
     * 
     * We need to provide these basic services:
     * /adevice
     *   - querying for a list of audio devices
     *   - selection of audio device.
     *   - buffer size, samples size, etc.
     * /transport
     *   - play (receives play start, end, and boolean if looping)
     *   - stop
     *   - bpm
     *   - time signature
     * /agraph (audio graph)
     *   - add/delete anode
     *   - add/delte aconnection
     * /timesync
     *   - gets sent from the server to clients with the current audio
     *     time.
     *   - set rate (i.e. how many times per second we send a time sync message)
     * 
     * One way we can do that is by reserving those names in the global 
     * namespace; then we can simply communicate with Messages.
     * 
     * In addition, we needs to route any incoming messages to the appropriate
     * node, at the appropriate time (we need to slot it into the correct
     * audio frame, unless it has time NaN, in which case it's immediate).
     * 
     * Also, any incoming messages need echoed back out to all the clients.
     * 
     * On the graph side, we need to be able to construct graphs of audio 
     * nodes (processors). Ideally, we should be able to swap in/out audio
     * with minimal impact on already playing/recording audio. Hotswap!
     * 
     * We need to provide a framework to build Nodes easily and error-free.
     *   - need to support asynchronous update (from GUI)
     *   - can itself generate updates.
     *   - and all of this needs to be done without any locks on the audio
     *     thread.
     *   - key concept: bind together a Message argument with a member value.
     * 
     * e.g.
     * 
     * @Plugin(type="Filter",name="Butterworth Filter", minIns=1, minOuts=1)
     * public class BFilter implements AudioNode {
     *     
     * 
     *     @Parameter // this/cutoff is bound to this guy now.
     *     private float cutoff;
     *     @Parameter
     *     private float resonance;
     *     // these can be changed by external forces (GUI or Score) 
     *     // and in turn we can change them and notify GUI and Score of
     *     // the change (later!). All of that is transparent to the writer,
     *     // who just uses those variables as normal.
     *     
     *     @Action // bound to - this/reset (which has no arguments or impulse)
     *     public void reset() {
     *        // set cutoff and resonance back to factory defaults, propagating
     *        // the change.
     *     }
     *     @Action  // could access via this/cutoffAndRes (with first 
     *              // arg cutoff and second resonance!)
     *     public void cutoffAndResonance(float cutoff, float resonance) {
     *        
     *     }
     *     // fooFudge isn't serialized!
     *     transient float fooFudge = cutoff/resonance;
     * 
     *     @Override
     *     private void initialize(int nIns, int nOuts, int frameSize) { ... }
     *     @Override
     *     private void process(float[][] audioIn, float[][] audioOut, 
     *       MessageBuffer msgIn, MessageBuffer msgOut)
     *     { ... }
     *     @Override
     *     private void release() { }
     *     
     *      
     *     // above we see how we can bind methods of the class
     * 
     * }
     * 
     * 
     * 
     *  
     */
    
    public static void main(String...args) {
        AudioServer server = new AudioServer();
        server.start();
    }

    public ConcurrentLinkedQueue<Message> inbox() {
        return inMsgs;
    }
    
    public ConcurrentLinkedQueue<Message> outbox() {
        return outMsgs;
    }

    MessageRingBuffer getMessageRingBuffer() {
        return clientToServerMessages;
    }
    
}
