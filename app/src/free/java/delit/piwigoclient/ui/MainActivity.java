package delit.piwigoclient.ui;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity extends AbstractMainActivity<MainActivity> {

    private AdsManager.MyRewardedAdControl rewardedVideoAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        rewardedVideoAd = AdsManager.getInstance().getRewardedVideoAd(this, new AdsManager.OnRewardEarnedListener(this, REWARD_COUNT_UPDATE_FREQUENCY, BuildConfig.APPLICATION_ID));
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        rewardedVideoAd.pause(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        rewardedVideoAd.resume(this);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        rewardedVideoAd.destroy(this);
        super.onDestroy();
    }

    @Override
    protected void onNavigationItemSelected(NavigationItemSelectEvent event, int itemId) {
        int id = event.navigationitemSelected;
        if (id == R.id.nav_buy_time) {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_question_title, getString(R.string.watch_advert_to_buy_advert_free_time), R.string.button_no, R.string.button_yes, new BuyAdvertFreeTimeQuestionListener(getUiHelper()));
        }
    }

    protected void showRewardVideo() {
        if (!rewardedVideoAd.show()) {
            getUiHelper().showShortMsg(R.string.reward_video_not_yet_loaded);
        }
    }

    private static class BuyAdvertFreeTimeQuestionListener<T extends ActivityUIHelper<R>,R extends MainActivity> extends UIHelper.QuestionResultAdapter<T,R> implements Parcelable {

        public BuyAdvertFreeTimeQuestionListener(T uiHelper) {
            super(uiHelper);
        }

        protected BuyAdvertFreeTimeQuestionListener(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<BuyAdvertFreeTimeQuestionListener> CREATOR = new Creator<BuyAdvertFreeTimeQuestionListener>() {
            @Override
            public BuyAdvertFreeTimeQuestionListener createFromParcel(Parcel in) {
                return new BuyAdvertFreeTimeQuestionListener(in);
            }

            @Override
            public BuyAdvertFreeTimeQuestionListener[] newArray(int size) {
                return new BuyAdvertFreeTimeQuestionListener[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE.equals(positiveAnswer)) {
                getUiHelper().getParent().showRewardVideo();
            }
        }
    }

    @Override
    protected void showFavorites() {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_feature_only), R.string.button_close);
    }

    @Override
    protected void showTags() {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_feature_only), R.string.button_close);
    }
}