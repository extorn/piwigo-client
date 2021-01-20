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
        TaskProgressTracker tracker = new TaskProgressTracker(totalWork, testProgressListener);
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
        TaskProgressTracker overallTracker = new TaskProgressTracker(totalWork, testProgressListener);
        assertFalse(overallTracker.isComplete());
        assertEquals(2000, overallTracker.getRemainingWork());
        runSubTask(overallTracker,totalWork, 200);// 10% of total
        assertEquals(0, overallTracker.getActiveSubTasks());
        overallTracker.incrementWorkDone(200); // now 20% done
        assertEquals(0.2, overallTracker.getTaskProgress(), EPSILON);
        runSubTask(overallTracker,totalWork, 200);// 10% of total
        assertEquals(0.3, overallTracker.getTaskProgress(), EPSILON);
        runSubTask(overallTracker,totalWork, 1200);// 60% of total
        assertEquals(0.9, overallTracker.getTaskProgress(), EPSILON);
        overallTracker.incrementWorkDone(overallTracker.getRemainingWork());
        assertEquals(1, overallTracker.getTaskProgress(), EPSILON);
        assertTrue(overallTracker.isComplete());
        testProgressListener.assertReportsValid();
    }

    private void runSubTask(TaskProgressTracker overallTracker, int totalWork, int subTaskMainWorkUnits) {

        int subTaskWork = 20;
        double startProgress = overallTracker.getTaskProgress();
        TaskProgressTracker subTaskTracker = overallTracker.addSubTask(subTaskWork, subTaskMainWorkUnits);
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
        }
        subTaskTracker.incrementWorkDone(1);
        assertTrue(subTaskTracker.isComplete());
    }

}