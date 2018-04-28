package delit.piwigoclient.model.piwigo;

import android.content.Context;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.ui.MyApplication;

/**
 * Created by gareth on 25/05/17.
 */

public class PiwigoSessionDetails {
    private static PiwigoSessionDetails instance;
    public static final int NOT_LOGGED_IN = 0;
    public static final int LOGGED_IN = 1;
    public static final int LOGGED_IN_WITH_SESSION_DETAILS = 2;
    public static final int LOGGED_IN_WITH_SESSION_AND_USER_DETAILS = 3;
    private Set<String> methodsAvailable;
    private final long userGuid;
    private final String username;
    private String userType;
    private final String piwigoVersion;
    private final Set<String> availableImageSizes;
    private Set<String> allowedFileTypes;
    private long webInterfaceUploadChunkSizeKB;
    private final String sessionToken;
    private User userDetails;
    private boolean sessionMayHaveExpired;
    private int loginStatus = NOT_LOGGED_IN;
    private Boolean useCommunityPlugin;
    private final String serverUrl;

    public PiwigoSessionDetails(String serverUrl, long userGuid, String username, String userType, String piwigoVersion, Set<String> availableImageSizes, String sessionToken) {
        this.serverUrl = serverUrl;
        this.userGuid = userGuid;
        this.username = username;
        this.userType = userType;
        this.piwigoVersion = piwigoVersion;
        this.availableImageSizes = availableImageSizes;
        this.sessionToken = sessionToken;
        this.loginStatus = LOGGED_IN_WITH_SESSION_DETAILS;
    }

    public PiwigoSessionDetails(String serverUrl, long userGuid, String username, String userType, String piwigoVersion, Set<String> availableImageSizes, Set<String> allowedFileTypes, long webInterfaceUploadChunkSizeKB, String sessionToken) {
        this.serverUrl = serverUrl;
        this.userGuid = userGuid;
        this.username = username;
        this.userType = userType;
        this.piwigoVersion = piwigoVersion;
        this.availableImageSizes = availableImageSizes;
        this.allowedFileTypes = allowedFileTypes;
        this.webInterfaceUploadChunkSizeKB = webInterfaceUploadChunkSizeKB;
        this.sessionToken = sessionToken;
        this.loginStatus = LOGGED_IN_WITH_SESSION_DETAILS;
    }

    public void setSessionMayHaveExpired() {
        this.sessionMayHaveExpired = true;
    }

    public boolean isSessionMayHaveExpired() {
        return sessionMayHaveExpired;
    }

    public static boolean isLoggedInAndHaveSessionAndUserDetails() {
        return instance != null && instance.loginStatus == LOGGED_IN_WITH_SESSION_AND_USER_DETAILS;
    }

    public static long getUserGuid() {
        return instance != null ? instance.userGuid : -1;
    }

    public static boolean isLoggedIn() {
        return instance != null && instance.loginStatus >= 1;
    }

    public static boolean isLoggedInWithSessionDetails() {
        return instance != null && instance.loginStatus >= 2 && instance.isCommunityPluginStatusAvailable();
    }

    public synchronized static PiwigoSessionDetails getInstance() {
        return instance;
    }

    public synchronized static void setInstance(PiwigoSessionDetails sessionDetails) {
        instance = sessionDetails;
    }

    public void setMethodsAvailable(Set<String> methodsAvailable) {
        this.methodsAvailable = methodsAvailable;
    }

    public synchronized static String getActiveSessionToken() {
        return instance == null ? null : instance.getSessionToken();
    }

    public static boolean isGuest() {
        return instance != null && "guest".equals(instance.userType);
    }

    public synchronized  static void logout(Context context) {
        HttpClientFactory.getInstance(context).flushCookies();
        instance = null;
    }

    public static boolean isFullyLoggedIn() {
        return (isLoggedInWithSessionDetails() && !isAdminUser()) || isLoggedInAndHaveSessionAndUserDetails();
    }

    public static boolean isAdminUser() {
        return instance != null && ("webmaster".equals(instance.userType) || "admin".equals(instance.userType));
    }

    public static boolean isUseCommunityPlugin() {
        return instance != null && Boolean.TRUE.equals(instance.useCommunityPlugin);
    }

    public String getUsername() {
        return username;
    }

    public String getUserType() {
        return userType;
    }

    public String getPiwigoVersion() {
        return piwigoVersion;
    }

    public Set<String> getAvailableImageSizes() {
        return availableImageSizes;
    }

    public Set<String> getAllowedFileTypes() {
        return allowedFileTypes;
    }

    public long getWebInterfaceUploadChunkSizeKB() {
        return webInterfaceUploadChunkSizeKB;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public long getUserId() {
        if(userDetails == null) {
            return -1;
        }
        return userDetails.getId();
    }

    //TODO - need to update this if we add the user to any groups...
    public HashSet<Long> getGroupMemberships() {
        if(userDetails == null) {
            return new HashSet<>();
        }
        return userDetails.getGroups();
    }

    public void setUserDetails(User userDetails) {
        this.userDetails = userDetails;
        this.loginStatus = LOGGED_IN_WITH_SESSION_AND_USER_DETAILS;
    }

    public boolean isCommunityPluginStatusAvailable() {
        return useCommunityPlugin != null;
    }

    public void setUseCommunityPlugin(boolean useCommunityPlugin) {
        this.useCommunityPlugin = useCommunityPlugin;
    }

    public void updateUserType(String realUserStatus) {
        this.userType = realUserStatus;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public static boolean matchesSessionToken(String piwigoSessionToken) {
        String activeToken = getActiveSessionToken();
        return activeToken == null && piwigoSessionToken == null
                || activeToken != null && activeToken.equals(piwigoSessionToken);
    }

    public static boolean matchesServerConnection(String piwigoServerConnection) {
        String activeToken = getActiveServerConnection();
        return activeToken == null && piwigoServerConnection == null
                || activeToken != null && activeToken.equals(piwigoServerConnection);
    }

    public static String getActiveServerConnection() {
        return instance == null ? null : instance.getServerUrl();
    }

    public boolean isMethodsAvailableListAvailable() {
        return methodsAvailable != null;
    }

    private boolean isMethodAvailable(String methodName) {
        return methodsAvailable != null && methodsAvailable.contains(methodName);
    }

    public boolean isUseUserTagPluginForUpdate() {
        return isMethodAvailable("user_tags.tags.update");
    }

    public boolean isUseUserTagPluginForSearch() {
        return isMethodAvailable("user_tags.tags.list");
    }
}
