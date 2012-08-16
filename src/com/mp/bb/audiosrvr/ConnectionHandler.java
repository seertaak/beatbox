/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audiosrvr;

import com.mp.bb.audio.msg.Message;
import com.mp.bb.audio.msg.MessageReader;
import java.io.IOException;
import java.net.Socket;

/**
 *
 * @author Martin Percossi
 */
public class ConnectionHandler implements Runnable {
    
    private final Socket socket;
    private final AudioServer server;

    ConnectionHandler(Socket socket, AudioServer server) {
        this.server = server;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (MessageReader in = new MessageReader(socket.getInputStream())) {
            Message msg;
            while ((msg = in.readMsg()) != null) {
                System.out.println("Server received: " + msg);
                
                server.inbox().offer(msg);
                server.outbox().offer(msg);
            }
        } catch (IOException ex) {
            throw new AudioServerException(ex);
        }
    }
    
}
