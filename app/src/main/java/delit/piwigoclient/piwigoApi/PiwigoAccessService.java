package delit.piwigoclient.piwigoApi;

import android.app.IntentService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumAddPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumRemovePermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumSetStatusResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupAddMembersResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupPermissionsAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupPermissionsRemovedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupRemoveMembersResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GroupsGetListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageAlterRatingResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageCopyToAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToFileHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageSetLinkedAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesGetResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LogoutResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserPermissionsAddResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserPermissionsRemovedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UserUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsersGetListResponseHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.ui.MyApplication;
import delit.piwigoclient.ui.preferences.SecurePrefsUtil;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class PiwigoAccessService {

    private PiwigoAccessService(){}

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     */
    public static final ThreadPoolExecutor HTTP_THREAD_POOL_EXECUTOR;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<>(128);
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
        }
    };
    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        HTTP_THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }



    public static abstract class Worker extends AsyncTask<Long, Integer, Boolean> {

        private final Context context;

        private final String DEFAULT_TAG = "PwgAccessSvcAsyncTask";

        private String tag = DEFAULT_TAG;

        public Worker(Context context) {
            this.context = context;
        }

        public Context getContext() {
            return context;
        }

        public void beforeCall() {}

        private void updatePoolSize(AbstractPiwigoDirectResponseHandler handler) {
            //Update the max pool size.
            try {
                CachingAsyncHttpClient client = handler.getHttpClientFactory().getAsyncHttpClient();
                if(client != null) {
                    int newMaxPoolSize = client.getMaxConcurrentConnections();
                    HTTP_THREAD_POOL_EXECUTOR.setCorePoolSize(Math.min(newMaxPoolSize, Math.max(3, newMaxPoolSize / 2)));
                    HTTP_THREAD_POOL_EXECUTOR.setMaximumPoolSize(newMaxPoolSize);
                }
            } catch(RuntimeException e) {
                handler.sendFailureMessage(-1, null, null, new IllegalStateException(MyApplication.getInstance().getString(R.string.error_building_http_engine), e));
            }

        }

        public void afterCall(boolean success) {}

        @Override
        protected final Boolean doInBackground(Long... params) {
            try {
                if (params.length != 1) {
                    throw new IllegalArgumentException("Exactly one parameter must be passed - the id for this call");
                }
                long messageId = params[0];
                return executeCall(messageId);
            } catch(RuntimeException e) {
                if(BuildConfig.DEBUG) {
                    Log.e(tag, "ASync code crashed unexpectedly", e);
                }
                return false;
            }
        }

        protected boolean executeCall(long messageId) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String piwigoServerUrl = prefs.getString(context.getString(R.string.preference_piwigo_server_address_key), null);
            AbstractPiwigoDirectResponseHandler handler = buildHandler(prefs);
            if(handler != null) {
                this.tag = handler.getTag();
            }
            handler.setMessageId(messageId);
            handler.setCallDetails(context, piwigoServerUrl, true);

            beforeCall();
            updatePoolSize(handler);
            handler.runCall();

            long callTimeoutAtTime = System.currentTimeMillis() + 300000;
            boolean callCancelled = false;
            while (handler.isRunning() && System.currentTimeMillis() <= callTimeoutAtTime) {
                if (isCancelled()) {
                    handler.cancelCallAsap();
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    handler.cancelCallAsap();
                }
            }
            if (System.currentTimeMillis() > callTimeoutAtTime) {
                // Kill the http call immediately if still in progress
                handler.cancelCallAsap();
                if(BuildConfig.DEBUG) {
                    Log.e(handler.getTag(), "Timeout while waiting for handler to finish running");
                }
            }
            afterCall(handler.isSuccess());

            return handler.isSuccess();
        }

        protected abstract AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs);

        public long start(long messageId) {
            AsyncTask<Long, Integer, Boolean> task = executeOnExecutor(HTTP_THREAD_POOL_EXECUTOR, messageId);
            //TODO collect a list of tasks and kill them all if the app exits.
            return messageId;
        }
    }

//    /**
//     * Starts this service to perform action Foo with the given parameters. If
//     * the service is already performing a task this action will be queued.
//     *
//     * @see IntentService
//     */
//    public static long startActionGetSessionDetails(Context context) {
//        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
//        return new Worker(context) {
//
//            @Override
//            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
//                return new GetSessionStatusResponseHandler();
//            }
//        }.start(messageId);
//    }

    public static long startActionCleanupHttpConnections(final Context context) {
        final long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {
            @Override
            protected boolean executeCall(long messageId) {
                HttpClientFactory.getInstance(getContext()).clearCachedClients();
                PiwigoResponseBufferingHandler.getDefault().processResponse(new PiwigoResponseBufferingHandler.HttpClientsShutdownResponse(messageId));
                return true;
            }

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return null;
            }
        }.start(messageId);
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static long startActionLogin(final Context context, final String password) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);
                String username = prefUtil.readSecureStringPreference(prefs, context.getString(R.string.preference_piwigo_server_username_key), null);
                return new LoginResponseHandler(username, password);
            }
        }.start(messageId);
    }

    public static long startActionLogin(final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);
        String password = prefUtil.readSecureStringPreference(prefs, context.getString(R.string.preference_piwigo_server_password_key), null);
        return startActionLogin(context, password);
    }

    //TODO this seems totally superfluous to requirements.
    public static long startActionSetGalleryStatus(final PiwigoGalleryDetails gallery, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumSetStatusResponseHandler(gallery);
            }
        }.start(messageId);
    }

    public static long startActionGetAllAlbumPermissionsForGroup(long groupId, final Context context) {
        HashSet<Long> groupIds = new HashSet<>(1);
        groupIds.add(groupId);
        return startActionGetAllAlbumPermissionsForGroups(groupIds, context);
    }

    public static long startActionGetAllAlbumPermissionsForGroups(final HashSet<Long> groupIds, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupGetPermissionsResponseHandler(groupIds);
            }
        }.start(messageId);
    }

    public static long startActionGetAllAlbumPermissionsForUser(final long userId, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UserGetPermissionsResponseHandler(userId);
            }
        }.start(messageId);
    }

    public static long startActionGetAlbumPermissions(final CategoryItem gallery, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumGetPermissionsResponseHandler(gallery);
            }
        }.start(messageId);
    }

    public static long startActionAddAlbumPermissions(final PiwigoGalleryDetails gallery, final HashSet<Long> groupIds, final HashSet<Long> userIds, final boolean recursive, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumAddPermissionsResponseHandler(gallery, groupIds, userIds, recursive);
            }
        }.start(messageId);
    }

    public static long startActionAddAlbumPermissions(final CategoryItem gallery, final HashSet<Long> groupIds, final HashSet<Long> userIds, final boolean recursive, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumAddPermissionsResponseHandler(gallery, groupIds, userIds, recursive);
            }
        }.start(messageId);
    }

    public static long startActionRemoveAlbumPermissions(final CategoryItem gallery, final HashSet<Long> groupIds, final HashSet<Long> userIds, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumRemovePermissionsResponseHandler(gallery, groupIds, userIds);
            }
        }.start(messageId);
    }

    public static long startActionDeleteGallery(final long galleryId, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumDeleteResponseHandler(galleryId);
            }
        }.start(messageId);
    }

    public static long startActionAddGallery(final PiwigoGalleryDetails newAlbum, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumCreateResponseHandler(newAlbum);
            }
        }.start(messageId);
    }

    public static long startActionLogout(Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new LogoutResponseHandler();
            }
        }.start(messageId);
    }

    public static long startActionGetSubCategoryNames(final long parentAlbumId, final boolean recursive, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumGetSubAlbumNamesResponseHandler(parentAlbumId, recursive);
            }
        }.start(messageId);
    }

    public static long startActionGetSubCategories(final CategoryItem parentCategory, final boolean recursive, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumGetSubAlbumsResponseHandler(parentCategory, recursive);
            }
        }.start(messageId);

    }

    public static long startActionGetUserDetails(final String username, final String userType, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UserGetInfoResponseHandler(username, userType);
            }
        }.start(messageId);

    }

    public static long startActionGetUsernamesList(final List<Long> groupIds, final int page, final int pageSize, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UsernamesGetListResponseHandler(groupIds, page, pageSize);
            }
        }.start(messageId);

    }

    public static long startActionGetUsernamesList(final int page, final int pageSize, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UsernamesGetListResponseHandler(page, pageSize);
            }
        }.start(messageId);
    }

    public static long startActionGetUsersList(final int page, final int pageSize, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UsersGetListResponseHandler(page, pageSize);
            }
        }.start(messageId);
    }

    public static long startActionGetGroupsList(final Set<Long> groupIds, final int page, final int pageSize, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupsGetListResponseHandler(groupIds, page, pageSize);
            }
        }.start(messageId);
    }

    public static long startActionGetGroupsList(final int page, final int pageSize, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupsGetListResponseHandler(null, page, pageSize);
            }
        }.start(messageId);
    }

    public static long startActionGetResources(final CategoryItem parentCategory, final String sortOrder, final int page, final int pageSize, final String multimediaExtensionList, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new ImagesGetResponseHandler(parentCategory, sortOrder, page, pageSize, context, multimediaExtensionList);
            }
        }.start(messageId);
    }

    public static long startActionChangeRating(final ResourceItem piwigoResource, final float rating, final Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new ImageAlterRatingResponseHandler(piwigoResource, rating);
            }
        }.start(messageId);
    }

    public static long startActionUpdateGalleryInfo(final CategoryItem piwigoResource, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumUpdateInfoResponseHandler(piwigoResource);
            }
        }.start(messageId);
    }

    public static <T extends ResourceItem> long startActionUpdateResourceInfo(final T piwigoResource, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new ImageUpdateInfoResponseHandler(piwigoResource);
            }
        }.start(messageId);
    }


    //TODO this is not currently supported by the API
//    public static void startActionSetIsFavorite(PiwigoAlbum.GalleryItem piwigoResource, boolean isChecked, Context context, ImageAlterFavoriteStatusResponseHandler.AlterResourceFavoriteStatusResponseListener ) {
//        Intent intent = pkg Intent(context, PiwigoAccessService.class);
//        intent.setAction(ACTION_ALTER_RESOURCE_RATING);
//
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
//
//        RequestParams params = pkg RequestParams();
//        params.put("image_id", String.valueOf(piwigoResource.id));
//        params.put("rate", String.valueOf(rating));
//
//
//        "pwg.images.rate", params, pkg ImageAlterRatingResponseHandler(piwigoResource, )
//
//    }

//    public static long startActionGetResource(final String url, Context context) {
//        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
//        return new Worker(context) {
//
//            @Override
//            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
//                return new ImageGetToByteArrayHandler(url);
//            }
//        }.start(messageId);
//    }

    public static long startActionGetResourceToFile(final String url, final File outputFile, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new ImageGetToFileHandler(url, outputFile);
            }
        }.start(messageId);
    }

    public static long startActionDeleteGalleryItemFromServer(final long itemId, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new ImageDeleteResponseHandler(itemId);
            }
        }.start(messageId);
    }

    public static long startActionDeleteGalleryItemsFromServer(final HashSet<Long> itemIds, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new ImageDeleteResponseHandler(itemIds);
            }
        }.start(messageId);
    }

    public static <T extends AbstractPiwigoDirectResponseHandler> long rerunHandler(final T handler, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return handler;
            }
        }.start(messageId);
    }

    public static <T extends ResourceItem> long startActionGetResourceInfo(final T model, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new ImageGetInfoResponseHandler<>(model);
            }
        }.start(messageId);
    }

    public static long startActionDeleteGroup(final long groupId, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupDeleteResponseHandler(groupId);
            }
        }.start(messageId);
    }

    public static long startActionDeleteUser(final long userId, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UserDeleteResponseHandler(userId);
            }
        }.start(messageId);
    }

    public static long startActionUpdateUserInfo(final User user, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UserUpdateInfoResponseHandler(user);
            }
        }.start(messageId);
    }

    public static long startActionAddUser(final User user, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UserAddResponseHandler(user);
            }
        }.start(messageId);
    }

    public static long startActionAddUserPermissions(final long userId, final HashSet<Long> newAlbumsAllowedAccessTo, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UserPermissionsAddResponseHandler(userId, newAlbumsAllowedAccessTo);
            }
        }.start(messageId);
    }

    public static long startActionRemoveUserPermissions(final long userId, final HashSet<Long> newAlbumsNotAllowedAccessTo, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new UserPermissionsRemovedResponseHandler(userId, newAlbumsNotAllowedAccessTo);
            }
        }.start(messageId);
    }

    public static long startActionAddGroup(final Group group, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupAddResponseHandler(group);
            }
        }.start(messageId);
    }

    public static long startActionUpdateGroupInfo(final Group originalGroup, final Group newGroup, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupUpdateInfoResponseHandler(originalGroup, newGroup);
            }
        }.start(messageId);
    }

    public static long startActionAddUsersToGroup(final long groupId, final ArrayList<Long> newGroupMemberIds, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupAddMembersResponseHandler(groupId, newGroupMemberIds);
            }
        }.start(messageId);
    }

    public static long startActionRemoveUsersFromGroup(final long groupId, final ArrayList<Long> groupMemberIdsToRemove, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupRemoveMembersResponseHandler(groupId, groupMemberIdsToRemove);
            }
        }.start(messageId);

    }

    public static long startActionGroupAddPermissions(final long groupId, final ArrayList<Long> newAlbumsAllowedAccessTo, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupPermissionsAddResponseHandler(groupId, newAlbumsAllowedAccessTo);
            }
        }.start(messageId);

    }

    public static long startActionGroupRemovePermissions(final long groupId, final ArrayList<Long> albumsNotAllowedAccessTo, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new GroupPermissionsRemovedResponseHandler(groupId, albumsNotAllowedAccessTo);
            }
        }.start(messageId);

    }

    public static long startActionUpdateAlbumThubnail(final long categoryId, final Long categoryParentId, final Long resourceId, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new AlbumThumbnailUpdatedResponseHandler(categoryId, categoryParentId, resourceId);
            }
        }.start(messageId);

    }

    public static long startActionCopyItemsToAlbum(final ResourceItem itemToCopy, final CategoryItem targetAlbum, Context context) {
        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                return new ImageCopyToAlbumResponseHandler(itemToCopy, targetAlbum);
            }
        }.start(messageId);

    }

    public static long startActionMoveItemsToAlbum(final ResourceItem itemToMove, final CategoryItem targetAlbum, Context context) {

        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        return new Worker(context) {

            @Override
            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
                long moveFromAlbum = itemToMove.getParentId();
                HashSet<Long> linkedAlbums = itemToMove.getLinkedAlbums();
                linkedAlbums.remove(moveFromAlbum);
                linkedAlbums.add(targetAlbum.getId());
                return new ImageSetLinkedAlbumsResponseHandler(itemToMove, linkedAlbums);
            }
        }.start(messageId);
    }

//    public static long startActionSetLinkedAlbums(final ResourceItem itemToMove, final HashSet<Long> linkedAlbums, Context context) {
//        long messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
//        return new Worker(context) {
//
//            @Override
//            protected AbstractPiwigoDirectResponseHandler buildHandler(SharedPreferences prefs) {
//                return new ImageSetLinkedAlbumsResponseHandler(itemToMove, linkedAlbums);
//            }
//        }.start(messageId);
//
//    }
}
