package delit.piwigoclient.ui;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import com.google.android.gms.ads.reward.RewardedVideoAd;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity extends AbstractMainActivity<MainActivity> {

    private RewardedVideoAd rewardedVideoAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        rewardedVideoAd = AdsManager.getInstance().getRewardedVideoAd(this, new AdsManager.OnRewardEarnedListener(this, REWARD_COUNT_UPDATE_FREQUENCY));
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
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
    protected void onRestart() {
        super.onRestart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    protected void onNavigationItemSelected(NavigationItemSelectEvent event, int itemId) {
        int id = event.navigationitemSelected;
        if (id == R.id.nav_buy_time) {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_question_title, getString(R.string.watch_advert_to_buy_advert_free_time), R.string.button_no, R.string.button_yes, new BuyAdvertFreeTimeQuestionListener(getUiHelper()));
        }
    }

    private void showRewardVideo() {
        if (rewardedVideoAd.isLoaded()) {
            rewardedVideoAd.show();
        } else {
            rewardedVideoAd.getRewardedVideoAdListener().onRewardedVideoAdClosed(); // trigger a reload
            getUiHelper().showShortMsg(R.string.reward_video_not_yet_loaded);
        }
    }

    private static class BuyAdvertFreeTimeQuestionListener<T extends ActivityUIHelper<MainActivity>> extends UIHelper.QuestionResultAdapter<T> {

        public BuyAdvertFreeTimeQuestionListener(T uiHelper) {
            super(uiHelper);
        }

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