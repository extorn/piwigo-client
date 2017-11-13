package delit.piwigoclient.business.video;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import delit.piwigoclient.R;

/**
 * Created by gareth on 01/07/17.
 */

public class CacheUtils {

    public static File getVideoCacheFolder(Context c) throws IOException {
        File f = new File(c.getApplicationContext().getExternalCacheDir(), "videos");
        if(!f.exists() || !f.isDirectory()) {
            boolean created = f.mkdir();
            if(!created) {
                throw new IOException("Unable to configure the cache folder");
            }
        }
        return f;
    }

    public static void clearVideoCache(Context c) throws IOException {
        File cacheDir = getVideoCacheFolder(c);
        deleteQuietly(cacheDir);
//        for(File f : cacheDir.listFiles()) {
//            if(f.isDirectory()) {
//                deleteDir(f);
//            } else {
//                f.delete();
//            }
//        }
    }

    public static CachedContent loadCachedContent(File f) throws IOException {
        CachedContent cachedContent = null;
        try {
            ObjectInputStream ois = null;
            try {
                ois = new ObjectInputStream(new FileInputStream(f));
                cachedContent = (CachedContent) ois.readObject();
                cachedContent.setPersistTo(f);
            } finally {
                if(ois != null) {
                    ois.close();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        return cachedContent;
    }

    public static final FilenameFilter metadataFileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".dat");
        }
    };

    public static final Comparator<CachedContent> cacheItemAgeComparator = new Comparator<CachedContent>() {

        @Override
        public int compare(CachedContent o1, CachedContent o2) {
            Date first = o1.getLastAccessed();
            Date second = o2.getLastAccessed();
            if(first == null) {
                return -1;
            }
            if(second == null) {
                return 1;
            }
            return o1.getLastAccessed().compareTo(o2.getLastAccessed());
        }
    };

    public static void manageVideoCache(Context c, long maxCacheSizeBytes) throws IOException {
        synchronized (CacheUtils.class) {
            File cacheDir = getVideoCacheFolder(c);
            List<CachedContent> cacheContent = new ArrayList<>();
            long totalCacheSize = 0;
            for (File f : cacheDir.listFiles(metadataFileFilter)) {
                boolean loaded = false;
                int attempts = 0;
                while (!loaded) {
                    try {
                        try {
                            CachedContent content = loadCachedContent(f);
                            cacheContent.add(content);
                            totalCacheSize += content.getTotalBytes();
                        } catch(InvalidClassException e) {
                            // the cache file structure has altered - this cannot be loaded so we must delete it.
                            f.delete();
                            File dataFile = getCacheDataFile(c, f.getName().replaceAll("\\.dat$", ""));
                            dataFile.delete();
                        }
                        loaded = true;
                    } catch (IOException e) {
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

            while (totalCacheSize > maxCacheSizeBytes) {
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
        if(metadataFile.exists()) {
            boolean deleted = metadataFile.delete();
            if (deleted) {
                File dataFile = cc.getCachedDataFile();
                if(dataFile.exists()) {
                    deleted = dataFile.delete();
                }
            }
            if(!deleted) {
                // something went wrong...
                //TODO think of some thing to do in this instance...
            }
        }

    }

    public static void saveCachedContent(CachedContent cacheFileContent) throws IOException {
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(new FileOutputStream(cacheFileContent.getPersistTo()));
            oos.writeObject(cacheFileContent);
            oos.flush();
        } finally {
            if(oos != null) {
                oos.close();
            }
        }
    }

    public static void deleteCachedContent(Context ctx, String uri) throws IOException {
        String filename = getVideoFilename(uri);
        File metadataFile = getCacheMetadataFile(ctx, filename);
        if(metadataFile.exists()) {
            boolean deleted = metadataFile.delete();
            if (deleted) {
                File dataFile = getCacheDataFile(ctx, filename);
                if(dataFile.exists()) {
                    deleted = dataFile.delete();
                }
            }
            if(!deleted) {
                // something went wrong...
                //TODO think of some thing to do in this instance...
            }
        }
    }

    public static String getVideoFilename(String uri) {
        return uri.replaceAll("^.*/", "");
    }

    public static File getCacheMetadataFile(Context context, String connectedToFile) throws IOException {
        return new File(getVideoCacheFolder(context), connectedToFile + ".dat");
    }

    public static File getCacheDataFile(Context context, String connectedToFile) throws IOException {
        return new File(getVideoCacheFolder(context), connectedToFile);
    }

    public static void clearBasicCache(Context c) throws IOException {
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

    public static boolean deleteQuietly(File file) {
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
        File cacheFolder = null;
        try {
            cacheFolder = new File(context.getApplicationContext().getExternalCacheDir(), "basic-cache");
            if (!cacheFolder.exists()) {
                cacheFolder.mkdir();
            }
            if(!(cacheFolder.canRead() && cacheFolder.canWrite())) {
                //Permission has been revoked!
                throw new SecurityException(context.getString(R.string.error_insufficient_permissions_for_cache_folder));
            }
        } catch(SecurityException e) {
            //Permission has been revoked!
            throw new SecurityException(context.getString(R.string.error_insufficient_permissions_for_cache_folder));
        }
        return cacheFolder;
    }
}
