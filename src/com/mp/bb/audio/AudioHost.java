/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio;

import com.mp.bb.audio.msg.Message;

/**
 * This interface details the services provided by an audio host. Compare
 * to <code>AudioProcessor</code>.
 *
 * @author Martin Percossi
 */
public interface AudioHost {
    
    double bpm();
    AudioHost bpm(double bpm);
    
    double time();
    AudioHost time(double time);
    
    boolean playing();
    AudioHost play();
    AudioHost stop();
    
    int samplePos();
    int frame();
    double sampleRate();
    int frameSize();
    
    AudioHost sendMsg(Message msg);
}
