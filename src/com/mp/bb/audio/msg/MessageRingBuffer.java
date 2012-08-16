/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio.msg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A <code>MessageReader</code> reads data from an <code>InputStream</code>
 * and stores it in an intermediate ring buffer, from which it is possible
 * to reconstruct (in an efficient manner) the message. 
 * 
 * @author Martin Percossi
 */
public class MessageRingBuffer {
    
    public static final int MSG_BUF_SIZE = 1 << 28; // 256MB enough for 65536 msgs
    public static final int POS_BUF_SIZE = 1 << 16; // enough for at least 65536 messages
    public static final int MAX_MSG_SIZE = 1 << 12; // 4K max message size.
    
    private static final int MSG_BUF_SIZE_MINUS_1 = MSG_BUF_SIZE - 1;
    
    private final ThreadLocal<byte[]> intbuf = new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[4];
        }
    };
    
    private byte[] msgbuf;
    
    private AtomicLong bufEnd;
    private volatile long bufCommit;
    private volatile long bufStart;

    public MessageRingBuffer() {
        this.msgbuf = new byte[MSG_BUF_SIZE+MAX_MSG_SIZE];
        this.bufEnd = new AtomicLong(0);
        this.bufCommit = 0;
        this.bufStart = 0;
    }
    
    public void streamingPut(InputStream in, OutputStream out) 
        throws IOException 
    {
        byte[] data = intbuf.get();
        
        while (true) {
            int nread = in.read(data, 0, 4);            
            if (nread < 0)
                return;
            else if (nread != 4)
                throw new MessageException("Fuck.");

            int size = 
                (0xff & data[0]) << 24  |
                (0xff & data[1]) << 16  |
                (0xff & data[2]) << 8   |
                (0xff & data[3]);      

            // at this  point, we have all the information we need to atomically
            // claim the next chunk of memory!

            // what we get back from that claim is a starting point in the buffer
            // which we can use to place the data we're gonna read!
            
            long newBufEnd = bufEnd.getAndAdd(size);
            // below computes seqStart % MSG_BUF_SIZE fast.
            int start = (int) newBufEnd & MSG_BUF_SIZE_MINUS_1;
            long seqEnd = newBufEnd + size;
            
            while (seqEnd - bufStart > MSG_BUF_SIZE) {
                // TODO: probably what we want to do in this case is send
                // ignore a message and send a message back to the client
                // saying the message was ignored.
                
                // wait for the consumer to catch up. with the buffer size
                // we've set up, something must be seriously wrong if we've
                // gotten here; a combination of getting way too many messages
                // and processing them way too slowly.
                System.out.println("Problem.");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                }
            }
            
            System.arraycopy(data, 0, msgbuf, start, 4);
            do {
                nread += in.read(msgbuf, start+nread, size-nread);
            } while (nread != size);
            
            while (newBufEnd != bufCommit) {
                // NOP
            }
            bufCommit = newBufEnd;
        }
    }
    
    public void peek(MessageReconstructor msg) {
        msg.setBuffer(msgbuf, (int) bufStart & MSG_BUF_SIZE_MINUS_1);
    }
    
    public void reconstruct(long position, MessageReconstructor msg) {
        msg.setBuffer(msgbuf, (int) position & MSG_BUF_SIZE_MINUS_1);
    }
    
    public void getNewMsgs(long lastMsg, 
            SortedSet<Long> timeOrderedMessageIxs) 
    {
        while (lastMsg < bufStart) {
            int ix = (int) lastMsg & MSG_BUF_SIZE_MINUS_1;
            int size = 
                (0xff & msgbuf[ix])   << 24  |
                (0xff & msgbuf[ix+1]) << 16  |
                (0xff & msgbuf[ix+2]) << 8   |
                (0xff & msgbuf[ix+3]);
            
            long ltime = (long)(
            // (Below) convert to longs before shift because digits
            //         are lost with ints beyond the 32-bit limit
            (long)(0xff & msgbuf[0]) << 56  |
            (long)(0xff & msgbuf[1]) << 48  |
            (long)(0xff & msgbuf[2]) << 40  |
            (long)(0xff & msgbuf[3]) << 32  |
            (long)(0xff & msgbuf[4]) << 24  |
            (long)(0xff & msgbuf[5]) << 16  |
            (long)(0xff & msgbuf[6]) << 8   |
            (long)(0xff & msgbuf[7]) << 0);

            double time = Double.longBitsToDouble(ltime);
            
            
            lastMsg += size;
        }
    }
    
    public void eat() {
        PriorityQueue<Integer> x=null;
    }
    
    public int claimChunk(int msgSize) {
        
        return 0;
    }

    
    public static void main(String...args) {
        byte[] data = new byte[1024];
        
        ByteBuffer bbuf = ByteBuffer.wrap(data);
        
        bbuf.putFloat(41245635f);

        int reconstructed =
            (0xff & data[0]) << 24  |
            (0xff & data[1]) << 16  |
            (0xff & data[2]) << 8   |
            (0xff & data[3]);      
        
        System.out.println(Float.intBitsToFloat(reconstructed));
        int x = 1 << 10;
        int numRuns = 10;
        int numIterationsPerRun = 10000000;
        
        
        {
            int foo = 0;
            
            long t = System.currentTimeMillis();
            for (int run = 0; run < 100; run++) {
                for (int i = 0; i < numIterationsPerRun; i++) {
                    foo += i % x;
                }
            }
            System.out.println("Time taken: " + (System.currentTimeMillis() - t));
            System.out.println(foo);
        }
        {
            int xm1 = x - 1;
            int foo = 0;
            
            long t = System.currentTimeMillis();
            for (int run = 0; run < 100; run++) {
                for (int i = 0; i < numIterationsPerRun; i++) {
                    foo += i & xm1;
                }
            }
            System.out.println("Time taken: " + (System.currentTimeMillis() - t));
            System.out.println(foo);
        }
    }

    
}
