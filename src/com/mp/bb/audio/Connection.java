/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio;

import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 *
 * @author Martin Percossi
 */
public class Connection {
    
    private AudioProcessor src;
    private AudioProcessor dest;

    public Connection(AudioProcessor srcNode, AudioProcessor destNode) {
        this.src = srcNode;
        this.dest = destNode;
    }
    
    public AudioProcessor getDestNode() {
        return dest;
    }
    
    public AudioProcessor getSrcNode() {
        return src;
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
        final Connection other = (Connection) obj;
        if (!this.src.equals(other.src)) {
            return false;
        }
        if (!this.dest.equals(other.dest)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + src.hashCode();
        hash = 71 * hash + dest.hashCode();
        return hash;
    }

    

}
