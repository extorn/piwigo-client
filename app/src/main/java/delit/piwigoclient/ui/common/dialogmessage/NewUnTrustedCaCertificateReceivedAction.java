package delit.piwigoclient.ui.common.dialogmessage;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.X509Utils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.common.UIHelper;

public class NewUnTrustedCaCertificateReceivedAction<P extends UIHelper<P,T>, T> extends QuestionResultAdapter<P,T> implements Parcelable {

    private final HashMap<String, X509Certificate> untrustedCerts;

    public NewUnTrustedCaCertificateReceivedAction(P uiHelper, HashMap<String, X509Certificate> untrustedCerts) {
        super(uiHelper);
        this.untrustedCerts = untrustedCerts;
    }

    protected NewUnTrustedCaCertificateReceivedAction(Parcel in) {
        super(in);
        untrustedCerts = ParcelUtils.readMap(in, X509Certificate.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeMap(dest, untrustedCerts);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<NewUnTrustedCaCertificateReceivedAction<?,?>> CREATOR = new Creator<NewUnTrustedCaCertificateReceivedAction<?,?>>() {
        @Override
        public NewUnTrustedCaCertificateReceivedAction<?,?> createFromParcel(Parcel in) {
            return new NewUnTrustedCaCertificateReceivedAction<>(in);
        }

        @Override
        public NewUnTrustedCaCertificateReceivedAction<?,?>[] newArray(int size) {
            return new NewUnTrustedCaCertificateReceivedAction<?,?>[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {

            final Set<String> preNotifiedCerts = new HashSet<>(Objects.requireNonNull(getUiHelper().getPrefs().getStringSet(getContext().getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<>())));
            if (preNotifiedCerts.containsAll(untrustedCerts.keySet())) {
                // already dealt with this
                return;
            }

            KeyStore trustStore = X509Utils.loadTrustedCaKeystore(getContext());
            try {
                for (Map.Entry<String, X509Certificate> entry : untrustedCerts.entrySet()) {
                    trustStore.setCertificateEntry(entry.getKey(), entry.getValue());
                }
                X509Utils.saveTrustedCaKeystore(getContext(), trustStore);
            } catch (KeyStoreException e) {
                Logging.recordException(e);
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.alert_error_adding_certificate_to_truststore));
            }
            preNotifiedCerts.addAll(untrustedCerts.keySet());
            new ConnectionPreferences.PreferenceActor().with(R.string.preference_pre_user_notified_certificates_key).writeStringSet(getUiHelper().getPrefs(), getContext(), preNotifiedCerts);
            long messageId = new HttpConnectionCleanup(ConnectionPreferences.getActiveProfile(), getContext(), true).start();
            PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, new BasicPiwigoResponseListener<P,T>() {
                @Override
                public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
                    getUiHelper().showDetailedMsg(R.string.alert_information, getContext().getString(R.string.alert_http_engine_shutdown));
                }
            });
        }
    }

    @Override
    public void onShow(AlertDialog alertDialog) {

    }
}
