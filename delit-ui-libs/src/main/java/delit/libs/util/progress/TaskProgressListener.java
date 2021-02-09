package delit.libs.util.progress;

public interface TaskProgressListener extends ProgressListener {

    void onTaskStarted();

    void onTaskFinished();
}
