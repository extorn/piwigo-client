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
public class ResourceItem extends GalleryItem {
    private float yourRating;
    private float averageRating;
    private int ratingsGiven;
    private int privacyLevel;
    private ArrayList<ResourceFile> availableFiles = new ArrayList<>();
    private ResourceFile fullSizeImage;
    private HashSet<Long> linkedAlbums;
    private String fileChecksum;

    public ResourceItem(long id, String name, String description, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, lastAltered, thumbnailUrl);
    }

    public String getFileChecksum() {
        return fileChecksum;
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

    public void setLinkedAlbums(HashSet<Long> linkedAlbums) {
        this.linkedAlbums = linkedAlbums;
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

    @Override
    public int hashCode() {
        int hashcode = super.hashCode();
        return hashcode;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GalleryItem)) {
            return false;
        }
        return ((GalleryItem) other).getId() == this.getId();
    }

    public String getDownloadFileName(ResourceFile selectedItem) {
        // calculate filename from URI
        Pattern p = Pattern.compile("^.*/([^?]*).*$");
        Matcher m = p.matcher(selectedItem.url);
        if(!m.matches()) {
            throw new IllegalArgumentException("Filename pattern is not working for url " + selectedItem.url);
        }
        String filenameInUrl = m.group(1);

        String ext = filenameInUrl.substring(filenameInUrl.lastIndexOf('.'));
        String filenameRoot = getName();
        if(filenameRoot == null) {
            filenameRoot = filenameInUrl.substring(0, filenameInUrl.lastIndexOf('.'));
        } else {
            if (filenameRoot.endsWith(ext)) {
                filenameRoot = getName().substring(0, getName().lastIndexOf(ext));
            }
        }
        String filesystemSafeFilenameRoot = filenameRoot.replaceAll("[:\\\\/*\"?|<>']", "_");
        int maxLen = 127 - selectedItem.name.length() - ext.length();
        if(filesystemSafeFilenameRoot.length() > maxLen) {
            filesystemSafeFilenameRoot = filesystemSafeFilenameRoot.substring(0, 127);
        }
        return filesystemSafeFilenameRoot + '_' + selectedItem.name + ext;
    }

    public HashSet<Long> getLinkedAlbums() {
        return linkedAlbums;
    }

    public void setPrivacyLevel(int privacyLevel) {
        this.privacyLevel = privacyLevel;
    }

    public void setAverageRating(float averageRating) {
        this.averageRating = averageRating;
    }

    public void setYourRating(float yourRating) {
        this.yourRating = yourRating;
    }

    public float getYourRating() {
        return yourRating;
    }

    public float getAverageRating() {
        return averageRating;
    }

    public int getPrivacyLevel() {
        return privacyLevel;
    }

    public int getRatingsGiven() {
        return ratingsGiven;
    }

    public void setRatingsGiven(int ratingsGiven) {
        this.ratingsGiven = ratingsGiven;
    }

    public void setFileChecksum(String fileChecksum) {
        this.fileChecksum = fileChecksum;
    }

    public void copyFrom(ResourceItem other, boolean copyParentage) {
        super.copyFrom(other, copyParentage);
        yourRating = other.yourRating;
        averageRating = other.averageRating;
        ratingsGiven = other.ratingsGiven;
        privacyLevel = other.privacyLevel;
        availableFiles = other.availableFiles;
        fullSizeImage = other.fullSizeImage;
        linkedAlbums = other.linkedAlbums;
        fileChecksum = other.fileChecksum;
    }

    public static class ResourceFile implements Comparable<ResourceFile>, Serializable {
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
