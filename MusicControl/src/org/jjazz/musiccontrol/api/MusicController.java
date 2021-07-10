/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright @2019 Jerome Lelasseux. All rights reserved.
 *
 *  This file is part of the JJazzLabX software.
 *
 *  JJazzLabX is free software: you can redistribute it and/or modify
 *  it under the terms of the Lesser GNU General Public License (LGPLv3)
 *  as published by the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  JJazzLabX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with JJazzLabX.  If not, see <https://www.gnu.org/licenses/>
 *
 *  Contributor(s):
 */
package org.jjazz.musiccontrol.api;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.sound.midi.ControllerEventListener;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jjazz.analytics.api.Analytics;
import org.jjazz.leadsheet.chordleadsheet.api.item.CLI_ChordSymbol;
import org.jjazz.leadsheet.chordleadsheet.api.item.Position;
import org.jjazz.midi.api.InstrumentMix;
import org.jjazz.midi.api.MidiConst;
import org.jjazz.midi.api.MidiUtilities;
import org.jjazz.midi.api.JJazzMidiSystem;
import org.jjazz.midimix.api.MidiMix;
import org.jjazz.midimix.api.UserChannelRvKey;
import org.jjazz.outputsynth.api.OutputSynthManager;
import org.jjazz.rhythm.api.RhythmVoice;
import org.jjazz.rhythmmusicgeneration.api.MusicGenerationContext;
import org.jjazz.rhythm.api.MusicGenerationException;
import org.jjazz.rhythmmusicgeneration.spi.MusicGenerator;
import org.jjazz.song.api.Song;
import org.jjazz.songstructure.api.SongPart;
import org.jjazz.util.api.ResUtil;
import org.jjazz.util.api.Utilities;
import org.openide.util.Exceptions;
import org.openide.util.NbPreferences;

/**
 * Control the music playback.
 * <p>
 * Property changes are fired for:<br>
 * - start/pause/stop/disabled state changes<br>
 * - pre-playing : vetoable change, ie listeners can fire a PropertyVetoException to prevent playback to start<br>
 * <p>
 * Use NoteListener to get notified of note ON/OFF events. Use PlaybackListener to get notified of other events (e.g. bar/beat
 * changes) during playback. Note that listeners will be notified out of the Swing EDT.<br>
 * The current output synth latency is taken into account to fire events to NoteListeners and PlaybackListeners.
 * <p>
 */
public class MusicController implements PropertyChangeListener, MetaEventListener, ControllerEventListener, ChangeListener
{

    public static final String PROP_PLAYBACK_KEY_TRANSPOSITION = "PlaybackTransposition";              //NOI18N
    public static final String PROP_STATE = "PropPlaybackState";   //NOI18N 
    /**
     * This vetoable property is changed/fired just before playing song and can be vetoed by vetoables listeners to cancel
     * playback start.
     * <p>
     * NewValue=MusicGenerationContext object.
     */
    public static final String PROPVETO_PRE_PLAYBACK = "PropVetoPrePlayback";   //NOI18N 
    public static final String PROP_LOOPCOUNT = "PropLoopCount";   //NOI18N 

    /**
     * The playback states.
     * <p>
     * Property change listeners are notified with property PROP_STATE.
     */
    public enum State
    {
        DISABLED,
        STOPPED,
        PAUSED,
        PLAYING
    }
    private static MusicController INSTANCE;
    private Sequencer sequencer;
    private final Object lock = new Object();
    private PlaybackSession playbackSession;
    /**
     * The optional current post processors.
     */
    private MusicGenerator.PostProcessor[] postProcessors;

    private State state;
    /**
     * The current beat position during playback.
     */
    Position currentBeatPosition = new Position();
    private int loopCount;

    /**
     * Sequencer lock by an external entity.
     */
    private Object sequencerLockHolder;

    /**
     * The tempo factor to go from MidiConst.SEQUENCER_REF_TEMPO to song tempo.
     */
    private float songTempoFactor;
    /**
     * The tempo factor of the SongPart being played.
     */
    private float songPartTempoFactor = 1;
    private int audioLatency;
    /**
     * Keep track of active timers used to compensate the audio latency.
     * <p>
     * Needed to force stop them when sequencer is stopped/paused by user.
     */
    private Set<Timer> audioLatencyTimers = new HashSet<>();
    /**
     * Our MidiReceiver to be able to fire events to NoteListeners and PlaybackListener (midiActivity).
     */
    private McReceiver receiver;
    /**
     * The list of the controller changes listened to
     */
    private static final int[] listenedControllers =
    {
        MidiConst.CTRL_CHG_JJAZZ_BEAT_CHANGE,
        MidiConst.CTRL_CHG_JJAZZ_TEMPO_FACTOR
    };
    /**
     * If true display built sequence when it is built
     */
    private boolean debugPlayedSequence = false;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final VetoableChangeSupport vcs = new VetoableChangeSupport(this);
    private final List<PlaybackListener> playbackListeners = new ArrayList<>();
    private final List<NoteListener> noteListeners = new ArrayList<>();
    private static final Preferences prefs = NbPreferences.forModule(MusicController.class);
    private static final Logger LOGGER = Logger.getLogger(MusicController.class.getSimpleName());  //NOI18N

    public static MusicController getInstance()
    {
        synchronized (MusicController.class)
        {
            if (INSTANCE == null)
            {
                INSTANCE = new MusicController();
            }
        }
        return INSTANCE;
    }

    /**
     * The sequencer tempo must be set to 100.
     */
    private MusicController()
    {
        loopCount = 0;

        state = State.STOPPED;
        sequencer = JJazzMidiSystem.getInstance().getDefaultSequencer();
        receiver = new McReceiver();
        initSequencer();
        sequencerLockHolder = null;


        // Get notified of the notes sent by the Sequencer
        try
        {
            Transmitter t = sequencer.getTransmitter();
            t.setReceiver(receiver);
        } catch (MidiUnavailableException ex)
        {
            // Should never occur
            Exceptions.printStackTrace(ex);
        }


        // Listen to click settings changes
        ClickManager.getInstance().addPropertyChangeListener(this);


        // Listen to latency changes
        var osm = OutputSynthManager.getInstance();
        osm.addPropertyChangeListener(this);
        audioLatency = osm.getOutputSynth().getAudioLatency();


    }

    /**
     * Try to temporarily acquire the java sequencer opened and connected to the default Midi out device.
     * <p>
     * You can acquire the sequencer only if MusicController is in the STOPPED state. If acquisition is successful the
     * MusicController is put in DISABLED state since it can't use the sequencer anymore. When the caller is done with the
     * sequencer, he must call releaseSequencer(lockHolder) so that the MusicController can use it again.
     *
     * @param lockHolder Must be non-null
     * @return Null if sequencer has already a different lock or if MusicController is in the PLAYING state.
     */
    public synchronized Sequencer acquireSequencer(Object lockHolder)
    {
        if (lockHolder == null)
        {
            throw new NullPointerException("lockHolder");   //NOI18N
        }

        LOGGER.fine("acquireSequencer() -- lockHolder=" + lockHolder);  //NOI18N

        if (sequencerLockHolder == lockHolder)
        {
            // lock already acquired
            return sequencer;
        } else if (sequencerLockHolder == null && state.equals(State.STOPPED))
        {
            // lock acquired by external entity: get disabled
            sequencerLockHolder = lockHolder;

            // Remove the MusicController listeners
            releaseSequencer();

            State old = state;
            state = State.DISABLED;
            pcs.firePropertyChange(PROP_STATE, old, state);

            LOGGER.fine("acquireSequencer() external lock acquired.  MusicController released the sequencer, oldState=" + old + " newState=DISABLED");  //NOI18N

            return sequencer;
        } else
        {
            LOGGER.fine("acquireSequencer() can't give lock to " + lockHolder + ", current lock=" + sequencerLockHolder);  //NOI18N
            return null;
        }
    }

    /**
     * Release the external sequencer lock.
     * <p>
     * Once released MusicController becomes able to take the Sequencer lock when playing music.
     *
     * @param lockHolder
     * @throws IllegalArgumentException If lockHolder does not match
     */
    public synchronized void releaseSequencer(Object lockHolder)
    {
        if (lockHolder == null || sequencerLockHolder != lockHolder)
        {
            throw new IllegalArgumentException("lockHolder=" + lockHolder + " sequencerLockHolder=" + sequencerLockHolder);   //NOI18N
        }

        LOGGER.fine("releaseSequencer() -- lockHolder=" + lockHolder);  //NOI18N

        sequencerLockHolder = null;

        sequencer.stop(); // Just to make sure

        // Initialize sequencer for MusicController
        initSequencer();

        if (playbackSession != null)
        {
            playbackSession.setDirty();
        }

        // Change state
        State old = state;
        assert old.equals(State.DISABLED);   //NOI18N
        state = State.STOPPED;
        pcs.firePropertyChange(PROP_STATE, old, state);
    }

    /**
     * Set the playback session on which the MusicController's methods (play/pause/etc.) will operate.
     *
     * @param context If context is instanceof SongPlaybackSession,the MusicController
     */
    public void setSession(PlaybackSession context)
    {
        if (context instanceof SongPlaybackSession)
        {
            setSession((SongPlaybackSession) context);
        }
    }

    /**
     * Set the music session on which this controller's methods (play/pause/etc.) will operate, and build the sequence.
     * <p>
     * Stop the playback if it was on. Tempo is set to song's tempo.
     *
     * @param context Can be null.
     * @param postProcessors Optional PostProcessors to use when generating the backing track.
     * @throws org.jjazz.rhythm.api.MusicGenerationException E.g. if state is DISABLED, sequence could not be generated, etc.
     */
    public void setSession(MusicGenerationContext context, MusicGenerator.PostProcessor... postProcessors) throws MusicGenerationException
    {
        if (context != null && context.equals(mgContext))
        {
            return;
        }

        if (state.equals(State.DISABLED))
        {
            throw new MusicGenerationException(ResUtil.getString(getClass(), "PLAYBACK IS DISABLED"));
        }

        this.postProcessors = postProcessors;

        stop();
        songPartTempoFactor = 1f;

        if (mgContext != null)
        {
            mgContext.getMidiMix().removePropertyListener(this);
            mgContext.getSong().removePropertyChangeListener(this);
        }

        if (playbackSession != null)
        {
            playbackSession.cleanup();
            playbackSession = null;
        }

        mgContext = context;
        if (mgContext != null)
        {
            mgContext.getMidiMix().addPropertyListener(this);
            mgContext.getSong().addPropertyChangeListener(this);
            songTempoChanged(mgContext.getSong().getTempo());
            try
            {
                playbackSession = new SongPlaybackSession(mgContext, this, this.postProcessors); // Exception possible when building the sequence
            } catch (MusicGenerationException ex)
            {
                // Roll back variables state
                mgContext.getMidiMix().removePropertyListener(this);
                mgContext.getSong().removePropertyChangeListener(this);
                mgContext = null;
                throw ex;
            }
        }
    }


    /**
     * Create (if necessary) a new SongPlaybackSession from mgContext with no PostProcessors, then play from bar fromBarIndex.
     *
     * @param mgContext
     * @param fromBarIndex Must be a valid bar in mgContext bar range
     * @throws java.beans.PropertyVetoException If a vetoable listener vetoed the playback start. A listener who has already
     * notified user should throw an exception with a null message.
     * @throws MusicGenerationException If a problem occurred which prevents song playing: no Midi out, song is already playing,
     * rhythm music generation problem, MusicController is disabled, etc.
     *
     */
    public void play(MusicGenerationContext mgContext, int fromBarIndex) throws MusicGenerationException, PropertyVetoException
    {
        if (mgContext == null)
        {
            throw new IllegalArgumentException("mgContext=" + mgContext + " fromBarIndex=" + fromBarIndex);
        }

        if (playbackSession == null || getSongPlaybackContext() == null || !mgContext.equals(getMusicGenerationContext()))
        {
            // Need to create a new SongPlaybackSession
            var spContext = new SongPlaybackSession(mgContext);
            play(spContext, fromBarIndex);
        } else
        {
            // Reuse existing context
            play(getSongPlaybackContext(), fromBarIndex);
        }
    }

    /**
     * Play from bar fromBarIndex in the specified context.
     * <p>
     * If spSession state is NEW then generate the session data. If spSession state is OUTDATED then create a new session and
     * generate the data. If a different session is already playing, change the session on the fly (without stopping the
     * sequencer).
     *
     * @param spSession
     * @param fromBarIndex Must be a valid bar in spSession bar range
     * @throws java.beans.PropertyVetoException If a vetoable listener vetoed the playback of the specified session. A listener
     * who has already notified the end-user via its own UI must throw a PropertyVetoException with a null message to avoid
     * another notification by the framework.
     * @throws MusicGenerationException If a problem occurred which prevents song playing: no Midi out, rhythm music generation
     * problem, MusicController is disabled, etc.
     *
     */
    public void play(SongPlaybackSession spSession, int fromBarIndex) throws MusicGenerationException, PropertyVetoException
    {
        playImpl(spSession, fromBarIndex, Long.MIN_VALUE);
    }


    /**
     * Play the specified context from the specified tick position.
     * <p>
     * To use 100% of the MusicController features, use play(SongPlaybackSession, int).
     *
     * @param pSession
     * @param fromTick
     * @throws org.jjazz.rhythm.api.MusicGenerationException E.g. if state is DISABLED, sequence could not be generated, etc.
     */
    public void play(PlaybackSession pSession, long fromTick) throws MusicGenerationException, PropertyVetoException
    {
        playImpl(pSession, Integer.MIN_VALUE, fromTick);
    }


    /**
     * Start the playback of a song using the current context.
     * <p>
     * Song is played from the beginning of the context range. Before playing the song, vetoable listeners are notified with a
     * PROPVETO_PRE_PLAYBACK property change.<p>
     *
     *
     * @param fromBarIndex Play the song from this bar. Bar must be within the context's range.
     *
     * @throws java.beans.PropertyVetoException If a vetoable listener vetoed the playback start. A listener who has already
     * notified user should throw an exception with a null message.
     * @throws MusicGenerationException If a problem occurred which prevents song playing: no Midi out, song is already playing,
     * rhythm music generation problem, MusicController is disabled, etc.
     * @throws IllegalStateException If context is null.
     *
     * @see #getPlayingSongCopy()
     */
    public void play(int fromBarIndex) throws MusicGenerationException, PropertyVetoException
    {
        if (mgContext == null)
        {
            throw new IllegalStateException("context=" + mgContext + ", fromBarIndex=" + fromBarIndex);   //NOI18N
        }
        if (!mgContext.getBarRange().contains(fromBarIndex))
        {
            throw new IllegalArgumentException("context=" + mgContext + ", fromBarIndex=" + fromBarIndex);   //NOI18N
        }


        checkMidi();                // throws MusicGenerationException


        // Check state
        if (state == State.PLAYING)
        {
            throw new MusicGenerationException(ResUtil.getString(getClass(), "A SONG IS ALREADY PLAYING"));
        } else if (state == State.DISABLED)
        {
            throw new MusicGenerationException(ResUtil.getString(getClass(), "PLAYBACK IS DISABLED"));
        }


        // If we're here then playbackState = PAUSE or STOPPED
        if (mgContext.getBarRange().isEmpty())
        {
            // Throw an exception to let the UI roll back (eg play stateful button)
            throw new MusicGenerationException(ResUtil.getString(getClass(), "NOTHING TO PLAY"));
        }


        // Check that all listeners are OK to start playback
        vcs.fireVetoableChange(PROPVETO_PRE_PLAYBACK, null, mgContext);  // can raise PropertyVetoException


        // Log the play event        
        Analytics.logEvent("Play", Analytics.buildMap("Bar Range", mgContext.getBarRange().toString(), "Rhythms", Analytics.toStrList(mgContext.getUniqueRhythms())));
        Analytics.incrementProperties("Nb Play", 1);
        Analytics.setPropertiesOnce(Analytics.buildMap("First Play", Analytics.toStdDateTimeString()));


        // Regenerate the sequence and the related data if needed
        if (playbackSession.isOutdated())
        {
            try
            {
                playbackSession.buildSequence();

                if (debugPlayedSequence)
                {
                    MusicController.LOGGER.info("buildSequence() song=" + workMgContext.getSong().getName() + " sequence :"); //NOI18N
                    MusicController.LOGGER.info(MidiUtilities.toString(sequence)); //NOI18N
                }

                // Initialize sequencer with the built sequence
                outer.sequencer.setSequence(sequence); // Can raise InvalidMidiDataException
                // Update muted state for each track
                updateAllTracksMuteState(workMgContext.getMidiMix());
                outer.sequencer.setTrackMute(controlTrackId, false);
                outer.sequencer.setTrackMute(precountClickTrackId, false);
                outer.sequencer.setTrackMute(playbackClickTrackId, !ClickManager.getInstance().isPlaybackClickEnabled());
                // Set position and loop points
                outer.sequencer.setLoopStartPoint(songTickStart);

                outer.sequencer.setLoopEndPoint(songTickEnd);


            } catch (MusicGenerationException ex)
            {
                throw ex;
            }
        }


        // Set start position
        setPosition(fromBarIndex);


        // Start or restart the sequencer
        sequencer.setLoopCount(loopCount);
        seqStart();


        State old = this.getState();
        state = State.PLAYING;


        pcs.firePropertyChange(PROP_STATE, old, state);
    }

    /**
     * Resume playback from the pause state.
     * <p>
     * If played/paused song was modified, then resume() will just redirect to the play() method. If state is not PAUSED, nothing
     * is done.
     *
     * @throws org.jjazz.rhythm.api.MusicGenerationException For example if state==DISABLED
     * @throws java.beans.PropertyVetoException
     */
    public void resume() throws MusicGenerationException, PropertyVetoException
    {
        if (state == State.DISABLED)
        {
            throw new MusicGenerationException(ResUtil.getString(getClass(), "PLAYBACK IS DISABLED"));
        } else if (!state.equals(State.PAUSED))
        {
            return;
        }


        if (playbackSession.isOutdated())
        {
            // Song was modified during playback, do play() instead
            play(mgContext.getBarRange().from);
            return;
        }


        if (mgContext == null)
        {
            throw new IllegalStateException("context=" + mgContext);   //NOI18N
        }


        checkMidi();                // throws MusicGenerationException


        // Check that all listeners are OK to resume playback
        vcs.fireVetoableChange(PROPVETO_PRE_PLAYBACK, null, mgContext.getSong());  // can raise PropertyVetoException


        // Let's go again
        seqStart();


        // Update state
        State old = this.getState();
        state = State.PLAYING;
        pcs.firePropertyChange(PROP_STATE, old, state);

    }

    /**
     * Stop the playback of the sequence if it was playing, and reset the position to the beginning of the sequence.
     * <p>
     */
    public void stop()
    {
        switch (state)
        {
            case DISABLED:
            case STOPPED:
                return;
            case PAUSED:
                // Nothing
                break;
            case PLAYING:
                sequencer.stop();
                clearPendingEvents();
                break;
            default:
                throw new AssertionError(state.name());
        }

        // Update state
        songPartTempoFactor = 1;

        State old = this.getState();
        state = State.STOPPED;
        pcs.firePropertyChange(PROP_STATE, old, state);

        // Position must be reset after the stop so that playback beat change tracking listeners are not reset upon stop
        int bar = mgContext != null ? mgContext.getBarRange().from : 0;
        setPosition(bar);

    }

    /**
     * Stop the playback of the sequence and leave the position unchanged.
     * <p>
     * If played song was modified after playback started, then pause() will just redirect to the stop() method. If state is not
     * PLAYING, nothing is done.
     */
    public void pause()
    {

        if (!state.equals(State.PLAYING))
        {
            return;
        }

        if (playbackSession.isOutdated())
        {
            // Song was modified during playback, pause() not allowed, do stop() instead
            stop();
            return;
        }

        sequencer.stop();
        clearPendingEvents();

        State old = getState();
        state = State.PAUSED;
        pcs.firePropertyChange(PROP_STATE, old, state);
    }

    /**
     * The current MusicGenerationContext.
     *
     * @return Can be null.
     */
    public MusicGenerationContext getContext()
    {
        return mgContext;
    }

    /**
     * The current PostProcessors.
     *
     * @return
     */
    public MusicGenerator.PostProcessor[] getPostProcessors()
    {
        return postProcessors;
    }

    /**
     * The current playback position updated at every beat (beat is an integer).
     *
     * @return
     */
    public Position getBeatPosition()
    {
        return currentBeatPosition;
    }

    public State getState()
    {
        return state;
    }

    /**
     * Get the key transposition applied to chord symbols when playing a song.
     * <p>
     *
     * @return [0;-11] Default is 0.
     */
    public int getPlaybackKeyTransposition()
    {
        return prefs.getInt(PROP_PLAYBACK_KEY_TRANSPOSITION, 0);
    }

    /**
     * Set the key transposition applied to chord symbols when playing a song.
     * <p>
     * Ex: if transposition=-2, chord=C#7 will be replaced by B7.
     *
     * @param t [0;-11]
     */
    public void setPlaybackKeyTransposition(int t)
    {
        if (t < -11 || t > 0)
        {
            throw new IllegalArgumentException("t=" + t);   //NOI18N
        }

        int old = getPlaybackKeyTransposition();
        if (old != t)
        {
            if (playbackSession != null)
            {
                playbackSession.setDirty();
            }
            prefs.putInt(PROP_PLAYBACK_KEY_TRANSPOSITION, t);
            pcs.firePropertyChange(PROP_PLAYBACK_KEY_TRANSPOSITION, old, t);
        }
    }

    public int getLoopCount()
    {
        return loopCount;
    }

    /**
     * Set the loop count of the playback.
     * <p>
     * Do nothing if state == DISABLED.
     *
     * @param loopCount If 0, play the song once (no loop). Use Sequencer.LOOP_CONTINUOUSLY for endless loop.
     */
    public void setLoopCount(int loopCount)
    {
        if (loopCount != Sequencer.LOOP_CONTINUOUSLY && loopCount < 0)
        {
            throw new IllegalArgumentException("loopCount=" + loopCount);   //NOI18N
        }

        if (state.equals(State.DISABLED))
        {
            return;
        }

        int old = this.loopCount;
        this.loopCount = loopCount;

        sequencer.setLoopCount(loopCount);

        pcs.firePropertyChange(PROP_LOOPCOUNT, old, this.loopCount);
    }

    public void setDebugPlayedSequence(boolean b)
    {
        debugPlayedSequence = b;
    }

    public boolean isDebugPlayedSequence()
    {
        return debugPlayedSequence;
    }

    /**
     * Add a listener of note ON/OFF events.
     * <p>
     * Listeners will be called out of the Swing EDT (Event Dispatch Thread).
     *
     * @param listener
     */
    public synchronized void addNoteListener(NoteListener listener)
    {
        if (!noteListeners.contains(listener))
        {
            noteListeners.add(listener);
        }
    }

    public synchronized void removeNoteListener(NoteListener listener)
    {
        noteListeners.remove(listener);
    }

    /**
     * Add a listener to be notified of playback bar/beat changes events etc.
     * <p>
     * Listeners will be called out of the Swing EDT (Event Dispatch Thread).
     *
     * @param listener
     */
    public synchronized void addPlaybackListener(PlaybackListener listener)
    {
        if (!playbackListeners.contains(listener))
        {
            playbackListeners.add(listener);
        }
    }

    public synchronized void removePlaybackListener(PlaybackListener listener)
    {
        playbackListeners.remove(listener);
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.addPropertyChangeListener(listener);
    }

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Listeners will be notified via the PROPVETO_PRE_PLAYBACK property change before a playback is started.
     * <p>
     * The NewValue is a MusicGenerationContext object. Listener is responsible for informing the user if the change was vetoed.
     *
     * @param listener
     */
    public synchronized void addVetoableChangeListener(VetoableChangeListener listener)
    {
        vcs.addVetoableChangeListener(listener);
    }

    public synchronized void removeVetoableChangeListener(VetoableChangeListener listener)
    {
        vcs.removeVetoableChangeListener(listener);
    }

    //-----------------------------------------------------------------------
    // Implementation of the ChangeListener interface
    //-----------------------------------------------------------------------

    @Override
    public void stateChanged(ChangeEvent e)
    {
        if (e.getSource() == playbackSession)
        {

        }
    }

    //-----------------------------------------------------------------------
    // Implementation of the ControlEventListener interface
    //-----------------------------------------------------------------------
    /**
     * Handle the listened controllers notifications.
     * <p>
     * CAUTIOUS : the global listenedControllers array must be consistent with this method !
     *
     * @param event
     */
    @Override
    public void controlChange(ShortMessage event)
    {
        int data1 = event.getData1();
        switch (data1)
        {
            case MidiConst.CTRL_CHG_JJAZZ_BEAT_CHANGE:
                long tick = sequencer.getTickPosition() - playbackSession.songTickStart;
                int index = (int) (tick / MidiConst.PPQ_RESOLUTION);
                long remainder = tick % MidiConst.PPQ_RESOLUTION;
                index += (remainder <= MidiConst.PPQ_RESOLUTION / 2) ? 0 : 1;
                if (index >= playbackSession.naturalBeatPositions.size())
                {
                    index = playbackSession.naturalBeatPositions.size() - 1;
                }
                Position newPos = playbackSession.naturalBeatPositions.get(index);
                updateCurrentPosition(newPos.getBar(), newPos.getBeat());

                break;

            case MidiConst.CTRL_CHG_JJAZZ_TEMPO_FACTOR:
                songPartTempoFactor = MidiUtilities.getTempoFactor(event);
                updateTempoFactor();
                break;

            default:
                LOGGER.log(Level.WARNING, "controlChange() controller event not managed data1={0}", data1);  //NOI18N
                break;
        }
    }

    //-----------------------------------------------------------------------
    // Implementation of the MetaEventListener interface
    //-----------------------------------------------------------------------
    @Override
    public void meta(MetaMessage meta)
    {
        if (meta.getType() == 47) // Meta Event for end of sequence
        {
            // This method  is called from the Sequencer thread, NOT from the EDT !
            // So if this method impacts the UI, it must use SwingUtilities.InvokeLater() (or InvokeAndWait())
            LOGGER.fine("Sequence end reached");  //NOI18N        
            SwingUtilities.invokeLater(() -> stop());
        } else if (meta.getType() == 6)     // Marker
        {
            String s = Utilities.toString(meta.getData());
            if (s.startsWith("csIndex="))           // Marker for chord symbol
            {
                synchronized (lock)     // Synchronized with updateSequencer() which modifies playbackSession and the sequence
                {
                    if (playbackSession instanceof SongPlaybackSession)
                    {
                        SongPlaybackSession spSession = (SongPlaybackSession) playbackSession;
                        // Fire chord symbol change
                        int csIndex = Integer.valueOf(s.substring(8));
                        CLI_ChordSymbol cliCs = spSession.getContextChordGetSequence().get(csIndex);
                        fireChordSymbolChanged(cliCs);


                        // Check if there is a song part change as well
                        Position pos = cliCs.getPosition();
                        if (pos.isFirstBarBeat())
                        {
                            SongPart newSpt = spSession.getMusicGenerationContext().getSongParts().stream()
                                    .filter(spt -> spt.getStartBarIndex() == pos.getBar())
                                    .findFirst().orElse(null);
                            if (newSpt != null)
                            {
                                fireSongPartChanged(newSpt);
                            }
                        }
                    }
                }
            }
        }
    }

    //-----------------------------------------------------------------------
    // Implementation of the PropertiesListener interface
    //-----------------------------------------------------------------------
    @SuppressWarnings(
            {
                "unchecked", "rawtypes"
            })
    @Override
    public void propertyChange(PropertyChangeEvent e)
    {
        LOGGER.log(Level.FINE, "propertyChange() e={0}", e);  //NOI18N

        // Always enabled changes
        if (e.getSource() == OutputSynthManager.getInstance())
        {
            if (e.getPropertyName().equals(OutputSynthManager.PROP_AUDIO_LATENCY))
            {
                audioLatency = (int) e.getNewValue();
            }
        }

        // Below property changes are meaningless if no context or if state is DISABLED
        if (mgContext == null || state.equals(State.DISABLED))
        {
            return;
        }

        if (e.getSource() == mgContext.getSong())
        {
            if (e.getPropertyName().equals(Song.PROP_MODIFIED_OR_SAVED))
            {
                if ((Boolean) e.getNewValue() == true)
                {
                    playbackSession.setDirty();
                }
            } else if (e.getPropertyName() == null ? Song.PROP_TEMPO == null : e.getPropertyName().equals(Song.PROP_TEMPO))
            {
                songTempoChanged((Integer) e.getNewValue());
            } else if (e.getPropertyName() == null ? Song.PROP_CLOSED == null : e.getPropertyName().equals(Song.PROP_CLOSED))
            {
                stop();
            }
        } else if (e.getSource() == mgContext.getMidiMix() && null != e.getPropertyName())
        {
            switch (e.getPropertyName())
            {
                case MidiMix.PROP_INSTRUMENT_MUTE:
                    InstrumentMix insMix = (InstrumentMix) e.getOldValue();
                    updateTrackMuteState(insMix, playbackSession.mapRvTrackId);
                    break;
                case MidiMix.PROP_CHANNEL_DRUMS_REROUTED:
                case MidiMix.PROP_INSTRUMENT_TRANSPOSITION:
                case MidiMix.PROP_INSTRUMENT_VELOCITY_SHIFT:
                    // This can impact the sequence, make sure it is rebuilt
                    playbackSession.setDirty();
                    break;
                case MidiMix.PROP_CHANNEL_INSTRUMENT_MIX:
                    playbackSession.mapRvTrackId.clear();       // Mapping between RhythmVoice and Sequence tracks is no longer valid
                    playbackSession.setDirty();                 // Make sure sequence is rebuilt
                    break;
                case MidiMix.PROP_DRUMS_INSTRUMENT_KEYMAP:
                    // KeyMap has changed, need to regenerate the sequence
                    playbackSession.setDirty();
                    break;
                default:
                    // eg MidiMix.PROP_USER_CHANNEL: do nothing
                    break;
            }
        } else if (e.getSource() == ClickManager.getInstance())
        {
            if (e.getPropertyName().equals(ClickManager.PROP_PLAYBACK_CLICK_ENABLED))
            {
                // Click track is always there, just unmute/mute it when needed
                boolean isClickEnabled = (Boolean) e.getNewValue();
                int clickTrackId = playbackSession.getClickTrackId();
                if (clickTrackId != -1)
                {
                    sequencer.setTrackMute(clickTrackId, !isClickEnabled);
                }
            }
        }

        if (playbackSession.isOutdated() && state == State.PAUSED)
        {
            stop();
        }
    }

    // =====================================================================================
    // Private methods
    // =====================================================================================
    /**
     * Initialize the sequencer for our usage.
     */
    private void initSequencer()
    {
        addSequencerListeners();
        sequencer.setTempoInBPM(MidiConst.SEQUENCER_REF_TEMPO);
        receiver.setEnabled(true);
    }

    /**
     * Remove our listeners.
     */
    private void releaseSequencer()
    {
        removeSequencerListeners();
        receiver.setEnabled(false);
    }

    private void addSequencerListeners()
    {
        sequencer.addMetaEventListener(this);
        int[] res = sequencer.addControllerEventListener(this, listenedControllers);
        if (res.length != listenedControllers.length)
        {
            LOGGER.severe("This sequencer implementation is limited, music playback may not work");  //NOI18N
        }
    }

    private void removeSequencerListeners()
    {
        sequencer.removeMetaEventListener(this);
        sequencer.removeControllerEventListener(this, listenedControllers);
    }

    /**
     *
     * Set the sequencer and model position to the specified bar.
     * <p>
     * Take into account the possible precount bars.
     * <p>
     * @param fromBar Must be &gt;=0
     */
    private void setPosition(int fromBar)
    {
        assert !state.equals(State.DISABLED) && playbackSession instanceof SongPlaybackSession : "state=" + state + " playbackSession=" + playbackSession;

        SongPlaybackSession spSession = (SongPlaybackSession) playbackSession;

        long tick = 0;       // Default when fromBar==0 and click precount is true
        if (ClickManager.getInstance().isClickPrecountEnabled())
        {
            // Start from 0 to get the precount notes
        } else
        {
            tick = spSession.getTickStart();   // Bar 0 of the range            
            if (fromBar > spSession.getMusicGenerationContext().getBarRange().from)
            {
                tick += spSession.getMusicGenerationContext().getRelativeTick(new Position(fromBar, 0));
            }
        }

        sequencer.setTickPosition(tick);

        updateCurrentPosition(fromBar, 0);
    }

    /**
     * Update the muted tracks
     * <p>
     *
     * @param insMix
     * @param mapRvTrack The map which provides the track index corresponding to each rhythmvoice.
     */
    private void updateTrackMuteState(InstrumentMix insMix, HashMap<RhythmVoice, Integer> mapRvTrack)
    {
        assert !state.equals(State.DISABLED);   //NOI18N

        boolean b = insMix.isMute();
        RhythmVoice rv = mgContext.getMidiMix().geRhythmVoice(insMix);
        if (rv instanceof UserChannelRvKey)
        {
            return;
        }
        Integer trackIndex = mapRvTrack.get(rv);
        if (trackIndex != null)
        {
            sequencer.setTrackMute(trackIndex, b);
//            if (sequencer.getTrackMute(trackIndex) != b)
//            {
//                LOGGER.log(Level.SEVERE, "updateTrackMuteState() can''t mute on/off track number: {0} mute={1} insMix={2}", new Object[]  //NOI18N
//                {
//                    trackIndex, b, insMix
//                });
//            }
        } else
        {
            // Might be null if mapRvTrack was reset because of a MidiMix change and sequence has not been rebuilt yet.
            // Also if multi-song and play with a context on only 1 rhythm.
        }
    }

    private void fireChordSymbolChanged(CLI_ChordSymbol cliCs)
    {
        if (cliCs == null)
        {
            throw new IllegalArgumentException("cliCs=" + cliCs);
        }
        fireLatencyAwareEvent(() ->
        {
            for (PlaybackListener pl : playbackListeners.toArray(new PlaybackListener[0]))
            {
                pl.chordSymbolChanged(cliCs);
            }
        });
    }

    private void fireBeatChanged(Position oldPos, Position newPos)
    {
        fireLatencyAwareEvent(() ->
        {
            for (PlaybackListener pl : playbackListeners.toArray(new PlaybackListener[0]))
            {
                pl.beatChanged(oldPos, newPos);
            }
        });
    }

    private void fireSongPartChanged(SongPart newSpt)
    {
        fireLatencyAwareEvent(() ->
        {
            for (PlaybackListener pl : playbackListeners.toArray(new PlaybackListener[0]))
            {
                pl.songPartChanged(newSpt);
            }
        });
    }

    private void fireNoteOn(long tick, int channel, int pitch, int velocity)
    {
        fireLatencyAwareEvent(() ->
        {
            for (NoteListener l : noteListeners.toArray(new NoteListener[0]))
            {
                l.noteOn(tick, channel, pitch, velocity);
            }
        });
    }

    private void fireNoteOff(long tick, int channel, int pitch)
    {
        fireLatencyAwareEvent(() ->
        {
            for (NoteListener l : noteListeners.toArray(new NoteListener[0]))
            {
                l.noteOff(tick, channel, pitch);
            }
        });
    }

    private void fireBarChanged(int oldBar, int newBar)
    {
        fireLatencyAwareEvent(() ->
        {
            for (PlaybackListener pl : playbackListeners.toArray(new PlaybackListener[0]))
            {
                pl.barChanged(oldBar, newBar);
            }
        });
    }

    private void fireMidiActivity(long tick, int channel)
    {
        fireLatencyAwareEvent(() ->
        {
            for (PlaybackListener pl : playbackListeners.toArray(new PlaybackListener[0]))
            {
                pl.midiActivity(tick, channel);
            }
        });
    }

    /**
     * Fire an event on the EDT after a time delay to take into account the current output synth latency.
     * <p>
     * Active timers are available in audioLatencyTimers.
     *
     * @param r
     */
    private void fireLatencyAwareEvent(Runnable r)
    {
        if (audioLatency == 0)
        {
            SwingUtilities.invokeLater(r);
        } else
        {
            Timer t = new Timer(audioLatency, evt ->
            {
                r.run();            // Will be run on the EDT
            });
            t.addActionListener(evt ->
            {
                synchronized (audioLatencyTimers)
                {
                    audioLatencyTimers.remove(t);
                }
            });
            synchronized (audioLatencyTimers)
            {
                audioLatencyTimers.add(t);
            }
            t.setRepeats(false);
            t.start();
        }
    }

    /**
     * When sequencer is stopped or paused, make sure there is no pending events.
     */
    private void clearPendingEvents()
    {
        for (Iterator<Timer> it = audioLatencyTimers.iterator(); it.hasNext();)
        {
            it.next().stop();
            it.remove();
        }
    }

    /**
     * Start the sequencer with the bug fix (tempo reset at 120 upon each start) + possibly fire a chord change event.
     * <p>
     * If sequencer is already playing or is disabled, do nothing.
     * <p>
     * If playbackSession is a SongPlaybackSession, and if there is no chord symbol at current position, then fire a chord change
     * event using the previous chord symbol (ie the current chord symbol at this start position).
     */
    private void seqStart()
    {
        switch (state)
        {
            case DISABLED:
                LOGGER.warning("seqStart() called with state=" + state);
                break;

            case PLAYING:
                // Nothing
                break;

            case STOPPED:
            case PAUSED:

                if (playbackSession instanceof SongPlaybackSession)
                {
                    SongPlaybackSession spSession = (SongPlaybackSession) playbackSession;
                    var mgContext = spSession.getMusicGenerationContext();


                    // Fire chord symbol change if no chord symbol at current position (current chord symbol is the previous one)
                    // Fire a song part change event
                    long relativeTick = sequencer.getTickPosition() - spSession.getTickStart();    // Can be negative if precount is ON
                    Position posStart = mgContext.getPosition(relativeTick);
                    if (posStart != null)
                    {
                        CLI_ChordSymbol lastCliCs = spSession.getContextChordGetSequence().getChordSymbol(posStart); // Process substitute chord symbols
                        if (lastCliCs != null)
                        {
                            // Fire the event
                            fireChordSymbolChanged(lastCliCs);
                        }

                        SongPart spt = mgContext.getSong().getSongStructure().getSongPart(posStart.getBar());
                        fireSongPartChanged(spt);
                    }
                }


                sequencer.start();
                // JDK -11 BUG: start() resets tempo at 120 !
                sequencer.setTempoInBPM(MidiConst.SEQUENCER_REF_TEMPO);

                break;
            default:
                throw new AssertionError(state.name());
        }
    }

    private void updateCurrentPosition(int bar, float beat)
    {
        assert !state.equals(State.DISABLED);   //NOI18N
        Position oldPos = new Position(currentBeatPosition);
        currentBeatPosition.setBar(bar);
        currentBeatPosition.setBeat(beat);
        fireBeatChanged(oldPos, new Position(currentBeatPosition));
        if (beat == 0)
        {
            fireBarChanged(oldPos.getBar(), bar);
        }
    }

    private void songTempoChanged(float tempoInBPM)
    {
        songTempoFactor = tempoInBPM / MidiConst.SEQUENCER_REF_TEMPO;
        updateTempoFactor();
    }

    /**
     * Update the tempo factor of the sequencer.
     * <p>
     * Depends on songPartTempoFactor and songTempoFactor. Sequencer tempo must always be MidiConst.SEQUENCER_REF_TEMPO.
     *
     * @param bpm
     */
    private void updateTempoFactor()
    {
        assert !state.equals(State.DISABLED);   //NOI18N

        // Recommended way instead of setTempoInBpm() which cause problems when playback is looped
        float f = songPartTempoFactor * songTempoFactor;
        sequencer.setTempoFactor(f);
    }

    private void checkMidi() throws MusicGenerationException
    {
        if (JJazzMidiSystem.getInstance().getDefaultOutDevice() == null)
        {
            throw new MusicGenerationException(ResUtil.getString(getClass(), "ERR_NoMidiOutputDeviceSet"));
        }
    }

    private SongPlaybackSession getSongPlaybackContext()
    {
        return playbackSession instanceof SongPlaybackSession ? (SongPlaybackSession) playbackSession : null;
    }

    private MusicGenerationContext getMusicGenerationContext()
    {
        return playbackSession instanceof SongPlaybackSession ? ((SongPlaybackSession) playbackSession).getMusicGenerationContext() : null;
    }

    /**
     * Set the sequencer to play the session sequence with the session settings, update playbackSession.
     * <p>
     * Work even is sequencer is playing.
     *
     * @param pSession
     * @throws MusicGenerationException
     */
    private void applySession(PlaybackSession pSession) throws MusicGenerationException
    {
        if (playbackSession == pSession)
        {
            return;
        }

        synchronized (lock)
        {
            if (playbackSession != null)
            {
                playbackSession.removeChangeListener(this);
            }
            playbackSession = null;     // Make sure meta/control listeners do nothing with playbackSession while we update the sequencer's sequence
        }


        // Set sequence
        try
        {
            // This also resets Mute, Solo, LoopStart/End points, LoopCount
            sequencer.setSequence(pSession.getSequence()); // Can raise InvalidMidiDataException


            sequencer.setLoopStartPoint(pSession.getTickStart());
            sequencer.setLoopEndPoint(pSession.getTickEnd());
            sequencer.setLoopCount(loopCount);

        } catch (InvalidMidiDataException ex)
        {
            throw new MusicGenerationException(ex.getLocalizedMessage());
        }


        synchronized (lock)
        {
            playbackSession = pSession;
            playbackSession.addChangeListener(this);
            // From there meta/controller listeners can work
        }


        // Update muted tracks state based on the MidiMix
        if (pSession instanceof SongPlaybackSession)
        {
            SongPlaybackSession spSession = (SongPlaybackSession) pSession;
            updateAllTracksMuteState(spSession.getMusicGenerationContext().getMidiMix(), spSession.getRvTrackId());
        }

        // Optional click track
        if (pSession.getClickTrackId() > -1)
        {
            sequencer.setTrackMute(pSession.getClickTrackId(), !ClickManager.getInstance().isPlaybackClickEnabled());
        }

    }


    /**
     * Manage both the PlaybackSession or SongPlaybackSession cases.
     *
     * @param pSession
     * @param fromBarIndex Ignored if pSession is not a SongPlaybackSession
     * @param fromTick Ignored if pSession is a SongPlaybackSession
     * @throws MusicGenerationException
     * @throws PropertyVetoException
     */
    private void playImpl(PlaybackSession pSession, int fromBarIndex, long fromTick) throws MusicGenerationException, PropertyVetoException
    {
        if (pSession == null)
        {
            throw new IllegalArgumentException("pSession=" + pSession + " fromBarIndex=" + fromBarIndex);
        }


        // Specific checks depending on 
        SongPlaybackSession spSession = (pSession instanceof SongPlaybackSession) ? (SongPlaybackSession) pSession : null;
        MusicGenerationContext mgContext = spSession != null ? spSession.getMusicGenerationContext() : null;
        if (spSession != null)
        {
            if (!spSession.getMusicGenerationContext().getBarRange().contains(fromBarIndex))
            {
                throw new IllegalArgumentException("spSession.getMusicGenerationContext()=" + spSession.getMusicGenerationContext() + ", fromBarIndex=" + fromBarIndex);   //NOI18N
            }
            if (mgContext.getBarRange().isEmpty())
            {
                // Throw an exception to let the UI roll back (eg play stateful button)
                throw new MusicGenerationException(ResUtil.getString(getClass(), "NOTHING TO PLAY"));
            }
        }


        // Check that a Midi ouput device is set
        checkMidi();                // throws MusicGenerationException


        // Check that all listeners are OK to start playback
        vcs.fireVetoableChange(PROPVETO_PRE_PLAYBACK, null, mgContext);  // can raise PropertyVetoException


        // Log the play event        
        Analytics.setPropertiesOnce(Analytics.buildMap("First Play", Analytics.toStdDateTimeString()));
        Analytics.incrementProperties("Nb Play", 1);
        var mapParams = (mgContext != null)
                ? Analytics.buildMap("Bar Range", mgContext.getBarRange().toString(), "Rhythms", Analytics.toStrList(mgContext.getUniqueRhythms()))
                : Analytics.buildMap("Rhythms", Analytics.toStrList(mgContext.getUniqueRhythms()));
        Analytics.logEvent("Play", mapParams);


        // Get new clean session if required
//        if (pSession.getState().equals(PlaybackSession.State.OUTDATED))
//        {
//            PlaybackSession old = pSession;
//            pSession = pSession.getCopyInNewState();
//            old.cleanup();
//        }
        // Generate sequence if required
        if (pSession.getState().equals(PlaybackSession.State.NEW))
        {
            pSession.generate();
        }


        if (debugPlayedSequence)
        {
            String songName = mgContext != null ? mgContext.getSong().getName() : "unknown";
            LOGGER.info("play() song=" + songName + " sequence :"); //NOI18N
            LOGGER.info(MidiUtilities.toString(pSession.getSequence())); //NOI18N
        }


        // Apply the new session in the sequencer  ==> from now playbackSession = spSession
        applySession(pSession);


        // Set sequencer start position
        if (spSession != null)
        {
            setPosition(fromBarIndex);
        } else
        {
            sequencer.setTickPosition(pSession.getTickStart());
        }


        // Start or restart the sequencer
        seqStart();


        State old = this.getState();
        state = State.PLAYING;
        pcs.firePropertyChange(PROP_STATE, old, state);

    }


    /**
     * Update the track mute state depending on the MidiMix mute state.
     *
     * @param mm
     * @param mapRvTrackId
     */
    private void updateAllTracksMuteState(MidiMix mm, HashMap<RhythmVoice, Integer> mapRvTrackId)
    {
        for (RhythmVoice rv : mm.getRhythmVoices())
        {
            if (!(rv instanceof UserChannelRvKey))
            {
                InstrumentMix insMix = mm.getInstrumentMixFromKey(rv);
                Integer trackId = mapRvTrackId.get(rv);
                if (trackId != null)
                {
                    sequencer.setTrackMute(trackId, insMix.isMute());
                } else
                {
                    // It can be null, e.g. if multi-rhythm song and context is only on one of the rhythms.
                }
            }
        }
    }


    /**
     * Our Midi Receiver used to fire events to NoteListeners and PlaybackListener.midiActivity().
     * <p>
     * Events are fired taking into account the current output synth latency.
     * <p>
     */
    private class McReceiver implements Receiver
    {

        /**
         * Fire only one Activity event for this period of time, even if there are several notes.
         */
        public static final int ACTIVITY_MIN_PERIOD_MS = 100;

        // Store the last Note On millisecond position for each note. Use -1 if initialized.
        private long lastNoteOnMs[] = new long[16];
        private boolean enabled;

        public McReceiver()
        {
            setEnabled(true);
        }

        /**
         * @return the enabled
         */
        public boolean isEnabled()
        {
            return enabled;
        }

        /**
         * Enable or disable this receiver.
         *
         *
         * @param enabled the enabled to set
         */
        public void setEnabled(boolean enabled)
        {
            if (this.enabled != enabled)
            {
                this.enabled = enabled;
                reset();
            }
        }

        @Override
        public void send(MidiMessage msg, long timeStamp)
        {
            if (!enabled)
            {
                return;
            }

            if (msg instanceof ShortMessage)
            {
                ShortMessage sm = (ShortMessage) msg;

                if (playbackListeners.isEmpty() && noteListeners.isEmpty())
                {
                    return;
                }


                if (sm.getCommand() == ShortMessage.NOTE_ON)
                {
                    int pitch = sm.getData1();
                    int velocity = sm.getData2();
                    if (velocity > 0)
                    {
                        noteOnReceived(sm.getChannel(), pitch, velocity);
                    } else
                    {
                        noteOffReceived(sm.getChannel(), pitch);
                    }


                } else if (sm.getCommand() == ShortMessage.NOTE_OFF)
                {
                    int pitch = sm.getData1();
                    noteOffReceived(sm.getChannel(), pitch);
                }
            }
        }

        @Override
        public void close()
        {
            // Do nothing
        }

        /**
         * Reset internal state.
         */
        private void reset()
        {
            for (int i = 0; i < 16; i++)
            {
                lastNoteOnMs[i] = -1;
            }
        }

        private void noteOnReceived(int channel, int pitch, int velocity)
        {
            if (enabled)
            {
                // Midi activity only once for a given channel in the ACTIVITY_MIN_PERIOD_MS period 
                long pos = System.currentTimeMillis();
                long lastPos = lastNoteOnMs[channel];
                if (lastPos < 0 || (pos - lastPos) > ACTIVITY_MIN_PERIOD_MS)
                {
                    fireMidiActivity(-1, channel);
                }
                lastNoteOnMs[channel] = pos;


                fireNoteOn(-1, channel, pitch, velocity);
            }
        }

        private void noteOffReceived(int channel, int pitch)
        {
            if (enabled)
            {
                lastNoteOnMs[channel] = -1;
                fireNoteOff(-1, channel, pitch);
            }
        }

    }


}
