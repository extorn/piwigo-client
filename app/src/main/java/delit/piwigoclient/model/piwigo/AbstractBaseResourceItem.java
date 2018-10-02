package delit.piwigoclient.model.piwigo;

import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by gareth on 12/07/17.
 */
public abstract class AbstractBaseResourceItem extends GalleryItem {
    private float yourRating = 0;
    private float averageRating = 0;
    private int ratingsGiven = 0;
    private int privacyLevel = 0;
    private ArrayList<ResourceFile> availableFiles = new ArrayList<>();
    private ResourceFile fullSizeImage;
    private HashSet<Long> linkedAlbums;
    private String fileChecksum;
    private Date creationDate;
    private float score;

    public AbstractBaseResourceItem(long id, String name, String description, Date creationDate, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, lastAltered, thumbnailUrl);
        this.creationDate = creationDate;
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

    public float getYourRating() {
        return yourRating;
    }

    public void setYourRating(float yourRating) {
        this.yourRating = yourRating;
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
        yourRating = other.yourRating;
        score = other.score;
        averageRating = other.averageRating;
        ratingsGiven = other.ratingsGiven;
        privacyLevel = other.privacyLevel;
        availableFiles = other.availableFiles;
        fullSizeImage = other.fullSizeImage;
        linkedAlbums = other.linkedAlbums;
        fileChecksum = other.fileChecksum;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public float getScore() {
        return score;
    }

    public static class ResourceFile implements Comparable<ResourceFile>, Serializable {
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
    }
}
