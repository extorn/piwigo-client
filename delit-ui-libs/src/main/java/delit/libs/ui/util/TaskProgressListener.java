package delit.libs.ui.util;

public interface TaskProgressListener extends ProgressListener {

    void onTaskStarted();

    void onTaskFinished();
}
