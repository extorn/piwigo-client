package delit.libs.ui.util;

import androidx.annotation.FloatRange;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.util.progress.BasicProgressTracker;
import delit.libs.util.progress.SimpleProgressListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BasicProgressTrackerTest {
    private static MockedStatic<Logging> mockLogging;
    public static final double EPSILON = 0.0001;

    @BeforeClass
    public static void beforeClass() {
        mockLogging = Mockito.mockStatic(Logging.class);
    }

    @AfterClass
    public static void afterClass() {
        mockLogging.close();
    }

    protected class TestProgressListener extends SimpleProgressListener {
        private List<Double> reports = new ArrayList<>();
        private double lastProgressValue = -1;

        public TestProgressListener(double notifyOnProgress) {
            super(notifyOnProgress);
        }


        @Override
        public void onProgress(@FloatRange(from = 0, to = 1) double percent, boolean forceNotification) {
            super.onProgress(percent, forceNotification);
            lastProgressValue = percent;
        }

        @Override
        protected void onNotifiableProgress(double percent) {
            super.onNotifiableProgress(percent);
            reports.add(percent);
        }

        public double getReportedProgress() {
            return reports.isEmpty() ? 0 : reports.get(reports.size()-1);
            //return lastProgressValue;
        }

        public List<Double> getReports() {
            return reports;
        }

        public void assertReportsValid(double expectedProgress) {
            double lastReport = Double.MIN_VALUE;
            double updateStep = getMinimumProgressToNotifyFor();
            for (int i = 0; i < reports.size() -1; i++) {
                Double report = reports.get(i);
                double fudge = 1.1;
                assertTrue(i+ " progress reports should be spaced by at least " + updateStep, lastReport + updateStep <= (report* fudge));
                lastReport = report;
            }
            assertEquals(expectedProgress, reports.get(reports.size() -1), 0.0001);
        }
    }


    @Test
    public void testBasicTracker() {
        int totalWork = 20;
        TestProgressListener testProgressListener = new TestProgressListener(0.05);
        BasicProgressTracker tracker = new BasicProgressTracker("task", totalWork, testProgressListener);
        assertFalse(tracker.isComplete());
        for(int i = 0; i < totalWork; i++) {
            tracker.setWorkDone(i);
            assertFalse("Tracker should still be in progress", tracker.isComplete());
            assertEquals(((double)i) / totalWork, tracker.getProgressPercentage(), 0.0001);
        }
        tracker.setWorkDone(totalWork);
        assertTrue(tracker.isComplete());
        testProgressListener.assertReportsValid(1.0);
    }

    @Test
    public void testProgressUpdates() {
        int totalWork = 100;
        TestProgressListener testProgressListener = new TestProgressListener(0);
        BasicProgressTracker overallTracker = new BasicProgressTracker("overall task", totalWork, testProgressListener);
        for(int i = 0; i < totalWork; i++) {
            overallTracker.incrementWorkDone(1);
            int expect = i + 1;
            if(totalWork - 1 == i) {
                expect++;
            }
            assertEquals(expect,testProgressListener.reports.size());
        }
    }

}