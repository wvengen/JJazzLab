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
package org.jjazz.pianoroll.api;

import com.google.common.base.Preconditions;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.jjazz.harmony.api.TimeSignature;
import org.jjazz.leadsheet.chordleadsheet.api.item.Position;
import org.jjazz.midi.api.DrumKit;
import org.jjazz.phrase.api.NoteEvent;
import org.jjazz.phrase.api.Phrase;
import org.jjazz.phrase.api.Phrases;
import org.jjazz.pianoroll.EditToolBar;
import org.jjazz.pianoroll.MouseDragLayerUI;
import org.jjazz.pianoroll.NotesPanel;
import org.jjazz.pianoroll.RulerPanel;
import org.jjazz.pianoroll.actions.CopyNotes;
import org.jjazz.pianoroll.actions.CutNotes;
import org.jjazz.pianoroll.actions.DeleteSelection;
import org.jjazz.pianoroll.actions.MoveSelectionLeft;
import org.jjazz.pianoroll.actions.MoveSelectionRight;
import org.jjazz.pianoroll.actions.PasteNotes;
import org.jjazz.pianoroll.actions.ResizeSelection;
import org.jjazz.pianoroll.actions.SelectAllNotes;
import org.jjazz.pianoroll.actions.TransposeSelectionDown;
import org.jjazz.pianoroll.actions.TransposeSelectionUp;
import org.jjazz.pianoroll.actions.ZoomToFit;
import org.jjazz.pianoroll.edittools.EraserTool;
import org.jjazz.pianoroll.edittools.PencilTool;
import org.jjazz.pianoroll.edittools.SelectionTool;
import org.jjazz.pianoroll.spi.PianoRollEditorSettings;
import org.jjazz.quantizer.api.Quantization;
import org.jjazz.song.api.Song;
import org.jjazz.ui.keyboardcomponent.api.KeyboardComponent;
import org.jjazz.ui.keyboardcomponent.api.KeyboardRange;
import org.jjazz.ui.utilities.api.MidiFileDragInTransferHandler;
import static org.jjazz.ui.utilities.api.Utilities.getGenericControlKeyStroke;
import org.jjazz.ui.utilities.api.Zoomable;
import org.jjazz.undomanager.api.JJazzUndoManager;
import org.jjazz.undomanager.api.JJazzUndoManagerFinder;
import org.jjazz.util.api.FloatRange;
import org.jjazz.util.api.IntRange;
import org.jjazz.util.api.ResUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 * A piano roll editor of a phrase.
 * <p>
 * It can edit whole or part of a Phrase.
 * <p>
 * Its Lookup must contain :<br>
 * - editor's ActionMap<br>
 * - editor's Zoomable instance
 */
public class PianoRollEditor extends JPanel implements PropertyChangeListener
{

    /**
     * newValue=false. This property change event is fired ONLY once, when the editor is destroyed (cleanup() is called).
     */
    public static final String PROP_EDITOR_ALIVE = "EditorAlive";
    /**
     * oldValue=old Phrase model, newValue=new Phrase model.
     */
    public static final String PROP_MODEL_PHRASE = "PhraseModel";
    /**
     * oldValue=old channel, newValue=new channel model.
     */
    public static final String PROP_MODEL_CHANNEL = "PhraseChannel";
    /**
     * oldValue=sorted list of NoteViews whose state has changeds, newValue=selected state
     */
    public static final String PROP_SELECTED_NOTE_VIEWS = "NoteViewSelection";
    /**
     * oldValue=old tool, newValue=new tool
     */
    public static final String PROP_ACTIVE_TOOL = "ActiveTool";
    /**
     * newValue=boolean
     */
    public static final String PROP_SNAP_ENABLED = "SnapEnabled";
    /**
     * oldValue=old quantization value, newValue=new quantization value
     */
    public static final String PROP_QUANTIZATION = "Quantization";
    /**
     * newValue=playback point position in beats
     */
    public static final String PROP_PLAYBACK_POINT_POSITION = "PlaybackPointPosition";
    private static final float MAX_WIDTH_FACTOR = 1.5f;
    private NotesPanel notesPanel;
    private KeyboardComponent keyboard;
    private RulerPanel rulerPanel;
    private JScrollPane scrollpane;
    private MouseDragLayerUI mouseDragLayerUI;
    private JLayer mouseDragLayer;
    private ZoomValue zoomValue;
    private Phrase model;
    private DrumKit.KeyMap keyMap;
    private final PianoRollEditorSettings settings;
    private final TreeSet<NoteView> selectedNoteViews = new TreeSet<>((nv1, nv2) -> nv1.getModel().compareTo(nv2.getModel()));
    private Quantization quantization;
    private final Lookup lookup;
    private JJazzUndoManager undoManager;
    private final InstanceContent generalLookupContent;
    private int rulerStartBar;
    private EditTool activeTool;
    private final EditToolProxyMouseListener editToolProxyMouseListener;
    private final GenericMouseListener genericMouseListener;
    private static final Logger LOGGER = Logger.getLogger(PianoRollEditor.class.getSimpleName());
    private boolean snapEnabled;
    private float playbackPointPosition;
    private boolean playbackAutoScrollEnabled;
    private final List<EditTool> editTools;
    private Song song;
    private FloatRange beatRange;
    private int channel = 0;
    private NavigableMap<Float, TimeSignature> mapPosTimeSignature;
    private int phraseStartBar;


    /**
     * Create a piano roll editor for a dummy phrase model.
     *
     * @param settings Can't be null
     */
    public PianoRollEditor(PianoRollEditorSettings settings)
    {
        Preconditions.checkNotNull(settings);

        LOGGER.fine("PianoRollEditor() -- ");

        this.settings = settings;
        this.phraseStartBar = 0;
        this.rulerStartBar = this.phraseStartBar;
        this.model = new Phrase(0, false);
        this.channel = 0;
        this.beatRange = new FloatRange(0, 8f);
        this.keyMap = null;
        this.quantization = Quantization.ONE_QUARTER_BEAT;
        this.mapPosTimeSignature = new TreeMap<>();
        mapPosTimeSignature.put(0f, TimeSignature.FOUR_FOUR);


        // Be notified of changes, note added, moved, removed, set
        model.addPropertyChangeListener(this);


        // Default undo manager to listen for model changes
        undoManager = new JJazzUndoManager();
        model.addUndoableEditListener(undoManager);


        // The lookup for other stuff, before createUI()
        generalLookupContent = new InstanceContent();
        generalLookupContent.add(new PianoRollZoomable());
        generalLookupContent.add(getActionMap());
        lookup = new AbstractLookup(generalLookupContent);


        editTools = Arrays.asList(new SelectionTool(this), new PencilTool(this), new EraserTool(this));
        activeTool = editTools.get(0);


        // Build the UI
        createUI();


        // NotesPanel mouse listeners
        genericMouseListener = new GenericMouseListener();
        notesPanel.addMouseListener(genericMouseListener);
        notesPanel.addMouseMotionListener(genericMouseListener);
        notesPanel.addMouseWheelListener(genericMouseListener);
        editToolProxyMouseListener = new EditToolProxyMouseListener();
        notesPanel.addMouseListener(editToolProxyMouseListener);
        notesPanel.addMouseMotionListener(editToolProxyMouseListener);
        notesPanel.addMouseWheelListener(editToolProxyMouseListener);


        // By default enable the drag in transfer handler
        notesPanel.setTransferHandler(new MidiFileDragInTransferHandlerImpl());


        setKeyboardActions();


        // Normal zoom
        zoomValue = new ZoomValue(20, keyMap == null ? 30 : 60);        // Drum notes need more heigth
        notesPanel.setScaleFactorX(toScaleFactorX(zoomValue.hValue()));
        float yFactor = toScaleFactorY(zoomValue.vValue());
        keyboard.setScaleFactor(yFactor, Math.min(MAX_WIDTH_FACTOR, yFactor));


        // Add the notes
        addNotes(model.getNotes());

    }

    /**
     * Associate an optional song to the editor.
     * <p>
     * Put the song in the editor's lookup. Song undo manager is used. The ruler can show the chord symbols and listen to chord symbols
     * changes.<p>
     * <p>
     * This method can be called only once.
     *
     * @param song Can't be null
     */
    public void setSong(Song song)
    {
        Preconditions.checkNotNull(song);
        if (this.song != null)
        {
            throw new IllegalStateException("this.song is already set: " + this.song);
        }

        this.song = song;
        generalLookupContent.add(song);
        rulerPanel.setSong(song);
        setUndoManager(JJazzUndoManagerFinder.getDefault().get(getSong()));

    }

    public Song getSong()
    {
        return song;
    }

    /**
     * Get the channel of the editor.
     * <p>
     * The channel is used e.g. when "hear preview" or "solo mode" is activated, or when notes are imported from a dragged Midi file.
     *
     * @return
     * @see #setModel(int, org.jjazz.util.api.FloatRange, org.jjazz.phrase.api.Phrase, int, java.util.NavigableMap,
     * org.jjazz.midi.api.DrumKit.KeyMap)
     */
    public int getChannel()
    {
        return channel;
    }

    /**
     * The available EditTools.
     *
     * @return
     */
    public List<EditTool> getEditTools()
    {
        return editTools;
    }


    /**
     * Optional view-only phrases shown in the background of the editor.
     * <p>
     * The specified phrases are shown faded in the background in order to facilite the editing of the Phrase model. E.g. if the edited
     * phrase is a bass line, you can use this method to make the corresponding drums phrase also visible.
     *
     * @param mapChannelPhrases A name associated to a Phrase.
     */
    public void setBackgroundPhases(Map<Integer, Phrase> mapChannelPhrases)
    {
        notesPanel.setBackgroundPhrases(mapChannelPhrases);
    }

    /**
     * Get the optional view-only phrases shown in the background of the editor.
     *
     * @return Can be empty
     */
    public Map<Integer, Phrase> getBackgroundPhrases()
    {
        return notesPanel.getBackgroundPhrases();
    }

    /**
     * Get the Phrase edited by this editor.
     *
     * @return
     */
    public Phrase getModel()
    {
        return model;
    }

    /**
     * Set the edited model.
     * <p>
     * Fire a PROP_MODEL_PHRASE change event if p is different from existing model.
     *
     * @param p              The phrase model
     * @param beatRange      The edited part of the phrase model
     * @param phraseStartBar The start bar corresponding to beatRange.from
     * @param rulerStartBar  The start bar displayed on the ruler corresponding to beatRange.from. Usually it has the same value than
     *                       phraseStartBar, but it can be different to make the edited range appear at a different bar range.
     * @param channel        The Midi channel of the edited Phrase (p.getChannel() is ignored).
     * @param mapPosTs       The position of each time signature. Must have at least 1 entry at beatRange.from position or before.
     * @param kMap           If null means it's a melodic phrase
     */
    public void setModel(Phrase p, FloatRange beatRange, int phraseStartBar, int rulerStartBar, int channel, NavigableMap<Float, TimeSignature> mapPosTs, DrumKit.KeyMap kMap)
    {
        Preconditions.checkNotNull(p);
        Preconditions.checkNotNull(beatRange);
        Preconditions.checkArgument(rulerStartBar >= 0);
        Preconditions.checkArgument(phraseStartBar >= 0);
        Preconditions.checkArgument(!mapPosTs.isEmpty() && mapPosTs.firstKey() <= beatRange.from, "mapPosTs=%s  beatRange=%s",
                mapPosTs, beatRange);

        LOGGER.log(Level.FINE, "setModel() -- p={0}", p);


        if (p.equals(model) && this.channel == channel && rulerStartBar == this.rulerStartBar && beatRange.equals(this.beatRange)
                && this.mapPosTimeSignature.equals(mapPosTs) && Objects.equals(kMap, this.keyMap) && phraseStartBar == this.phraseStartBar)
        {
            return;
        }


        removeNotes(model.getNotes());


        model.removePropertyChangeListener(this);
        model.removeUndoableEditListener(undoManager);


        var oldModel = model;
        model = p;
        this.rulerStartBar = rulerStartBar;
        this.phraseStartBar = phraseStartBar;
        keyMap = kMap;
        int oldChannel = this.channel;
        this.channel = channel;
        this.beatRange = beatRange;
        this.mapPosTimeSignature = mapPosTs;
        labelNotes(keyboard, keyMap);


        model.addPropertyChangeListener(this);
        model.addUndoableEditListener(undoManager);


        // Update the subcomponents                  
        notesPanel.getXMapper().refresh();
        notesPanel.repaint();
        rulerPanel.revalidate();
        rulerPanel.repaint();


        // Add the notes
        addNotes(model.getNotes());


        notesPanel.scrollToFirstNote();
        notesPanel.revalidate();
        notesPanel.repaint();


        firePropertyChange(PROP_MODEL_PHRASE, oldModel, model);
        firePropertyChange(PROP_MODEL_CHANNEL, oldChannel, this.channel);

    }


    /**
     * Get the bar index displayed on the ruler corresponding to getBeatRange().from.
     * <p>
     * Usually identical to getPhraseStartBar(), but it can be different to make the edited range appear at a different bar in the ruler.
     *
     * @return
     */
    public int getRulerStartBar()
    {
        return rulerStartBar;
    }

    /**
     * Get the ruler bar range.
     * <p>
     * An IntRange starting at getRulerStartbar() with size equals to getPhraseBarRange().
     *
     * @return
     */
    public IntRange getRulerBarRange()
    {
        return new IntRange(rulerStartBar, rulerStartBar + getPhraseBarRange().size() - 1);
    }


    /**
     * The time signature at the specified beat position.
     *
     * @param posInBeats Must be in the beat range
     * @return Can't be null
     */
    public TimeSignature getTimeSignature(float posInBeats)
    {
        Preconditions.checkArgument(beatRange.contains(posInBeats, false));
        return mapPosTimeSignature.floorEntry(posInBeats).getValue();
    }

    /**
     * Get all the time signatures with their position.
     *
     * @return Can't be empty.
     */
    public NavigableMap<Float, TimeSignature> getTimeSignatures()
    {
        return mapPosTimeSignature;
    }


    public FloatRange getPhraseBeatRange()
    {
        return beatRange;
    }

    /**
     * Get the bar index corresponding to getPhraseBeatRange().from.
     *
     * @return
     */
    public int getPhraseStartBar()
    {
        return phraseStartBar;
    }

    public IntRange getPhraseBarRange()
    {
        return notesPanel.getXMapper().getBarRange();
    }

    /**
     * Get the lookup of this editor.
     * <p>
     * Lookup contains
     *
     * @return
     */
    public Lookup getLookup()
    {
        return lookup;
    }


    /**
     * Convenience method which returns true if getDrumKeyMap() is non null.
     *
     * @return
     */
    public boolean isDrums()
    {
        return getDrumKeyMap() != null;
    }


    /**
     * The drum key map used by the edited phrase.
     *
     * @return Null if it's a melodic phrase.
     */
    public DrumKit.KeyMap getDrumKeyMap()
    {
        return keyMap;
    }


    /**
     * Get the graphical settings of this editor.
     *
     * @return
     */
    public PianoRollEditorSettings getSettings()
    {
        return settings;
    }

    /**
     * Clean up everything so component can be garbaged.
     * <p>
     * Fire a PROP_EDITOR_ALIVE with value=false.
     */
    public void cleanup()
    {
        LOGGER.fine("cleanup() --");
        rulerPanel.cleanup();
        notesPanel.cleanup();
        model.removeUndoableEditListener(undoManager);
        model.removePropertyChangeListener(this);
        firePropertyChange(PROP_EDITOR_ALIVE, true, false);
    }

    /**
     * Set the editor zoom value.
     *
     * @param zoom
     */
    public void setZoom(ZoomValue zoom)
    {
        Preconditions.checkNotNull(zoom);
        LOGGER.log(Level.FINE, "setZoom() -- this.zoomvalue={0} zoom={1}", new Object[]
        {
            this.zoomValue, zoom
        });


        if (zoomValue == null || zoomValue.hValue() != zoom.hValue())
        {
            // Save position center
            float saveCenterPosInBeats = getVisibleBeatRange().getCenter();

            // This updates notesPanel preferred size and calls revalidate(), which will update the size on the EDT
            notesPanel.setScaleFactorX(toScaleFactorX(zoom.hValue()));

            // Restore position at center
            // Must be done later on the EDT to get the notesPanel effectively resized after previous command, so that
            // XMapper() will be refreshed before calling scrollToCenter
            SwingUtilities.invokeLater(() -> scrollToCenter(saveCenterPosInBeats));

        }

        if (zoomValue == null || zoomValue.vValue() != zoom.vValue())
        {
            // Save pitch at center
            int saveCenterPitch = (int) getVisiblePitchRange().getCenter();


            // Scale the keyboard
            float factor = toScaleFactorY(zoom.vValue());


            // Because keyboard is in RIGHT orientation factorX impacts the keyboard height.
            // We limit factorY because we don't want the keyboard to get wide
            // This updates keyboard preferred size and calls revalidate(), which will update the size         
            keyboard.setScaleFactor(factor, Math.min(MAX_WIDTH_FACTOR, factor));


            // This is to avoid a difficult bug when zooming in/out vertically fast with mouse-wheel: sometimes 2 successive zoom events
            // occur BEFORE the keyboard component resized event (triggered by setScaleFactor() just above) is fired. In this case 
            // the refresh of YMapper() is done too late (see component size listener in YMapper), and scrollToCenter() below fails because YMapper is not up to date.
            // So we force the refresh now.
            notesPanel.getYMapper().refresh(keyboard.getPreferredSize().height);


            // restore pitch at center
            // Note that surprisingly using SwingUtilities.invokeLater() on scrollToCenter() did not solve the bug explained above
            scrollToCenter(saveCenterPitch);
        }

        zoomValue = zoom;
    }

    /**
     * Get the editor zoom value.
     *
     * @return
     */
    public ZoomValue getZoom()
    {
        return zoomValue;
    }


    /**
     * Set the display quantization.
     * <p>
     * Fire a PROP_QUANTIZATION change event.
     *
     * @param q Accepted values are BEAT, HALF_BEAT, ONE_THIRD_BEAT, ONE_QUARTER_BEAT, ONE_SIXTH_BEAT.
     */
    public void setQuantization(Quantization q)
    {
        Preconditions.checkArgument(EnumSet.of(Quantization.BEAT,
                Quantization.HALF_BEAT,
                Quantization.ONE_THIRD_BEAT,
                Quantization.ONE_QUARTER_BEAT,
                Quantization.ONE_SIXTH_BEAT).contains(q));
        if (quantization.equals(q))
        {
            return;
        }
        var old = quantization;
        quantization = q;
        firePropertyChange(PROP_QUANTIZATION, old, quantization);
    }

    /**
     * Get the display quantization.
     *
     * @return Can't be null
     */
    public Quantization getQuantization()
    {
        return quantization;
    }

    public boolean isNoteSelected(NoteEvent ne)
    {
        return selectedNoteViews.contains(getNoteView(ne));
    }

    public void selectNote(NoteEvent ne, boolean b)
    {
        PianoRollEditor.this.selectNotes(Arrays.asList(ne), b);
    }

    /**
     * Select or unselect NoteViews.
     * <p>
     * Fire a PROP_SELECTED_NOTE_VIEWS change event.
     *
     * @param notes
     * @param b
     */
    public void selectNotes(Collection<NoteEvent> notes, boolean b)
    {
        if (notes.isEmpty())
        {
            return;
        }

        List<NoteView> nvs = new ArrayList<>();
        for (var n : notes)
        {
            var nv = getNoteView(n);
            if (nv == null)
            {
                continue;
            }
            if (b && !selectedNoteViews.contains(nv))
            {
                selectedNoteViews.add(nv);
                nvs.add(nv);
            } else if (!b && selectedNoteViews.contains(nv))
            {
                selectedNoteViews.remove(nv);
                nvs.add(nv);
            }
            nv.setSelected(b);
        }

        if (!nvs.isEmpty())
        {
            firePropertyChange(PROP_SELECTED_NOTE_VIEWS, nvs, b);
        }
    }

    /**
     * Enable or disable the snap to quantization feature.
     * <p>
     * Fire a PROP_SNAP_ENABLED change event.
     *
     * @param b
     */
    public void setSnapEnabled(boolean b)
    {
        if (b == snapEnabled)
        {
            return;
        }
        snapEnabled = b;
        firePropertyChange(PROP_SNAP_ENABLED, !b, b);
    }

    /**
     * Check if the snap to quantization feature is enabled.
     *
     * @return
     */
    public boolean isSnapEnabled()
    {
        return snapEnabled;
    }

    /**
     * Get the NoteView associated to the specified NoteEvent.
     *
     * @param ne
     * @return Can be null
     */
    public NoteView getNoteView(NoteEvent ne)
    {
        return notesPanel.getNoteView(ne);
    }

    /**
     * Get all the NoteViews sorted by NoteEvent natural order.
     * <p>
     * @return
     */
    public List<NoteView> getNoteViews()
    {
        return notesPanel.getNoteViews();
    }

    /**
     * Unselect all notes.
     */
    public void unselectAll()
    {
        selectNotes(NoteView.getNotes(selectedNoteViews), false);
    }

    /**
     * Get the currently selected NoteViews sorted by NoteEvent natural order.
     *
     * @return
     */
    public List<NoteView> getSelectedNoteViews()
    {
        return new ArrayList<>(selectedNoteViews);
    }

    /**
     * Set the active EditTool.
     * <p>
     * Fire a PROP_ACTIVE_TOOL change event.
     *
     * @param tool
     */
    public void setActiveTool(EditTool tool)
    {
        Preconditions.checkNotNull(tool);
        if (activeTool == tool)
        {
            return;
        }
        var old = activeTool;
        activeTool = tool;
        notesPanel.setCursor(activeTool.getCursor());
        firePropertyChange(PROP_ACTIVE_TOOL, old, activeTool);
    }

    /**
     * Get the actived EditTool.
     *
     * @return Can't be null
     */
    public EditTool getActiveTool()
    {
        return activeTool;
    }

    public boolean isPlaybackAutoScrollEnabled()
    {
        return playbackAutoScrollEnabled;
    }

    public void setPlaybackAutoScrollEnabled(boolean playbackAutoScrollEnabled)
    {
        this.playbackAutoScrollEnabled = playbackAutoScrollEnabled;
    }

    /**
     * Show (or hide) a playback point in the editor at specified phrase position.
     * <p>
     * If pos is &lt; 0 or out of the editor bounds, nothing is shown. Fire a PROP_PLAYBACK_POINT_POSITION change event.
     *
     * @param pos The phrase position in beats.
     */
    public void showPlaybackPoint(float pos)
    {
        if (Float.floatToIntBits(pos) == Float.floatToIntBits(playbackPointPosition))
        {
            return;
        }
        float old = playbackPointPosition;
        playbackPointPosition = pos;


        int xPos = -1;
        if (getPhraseBeatRange().contains(playbackPointPosition, false))
        {
            xPos = notesPanel.getXMapper().getX(pos);
        }

        rulerPanel.showPlaybackPoint(xPos);
        mouseDragLayerUI.showPlaybackPoint(xPos);
        mouseDragLayer.repaint();


        // Scroll if required so that playback point is on the left side
        var visibleBr = getVisibleBeatRange();
        if (xPos >= 0 && playbackAutoScrollEnabled && !visibleBr.contains(pos, true))
        {
            float shiftedPos = Math.min(getPhraseBeatRange().to - visibleBr.size() / 2, pos + visibleBr.size() / 2 - 1f);
            scrollToCenter(shiftedPos);
        }

        firePropertyChange(PROP_PLAYBACK_POINT_POSITION, old, playbackPointPosition);
    }

    /**
     * Get the playback point position.
     *
     * @return If &lt; 0 no playback point is shown.
     */
    public float getPlaybackPointPosition()
    {
        return playbackPointPosition;
    }

    /**
     * Return the phrase position in beats that corresponds to a graphical point in the editor.
     * <p>
     *
     * @param editorPoint A point in the editor's coordinates. -1 if point is not valid.
     * @return
     *
     */
    public float getPositionFromPoint(Point editorPoint)
    {
        return notesPanel.getXMapper().getPositionInBeats(editorPoint.x);
    }


    /**
     * Return the X editor position that corresponds to a beat position of the Phrase model.
     *
     * @param pos
     * @return -1 If pos is outside the Phrase
     */
    public int getXFromPosition(float pos)
    {
        return getPhraseBeatRange().contains(pos, false) ? notesPanel.getXMapper().getX(pos) : -1;
    }

    /**
     * Convert a phrase Position into a phrase position in beats.
     *
     * @param pos Must be in the bar range.
     * @return
     */
    public float toPositionInBeats(Position pos)
    {
        return notesPanel.getXMapper().toPositionInBeats(pos);
    }

    /**
     * Convert a phrase position in beats into a Position.
     *
     * @param posInBeats Must be in the beat range.
     * @return
     * @see #getPhraseStartBar()
     */
    public Position toPosition(float posInBeats)
    {
        return notesPanel.getXMapper().toPosition(posInBeats);
    }

    /**
     * Return the pitch that correspond to a graphical point in the editor.
     *
     * @param notesPanelPoint A point in the editor's coordinates. -1 if point is not valid.
     * @return
     */
    public int getPitchFromPoint(Point notesPanelPoint)
    {
        return notesPanel.getYMapper().getPitch(notesPanelPoint.y);
    }


    /**
     * Scroll so that specified pitch is shown in the center of the editor, if possible.
     *
     * @param pitch
     */
    public void scrollToCenter(int pitch)
    {
        Preconditions.checkArgument(pitch >= 0 && pitch < 128);

        var vpRect = scrollpane.getViewport().getViewRect();
        float vpCenterY = vpRect.y + vpRect.height / 2f;
        IntRange pitchYRange = notesPanel.getYMapper().getKeyboardYRange(pitch);
        float pitchCenterY = (int) pitchYRange.getCenter();
        int dy = Math.round(vpCenterY - pitchCenterY);
        var r = new Rectangle(vpRect.x, dy > 0 ? vpRect.y - dy : vpRect.y + vpRect.height - 1 - dy, 1, 1);
        notesPanel.scrollRectToVisible(r);
        LOGGER.log(Level.FINE, "scrollToCenter() pitch={0} vpRect={1} r={2} notesPanel.bounds={3}", new Object[]
        {
            pitch, vpRect, r, notesPanel.getBounds()
        });

    }

    /**
     * Scroll so that specified position is shown in the center of the editor, if possible.
     *
     * @param posInBeats
     */
    public void scrollToCenter(float posInBeats)
    {
        Preconditions.checkArgument(getPhraseBeatRange().contains(posInBeats, true));

        var vpRect = scrollpane.getViewport().getViewRect();
        int vpCenterX = vpRect.x + vpRect.width / 2;
        int posCenterX = notesPanel.getXMapper().getX(posInBeats);
        int dx = vpCenterX - posCenterX;
        var r = new Rectangle(dx > 0 ? vpRect.x - dx : vpRect.x + vpRect.width - 1 - dx, vpRect.y, 1, 1);
        notesPanel.scrollRectToVisible(r);
        LOGGER.log(Level.FINE, "scrollToCenter() posInBeats={0} vpRect={1} r={2} notesPanel.bounds={3}", new Object[]
        {
            posInBeats, vpRect, r, notesPanel.getBounds()
        });
    }


    /**
     * Get the min/max notes which are currently visible.
     *
     * @return
     */
    public IntRange getVisiblePitchRange()
    {
        assert notesPanel.getYMapper().isUptodate() :
                "YMapper.getLastKeyboardHeight()=" + notesPanel.getYMapper().getLastKeyboardHeight()
                + " kbd.getHeight()=" + keyboard.getHeight()
                + " kbd.getPreferredHeight()=" + keyboard.getPreferredSize().getHeight();
        IntRange vpYRange = getYRange(scrollpane.getViewport().getViewRect());
        IntRange keysYRange = getYRange(keyboard.getKeysBounds());
        IntRange ir = keysYRange.getIntersection(vpYRange);
        var pitchBottom = notesPanel.getYMapper().getPitch(ir.to);
        var pitchTop = notesPanel.getYMapper().getPitch(ir.from);
        return new IntRange(pitchBottom, pitchTop);
    }

    /**
     * Get the min/max beat positions which are visible.
     *
     * @return
     */
    public FloatRange getVisibleBeatRange()
    {
        assert notesPanel.getXMapper().isUptodate() : "notesPanel.getWidth()=" + notesPanel.getWidth()
                + "XMapper.getLastWidth()=" + notesPanel.getXMapper().getLastWidth()
                + "notesPanel.getPreferredWidth()=" + notesPanel.getPreferredSize().getWidth();
        var vpRect = scrollpane.getViewport().getViewRect();
        var notesPanelBounds = notesPanel.getBounds();
        var vRect = vpRect.intersection(notesPanelBounds);
        var posLeft = notesPanel.getXMapper().getPositionInBeats(vRect.x);
        var posRight = notesPanel.getXMapper().getPositionInBeats(vRect.x + vRect.width - 1);
        return new FloatRange(posLeft, posRight);
    }

    /**
     * Check is editor is ready, ie painted and layouted at the correct size, so all editor methods can be called.
     *
     * @return
     */
    public boolean isReady()
    {
        return notesPanel.getXMapper().isUptodate() && notesPanel.getYMapper().isUptodate();
    }

    /**
     * Get the min/max bar indexes which are visible.
     *
     * @return
     */
    public IntRange getVisibleBarRange()
    {
        var visibleBr = getVisibleBeatRange();
        Position posFrom = notesPanel.getXMapper().toPosition(visibleBr.from);
        Position posTo = notesPanel.getXMapper().toPosition(visibleBr.to);
        var res = new IntRange(posFrom.getBar(), posTo.getBar());
        return res;
    }

    /**
     * Retrieve an editor action from its ActionMap.
     *
     * @param actionId
     * @return
     * @throws IllegalArgumentException If no action found
     */
    public Action getAction(String actionId)
    {
        Action a = getActionMap().get(actionId);
        if (a == null)
        {
            a = notesPanel.getActionMap().get(actionId);
        }
        if (a == null)
        {
            throw new IllegalArgumentException("unknown actionId=" + actionId);
        }
        return a;
    }

    /**
     * @return The UndoManager used by this editor.
     */
    public JJazzUndoManager getUndoManager()
    {
        return undoManager;
    }

    /**
     * Set the UndoManager used by this editor.
     *
     * @param um
     */
    public final void setUndoManager(JJazzUndoManager um)
    {
        model.removeUndoableEditListener(undoManager);
        undoManager = um;
        model.addUndoableEditListener(undoManager);
    }

    @Override
    public String toString()
    {
        return "PianoRollEditor[" + song.getName() + "]";
    }

    // ==========================================================================================================
    // PropertyChangeListener interface
    // ==========================================================================================================    
    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        LOGGER.log(Level.FINE, "propertyChange() -- evt={0}", evt);

        if (evt.getSource() == model)
        {
            switch (evt.getPropertyName())
            {
                case Phrase.PROP_NOTES_ADDED, Phrase.PROP_NOTES_ADDED_ADJUSTING ->
                {
                    List<NoteEvent> nes = (List<NoteEvent>) evt.getNewValue();
                    addNotes(nes);
                    notesPanel.revalidate();
                }
                case Phrase.PROP_NOTES_REMOVED, Phrase.PROP_NOTES_REMOVED_ADJUSTING ->
                {
                    List<NoteEvent> nes = (List<NoteEvent>) evt.getNewValue();
                    removeNotes(nes);
                    notesPanel.revalidate();
                    notesPanel.repaint();
                }
                case Phrase.PROP_NOTES_MOVED, Phrase.PROP_NOTES_MOVED_ADJUSTING, Phrase.PROP_NOTES_REPLACED, Phrase.PROP_NOTES_REPLACED_ADJUSTING ->
                {
                    Map<NoteEvent, NoteEvent> mapOldNew = (Map<NoteEvent, NoteEvent>) evt.getNewValue();
                    for (var oldNe : mapOldNew.keySet())
                    {
                        var newNe = mapOldNew.get(oldNe);
                        notesPanel.setNoteViewModel(oldNe, newNe);
                    }
                    notesPanel.revalidate();
                    notesPanel.repaint();
                }
                default ->
                {
                }
            }

        }
    }

    // =======================================================================================================================
    // Private methods
    // =======================================================================================================================

    /**
     * Caller is responsible to call revalidate() and/or repaint() as required.
     * <p>
     * Don't add if not in the beat range.
     *
     * @param notes
     */
    private void addNotes(List<NoteEvent> notes)
    {
        var rangeNotes = notes.stream()
                .filter(ne -> beatRange.contains(ne.getBeatRange(), false))
                .toList();
        if (!rangeNotes.isEmpty())
        {
            for (var ne : rangeNotes)
            {
                var nv = notesPanel.addNoteView(ne);
                registerNoteView(nv);
            }
            notesPanel.revalidate();
        }
    }

    /**
     * Caller is responsible to call revalidate() and/or repaint() as required.
     *
     * @param notes
     */
    private void removeNotes(List<NoteEvent> notes)
    {
        var rangeNotes = notes.stream()
                .filter(ne -> beatRange.contains(ne.getBeatRange(), false))
                .toList();
        if (!rangeNotes.isEmpty())
        {
            PianoRollEditor.this.selectNotes(rangeNotes, false);
            for (var ne : rangeNotes)
            {
                unregisterNoteView(notesPanel.getNoteView(ne));
                notesPanel.removeNoteView(ne);
            }
        }
    }

    private void registerNoteView(NoteView nv)
    {
        nv.addMouseListener(editToolProxyMouseListener);
        nv.addMouseListener(genericMouseListener);
        nv.addMouseMotionListener(editToolProxyMouseListener);
        nv.addMouseMotionListener(genericMouseListener);
        nv.addMouseWheelListener(editToolProxyMouseListener);
        nv.addMouseWheelListener(genericMouseListener);
        nv.setInheritsPopupMenu(true);

    }

    private void unregisterNoteView(NoteView nv)
    {
        nv.removeMouseListener(editToolProxyMouseListener);
        nv.removeMouseListener(genericMouseListener);
        nv.removeMouseMotionListener(editToolProxyMouseListener);
        nv.removeMouseMotionListener(genericMouseListener);
        nv.removeMouseWheelListener(editToolProxyMouseListener);
        nv.removeMouseWheelListener(genericMouseListener);
    }

    private int toZoomHValue(float scaleFactorX)
    {
        int zoomHValue = (int) (100f * (scaleFactorX - 0.1f) / 4);
        zoomHValue = Math.max(0, zoomHValue);
        zoomHValue = Math.min(100, zoomHValue);
        return zoomHValue;
    }

    private float toScaleFactorX(int zoomHValue)
    {
        float xFactor = 0.1f + 4 * zoomHValue / 100f;
        return xFactor;
    }

    private float toScaleFactorY(int zoomVValue)
    {
        float yFactor = 0.6f + 4 * zoomVValue / 100f;
        return yFactor;
    }

    private void createUI()
    {
        // The keyboard 
        // We need an enclosing panel for keyboard, so that keyboard size changes when its scaleFactor changes (zoom in/out). If we put the keyboard directly
        // in the JScrollpane, keyboard size might not change when JScrollpane is much bigger than the keys bounds.
        JPanel pnl_keyboard = new JPanel();
        pnl_keyboard.setLayout(new BorderLayout());
        keyboard = new KeyboardComponent(KeyboardRange._128_KEYS, KeyboardComponent.Orientation.RIGHT, false);
        labelNotes(keyboard, keyMap);
        pnl_keyboard.add(keyboard, BorderLayout.PAGE_START);


        // The scrollpane and the notesPanel inside
        mouseDragLayerUI = new MouseDragLayerUI();
        notesPanel = new NotesPanel(this, keyboard);
        mouseDragLayer = new JLayer(notesPanel, mouseDragLayerUI);
        rulerPanel = new RulerPanel(this, notesPanel);
        scrollpane = new JScrollPane();
        scrollpane.setViewportView(mouseDragLayer);
        scrollpane.setRowHeaderView(pnl_keyboard);
        scrollpane.setColumnHeaderView(rulerPanel);
        var vsb = scrollpane.getVerticalScrollBar();
        var hsb = scrollpane.getHorizontalScrollBar();
        vsb.setUnitIncrement(vsb.getUnitIncrement() * 10);   // view can be large...
        hsb.setUnitIncrement(hsb.getUnitIncrement() * 10);


        // The splitpane
//        velocityPanel = new VelocityPanel(this, notesPanel);
//        splitPane = new javax.swing.JSplitPane();
//        splitPane.setDividerSize(3);
//        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
//        splitPane.setResizeWeight(1.0);
//        splitPane.setLeftComponent(scrollpane);
//        splitPane.setRightComponent(velocityPanel);
        // Final layout
        setLayout(new BorderLayout());
        // add(splitPane, BorderLayout.CENTER);
        add(scrollpane, BorderLayout.CENTER);


        // Create the popupmenu
        var popupMenu = new JPopupMenu();
        var menuItem = new JMenuItem();
        menuItem.setBorder(BorderFactory.createEmptyBorder());
        EditToolBar editToolBar = new EditToolBar(this);
        editToolBar.setClickListener(() -> popupMenu.setVisible(false));
        menuItem.setPreferredSize(editToolBar.getPreferredSize());
        menuItem.add(editToolBar);
        popupMenu.add(menuItem);
        notesPanel.setComponentPopupMenu(popupMenu);
    }

    private void showSelectionRectangle(Rectangle r)
    {
        mouseDragLayerUI.showSelectionRectangle(r);
        mouseDragLayer.repaint();
    }

    private IntRange getYRange(Rectangle r)
    {
        assert r.height > 0;
        IntRange res = new IntRange(r.y, r.y + r.height - 1);
        return res;
    }

    private IntRange getXRange(Rectangle r)
    {
        assert r.width > 0;
        IntRange res = new IntRange(r.x, r.x + r.width - 1);
        return res;
    }


    private void labelNotes(KeyboardComponent keyboard, DrumKit.KeyMap keymap)
    {
        for (var key : keyboard.getAllKeys())
        {
            String s;
            if (keymap == null)
            {
                s = key.getPitch() % 12 == 0 ? "C" + (key.getPitch() / 12 - 1) : null;
            } else
            {
                s = keymap.getKeyName(key.getPitch());
                if (s != null)
                {
                    s = s.toLowerCase();
                }
            }
            key.setText(s);
        }
    }

    private void setKeyboardActions()
    {
        // Our delegates for standard Netbeans callback actions
        getActionMap().put("cut-to-clipboard", new CutNotes(this));
        getActionMap().put("copy-to-clipboard", new CopyNotes(this));
        // WEIRD: we need also, only for past callback action, the action in PianoRollEditorTopComponent! Otherwise our callback action is never called
        getActionMap().put("paste-from-clipboard", new PasteNotes(this));


        // Delegates for our callback actions        
        // Must be the editor's action map because it will be in the lookup of the TopComponent
        getActionMap().put("jjazz-delete", new DeleteSelection(this));
        getActionMap().put("jjazz-selectall", new SelectAllNotes(this));


        // Action with no UI button or menu associated
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(getGenericControlKeyStroke(KeyEvent.VK_F), ZoomToFit.ACTION_ID);
        getActionMap().put(ZoomToFit.ACTION_ID, new ZoomToFit(this));


        // Use the notesPanel input map to avoid the arrow keys being captured by the enclosing JScrollPane
        notesPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("LEFT"),
                "MoveSelectionLeft");
        notesPanel.getActionMap().put("MoveSelectionLeft", new MoveSelectionLeft(this));
        notesPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("RIGHT"),
                "MoveSelectionRight");
        notesPanel.getActionMap().put("MoveSelectionRight", new MoveSelectionRight(this));

        notesPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("alt LEFT"),
                "ResizeSelectionShorter");
        notesPanel.getActionMap().put("ResizeSelectionShorter", new ResizeSelection(this, false));
        notesPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("alt RIGHT"),
                "ResizeSelectionLonger");
        notesPanel.getActionMap().put("ResizeSelectionLonger", new ResizeSelection(this, true));
        notesPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("UP"), "TransposeUp");
        notesPanel.getActionMap().put("TransposeUp", new TransposeSelectionUp(this));
        notesPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("DOWN"), "TransposeDown");
        notesPanel.getActionMap().put("TransposeDown", new TransposeSelectionDown(this));


    }


    // =======================================================================================================================
    // Inner classes
    // =======================================================================================================================
    /**
     * Implements the Zoomable functionalities.
     */
    private class PianoRollZoomable implements Zoomable
    {


        @Override
        public Zoomable.Capabilities getZoomCapabilities()
        {
            return Zoomable.Capabilities.X_Y;
        }


        @Override
        public int getZoomYFactor()
        {
            return getZoom().vValue();
        }


        @Override
        public void setZoomYFactor(int newFactor, boolean valueIsAdjusting)
        {
            if (valueIsAdjusting)
            {
                // Safer, avoid some flickering
                return;
            }
            int old = getZoomYFactor();
            if (old != newFactor)
            {
                setZoom(new ZoomValue(getZoomXFactor(), newFactor));
                firePropertyChange(Zoomable.PROPERTY_ZOOM_Y, old, newFactor);
            }
        }


        @Override
        public int getZoomXFactor()
        {
            return getZoom().hValue();
        }


        @Override
        public void setZoomXFactor(int newFactor, boolean valueIsAdjusting)
        {
//            if (valueIsAdjusting)
//            {
//                return;
//            }
            int old = getZoomXFactor();
            if (old != newFactor)
            {
                setZoom(new ZoomValue(newFactor, getZoomYFactor()));
                firePropertyChange(Zoomable.PROPERTY_ZOOM_X, old, newFactor);
            }

        }

        @Override
        public void setZoomYFactorToFitContent()
        {
            // Don't bother zooming, just make sure center pitch is visible
            var nvs = getNoteViews();
            if (nvs.isEmpty())
            {
                return;
            }
            var firstNe = nvs.get(0).getModel();
            var lastNe = nvs.get(nvs.size() - 1).getModel();
            scrollToCenter((int) Math.round((lastNe.getPitch() + firstNe.getPitch()) / 2f));
        }

        @Override
        public void setZoomXFactorToFitContent()
        {
            // Try to show all notes horizontally in the visible rectangle
            var nvs = getNoteViews();
            if (nvs.isEmpty())
            {
                return;
            }
            var firstNe = nvs.get(0).getModel();
            var lastNe = nvs.get(nvs.size() - 1).getModel();


            int visibleWidthPixel = Math.max(100, scrollpane.getViewport().getViewRect().width);
            var notesBeatRange = firstNe.getBeatRange().getUnion(lastNe.getBeatRange());
            float beatRange = Math.max(4f, notesBeatRange.size());


            // Compute optimal scaleX
            float factorX = notesPanel.getScaleFactorX(visibleWidthPixel, beatRange);
            int zoomH = toZoomHValue(factorX);
            setZoomXFactor(zoomH, false);


            SwingUtilities.invokeLater(() -> scrollToCenter(notesBeatRange.getCenter()));

        }


        @Override
        public void addPropertyListener(PropertyChangeListener l)
        {
            addPropertyChangeListener(Zoomable.PROPERTY_ZOOM_X, l);
            addPropertyChangeListener(Zoomable.PROPERTY_ZOOM_Y, l);
        }


        @Override
        public void removePropertyListener(PropertyChangeListener l)
        {
            removePropertyChangeListener(Zoomable.PROPERTY_ZOOM_X, l);
            removePropertyChangeListener(Zoomable.PROPERTY_ZOOM_Y, l);
        }


    };

    /**
     * Provide generic services.
     * <p>
     * - Handle the selection rectangle when dragging on the editor.<br>
     * - Show the corresponding key on the keyboard when mouse is moved.<br>
     * - Handle ctrl-mousewheel for zoom<br>
     * <p>
     */
    private class GenericMouseListener implements MouseListener, MouseMotionListener, MouseWheelListener
    {

        /**
         * Null if no dragging.
         */
        private Point startDraggingPoint;
        int lastHighlightedPitch = -1;


        @Override
        public void mouseClicked(MouseEvent e)
        {
            // Nothing
        }


        @Override
        public void mousePressed(MouseEvent e)
        {
            notesPanel.requestFocusInWindow();          // Needed for InputMap/ActionMap actions
        }


        @Override
        public void mouseMoved(MouseEvent e)
        {
            showMarkOnKeyboard(e);
        }


        @Override
        public void mouseEntered(MouseEvent e)
        {
            // Nothing
        }


        @Override
        public void mouseExited(MouseEvent e)
        {
            if (lastHighlightedPitch != -1)
            {
                keyboard.getKey(lastHighlightedPitch).release();
            }
            lastHighlightedPitch = -1;
        }


        @Override
        public void mouseDragged(MouseEvent e)
        {
            showMarkOnKeyboard(e);

            if (e.getSource() == notesPanel && activeTool.isEditMultipleNotesSupported() && SwingUtilities.isLeftMouseButton(e))
            {
                if (startDraggingPoint == null)
                {
                    startDraggingPoint = e.getPoint();
                    unselectAll();
                } else
                {
                    ((JPanel) e.getSource()).scrollRectToVisible(new Rectangle(e.getX(), e.getY(), 1, 1));

                    Rectangle r = new Rectangle(startDraggingPoint);
                    r.add(e.getPoint());
                    showSelectionRectangle(r);
                }
            }

        }


        @Override
        public void mouseReleased(MouseEvent e)
        {
            if (startDraggingPoint != null && activeTool.isEditMultipleNotesSupported())
            {
                Rectangle r = new Rectangle(startDraggingPoint);
                r.add(e.getPoint());
                showSelectionRectangle(null);
                startDraggingPoint = null;

                var nvs = notesPanel.getNoteViews(r);
                if (!nvs.isEmpty())
                {
                    activeTool.editMultipleNotes(nvs);
                }
            }
        }


        @Override
        public void mouseWheelMoved(MouseWheelEvent e)
        {
            // Manage vertical/horizontal zoom accelerators via ctrl (+shift)
            if (!e.isControlDown())
            {
                if (e.isAltDown())
                {
                    // Used by SelectionTool
                    return;
                }

                // We don't want to lose the event because it is processed by the the enclosing JScrollPane to moveAll the scrollbar up/down or left-right if shift pressed
                Container source = e.getSource() instanceof NoteView ? ((Component) e.getSource()).getParent()
                        : (Container) e.getSource();
                Container parent = source.getParent();
                MouseEvent parentEvent = SwingUtilities.convertMouseEvent(source, e, parent);
                parent.dispatchEvent(parentEvent);
                return;
            }

            // Use the Zoomable to get the Zoomable scrollbars updated
            Zoomable zoomable = getLookup().lookup(Zoomable.class);
            if (zoomable == null)
            {
                return;
            }

            if (!e.isShiftDown())
            {
                // Horizontal Zoom
                final int STEP = 5;
                int hFactor = zoomable.getZoomXFactor();
                if (e.getWheelRotation() < 0)
                {
                    hFactor = Math.min(100, hFactor + STEP);
                } else
                {
                    hFactor = Math.max(0, hFactor - STEP);
                }
                zoomable.setZoomXFactor(hFactor, false);
            } else
            {
                // Vertical Zoom
                final int STEP = 5;
                int vFactor = zoomable.getZoomYFactor();
                if (e.getWheelRotation() < 0)
                {
                    vFactor = Math.min(100, vFactor + STEP);
                } else
                {
                    vFactor = Math.max(0, vFactor - STEP);
                }
                zoomable.setZoomYFactor(vFactor, false);
            }
        }


        private void showMarkOnKeyboard(MouseEvent e)
        {
            if (!notesPanel.getYMapper().isUptodate())
            {
                return;
            }

            Point p = e.getSource() instanceof NoteView nv ? SwingUtilities.convertPoint(nv, e.getPoint(), notesPanel)
                    : e.getPoint();
            int pitch = notesPanel.getYMapper().getPitch(p.y);
            if (pitch == lastHighlightedPitch)
            {
                // Nothing
            } else if (pitch == -1)
            {
                keyboard.getKey(lastHighlightedPitch).release();
            } else
            {
                if (lastHighlightedPitch != -1)
                {
                    keyboard.getKey(lastHighlightedPitch).release();
                }
                keyboard.getKey(pitch).setPressed(50, Color.LIGHT_GRAY);
            }
            lastHighlightedPitch = pitch;
        }


    }

    /**
     * Redirect mouse events to the active EditTool.
     */
    private class EditToolProxyMouseListener implements MouseListener, MouseMotionListener, MouseWheelListener
    {


        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (e.getSource() == notesPanel)
            {
                activeTool.editorClicked(e);
            } else if (e.getSource() instanceof NoteView nv)
            {
                activeTool.noteClicked(e, nv);
            }
        }


        @Override
        public void mousePressed(MouseEvent e)
        {
            // Nothing
        }


        @Override
        public void mouseReleased(MouseEvent e)
        {
            if (e.getSource() == notesPanel)
            {
                activeTool.editorReleased(e);
            } else if (e.getSource() instanceof NoteView nv)
            {
                activeTool.noteReleased(e, nv);
            }
        }


        @Override
        public void mouseEntered(MouseEvent e)
        {
            if (e.getSource() instanceof NoteView nv)
            {
                activeTool.noteEntered(e, nv);
            }
        }


        @Override
        public void mouseExited(MouseEvent e)
        {
            if (e.getSource() instanceof NoteView nv)
            {
                activeTool.noteExited(e, nv);
            }
        }


        @Override
        public void mouseDragged(MouseEvent e)
        {
            if (!SwingUtilities.isLeftMouseButton(e))
            {
                return;
            }

            if (e.getSource() == notesPanel)
            {
                activeTool.editorDragged(e);
            } else if (e.getSource() instanceof NoteView nv)
            {
                ((JPanel) e.getSource()).scrollRectToVisible(new Rectangle(e.getX(), e.getY(), 1, 1));
                activeTool.noteDragged(e, nv);
            }
        }


        @Override
        public void mouseMoved(MouseEvent e)
        {
            if (e.getSource() instanceof NoteView nv)
            {
                activeTool.noteMoved(e, nv);

                // Event needs also to be processed by the GenericMouseListener
                MouseEvent e2 = SwingUtilities.convertMouseEvent(nv, e, notesPanel);
                genericMouseListener.mouseMoved(e2);
            }

        }


        @Override
        public void mouseWheelMoved(MouseWheelEvent e)
        {
            if (e.getSource() == notesPanel)
            {
                activeTool.editorWheelMoved(e);
            } else if (e.getSource() instanceof NoteView nv)
            {
                activeTool.noteWheelMoved(e, nv);
            }
        }

    }


    /**
     * Handle a Midi file dragged into the notesPanel
     */
    private class MidiFileDragInTransferHandlerImpl extends MidiFileDragInTransferHandler
    {

        @Override
        protected boolean isImportEnabled()
        {
            return isEnabled();
        }

        @Override
        protected boolean importMidiFile(File midiFile)
        {
            Phrase p = Phrases.importPhrase(midiFile, channel, isDrums(), false, true);
            if (!p.isEmpty())
            {
                unselectAll();

                String undoText = ResUtil.getString(getClass(), "importMidiFile");
                getUndoManager().startCEdit(undoText);

                model.add(p, true);           // Do not clone the notes

                getUndoManager().endCEdit(undoText);


                final var nv0 = getNoteView(p.getNotes().get(0));
                SwingUtilities.invokeLater(() -> notesPanel.scrollRectToVisible(nv0.getBounds()));     // invokeLater to make sure task is run after nv0 is layouted
            }

            return true;
        }
    }
}
