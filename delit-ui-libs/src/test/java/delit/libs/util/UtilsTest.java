package delit.libs.util;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import delit.libs.core.util.Logging;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class UtilsTest {

    private static class A extends A1 {
        private static String TAG = "A";
    }
    private static class A1 {

    }

    private static class B extends B1 {
        private static String TAG = "B";
    }

    private static class B1 {
        private static String TAG = "B1";
    }
    private static class C extends C1 {
    }

    private static class C1 {
        private static String TAG = "C1";
    }

    private static class D extends D1 {
    }

    private static class D1 {
    }

    @Test
    public void getIdHasTagSuperDoesnt() {
        try (MockedStatic<Logging> loggingMockedStatic = mockStatic(Logging.class)) {
            assertEquals("A(class delit.libs.util.UtilsTest$A)", Utils.getId(new A()));
            loggingMockedStatic.verify(times(1), () -> Logging.logAnalyticEventIfPossible(anyString(), any(Bundle.class)));
        }
    }
    @Test
    public void getIdHasTagSuperDoes() {
        try (MockedStatic<Logging> loggingMockedStatic = mockStatic(Logging.class)) {
            assertEquals("B(class delit.libs.util.UtilsTest$B)", Utils.getId(new B()));
            loggingMockedStatic.verify(times(1), () -> Logging.logAnalyticEventIfPossible(anyString(), any(Bundle.class)));
        }
    }
    @Test
    public void getIdSuperHasTag() {
        try (MockedStatic<Logging> loggingMockedStatic = mockStatic(Logging.class)) {
            assertEquals("C1(class delit.libs.util.UtilsTest$C)", Utils.getId(new C()));
            loggingMockedStatic.verify(times(1), () -> Logging.logAnalyticEventIfPossible(anyString(), any(Bundle.class)));
        }
    }
    @Test
    public void getIdNoTagInHeirachy() {
        assertEquals("class delit.libs.util.UtilsTest$D", Utils.getId(new D()));
    }
    @Test
    public void getIdNull() {
        assertEquals("?", Utils.getId(null));
    }
}