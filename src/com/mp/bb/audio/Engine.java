/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mp.bb.audio;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jrtaudio.DeviceInfo;
import org.jrtaudio.JRtAudio;
import org.jrtaudio.StreamParameters;

/**
 * New version of the audio engine.
 *
 * @author Martin Percossi
 */
public class Engine {

    private JRtAudio audio;

    public Engine init() {
        audio = new JRtAudio();
        audio.showWarnings();

        for (int i = 0; i < audio.getDeviceCount(); i++) {
            System.out.println("Device " + i + ":"
                    + ToStringBuilder.reflectionToString(
                    audio.getDeviceInfo(i),
                    ToStringStyle.SHORT_PREFIX_STYLE));
        }

        int out = 2; //audio.getDefaultOutputDevice();
        int in = 2; //audio.getDefaultInputDevice();

        DeviceInfo devin = audio.getDeviceInfo(in);
        System.out.println("Input device: "
                + ToStringBuilder.reflectionToString(devin,
                ToStringStyle.SHORT_PREFIX_STYLE));
        StreamParameters inParams = new StreamParameters();
        inParams.nChannels = devin.inputChannels;
        inParams.deviceId = in;

        DeviceInfo devout = audio.getDeviceInfo(out);
        System.out.println("Output device: "
                + ToStringBuilder.reflectionToString(devout,
                ToStringStyle.SHORT_PREFIX_STYLE));
        StreamParameters oParams = new StreamParameters();
        oParams.nChannels = devout.outputChannels;
        oParams.deviceId = out;

        audio.openStream(oParams, inParams, 44100, 512, this);
        audio.startStream();
        //latency = 2 * 512;
        //System.out.println("Latency: " + latency);

        return this;

    }

    public int callback(float[] outputBuffer, float[] inputBuffer,
            int nBufferFrames, double streamTime, int status) 
    {
        return 0;
    }
}
