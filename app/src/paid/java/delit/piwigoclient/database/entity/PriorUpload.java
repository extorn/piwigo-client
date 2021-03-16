package delit.piwigoclient.database.entity;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

import static androidx.room.ForeignKey.NO_ACTION;

/**
 * If a prior upload is deleted, we cascade delete any links to the upload destination(s)
 * If a link to an upload destination is deleted, we do nothing (might be others linked still)
 */
@Entity(tableName = "PriorUpload",
        indices = {@Index(value = "uri", unique = true), @Index(value = "checksum", unique = true)}/*,
        foreignKeys = @ForeignKey(entity = UploadDestinationPriorUploadCrossRef.class,
                                  parentColumns = "priorUploadId", // the fk entity being watched
                                  childColumns = "id",// this entity
                                  onDelete = NO_ACTION)*/)
public class PriorUpload {

    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public Uri uri;
    public String parentUri;
    public String checksum;
    public Date lastUploadedAt;


    public PriorUpload(long id, @Nullable String parentUri, @NonNull Uri uri, @NonNull String checksum, @NonNull Date lastUploadedAt) {
        this.id = id;
        this.parentUri = parentUri;
        this.uri = uri;
        this.checksum = checksum;
        this.lastUploadedAt = lastUploadedAt;
    }

    @Ignore
    public PriorUpload(@Nullable String parentUri, @NonNull Uri uri, @NonNull String checksum, @NonNull Date lastUploadedAt) {
        this.parentUri = parentUri;
        this.uri = uri;
        this.checksum = checksum;
        this.lastUploadedAt = lastUploadedAt;
    }

    public boolean isMatch(PriorUpload other) {
        return uri.equals(other.uri) || checksum.equals(other.checksum);
    }

    public void copyFrom(PriorUpload other) {
        this.parentUri = other.parentUri;
        this.uri = other.uri;
        this.checksum = other.checksum;
        this.lastUploadedAt = other.lastUploadedAt;
    }
}
