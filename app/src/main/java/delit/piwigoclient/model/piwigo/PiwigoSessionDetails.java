package delit.piwigoclient.model.piwigo;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.ui.MyApplication;

/**
 * Created by gareth on 25/05/17.
 */

public class PiwigoSessionDetails {
    private static PiwigoSessionDetails instance;

    private long userGuid;
    private String username;
    private String userType;
    private String piwigoVersion;
    private Set<String> availableImageSizes;
    private Set<String> allowedFileTypes;
    private long webInterfaceUploadChunkSizeKB;
    private String sessionToken;
    private User userDetails;
    private int loginStatus = 0;
    private Boolean useCommunityPlugin;
    private String serverUrl;

    public PiwigoSessionDetails(String serverUrl, long userGuid, String username, String userType, String piwigoVersion, Set<String> availableImageSizes, String sessionToken) {
        this.serverUrl = serverUrl;
        this.userGuid = userGuid;
        this.username = username;
        this.userType = userType;
        this.piwigoVersion = piwigoVersion;
        this.availableImageSizes = availableImageSizes;
        this.sessionToken = sessionToken;
        this.loginStatus = 2;
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
        this.loginStatus = 2;
    }

    public static boolean isLoggedInAndHaveSessionAndUserDetails() {
        return instance != null && instance.loginStatus == 3;
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

    public synchronized static String getActiveSessionToken() {
        return instance == null ? null : instance.getSessionToken();
    }

    public static boolean isGuest() {
        return instance != null && "guest".equals(instance.userType);
    }

    public synchronized  static void logout() {
        HttpClientFactory.getInstance(MyApplication.getInstance()).flushCookies();
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
        this.loginStatus = 3;
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
        return (activeToken == null && piwigoSessionToken == null) || activeToken.equals(piwigoSessionToken);
    }
}
