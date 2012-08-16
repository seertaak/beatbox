/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audiosrvr;

import com.mp.bb.audio.msg.Message;
import com.mp.bb.audio.msg.MessageReader;
import com.mp.bb.audio.msg.MessageRingBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 *
 * @author Martin Percossi
 */
public class ConnectionHandlerFast implements Runnable {
    
    private final Socket socket;
    private final AudioServer server;
    private final MessageRingBuffer msgBox;

    ConnectionHandlerFast(Socket socket, AudioServer server) {
        this.server = server;
        this.socket = socket;
        this.msgBox = server.getMessageRingBuffer();
    }

    @Override
    public void run() {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) 
        {
            msgBox.streamingPut(in, out);
        } catch (IOException ex) {
            throw new AudioServerException(ex);
        }
    }
    
}
