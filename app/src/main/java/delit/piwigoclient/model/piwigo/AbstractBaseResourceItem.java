package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;

/**
 * Created by gareth on 12/07/17.
 */
public abstract class AbstractBaseResourceItem extends GalleryItem {
    private static final String TAG = "AbstractBaseResItem";
    private float myRating = 0;
    private float averageRating = 0;
    private int ratingsGiven = 0;
    private byte privacyLevel = -1;
    private ArrayList<ResourceFile> availableFiles = new ArrayList<>();
    private HashSet<Long> linkedAlbums;
    private String fileChecksum;
    private Date creationDate;
    private float score;
    private long resourceDetailsLoadedAt;

    public AbstractBaseResourceItem(long id, String name, String description, Date creationDate, Date lastAltered, String baseResourceUrl) {
        super(id, name, description, lastAltered, baseResourceUrl);
        this.creationDate = creationDate;
    }

    public AbstractBaseResourceItem(Parcel in) {
        super(in);
        myRating = in.readFloat();
        averageRating = in.readFloat();
        ratingsGiven = in.readInt();
        privacyLevel = in.readByte();
        availableFiles = in.createTypedArrayList(ResourceFile.CREATOR);
        linkedAlbums = ParcelUtils.readLongSet(in);
        fileChecksum = in.readString();
        creationDate = ParcelUtils.readDate(in);
        score = in.readFloat();
        resourceDetailsLoadedAt = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeFloat(myRating);
        out.writeFloat(averageRating);
        out.writeInt(ratingsGiven);
        out.writeInt(privacyLevel);
        out.writeTypedList(availableFiles);
        ParcelUtils.writeLongSet(out, linkedAlbums);
        out.writeString(fileChecksum);
        ParcelUtils.writeDate(out, creationDate);
        out.writeFloat(score);
        out.writeLong(resourceDetailsLoadedAt);
    }

    @Override
    public String getThumbnailUrl() {
        return getFileUrl(ResourceFile.THUMB);
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public void setFileChecksum(String fileChecksum) {
        this.fileChecksum = fileChecksum;
    }

    public ArrayList<ResourceFile> getAvailableFiles() {
        return availableFiles;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        if (getFile(ResourceFile.THUMB) == null) {
            addResourceFile(ResourceFile.THUMB, thumbnailUrl, -1, -1);
        }
    }

    public ResourceFile getFile(String name) {
        byte wantedId = ResourceFile.getId(name);
        for (ResourceFile rf : availableFiles) {
            if (rf.id == wantedId) {
                return rf;
            }
        }
        return null;
    }

    private void addResourceFile(ResourceFile img) {
        if (availableFiles.size() > 0) {
            ResourceFile last = availableFiles.get(availableFiles.size() - 1);
            if (last.width == img.width && last.height == img.height) {
                return;
            }
        }
        availableFiles.add(img);
    }

    public ResourceFile getFullSizeFile() {
        for (ResourceFile f : availableFiles) {
            if (ResourceFile.ORIGINAL.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GalleryItem && ((GalleryItem) other).getId() == this.getId();
    }

    public String getFileExtension() {
        ResourceFile usingFile = getFullSizeFile();
        if (usingFile == null) {
            if (availableFiles.size() > 0) {
                usingFile = availableFiles.get(0);
            }
        }
        if (usingFile != null) {
            String url = usingFile.getUrl();
            int idx = url.lastIndexOf('.');
            return url.substring(idx + 1);
        }
        return null;
    }

    public HashSet<Long> getLinkedAlbums() {
        return linkedAlbums;
    }

    public void setLinkedAlbums(HashSet<Long> linkedAlbums) {
        this.linkedAlbums = linkedAlbums;
    }

    public float getMyRating() {
        return myRating;
    }

    public void setMyRating(float myRating) {
        this.myRating = myRating;
    }

    public float getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(float averageRating) {
        this.averageRating = averageRating;
    }

    public static String getDownloadFileName(String resourceName, String resourceFileUrl, ResourceFile selectedItem) {
        // calculate filename from URI
        Pattern p = Pattern.compile("^.*/([^?]*).*$");
        Matcher m = p.matcher(resourceFileUrl);
        if (!m.matches()) {
            throw new IllegalArgumentException("Filename pattern is not working for url " + resourceFileUrl);
        }
        String filenameInUrl = m.group(1);

        String ext = filenameInUrl.substring(filenameInUrl.lastIndexOf('.'));
        String filenameRoot = resourceName;
        if (filenameRoot == null) {
            filenameRoot = filenameInUrl.substring(0, filenameInUrl.lastIndexOf('.'));
        } else {
            // do this in a while loop to strip multiple extensions off due to faulty uploads!
            filenameRoot = resourceName;
            while(filenameRoot.endsWith(ext)) {
                filenameRoot = filenameRoot.substring(0, filenameRoot.lastIndexOf(ext));
            }
        }
        String filePiwigoSizeName = selectedItem.getName();
        String filesystemSafeFilenameRoot = filenameRoot.replaceAll("[:\\\\/*\"?|<>']", "_");
        int maxLen = 127 - filePiwigoSizeName.length() - ext.length();
        if (filesystemSafeFilenameRoot.length() > maxLen) {
            filesystemSafeFilenameRoot = filesystemSafeFilenameRoot.substring(0, 127);
        }
        return filesystemSafeFilenameRoot + '_' + filePiwigoSizeName + ext;
    }

    public String getDownloadFileName(ResourceFile selectedItem) {
        return getDownloadFileName(getName(), getFullPath(selectedItem.getUrl()), selectedItem);
    }

    public byte getPrivacyLevel() {
        return privacyLevel;
    }

    public int getRatingsGiven() {
        return ratingsGiven;
    }

    public void setRatingsGiven(int ratingsGiven) {
        this.ratingsGiven = ratingsGiven;
    }

    public void copyFrom(AbstractBaseResourceItem other, boolean copyParentage) {
        super.copyFrom(other, copyParentage);
        myRating = other.myRating;
        score = other.score;
        averageRating = other.averageRating;
        ratingsGiven = other.ratingsGiven;
        privacyLevel = other.privacyLevel;
        availableFiles = other.availableFiles;
        linkedAlbums = other.linkedAlbums;
        fileChecksum = other.fileChecksum;
        resourceDetailsLoadedAt = other.resourceDetailsLoadedAt;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public float getScore() {
        return score;
    }

    public void markResourceDetailUpdated() {
        resourceDetailsLoadedAt = System.currentTimeMillis();
    }

    public void setPrivacyLevel(byte privacyLevel) {
        this.privacyLevel = privacyLevel;
    }

    public String getFileUrl(String fileId) {
        ResourceFile rf = getFile(fileId);
        if (rf != null) {
            return getFullPath(rf.getUrl());
        }
        return null;
    }

    public void addResourceFile(String name, String url, int originalResourceUrlWidth, int originalResourceUrlHeight) {
        ResourceItem.ResourceFile img = new ResourceItem.ResourceFile(name, getRelativePath(url), originalResourceUrlWidth, originalResourceUrlHeight);
        if (getFile(name) != null) {
            Logging.log(Log.ERROR, TAG, "attempting to add duplicate resource file of type " + name);
        }
        addResourceFile(img);
    }

    public String getFirstSuitableUrl() {
        String uri = getThumbnailUrl();
        if (uri == null) {
            for (AbstractBaseResourceItem.ResourceFile f : getAvailableFiles()) {
                if (f != getFullSizeFile()) {
                    uri = getFileUrl(f.getName());
                    break;
                }
            }
        }
        if (uri == null) {
            uri = getFileUrl(getFullSizeFile().getName());
        }
        return uri;
    }

    public static class ResourceFile implements Comparable<ResourceFile>, Parcelable {

        private static final String TAG = "ResourceFile";
        public static final String ORIGINAL = "original";
        public static final String BEST_FIT = "best-fit";
        public static final String XXLARGE = "xxlarge";
        public static final String XLARGE = "xlarge";
        public static final String LARGE = "large";
        public static final String MEDIUM = "medium";
        public static final String SMALL = "small";
        public static final String XSMALL = "xsmall";
        public static final String SMALL1 = "2small";
        public static final String THUMB = "thumb";
        public static final String SQUARE = "square";
        public static final String OPTIMAL = "Optimal";

        private final byte id;
        private final String url;
        private final int width;
        private final int height;

        public static ResourceFile getGenericOriginalFile() {
            return new ResourceFile(ORIGINAL, null, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        public ResourceFile(String name, String url, int width, int height) {
            this.id = getId(name);
            this.url = url;
            this.width = width;
            this.height = height;
        }

        public ResourceFile(Parcel in) {
            id = in.readByte();
            url = in.readString();
            width = in.readInt();
            height = in.readInt();
        }

        private static String getName(byte id) {
            switch (id) {
                case 0:
                    return ORIGINAL;
                case 1:
                    return BEST_FIT;
                case 2:
                    return XXLARGE;
                case 3:
                    return XLARGE;
                case 4:
                    return LARGE;
                case 5:
                    return MEDIUM;
                case 6:
                    return SMALL;
                case 7:
                    return XSMALL;
                case 8:
                    return SMALL1;
                case 9:
                    return THUMB;
                case 10:
                    return SQUARE;
                default:
                    Logging.log(Log.ERROR, TAG, "Unsupported resource id encountered : " + id);
                    return "unknown";
            }
        }

        private static byte getId(String name) {
            if (name == null) {
                name = "null";
            }
            switch (name) {
                case ORIGINAL:
                    return 0;
                case BEST_FIT:
                    return 1;
                case XXLARGE:
                    return 2;
                case XLARGE:
                    return 3;
                case LARGE:
                    return 4;
                case MEDIUM:
                    return 5;
                case SMALL:
                    return 6;
                case XSMALL:
                    return 7;
                case SMALL1:
                    return 8;
                case THUMB:
                    return 9;
                case SQUARE:
                    return 10;
                case OPTIMAL:
                    return 11;
                default:
                    Logging.log(Log.ERROR, TAG, "Unsupported resource name encountered : " + name);
                    return -1;
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeByte(id);
            dest.writeString(url);
            dest.writeInt(width);
            dest.writeInt(height);
        }

        private String getUrl() {
            return url;
        }

        public int getHeight() {
            return height;
        }

        public int getWidth() {
            return width;
        }

        public String getName() {
            return getName(id);
        }

        @NonNull
        @Override
        public String toString() {
            if(width < Integer.MAX_VALUE && height < Integer.MAX_VALUE) {
                return getName(id) + " (" + width + " * " + height + ')';
            } else {
                return getName(id);
            }
        }

        @Override
        public int compareTo(@NonNull ResourceFile o) {
            if (this.width > o.width) {
                return 1;
            }
            if (this.width < o.width) {
                return -1;
            }
            return Byte.compare(this.id, o.id);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<ResourceFile> CREATOR
                = new Parcelable.Creator<ResourceFile>() {
            public ResourceFile createFromParcel(Parcel in) {
                return new ResourceFile(in);
            }

            public ResourceFile[] newArray(int size) {
                return new ResourceFile[size];
            }
        };
    }

    public boolean isResourceDetailsLikelyOutdated() {
        return isLikelyOutdated(resourceDetailsLoadedAt);
    }

    public ResourceFile getResourceFileWithUri(@NonNull String uri) {
        for(ResourceFile file : availableFiles) {
            if(uri.equals(file.getUrl())) {
                return file;
            }
        }
        throw new IllegalArgumentException("No file could be found with the uri " + uri);
    }
}
