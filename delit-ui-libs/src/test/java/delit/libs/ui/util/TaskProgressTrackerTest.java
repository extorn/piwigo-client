package delit.libs.ui.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class TaskProgressTrackerTest {

    @Test
    public void testInitialisation() {
        TaskProgressTracker tracker = spy(new TaskProgressTracker() {
            @Override
            protected void reportProgress(int newOverallProgress) {
            }
        });
        tracker.withStage(0,100, 100);
        assertEquals(0.01, tracker.getProgressPerTick(),0.0001);
        assertEquals(0.01, tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);


        tracker.withStage(20,40, 50);
        assertEquals(0.004, tracker.getProgressPerTick(),0.0001);
        assertEquals(0.002, tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);

        tracker.withStage(25,26, 50);
        assertEquals(((double)1)/100/50, tracker.getProgressPerTick(),0.0001);
        assertEquals(((double)1)/100/100, tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);
    }

    @Test
    public void onProgress() {
        TaskProgressTracker tracker = spy(new TaskProgressTracker() {
            @Override
            protected void reportProgress(int newOverallProgress) {
            }
        });
        tracker.withStage(0,100, 100);
        assertEquals(0.01, tracker.getProgressPerTick(),0.0001);
        assertEquals(0.01, tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);
        tracker.onProgress(5);
        assertEquals(5.0, tracker.getLastReportedProgress(),0.0001);
        tracker.onProgress(15);
        assertEquals(15.0, tracker.getLastReportedProgress(),0.0001);
        tracker.onProgress(100);
        assertEquals(100, tracker.getLastReportedProgress(),0.0001);


        tracker.withStage(10,40, 20);
        assertEquals(((double)30)/100/20, tracker.getProgressPerTick(),0.0001);
        assertEquals(((double)30)/100/100, tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);
        // Note, due to rounding up or down at .5 to get an int, the accuracy is +-0.5%
        tracker.onProgress(5);
        assertEquals(10 + (5 * ((double)30)/100), tracker.getLastReportedProgress(),0.5);
        tracker.onProgress(15);
        assertEquals(10 + (15 * ((double)30)/100), tracker.getLastReportedProgress(),0.5);
        tracker.onProgress(100);
        assertEquals(40, tracker.getLastReportedProgress(),0.0001);
        assertThrows(IllegalArgumentException.class, () -> tracker.onProgress(101));
        tracker.withStage(40,50, 20);
        assertEquals(((double)10)/100/20, tracker.getProgressPerTick(),0.0001);
        assertEquals(((double)10)/100/100, tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);
        tracker.onProgress(15);
    }

    @Test
    public void onTick() {
        TaskProgressTracker tracker = spy(new TaskProgressTracker() {
            @Override
            protected void reportProgress(int newOverallProgress) {
            }
        });
        tracker.withStage(0,100, 100);
        assertEquals(0.01, tracker.getProgressPerTick(),0.0001);
        assertEquals(0.01, tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);
        tracker.onTick(5);
        assertEquals(5.0, tracker.getLastReportedProgress(),0.0001);
        tracker.onTick(15);
        assertEquals(15.0, tracker.getLastReportedProgress(),0.0001);

        tracker.withStage(0,100, 20);
        assertEquals((1.0/20), tracker.getProgressPerTick(),0.0001);
        assertEquals(0.01, tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);
        tracker.onTick(5);
        assertEquals(5.0 * (100.0/20), tracker.getLastReportedProgress(),0.0001);
        tracker.onTick(15);
        assertEquals(15.0 * (100.0/20), tracker.getLastReportedProgress(),0.0001);

        tracker.withStage(10,12, 10);
        assertEquals(((2.0/100)/(100.0/10)), tracker.getProgressPerTick(),0.0001);
        assertEquals((2.0/100/100), tracker.getMainTaskProgressPerPercentOfThisTask(),0.0001);
        tracker.onTick(5);
        assertEquals(10 + (5.0 * ((2.0/100)*(100.0/10))), tracker.getLastReportedProgress(),0.0001);
        tracker.onTick(10);
        assertEquals(12, tracker.getLastReportedProgress(),0.0001);

        assertThrows(IllegalArgumentException.class, () -> tracker.onTick(15));

    }

}