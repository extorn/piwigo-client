package delit.piwigoclient.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import delit.piwigoclient.database.dao.PriorUploadDao;
import delit.piwigoclient.database.dao.UploadDestinationDao;
import delit.piwigoclient.database.entity.PriorUpload;
import delit.piwigoclient.database.entity.UploadDestination;
import delit.piwigoclient.database.entity.UploadDestinationPriorUploadCrossRef;

@Database(entities = {PriorUpload.class, UploadDestination.class, UploadDestinationPriorUploadCrossRef.class}, version = 3)
@TypeConverters({Converters.class})
public abstract class PiwigoUploadsDatabase extends RoomDatabase {

    private static PiwigoUploadsDatabase instance;
    private static final int NUMBER_OF_THREADS = 4;
    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public synchronized static PiwigoUploadsDatabase getInstance(Context ctx) {
        if (instance == null) {
            instance = Room.databaseBuilder(ctx.getApplicationContext(),
                    PiwigoUploadsDatabase.class, "piwigo-database")
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_1_2)
//                    .addMigrations(MIGRATION_2_3)
                    .build();
        }
        return instance;
    }


    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // we deleted the old entity and replaced it.
            // Remove the old table
            database.execSQL("DROP TABLE IF EXISTS UploadedFile");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2,3) {
        // This didn't work. Likely needed to recreate everything after.
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // No data in the database yet for users
            // Remove the old tables
            database.beginTransaction();
            database.execSQL("DROP TABLE IF EXISTS PriorUpload");
            database.execSQL("DROP TABLE IF EXISTS UploadDestination");
            database.execSQL("DROP TABLE IF EXISTS UploadDestinationPriorUploadCrossRef");
            database.endTransaction();
        }
    };


    public abstract PriorUploadDao priorUploadDao();

    public abstract UploadDestinationDao uploadDestinationWithPriorUploadsDao();
}
