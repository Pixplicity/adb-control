package com.pixplicity.adb;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import io.fabric.sdk.android.Fabric;

public class AdbControlApp extends Application {

    private static final String TAG = AdbControlApp.class.getSimpleName();

    public static final String ACTION_OPEN = "com.pixplicity.adb.OPEN";
    public static final String ACTION_ENABLE = "com.pixplicity.adb.ENABLE";
    public static final String ACTION_DISABLE = "com.pixplicity.adb.DISABLE";
    public static final String ACTION_REQUEST_UPDATE = "com.pixplicity.adb.REQUEST_UPDATE";
    public static final String ACTION_UPDATED = "com.pixplicity.adb.UPDATED";
    public static final String ACTION_COMPLETE = "com.pixplicity.adb.COMPLETE";
    public static final String ACTION_IDLE = "";

    protected static final long REFRESH_INTERVAL = 30000;
    protected static final String SU_COMMAND_FOREGROUND = "__FOREGROUND__";

    protected static final String[] TEST_COMMANDS = new String[] {
            "sleep 3",
            AdbControlApp.SU_COMMAND_FOREGROUND,
            "sleep 3",
            AdbControlApp.SU_COMMAND_FOREGROUND,
    };
    protected static final String[] ENABLE_COMMANDS = new String[] {
            "setprop persist.service.adb.enable 1",
            "stop adbd",
            "sleep 1",
            AdbControlApp.SU_COMMAND_FOREGROUND,
            "start adbd",
            "sleep 1",
            AdbControlApp.SU_COMMAND_FOREGROUND,
    };
    protected static final String[] DISABLE_COMMANDS = new String[] {
            "setprop persist.service.adb.enable 0",
            "stop adbd",
            "sleep 1",
            AdbControlApp.SU_COMMAND_FOREGROUND,
    };

    private static final int MAX_LINES = 50;

    @Override
    public void onCreate() {
        super.onCreate();

        Fabric.with(this, new Crashlytics());
    }

    protected static RootResponse runRoot(RootTask task, Context context,
                                          String className, String... commands) {
        RootResponse retval = RootResponse.FAILURE;
        Log.v(TAG, "su with " + commands.length + " command(s)");

        boolean suDenied = false;
        try {
            if (commands != null && commands.length > 0) {
                Log.v(TAG, "su");
                ProcessBuilder builder = new ProcessBuilder("su");
                builder.redirectErrorStream(true);
                Process suProcess = builder.start();
                DataOutputStream os = new DataOutputStream(
                        suProcess.getOutputStream());
                DataInputStream is = new DataInputStream(
                        suProcess.getInputStream());

                // Execute commands that require root access
                for (String command : commands) {
                    if (command.equals(SU_COMMAND_FOREGROUND)) {
                        Log.v(TAG, "bring to foreground");
                        bringToForeground(context, className);
                        continue;
                    }
                    Log.v(TAG, "su >> " + command);
                    if (!command.trim().equals("exit")) {
                        command = "(("
                                + command
                                + ") && echo \"--EOL--\") || echo \"--EOL--\"\n";
                    }
                    os.writeBytes(command + "\n");
                    os.flush();
                    ArrayList<String> lines = new ArrayList<String>();
                    for (int lineNumber = 0; lineNumber < MAX_LINES; lineNumber++) {
                        String line = is.readLine();
                        if (line != null && !line.trim().equals("--EOL--")) {
                            Log.v(TAG, "su << [" + lineNumber + "] " + line);
                        } else {
                            break;
                        }
                        if (line.contains("not allowed to su")) {
                            suDenied = true;
                            break;
                        }
                        lines.add(line);
                    }
                    if (suDenied) {
                        break;
                    }
                    if (task != null) {
                        task.onLineExecuted(lines.toArray(new String[0]));
                    }
                }

                if (!suDenied) {
                    os.writeBytes("exit\n");
                    os.flush();
                    Log.d(TAG, "su finished");
                }

                try {
                    int suProcessRetval = suProcess.waitFor();
                    if (suDenied) {
                        // We received a rejection response
                        retval = RootResponse.NO_SU;
                    } else if (255 != suProcessRetval) {
                        // Root access granted
                        retval = RootResponse.SUCCESS;
                    }
                } catch (InterruptedException ex) {
                    // The thread was interrupted; assume success
                    retval = RootResponse.SUCCESS;
                } catch (Exception ex) {
                    Log.e(TAG, "Error executing root action", ex);
                    retval = RootResponse.FAILURE;
                }
            }
        } catch (IOException ex) {
            Log.w("Can't get root access", ex);
            if (ex.getMessage().contains("EPIPE")
                    || ex.getMessage().contains("Error running exec")) {
                retval = RootResponse.NO_SU;
            } else {
                retval = RootResponse.DENIED1;
            }
        } catch (SecurityException ex) {
            Log.w("Can't get root access", ex);
            retval = RootResponse.DENIED2;
        } catch (Exception ex) {
            Log.w("Error executing internal operation", ex);
            retval = RootResponse.FAILURE;
        }
        return retval;
    }

    public static void bringToForeground(Context context, String className) {
        if (context != null) {
            Intent i = new Intent();
            i.setAction(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            ComponentName cn = new ComponentName(context, className);
            i.setComponent(cn);
            context.startActivity(i);
        }
    }

}
