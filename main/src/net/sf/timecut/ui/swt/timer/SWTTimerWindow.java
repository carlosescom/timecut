/*
 * Copyright (c) TimeCult Project Team, 2005-2011 (dyadix@gmail.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * $Id: SWTTimerWindow.java,v 1.25 2011/01/19 14:22:05 dyadix Exp $
 */
package net.sf.timecut.ui.swt.timer;

import net.sf.timecut.PlatformUtil;
import net.sf.timecut.TimeTracker;
import net.sf.timecut.conf.AppPreferences;
import net.sf.timecut.model.Task;
import net.sf.timecut.model.TimeRecord;
import net.sf.timecut.model.Workspace;
import net.sf.timecut.model.WorkspaceEvent;
import net.sf.timecut.stopwatch.Stopwatch;
import net.sf.timecut.stopwatch.StopwatchEvent;
import net.sf.timecut.stopwatch.StopwatchListener;
import net.sf.timecut.ui.swt.SWTWindow;
import net.sf.timecut.ui.swt.SWTUIManager;
import net.sf.timecut.ui.swt.timelog.TimeLogEntryEditDialog;
import net.sf.timecut.util.Formatter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

public class SWTTimerWindow implements StopwatchListener {

    private final static int MAX_TITLE_CHARS        = 15;
    private final static int NIMAGES                = 4;
    private final static int NOTIF_MESSAGE_INTERVAL = 5000;           // 5
                                                                         // sec
    private Workspace        workspace              = TimeTracker.getInstance()
                                                        .getWorkspace();

    private Shell           _shell;
    private Stopwatch       _stopwatch;
    private long            _duration;
    private Display         _display;
    private Task            _task;
    private Label           _timeLabel;
    private SWTWindow       _parent;
    private static int      _instanceCount;
    private TrayItem        _trayItem;
    private TimerTrayMenu   _trayMenu;
    private int             _imageCount;
    private SWTTimerToolBar _toolBar;
    private TimeRecord      _timeRec;
    private Workspace       _workspace;

    private long            _lastNotifTime;
    private long            _lastIntervalTime;
    private long            _saveTimersInterval     = 10000; // 10 seconds by default


    private SWTTimerWindow(SWTWindow parent, Workspace workspace, Task task) {
        _stopwatch = new Stopwatch();
        _stopwatch.addStopwatchListener(this);
        _task = task;
        _parent = parent;
        _workspace = workspace;
        long roundUpInterval = _workspace.getSettings().getRoundUpInterval();
        if (roundUpInterval > 0) {
            _saveTimersInterval = roundUpInterval;
        }
    }

    public Shell getParentShell() {
        return _parent.getShell();
    }

    public SWTWindow getParent() {
        return _parent;
    }

    private void setup(Shell shell) {
        GridLayout layout = new GridLayout();
        layout.numColumns = 1;
        shell.setLayout(layout);
        SWTUIManager.setTimeCultWindowIcons(shell);
        Display parentShellDisplay = _parent.getShell().getDisplay();
        Font font = _parent.getLcdFont();

        Color background = new Color(parentShellDisplay, 192, 230, 230);
        Color foreground = new Color(parentShellDisplay, 64, 115, 115);

        Composite labelCell = new Composite(shell, SWT.BORDER);
        GridData gridData = new GridData();
        gridData.grabExcessHorizontalSpace = true;
        labelCell.setLayoutData(gridData);
        labelCell.setLayout(new GridLayout());
        labelCell.setBackground(background);
        _timeLabel = new Label(labelCell, SWT.NONE);
        _timeLabel.setFont(font);
        _timeLabel.setText("00:00:00");
        _timeLabel.setBackground(background);
        _timeLabel.setForeground(foreground);
        _timeLabel.setAlignment(SWT.RIGHT);
        _timeLabel.setToolTipText(_task.toString());

        _toolBar = new SWTTimerToolBar(this, shell);

        java.awt.Point timerPos = TimeTracker.getInstance()
            .getConfigurationManager().getDefaultTimerPos();
        if (timerPos != null && AppPreferences.getInstance().isKeepTimerPos()) {
            shell.setLocation(timerPos.x, timerPos.y);
        }
        shell.addShellListener(new ShellAdapter() {
            @Override
            public void shellClosed(ShellEvent e) {
                terminate();
            }
        });
        shell.pack();
    }


    public static SWTTimerWindow newInstance(SWTWindow Window,
        Workspace workspace, Task task) {
        incInstances();
        return new SWTTimerWindow(Window, workspace, task);
    }


    public void launchTimer() {
        Shell parent = _parent.getShell();
        if (_shell == null) {
            int style = (PlatformUtil.isGtk ? SWT.CLOSE : SWT.ON_TOP) | SWT.TITLE;
            _shell = new Shell(parent.getDisplay(), style);
            if (_task.getName().length() > MAX_TITLE_CHARS) {
                _shell.setText(_task.getName().substring(0, MAX_TITLE_CHARS)
                    + "...");
            }
            else {
                _shell.setText(_task.getName());
            }
            setup(_shell);
            _shell.open();
        }
        else {
            _shell.setVisible(true);
        }
        _display = parent.getDisplay();
        _stopwatch.start();
        // addToTray();
        while (!_shell.isDisposed()) {
            if (!_display.readAndDispatch()) {
                _display.sleep();
            }
        }
    }


    public void stateChanged(StopwatchEvent evt) {
        switch (evt.getID()) {
        case StopwatchEvent.STOP:
            if (_task != null) {
                //
                // Keep the current timer position
                //
                org.eclipse.swt.graphics.Point timerLoc = _shell
                    .getLocation();
                TimeTracker.getInstance().getConfigurationManager()
                    .setDefaultTimerPos(timerLoc.x, timerLoc.y);
                addTimeRecord();
            }
            break;
        case StopwatchEvent.TICK:
            _duration = evt.getSource().getDuration();
            if (_duration - _lastNotifTime >= NOTIF_MESSAGE_INTERVAL
                && AppPreferences.getInstance().isRunningTimerNotification()) {
                    _lastNotifTime = evt.getSource().getDuration();
                    _parent.showPopupMessage(_task.toString() + "\n"
                    + Formatter.toDurationString(_duration, true));
            }
            long intervalTime = _duration - _lastIntervalTime;
            intervalTime = _workspace.roundUpTime(intervalTime);
            if (intervalTime >= _saveTimersInterval) {
                _lastIntervalTime = _duration;
                getTimeRecord().setDuration(_workspace.roundUpTime(_stopwatch.getDuration()));
                if (!_display.isDisposed()) {
                    _display.asyncExec(new Runnable() {

                        public void run() {
                            TimeTracker
                                .getInstance()
                                .getWorkspace()
                                .fireWorkspaceChanged(
                                    new WorkspaceEvent(
                                        WorkspaceEvent.WORKSPACE_TIME_REC_ADDED));
                        }
                    });
                }
            }
            if (!_display.isDisposed()) {
                _display.asyncExec(new Runnable() {

                    public void run() {
                        updateView(_duration);
                    }
                });
            }
            break;
        }
    }

    private boolean mustKeep() {
        return !(_timeRec.getDuration().getValue() == 0 && AppPreferences.getInstance().isDontSaveEmptyTimeRec());
    }

    private TimeRecord getTimeRecord() {
        if (_timeRec == null) {
            _timeRec = workspace.createRecord(
                _task,
                _stopwatch.getStartTime(),
                _workspace.roundUpTime(_stopwatch.getDuration()),
                "running...",
                true);
            if (mustKeep()) {
                TimeTracker.getInstance().getWorkspace()
                        .recordTimeEx(_timeRec, false);
            }
        }
        return _timeRec;
    }


    private void addTimeRecord() {
        AppPreferences appPrefs = TimeTracker.getInstance().getAppPreferences();
        TimeRecord timeRec = getTimeRecord();
        timeRec.setNotes("");
        TimeLogEntryEditDialog entryDialog = null;
        if (appPrefs.isShowRecEditDialog()) {
            entryDialog = new TimeLogEntryEditDialog(_parent, timeRec,
                true);
        }
        else {
            if (mustKeep()) {
                TimeTracker.getInstance().getWorkspace().recordTimeFinish(timeRec);
            }
        }
        _parent.restoreWindow();
        _shell.dispose();
        if (entryDialog != null) {
            entryDialog.open();
        }
    }


    public void updateView(long duration) {
        if (!_shell.isDisposed()) {
            // _shell.setVisible(true);
            adjustPosition(_shell);
            _timeLabel.setText(Formatter.toDurationString(duration, true));
            if (_trayItem != null) {
                _trayItem.setToolTipText(_task.toString() + ": "
                    + Formatter.toDurationString(duration, true));
                _trayItem.setImage(_parent.getIconSet().getIcon(
                    "timer." + Integer.toString(_imageCount),
                    true));
                _imageCount++;
                if (_imageCount >= NIMAGES) {
                    _imageCount = 0;
                }
            }
        }
    }


    /**
     * Checks if the timer window is within the limits of the currently active
     * display area. If not, move the timer window so, that it becomes visible
     * again.
     *
     * @param shell
     *            The timer window (shell).
     */
    private void adjustPosition(Shell shell) {
        Rectangle dispArea = shell.getDisplay().getBounds();
        Point timerLocation = shell.getLocation();
        if (!dispArea.contains(timerLocation)) {
            int x = timerLocation.x % dispArea.width;
            int y = timerLocation.y % dispArea.height;
            shell.setLocation(x, y);
        }
    }


    public Stopwatch getStopwatch() {
        return _stopwatch;
    }


    public void terminate() {
        if (_trayItem != null) {
            _trayItem.setVisible(false);
            _trayItem.dispose();
        }
        decInstances();
        _stopwatch.stop();
        _shell.dispose();
    }


    private static void decInstances() {
        SWTTimerWindow._instanceCount--;
        if (SWTTimerWindow._instanceCount == 0) {
            TimeTracker.getInstance().getWorkspace().startIdle();
        }
    }


    private static void incInstances() {
        if (_instanceCount == 0) {
            Workspace wsp = TimeTracker.getInstance().getWorkspace();
            wsp.stopIdle();
        }
        _instanceCount++;
    }


    public static boolean activeTimersExist() {
        return _instanceCount > 0;
    }


    private void addToTray() {
        Tray tray = _shell.getDisplay().getSystemTray();
        _trayItem = new TrayItem(tray, SWT.NONE);
        _trayItem.setImage(_parent.getIconSet().getIcon("timer.0", true));
        _trayItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent evt) {
                _shell.setVisible(true);
                _shell.setActive();
                _trayItem.setVisible(false);

            }
        });
        _trayItem.addListener(SWT.MenuDetect, new Listener() {
            public void handleEvent(Event evt) {
                _trayMenu = new TimerTrayMenu(SWTTimerWindow.this);
                _trayMenu.open();
            }
        });
    }


    public void hide() {
        _shell.setVisible(false);
        if (_trayItem == null) {
            addToTray();
        }
        else {
            _trayItem.setVisible(true);
        }
    }


    public Shell getShell() {
        return _shell;
    }


    public SWTTimerToolBar getToolBar() {
        return _toolBar;
    }

}
