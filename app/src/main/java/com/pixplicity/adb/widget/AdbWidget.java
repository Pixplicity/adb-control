package com.pixplicity.adb.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.pixplicity.adb.AdbActivity;
import com.pixplicity.adb.AdbControlApp;
import com.pixplicity.adb.AdbService;
import com.pixplicity.adb.R;
import com.pixplicity.adb.RootResponse;

import java.util.Arrays;

public abstract class AdbWidget extends AppWidgetProvider {

    private static final String TAG = AdbWidget.class.getSimpleName();

    private static final boolean USE_ALARMS = false;
    private static final long ALARM_INTERVAL = 3000;

    public static final boolean HANDLE_RESPONSE = false;


    private enum IntentType {
        BROADCAST,
        ACTIVITY,
        SERVICE,
    }


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        if (!USE_ALARMS) {
            Intent intent = new Intent(context, AdbService.class);
            intent.putExtra("widgets", appWidgetIds);
            context.startService(intent);
        }

        // Perform this loop procedure for each App Widget that belongs to this
        // provider
        for (int appWidgetId : appWidgetIds) {
            if (USE_ALARMS) {
                setAlarm(context, appWidgetId, true);
            }

            // Get the layout for the App Widget and attach an on-click listener
            // to the button
            RemoteViews views = new RemoteViews(context.getPackageName(),
                    getLayout());
            updateRemoteViews(context, appWidgetId, views);

            // Tell the AppWidgetManager to perform an update on the current app
            // widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private void updateRemoteViews(Context context, int appWidgetId, RemoteViews views) {
        if (appWidgetId == -1) {
            int[] appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    new ComponentName(context.getApplicationContext(), getClass()));
            for (int widgetId : appWidgetIds) {
                updateRemoteViews(context, widgetId, views);
            }
            return;
        }

        Log.d(TAG, "update widget " + appWidgetId);

        // Faster operation for HC and up
        if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            views.setOnClickFillInIntent(
                    R.id.icon,
                    makeIntent(context, IntentType.ACTIVITY, AdbControlApp.ACTION_OPEN,
                            appWidgetId));
            views.setOnClickFillInIntent(
                    R.id.button1,
                    makeIntent(context, IntentType.SERVICE, AdbControlApp.ACTION_ENABLE,
                            appWidgetId));
            views.setOnClickFillInIntent(
                    R.id.button2,
                    makeIntent(context, IntentType.SERVICE, AdbControlApp.ACTION_DISABLE,
                            appWidgetId));
        } else {
            views.setOnClickPendingIntent(
                    R.id.icon,
                    makePendingIntent(context, IntentType.ACTIVITY, AdbControlApp.ACTION_OPEN,
                            appWidgetId));
            views.setOnClickPendingIntent(
                    R.id.button1,
                    makePendingIntent(context, IntentType.SERVICE, AdbControlApp.ACTION_ENABLE,
                            appWidgetId));
            views.setOnClickPendingIntent(
                    R.id.button2,
                    makePendingIntent(context, IntentType.SERVICE, AdbControlApp.ACTION_DISABLE,
                            appWidgetId));
        }
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(TAG, "enabled");
        updateStatus(context, 0, -1, null);
    }

    @Override
    public void onDisabled(Context context) {
        if (USE_ALARMS) {
            setAlarm(context, -1, false);
        } else {
            Log.i(TAG, "disabled");
            // FIXME We don't know if other widgets are running; how do we
            // manage stopping the service?
            /*
            Intent intent = new Intent(context, AdbService.class);
            context.stopService(intent);
            */
        }
        super.onDisabled(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        if (USE_ALARMS) {
            for (int appWidgetId : appWidgetIds) {
                setAlarm(context, appWidgetId, false);
            }
        } else {
            Log.d(TAG, "deleted: " + Arrays.toString(appWidgetIds));
            Intent intent = new Intent(AdbControlApp.ACTION_REQUEST_UPDATE);
            intent.putExtra("widgets-remove", appWidgetIds);
            context.sendBroadcast(intent);
            // FIXME We don't know if other widgets are running; how do we
            // manage stopping the service?
            /*
            Intent intent = new Intent(context, AdbService.class);
            context.stopService(intent);
            */
        }
        super.onDeleted(context, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "received action " + action);
        int appWidgetId = intent.getIntExtra(AdbService.EXTRA_WIDGET_ID, -1);
        if (AdbControlApp.ACTION_UPDATED.equals(action)) {
            updateAdb(context, appWidgetId, intent.getStringExtra("pid"));
        } else if (AdbControlApp.ACTION_COMPLETE.equals(action)) {
            updateStatus(context, 0, appWidgetId,
                    (RootResponse) intent.getSerializableExtra(AdbService.EXTRA_RESPONSE));
        } else if (AdbControlApp.ACTION_ENABLE.equals(action)
                || AdbControlApp.ACTION_DISABLE.equals(action)) {
            updateStatus(context, 1, appWidgetId, null);
        } else {
            super.onReceive(context, intent);
        }
    }

    protected void updateAdb(Context context, int appWidgetId, String pid) {
        AppWidgetManager appWidgetManager = AppWidgetManager
                .getInstance(context);

        // Get the layout for the App Widget and attach an on-click listener
        // to the button
        RemoteViews views = new RemoteViews(context.getPackageName(), getLayout());
        updateRemoteViews(context, appWidgetId, views);

        final int resImg, resText;
        if (pid == null) {
            resImg = R.drawable.ic_dialog_start;
            resText = R.string.adb_enable;
        } else {
            resImg = R.drawable.ic_dialog_restart;
            resText = R.string.adb_restart;
        }
        views.setInt(R.id.button1, "setImageResource", resImg);
        /* FIXME doesn't appear to work on older devices
        views.setCharSequence(R.id.button1, "setContentDescription",
                context.getString(resText));
        */

        // define the component for self
        ComponentName comp = new ComponentName(context.getPackageName(),
                getClass().getName());

        // tell the manager to update all instances of the toggle widget with
        // the click listener
        appWidgetManager.updateAppWidget(comp, views);
    }

    protected void updateStatus(Context context, int buttonBusy, int appWidgetId,
                                RootResponse response) {
        AppWidgetManager appWidgetManager = AppWidgetManager
                .getInstance(context);

        // Get the layout for the App Widget and attach an on-click listener
        // to the button
        RemoteViews views = new RemoteViews(context.getPackageName(), getLayout());
        updateRemoteViews(context, appWidgetId, views);

        switch (buttonBusy) {
            case 1:
            case 2:
                for (int button : getButtons()) {
                    views.setViewVisibility(button, View.GONE);
                }
                views.setViewVisibility(R.id.progress, View.VISIBLE);
                break;
            default:
                if (HANDLE_RESPONSE && response != null) {
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
                            Toast.makeText(context,
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
                views.setViewVisibility(R.id.progress, View.GONE);
                for (int button : getButtons()) {
                    views.setViewVisibility(button, View.VISIBLE);
                }
                break;
        }

        // define the componenet for self
        ComponentName comp = new ComponentName(context.getPackageName(),
                getClass().getName());

        // tell the manager to update all instances of the toggle widget with
        // the click listener
        appWidgetManager.updateAppWidget(comp, views);
    }

    public void setAlarm(Context context, int appWidgetId, boolean enable) {
        if (appWidgetId < 0) {
            if (!enable) {
                AppWidgetManager appWidgetManager = AppWidgetManager
                        .getInstance(context);
                ComponentName thisWidget = new ComponentName(context,
                        getClass());
                for (int id : appWidgetManager.getAppWidgetIds(thisWidget)) {
                    setAlarm(context, id, enable);
                }
            }
            return;
        }
        PendingIntent newPending = makePendingIntent(context, IntentType.SERVICE,
                AdbControlApp.ACTION_REQUEST_UPDATE, appWidgetId);
        AlarmManager alarms = (AlarmManager) context
                .getSystemService(Context.ALARM_SERVICE);
        if (enable) {
            Log.i(TAG, "alarm enabled for " + appWidgetId);
            alarms.setRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime(), ALARM_INTERVAL, newPending);
        } else {
            Log.i(TAG, "alarm disabled for " + appWidgetId);
            // on a negative updateRate stop the refreshing
            alarms.cancel(newPending);
        }
    }

    public static PendingIntent makePendingIntent(Context context, IntentType type,
                                                  String action, int appWidgetId) {
        Intent intent = makeIntent(context, type, action, appWidgetId);
        int flags = 0;
        flags |= PendingIntent.FLAG_UPDATE_CURRENT;
        intent.getType();
        switch (type) {
            case SERVICE:
                return PendingIntent
                        .getService(context, 0, intent, flags);
            case ACTIVITY:
                return PendingIntent
                        .getActivity(context, 0, intent, flags);
            case BROADCAST:
                return PendingIntent
                        .getBroadcast(context, 0, intent, flags);
        }
        return null;
    }

    @NonNull
    private static Intent makeIntent(Context context, IntentType type,
                                     String action, int appWidgetId) {
        Intent intent = null;
        switch (type) {
            case SERVICE:
                intent = new Intent(context, AdbService.class);
                break;
            case ACTIVITY:
                intent = new Intent(context, AdbActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                break;
            case BROADCAST:
                intent = new Intent(action);
                break;
        }
        intent.putExtra(AdbService.EXTRA_WIDGET_ID, appWidgetId);
        intent.setAction(action);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // this Uri data is to make the PendingIntent unique, so it wont be
        // updated by FLAG_UPDATE_CURRENT so if there are multiple widget
        // instances they wont override each other
        Uri data = Uri.withAppendedPath(
                Uri.parse("adbwidget://widget/id/#" + action
                        + appWidgetId), String.valueOf(appWidgetId));
        intent.setData(data);
        return intent;
    }

    protected abstract int getLayout();

    protected abstract int[] getButtons();

}
