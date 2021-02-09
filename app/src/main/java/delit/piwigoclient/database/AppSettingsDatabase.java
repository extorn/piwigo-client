package delit.piwigoclient.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {UriPermissionUse.class}, version = 1)
public abstract class AppSettingsDatabase extends RoomDatabase {

    private static volatile AppSettingsDatabase instance;

    private static final int NUMBER_OF_THREADS = 4;
    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public synchronized static AppSettingsDatabase getInstance(Context ctx) {
        if (instance == null) {
            instance = Room.databaseBuilder(ctx.getApplicationContext(),
                    AppSettingsDatabase.class, "app-settings-database").build();
        }
        return instance;
    }

    public abstract UriPermissionUseDao uriPermissionUseDao();

}
