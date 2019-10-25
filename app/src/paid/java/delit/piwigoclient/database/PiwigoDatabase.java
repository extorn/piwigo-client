package delit.piwigoclient.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {UploadedFile.class}, version = 1)
public abstract class PiwigoDatabase extends RoomDatabase {

    private static PiwigoDatabase instance;

    public synchronized static PiwigoDatabase getInstance(Context ctx) {
        if (instance == null) {
            instance = Room.databaseBuilder(ctx.getApplicationContext(),
                    PiwigoDatabase.class, "piwigo-database").build();
        }
        return instance;
    }

    public abstract UploadedFileDao uploadedFileDao();

}
