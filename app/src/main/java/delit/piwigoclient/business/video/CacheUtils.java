package delit.piwigoclient.business.video;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.util.Base64Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.util.LegacyIOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.HttpClientFactory;

/**
 * Created by gareth on 01/07/17.
 */

public class CacheUtils {

    private static final String TAG = "CacheUtils";
    private static final FilenameFilter metadataFileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".dat");
        }
    };
    private static final Comparator<CachedContent> cacheItemAgeComparator = new Comparator<CachedContent>() {

        @Override
        public int compare(CachedContent o1, CachedContent o2) {
            Date first = o1.getLastAccessed();
            Date second = o2.getLastAccessed();
            if (first == null) {
                return -1;
            }
            if (second == null) {
                return 1;
            }
            return o1.getLastAccessed().compareTo(o2.getLastAccessed());
        }
    };

    private static File getVideoCacheFolder(Context c) throws IOException {
        File f = new File(c.getExternalCacheDir(), "videos");
        if (!f.exists() || !f.isDirectory()) {
            boolean created = f.mkdir();
            if (!created) {
                throw new IOException("Unable to configure the cache folder");
            }
        }
        return f;
    }

    public static void clearResponseCache(Context c) {
        // do an incremental clean
        HttpClientFactory.getInstance(c).clearCache();
        // now delete the theoretically empty folder.
        File cacheDir = getBasicCacheFolder(c);
        deleteQuietly(cacheDir);
    }

    public static void clearVideoCache(Context c) throws IOException {
        File cacheDir = getVideoCacheFolder(c);
        if (deleteQuietly(cacheDir)) {
            cacheDir.mkdir();
        } else {
            Logging.log(Log.ERROR, TAG, "Error deleting video cache. Delete failed");
        }
//        for(File f : cacheDir.listFiles()) {
//            if(f.isDirectory()) {
//                deleteDir(f);
//            } else {
//                f.delete();
//            }
//        }
    }

    public static CachedContent loadCachedContent(File f) {
        CachedContent cachedContent = LegacyIOUtils.readObjectFromFile(f);
        if (cachedContent != null) {
            cachedContent.setPersistTo(f);
        } else {
            if (f.exists()) {
                if (!f.delete()) {
                    Logging.log(Log.ERROR, TAG, "Error deleting corrupt file : " + f.getAbsolutePath());
                }
            }
            Logging.log(Log.ERROR, TAG, "Unable to load cached content from file " + f);
        }
        return cachedContent;
    }

    public static void manageVideoCache(Context c, long maxCacheSizeBytesVar) throws IOException {
        long maxCacheSizeBytes = Math.max(0, maxCacheSizeBytesVar);
        synchronized (CacheUtils.class) {
            File cacheDir = getVideoCacheFolder(c);
            List<CachedContent> cacheContent = new ArrayList<>();
            long totalCacheSize = 0;
            for (File cacheMetadataFile : cacheDir.listFiles(metadataFileFilter)) {
                boolean loaded = false;
                int attempts = 0;
                while (!loaded) {
                    try {
                        CachedContent content = loadCachedContent(cacheMetadataFile);
                        if (content != null) {
                            cacheContent.add(content);
                            totalCacheSize += content.getTotalBytes();
                        } else {
                            // need to delete the data file too
                            File dataFile = getCacheDataFile(c, cacheMetadataFile.getName().replaceAll("\\.dat$", ""));
                            boolean deleted = dataFile.delete();
                        }
                        loaded = true;
                    } catch (IOException e) {
                        Logging.recordException(e);
                        // occurs when file is in use
                        attempts++;
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException e1) {
                            // do nothing.
                        }
                        if (attempts > 10) {
                            throw e;
                        }
                    }
                }
            }

            Collections.sort(cacheContent, cacheItemAgeComparator);

            while (totalCacheSize > maxCacheSizeBytes && !cacheContent.isEmpty()) {
                // delete oldest items until within wanted cache size again.
                CachedContent cc = cacheContent.get(0);
                totalCacheSize -= cc.getTotalBytes();
                deleteCacheItem(cc);
                cacheContent.remove(0);
            }
        }

    }

    private static void deleteCacheItem(CachedContent cc) {
        File metadataFile = cc.getPersistTo();
        if (metadataFile.exists()) {
            boolean deleted = metadataFile.delete();
            if (deleted) {
                File dataFile = cc.getCachedDataFile();
                if (dataFile.exists()) {
                    deleted = dataFile.delete();
                }
            }
            if (!deleted) {
                // something went wrong...
                //TODO think of some thing to do in this instance...
                Logging.log(Log.ERROR, "VideoCacheUtils", "Error, Unable to delete cache item");
            }
        }

    }

    public static void saveCachedContent(CachedContent cacheFileContent) throws IOException {

        if (!cacheFileContent.getPersistTo().exists()) {
            boolean created = cacheFileContent.getPersistTo().createNewFile();
            if(!created) {
                Logging.log(Log.ERROR, TAG, "Tried to create cache file, but it already exists - threading issue?");
            }
        }
        try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFileContent.getPersistTo()))) {
            oos.writeObject(cacheFileContent);
            oos.flush();
        }
    }

    public static void deleteCachedContent(Context ctx, String uri) throws IOException {
        String filenameStem = getVideoCacheFilenameStemFromVideoUri(uri);
        File metadataFile = getCacheMetadataFile(ctx, filenameStem);
        if (metadataFile.exists()) {
            boolean deleted = metadataFile.delete();
            if (deleted) {
                File dataFile = getCacheDataFile(ctx, filenameStem);
                if (dataFile.exists()) {
                    deleted = dataFile.delete();
                }
            }
            if (!deleted) {
                // something went wrong...
                //TODO think of some thing to do in this instance...
            }
        }
    }

    public static String getVideoCacheFilenameStemFromVideoUri(String uri) {
        return Base64Utils.encodeUrlSafe(uri.getBytes());
//        return uri.replaceAll("^.*/", "");
    }

    public static File getCacheMetadataFile(Context context, String connectedToFile) throws IOException {
        return new File(getVideoCacheFolder(context), connectedToFile + ".dat");
    }

    public static File getCacheDataFile(Context context, String connectedToFile) throws IOException {
        return new File(getVideoCacheFolder(context), connectedToFile);
    }

    public static void clearBasicCache(Context c) {
        File cacheDir = getBasicCacheFolder(c);
        deleteQuietly(cacheDir);
//        for(File f : cacheDir.listFiles()) {
//            if(f.isDirectory()) {
//                deleteDir(f);
//            } else {
//                f.delete();
//            }
//        }
    }

//    private static void deleteDir(File file) {
//        File[] contents = file.listFiles();
//        if (contents != null) {
//            for (File f : contents) {
//                deleteDir(f);
//            }
//        }
//        file.delete();
//    }

    private static boolean deleteQuietly(File file) {
        if (file == null || !file.exists())
            return true;
        if (!file.isDirectory())
            return file.delete();
        LinkedList<File> dirs = new LinkedList<>();
        dirs.add(0, file);
        boolean succeededDeletion = true;
        while (!dirs.isEmpty()) {
            file = dirs.remove(0);
            File[] children = file.listFiles();
            if (children == null || children.length == 0)
                succeededDeletion &= file.delete();
            else {
                dirs.add(0, file);
                for (File child : children)
                    if (child.isDirectory())
                        dirs.add(0, child);
                    else
                        succeededDeletion &= child.delete();
            }
        }
        return succeededDeletion;
    }

    public static File getBasicCacheFolder(Context context) {
        File cacheFolder;
        try {
            cacheFolder = new File(context.getExternalCacheDir(), "basic-cache");
            boolean created = cacheFolder.exists();
            if (!created) {
                created = cacheFolder.mkdir();
            }
            if (!(created && cacheFolder.canRead() && cacheFolder.canWrite())) {
                //Permission has been revoked!
                throw new SecurityException(context.getString(R.string.error_insufficient_permissions_for_cache_folder));
            }
        } catch (SecurityException e) {
            Logging.recordException(e);
            //Permission has been revoked!
            throw new SecurityException(context.getString(R.string.error_insufficient_permissions_for_cache_folder));
        }
        return cacheFolder;
    }

    public static long getResponseCacheSize(Context context) {
        try {
            File responseCacheFolder = getBasicCacheFolder(context);
            return folderSize(responseCacheFolder);
        } catch (SecurityException e) {
            Logging.recordException(e);
            return 0;
        }
    }

    public static long getVideoCacheSize(Context context) {
        try {
            File videoCacheFolder = getVideoCacheFolder(context);
            return folderSize(videoCacheFolder);
        } catch (IOException e) {
            Logging.recordException(e);
            return 0;
        }
    }

    private static long folderSize(File directory) {
        long length = 0;
        File[] fileList = directory.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile())
                    length += file.length();
                else
                    length += folderSize(file);
            }
        }
        return length;
    }

    public static long getItemsInResponseCache(Context context) {
        return HttpClientFactory.getInstance(context).getItemsInResponseCache();
    }
}
