/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio.msg;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A <code>MessageReader</code> is a (relatively) inefficient way
 * of reading messages, suitable for offline use. Uses 
 * <code>Message.unpack()</code> to populate the high-level fields in
 * the <code>Message</code> that is returned by 
 * <code>MessageReader.readMsg()</code>. 
 * 
 * @author Martin Percossi
 */
public class MessageReader implements Closeable {
    public static final int BUF_SIZE = 4096;
    
    private final InputStream in;
    private transient byte[] buffer;
    
    public MessageReader(InputStream in) {
        this.in = in;
        this.buffer = new byte[BUF_SIZE];
    }
    
    public final Message readMsg() throws IOException {
        // first, read the size.
        int pos = in.read(buffer, 0, 4);
        if (pos < 0) {
            return null;
        }
        int n = ByteBuffer.wrap(buffer, 0, 4).getInt();
        System.out.println("Server: expecting buffer size: " + n);
        while (pos < n)
            pos += in.read(buffer, pos, n-4);
        assert pos > 0;
        return Message.unpack(buffer);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
