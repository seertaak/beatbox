/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio.plugin;

import com.mp.bb.audio.msg.Message;
import java.util.Queue;

/**
 *
 * @author Martin Percossi
 */
public class GainPlugin extends AudioPlugin {

    @PluginProperty
    private float gain = 1;
    
//    @Override
//    public void process(float[][] input, float[][] output, int numSamples) {
//        for (int chan = 0; chan < input.length; chan++)
//            for (int i = 0; i < numSamples; i++) 
//                output[chan][i] = gain * input[chan][i];
//    }

    @Override
    public void process(float[][] input, float[][] output, int nSamples, Queue<Message> inbox, Queue<Message> outbox) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
