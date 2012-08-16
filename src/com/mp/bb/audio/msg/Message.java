/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio.msg;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Maximum message size is 4K.
 * @author Martin Percossi
 */
public class Message {
    private static final Charset ASCII = Charset.forName("US-ASCII");
    private static final CharsetEncoder ENCODER = ASCII.newEncoder();
    private static final CharsetDecoder DECODER = ASCII.newDecoder();
    
    private final static int INIT_NUM_ARGS = 4;
    private final static int INIT_STRING_SIZE = 64;
    
    private static AtomicLong lastGuid = new AtomicLong(0L);
    
    private long guid;
    private double time;
    private String address;
    private List<Object> arguments;
    private int size;
    
    public Message(byte[] msg) {
        int rawSize = msg.length;
        ByteBuffer bbuf = ByteBuffer.wrap(msg);
        size = bbuf.getInt();
        assert size <= rawSize;
        guid = bbuf.getLong();
        time = bbuf.getDouble();
        address = readStringZero(bbuf);      
        arguments = new ArrayList<>(INIT_NUM_ARGS);
        while (bbuf.position() < size)
            arguments.add(readArgument(bbuf));
    }
    
    public static long maxGuid() {
        return lastGuid.get();
    }
    
    public static void setGuidSequence(long value) {
        lastGuid.set(value);
    }
    
    public static String readStringZero(ByteBuffer bbuf) {
        int pos = bbuf.position();
        assert pos % 4 == 0;
        int n = 0;
        while (bbuf.get() != 0) n++;
        bbuf.position(pos);
        byte[] buf = new byte[n];
        bbuf.get(buf);
        while (bbuf.position() % 4 != 0)
            bbuf.get();
        return new String(buf, ASCII);
    }
    
    private static Object readArgument(ByteBuffer bbuf) {
        byte b = bbuf.get();
        assert b == (byte) ',';
        b = bbuf.get();
        bbuf.get();
        bbuf.get();
        
        switch (b) {
            case 'f':
                return bbuf.getFloat();
            case 's':
                return readStringZero(bbuf);
            case 'i':
                return bbuf.getInt();
            case 'T':
                return true;
            case 'F':
                return false;
            case 'N':   
                return null;
            case 'b':
                int n = bbuf.getInt();
                byte[] buf = new byte[n];
                bbuf.get(buf);
                while (bbuf.position() % 4 != 0)
                    bbuf.get();
                return buf;
        }
        
        throw new MessageException("Unexpected OSC type with specifier: " + b);
    }
    
    public Message(String address) {
        this.address = address;
        this.arguments = new ArrayList<>(INIT_NUM_ARGS);
        this.time = Double.NaN;
        this.guid = lastGuid.getAndIncrement();
        this.size = 4 /* size */ + 8 /* guid */ + 8 /* time */ 
                + sizeof(address);
    }
    
    public static Message to(String address) {
        return new Message(address);
    }
    
    public Message at(float time) {
        this.time = time;
        return this;
    }
    
    public Message guid(int id) {
        guid = id;
        return this;
    }
    
    public long guid() {
        return guid;
    }
       
    public Message addArg(Object o) {
        arguments.add(o);
        size += 4 + sizeof(o);
        return this;
    }
    
    public Object getArg(int i) {
        return arguments.get(i);
    }
    
    public int size() {
        return size;
    }
    
    public String address() {
        return address;
    }
    
    public Message address(String address) {
        this.address = address;
        return this;
    }
    
    public byte[] pack() {
        try {
            byte[] buf = new byte[size];
            ByteBuffer bbuf = ByteBuffer.wrap(buf);
            
            bbuf.putInt(size);
            bbuf.putLong(guid);
            bbuf.putDouble(time);
            bbuf.put(ENCODER.encode(CharBuffer.wrap(address))).put((byte) 0);
            align32(bbuf);
            
            for (Object arg : arguments) {
                //System.out.println("Packing: " + arg);
                packArg(bbuf, arg);
            }
            
            assert size == bbuf.position();
            
            return buf;
        } catch (CharacterCodingException ex) {
            throw new MessageException(ex);
        }
    }
    
    public static Message unpack(byte[] packedMessage) {
        return new Message(packedMessage);
    }
        
    private static void packArg(ByteBuffer bbuf, Object obj) {
        byte zero = (byte) 0;
        bbuf.put((byte) ',');
        
        if (obj == null) {
            bbuf.put((byte) 'N').put(zero).put(zero);
        } else if (obj instanceof String) {
            bbuf.put((byte) 's').put(zero).put(zero);
            String s = (String) obj;
            try {
                bbuf.put(ENCODER.encode(CharBuffer.wrap(s)));
                bbuf.put(zero);
                align32(bbuf);
            } catch (CharacterCodingException ex) {
                throw new MessageException(ex);
            }
        } else if (obj instanceof Float) {
            bbuf.put((byte) 'f').put(zero).put(zero);
            bbuf.putFloat((Float) obj);
        } else if (obj instanceof Integer) {
            bbuf.put((byte) 'i').put(zero).put(zero);
            bbuf.putInt((Integer) obj);
        } else if (obj instanceof byte[]) {
            bbuf.put((byte) 'b').put(zero).put(zero);
            byte[] buf = (byte[]) obj;
            bbuf.putInt(buf.length);
            bbuf.put(buf);
            align32(bbuf);
        } else if (obj instanceof Boolean) {
            bbuf.put((byte) ((boolean) obj ? 'T' : 'F'))
                .put(zero).put(zero);
        } else {
            throw new MessageException("Unhandled OSC type: " + obj 
                    + ":" + obj.getClass().getSimpleName());
        }        
    }
    
    private static void align32(ByteBuffer bbuf) {
        while (bbuf.position() % 4 != 0)
            bbuf.put((byte) 0);        
    }
    
    private static int sizeof(Object obj) {
        if (obj == null) {
            return 0;
        } else if (obj instanceof String) {
            return pad(((String) obj).length() + 1/*(null-terminated)*/);
        } else if (obj instanceof Float) {
            return 4;
        } else if (obj instanceof Integer) {
            return 4;
        } else if (obj instanceof byte[]) {
            return pad(4 + ((byte[])obj).length);
        } else if (obj instanceof Boolean) {
            return 4;
        } else {
            throw new MessageException("Unhandled OSC type: " + obj 
                    + ":" + obj.getClass().getSimpleName());
        }
    }
    
    private static int pad(int size) {
        while (size % 4 != 0)
            size++;
        return size;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Message other = (Message) obj;
        return this.guid == other.guid;
        /*
        if (!other.address.equals(address))
            return false;
        if (other.arguments.size() != arguments.size())
            return false;
        ListIterator<Object> i = this.arguments.listIterator();
        ListIterator<Object> j = this.arguments.listIterator();
        while (i.hasNext() && j.hasNext()) {
            Object o1 = i.next();
            Object o2 = j.next();
            if (o1.getClass() != o2.getClass())
                return false;
            if (o1 instanceof byte[]) {
                if (!Arrays.equals((byte[])o1, (byte[])o2))
                    return false;
            } else if (!(o1==null ? o2==null : o1.equals(o2))) {
                return false;
            }
        }
        
        return true;
        */
    }

    @Override
    public int hashCode() {
        return Long.valueOf(guid).hashCode();
        /*
        int hash = 7;
        hash = 61 * hash + address.hashCode();
        hash = 61 * hash + Integer.valueOf(arguments.size()).hashCode();
        for (Object arg: arguments) {
            if (arg instanceof byte[]) {
                for (byte b: (byte[]) arg)
                    hash = 61 * hash + Byte.valueOf(b).hashCode();
            } else if (arg != null) {
                hash = 61 * hash + arg.hashCode();
            } 
        }
        hash = 61 * hash + Integer.valueOf(size).hashCode();
        return hash;
        */
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, 
                ToStringStyle.SHORT_PREFIX_STYLE);
    }
    
    public static void main(String...args) {
        byte[] foo = new byte[] { 0, 4, 2 };
        Message msg = Message.to("/a").at(4).addArg(2f).addArg(foo).addArg("foo")
                .addArg(43);
        System.out.println(msg.size());
        byte[] packedMsg = msg.pack();
        System.out.println(msg);
        System.out.println(packedMsg.length);
        System.out.println(ToStringBuilder.reflectionToString(packedMsg));
        System.out.println((byte) 'f');
        Message rmsg = Message.unpack(packedMsg);
        System.out.println(rmsg);
        System.out.println(rmsg.equals(msg));
        System.out.println(rmsg.hashCode());
        System.out.println(msg.hashCode());
        System.out.println(((byte[])msg.getArg(1)).length);
        System.out.println(((byte[])rmsg.getArg(1)).length);
        System.out.println(ToStringBuilder.reflectionToString(msg.getArg(1)));
        System.out.println(ToStringBuilder.reflectionToString(rmsg.getArg(1)));
    }

    public double time() {
        return time;
    }
    
    public Message time(double time) {
        this.time = time;
        return this;
    }

    public int numArgs() {
        return arguments.size();
    }
}
