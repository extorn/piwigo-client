package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * Created by gareth on 12/07/17.
 */
public abstract class AbstractBaseResourceItem extends GalleryItem {
    private float myRating = 0;
    private float averageRating = 0;
    private int ratingsGiven = 0;
    private int privacyLevel = 0;
    private ArrayList<ResourceFile> availableFiles = new ArrayList<>();
    private ResourceFile fullSizeImage;
    private HashSet<Long> linkedAlbums;
    private String fileChecksum;
    private Date creationDate;
    private float score;
    private long resourceDetailsLoadedAt;

    public AbstractBaseResourceItem(long id, String name, String description, Date creationDate, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, lastAltered, thumbnailUrl);
        this.creationDate = creationDate;
    }

    public AbstractBaseResourceItem(Parcel in) {
        super(in);
        myRating = in.readFloat();
        averageRating = in.readFloat();
        ratingsGiven = in.readInt();
        privacyLevel = in.readInt();
        availableFiles = ParcelUtils.readTypedList(in, ResourceFile.CREATOR);
        fullSizeImage = in.readParcelable(getClass().getClassLoader());
        linkedAlbums = ParcelUtils.readLongSet(in, null);
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
        out.writeParcelable(fullSizeImage, flags);
        ParcelUtils.writeLongSet(out, linkedAlbums);
        out.writeString(fileChecksum);
        ParcelUtils.writeDate(out, creationDate);
        out.writeFloat(score);
        out.writeLong(resourceDetailsLoadedAt);
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

    public ResourceFile getFile(String name) {
        for (ResourceFile rf : availableFiles) {
            if (rf.name.equals(name)) {
                return rf;
            }
        }
        return null;
    }

    public void addResourceFile(ResourceFile img) {
        if (availableFiles.size() > 0) {
            ResourceFile last = availableFiles.get(availableFiles.size() - 1);
            if (last.width == img.width && last.height == img.height) {
                return;
            }
        }
        availableFiles.add(img);
    }

    public ResourceFile getFullSizeFile() {
        return fullSizeImage;
    }

    public void setFullSizeImage(ResourceFile fullSizeImage) {
        this.fullSizeImage = fullSizeImage;
    }

    public String getFileExtension() {
        int idx = fullSizeImage.url.lastIndexOf('.');
        return fullSizeImage.url.substring(idx + 1);
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

    public String getDownloadFileName(ResourceFile selectedItem) {
        // calculate filename from URI
        Pattern p = Pattern.compile("^.*/([^?]*).*$");
        Matcher m = p.matcher(selectedItem.url);
        if (!m.matches()) {
            throw new IllegalArgumentException("Filename pattern is not working for url " + selectedItem.url);
        }
        String filenameInUrl = m.group(1);

        String ext = filenameInUrl.substring(filenameInUrl.lastIndexOf('.'));
        String filenameRoot = getName();
        if (filenameRoot == null) {
            filenameRoot = filenameInUrl.substring(0, filenameInUrl.lastIndexOf('.'));
        } else {
            if (filenameRoot.endsWith(ext)) {
                filenameRoot = getName().substring(0, getName().lastIndexOf(ext));
            }
        }
        String filesystemSafeFilenameRoot = filenameRoot.replaceAll("[:\\\\/*\"?|<>']", "_");
        int maxLen = 127 - selectedItem.name.length() - ext.length();
        if (filesystemSafeFilenameRoot.length() > maxLen) {
            filesystemSafeFilenameRoot = filesystemSafeFilenameRoot.substring(0, 127);
        }
        return filesystemSafeFilenameRoot + '_' + selectedItem.name + ext;
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

    public int getPrivacyLevel() {
        return privacyLevel;
    }

    public void setPrivacyLevel(int privacyLevel) {
        this.privacyLevel = privacyLevel;
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
        fullSizeImage = other.fullSizeImage;
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

    public static class ResourceFile implements Comparable<ResourceFile>, Parcelable, Serializable {

        private static final long serialVersionUID = 2807336261739692481L;

        private final String name;
        private final String url;
        private final int width;
        private final int height;

        public ResourceFile(String name, String url, int width, int height) {
            this.name = name;
            this.url = url;
            this.width = width;
            this.height = height;
        }

        public ResourceFile(Parcel in) {
            name = in.readString();
            url = in.readString();
            width = in.readInt();
            height = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeString(url);
            dest.writeInt(width);
            dest.writeInt(height);
        }

        public String getUrl() {
            return url;
        }

        public int getHeight() {
            return height;
        }

        public int getWidth() {
            return width;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name + " (" + width + " * " + height + ')';
        }

        @Override
        public int compareTo(@NonNull ResourceFile o) {
            if (this.width > o.width) {
                return 1;
            }
            if (this.width < o.width) {
                return -1;
            }
            return this.name.compareTo(o.name);
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
}
