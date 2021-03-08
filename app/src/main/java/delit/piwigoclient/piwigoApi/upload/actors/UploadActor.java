package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class UploadActor {
    private final ActorListener listener;
    private final UploadJob uploadJob;
    private final StatelessErrorRecordingServerCaller serverCaller;
    private final Context context;

    public UploadActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        this.context = context;
        this.uploadJob = uploadJob;
        this.listener = listener;
        serverCaller = new StatelessErrorRecordingServerCaller(context);
    }

    protected SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public ActorListener getListener() {
        return listener;
    }

    public Context getContext() {
        return context;
    }

    public StatelessErrorRecordingServerCaller getServerCaller() {
        return serverCaller;
    }

    public UploadJob getUploadJob() {
        return uploadJob;
    }

}
