package delit.libs.ui.util;

import androidx.annotation.FloatRange;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import delit.libs.util.progress.ProgressListener;
import delit.libs.util.progress.TaskProgressTracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class TaskProgressTrackerTest {

    public static final double EPSILON = 0.0001;
    private TestProgressListener testProgressListener;

    private class TestProgressListener implements ProgressListener {
        private List<Double> reports = new ArrayList<>();
        private double reportedProgress;
        private @FloatRange(from = 0, to = 1) double updateStep;

        @Override
        public void onProgress(@FloatRange(from = 0, to = 1) double percent) {
            reports.add(percent);
            reportedProgress = percent;
        }

        public double getReportedProgress() {
            return reportedProgress;
        }

        public void assertReportsValid() {
            double lastReport = Double.MIN_VALUE;
            double updateStep = getUpdateStep();
            for(Double report : reports) {
                Assert.assertTrue("progress reports should be spaced by at least " + updateStep,lastReport + updateStep <= report);
                lastReport = report;
            }
            assertEquals(1, reports.get(reports.size() -1), 0.0001);
        }

        @Override
        public @FloatRange(from = 0, to = 1) double getUpdateStep() {
            return updateStep;
        }

        public void setUpdateStep(@FloatRange(from = 0, to = 1) double updateStep) {
            this.updateStep = updateStep;
        }
    }

    @Before
    public void initialise() {
        testProgressListener = new TestProgressListener();
    }

    @Test
    public void testBasicTracker() {
        int totalWork = 20;
        testProgressListener.setUpdateStep(0.05);//5%
        TaskProgressTracker tracker = new TaskProgressTracker("task", totalWork, testProgressListener);
        assertFalse(tracker.isComplete());
        for(int i = 0; i < totalWork; i++) {
            tracker.setWorkDone(i);
            assertFalse("Tracker should still be in progress", tracker.isComplete());
            assertEquals(((double)i) / totalWork, tracker.getTaskProgress(), 0.0001);
        }
        tracker.setWorkDone(totalWork);
        assertTrue(tracker.isComplete());
        testProgressListener.assertReportsValid();
    }

    @Test
    public void testSubDividedTracker() {
        int totalWork = 2000;
        testProgressListener.setUpdateStep(0.05);//5%
        TaskProgressTracker overallTracker = new TaskProgressTracker("overall task", totalWork, testProgressListener);
        assertFalse(overallTracker.isComplete());
        assertEquals(2000, overallTracker.getRemainingWork());
        assertEquals("TaskProgressTracker{taskName=overall task, complete=0% (0 / 2000), subTasks=[0]}",overallTracker.toString());
        runSubTask(overallTracker,totalWork, 200);// 10% of total
        assertEquals(0, overallTracker.getActiveSubTasks());
        assertEquals("TaskProgressTracker{taskName=overall task, complete=10% (200 / 2000), subTasks=[0]}",overallTracker.toString());
        overallTracker.incrementWorkDone(200); // now 20% done
        assertEquals(0.2, overallTracker.getTaskProgress(), EPSILON);
        assertEquals("TaskProgressTracker{taskName=overall task, complete=20% (400 / 2000), subTasks=[0]}",overallTracker.toString());
        runSubTask(overallTracker,totalWork, 200);// 10% of total
        assertEquals(0.3, overallTracker.getTaskProgress(), EPSILON);
        assertEquals("TaskProgressTracker{taskName=overall task, complete=30% (600 / 2000), subTasks=[0]}",overallTracker.toString());
        runSubTask(overallTracker,totalWork, 1200);// 60% of total
        assertEquals(0.9, overallTracker.getTaskProgress(), EPSILON);
        assertEquals("TaskProgressTracker{taskName=overall task, complete=90% (1800 / 2000), subTasks=[0]}",overallTracker.toString());
        overallTracker.incrementWorkDone(overallTracker.getRemainingWork());
        assertEquals(1, overallTracker.getTaskProgress(), EPSILON);
        assertEquals("TaskProgressTracker{taskName=overall task, complete=100% (2000 / 2000), subTasks=[0]}",overallTracker.toString());
        assertTrue(overallTracker.isComplete());
        testProgressListener.assertReportsValid();
    }

    private void runSubTask(TaskProgressTracker overallTracker, int totalWork, int subTaskMainWorkUnits) {

        int subTaskWork = 20;
        double startProgress = overallTracker.getTaskProgress();
        TaskProgressTracker subTaskTracker = overallTracker.addSubTask("subtasks", subTaskWork, subTaskMainWorkUnits);
        for(int i = 0; i < subTaskWork; i++) {
            subTaskTracker.setWorkDone(i);
            assertFalse("Tracker should still be in progress", subTaskTracker.isComplete());
            assertEquals(((double)i / subTaskWork), subTaskTracker.getTaskProgress(), EPSILON);
            int completedSubUnits = (int) (subTaskTracker.getTaskProgress() * subTaskMainWorkUnits);
            double expectedOverallProgressPerc = ((double)completedSubUnits) / totalWork;
            double notifiableOverallProgress = overallTracker.getMinimumUpdatePercent();
            int incrementsOfMinNotifiablePerc = (int)Math.floor(expectedOverallProgressPerc / notifiableOverallProgress);
            double expectedReportedOverallProgress = startProgress + (notifiableOverallProgress * incrementsOfMinNotifiablePerc);
            double expectedActualOverallProgress = startProgress + expectedOverallProgressPerc;
            assertEquals("loop " + i, expectedActualOverallProgress, overallTracker.getTaskProgress(), overallTracker.getMinimumUpdatePercent());
            assertEquals("loop " + i, expectedReportedOverallProgress, testProgressListener.getReportedProgress(), overallTracker.getMinimumUpdatePercent());

            double subTaskProgress = ((double)i)/subTaskWork;
            double mainTaskProgress = Math.rint(100 * overallTracker.getTaskProgress());
            String expectedProgressToStr = String.format("TaskProgressTracker{taskName=overall task, complete=%3$d%% (%4$d / 2000), subTasks=[1]{TaskProgressTracker{taskName=subtasks, complete=%1$d%% (%2$d / 20), subTasks=[0]}}}", (int)Math.rint(subTaskProgress*100), i, (int)Math.rint(mainTaskProgress), overallTracker.getWorkDone());
            assertEquals("loop " + i, expectedProgressToStr,overallTracker.toString());
        }
        subTaskTracker.incrementWorkDone(1);
        assertTrue(subTaskTracker.isComplete());
    }

    @Test
    public void testResetWhenSubDivided() {
        TaskProgressTracker overallTracker = new TaskProgressTracker("overall task", 100, testProgressListener);
        TaskProgressTracker subTask = overallTracker.addSubTask("subTask", 20, 10, 80);
        overallTracker.reset();
        assertEquals(0, overallTracker.getWorkDone());
        assertEquals(100, overallTracker.getRemainingWork());
        assertEquals(0, overallTracker.getTaskProgress(), EPSILON);
    }

}