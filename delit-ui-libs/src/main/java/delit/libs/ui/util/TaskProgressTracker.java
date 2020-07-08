package delit.libs.ui.util;

/**
 * Override reportProgress to manage the progress tracked
 * <p>
 * task comprises of x ticks (equal sized)
 */
public abstract class TaskProgressTracker implements ProgressListener {

    private final double reportAtIncrement;
    private double overallTaskProgress;
    private double lastProgressReportAt;
    private double mainTaskProgressPerPercentOfThisTask;
    private int ticksInTask;
    private double progressPerTick;

    public TaskProgressTracker() {
        this(1);
    }

    public TaskProgressTracker(double reportAtIncrement) {
        if(Math.max(100,Math.round(reportAtIncrement)) == 100) {
            reportAtIncrement = 100;
        } else if(Math.round(reportAtIncrement) == 0) {
            reportAtIncrement = 0;
        }
        if(reportAtIncrement < 0 || reportAtIncrement > 100) {
            throw new IllegalArgumentException("report increment must be between 0 and 100");
        }
        this.reportAtIncrement = reportAtIncrement / 100;
    }

    /**
     * A single tick task. Progress can be fluid across that task
     *
     * @param startProgress what progress of the main task will be before this task runs (between 0 and 100)
     * @param endProgress   what progress of the main task will be after this task finishes
     * @return this
     */
    public TaskProgressTracker withStage(double startProgress, double endProgress) {
        return withStage(startProgress, endProgress, 1);
    }

    /**
     * A multi tick task. Progress can be fluid across that task using onProgress(int), but if wished,
     * progress can be jumped tick at a time by using onTick(x) where x is the last complete tick within the task of y ticks
     * There is NO logic to stop you jumping back and forward in progress if you wish by
     * using these two methods interchangeably.
     *
     * @param startProgress what progress of the main task will be before this task runs (between 0 and 100)
     * @param endProgress   what progress of the main task will be after this task finishes
     * @param ticksInTask     the number of equal length sub tasks this task can be divided into
     * @return this
     */
    public TaskProgressTracker withStage(double startProgress, double endProgress, int ticksInTask) {
        if(startProgress < 0 || startProgress > 100) {
            if(Math.round(startProgress) == 0) {
                startProgress = 0;
            } else {
                throw new IllegalArgumentException("start progress must be between 0 and 100 : " + startProgress);
            }
        }
        if(endProgress < 0 || endProgress > 100) {
            if(Math.max(100, Math.round(endProgress)) == 100) {
                endProgress = 100;
            } else {
                throw new IllegalArgumentException("end progress must be between 0 and 100 : " + endProgress);
            }
        }
        if(startProgress >= endProgress) {
            throw new IllegalArgumentException("end progress must be more than start progress: " + startProgress + " - " + endProgress);
        }
        this.mainTaskProgressPerPercentOfThisTask = (endProgress - startProgress) / (100 * 100);
        this.overallTaskProgress = startProgress / 100;
        this.lastProgressReportAt = overallTaskProgress;
        this.ticksInTask = ticksInTask;
        this.progressPerTick = mainTaskProgressPerPercentOfThisTask * 100 / ticksInTask;
        return this;
    }

    public double getOverallProgressOnceFinished() {
        return mainTaskProgressPerPercentOfThisTask * 100;
    }

    public double getOverallTaskProgress() {
        return overallTaskProgress;
    }

    /**
     * an even division of the task where tick is between 0 and ticks
     *
     * @param tick
     */
    public void onTick(int tick) {
        if (tick < 0 || tick > ticksInTask) {
            throw new IllegalArgumentException("tick must be between 0 and " + ticksInTask);
        }
        onProgressIncrement(tick * progressPerTick);
    }

    /**
     * @return A number between 0 and 1
     */
    public double getProgressPerTick() {
        return progressPerTick;
    }

    /**
     * @return A number between 0 and 1
     */
    public double getMainTaskProgressPerPercentOfThisTask() {
        return mainTaskProgressPerPercentOfThisTask;
    }

    /**
     * A potentially random jump of percentage complete independent of any others.
     *
     * @param percentCompleteOfThisTask Total percentage complete of this task
     */
    @Override
    public void onProgress(int percentCompleteOfThisTask) {
        if (percentCompleteOfThisTask < 0 || percentCompleteOfThisTask > 100) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }
        onProgressIncrement(mainTaskProgressPerPercentOfThisTask * percentCompleteOfThisTask);
    }

    private void onProgressIncrement(double overallProgressIncrement) {
        double actualCurrentOverallTaskProgress = (overallTaskProgress + overallProgressIncrement);
        if (100 * actualCurrentOverallTaskProgress >= lastProgressReportAt + reportAtIncrement) {
            lastProgressReportAt = actualCurrentOverallTaskProgress;
            reportProgress((int)Math.rint(actualCurrentOverallTaskProgress * 100));
        }
    }

    /**
     * progress through task - between 0 and 100.
     * @param newOverallProgress
     */
    protected abstract void reportProgress(int newOverallProgress);

    public double getMainTaskProgressPerTickOfThisTask() {
        return progressPerTick;
    }

    /**
     * @return progress through task - between 0 and 100.
     */
    public int getLastReportedProgress() {
        return (int)Math.rint(lastProgressReportAt * 100);
    }

}