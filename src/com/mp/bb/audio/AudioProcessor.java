package com.mp.bb.audio;

import com.mp.bb.audio.msg.Message;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Queue;

/**
 * This is the interface for units that can generate and process audio or 
 * messages. As such, it details only the obligations of the 
 * <code>AudioProcessor</code>.
 * To get a complete view, one must also consider the obligations of the 
 * <code>AudioHost</code>.
 * @author Martin Percossi
 */
public interface AudioProcessor extends Serializable {
    /**
     * Return the ID of the audio processor within the processor graph.
     */
    int id();
    void id(int id);
    

    void process(float[][] input, float[][] output, int nSamples,
            Queue<Message> inbox, Queue<Message> outbox);
    
    void init(AudioHost host, int id);
    
    void release();
}
