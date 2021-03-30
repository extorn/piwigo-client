package delit.piwigoclient.subscription.api;

public interface ConnectionListener {
    void onConnectionAcquired();
    void onConnectionLost();
}
