package delit.libs.ui.util;

import org.junit.Test;

import delit.libs.util.progress.DividableProgressTracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DividableProgressTrackerTest extends BasicProgressTrackerTest {

    @Test
    public void testSubDividedTracker() {
        int totalWork = 2000;
        TestProgressListener testProgressListener = new TestProgressListener(0.05);
        DividableProgressTracker overallTracker = new DividableProgressTracker("overall task", totalWork, testProgressListener);
        overallTracker.setAlwaysNotifyOnChildComplete(true);
        assertFalse(overallTracker.isComplete());
        assertEquals(2000, overallTracker.getRemainingWork());
        assertEquals("DividableProgressTracker{taskName=overall task, complete=0% (0 / 2000) allocatedWork:[0], activeChildren=[0]}",overallTracker.toString());
        runSubTask(overallTracker,totalWork, 200, testProgressListener);// 10% of total
        assertEquals(0, overallTracker.getActiveChildCount());
        assertEquals("DividableProgressTracker{taskName=overall task, complete=10% (200 / 2000) allocatedWork:[0], activeChildren=[0]}",overallTracker.toString());
        overallTracker.incrementWorkDone(200); // now 20% done
        assertEquals(0.2, overallTracker.getProgressPercentage(), EPSILON);
        assertEquals("DividableProgressTracker{taskName=overall task, complete=20% (400 / 2000) allocatedWork:[0], activeChildren=[0]}",overallTracker.toString());
        runSubTask(overallTracker,totalWork, 200, testProgressListener);// 10% of total
        assertEquals(0.3, overallTracker.getProgressPercentage(), EPSILON);
        assertEquals("DividableProgressTracker{taskName=overall task, complete=30% (600 / 2000) allocatedWork:[0], activeChildren=[0]}",overallTracker.toString());
        runSubTask(overallTracker,totalWork, 1200, testProgressListener);// 60% of total
        assertEquals(0.9, overallTracker.getProgressPercentage(), EPSILON);
        assertEquals("DividableProgressTracker{taskName=overall task, complete=90% (1800 / 2000) allocatedWork:[0], activeChildren=[0]}",overallTracker.toString());
        overallTracker.incrementWorkDone(overallTracker.getRemainingWork());
        assertEquals(1, overallTracker.getProgressPercentage(), EPSILON);
        assertEquals("DividableProgressTracker{taskName=overall task, complete=100% (2000 / 2000) allocatedWork:[0], activeChildren=[0]}",overallTracker.toString());
        assertTrue(overallTracker.isComplete());
        testProgressListener.assertReportsValid(1.0);
    }

    private void runSubTask(DividableProgressTracker overallTracker, int totalWork, int subTaskMainWorkUnits, TestProgressListener overallListener) {

        int subTaskWork = 20;
        double startProgress = overallTracker.getProgressPercentage();
        DividableProgressTracker subTaskTracker = overallTracker.addChildTask("subtasks", subTaskWork, subTaskMainWorkUnits);
        for(int i = 0; i < subTaskWork; i++) {
            subTaskTracker.setWorkDone(i);
            assertFalse("Tracker should still be in progress", subTaskTracker.isComplete());
            assertEquals(((double)i / subTaskWork), subTaskTracker.getProgressPercentage(), EPSILON);
            int completedSubUnits = (int) (subTaskTracker.getProgressPercentage() * subTaskMainWorkUnits);
            double expectedOverallProgressPerc = ((double)completedSubUnits) / totalWork;
            double notifiableOverallProgress = 1;
            int incrementsOfMinNotifiablePerc = (int)Math.floor(expectedOverallProgressPerc / notifiableOverallProgress);
            double expectedReportedOverallProgress = startProgress + (notifiableOverallProgress * incrementsOfMinNotifiablePerc);
            double expectedActualOverallProgress = startProgress + expectedOverallProgressPerc;
            assertEquals("loop " + i, expectedActualOverallProgress, overallTracker.getProgressPercentage(), notifiableOverallProgress);
            assertEquals("loop " + i, expectedReportedOverallProgress, overallListener.getReportedProgress(), notifiableOverallProgress);

            double subTaskProgress = ((double)i)/subTaskWork;
            double mainTaskProgress = Math.rint(100 * overallTracker.getProgressPercentage());
            String expectedProgressToStr = String.format("DividableProgressTracker{taskName=overall task, complete=%3$d%% (%4$d / 2000) allocatedWork:[%5$d], activeChildren=[1]{DividableProgressTracker{taskName=subtasks, complete=%1$d%% (%2$d / 20) allocatedWork:[0], activeChildren=[0]}}}", (int)Math.rint(subTaskProgress*100), i, (int)Math.rint(mainTaskProgress), overallTracker.getWorkComplete(),subTaskMainWorkUnits);
            assertEquals("loop " + i, expectedProgressToStr,overallTracker.toString());
        }
        subTaskTracker.incrementWorkDone(1);
        assertTrue(subTaskTracker.isComplete());
    }

    @Test
    public void testResetWhenSubDivided() {

        TestProgressListener testProgressListener = new TestProgressListener(0.05);
        DividableProgressTracker overallTracker = new DividableProgressTracker("overall task", 100, testProgressListener);
        DividableProgressTracker subTask = overallTracker.addChildTask("subTask", 20, 80);
        overallTracker.rollbackProgress();
        assertEquals(0, overallTracker.getWorkComplete());
        assertEquals(100, overallTracker.getRemainingWork());
        assertEquals(0, overallTracker.getProgressPercentage(), EPSILON);
    }

    @Test
    public void testProgressUpdatesWhenSubDivided() {
        int totalWork = 100;
        int mainTaskUnitsInSubTask = 80;
        int subTaskTotalWork = 1000;
        TestProgressListener testProgressListener = new TestProgressListener(0.0095);
        DividableProgressTracker overallTracker = new DividableProgressTracker("overall task", totalWork, testProgressListener);
        overallTracker.setAlwaysNotifyOnChildComplete(true);
        DividableProgressTracker subTask = overallTracker.addChildTask("subTask", subTaskTotalWork, mainTaskUnitsInSubTask);
        while(subTask.getRemainingWork() > 0) {
            subTask.incrementWorkDone(1);
        }
        testProgressListener.assertReportsValid(0.8);

        assertTrue(mainTaskUnitsInSubTask < testProgressListener.getReports().size());

        for(int i = 0; i < totalWork; i++) {
            overallTracker.incrementWorkDone(1);
            assertTrue(Math.round(overallTracker.getProgressPercentage() * 100) < testProgressListener.getReports().size());
        }
    }

    @Test
    public void testMultiSubDividedTracker() {
        TestProgressListener testProgressListener = new TestProgressListener(0.05);
        DividableProgressTracker overallTracker = new DividableProgressTracker("real overall task", 100, testProgressListener);
        DividableProgressTracker subTask = overallTracker.addChildTask("overall task", 2000, 100);
        runSubTask(subTask, 20, 10, testProgressListener);
    }

}