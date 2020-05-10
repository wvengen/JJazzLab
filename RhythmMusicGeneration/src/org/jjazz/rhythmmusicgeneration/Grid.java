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
package org.jjazz.rhythmmusicgeneration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.jjazz.harmony.Note;
import org.jjazz.midi.MidiUtilities;
import org.jjazz.rhythm.api.Feel;
import org.jjazz.rhythm.api.Rhythm;

/**
 * A convenience class to manipulate notes from a Phrase.
 * <p>
 * The class assigns notes in fixed-sized "cells" (eg 4 cells per beats=1/16) which can then be directly accessed or modified
 * using the cell index.
 * <p>
 * To accomodate real time playing, notes starting just before a cell are included in that cell.
 * <p>
 * The refresh() method must be called whenever the phrase is modified outside this Grid object.
 */
public class Grid
{

    /**
     * Notes whose relative position is &gt; -PRE_CELL_BEAT_WINDOW will be included in the current cell.
     */
    public static float PRE_CELL_BEAT_WINDOW = 0.1f;     // This is just below 1/8=0.125f (thirteenth notes)
    private float preCellBeatWindow;
    private Phrase phrase;
    private float startPos;
    private float endPos;
    private int cellsPerBeat;
    private float cellDuration;
    private Predicate<NoteEvent> predicate;
    private int lastCellIndex;
    private final HashMap<Integer, List<NoteEvent>> mapCellNotes = new HashMap<>();
    protected static final Logger LOGGER = Logger.getLogger(Grid.class.getSimpleName());

    /**
     * Obtain a grid for the specified Phrase p.
     * <p>
     * The first cell starts at positionInBeatsFrom. The filter parameter can be used to accept only specific Phrase notes.<p>
     * If the caller modifies p outside of this grid it must then call Grid.refresh() to keep it up to date.
     *
     * @param p Time signature must not change in the phrase.
     * @param startPosInBeats Grid will contain notes from this position in beats (included). Must be an integer.
     * @param endPosInBeats Grid will contain notes until this position in beats (excluded). Must be an integer.
     * @param cellsPerBeat Must be &gt; 0.
     * @param filter If null this grid will consider all Phrase notes
     */
    public Grid(Phrase p, float startPosInBeats, float endPosInBeats, int cellsPerBeat, Predicate<NoteEvent> filter)
    {
        if (p == null || startPosInBeats < 0 || startPosInBeats >= endPosInBeats || cellsPerBeat < 1
                || startPosInBeats != Math.floor(startPosInBeats) || endPosInBeats != Math.floor(endPosInBeats))
        {
            throw new IllegalArgumentException(
                    "p=" + p + " startPosInBeats=" + startPosInBeats + " endPosInBeats=" + endPosInBeats + " cellsPerBeat=" + cellsPerBeat + " filter=" + filter);
        }
        phrase = p;
        this.startPos = startPosInBeats;
        this.endPos = endPosInBeats;
        this.cellsPerBeat = cellsPerBeat;
        this.cellDuration = 1f / this.cellsPerBeat;
        this.lastCellIndex = ((int) (endPos - startPos) * this.cellsPerBeat) - 1;
        this.predicate = (filter != null) ? filter : ne -> true;
        preCellBeatWindow = Math.min(PRE_CELL_BEAT_WINDOW, cellDuration);
        refresh();
    }

    /**
     * Search the cell which contains the maximum of notes and return this cell index.
     *
     * @return -1 if no note in the grid.
     */
    public int getMaxNotesCellIndex()
    {
        int indexMax = -1;
        int maxSize = 0;
        for (int index : mapCellNotes.keySet())
        {
            List<NoteEvent> nes = mapCellNotes.get(index);
            if (nes != null && nes.size() > maxSize)
            {
                maxSize = nes.size();
                indexMax = index;
            }
        }
        return indexMax;
    }

    /**
     * Return the cell index for specified position.
     * <p>
     * Take into account the getPreCellBeatWindow() value.
     *
     * @param posInBeats
     * @return -1 if posInBeats is after the last cell of this grid
     * @throws IllegalArgumentException If posInBeats (&lt; 0 or &lt; grid's start position - getPreCellBeatWindow())
     */
    public int getCellIndex(float posInBeats)
    {
        if (posInBeats < 0 || posInBeats < (startPos - getPreCellBeatWindow()))
        {
            throw new IllegalArgumentException("posInBeats=" + posInBeats + " startPos=" + startPos + " getPreCellBeatWindow()=" + getPreCellBeatWindow() + " endPos=" + endPos);
        }
        int res = -1;
        if (posInBeats < endPos - getPreCellBeatWindow())
        {
            res = (int) Math.floor((posInBeats - startPos) / cellDuration);
            float nextCellStartPos = startPos + (res + 1) * cellDuration;
            if (nextCellStartPos - posInBeats <= getPreCellBeatWindow())
            {
                res++;
            }
        }
        return res;
    }

    public int getLastCellIndex()
    {
        return lastCellIndex;
    }

    /**
     * Get the indexes of the non empty cells.
     *
     * @return List is ordered. Can be empty.
     */
    public List<Integer> getNonEmptyCells()
    {
        ArrayList<Integer> res = new ArrayList<>(mapCellNotes.keySet());
        Collections.sort(res);
        return res;
    }

    /**
     * Is there is some note in the specified cell.
     *
     * @param cellIndex
     * @return
     */
    public boolean isEmpty(int cellIndex)
    {
        List<NoteEvent> nes = mapCellNotes.get(cellIndex);
        return nes == null ? true : nes.isEmpty();
    }

    /**
     * Change the duration of all notes in specified cells so that they end at cell cellIndexOff.
     * <p>
     * Notes can be shortened or made longer.
     *
     * @param cellIndexFrom
     * @param cellIndexTo
     * @param cellIndexOff The cell index where notes should go off.
     * @param shorterOk If false do not make notes shorter. Can't be false if longerOk is false.
     * @param longerOk If false do not make notes longer. Can't be false if shorterOk is false.
     */
    public void changeDuration(int cellIndexFrom, int cellIndexTo, int cellIndexOff, boolean shorterOk, boolean longerOk)
    {
        if (cellIndexOff < cellIndexTo || cellIndexOff > lastCellIndex || (!shorterOk && !longerOk))
        {
            throw new IllegalArgumentException("cellIndexFrom=" + cellIndexFrom + " cellIndexTo=" + cellIndexTo + " cellIndexOff=" + cellIndexOff + " shorterOk=" + shorterOk + " longerOk=" + longerOk + " lastCellIndex=" + lastCellIndex);
        }
        var nes = getCellNotes(cellIndexFrom, cellIndexTo);
        HashSet<Integer> usedPitches = new HashSet<>();
        for (NoteEvent ne  : nes)
        {
            float endPosInBeats = ne.getPositionInBeats() + ne.getDurationInBeats();
            int endPosCellIndex = (endPosInBeats >= endPos) ? lastCellIndex : getCellIndex(endPosInBeats);
            if (usedPitches.contains(ne.getPitch()))
            {
                phrase.remove(ne);
            } else if ((longerOk && endPosCellIndex < cellIndexOff) || (shorterOk && endPosCellIndex > cellIndexOff))
            {
                float newDur = ne.getDurationInBeats() + (cellIndexOff - endPosCellIndex) * cellDuration;
                newDur = Math.max(cellDuration, newDur);
                NoteEvent newNe = new NoteEvent(ne, newDur);       // This clone also the clientProperties
                phrase.set(phrase.indexOf(ne), newNe);
                usedPitches.add(newNe.getPitch());
            }
        }
        if (!usedPitches.isEmpty())
        {
            refresh();
        }
    }

    /**
     * Modify velocity of notes in the specified cells.
     * <p>
     * Velocity is always maintained in the 0-127 range.
     *
     * @param cellIndexFrom
     * @param cellIndexTo
     * @param f A function to modify velocity to another velocity.
     */
    public void changeVelocity(int cellIndexFrom, int cellIndexTo, Function<Integer, Integer> f)
    {
        if (f == null)
        {
            throw new IllegalArgumentException("cellIndexFrom=" + cellIndexFrom + " cellIndexTo=" + cellIndexTo + " f=" + f);
        }
        List<NoteEvent> nes = getCellNotes(cellIndexFrom, cellIndexTo);
        for (NoteEvent ne : nes)
        {
            int newVelocity = MidiUtilities.limit(f.apply(ne.getVelocity()));
            NoteEvent tNe = new NoteEvent(ne, ne.getPitch(), ne.getDurationInBeats(), newVelocity);       // This clone also the clientProperties
            phrase.set(phrase.indexOf(ne), tNe);
        }
        if (!nes.isEmpty())
        {
            refresh();
        }
    }

    /**
     * Get the notes in the specified cell range.
     *
     * @param cellIndexFrom The index of the first cell, starting at 0 for beat=getPosInBeatsFrom().
     * @param cellIndexTo The index of the last cell (included)
     * @return List has the same note order than the Phrase.
     */
    public List<NoteEvent> getCellNotes(int cellIndexFrom, int cellIndexTo)
    {
        if (cellIndexFrom < 0 || cellIndexTo < cellIndexFrom || cellIndexTo > lastCellIndex)
        {
            throw new IllegalArgumentException("cellIndexFrom=" + cellIndexFrom + " cellIndexTo=" + cellIndexTo);
        }
        List<NoteEvent> res = new ArrayList<>();
        for (int i = cellIndexFrom; i <= cellIndexTo; i++)
        {
            List<NoteEvent> nes = mapCellNotes.get(i);
            if (nes != null)
            {
                res.addAll(nes);
            }
        }
        return res;
    }

    /**
     * Get the first note of the cell
     *
     * @param cellIndex
     * @return Null if no note
     */
    public NoteEvent getFirstNote(int cellIndex)
    {
        if (cellIndex < 0 || cellIndex > lastCellIndex)
        {
            throw new IllegalArgumentException("cellIndex=" + cellIndex);
        }
        NoteEvent res = null;

        List<NoteEvent> nes = mapCellNotes.get(cellIndex);
        if (nes != null)
        {
            res = nes.get(0);
        }
        return res;
    }

    /**
     * Get the cell of the first note in the specified cell range.
     *
     * @param cellIndexFrom The index of the first cell, starting at 0 for beat=getPosInBeatsFrom()
     * @param cellIndexTo The index of the last cell (included))
     * @return -1 if note
     */
    public int getFirstNoteCell(int cellIndexFrom, int cellIndexTo)
    {
        if (cellIndexFrom < 0 || cellIndexTo < cellIndexFrom || cellIndexTo > lastCellIndex)
        {
            throw new IllegalArgumentException("cellIndexFrom=" + cellIndexFrom + " cellIndexTo=" + cellIndexTo);
        }
        int res = -1;
        for (int i = cellIndexFrom; i <= cellIndexTo; i++)
        {
            List<NoteEvent> nes = mapCellNotes.get(i);
            if (nes != null)
            {
                res = i;
                break;
            }
        }
        return res;
    }

    /**
     * Get the last note of the cell
     *
     * @param cellIndex
     * @return Null if no note
     */
    public NoteEvent getLastNote(int cellIndex)
    {
        if (cellIndex < 0 || cellIndex > lastCellIndex)
        {
            throw new IllegalArgumentException("cellIndex=" + cellIndex);
        }
        NoteEvent res = null;

        List<NoteEvent> nes = mapCellNotes.get(cellIndex);
        if (nes != null)
        {
            res = nes.get(nes.size() - 1);
        }
        return res;
    }

    /**
     * Get the cell of the last note in the specified cell range.
     *
     * @param cellIndexFrom The index of the first cell, starting at 0 for beat=getPosInBeatsFrom()
     * @param cellIndexTo The index of the last cell (included))
     * @return -1 if no note found
     */
    public int getLastNoteCell(int cellIndexFrom, int cellIndexTo)
    {
        if (cellIndexFrom < 0 || cellIndexTo < cellIndexFrom || cellIndexTo > lastCellIndex)
        {
            throw new IllegalArgumentException("cellIndexFrom=" + cellIndexFrom + " cellIndexTo=" + cellIndexTo);
        }
        int res = -1;
        for (int i = cellIndexTo; i >= cellIndexFrom; i--)
        {
            List<NoteEvent> nes = mapCellNotes.get(i);
            if (nes != null)
            {
                res = i;
                break;
            }
        }
        return res;
    }

    /**
     * Remove all notes in the specified cells range.
     *
     * @param cellIndexFrom The index of the first cell, starting at 0 for beat=getPosInBeatsFrom()
     * @param cellIndexTo The index of the last cell (included))
     * @return The removed notes.
     */
    public List<NoteEvent> removeNotes(int cellIndexFrom, int cellIndexTo)
    {
        if (cellIndexFrom < 0 || cellIndexTo < cellIndexFrom || cellIndexTo > lastCellIndex)
        {
            throw new IllegalArgumentException("cellIndexFrom=" + cellIndexFrom + " cellIndexTo=" + cellIndexTo);
        }
        List<NoteEvent> nes = getCellNotes(cellIndexFrom, cellIndexTo);
        phrase.removeAll(nes);
        refresh();
        return nes;
    }

    /**
     * Add a new NoteEvent from the parameters.
     *
     *
     * @param cellIndex
     * @param n Pitch, duration and velocity are reused to create the NoteEvent.
     * @param relPosInCell The relative position in beats of the note in the cell. Value must be in the
     * [-getPreCellBeatWindow():+1/cellsPerBeat[ interval
     * @return The added note.
     */
    public NoteEvent addNote(int cellIndex, Note n, float relPosInCell)
    {
        if (cellIndex < 0 || cellIndex > lastCellIndex || relPosInCell < -preCellBeatWindow || relPosInCell >= cellDuration)
        {
            throw new IllegalArgumentException("cellIndex=" + cellIndex + " relPosInCell=" + relPosInCell);
        }
        float posInBeats = getStartPos(cellIndex) + relPosInCell;
        NoteEvent ne = new NoteEvent(n.getPitch(), n.getDurationInBeats(), n.getVelocity(), posInBeats);
        phrase.addOrdered(ne);
        refresh();
        return ne;
    }

    /**
     * Replace a note by another one at same position.
     * <p>
     * The 2 notes must have the same position.
     *
     * @param oldNote
     * @param newNote
     */
    public void replaceNote(NoteEvent oldNote, NoteEvent newNote)
    {
        int index = phrase.indexOf(oldNote);
        if (oldNote.getPositionInBeats() != newNote.getPositionInBeats() || index == -1)
        {
            throw new IllegalArgumentException("oldNote=" + oldNote + " newNote=" + newNote);
        }
        phrase.set(index, newNote);
        refresh();
    }


    /**
     * Move all notes from one cell to another.
     *
     * @param cellIndexFrom The index of the cell containing the notes to be moved.
     * @param cellIndexDest The index of the destination cell
     * @param keepNoteOffPosition If true AND notes are moved earlier (cellIndexFrom &gt; cellIndexDest), extend the duration of
     * the moved notes so they keep the same NOTE_OFF position.
     * @return The number of moved notes
     */
    public int moveNotes(int cellIndexFrom, int cellIndexDest, boolean keepNoteOffPosition)
    {
        if (cellIndexFrom < 0 || cellIndexDest < 0 || cellIndexFrom > lastCellIndex || cellIndexDest > lastCellIndex)
        {
            throw new IllegalArgumentException("cellIndexFrom=" + cellIndexFrom + " cellIndexDest=" + cellIndexDest);
        }
        if (cellIndexFrom == cellIndexDest)
        {
            return 0;
        }
        List<NoteEvent> nes = getCellNotes(cellIndexFrom, cellIndexFrom);
        if (cellIndexFrom != cellIndexDest && !nes.isEmpty())
        {
            for (NoteEvent ne : nes)
            {
                float cellFromPos = getStartPos(cellIndexFrom);
                float cellDestPos = getStartPos(cellIndexDest);
                float inCellpos = ne.getPositionInBeats() - cellFromPos;       // Can be negative for notes right before the cell !
                float newPosInBeats = Math.max(0, cellDestPos + inCellpos);
                float durationInBeats = ne.getDurationInBeats();
                if (keepNoteOffPosition && cellIndexFrom > cellIndexDest)
                {
                    // Extend the duration
                    durationInBeats = ne.getPositionInBeats() + ne.getDurationInBeats() - newPosInBeats;
                }
                NoteEvent movedNe = new NoteEvent(ne, durationInBeats, newPosInBeats);
                phrase.remove(ne);
                phrase.addOrdered(movedNe);
            }
            refresh();
        }
        return nes.size();
    }

    /**
     * Move the first note of cellIndexFrom to another cell.
     *
     * @param cellIndexFrom The index of the cell containing the note to be moved.
     * @param cellIndexDest The index of the destination cell
     * @param keepNoteOffPosition If true AND note is moved earlier (cellIndexFrom &gt; cellIndexDest), extend the duration of the
     * moved note so it keeps the same NOTE_OFF position.
     * @return True if a note was moved.
     */
    public boolean moveFirstNote(int cellIndexFrom, int cellIndexDest, boolean keepNoteOffPosition)
    {
        if (cellIndexFrom < 0 || cellIndexDest < 0 || cellIndexFrom > lastCellIndex || cellIndexDest > lastCellIndex)
        {
            throw new IllegalArgumentException("cellIndexFrom=" + cellIndexFrom + " cellIndexDest=" + cellIndexDest);
        }
        NoteEvent ne = getFirstNote(cellIndexFrom);
        if (ne != null)
        {
            float cellFromPos = getStartPos(cellIndexFrom);
            float cellDestPos = getStartPos(cellIndexDest);
            float relPos = ne.getPositionInBeats() - cellFromPos;
            float newPosInBeats = Math.max(0, cellDestPos + relPos);
            float durationInBeats = ne.getDurationInBeats();
            if (keepNoteOffPosition && cellIndexFrom > cellIndexDest)
            {
                // Extend the duration
                durationInBeats = ne.getPositionInBeats() + ne.getDurationInBeats() - newPosInBeats;
            }
            NoteEvent movedNe = new NoteEvent(ne, durationInBeats, newPosInBeats);
            phrase.remove(ne);
            phrase.addOrdered(movedNe);
            refresh();
        }
        return ne != null;
    }

    /**
     * Force all sounding notes to stop (eg shorten their duration) at the start of the specified cell.
     *
     * @param cellIndex
     * @return The number of notes which have been shortened.
     * @see getPreCellBeatWindow()
     */
    public int stopNotesBefore(int cellIndex)
    {
        float pos = getStartPos(cellIndex) - preCellBeatWindow;
        List<NoteEvent> nes = phrase.getCrossingNotes(pos, true);
        for (NoteEvent ne : nes)
        {
            float newDuration = pos - ne.getPositionInBeats();
            NoteEvent newNe = new NoteEvent(ne, newDuration);
            phrase.set(phrase.indexOf(ne), newNe);
        }
        refresh();
        return nes.size();
    }

    /**
     * The time window before the start of a cell we consider to be part of the cell.
     * <p>
     * This is used to accomodate real time playing notes which may start just before a cell.
     *
     * @return A duration in beats
     */
    public float getPreCellBeatWindow()
    {
        return preCellBeatWindow;
    }

    /**
     * @return the phrase
     */
    public Phrase getPhrase()
    {
        return phrase;
    }

    /**
     * The start position in beats of this grid.
     *
     * @return An integer value.
     */
    public float getStartPos()
    {
        return startPos;
    }

    /**
     * The end position in beats of this grid.
     *
     * @return An integer value.
     */
    public float getEndPos()
    {
        return endPos;
    }

    /**
     *
     * @return A value &gt; 0
     */
    public int getNbCellsPerBeat()
    {
        return cellsPerBeat;
    }

    /**
     * The position in beats of the start of the specified cell.
     * <p>
     * Note that some notes may belong to a cell even if their position is lower than the value returned by getStartPos(), see
     * getPreCellBeatWindow().
     * <p>
     *
     * @param cellIndex
     * @return
     */
    public float getStartPos(int cellIndex)
    {
        if (cellIndex < 0 || cellIndex > lastCellIndex)
        {
            throw new IllegalArgumentException("cellindex=" + cellIndex + " lastCellIndex=" + lastCellIndex);
        }
        float pos = startPos + cellIndex * cellDuration;
        return pos;
    }

    /**
     * Update the internal data structure: should be called when Phrase has been modified externally.
     * <p>
     * Manage the fact that a note can be included in a cell if its start position is just before the cell.
     *
     * @see getPreCellBeatWindow()
     */
    public final void refresh()
    {
        mapCellNotes.clear();
        for (NoteEvent ne : phrase)
        {
            float posInBeats = ne.getPositionInBeats();
            if (posInBeats >= endPos - preCellBeatWindow)
            {
                break;
            } else if (posInBeats >= (startPos - preCellBeatWindow))
            {
                if (!predicate.test(ne))
                {
                    continue;
                }
                float relPosInBeats = posInBeats - startPos;
                int cellIndex;
                if (relPosInBeats < 0)
                {
                    // Special case: the note is just before startPos (but still in the preCellBeatWindow)
                    cellIndex = 0;
                } else
                {
                    // Normal case
                    cellIndex = (int) Math.floor(relPosInBeats / cellDuration);
                    if (((cellIndex + 1) * cellDuration - relPosInBeats) <= preCellBeatWindow)
                    {
                        // We're in the preCellBeatWindow of next cell
                        cellIndex++;
                    }
                }
                List<NoteEvent> nes = mapCellNotes.get(cellIndex);
                if (nes == null)
                {
                    nes = new ArrayList<>();
                    mapCellNotes.put(cellIndex, nes);
                }
                nes.add(ne);
            }
        }
    }

    /**
     *
     * @param cellFrom If out of bound use 0
     * @param cellTo If out of bound use lastCellIndex
     * @return
     */
    public String toString(int cellFrom, int cellTo)
    {
        if (cellFrom > cellTo)
        {
            throw new IllegalArgumentException("cellFrom=" + cellFrom + " cellTo=" + cellTo);
        }
        StringBuilder sb = new StringBuilder();
        cellFrom = Math.max(0, cellFrom);
        cellTo = Math.min(this.lastCellIndex, cellTo);
        if (cellFrom % cellsPerBeat == 0)
        {
            sb.append("|");
        }
        for (int i = cellFrom; i <= cellTo; i++)
        {
            List<NoteEvent> nes = mapCellNotes.get(i);
            sb.append(nes == null ? "." : nes.size());
            if ((i + 1) % cellsPerBeat == 0)
            {
                sb.append("|");      // Every beat
            }
        }
        sb.append(" ").append(getCellNotes(cellFrom, cellTo).toString());
        return sb.toString();
    }

    @Override
    public String toString()
    {
        return toString(0, lastCellIndex);
    }


    /**
     * Get the recommended nb of cells for the specified rhythm.
     *
     * @param r
     * @return 3 or 4
     */
    static public int getRecommendedNbCellsPerBeat(Rhythm r)
    {
        int res = (r.getTimeSignature().getLower() == 8 || r.getFeatures().getFeel().equals(Feel.TERNARY)) ? 3 : 4;
        return res;
    }

    // =================================================================================
    // Private methods
    // ================================================================================= 
}
