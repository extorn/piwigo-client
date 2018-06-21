package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.util.SecurePrefsUtil;

/**
 * Created by gareth on 22/01/18.
 */

public class ConnectionPreferences {

    public static String getActiveConnectionProfile(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_piwigo_connection_profile_key), "");
    }

    public static Set<String> getConnectionProfileList(SharedPreferences prefs, Context context) {
        return prefs.getStringSet(context.getString(R.string.preference_piwigo_connection_profile_list_key), null);
    }

    public static void deletePreferences(SharedPreferences prefs, Context context, @NonNull String prefix) {
        if(prefix == null || prefix.isEmpty()) {
            //TODO do this better - this causes exceptions (crashes) for users.
//            throw new IllegalArgumentException("Unable to delete the core app preferences");
        }
        if(activeProfile != null && prefix.equals(activeProfile.prefix)) {
            throw new IllegalArgumentException("Unable to delete preferences for active profile");
        }
        new ProfilePreferences(prefix).delete(prefs, context);
    }

    public static void clonePreferences(SharedPreferences prefs, Context context, String fromPrefix, String toPrefix) {
        ProfilePreferences fromPrefs = new ProfilePreferences(fromPrefix);
        ProfilePreferences toPrefs = new ProfilePreferences(toPrefix);
        toPrefs.copyFrom(prefs, context, fromPrefs);
    }

    private static ProfilePreferences activeProfile;

    public static ProfilePreferences getActiveProfile() {
        if(activeProfile == null) {
            activeProfile = new ProfilePreferences(null);
        }
        return activeProfile;
    }

    public static ProfilePreferences getPreferences(String profile) {
        return new ProfilePreferences(profile);
    }

    public static class ProfilePreferences implements Serializable, Comparable<ProfilePreferences> {
        private final String prefix;
        private boolean asGuest;

        private ProfilePreferences(String prefix) {
            this.prefix = prefix;
        }

        private ProfilePreferences(String prefix, boolean asGuest) {
            this.prefix = prefix;
            this.asGuest = asGuest;
        }

        public ProfilePreferences asGuest() {
            return new ProfilePreferences(prefix, true);
        }

        @Override
        public String toString() {
            return prefix + " " + asGuest;
        }

        @Override
        public boolean equals(Object obj) {
            if(!(obj instanceof ProfilePreferences)) {
                return false;
            }
            ProfilePreferences other = (ProfilePreferences)obj;
            boolean equals = (prefix == other.prefix) || (prefix != null && prefix.equals(other.prefix));
            return equals && (asGuest == other.asGuest);
        }

        @Override
        public int compareTo(@NonNull ProfilePreferences o) {
            int retVal = 0;
            if(prefix == null) {
                if(o.prefix != null) {
                    return 1;
                }
            } else {
                retVal = prefix.compareTo(o.prefix);
            }
            if(retVal != 0) {
                return retVal;
            }
            if(asGuest && !o.asGuest) {
                return 1;
            }
            if(!asGuest && o.asGuest) {
                return -1;
            }
            return 0;
        }

        @Override
        public int hashCode() {
            int hashcode = prefix != null ? prefix.hashCode() : 0;
            hashcode += asGuest ? 13 : 17;
            return hashcode;
        }

        public String getAbsoluteProfileKey(SharedPreferences prefs, Context context) {
            return getProfileId(prefs, context) + ':' + asGuest;
        }

        public String getProfileId(SharedPreferences prefs, Context context) {
            if(prefix != null) {
                return prefix;
            } else {
                return prefs.getString(context.getString(R.string.preference_piwigo_connection_profile_key), "");
            }
        }
        
        private String getKey(Context context, @StringRes int keyId) {
            if(this.prefix != null && this.prefix.length() > 0) {
                return prefix + ':' + context.getString(keyId);
            }
            return context.getString(keyId);
        }

        public String getPiwigoServerAddress(SharedPreferences prefs, Context context) {
            return prefs.getString(getKey(context, R.string.preference_piwigo_server_address_key), null);
        }

        public String getTrimmedNonNullPiwigoServerAddress(SharedPreferences prefs, Context context) {
            return prefs.getString(getKey(context, R.string.preference_piwigo_server_address_key), "").trim();
        }

        public boolean getUseBasicAuthentication(SharedPreferences prefs, Context context) {
            return prefs.getBoolean(getKey(context, R.string.preference_server_use_basic_auth_key), false);
        }

        public String getBasicAuthenticationUsername(SharedPreferences prefs, Context context) {
            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);
            return prefUtil.readSecureStringPreference(prefs, getKey(context, R.string.preference_server_basic_auth_username_key), "");
        }

        public String getBasicAuthenticationPassword(SharedPreferences prefs, Context context) {
            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);
            return prefUtil.readSecureStringPreference(prefs, getKey(context, R.string.preference_server_basic_auth_password_key), "");
        }

        public boolean getUseClientCertificates(SharedPreferences prefs, Context context) {
            return prefs.getBoolean(getKey(context, R.string.preference_server_use_client_certs_key), context.getResources().getBoolean(R.bool.preference_server_use_client_certs_default));
        }

        public boolean getUsePinnedServerCertificates(SharedPreferences prefs, Context context) {
            return prefs.getBoolean(getKey(context, R.string.preference_server_use_custom_trusted_ca_certs_key), context.getResources().getBoolean(R.bool.preference_server_use_custom_trusted_ca_certs_default));
        }

        public Set<String> getUserPreNotifiedCerts(SharedPreferences prefs, Context context) {
            return prefs.getStringSet(getKey(context, R.string.preference_pre_user_notified_certificates_key), new HashSet<String>());
        }

        public String getPiwigoUsername(SharedPreferences prefs, Context context) {
            if(asGuest) {
                return null;
            }
            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);
            return prefUtil.readSecureStringPreference(prefs, getKey(context, R.string.preference_piwigo_server_username_key), null);
        }

        public String getPiwigoPassword(SharedPreferences prefs, Context context) {
            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);
            return prefUtil.readSecureStringPreference(prefs, getKey(context, R.string.preference_piwigo_server_password_key), null);
        }

        public String getPiwigoPasswordNotNull(SharedPreferences prefs, Context context) {
            String pass = getPiwigoPassword(prefs, context);
            if(pass == null) {
                return "";
            }
            return pass;
        }

        public String getCertificateHostnameVerificationLevel(SharedPreferences prefs, Context context) {
            return prefs.getString(getKey(context, R.string.preference_server_ssl_certificate_hostname_verification_key), context.getResources().getString(R.string.preference_server_ssl_certificate_hostname_verification_default));
        }

        public boolean getFollowHttpRedirects(SharedPreferences prefs, Context context) {
            boolean defaultAllowRedirects = context.getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default);
            return prefs.getBoolean(getKey(context, R.string.preference_server_connection_allow_redirects_key), defaultAllowRedirects);
        }

        public int getMaxHttpRedirects(SharedPreferences prefs, Context context) {
            int defaultMaxRedirects = context.getResources().getInteger(R.integer.preference_server_connection_max_redirects_default);
            return prefs.getInt(getKey(context, R.string.preference_server_connection_max_redirects_key), defaultMaxRedirects);
        }

        public int getMaxServerConnectRetries(SharedPreferences prefs, Context context) {
            int defaultConnectRetries = context.getResources().getInteger(R.integer.preference_server_connection_retries_default);
            return prefs.getInt(getKey(context, R.string.preference_server_connection_retries_key), defaultConnectRetries);
        }

        public int getServerConnectTimeout(SharedPreferences prefs, Context context) {
            int defaultConnectTimeoutMillis = context.getResources().getInteger(R.integer.preference_server_socketTimeout_millisecs_default);
            return prefs.getInt(getKey(context, R.string.preference_server_socketTimeout_millisecs_key), defaultConnectTimeoutMillis);
        }

        public void copyFrom(SharedPreferences prefs, Context context, ProfilePreferences fromPrefs) {

            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);

            SharedPreferences.Editor editor = prefs.edit();

            // piwigo server connection details
            writeStringPref(editor,getKey(context, R.string.preference_piwigo_server_address_key), fromPrefs.getPiwigoServerAddress(prefs, context));
            writeSecurePref(editor, prefUtil, getKey(context, R.string.preference_piwigo_server_username_key), fromPrefs.getPiwigoUsername(prefs, context));
            writeSecurePref(editor, prefUtil, getKey(context, R.string.preference_piwigo_server_password_key), fromPrefs.getPiwigoPassword(prefs, context));

            // fine grained http connection configuration bits and bobs
            writeIntPref(editor, getKey(context, R.string.preference_server_socketTimeout_millisecs_key), fromPrefs.getServerConnectTimeout(prefs, context));
            writeIntPref(editor, getKey(context, R.string.preference_server_connection_retries_key), fromPrefs.getMaxServerConnectRetries(prefs, context));
            writeIntPref(editor, getKey(context, R.string.preference_server_connection_max_redirects_key), fromPrefs.getMaxHttpRedirects(prefs, context));
            writeBooleanPref(editor, getKey(context, R.string.preference_server_connection_allow_redirects_key), fromPrefs.getFollowHttpRedirects(prefs, context));
            writeStringPref(editor, getKey(context, R.string.preference_server_ssl_certificate_hostname_verification_key), fromPrefs.getCertificateHostnameVerificationLevel(prefs, context));

            // received server certs list
            writeStringSetPref(editor, getKey(context, R.string.preference_pre_user_notified_certificates_key), fromPrefs.getUserPreNotifiedCerts(prefs, context));

            // pinned server certs
            writeBooleanPref(editor, getKey(context, R.string.preference_server_use_custom_trusted_ca_certs_key), fromPrefs.getUsePinnedServerCertificates(prefs, context));

            // client certs
            writeBooleanPref(editor, getKey(context, R.string.preference_server_use_client_certs_key), fromPrefs.getUseClientCertificates(prefs, context));

            // Basic authentication
            writeBooleanPref(editor, getKey(context, R.string.preference_server_use_basic_auth_key), fromPrefs.getUseBasicAuthentication(prefs, context));
            writeSecurePref(editor, prefUtil, getKey(context, R.string.preference_server_basic_auth_username_key), fromPrefs.getBasicAuthenticationUsername(prefs, context));
            writeSecurePref(editor, prefUtil, getKey(context, R.string.preference_server_basic_auth_password_key), fromPrefs.getBasicAuthenticationPassword(prefs, context));

            editor.apply();
            editor.commit();
        }

        private void writeBooleanPref(SharedPreferences.Editor editor, String key, boolean value) {
            editor.putBoolean(key, value);
        }

        private void writeIntPref(SharedPreferences.Editor editor, String key, int value) {
            editor.putInt(key, value);
        }

        private void writeStringPref(SharedPreferences.Editor editor, String key, String value) {
            editor.putString(key, value);
        }

        private void writeStringSetPref(SharedPreferences.Editor editor, String key, Set<String> value) {
            editor.putStringSet(key, value);
        }

        public void writeSecurePref(SharedPreferences.Editor editor, SecurePrefsUtil prefUtil, String key, String plainTextValue) {
            String encryptedValue = prefUtil.encryptValue(key, plainTextValue);
            editor.putString(key, encryptedValue);
        }

        public void delete(SharedPreferences prefs, Context context) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(getKey(context, R.string.preference_piwigo_server_address_key));
            editor.remove(getKey(context, R.string.preference_piwigo_server_username_key));
            editor.remove(getKey(context, R.string.preference_piwigo_server_password_key));
            editor.remove(getKey(context, R.string.preference_server_socketTimeout_millisecs_key));
            editor.remove(getKey(context, R.string.preference_server_connection_retries_key));
            editor.remove(getKey(context, R.string.preference_server_connection_max_redirects_key));
            editor.remove(getKey(context, R.string.preference_server_connection_allow_redirects_key));
            editor.remove(getKey(context, R.string.preference_server_ssl_certificate_hostname_verification_key));
            editor.remove(getKey(context, R.string.preference_pre_user_notified_certificates_key));
            editor.remove(getKey(context, R.string.preference_server_use_custom_trusted_ca_certs_key));
            editor.remove(getKey(context, R.string.preference_server_use_client_certs_key));
            editor.remove(getKey(context, R.string.preference_server_use_basic_auth_key));
            editor.remove(getKey(context, R.string.preference_server_basic_auth_username_key));
            editor.remove(getKey(context, R.string.preference_server_basic_auth_password_key));
            editor.apply();
            editor.commit();
        }
    }

    public static String getPiwigoServerAddress(SharedPreferences prefs, Context context) {
        return getActiveProfile().getPiwigoServerAddress(prefs, context);
    }

    public static String getTrimmedNonNullPiwigoServerAddress(SharedPreferences prefs, Context context) {
        return getActiveProfile().getTrimmedNonNullPiwigoServerAddress(prefs, context);
    }

    public static boolean getUseBasicAuthentication(SharedPreferences prefs, Context context) {
        return getActiveProfile().getUseBasicAuthentication(prefs, context);
    }

    public static String getBasicAuthenticationUsername(SharedPreferences prefs, Context context) {
        return getActiveProfile().getBasicAuthenticationUsername(prefs, context);
    }

    public static String getBasicAuthenticationPassword(SharedPreferences prefs, Context context) {
        return getActiveProfile().getBasicAuthenticationPassword(prefs, context);
    }

    public static boolean getUseClientCertificates(SharedPreferences prefs, Context context) {
        return getActiveProfile().getUseClientCertificates(prefs, context);
    }

    public static boolean getUsePinnedServerCertificates(SharedPreferences prefs, Context context) {
        return getActiveProfile().getUsePinnedServerCertificates(prefs, context);
    }

    public static Set<String> getUserPreNotifiedCerts(SharedPreferences prefs, Context context) {
        return getActiveProfile().getUserPreNotifiedCerts(prefs, context);
    }

    public static String getPiwigoUsername(SharedPreferences prefs, Context context) {
        return getActiveProfile().getPiwigoUsername(prefs, context);
    }

    public static String getPiwigoPassword(SharedPreferences prefs, Context context) {
        return getActiveProfile().getPiwigoPassword(prefs, context);
    }

    public static String getPiwigoPasswordNotNull(SharedPreferences prefs, Context context) {
        return getActiveProfile().getPiwigoPasswordNotNull(prefs, context);
    }

    public static String getCertificateHostnameVerificationLevel(SharedPreferences prefs, Context context) {
        return getActiveProfile().getCertificateHostnameVerificationLevel(prefs, context);
    }

    public static boolean getFollowHttpRedirects(SharedPreferences prefs, Context context) {
        return getActiveProfile().getFollowHttpRedirects(prefs, context);
    }

    public static int getMaxHttpRedirects(SharedPreferences prefs, Context context) {
        return getActiveProfile().getMaxHttpRedirects(prefs, context);
    }

    public static int getMaxServerConnectRetries(SharedPreferences prefs, Context context) {
        return getActiveProfile().getMaxServerConnectRetries(prefs, context);
    }

    public static int getServerConnectTimeout(SharedPreferences prefs, Context context) {
        return getActiveProfile().getServerConnectTimeout(prefs, context);
    }


}
