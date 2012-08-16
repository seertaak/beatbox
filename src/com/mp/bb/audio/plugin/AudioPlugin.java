/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio.plugin;

import com.mp.bb.audio.AudioHost;
import com.mp.bb.audio.AudioProcessor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 *
 * @author Martin Percossi
 */
public abstract class AudioPlugin implements AudioProcessor {
    
    transient private int id;
    transient private AudioHost host;
    
    @Override
    public void init(AudioHost host, int id) {
        this.host = host;
        this.id = id;
    }
    
    @Override
    public void release() {
        // NOP.
    }
    
    @Override
    public int id() {
        return id;
    }
    
    @Override
    public void id(int id) {
        this.id = id;
    }
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, 
                ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AudioPlugin other = (AudioPlugin) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + this.id;
        return hash;
    }
    
    public static void main(String...args) {
        System.out.println("Testing annotations.");
        AudioPlugin plugin = new GainPlugin();
        plugin.init(null, 0);
    }

}
