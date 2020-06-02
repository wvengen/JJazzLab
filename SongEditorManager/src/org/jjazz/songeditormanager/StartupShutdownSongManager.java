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
package org.jjazz.songeditormanager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.SwingUtilities;
import org.jjazz.activesong.ActiveSongManager;
import org.jjazz.base.actions.Savable;
import org.jjazz.midimix.MidiMix;
import org.jjazz.midimix.MidiMixManager;
import org.jjazz.song.api.Song;
import org.jjazz.upgrade.UpgradeManager;
import org.jjazz.upgrade.spi.UpgradeTask;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.modules.OnStop;
import org.openide.util.Exceptions;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.OnShowing;

/**
 * Manage the opening/closing of song files at startup/shutdown.
 * <p>
 * Upon startup, if file names arguments are passed on the command line, open these files. Otherwise restore the last opened files
 * depending on setting. Upon shutdown ask for user confirmation for unsaved songs.
 */
@ServiceProvider(service = OptionProcessor.class)
@OnShowing
@OnStop
public class StartupShutdownSongManager extends OptionProcessor implements Runnable, Callable<Boolean>
{

    /**
     * Instance is automatically created upon startup.
     */
    private static StartupShutdownSongManager INSTANCE;
    /**
     * Used as the Preference id and property change.
     */
    private static final String PREF_OPEN_RECENT_FILES_UPON_STARTUP = "OpenRecentFilesUponStartup";
    private static final String PREF_FILES_TO_BE_REOPENED_UPON_STARTUP = "FilesToBeReOpenedUponStartup";
    private static final String NO_FILE = "__NO_FILE__";
    private static final int MAX_FILES = 6;
    private Option openOption = Option.defaultArguments();  // The command line arguments with no -x or --xyz option
    private List<File> cmdLineFilesToOpen = new ArrayList<>();
    private static Preferences prefs = NbPreferences.forModule(StartupShutdownSongManager.class);
    private static final Logger LOGGER = Logger.getLogger(StartupShutdownSongManager.class.getSimpleName());


    static public final StartupShutdownSongManager getInstance()
    {
        if (INSTANCE == null)
        {
            throw new NullPointerException("INSTANCE");
        }
        return INSTANCE;
    }

    /**
     * Reserved do not use : use getInstance() instead.
     */
    public StartupShutdownSongManager()
    {
        INSTANCE = this;
    }


    public void setOpenRecentFilesUponStartup(boolean b)
    {
        prefs.putBoolean(PREF_OPEN_RECENT_FILES_UPON_STARTUP, b);
    }

    public final boolean isOpenRecentFilesUponStartup()
    {
        return prefs.getBoolean(PREF_OPEN_RECENT_FILES_UPON_STARTUP, true);
    }


    // ==================================================================================
    // OptionProcessor implementation
    // ==================================================================================
    @Override
    protected Set<Option> getOptions()
    {
        HashSet set = new HashSet();
        set.add(openOption);
        return set;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> values) throws CommandException
    {
        LOGGER.fine("process() --  env=" + env + " values=" + values);
        if (values.containsKey(openOption))
        {
            for (String fileName : values.get(openOption))
            {
                LOGGER.info("process() Opening command line file: " + fileName + ", current dir: " + env.getCurrentDirectory().getAbsolutePath());
                File file = new File(fileName);
                // Normally fileName contains the absolute path, but just in case...
                if (file.getParentFile() == null)
                {
                    file = new File(env.getCurrentDirectory().getAbsolutePath(), file.getName());
                }
                if (!file.exists())
                {
                    LOGGER.warning("process() Can't find " + file.getAbsolutePath());
                    continue;
                } else
                {
                    // Just save them, can't open them right now because UI is not ready
                    cmdLineFilesToOpen.add(file);
                }
            }
        }
    }


    // ==================================================================================
    // Runnable implementation
    // ==================================================================================
    /**
     * Called upon startup after UI's ready, so AFTER process() which collects optional files from the command line.
     */
    @Override
    public void run()
    {
        LOGGER.fine("run() --");
        var sem = SongEditorManager.getInstance();
        // If command line arguments specified, just open them
        if (!cmdLineFilesToOpen.isEmpty())
        {
            for (File f : cmdLineFilesToOpen)
            {
                if (sem.showSong(f) == null)
                {
                    LOGGER.warning("run() Problem opening song file: " + f.getAbsolutePath());
                }
            }
        } else if (isOpenRecentFilesUponStartup())
        {
            openRecentFilesUponStartup();
        }
    }

    // ==================================================================================
    // Callable implementation
    // ==================================================================================

    /**
     * Called upon shutdown.
     * <p>
     * Ask user confirmation if unsaved changes (whatever nb of unsaved files), close properly the opened songs (so that listeners
     * with persistence like RecentFiles are notified).
     * <p>
     * Also save the opened songs for possible reopen at startup (see isOpenRecentFilesUponStartup()).
     */
    @Override
    public Boolean call() throws Exception
    {
        SongEditorManager sem = SongEditorManager.getInstance();


        // Ask user confirmation if there are still files to be saved
        List<Savable> savables = Savable.ToBeSavedList.getSavables();
        if (!savables.isEmpty())
        {
            StringBuilder msg = new StringBuilder();
            msg.append("There are unsaved changes in the files below. OK to exit anyway ?").append("\n\n");
            for (Savable s : savables)
            {
                msg.append("  ").append(s.toString()).append("\n");
            }
            NotifyDescriptor nd = new NotifyDescriptor.Confirmation(msg.toString(), NotifyDescriptor.OK_CANCEL_OPTION);
            Object result = DialogDisplayer.getDefault().notify(nd);
            if (result != NotifyDescriptor.OK_OPTION)
            {
                return Boolean.FALSE;
            }
        }


        // Close the open editors and update the preferences
        StringBuilder sb = new StringBuilder();
        for (Song s : sem.getOpenedSongs())
        {
            File f = s.getFile();
            if (f != null)
            {
                if (sb.length() > 0)
                {
                    sb.append(", ");
                }
                sb.append(f.getAbsolutePath());
            }
            sem.closeSong(s, true);
        }
        String s = sb.toString();
        prefs.put(PREF_FILES_TO_BE_REOPENED_UPON_STARTUP, s.isEmpty() ? NO_FILE : s);

        return Boolean.TRUE;
    }

    // ==================================================================================
    // Private methods
    // ==================================================================================
    private void openRecentFilesUponStartup()
    {
        String s = prefs.get(PREF_FILES_TO_BE_REOPENED_UPON_STARTUP, NO_FILE).trim();
        if (!s.equals(NO_FILE))
        {
            final List<String> strFiles = Arrays.asList(s.split(","));
            final int max = Math.min(strFiles.size(), MAX_FILES);         // Robustness
            Runnable run = new Runnable()
            {
                @Override
                public void run()
                {
                    Song lastSong = null;
                    for (int i = 0; i < max; i++)
                    {
                        File f = new File(strFiles.get(i).trim());
                        Song sg = SongEditorManager.getInstance().showSong(f);
                        if (sg != null)
                        {
                            lastSong = sg;
                        }
                    }
                    // Make the last open song active
                    if (lastSong != null)
                    {
                        MidiMix mm;
                        try
                        {
                            mm = MidiMixManager.getInstance().findMix(lastSong);
                            ActiveSongManager.getInstance().setActive(lastSong, mm);
                        } catch (MidiUnavailableException ex)
                        {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            };
            SwingUtilities.invokeLater(run);
        }
    }


    // =====================================================================================
    // Upgrade Task
    // =====================================================================================
    @ServiceProvider(service = UpgradeTask.class)
    static public class RestoreSettingsTask implements UpgradeTask
    {

        @Override
        public void upgrade(String oldVersion)
        {
            UpgradeManager um = UpgradeManager.getInstance();
            um.duplicateOldPreferences(prefs);
        }

    }


}
