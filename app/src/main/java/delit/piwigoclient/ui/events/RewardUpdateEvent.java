package delit.piwigoclient.ui.events;

import delit.libs.ui.events.SingleUseEvent;

public class RewardUpdateEvent extends SingleUseEvent {

    long rewardTimeRemaining;

    public RewardUpdateEvent(long rewardTimeRemaining) {
        this.rewardTimeRemaining = rewardTimeRemaining;
    }

    public long getRewardTimeRemaining() {
        return rewardTimeRemaining;
    }
}
