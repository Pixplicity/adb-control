package com.pixplicity.adb;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.pixplicity.adb.ProcessInfo.PsRow;
import com.pixplicity.adb.widget.AdbWidget;
import com.pixplicity.adb.widget.AdbWidget1;
import com.pixplicity.adb.widget.AdbWidget2;

public class AdbService extends Service {

    private static final String TAG = AdbService.class.getSimpleName();

    /**
     * List of all widget types to look for.
     */
    private static final Class<?>[] WIDGET_CLASSES = new Class<?>[] {
            AdbWidget1.class,
            AdbWidget2.class
    };

    private volatile boolean mStopped;
    private int mBindings;

    private AsyncTask<Void, PsRow, Void> mTask;
    private BroadcastReceiver mReceiver;

    /**
     * Indicates whether or not we have information about the processes yet
     */
    private boolean mHadResult;
    /**
     * Most recent information about the ADB process
     */
    private PsRow mAdbProcess;
    private String mAction = AdbControlApp.ACTION_IDLE;
    private String mClassName;
    /**
     * Source widget ID, if the intent originated from a widget. Otherwise
     * {@code 0}.
     */
    private int mWidget;

    private final Handler handler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "[AdbService] created");
        if (mTask == null) {
            mTask = new AsyncTask<Void, PsRow, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    Log.v(TAG, "[AdbService] running background task...");
                    ProcessInfo pi = new ProcessInfo();
                    while (!mStopped) {
                        synchronized (mAction) {
                            if (AdbControlApp.ACTION_ENABLE.equals(mAction)) {
                                Log.v(TAG, "[AdbService] enabling adb...");
                                runRoot(AdbControlApp.ENABLE_COMMANDS);
                            } else if (AdbControlApp.ACTION_DISABLE
                                    .equals(mAction)) {
                                Log.v(TAG, "[AdbService] disabling adb...");
                                runRoot(AdbControlApp.DISABLE_COMMANDS);
                            }
                            mAction = AdbControlApp.ACTION_IDLE;
                        }
                        Log.d(TAG, "[AdbService] service executing process...");
                        pi.execute();
                        if (mStopped) {
                            break;
                        }
                        Log.d(TAG, "[AdbService] service executed process");
                        mAdbProcess = pi.getPsRow("/sbin/adbd");
                        mHadResult = true;
                        publishProgress(mAdbProcess);
                        if (mAction.equals(AdbControlApp.ACTION_IDLE)) {
                            synchronized (mTask) {
                                try {
                                    Log.d(TAG, "[AdbService] service sleeping");
                                    wait(AdbControlApp.REFRESH_INTERVAL);
                                } catch (InterruptedException e) {
                                    break;
                                }
                                Log.d(TAG, "[AdbService] service resuming");
                            }
                        } else {
                            Log.v(TAG, "[AdbService] proceeding to execute "
                                    + mAction);
                        }
                    }
                    Log.v(TAG, "[AdbService] background task ended");
                    return null;
                }

                @Override
                protected void onProgressUpdate(PsRow... process) {
                    PsRow adbProcess = null;
                    if (process.length > 0) {
                        adbProcess = process[0];
                    }
                    update(adbProcess);
                }
            };
            mTask.execute();
        }
        if (mReceiver == null) {
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onAction(intent);
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(AdbControlApp.ACTION_ENABLE);
            filter.addAction(AdbControlApp.ACTION_DISABLE);
            filter.addAction(AdbControlApp.ACTION_REQUEST_UPDATE);
            registerReceiver(mReceiver, filter);
        }
    }

    protected void runRoot(String[] commands) {
        Intent intent = new Intent(AdbControlApp.ACTION_COMPLETE);
        final RootResponse response = AdbControlApp.runRoot(null, this,
                mClassName, commands);
        intent.putExtra("response", response);
        intent.putExtra("widget", mWidget);
        if (mWidget > 0 && !AdbWidget.HANDLE_RESPONSE) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (response != null) {
                        Context context = getApplicationContext();
                        switch (response) {
                        case SUCCESS:
                            break;
                        case DENIED1:
                        case DENIED2:
                            Toast.makeText(context,
                                    context.getString(R.string.root_denied),
                                    Toast.LENGTH_LONG).show();
                            break;
                        case NO_SU:
                            Toast.makeText(
                                    context,
                                    context.getString(R.string.root_not_available),
                                    Toast.LENGTH_LONG).show();
                            break;
                        case FAILURE:
                            Toast.makeText(context,
                                    context.getString(R.string.root_failed),
                                    Toast.LENGTH_LONG).show();
                            break;
                        }
                    }
                }
            });
        }
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "[AdbService] started (action="
                + (intent == null ? "NULL" : intent.getAction())
                + "; startId=" + startId + ")");
        onAction(intent);
        // Immediately broadcast the latest information about the process, as
        // likely an widget has been added
        update(mAdbProcess);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "[AdbService] bound");
        mBindings++;
        onAction(intent);
        // Immediately broadcast the latest information about the process, as
        // likely an activity has connected
        update(mAdbProcess);
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "[AdbService] unbound");
        mBindings--;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "[AdbService] stopped");
        mStopped = true;
        if (mTask != null) {
            mTask.cancel(true);
            synchronized (mTask) {
                mTask.notify();
            }
        }
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    private void update(PsRow adbProcess) {
        if (!mHadResult) {
            return;
        }
        boolean bound = false;
        if (mBindings > 0) {
            bound = true;
        }
        if (!bound) {
            // Locate all widgets
            AppWidgetManager appWidgetManager = AppWidgetManager
                    .getInstance(this.getApplicationContext());
            for (Class<?> cls : WIDGET_CLASSES) {
                ComponentName thisWidget = new ComponentName(
                        getApplicationContext(), cls);
                if (appWidgetManager.getAppWidgetIds(thisWidget).length > 0) {
                    bound = true;
                    break;
                }
            }
        }
        if (!bound) {
            // Nobody's bound and no widgets to update
            Log.v(TAG, "[AdbService] stopping service as there's nothing to update");
            stopSelf();
            return;
        }

        // Broadcast the ADB process details
        Log.v(TAG, "[AdbService] broadcasting & updating widgets");

        Intent intent = new Intent(AdbControlApp.ACTION_UPDATED);
        String adbPid = null;
        if (adbProcess != null) {
            adbPid = adbProcess.pid;
        }
        intent.putExtra("pid", adbPid);
        sendBroadcast(intent);

        /*
        // Update each widget
        for (int widgetId : allWidgetIds) {
            RemoteViews remoteViews = new RemoteViews(this
                    .getApplicationContext().getPackageName(),
                    R.layout.widget_provider);
            // Set the text
            remoteViews.setTextViewText(R.id.update, adbPid);

            // Register an onClickListener
            Intent clickIntent = new Intent(this.getApplicationContext(),
                    AdbWidget.class);

            clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    allWidgetIds);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    getApplicationContext(), 0, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.update, pendingIntent);
            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
        */
    }

    private void onAction(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        synchronized (mAction) {
            mAction = intent.getAction();
            mClassName = intent.getStringExtra("class");
            mWidget = intent.getIntExtra("widget", 0);
            Log.v(TAG, "[AdbService] action received: " + mAction
                    + (mWidget > 0 ? " (from widget " + mWidget + ")" : ""));
        }
        if (AdbControlApp.ACTION_REQUEST_UPDATE.equals(mAction)) {
            update(mAdbProcess);
        }
        if (mTask != null) {
            synchronized (mTask) {
                mTask.notify();
            }
        }
    }

}
