/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio.msg;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author Martin Percossi
 */
public class MessageWriter implements Closeable {
    
    private final OutputStream out;
    
    public MessageWriter(OutputStream out) {
        this.out = out;
    }
    
    public void writeMsg(Message msg) throws IOException {
        out.write(msg.pack());
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
    
}
