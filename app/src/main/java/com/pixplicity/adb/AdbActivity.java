package com.pixplicity.adb;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pixplicity.adb.ProcessInfo.PsRow;
import com.pixplicity.adb.RootTask.RootExecListener;

public class AdbActivity extends AppCompatActivity implements RootExecListener {

    private static final String TAG = AdbActivity.class.getSimpleName();

    private static final String URL_HELP_ROOT = "https://en.wikipedia.org/wiki/Rooting_(Android_OS)";
    private static final String URL_HELP_ADB = "https://en.wikipedia.org/wiki/Android_software_development#Android_Debug_Bridge";

    private static final boolean USE_SERVICE = true;
    private static final boolean USE_BROADCASTS = true;

    private Button mBtnEnable, mBtnDisable;
    private Button mBtnHelp1, mBtnHelp2;
    private ProgressBar mPbAdb;
    private TextView mLbAdb1, mLbAdb2;

    private AsyncTask<Void, PsRow, Void> mTask;
    private ServiceConnection mServiceConnection;
    private BroadcastReceiver mReceiver;
    private ProgressDialog mProgress;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (BuildConfig.DEBUG
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mBtnEnable = (Button) findViewById(R.id.btn_enable);
        mBtnDisable = (Button) findViewById(R.id.btn_disable);
        mBtnHelp1 = (Button) findViewById(R.id.btn_help1);
        mBtnHelp2 = (Button) findViewById(R.id.btn_help2);

        mPbAdb = (ProgressBar) findViewById(R.id.pb_adb);
        mLbAdb1 = (TextView) findViewById(R.id.lb_adb1);
        mLbAdb2 = (TextView) findViewById(R.id.lb_adb2);

        mPbAdb.setVisibility(View.VISIBLE);
        mLbAdb1.setText(R.string.adb_checking);
        mLbAdb2.setVisibility(View.GONE);

        mBtnEnable.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                runEnable();
            }
        });
        mBtnDisable.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                runDisable();
            }
        });
        mBtnHelp1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelp(URL_HELP_ROOT);
            }
        });
        mBtnHelp2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelp(URL_HELP_ADB);
            }
        });

        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(AdbControlApp.ACTION_ENABLE)) {
                runEnable();
            } else if (intent.getAction().equals(AdbControlApp.ACTION_DISABLE)) {
                runDisable();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMonitoring(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMonitoring(true);
    }

    private void startMonitoring(boolean focusChange) {
        if (USE_SERVICE && focusChange) {
            if (mReceiver == null) {
                // Receive broadcasts
                mReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (AdbControlApp.ACTION_UPDATED.equals(intent
                                .getAction())) {
                            updateAdb(intent.getStringExtra("pid"));
                        } else if (AdbControlApp.ACTION_COMPLETE.equals(intent
                                .getAction())) {
                            if (mProgress != null) {
                                mProgress.dismiss();
                            }
                            RootResponse response = (RootResponse) intent
                                    .getSerializableExtra(AdbService.EXTRA_RESPONSE);
                            Log.i(TAG, "completion result: " + response);
                            switch (response) {
                            case SUCCESS:
                                onExecutionFinished(null);
                                break;
                            case NO_SU:
                                onRootNotAvailable(null);
                                break;
                            case DENIED1:
                            case DENIED2:
                                onRootDenied(null);
                                break;
                            default:
                            case FAILURE:
                                onExecutionFailure(null);
                                break;
                            }
                        }
                    }
                };
                IntentFilter filter = new IntentFilter();
                filter.addAction(AdbControlApp.ACTION_UPDATED);
                filter.addAction(AdbControlApp.ACTION_COMPLETE);
                registerReceiver(mReceiver, filter);
            }

            // Build the intent to call the service
            Intent serviceIntent = new Intent(this, AdbService.class);

            // Bind to the service
            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name,
                        IBinder service) {
                    Log.v(TAG, "[AdbActivity] service " + name + " connected: "
                            + service);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.v(TAG, "[AdbActivity] service " + name + " disconnected");
                }
            };
            try {
                bindService(serviceIntent, mServiceConnection,
                        Service.BIND_AUTO_CREATE);
            } catch (SecurityException e) {
                Toast.makeText(this, "Failed to bind to service",
                        Toast.LENGTH_LONG).show();
            }

            // Request the service to send an update
            Intent intent = new Intent(AdbControlApp.ACTION_REQUEST_UPDATE);
            sendBroadcast(intent);
        } else if (!USE_SERVICE) {
            if (mTask != null) {
                mTask.cancel(true);
            }
            mTask = new AsyncTask<Void, PsRow, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    ProcessInfo pi = new ProcessInfo();
                    while (!isCancelled()) {
                        pi.execute();
                        PsRow process = pi.getPsRow("/sbin/adbd");
                        publishProgress(process);
                        try {
                            Thread.sleep(AdbControlApp.REFRESH_INTERVAL);
                        } catch (InterruptedException e) {
                        }
                    }
                    return null;
                }

                @Override
                protected void onProgressUpdate(PsRow... process) {
                    PsRow adb = null;
                    if (process.length == 0) {
                        adb = process[0];
                    }
                    updateAdb(adb);
                }
            };
            mTask.execute();
        }
    }

    private void stopMonitoring(boolean focusChange) {
        if (USE_SERVICE && focusChange) {
            if (mServiceConnection != null) {
                unbindService(mServiceConnection);
                mServiceConnection = null;
            }
            if (mReceiver != null) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
            }
        } else if (!USE_SERVICE && mTask != null) {
            mTask.cancel(true);
        }
    }

    protected void updateAdb(PsRow process) {
        updateAdb(process == null ? null : process.pid);
    }

    protected void updateAdb(String pid) {
        mPbAdb.setVisibility(View.GONE);
        if (pid == null) {
            mBtnEnable.setText(R.string.bt_enable);
            mBtnEnable.setCompoundDrawablesWithIntrinsicBounds(
                    getResources().getDrawable(R.drawable.ic_dialog_start),
                    null, null, null);
            mLbAdb1.setText(R.string.adb_not_running);
            mLbAdb2.setText("");
            mLbAdb2.setVisibility(View.GONE);
        } else {
            mBtnEnable.setText(R.string.bt_restart);
            mBtnEnable.setCompoundDrawablesWithIntrinsicBounds(
                    getResources().getDrawable(R.drawable.ic_dialog_restart),
                    null, null, null);
            mLbAdb1.setText(R.string.adb_running);
            mLbAdb2.setText(String.format(getString(R.string.pid), pid));
            mLbAdb2.setVisibility(View.VISIBLE);
        }
    }

    protected void showHelp(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    @Override
    public void onRootNotAvailable(final RootTask execution) {
        showRootDialog(execution, true);
    }

    @Override
    public void onRootDenied(final RootTask execution) {
        showRootDialog(execution, false);
    }

    @Override
    public void onExecutionFinished(final RootTask execution) {
        startMonitoring(false);
    }

    @Override
    public void onExecutionFailure(final RootTask execution) {
        Toast.makeText(this, getString(R.string.root_failed), Toast.LENGTH_LONG)
                .show();
        startMonitoring(false);
    }

    public void showRootDialog(final RootTask execution, final boolean showHelp) {
        if (isFinishing()) {
            return;
        }
        final AlertDialog.Builder dialog;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            dialog = new AlertDialog.Builder(AdbActivity.this, R.style.AlertDialogStyle);
        } else {
            dialog = new AlertDialog.Builder(AdbActivity.this);
        }
        dialog.setTitle(R.string.root_title)
                .setMessage(R.string.root_denied);
        if (execution != null || showHelp) {
            dialog.setPositiveButton(showHelp ? R.string.bt_help1
                    : R.string.bt_retry,
                    new AlertDialog.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (showHelp) {
                                showHelp(URL_HELP_ROOT);
                            } else {
                                runRoot(execution.getCommands());
                            }
                        }
                    });
        }
        dialog.setNegativeButton(execution == null ? R.string.bt_ok
                : R.string.bt_cancel,
                new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        dialog.show();
        startMonitoring(false);
    }

    private void runRoot(String[] commands) {
        stopMonitoring(false);
        new RootTask(this, this).execute(commands);
    }

    private void runEnable() {
        if (USE_BROADCASTS) {
            Intent intent = new Intent(AdbControlApp.ACTION_ENABLE);
            intent.putExtra("class", getClass().getName());
            sendBroadcast(intent);
        } else {
            runRoot(AdbControlApp.ENABLE_COMMANDS);
        }
    }

    private void runDisable() {
        if (USE_BROADCASTS) {
            Intent intent = new Intent(AdbControlApp.ACTION_DISABLE);
            intent.putExtra("class", getClass().getName());
            sendBroadcast(intent);
        } else {
            runRoot(AdbControlApp.DISABLE_COMMANDS);
        }
    }

    @Override
    public void sendBroadcast(Intent intent) {
        if (AdbControlApp.ACTION_ENABLE.equals(intent.getAction())
                || AdbControlApp.ACTION_DISABLE.equals(intent.getAction())) {
            mProgress = new ProgressDialog(this);
            mProgress.setMessage(getString(R.string.busy));
            mProgress.setIndeterminate(true);
            mProgress.show();
        }
        super.sendBroadcast(intent);
    }

}