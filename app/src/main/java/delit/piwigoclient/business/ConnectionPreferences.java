package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.preferences.SecurePrefsUtil;

/**
 * Created by gareth on 22/01/18.
 */

public class ConnectionPreferences {

    private static ProfilePreferences activeProfile;

    public static String getActiveConnectionProfile(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_piwigo_connection_profile_key), "");
    }

    public static Set<String> getConnectionProfileList(SharedPreferences prefs, Context context) {
        return new HashSet<>(prefs.getStringSet(context.getString(R.string.preference_piwigo_connection_profile_list_key), new HashSet<>(0)));
    }

    public static void deletePreferences(SharedPreferences prefs, Context context, @NonNull String prefix) {
        if (prefix.isEmpty()) {
            //FIXME do this better - this causes exceptions (crashes) for users.
//            throw new IllegalArgumentException("Unable to delete the core app preferences");
        }
        if (activeProfile != null && prefix.equals(activeProfile.prefix)) {
            throw new IllegalArgumentException("Unable to delete preferences for active profile");
        }
        new ProfilePreferences(prefix).delete(prefs, context);
    }

    public static void clonePreferences(SharedPreferences prefs, Context context, String fromPrefix, String toPrefix) {
        ProfilePreferences fromPrefs = new ProfilePreferences(fromPrefix);
        ProfilePreferences toPrefs = new ProfilePreferences(toPrefix);
        toPrefs.copyFrom(prefs, context, fromPrefs);
    }

    public static ProfilePreferences getActiveProfile() {
        if (activeProfile == null) {
            activeProfile = new ProfilePreferences((String)null);
        }
        return activeProfile;
    }

    public static ProfilePreferences getPreferences(String profile, SharedPreferences prefs, Context context) {
        if(profile != null) {
            if(profile.equals(getActiveConnectionProfile(prefs, context)) || getConnectionProfileList(prefs, context).size() == 1) {
                ConnectionPreferences.clonePreferences(prefs, context, null, profile);
            }
        }
        return new ProfilePreferences(profile);
    }

    public static int getMaxCacheEntrySizeBytes(SharedPreferences prefs, Context context) {
        int defaultValueKb = context.getResources().getInteger(R.integer.preference_caching_max_cache_entry_size_default);
        return prefs.getInt(context.getString(R.string.preference_caching_max_cache_entry_size_key), defaultValueKb * 1024);
    }

    public static int getMaxCacheEntries(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_caching_max_cache_entries_key), context.getResources().getInteger(R.integer.preference_caching_max_cache_entries_default));
    }

    public static String getCacheLevel(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_caching_level_key), context.getResources().getString(R.string.preference_caching_level_default));
    }

    public static ProfilePreferences.PreferenceActor getPreferenceActor(Context context, String profileId, int preferenceKey) {
        ProfilePreferences.PreferenceActor actor = new ProfilePreferences.PreferenceActor(profileId);
        actor.with(preferenceKey);
        actor.with(SecurePrefsUtil.getInstance(context, BuildConfig.APPLICATION_ID));
        return actor;
    }

    public static class ProfilePreferences implements Serializable, Parcelable, Comparable<ProfilePreferences> {

        private static final long serialVersionUID = -839430660180276975L;
        private final String prefix;
        private boolean asGuest;

        protected ProfilePreferences(Parcel in) {
            prefix = in.readString();
            asGuest = ParcelUtils.readBool(in);
        }

        public static final Creator<ProfilePreferences> CREATOR = new Creator<ProfilePreferences>() {
            @Override
            public ProfilePreferences createFromParcel(Parcel in) {
                return new ProfilePreferences(in);
            }

            @Override
            public ProfilePreferences[] newArray(int size) {
                return new ProfilePreferences[size];
            }
        };

        public PreferenceActor getPrefActor() {
            return new PreferenceActor(this.prefix);
        }

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

        @NotNull
        @Override
        public String toString() {
            return prefix + " " + asGuest;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ProfilePreferences)) {
                return false;
            }
            ProfilePreferences other = (ProfilePreferences) obj;
            boolean equals = (prefix == other.prefix) || (prefix != null && prefix.equals(other.prefix));
            return equals && (asGuest == other.asGuest);
        }

        @Override
        public int compareTo(@NonNull ProfilePreferences o) {
            int retVal = 0;
            if (prefix == null) {
                if (o.prefix != null) {
                    return 1;
                }
            } else {
                retVal = prefix.compareTo(o.prefix);
            }
            if (retVal != 0) {
                return retVal;
            }
            if (asGuest && !o.asGuest) {
                return 1;
            }
            if (!asGuest && o.asGuest) {
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
            return getProfileId(prefs, context) + ':' + (asGuest ? "guest" : getPiwigoUsername(prefs, context));
        }

        public boolean isDefaultProfile() {
            return prefix == null;
        }

        public String getProfileId(SharedPreferences prefs, Context context) {
            if (prefix != null) {
                return prefix;
            } else {
                return prefs.getString(context.getString(R.string.preference_piwigo_connection_profile_key), "");
            }
        }

        public String getPiwigoServerAddress(SharedPreferences prefs, Context context) {
            return getPrefActor().with(R.string.preference_piwigo_server_address_key).readString(prefs, context, null);
        }

        public String getTrimmedNonNullPiwigoServerAddress(SharedPreferences prefs, Context context) {
            return getPrefActor().with(R.string.preference_piwigo_server_address_key).readString(prefs, context, "").trim();
        }

        public boolean getUseBasicAuthentication(SharedPreferences prefs, Context context) {
            return getPrefActor().with(R.string.preference_server_use_basic_auth_key).readBoolean(prefs, context, false);
        }

        public String getBasicAuthenticationUsername(SharedPreferences prefs, Context context) {
            return getPrefActor().with(R.string.preference_server_basic_auth_username_key).readStringEncrypted(prefs, context, "");
        }

        public String getBasicAuthenticationPassword(SharedPreferences prefs, Context context) {
            return getPrefActor().with(R.string.preference_server_basic_auth_password_key).readStringEncrypted(prefs, context, "");
        }

        public boolean getUseClientCertificates(SharedPreferences prefs, Context context) {
            boolean defaultValue = context.getResources().getBoolean(R.bool.preference_server_use_client_certs_default);
            return getPrefActor().with(R.string.preference_server_use_client_certs_key).readBoolean(prefs, context, defaultValue);
        }

        public boolean getUsePinnedServerCertificates(SharedPreferences prefs, Context context) {
            boolean defaultValue = context.getResources().getBoolean(R.bool.preference_server_use_custom_trusted_ca_certs_default);
            return getPrefActor().with(R.string.preference_server_use_custom_trusted_ca_certs_key).readBoolean(prefs, context, defaultValue);
        }

        public Set<String> getUserPreNotifiedCerts(SharedPreferences prefs, Context context) {
            HashSet<String> defaultValue = new HashSet<>(0);
            return getPrefActor().with(R.string.preference_pre_user_notified_certificates_key).readStringSet(prefs, context, defaultValue);
        }

        public String getPiwigoUsername(SharedPreferences prefs, Context context) {
            if (asGuest) {
                return null;
            }
            return getPrefActor().with(R.string.preference_piwigo_server_username_key).readStringEncrypted(prefs, context, null);
        }

        public String getPiwigoPassword(SharedPreferences prefs, Context context) {
            return getPrefActor().with(R.string.preference_piwigo_server_password_key).readStringEncrypted(prefs, context, null);
        }

        public String getPiwigoPasswordNotNull(SharedPreferences prefs, Context context) {
            String pass = getPiwigoPassword(prefs, context);
            if (pass == null) {
                return "";
            }
            return pass;
        }

        public String getPiwigoUniqueResourceKey(SharedPreferences prefs, Context context) {
            String defaultVal = context.getResources().getString(R.string.preference_gallery_unique_id_default);
            return getPrefActor().with(R.string.preference_gallery_unique_id_key).readString(prefs, context, defaultVal);
        }

        public String getCertificateHostnameVerificationLevel(SharedPreferences prefs, Context context) {
            String defaultVal = context.getResources().getString(R.string.preference_server_ssl_certificate_hostname_verification_default);
            return getPrefActor().with(R.string.preference_server_ssl_certificate_hostname_verification_key).readString(prefs, context, defaultVal);
        }

        public boolean getFollowHttpRedirects(SharedPreferences prefs, Context context) {
            boolean defaultAllowRedirects = context.getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default);
            return getPrefActor().with(R.string.preference_server_connection_allow_redirects_key).readBoolean(prefs, context, defaultAllowRedirects);
        }

        public boolean isForceHttps(SharedPreferences prefs, Context context) {
            boolean forceHttpsUris = context.getResources().getBoolean(R.bool.preference_server_connection_force_https_default);
            return getPrefActor().with(R.string.preference_server_connection_force_https_key).readBoolean(prefs, context, forceHttpsUris);
        }

        public boolean isOfflineMode(SharedPreferences prefs, Context context) {
            boolean defaultOfflineMode = context.getResources().getBoolean(R.bool.preference_server_connection_offline_mode_default);
            return getPrefActor().with(R.string.preference_server_connection_offline_mode_key).readBoolean(prefs, context, defaultOfflineMode);
        }

        public void setForceHttps(SharedPreferences prefs, Context context, boolean newValue) {
            getPrefActor().with(R.string.preference_server_connection_force_https_key).writeBoolean(prefs, context, newValue);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(prefix);
            ParcelUtils.writeBool(dest, asGuest);
        }

        public static class PreferenceActor {
            private int prefKey;
            private String profileId;
            private SecurePrefsUtil securePrefUtil;
            private static final String TAG  = "PrefActor";

            public PreferenceActor(String profileId) {
                this.profileId = profileId;
            }
            
            public PreferenceActor with(SecurePrefsUtil securePrefUtil) {
                this.securePrefUtil = securePrefUtil;
                return this;
            }
            
            public PreferenceActor with(@StringRes int prefKey) {
                this.prefKey = prefKey;
                return this;
            }
            
            public void writeString(SharedPreferences prefs, Context context, String newValue) {
                SharedPreferences.Editor editor = prefs.edit();
                writeString(editor, context, newValue);
                editor.commit();
            }

            public SharedPreferences.Editor writeString(SharedPreferences.Editor editor, Context context, String newValue) {
                if(prefKey == R.string.preference_piwigo_playable_media_extensions_key) {
                    //TODO remove this once I find what is inserting a string into this key!
                    throw new RuntimeException();
                }
                editor.putString(getPrefKeyInProfile(context, prefKey), newValue);
                return editor;
            }

            public SharedPreferences.Editor writeStringEncrypted(SharedPreferences.Editor editor, Context context, String value) {
                if(securePrefUtil == null) {
                    securePrefUtil = SecurePrefsUtil.getInstance(context, BuildConfig.APPLICATION_ID);
                }
                securePrefUtil.writeSecurePreference(editor, getPrefKeyInProfile(context, prefKey), value);
                return editor;
            }

            public Set<String> readStringSet(SharedPreferences prefs, Context context, Set<String> defaultVal) {
                try {
                    return new HashSet<>(prefs.getStringSet(getPrefKeyInProfile(context, prefKey), defaultVal));
                } catch(ClassCastException e) {
                    String value = prefs.getString(getPrefKeyInProfile(context, prefKey), null);
                    Logging.log(Log.ERROR, TAG, "Expected a string set for pref "+prefKey+" but was string : " + value);
                    throw e;
                }
            }

            public String readString(SharedPreferences prefs, Context context, String defaultVal) {
                return prefs.getString(getPrefKeyInProfile(context, prefKey), defaultVal);
            }
            
            public String readStringEncrypted(SharedPreferences prefs, Context context, String defaultVal) {
                if(securePrefUtil == null) {
                    securePrefUtil = SecurePrefsUtil.getInstance(context, BuildConfig.APPLICATION_ID);
                }
                return securePrefUtil.readSecureStringPreference(context, prefs, getPrefKeyInProfile(context, prefKey), defaultVal);
            }

            public boolean readBoolean(SharedPreferences prefs, Context context, boolean defaultVal) {
                return prefs.getBoolean(getPrefKeyInProfile(context, prefKey), defaultVal);
            }

            /**
             * If active profile, updates in-use copy as well as actual profile
             * @param prefs
             * @param context
             * @param newValue
             */
            public void writeBoolean(SharedPreferences prefs, Context context, boolean newValue) {
                SharedPreferences.Editor editor = prefs.edit();
                writeBoolean(editor, context, newValue);
                editor.commit();
            }

            /**
             * If active profile, updates in-use copy as well as actual profile
             * 
             * @param editor
             * @param context
             * @param newValue
             * @return editor
             */
            public SharedPreferences.Editor writeBoolean(SharedPreferences.Editor editor, Context context, boolean newValue) {

                editor.putBoolean(getPrefKeyInProfile(context, prefKey), newValue);
                return editor;
            }

            public SharedPreferences.Editor writeInt(SharedPreferences.Editor editor, Context context, int newValue) {

                editor.putInt(getPrefKeyInProfile(context, prefKey), newValue);
                return editor;
            }

            private boolean isActiveProfile() {
                return this.profileId == null || this.profileId.length() == 0;
            }

            public String getPrefKeyInProfile(Context context, @StringRes int keyId) {
                if (!isActiveProfile()) {
                    return profileId + ':' + context.getString(keyId);
                }
                return context.getString(keyId);
            }


            public int readInt(SharedPreferences prefs, Context context, int defaultVal) {
                return prefs.getInt(getPrefKeyInProfile(context, prefKey), defaultVal);
            }

            public SharedPreferences.Editor writeStringSet(SharedPreferences.Editor editor, Context context, Set<String> newValue) {
                if(newValue != null) {
                    editor.putStringSet(getPrefKeyInProfile(context, prefKey), new HashSet<>(newValue));
                } else {
                    editor.remove(getPrefKeyInProfile(context, prefKey));
                }
                return editor;
            }

            public void remove(SharedPreferences.Editor editor, Context context) {
                editor.remove(getPrefKeyInProfile(context, prefKey));
            }

            public boolean isForActiveProfile() {
                return profileId == null;
            }
        }

        public void setFollowHttpRedirects(SharedPreferences prefs, Context context, boolean newValue) {
            getPrefActor().with(R.string.preference_server_connection_allow_redirects_key).writeBoolean(prefs, context, newValue);
        }

        public void setWarnInternalUriExposed(SharedPreferences prefs, Context context, boolean newValue) {
            getPrefActor().with(R.string.preference_server_connection_warn_internal_uri_exposed_key).writeBoolean(prefs, context, newValue);
        }
        public boolean isWarnInternalUriExposed(SharedPreferences prefs, Context context) {
            return getPrefActor().with(R.string.preference_server_connection_warn_internal_uri_exposed_key).readBoolean(prefs, context, true);
        }

        public int getMaxHttpRedirects(SharedPreferences prefs, Context context) {
            int defaultVal = context.getResources().getInteger(R.integer.preference_server_connection_max_redirects_default);
            return getPrefActor().with(R.string.preference_server_connection_max_redirects_key).readInt(prefs, context, defaultVal);
        }

        public Set<String> getKnownMultimediaExtensions(SharedPreferences prefs, Context context) {
            Set<String> value = getPrefActor().with(R.string.preference_piwigo_playable_media_extensions_key).readStringSet(prefs, context, null);
            if (value == null) {
                value = new HashSet<>();
                Collections.addAll(value, context.getResources().getStringArray(R.array.preference_piwigo_playable_media_extensions_default));
            }
            return value;
        }

        public int getMaxServerConnectRetries(SharedPreferences prefs, Context context) {
            int defaultVal = context.getResources().getInteger(R.integer.preference_server_connection_retries_default);
            return getPrefActor().with(R.string.preference_server_connection_retries_key).readInt(prefs, context, defaultVal);
        }

        public int getServerConnectTimeout(SharedPreferences prefs, Context context) {
            int defaultVal = context.getResources().getInteger(R.integer.preference_server_connection_timeout_secs_default);
            return getPrefActor().with(R.string.preference_server_connection_timeout_secs_key).readInt(prefs, context, defaultVal);
        }

        public int getServerResponseTimeout(SharedPreferences prefs, Context context) {
            int defaultVal = context.getResources().getInteger(R.integer.preference_server_response_timeout_secs_default);
            return getPrefActor().with(R.string.preference_server_response_timeout_secs_key).readInt(prefs, context, defaultVal);
        }

        public boolean isIgnoreServerCacheDirectives(SharedPreferences prefs, Context context) {
            boolean defaultVal = context.getResources().getBoolean(R.bool.preference_server_alter_cache_directives_default);
            return getPrefActor().with(R.string.preference_server_alter_cache_directives_key).readBoolean(prefs, context, defaultVal);
        }

        public boolean isPerformUriPathSegmentEncoding(SharedPreferences prefs, Context context) {
            boolean defaultVal = context.getResources().getBoolean(R.bool.preference_server_connection_uri_path_segment_encoding_default);
            return getPrefActor().with(R.string.preference_server_connection_uri_path_segment_encoding_key).readBoolean(prefs, context, defaultVal);
        }

        public void copyFrom(SharedPreferences prefs, Context context, ProfilePreferences fromPrefs) {

            SharedPreferences.Editor editor = prefs.edit();

            getPrefActor().with(SecurePrefsUtil.getInstance(context, BuildConfig.APPLICATION_ID));
            
            // piwigo server connection details
            getPrefActor().with(R.string.preference_piwigo_server_address_key).writeString(editor, context, fromPrefs.getPiwigoServerAddress(prefs, context));
            getPrefActor().with(R.string.preference_piwigo_server_username_key).writeStringEncrypted(editor, context, fromPrefs.getPiwigoUsername(prefs, context));
            getPrefActor().with(R.string.preference_piwigo_server_password_key).writeStringEncrypted(editor, context, fromPrefs.getPiwigoPassword(prefs, context));

            // piwigo server specific details.
            getPrefActor().with(R.string.preference_gallery_unique_id_key).writeString(editor, context, fromPrefs.getPiwigoUniqueResourceKey(prefs, context));
            getPrefActor().with(R.string.preference_piwigo_playable_media_extensions_key).writeStringSet(editor, context, fromPrefs.getKnownMultimediaExtensions(prefs, context));

            // fine grained http connection configuration bits and bobs
            getPrefActor().with(R.string.preference_server_connection_allow_redirects_key).writeBoolean(editor, context, fromPrefs.getFollowHttpRedirects(prefs, context));
            getPrefActor().with(R.string.preference_server_connection_max_redirects_key).writeInt(editor, context, fromPrefs.getMaxHttpRedirects(prefs, context));
            getPrefActor().with(R.string.preference_server_connection_retries_key).writeInt(editor, context, fromPrefs.getMaxServerConnectRetries(prefs, context));
            getPrefActor().with(R.string.preference_server_connection_timeout_secs_key).writeInt(editor, context, fromPrefs.getServerConnectTimeout(prefs, context));
            getPrefActor().with(R.string.preference_server_response_timeout_secs_key).writeInt(editor, context, fromPrefs.getServerResponseTimeout(prefs, context));

            getPrefActor().with(R.string.preference_server_ssl_certificate_hostname_verification_key).writeString(editor, context, fromPrefs.getCertificateHostnameVerificationLevel(prefs, context));
            getPrefActor().with(R.string.preference_server_alter_cache_directives_key).writeBoolean(editor, context, fromPrefs.isIgnoreServerCacheDirectives(prefs, context));
            getPrefActor().with(R.string.preference_server_connection_force_https_key).writeBoolean(editor, context, fromPrefs.isForceHttps(prefs, context));

            getPrefActor().with(R.string.preference_server_connection_offline_mode_key).writeBoolean(editor, context, fromPrefs.isOfflineMode(prefs, context));
            getPrefActor().with(R.string.preference_server_connection_uri_path_segment_encoding_key).writeBoolean(editor, context, fromPrefs.isPerformUriPathSegmentEncoding(prefs, context));

            getPrefActor().with(R.string.preference_server_connection_warn_internal_uri_exposed_key).writeBoolean(editor, context, fromPrefs.isWarnInternalUriExposed(prefs, context));

            // received server certs list
            getPrefActor().with(R.string.preference_pre_user_notified_certificates_key).writeStringSet(editor, context, fromPrefs.getUserPreNotifiedCerts(prefs, context));

            // pinned server certs
            getPrefActor().with(R.string.preference_server_use_custom_trusted_ca_certs_key).writeBoolean(editor, context, fromPrefs.getUsePinnedServerCertificates(prefs, context));

            // client certs
            getPrefActor().with(R.string.preference_server_use_client_certs_key).writeBoolean(editor, context, fromPrefs.getUseClientCertificates(prefs, context));

            // Basic authentication
            getPrefActor().with(R.string.preference_server_use_basic_auth_key).writeBoolean(editor, context, fromPrefs.getUseBasicAuthentication(prefs, context));
            getPrefActor().with(R.string.preference_server_basic_auth_username_key).writeStringEncrypted(editor, context, fromPrefs.getBasicAuthenticationUsername(prefs, context));
            getPrefActor().with(R.string.preference_server_basic_auth_password_key).writeStringEncrypted(editor, context, fromPrefs.getBasicAuthenticationPassword(prefs, context));

//            editor.apply();
            editor.commit();
        }


        public void writeSecurePref(SharedPreferences.Editor editor, SecurePrefsUtil prefUtil, String key, String plainTextValue) {
            String encryptedValue = prefUtil.encryptValue(key, plainTextValue);
            editor.putString(key, encryptedValue);
        }

        public void delete(SharedPreferences prefs, Context context) {
            SharedPreferences.Editor editor = prefs.edit();

            // piwigo server connection details
            getPrefActor().with(R.string.preference_piwigo_server_address_key).remove(editor, context);
            getPrefActor().with(R.string.preference_piwigo_server_username_key).remove(editor, context);
            getPrefActor().with(R.string.preference_piwigo_server_password_key).remove(editor, context);

            // piwigo server specific details.
            getPrefActor().with(R.string.preference_gallery_unique_id_key).remove(editor, context);
            getPrefActor().with(R.string.preference_piwigo_playable_media_extensions_key).remove(editor, context);

            // fine grained http connection configuration bits and bobs
            getPrefActor().with(R.string.preference_server_connection_allow_redirects_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_connection_max_redirects_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_connection_retries_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_connection_timeout_secs_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_response_timeout_secs_key).remove(editor, context);

            getPrefActor().with(R.string.preference_server_ssl_certificate_hostname_verification_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_alter_cache_directives_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_connection_force_https_key).remove(editor, context);

            getPrefActor().with(R.string.preference_server_connection_offline_mode_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_connection_uri_path_segment_encoding_key).remove(editor, context);

            getPrefActor().with(R.string.preference_server_connection_warn_internal_uri_exposed_key).remove(editor, context);

            // received server certs list
            getPrefActor().with(R.string.preference_pre_user_notified_certificates_key).remove(editor, context);

            // pinned server certs
            getPrefActor().with(R.string.preference_server_use_custom_trusted_ca_certs_key).remove(editor, context);

            // client certs
            getPrefActor().with(R.string.preference_server_use_client_certs_key).remove(editor, context);

            // Basic authentication
            getPrefActor().with(R.string.preference_server_use_basic_auth_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_basic_auth_username_key).remove(editor, context);
            getPrefActor().with(R.string.preference_server_basic_auth_password_key).remove(editor, context);

            editor.apply();
            editor.commit();
        }

        public boolean isValid(SharedPreferences prefs, Context context) {
            return ("".equals(prefix) || getConnectionProfileList(prefs, context).contains(prefix)) && getPiwigoServerAddress(prefs, context) != null;
        }

        public boolean isValid(Context context) {
            SharedPreferences overallSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
            return isValid(overallSharedPreferences, context);
        }
    }

}
