package delit.libs.util.progress;

import androidx.annotation.FloatRange;

public class DividableLinkProgressListener extends SimpleProgressListener implements DividableProgressTracker.ChildProgressListener {

    private final DividableProgressTracker parentTask;

    public DividableLinkProgressListener(@FloatRange(from = 0, to = 1) double notifyOnProgress, DividableProgressTracker parentTask) {
        super(notifyOnProgress);
        this.parentTask = parentTask;
    }

    @Override
    protected void onNotifiableProgress(double percent) {
        parentTask.onProgressChange(false);
    }

    @Override
    public void onRollback(DividableProgressTracker childTask) {
        parentTask.onChildTaskEventRollback(childTask);
    }

    @Override
    public void onComplete(DividableProgressTracker childTask) {
        parentTask.onChildTaskEventComplete(childTask);
    }

    @Override
    public void onComplete() {
        //Just force the parent to refresh the progress.
        parentTask.onProgressChange(true);
    }
}