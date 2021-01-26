package delit.piwigoclient.ui.util.download;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.view.View;

import androidx.documentfile.provider.DocumentFile;

import org.greenrobot.eventbus.EventBus;

import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.AbstractMainActivity;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.CancelDownloadEvent;
import delit.piwigoclient.ui.events.DownloadFileRequestEvent;
import delit.piwigoclient.util.MyDocumentProvider;

import static android.view.View.VISIBLE;

public class DownloadAction<UIH extends UIHelper<UIH,T>, T> extends UIHelper.Action<UIH, T, PiwigoResponseBufferingHandler.Response> implements Parcelable {

    private DownloadActionListener listener;
    private final DownloadFileRequestEvent downloadEvent;

    public interface DownloadActionListener {
        void onDownloadActionSuccess(DownloadFileRequestEvent downloadEvent, Uri downloadedToUri);
        void onDownloadActionFailure();
    }

    public DownloadAction(DownloadFileRequestEvent event, DownloadActionListener listener) {
        super();
        downloadEvent = event;
        this.listener = listener;
    }

    protected DownloadAction(Parcel in) {
        super(in);
        downloadEvent = in.readParcelable(DownloadFileRequestEvent.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(downloadEvent, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DownloadAction<?,?>> CREATOR = new Creator<DownloadAction<?,?>>() {
        @Override
        public DownloadAction<?,?> createFromParcel(Parcel in) {
            return new DownloadAction<>(in);
        }

        @Override
        public DownloadAction<?,?>[] newArray(int size) {
            return new DownloadAction[size];
        }
    };

    @Override
    public boolean onSuccess(UIH uiHelper, PiwigoResponseBufferingHandler.Response response) {
        //UrlProgressResponse, UrlToFileSuccessResponse,
        if (response instanceof PiwigoResponseBufferingHandler.UrlProgressResponse) {
            onProgressUpdate(uiHelper, (PiwigoResponseBufferingHandler.UrlProgressResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.UrlToFileSuccessResponse) {
            onGetResource(downloadEvent, uiHelper, (PiwigoResponseBufferingHandler.UrlToFileSuccessResponse) response);
        }
        return super.onSuccess(uiHelper, response);
    }

    @Override
    public boolean onFailure(UIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
        if (response instanceof PiwigoResponseBufferingHandler.UrlCancelledResponse) {
            onGetResourceCancelled(uiHelper, (PiwigoResponseBufferingHandler.UrlCancelledResponse) response);
        }
        if (response.isEndResponse()) {
            //TODO handle the failure and retry here so we can keep the activeDownloads field in sync properly. Presently two downloads may occur simulataneously.
            listener.onDownloadActionFailure();
        }
        return super.onFailure(uiHelper, response);
    }

    private void onProgressUpdate(UIH uiHelper, final PiwigoResponseBufferingHandler.UrlProgressResponse response) {
        ProgressIndicator progressIndicator = uiHelper.getProgressIndicator();
        if (response.getProgress() < 0) {
            progressIndicator.showProgressIndicator(R.string.progress_downloading, -1);
        } else {
            if (response.getProgress() == 0) {
                progressIndicator.showProgressIndicator(R.string.progress_downloading, response.getProgress(), new CancelDownloadListener(response.getMessageId()));
            } else if (progressIndicator.getVisibility() == VISIBLE) {
                progressIndicator.updateProgressIndicator(response.getProgress());
            }
        }
    }

    public void onGetResource(DownloadFileRequestEvent downloadEvent, UIH uiHelper, final PiwigoResponseBufferingHandler.UrlToFileSuccessResponse response) {
        Uri myDocUri = response.getLocalFileUri();
        if(!DocumentFile.isDocumentUri(uiHelper.getAppContext(),response.getLocalFileUri())) {
            myDocUri = DocumentsContract.buildDocumentUri(MyDocumentProvider.getAuthority(), IOUtils.getFilename(uiHelper.getAppContext(), response.getLocalFileUri()));
        }
//            Uri mediaStoreUri = IOUtils.addFileToMediaStore(uiHelper.getAppContext(), myDocUri);
//            downloadEvent.markDownloaded(response.getUrl(), mediaStoreUri);
        this.downloadEvent.markDownloaded(response.getUrl(), myDocUri);
        listener.onDownloadActionSuccess(downloadEvent, myDocUri);

    }

    private void onGetResourceCancelled(UIH uiHelper, PiwigoResponseBufferingHandler.UrlCancelledResponse response) {
        uiHelper.showDetailedMsg(R.string.alert_information, uiHelper.getAppContext().getString(R.string.alert_image_download_cancelled_message));
    }

    private static class CancelDownloadListener implements View.OnClickListener {
        private final long downloadMessageId;

        public CancelDownloadListener(long messageId) {
            downloadMessageId = messageId;
        }

        @Override
        public void onClick(View v) {
            EventBus.getDefault().post(new CancelDownloadEvent(downloadMessageId));
        }
    }
}
