package delit.piwigoclient.ui.events;

public class RewardUpdateEvent extends SingleUseEvent {

    long rewardTimeRemaining;

    public RewardUpdateEvent(long rewardTimeRemaining) {
        this.rewardTimeRemaining = rewardTimeRemaining;
    }

    public long getRewardTimeRemaining() {
        return rewardTimeRemaining;
    }
}
