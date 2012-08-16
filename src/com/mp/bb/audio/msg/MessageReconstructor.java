/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio.msg;

import java.nio.ByteBuffer;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * This class is designed to reconstruct messages in an easy yet efficient
 * way. 
 * 
 * The goal is basically to generate as little garbage as possible. Indeed,
 * the only thing created here is the ByteBuffer, which just wraps the true 
 * buffer.
 * 
 * @author Martin Percossi
 */
public class MessageReconstructor {
    
    private ByteBuffer buf;
    private int argsPos;

    public void setBuffer(byte[] buffer, int bufStart) {
        this.argsPos = -1;
        
        int size = 
            (0xff & buffer[0]) << 24  |
            (0xff & buffer[1]) << 16  |
            (0xff & buffer[2]) << 8   |
            (0xff & buffer[3]);      
        
        this.buf = ByteBuffer.wrap(buffer, bufStart, size);
    }
    
    public final long readGuid() {
        buf.position(4);
        return buf.getLong();
    }
    
    public final double time() {
        buf.position(12);
        return buf.getDouble();
    }
    
    public final int strcmpAddress(String str) {
        byte[] b1 = buf.array();
        byte[] b2 = str.getBytes();
        
        int result = 0;
        
        int i;
        for (i = 0; i < b2.length; i++) {
            if (b1[20+i] != b2[i] || b1[20+i] == 0) {
                result = b1[20+i] - b2[i];
                break;
            }   
        }
        
        // while we're at it, we're gonna compute where the string terminates.
        while (b1[20 + i++] != 0);
        argsPos = 20 + i;
        
        return result;
    }
    

    public static void main(String...args) {
        String str = "floobar";
        ByteBuffer bbuf = ByteBuffer.wrap(str.getBytes());
        
        System.out.println(ToStringBuilder.reflectionToString(str.getBytes(), 
                ToStringStyle.SIMPLE_STYLE));
    }

    public void clear() {
        this.buf = null;
    }
}
