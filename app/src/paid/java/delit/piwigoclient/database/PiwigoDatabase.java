package delit.piwigoclient.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {PriorUpload.class, UploadDestination.class, UploadDestinationPriorUploadCrossRef.class}, version = 2)
public abstract class PiwigoDatabase extends RoomDatabase {

    private static PiwigoDatabase instance;

    public synchronized static PiwigoDatabase getInstance(Context ctx) {
        if (instance == null) {
            instance = Room.databaseBuilder(ctx.getApplicationContext(),
                    PiwigoDatabase.class, "piwigo-database")
                    //.fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_1_2).build();
        }
        return instance;
    }


    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // we deleted the old entity and replaced it.
            // Remove the old table
            database.execSQL("DROP TABLE UploadedFile");
        }
    };


    public abstract PriorUploadDao priorUploadDao();

}
