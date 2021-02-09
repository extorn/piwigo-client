package delit.piwigoclient.test;

import java.util.Calendar;
import java.util.Date;

public class IdentifiableItemFactory {
    protected Calendar createDateCalendar = buildCalendar(2000,01,01);
    protected Calendar alterDateCalendar = buildCalendar(2000,01,01);
    protected static long nextItemId = 1;
    private String type;

    public static void resetId() {
        nextItemId = 1;
    }

    public IdentifiableItemFactory(String type) {
        this.type = type;
    }

    protected Calendar buildCalendar(int year, int month, int date) {
        Calendar c = Calendar.getInstance();
        c.set(year, month,date);
        return c;
    }

    protected String incrementAndGetName() {
        return String.format(type + "_%1$05d", nextItemId);
    }

    protected Date getAndIncrementAndDate(Calendar c) {
        Date time = c.getTime();
        c.set(Calendar.DATE, c.get(Calendar.DATE) + 1);
        return time;
    }
}
