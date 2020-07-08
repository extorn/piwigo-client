package delit.libs.ui.util;

public class SimpleSubTaskProgressTracker extends TaskProgressTracker {

    private ProgressListener mainTaskListener;

    public SimpleSubTaskProgressTracker(ProgressListener mainTaskListener) {
        this.mainTaskListener = mainTaskListener;
    }

    @Override
    protected void reportProgress(int newOverallProgress) {
        mainTaskListener.onProgress(newOverallProgress);
    }
}
