package com.pixplicity.adb;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

public class RootTask extends AsyncTask<String, String, RootResponse> {

    public interface RootExecListener {
        public abstract void onRootNotAvailable(RootTask execution);

        public abstract void onRootDenied(RootTask execution);

        public abstract void onExecutionFinished(RootTask execution);

        public abstract void onExecutionFailure(RootTask execution);
    }

    private final Context mContext;
    private Activity mActivity = null;
    private final RootExecListener mListener;
    private ProgressDialog mProgress;

    private String[] mCommands;
    private int mCommandIndex;

    public RootTask(Context context, RootExecListener listener) {
        this.mContext = context;
        this.mListener = listener;
    }

    public RootTask(Activity activity, RootExecListener listener) {
        this((Context) activity, listener);
        this.mActivity = activity;
    }

    public String[] getCommands() {
        return mCommands;
    }

    @Override
    protected void onPreExecute() {
        mProgress = new ProgressDialog(mContext);
        mProgress.setMessage(mContext.getString(R.string.busy));
        mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgress.setMax(1);
        mProgress.setIndeterminate(false);
        mProgress.setCancelable(false);
        mProgress.show();
        mCommandIndex = 0;
    }

    @Override
    protected void onProgressUpdate(String... lines) {
        mCommandIndex++;
        if (mProgress != null) {
            mProgress.setMax(mCommands.length);
            mProgress.setProgress(mCommandIndex);
        }
    }

    @Override
    protected void onPostExecute(RootResponse result) {
        mProgress.dismiss();
        switch (result) {
        case SUCCESS:
            mListener.onExecutionFinished(this);
            break;
        case NO_SU:
            mListener.onRootNotAvailable(this);
            break;
        case DENIED1:
        case DENIED2:
            mListener.onRootDenied(this);
            break;
        default:
        case FAILURE:
            mListener.onExecutionFailure(this);
            break;
        }
    }

    public void executeOnMainThread(String... commands) {
        onPreExecute();
        onPostExecute(doInBackground(commands));
    }

    @Override
    protected RootResponse doInBackground(String... commands) {
        mCommands = commands;
        return AdbControlApp.runRoot(this, mActivity, mActivity.getClass()
                .getName(), commands);
    }

    public void onLineExecuted(String[] lines) {
        publishProgress(lines);
    }

}
