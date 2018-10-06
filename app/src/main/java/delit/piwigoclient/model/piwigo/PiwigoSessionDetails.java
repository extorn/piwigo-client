package delit.piwigoclient.model.piwigo;

import android.content.Context;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.HttpClientFactory;

/**
 * Created by gareth on 25/05/17.
 */

public class PiwigoSessionDetails {
    public static final String UNKNOWN_VERSION = "Unknown";
    public static final int NOT_LOGGED_IN = 0;
    public static final int LOGGED_IN = 1;
    public static final int LOGGED_IN_WITH_SESSION_DETAILS = 2;
    public static final int LOGGED_IN_WITH_SESSION_AND_USER_DETAILS = 3;
    private static final HashMap<ConnectionPreferences.ProfilePreferences, PiwigoSessionDetails> sessionDetailsMap = new HashMap<>(3);

    private final Date retrievedAt;
    private final ConnectionPreferences.ProfilePreferences connectionPrefs;
    private final long userGuid;
    private final String username;
    private final String piwigoVersion;
    private final Set<String> availableImageSizes;
    private final String sessionToken;
    private final String serverUrl;
    private Set<String> methodsAvailable;
    private String userType;
    private Set<String> allowedFileTypes;
    private long webInterfaceUploadChunkSizeKB;
    private User userDetails;
    private boolean sessionMayHaveExpired;
    private int loginStatus = NOT_LOGGED_IN;
    private Boolean useCommunityPlugin;

    public PiwigoSessionDetails(ConnectionPreferences.ProfilePreferences connectionPrefs, String serverUrl, long userGuid, String username, String userType, String piwigoVersion, Set<String> availableImageSizes, String sessionToken) {
        this.connectionPrefs = connectionPrefs;
        this.serverUrl = serverUrl;
        this.userGuid = userGuid;
        this.username = username;
        this.userType = userType;
        this.piwigoVersion = piwigoVersion;
        this.availableImageSizes = availableImageSizes;
        this.sessionToken = sessionToken;
        this.loginStatus = LOGGED_IN_WITH_SESSION_DETAILS;
        this.retrievedAt = new Date();
    }

    public PiwigoSessionDetails(ConnectionPreferences.ProfilePreferences connectionPrefs, String serverUrl, long userGuid, String username, String userType, String piwigoVersion, Set<String> availableImageSizes, Set<String> allowedFileTypes, long webInterfaceUploadChunkSizeKB, String sessionToken) {
        this.connectionPrefs = connectionPrefs;
        this.serverUrl = serverUrl;
        this.userGuid = userGuid;
        this.username = username;
        this.userType = userType;
        this.piwigoVersion = piwigoVersion;
        this.availableImageSizes = availableImageSizes;
        this.sessionToken = sessionToken;
        this.loginStatus = LOGGED_IN_WITH_SESSION_DETAILS;
        this.retrievedAt = new Date();
        // extra params
        this.allowedFileTypes = allowedFileTypes;
        this.webInterfaceUploadChunkSizeKB = webInterfaceUploadChunkSizeKB;
    }

    public static boolean isLoggedInAndHaveSessionAndUserDetails(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails instance = getInstance(connectionPrefs);
        return instance != null && instance.isLoggedInAndHaveSessionAndUserDetails();
    }

    public static long getUserGuid(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails instance = getInstance(connectionPrefs);
        return instance != null ? instance.userGuid : -1;
    }

    public static boolean isLoggedIn(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails instance = getInstance(connectionPrefs);
        return instance != null && instance.isLoggedIn();
    }

    public synchronized static PiwigoSessionDetails getInstance(ConnectionPreferences.ProfilePreferences activeProfile) {
        return sessionDetailsMap.get(activeProfile);
    }

    public synchronized static void setInstance(ConnectionPreferences.ProfilePreferences activeProfile, PiwigoSessionDetails sessionDetails) {
        sessionDetailsMap.put(activeProfile, sessionDetails);
    }

    public synchronized static String getActiveSessionToken(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails instance = getInstance(connectionPrefs);
        return instance == null ? null : instance.getSessionToken();
    }

    public synchronized static void logout(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context) {
        PiwigoSessionDetails instance = sessionDetailsMap.remove(connectionPrefs);
        if (instance != null) {
            HttpClientFactory.getInstance(context).flushCookies(instance.connectionPrefs);
        }
    }

    public static boolean isFullyLoggedIn(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails instance = getInstance(connectionPrefs);
        return instance != null && instance.isFullyLoggedIn();
    }

    public static boolean isAdminUser(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails instance = getInstance(connectionPrefs);
        return instance != null && instance.isAdminUser();
    }

    public static boolean isUseCommunityPlugin(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails instance = getInstance(connectionPrefs);
        return instance != null && instance.isUseCommunityPlugin();
    }

    public static boolean matchesSessionToken(ConnectionPreferences.ProfilePreferences connectionPrefs, String piwigoSessionToken) {
        String activeToken = getActiveSessionToken(connectionPrefs);
        return activeToken == null && piwigoSessionToken == null
                || activeToken != null && activeToken.equals(piwigoSessionToken);
    }

    public static boolean matchesServerConnection(ConnectionPreferences.ProfilePreferences connectionPrefs, String piwigoServerConnection) {
        String activeToken = getActiveServerConnection(connectionPrefs);
        return activeToken == null && piwigoServerConnection == null
                || activeToken != null && activeToken.equals(piwigoServerConnection);
    }

    public static String getActiveServerConnection(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails instance = getInstance(connectionPrefs);
        return instance == null ? null : instance.getServerUrl();
    }

    public void setSessionMayHaveExpired() {
        this.sessionMayHaveExpired = true;
    }

    public boolean isSessionMayHaveExpired() {
        return sessionMayHaveExpired;
    }

    public boolean isLoggedInAndHaveSessionAndUserDetails() {
        return loginStatus == LOGGED_IN_WITH_SESSION_AND_USER_DETAILS;
    }

    public boolean isLoggedIn() {
        return loginStatus >= 1;
    }

    public boolean isLoggedInWithBasicSessionDetails() {
        return loginStatus >= 2;
    }

    public boolean isLoggedInWithFullSessionDetails() {
        return loginStatus >= 2 && isCommunityPluginStatusAvailable();
    }

    public void setMethodsAvailable(Set<String> methodsAvailable) {
        this.methodsAvailable = methodsAvailable;
    }

    public boolean isGuest() {
        return "guest".equals(userType);
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPrefs() {
        return connectionPrefs;
    }

    public boolean isFullyLoggedIn() {
        if (isAdminUser() /*|| isUseCommunityPlugin()*/) {
            return isLoggedInAndHaveSessionAndUserDetails();
        } else {
            return isLoggedInWithFullSessionDetails();
        }
    }

    public boolean isAdminUser() {
        return "webmaster".equals(userType) || "admin".equals(userType);
    }

    public boolean isUseCommunityPlugin() {
        return Boolean.TRUE.equals(useCommunityPlugin);
    }

    public void setUseCommunityPlugin(boolean useCommunityPlugin) {
        this.useCommunityPlugin = useCommunityPlugin;
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
        if (userDetails == null) {
            return -1;
        }
        return userDetails.getId();
    }

    //TODO - need to update this if we add the user to any groups...
    public HashSet<Long> getGroupMemberships() {
        if (userDetails == null) {
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

    public void updateUserType(String realUserStatus) {
        this.userType = realUserStatus;
    }

    public String getServerUrl() {
        return serverUrl;
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

    public boolean isOlderThanSeconds(int i) {
        long ageMillis = System.currentTimeMillis() - retrievedAt.getTime();
        return ageMillis > (1000 * i);
    }
}
