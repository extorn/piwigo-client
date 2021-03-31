package delit.piwigoclient.ui.album.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;

import java.util.Set;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.slideshow.action.SelectionContainsUnsuitableFilesQuestionResult;
import delit.piwigoclient.ui.slideshow.item.AbstractSlideshowItemFragment;
import delit.piwigoclient.ui.slideshow.item.DownloadSelectionMultiItemDialog;

public class MyDownloadSelectionMultiItemListener<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> implements DownloadSelectionMultiItemDialog.DownloadSelectionMultiItemListener {

    private final FUIH uiHelper;
    private final Context context;

    public MyDownloadSelectionMultiItemListener(Context context, FUIH uiHelper) {
        this.context = context;
        this.uiHelper = uiHelper;
    }

    public FUIH getUiHelper() {
        return uiHelper;
    }

    @Override
    public void onDownload(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
        if(filesUnavailableToDownload.size() > 0) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, context.getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new SelectionContainsUnsuitableFilesQuestionResult<F,FUIH,T>(getUiHelper(), items, selectedPiwigoFilesizeName));
        } else {
            new AbstractSlideshowItemFragment.BaseDownloadQuestionResult<>(getUiHelper()).doDownloadAction(items, selectedPiwigoFilesizeName, false);
        }
    }

    @Override
    public void onShare(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
        if(filesUnavailableToDownload.size() > 0) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, context.getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new SelectionContainsUnsuitableFilesQuestionResult<F,FUIH,T>(getUiHelper(), items, selectedPiwigoFilesizeName));
        } else {
            new AbstractSlideshowItemFragment.BaseDownloadQuestionResult<>(getUiHelper()).doDownloadAction(items, selectedPiwigoFilesizeName, true);
        }
    }

    @Override
    public void onCopyLink(Context context, Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
        ResourceItem item = items.iterator().next();
        String resourceName = item.getName();
        ResourceItem.ResourceFile resourceFile = item.getFile(selectedPiwigoFilesizeName);
        Uri uri = Uri.parse(item.getFileUrl(resourceFile.getName()));
        ClipboardManager mgr = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if(mgr != null) {
            ClipData clipData = ClipData.newRawUri(context.getString(R.string.download_link_clipboard_data_desc, resourceName), uri);
            mgr.setPrimaryClip(clipData);
            getUiHelper().showShortMsg(R.string.copied_to_clipboard);
        } else {
            Logging.logAnalyticEvent(context,"NoClipMgr", null);
        }
    }
}
