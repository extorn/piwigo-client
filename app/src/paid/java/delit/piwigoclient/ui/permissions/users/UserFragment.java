package delit.piwigoclient.ui.permissions.users;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

/**
 * Created by gareth on 21/06/17.
 */

public class UserFragment<F extends UserFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BaseUserFragment<F,FUIH> {

    public static UserFragment<?,?> newInstance(User user) {
        UserFragment<?,?> fragment = new UserFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        Bundle args = new Bundle();
        args.putParcelable(ARG_USER, user);
        fragment.setArguments(args);
        return fragment;
    }

    public void generateDeepLinkEmail(Uri deepLinkUri) {
        Context context = requireContext();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain"); // send email as plain text
        if(getUser().getEmail() != null) {
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getUser().getEmail()});
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, "PIWIGO Client");
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.piwigo_deep_link_login_email_pattern, deepLinkUri.toString()));
        context.startActivity(Intent.createChooser(intent, "Email Text"));
    }

    public void sendDeepLinkToAndroidNow(Uri deepLinkUri) {
        startActivity(new Intent(Intent.ACTION_VIEW, deepLinkUri));
    }

    public void sendDeepLinkToClipboard(Uri deepLinkUri) {
        Context context = requireContext();
        // copy link
        ClipboardManager mgr = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if(mgr != null) {
            ClipData clipData = ClipData.newRawUri(context.getString(R.string.download_link_clipboard_data_desc, "PiwigologinLink"), deepLinkUri);
            mgr.setPrimaryClip(clipData);
            getUiHelper().showShortMsg(R.string.copied_to_clipboard);
        } else {
            Logging.logAnalyticEvent(context,"NoClipMgr", null);
        }
    }

    @Override
    protected void onBeforeSave(User newUser) {
        actionSendDeepLinkToUserIfWanted(newUser);
    }

    private void actionSendDeepLinkToUserIfWanted(User newUser) {
        String serverUri = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).getServerUrl();
        String username = newUser.getUserType().equals("guest") ? null : newUser.getUsername();
        Uri deepLink = ConnectionPreferences.generateDeepLinkSettingsChange(requireContext(), serverUri, username, newUser.getPassword());
        if(newUser.getPassword() != null) {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_information, getString(R.string.deep_link_warning_password),
                    R.string.button_cancel, R.string.button_ok, new CreateDeepLinkWantedListener<>(getUiHelper(), deepLink));
        } else {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_information, getString(R.string.deep_link_warning_no_password),
                    R.string.button_cancel, R.string.button_ok, new CreateDeepLinkWantedListener<>(getUiHelper(), deepLink));
        }
    }

    public void onRequestUserActionDeepLinkDestination(Uri deepLink) {
        getUiHelper().showOrQueueTriButtonDialogQuestion(R.string.alert_information, getString(R.string.deep_link_explanation),
                R.string.button_this_app, R.string.button_clipboard, R.string.button_email,
                new DeepLinkQuestionListener<>(getUiHelper(), deepLink));
    }

    public static class DeepLinkQuestionListener<F extends UserFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        private Uri deepLink;

        public DeepLinkQuestionListener(FUIH uiHelper, Uri deepLink) {
            super(uiHelper);
            this.deepLink = deepLink;
        }

        protected DeepLinkQuestionListener(Parcel in) {
            super(in);
            deepLink = ParcelUtils.readParcelable(in, Uri.class);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeParcelable(dest, deepLink);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        protected void onNegativeResult(AlertDialog dialog) {
            // Test the link in this app
            getParent().sendDeepLinkToAndroidNow(deepLink);
        }

        @Override
        protected void onPositiveResult(AlertDialog dialog) {
            getParent().generateDeepLinkEmail(deepLink);
        }

        @Override
        protected void onNeutralResult(AlertDialog dialog) {
            getParent().sendDeepLinkToClipboard(deepLink);
        }

        public static final Creator<DeepLinkQuestionListener<?,?>> CREATOR = new Creator<DeepLinkQuestionListener<?,?>>() {
            @Override
            public DeepLinkQuestionListener<?,?> createFromParcel(Parcel in) {
                return new DeepLinkQuestionListener<>(in);
            }

            @Override
            public DeepLinkQuestionListener<?,?>[] newArray(int size) {
                return new DeepLinkQuestionListener[size];
            }
        };
    }

    private static class CreateDeepLinkWantedListener<F extends UserFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        private Uri deepLink;

        public CreateDeepLinkWantedListener(FUIH uiHelper, Uri deepLink) {
            super(uiHelper);
            this.deepLink = deepLink;
        }

        protected CreateDeepLinkWantedListener(Parcel in) {
            super(in);
            deepLink = ParcelUtils.readParcelable(in, Uri.class);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeParcelable(dest, deepLink);
        }

        @Override
        protected void onPositiveResult(AlertDialog dialog) {
            getParent().onRequestUserActionDeepLinkDestination(deepLink);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<CreateDeepLinkWantedListener<?,?>> CREATOR = new Creator<CreateDeepLinkWantedListener<?,?>>() {
            @Override
            public CreateDeepLinkWantedListener<?,?> createFromParcel(Parcel in) {
                return new CreateDeepLinkWantedListener<>(in);
            }

            @Override
            public CreateDeepLinkWantedListener<?,?>[] newArray(int size) {
                return new CreateDeepLinkWantedListener[size];
            }
        };
    }
}
