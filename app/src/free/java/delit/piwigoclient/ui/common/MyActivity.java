package delit.piwigoclient.ui.common;

import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import delit.piwigoclient.ui.AdsManager;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyActivity<T extends MyActivity<T>> extends BaseMyActivity {
    protected static final long REWARD_COUNT_UPDATE_FREQUENCY = 1000;
    private AdsManager.RewardCountDownAction rewardsCountdownAction;

    public MyActivity(@LayoutRes int contentView) {
        super(contentView);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rewardsCountdownAction = AdsManager.RewardCountDownAction.getInstance(getBaseContext(), REWARD_COUNT_UPDATE_FREQUENCY);
        AdsManager.getInstance(this).updateShowAdvertsSetting(this);
    }

    @Override
    protected void onAppPaused() {
        if (rewardsCountdownAction != null) {
            rewardsCountdownAction.stop();
        }
    }

    @Override
    protected void onAppResumed() {
        rewardsCountdownAction.start();
    }
}
