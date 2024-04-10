/*
 * 
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *  
 *   Copyright @2019 Jerome Lelasseux. All rights reserved.
 * 
 *   This file is part of the JJazzLab software.
 *    
 *   JJazzLab is free software: you can redistribute it and/or modify
 *   it under the terms of the Lesser GNU General Public License (LGPLv3) 
 *   as published by the Free Software Foundation, either version 3 of the License, 
 *   or (at your option) any later version.
 * 
 *   JJazzLab is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Lesser General Public License for more details.
 *  
 *   You should have received a copy of the GNU Lesser General Public License
 *   along with JJazzLab.  If not, see <https://www.gnu.org/licenses/>
 *  
 *   Contributor(s): 
 * 
 */
package org.jjazz.outputsynth.spi;

import java.beans.PropertyChangeListener;
import org.jjazz.midi.api.Instrument;
import org.jjazz.midimix.api.UserRhythmVoice;
import org.jjazz.midimix.spi.RhythmVoiceInstrumentProvider;
import org.jjazz.outputsynth.api.OutputSynth;
import org.jjazz.rhythm.api.RhythmVoice;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * A manager for OutputSynth instances.
 */
public interface OutputSynthManager
{

    // Some standard output synth names
    public static String STD_GM = "GM";
    public static String STD_GM2 = "GM2";
    public static String STD_XG = "XG";
    public static String STD_GS = "GS";
    public static String STD_JJAZZLAB_SOUNDFONT_GS = "JJazzLabSoundFontGS";
    public static String STD_JJAZZLAB_SOUNDFONT_XG = "JJazzLabSoundFontXG";
    public static String STD_YAMAHA_TYROS_REF = "YamahaTyrosRef";

    /**
     * Property change event fired each time a new OutputSynth is associated to the default JJazzLab MidiDevice OUT: oldValue=old OutputSynth, newValue=new
     * OutputSynth.
     * <p>
     * The change event is also fired when default JJazzLab MidiDevice OUT changes.
     */
    String PROP_DEFAULT_OUTPUTSYNTH = "PropDefaultOutputSynth";
    /**
     * Property change event fired each time a new OutputSynth is associated to a MidiDevice OUT: oldValue=Midi device OUT name, newValue=OutputSynth.
     */
    String PROP_MDOUT_OUTPUTSYNTH = "MdOut-OutputSynth";


    public static OutputSynthManager getDefault()
    {
        var res = Lookup.getDefault().lookup(OutputSynthManager.class);
        if (res == null)
        {
            throw new IllegalStateException("Can't find OutputSynthManager instance in the global lookup");
        }
        return res;
    }


    /**
     * Get the current OuputSynth associated to the default JJazzLab Midi Device OUT.
     * <p>
     * If no Midi Device OUT defined, then return a shared instance of a GM Output Synth.
     *
     * @return Can't be null
     * @see JJazzMidiSystem
     */
    OutputSynth getDefaultOutputSynth();

    /**
     * Get a new instance of a standard OutputSynth.
     *
     * @param stdName The name of the standard output synth, eg "GM".
     * @return Can be null
     */
    OutputSynth getStandardOutputSynth(String stdName);


    /**
     * Get the OutputSynth associated to the specified output MidiDevice.
     *
     * @param mdOutName A Midi device OUT name, can't be null or empty
     * @return Can't be null.
     */
    OutputSynth getMidiDeviceOutputSynth(String mdOutName);

    /**
     * Associate outSynth to the specified midi OUT device name.
     *
     * @param mdOutName Can't be null
     * @param outSynth  Can't be null
     */
    void setMidiDeviceOutputSynth(String mdOutName, OutputSynth outSynth);

    /**
     * Scan all the OUT MidiDevices and make sure each MidiDevice is associated to an OutputSynth.
     * <p>
     * Should be called if the list of available OUT MidiDevices has changed.
     */
    void refresh();

    void addPropertyChangeListener(PropertyChangeListener l);

    void addPropertyChangeListener(String propName, PropertyChangeListener l);

    void removePropertyChangeListener(PropertyChangeListener l);

    void removePropertyChangeListener(String propName, PropertyChangeListener l);


    // ===============================================================================================
    // Inner classes
    // ===============================================================================================    
    /**
     * Implement the RhythmVoiceInstrumentProvider service using the default OutputSynth provided by the OutputSynthManager.
     */
    @ServiceProvider(service = RhythmVoiceInstrumentProvider.class)
    static public class DefaultRhythmVoiceInstrumentProviderImpl implements RhythmVoiceInstrumentProvider
    {

        @Override
        public String getId()
        {
            return RhythmVoiceInstrumentProvider.DEFAULT_ID;
        }

        @Override
        public Instrument findInstrument(RhythmVoice rv)
        {
            Instrument ins;
            var outSynth = OutputSynthManager.getDefault().getDefaultOutputSynth();

            if ((rv instanceof UserRhythmVoice) && !rv.isDrums())
            {
                ins = outSynth.getUserSettings().getUserMelodicInstrument();
            } else
            {
                ins = outSynth.findInstrument(rv);

            }

            return ins;
        }


    }
}
