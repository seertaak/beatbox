/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio.msg;

import java.nio.charset.CharacterCodingException;

/**
 *
 * @author Martin Percossi
 */
class MessageException extends RuntimeException {

    public MessageException(String string) {
        super(string);
    }

    MessageException(Throwable e) {
        super(e);
    }
    
}
