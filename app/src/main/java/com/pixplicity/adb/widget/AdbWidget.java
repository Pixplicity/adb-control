package com.pixplicity.adb.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
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
            views.setOnClickPendingIntent(
                    R.id.icon,
                    makePendingIntent(context, AdbControlApp.ACTION_OPEN,
                            appWidgetId));
            views.setOnClickPendingIntent(
                    R.id.button1,
                    makePendingIntent(context, AdbControlApp.ACTION_ENABLE,
                            appWidgetId));
            views.setOnClickPendingIntent(
                    R.id.button2,
                    makePendingIntent(context, AdbControlApp.ACTION_DISABLE,
                            appWidgetId));

            // Tell the AppWidgetManager to perform an update on the current app
            // widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onEnabled(Context context) {
        updateStatus(context, 0, null);
    }

    @Override
    public void onDisabled(Context context) {
        if (USE_ALARMS) {
            setAlarm(context, -1, false);
        } else {
            Log.i(TAG, "[AdbWidget] disabled");
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
            Log.d(TAG, "[AdbWidget] deleted: " + Arrays.toString(appWidgetIds));
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
        Log.i(TAG, "[AdbWidget] received action " + action);
        if (AdbControlApp.ACTION_UPDATED.equals(action)) {
            updateAdb(context, intent.getStringExtra("pid"));
        } else if (AdbControlApp.ACTION_COMPLETE.equals(action)) {
            updateStatus(context, 0,
                    (RootResponse) intent.getSerializableExtra("response"));
        } else if (AdbControlApp.ACTION_ENABLE.equals(action)
                || AdbControlApp.ACTION_DISABLE.equals(action)) {
            updateStatus(context, 1, null);
        } else {
            super.onReceive(context, intent);
        }
    }

    protected void updateAdb(Context context, String pid) {
        AppWidgetManager appWidgetManager = AppWidgetManager
                .getInstance(context);

        // Get the layout for the App Widget and attach an on-click listener
        // to the button
        RemoteViews views = new RemoteViews(context.getPackageName(),
                getLayout());

        final int resImg, resText;
        if (pid == null) {
            resImg = R.drawable.ic_dialog_start;
            resText = R.string.bt_enable;
        } else {
            resImg = R.drawable.ic_dialog_restart;
            resText = R.string.bt_restart;
        }
        views.setInt(R.id.button1, "setImageResource", resImg);
        /* FIXME doesn't appear to work on older devices
        views.setCharSequence(R.id.button1, "setContentDescription",
                context.getString(resText));
        */

        // define the componenet for self
        ComponentName comp = new ComponentName(context.getPackageName(),
                getClass().getName());

        // tell the manager to update all instances of the toggle widget with
        // the click listener
        appWidgetManager.updateAppWidget(comp, views);
    }

    protected void updateStatus(Context context, int buttonBusy,
            RootResponse response) {
        AppWidgetManager appWidgetManager = AppWidgetManager
                .getInstance(context);

        // Get the layout for the App Widget and attach an on-click listener
        // to the button
        RemoteViews views = new RemoteViews(context.getPackageName(),
                getLayout());

        switch (buttonBusy) {
        case 1:
            // TODO
            break;
        case 2:
            // TODO
            break;
        }
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
        PendingIntent newPending = makePendingIntent(context,
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

    public static PendingIntent makePendingIntent(Context context,
            String action, int appWidgetId) {
        IntentType type = IntentType.BROADCAST;
        Intent intent;
        int flags = 0;
        if (action.equals(AdbControlApp.ACTION_REQUEST_UPDATE)) {
            // Start a service
            intent = new Intent(context, AdbService.class);
            type = IntentType.SERVICE;
        } else if (action.equals(AdbControlApp.ACTION_OPEN)) {
            // Start an activity
            intent = new Intent(context, AdbActivity.class);
            type = IntentType.ACTIVITY;
        } else {
            // Simply broadcast the command
            intent = new Intent(action);
        }
        intent.putExtra("widget", appWidgetId);
        intent.setAction(action);
        switch (type) {
        case SERVICE:
        case ACTIVITY:
            flags |= PendingIntent.FLAG_UPDATE_CURRENT;
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            // this Uri data is to make the PendingIntent unique, so it wont be
            // updated by FLAG_UPDATE_CURRENT so if there are multiple widget
            // instances they wont override each other
            Uri data = Uri.withAppendedPath(
                    Uri.parse("adbwidget://widget/id/#" + action
                            + appWidgetId), String.valueOf(appWidgetId));
            intent.setData(data);
            break;
        }
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

    protected abstract int getLayout();

    protected abstract int[] getButtons();

}
