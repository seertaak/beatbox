/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audiosrvr;

import com.mp.bb.audio.msg.Message;
import com.mp.bb.audio.msg.MessageWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 *
 * @author Martin Percossi
 */
public class ClientTest {

    public static void main(String... args) throws Exception {
        Socket socket = new Socket((String) null /*
                 * loopback
                 */,
                AudioServer.AUDIO_SERVER_PORT);
        byte[] foo = new byte[]{0, 4, 2};
        
        Message.setGuidSequence(1000000);
        
        int id = 0;

        try (MessageWriter mw = new MessageWriter(socket.getOutputStream())) {
            while (true) {
                Message msg = Message.to("/a")
                        .guid(id++)
                        .at(4)
                        .addArg(Float.valueOf((float) Math.random()))
                        .addArg(foo)
                        .addArg("foo")
                        .addArg(Integer.valueOf(
                            (int) System.currentTimeMillis()));

                //System.out.println("Client: about to send message " + msg);
                mw.writeMsg(msg);

                Thread.sleep(1);
            }
        }
    }
}
