package delit.piwigoclient.ui.file;

public interface TaskProgressListener {
    void onTaskProgress(double percentageComplete);

    void onTaskStarted();

    void onTaskFinished();
}
