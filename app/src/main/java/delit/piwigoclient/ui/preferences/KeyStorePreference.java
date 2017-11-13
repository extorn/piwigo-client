package delit.piwigoclient.ui.preferences;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.events.trackable.FileListSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileListSelectionNeededEvent;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 15/07/17.
 */

public abstract class KeyStorePreference extends DialogPreference {
    private final String tag;
    private boolean justKeysWanted;
    private KeyStore mValue;
    private boolean mValueSet;
    private RecyclerView certificateList;
    private int trackedRequest = -1;
    private ProgressDialog progressDialog;
    private ArrayList<String> allowedCertificateFileTypes = new ArrayList<>(Arrays.asList(new String[]{".cer", ".cert", ".pem"}));
    private ArrayList<String> allowedKeyFileTypes = new ArrayList<>(Arrays.asList(new String[]{".p12", ".pkcs12", ".pfx"}));

    public KeyStorePreference(String tag, Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.tag = tag;
    }

    public KeyStorePreference(String tag, Context context, AttributeSet attrs) {
        this(tag, context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public KeyStorePreference(String tag, Context context) {
        this(tag, context, null);
    }

    public void setJustKeysWanted(boolean keysWanted) {
        this.justKeysWanted = keysWanted;
    }

    public void setAllowedCertificateFileTypes(ArrayList<String> allowedCertificateFileTypes) {
        for (final ListIterator<String> i = allowedCertificateFileTypes.listIterator(); i.hasNext();) {
            final String element = i.next();
            i.set(element.toLowerCase());
        }
        this.allowedCertificateFileTypes = allowedCertificateFileTypes;
    }

    public void setAllowedKeyFileTypes(ArrayList<String> allowedKeyFileTypes) {
        for (final ListIterator<String> i = allowedCertificateFileTypes.listIterator(); i.hasNext();) {
            final String element = i.next();
            i.set(element.toLowerCase());
        }
        this.allowedKeyFileTypes = allowedKeyFileTypes;
    }

    /**
     * Returns the value of the key.
     *
     * @return The value of the key.
     */
    public KeyStore getValue() {
        return mValue;
    }

    /**
     * Sets the value of the key.
     *
     * @param value The value to set for the key.
     */
    public void setValue(KeyStore value) {
        // Always persist/notify the first time.
        boolean changed = !X509Utils.areEqual(mValue, value);
        if (!mValueSet || changed) {
            mValue = value;
            mValueSet = true;
            saveKeystore(mValue);
            if (changed) {
                notifyChanged();
            }
        }
    }

    protected abstract void saveKeystore(KeyStore keystore);

    protected abstract KeyStore loadKeystore();

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        mValue = loadKeystore();
        View view = buildCertificateListView();
        builder.setView(view);
    }

    private View buildCertificateListView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_fullsize_recycler_list, null, false);

        AdView adView = (AdView)view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);

        TextView heading = (TextView) view.findViewById(R.id.heading);
        heading.setText(R.string.certificates_heading);
        heading.setVisibility(View.VISIBLE);

        certificateList = (RecyclerView) view.findViewById(R.id.list);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        certificateList.setLayoutManager(mLayoutManager);
        KeyStoreContentsAdapter adapter = new KeyStoreContentsAdapter(getContext(), X509Utils.cloneKeystore(mValue));
        certificateList.setAdapter(adapter);

        CustomImageButton addListItemButton = (CustomImageButton) view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.VISIBLE);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewCertificate();
            }
        });

        Button saveChangesButton = (Button) view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);

        return view;
    }

    private void buildProgressDialog(Context context) {
        progressDialog = new ProgressDialog(context);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setTitle(R.string.alert_loading_certificates_from_selected_files);
    }

    public void onCertificatesSelected(ArrayList<File> certificateFiles) {
        AsyncTask runningTask = new AsyncTask<File, Integer, Map<Key, Certificate[]>>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

            }

            @Override
            protected Map<Key, Certificate[]> doInBackground(File ... certificateFiles) {
                HashMap<Key, Certificate[]> keystoreContents = new HashMap<>(certificateFiles.length);
                int currentFile = 0;
                for (File f : certificateFiles) {
                    String fileSuffix = f.getName().replaceFirst(".*(\\.[^.]*)","$1").toLowerCase();
                    if (allowedCertificateFileTypes.contains(fileSuffix)) {
                        X509Certificate cer = X509Utils.loadCertificateFromFile(f);
                        keystoreContents.put(cer.getPublicKey(), new X509Certificate[]{cer});
                    } else if (allowedKeyFileTypes.contains(fileSuffix)) {
                        char[] passcode = new char[0]; //TODO accept passphrase and add to list of known passwords
                        Map<Key, Certificate[]> fileContent = X509Utils.loadCertificatesAndPrivateKeysFromPkcs12KeystoreFile(f, passcode);
                        keystoreContents.putAll(fileContent);
                    }
                    currentFile++;
                    publishProgress((int)Math.rint(100 * ((double)currentFile / certificateFiles.length)));
                }
                return keystoreContents;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                for(Integer i : values) {

                    if(getDialog().isShowing() && getDialog().getOwnerActivity() != null) {
                        buildProgressDialog(getDialog().getContext());
                        progressDialog.setProgress(i);
                        progressDialog.show();
                    }
                }
            }

            @Override
            protected void onPostExecute(Map<Key, Certificate[]> keystoreContent) {
                if (!isCancelled()) {
                    KeyStoreContentsAdapter adapter = ((KeyStoreContentsAdapter) certificateList.getAdapter());
                    adapter.addData(keystoreContent);
                }
                if(progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        };
        File[] files = certificateFiles.toArray(new File[certificateFiles.size()]);
        runningTask.execute((Object[])files);
    }

    private void addNewCertificate() {
        FileListSelectionNeededEvent fileSelectionEvent = new FileListSelectionNeededEvent();
        ArrayList allowedFileTypes = new ArrayList();
        if(!justKeysWanted) {
            allowedFileTypes.addAll(allowedCertificateFileTypes);
        }
        allowedFileTypes.addAll(allowedKeyFileTypes);
        fileSelectionEvent.setAllowedFileTypes(allowedFileTypes);
        fileSelectionEvent.setUseAlphabeticalSortOrder(false);
        setTrackingRequest(fileSelectionEvent.getActionId());
        EventBus.getDefault().post(fileSelectionEvent);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {

        EventBus.getDefault().unregister(this);

        if(positiveResult) {
            mValueSet = false; // force the value to be saved.
            KeyStore newValue = ((KeyStoreContentsAdapter) certificateList.getAdapter()).getBackingObjectStore();
            if (callChangeListener(newValue)) {
                setValue(newValue);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    private KeyStore getPersistedValue() {
        if (!shouldPersist()) {
            //TODO do I need to check for an existing value here?
            mValue = buildBlankKeyStore();
        }
        return mValue;
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedValue() : buildBlankKeyStore());
    }

    private KeyStore buildBlankKeyStore() {
        try {
            return KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            throw new RuntimeException("Unable to set initial value", e);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = getValue();
        myState.trackedRequest = trackedRequest;
        return myState;
    }

    protected void setTrackingRequest(int requestId) {
        trackedRequest = requestId;
    }

    protected boolean isTrackingRequest(int requestId) {
        if(trackedRequest == requestId) {
            trackedRequest = -1;
            return true;
        }
        return false;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
        trackedRequest = myState.trackedRequest;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FileListSelectionCompleteEvent event) {
        if(isTrackingRequest(event.getActionId())) {
            onCertificatesSelected(event.getSelectedFiles());
        }
    }

    private static class SavedState extends BaseSavedState {


        private static final char[] ksPass = new char[] {'O','g','r','S','W','1','n','s','h','E','H','D','8','b','v','c','7','t','Z','J'};

        public static final Creator<SavedState> CREATOR =
                new Creator<KeyStorePreference.SavedState>() {
                    public KeyStorePreference.SavedState createFromParcel(Parcel in) {
                        return new KeyStorePreference.SavedState(in);
                    }

                    public KeyStorePreference.SavedState[] newArray(int size) {
                        return new KeyStorePreference.SavedState[size];
                    }
                };
        private int ksByteCount;
        private String ksType;
        private KeyStore value;
        private int trackedRequest;

        public SavedState(Parcel source) {
            super(source);
            ksByteCount = source.readInt();
            ksType = source.readString();
            byte[] ksBytes = new byte[ksByteCount];
            source.readByteArray(ksBytes);
            value = X509Utils.deserialiseKeystore(ksBytes, ksPass, ksType);
            trackedRequest = source.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            byte[] ksBytes = X509Utils.serialiseKeystore(value, ksPass);
            dest.writeInt(ksBytes.length);
            dest.writeString(value.getType());
            dest.writeByteArray(ksBytes);
            dest.writeInt(trackedRequest);
        }
    }

    private class KeyStoreItemViewHolder extends RecyclerView.ViewHolder {

        public KeyStoreItemViewHolder(View itemView) {
            super(itemView);
        }
    }

    private class KeyStoreCertificateItemViewHolder extends RecyclerView.ViewHolder {

        protected final TextView certNameField;
        protected final TextView certVerifiedByField;
        protected final TextView certValidFromField;
        protected final TextView certValidToField;
        protected final ImageButton deleteButton;

        public KeyStoreCertificateItemViewHolder(View itemView) {
            super(itemView);
            certNameField = (TextView) itemView.findViewById(R.id.x509_cert_identity);
            certVerifiedByField = (TextView) itemView.findViewById(R.id.x509_cert_verified_by);
            certValidFromField = (TextView) itemView.findViewById(R.id.x509_cert_valid_from);
            certValidToField = (TextView) itemView.findViewById(R.id.x509_cert_valid_to);
            deleteButton = (ImageButton) itemView.findViewById(R.id.list_item_delete_button);
        }
    }

    private class KeyStorePrivateKeyItemViewHolder extends KeyStoreCertificateItemViewHolder {

        protected final TextView keyTypeField;
        protected final TextView keyStrengthField;

        public KeyStorePrivateKeyItemViewHolder(View itemView) {
            super(itemView);
            keyTypeField = (TextView) itemView.findViewById(R.id.x509_key_type);
            keyStrengthField = (TextView) itemView.findViewById(R.id.x509_key_strength);
        }
    }

    private class KeyStoreContentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int VIEW_TYPE_PRIVATE_KEY = 1;
        private static final int VIEW_TYPE_CERTIFICATE = 2;
        private final SimpleDateFormat sdf;
        private KeyStore backingObjectStore;
        private ArrayList<String> aliasesList;

        public KeyStoreContentsAdapter(@NonNull Context context, @NonNull KeyStore ks) {
            setData(ks);
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }

        public KeyStore getBackingObjectStore() {
            return backingObjectStore;
        }

        private void buildKeyStoreAliasList() {
            this.aliasesList = X509Utils.extractAliasesFromKeystore(backingObjectStore);
        }

        @Override
        public int getItemCount() {
            return aliasesList.size();
        }

        private char[] getKeyPass(String alias) {
            return new char[0];
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if(viewType == VIEW_TYPE_PRIVATE_KEY) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.x509key_actionable_list_item_layout, parent, false);
                return new KeyStorePrivateKeyItemViewHolder(view);
            } else if(viewType == VIEW_TYPE_CERTIFICATE) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.x509cert_actionable_list_item_layout, parent, false);
                return new KeyStoreCertificateItemViewHolder(view);
            } else {
                throw new RuntimeException("Unsupported view type : " + viewType);
            }

        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_PRIVATE_KEY:
                    populatePrivateKeyDetails((KeyStorePrivateKeyItemViewHolder) holder, position, (KeyStore.PrivateKeyEntry) getItem(position));
                    break;
                case VIEW_TYPE_CERTIFICATE:
                    populateCertificateDetails((KeyStoreCertificateItemViewHolder) holder, position, (KeyStore.TrustedCertificateEntry) getItem(position));
            }
        }

        @Override
        public int getItemViewType(int position) {
            KeyStore.Entry item = getItem(position);
            if(item instanceof KeyStore.PrivateKeyEntry) {
                return VIEW_TYPE_PRIVATE_KEY;
            } else if(item instanceof KeyStore.TrustedCertificateEntry) {
                return VIEW_TYPE_CERTIFICATE;
            } else {
                throw new RuntimeException("Unsupported keystore entry type : " + item.getClass().getName());
            }
        }

        public <T extends KeyStore.Entry> T getItem(int position) {
            try {
                if(aliasesList.size() <= position) {
                    throw new IllegalArgumentException("Keystore does not contain that many entries");
                }
                String alias = aliasesList.get(position);
                return (T)backingObjectStore.getEntry(alias, new KeyStore.PasswordProtection(getKeyPass(alias)));
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            } catch (UnrecoverableEntryException e) {
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        private void populatePrivateKeyDetails(KeyStorePrivateKeyItemViewHolder viewHolder, final int position, KeyStore.PrivateKeyEntry item) {
            //TODO move the fields to the view holder

            PrivateKey privateKey = item.getPrivateKey();
            viewHolder.keyTypeField.setText(getContext().getString(R.string.x509_key_type_field_pattern, privateKey.getAlgorithm()));
            viewHolder.keyStrengthField.setText(getContext().getString(R.string.x509_key_strength_field_pattern, ((RSAPrivateKey) privateKey).getModulus().bitLength()));

            X509Certificate cert = (X509Certificate)item.getCertificate();
            fillCertificateFields(cert, viewHolder);
            viewHolder.deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteItem(position, v);
                }
            });
        }

        private String getIsolatedCnFieldIfPossible(X500Principal principal) {
            String val = principal.toString();
            Pattern p = Pattern.compile(".*(CN=)(.*?)(?<![\\\\]{1}),.*$");
            Matcher m = p.matcher(val);
            if(m.matches()) {
                return m.group(2);
            }
            return val;
        }

        private void fillCertificateFields(X509Certificate cert, KeyStoreCertificateItemViewHolder viewHolder) {
            String certSubject = getIsolatedCnFieldIfPossible(cert.getSubjectX500Principal());
            String issuerSubject = getIsolatedCnFieldIfPossible(cert.getIssuerX500Principal());
            viewHolder.certNameField.setText(getContext().getString(R.string.x509_cert_name_field_pattern, certSubject));
            viewHolder.certVerifiedByField.setText(getContext().getString(R.string.x509_cert_verified_by_field_pattern, issuerSubject));
            viewHolder.certValidFromField.setText(getContext().getString(R.string.x509_cert_valid_from_field_pattern, sdf.format(cert.getNotBefore())));
            viewHolder.certValidToField.setText(getContext().getString(R.string.x509_cert_valid_to_field_pattern, sdf.format(cert.getNotAfter())));
        }

        private void populateCertificateDetails(KeyStoreCertificateItemViewHolder viewHolder, final int position, KeyStore.TrustedCertificateEntry item) {

            X509Certificate cert = (X509Certificate)item.getTrustedCertificate();
            fillCertificateFields(cert, viewHolder);
            viewHolder.deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteItem(position, v);
                }
            });
        }

        private void onDeleteItem(int position, View v) {
            try {
                backingObjectStore.deleteEntry(aliasesList.get(position));
                aliasesList.remove(position);
                notifyDataSetChanged();
            } catch (KeyStoreException e) {
                throw new RuntimeException("Unable to delete keystore entry", e);
            }
        }

        public final void setData(KeyStore ks) {
            backingObjectStore = ks;
            buildKeyStoreAliasList();
            notifyDataSetChanged();
        }

        public void addData(Map<Key, Certificate[]> keystoreContent) {
            X509Utils.addToKeystore(backingObjectStore, keystoreContent);
            buildKeyStoreAliasList();
            notifyDataSetChanged();
        }
    }

}