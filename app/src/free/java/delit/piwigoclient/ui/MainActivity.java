package delit.piwigoclient.ui;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity<A extends MainActivity<A, AUIH>, AUIH extends ActivityUIHelper<AUIH, A>> extends AbstractMainActivity<A, AUIH> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        AdsManager.getInstance(this).pauseRewardVideoAd(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        AdsManager.getInstance(this).resumeRewardVideoAd(this);
        super.onResume();
        // try and load a reward ad here - the ad service may have finished initialising now.
        AdsManager.getInstance(this).createRewardedVideoAd(this, REWARD_COUNT_UPDATE_FREQUENCY);
    }

    @Override
    protected void onDestroy() {
        AdsManager.getInstance(this).destroyRewardVideoAd(this);
        super.onDestroy();
    }

    @Override
    protected void onNavigationItemSelected(NavigationItemSelectEvent event, int itemId) {
        int id = event.navigationitemSelected;
        if (id == R.id.nav_buy_time) {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_question_title, getString(R.string.watch_advert_to_buy_advert_free_time), R.string.button_no, R.string.button_yes, new BuyAdvertFreeTimeQuestionListener<>(getUiHelper()));
        }
    }

    protected void showRewardVideo() {
        if (!AdsManager.getInstance(this).showRewardVideoAd(this)) {
            getUiHelper().showShortMsg(R.string.reward_video_not_yet_loaded);
            if(!AdsManager.getInstance(this).hasRewardVideoAd(this)) {
                AdsManager.getInstance(this).createRewardedVideoAd(this, REWARD_COUNT_UPDATE_FREQUENCY);
            }
        }
    }

    private static class BuyAdvertFreeTimeQuestionListener<A extends MainActivity<A, AUIH>, AUIH extends ActivityUIHelper<AUIH, A>> extends QuestionResultAdapter<AUIH,A> implements Parcelable {

        public BuyAdvertFreeTimeQuestionListener(AUIH uiHelper) {
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

        public static final Creator<BuyAdvertFreeTimeQuestionListener<?,?>> CREATOR = new Creator<BuyAdvertFreeTimeQuestionListener<?,?>>() {
            @Override
            public BuyAdvertFreeTimeQuestionListener<?,?> createFromParcel(Parcel in) {
                return new BuyAdvertFreeTimeQuestionListener<>(in);
            }

            @Override
            public BuyAdvertFreeTimeQuestionListener<?,?>[] newArray(int size) {
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
    protected void showOrphans() {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_feature_only), R.string.button_close);
    }

    @Override
    protected void showTags() {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_feature_only), R.string.button_close);
    }
}