package delit.piwigoclient.ui.preferences;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
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
import delit.piwigoclient.util.security.CertificateLoadException;
import delit.piwigoclient.util.security.CertificateLoadOperationResult;
import delit.piwigoclient.util.security.KeyStoreContentException;
import delit.piwigoclient.util.security.KeyStoreOperationException;
import delit.piwigoclient.util.security.KeystoreLoadOperation;
import delit.piwigoclient.util.security.KeystoreLoadOperationResult;
import delit.piwigoclient.util.security.LoadOperationResult;
import delit.piwigoclient.util.security.SecurityOperationException;
import delit.piwigoclient.util.security.X509LoadOperation;

/**
 * Created by gareth on 15/07/17.
 */

public abstract class KeyStorePreference extends DialogPreference {

    private boolean justKeysWanted;
    private KeyStore mValue;
    private boolean mValueSet;
    private RecyclerView certificateList;
    private int trackedRequest = -1;
    private ProgressDialog progressDialog;
    private AlertDialog alertDialog;
    private LoadOperationResult keystoreLoadOperationResult;
    private ArrayList<String> allowedCertificateFileTypes = new ArrayList<>(Arrays.asList(".cer", ".cert", ".pem"));
    private static final String BKS_FILE_SUFFIX = ".bks";
    private ArrayList<String> allowedKeyFileTypes = new ArrayList<>(Arrays.asList(".p12", ".pkcs12", ".pfx", BKS_FILE_SUFFIX));
    private CustomImageButton addListItemButton;
    private boolean keystoreLoadInProgress;

    public KeyStorePreference(String tag, Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        String tag1 = tag;
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
            String element = i.next();
            if(!element.startsWith(".")) {
                element = '.' + element;
            }
            i.set(element.toLowerCase());
        }
        this.allowedCertificateFileTypes = allowedCertificateFileTypes;
    }

    public void setAllowedKeyFileTypes(ArrayList<String> allowedKeyFileTypes) {
        for (final ListIterator<String> i = allowedCertificateFileTypes.listIterator(); i.hasNext();) {
            String element = i.next();
            if(!element.startsWith(".")) {
                element = '.' + element;
            }
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

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.certificates_heading);
        heading.setVisibility(View.VISIBLE);

        certificateList = view.findViewById(R.id.list);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        certificateList.setLayoutManager(mLayoutManager);
        KeyStoreContentsAdapter adapter = new KeyStoreContentsAdapter(getContext(), X509Utils.cloneKeystore(mValue));
        certificateList.setAdapter(adapter);

        addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.VISIBLE);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!keystoreLoadInProgress) {
                    addNewCertificate();
                }
            }
        });

        Button saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);

        return view;
    }

    private void buildAndShowAlertErrorLoadingFilesDialog(String errorMessage) {
        alertDialog = new AlertDialog.Builder(getContext())
                            .setTitle(R.string.alert_error)
                            .setMessage(errorMessage)
                            .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    alertDialog.dismiss();
                                    keystoreLoadOperationResult.removeUnrecoverableErrors();
                                    processRecoverableErrors();
                                }
                            })
                            .show();
    }

    private void processRecoverableErrors() {

        final SecurityOperationException recoverableError = keystoreLoadOperationResult.getNextRecoverableError();

        if(recoverableError == null) {
            // all passwords retrieved
            List<X509LoadOperation> loadOperations = keystoreLoadOperationResult.getRemainingLoadOperations();
            if(loadOperations.size() > 0) {
                keystoreLoadOperationResult = null;
                new AsyncX509LoaderTask().execute(loadOperations.toArray(new X509LoadOperation[loadOperations.size()]));
            }
            return;
        }

        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View v = null;
        if (recoverableError instanceof KeyStoreOperationException) {
            // request keystore password
            v = layoutInflater.inflate(R.layout.keystore_password_entry_layout, null);
        } else if (recoverableError instanceof KeyStoreContentException) {
            // request keystore alias key password
            v = layoutInflater.inflate(R.layout.keystore_key_password_entry_layout, null);

            KeyStoreContentException e = (KeyStoreContentException) recoverableError;

            EditText keystoreAliasEditText = v.findViewById(R.id.keystore_alias_editText);
            keystoreAliasEditText.setText(e.getAlias());
        }

        EditText filenameEditText = v.findViewById(R.id.keystore_filename_editText);
        filenameEditText.setText(recoverableError.getFile().getName());

        alertDialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.alert_information)
                .setView(v)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        EditText passwordEditText = alertDialog.findViewById(R.id.keystore_password_editText);
                        char[] pass = new char[passwordEditText.getText().length()];
                        passwordEditText.getText().getChars(0, passwordEditText.getText().length(), pass, 0);
                        alertDialog.dismiss();
                        keystoreLoadOperationResult.addPasswordForRerun(recoverableError, pass);
                        processRecoverableErrors();
                    }
                })
                .show();
    }

    private void buildProgressDialog(Context context) {
        progressDialog = new ProgressDialog(context);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle(R.string.alert_loading_certificates_from_selected_files);
    }

    public void onCertificatesSelected(ArrayList<File> certificateFiles) {

        X509LoadOperation[] params = new X509LoadOperation[certificateFiles.size()];
        int i = 0;
        for(File f : certificateFiles) {
            params[i++] = new X509LoadOperation(f);
        }
        keystoreLoadOperationResult = null;
        keystoreLoadInProgress = true;
        new AsyncX509LoaderTask().execute(params);
    }

    class AsyncX509LoaderTask extends AsyncTask<X509LoadOperation, Integer, LoadOperationResult> {


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoadOperationResult doInBackground(X509LoadOperation ... loadOps) {
            LoadOperationResult loadOperationResult = new LoadOperationResult();
            int currentFile = 0;
            for (X509LoadOperation loadOp : loadOps) {
                String fileSuffix = loadOp.getFile().getName().replaceFirst(".*(\\.[^.]*)", "$1").toLowerCase();
                if (allowedCertificateFileTypes.contains(fileSuffix)) {
                    try {
                        Collection<X509Certificate> certs = X509Utils.loadCertificatesFromFile(loadOp.getFile());
                        CertificateLoadOperationResult result = new CertificateLoadOperationResult(loadOp);
                        result.getCerts().addAll(certs);
                        loadOperationResult.getCertLoadResults().add(result);
                    } catch(CertificateLoadException e) {
                        CertificateLoadOperationResult result = new CertificateLoadOperationResult(loadOp);
                        result.setException(e);
                        loadOperationResult.getCertLoadResults().add(result);
                    }
                } else if (allowedKeyFileTypes.contains(fileSuffix)) {
                    KeystoreLoadOperationResult keystoreLoadOperationResult;
                    if(BKS_FILE_SUFFIX.equals(fileSuffix)) {
                        keystoreLoadOperationResult = X509Utils.loadCertificatesAndPrivateKeysFromKeystoreFile(KeystoreLoadOperation.from(loadOp), "bks");
                    } else {
                        keystoreLoadOperationResult = X509Utils.loadCertificatesAndPrivateKeysFromPkcs12KeystoreFile(KeystoreLoadOperation.from(loadOp));
                    }
                    loadOperationResult.getKeystoreLoadResults().add(keystoreLoadOperationResult);
                }
                currentFile++;
                publishProgress((int)Math.rint(100 * ((double)currentFile / loadOps.length)));
            }
            return loadOperationResult;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            for(Integer i : values) {
                if(getDialog() != null && getDialog().isShowing()) {
                    if(progressDialog == null || !progressDialog.isShowing()) {
                        buildProgressDialog(getDialog().getContext());
                    }
                    progressDialog.setProgress(i);
                    progressDialog.show();
                }
            }
        }

        @Override
        protected void onPostExecute(LoadOperationResult loadOperationResult) {
            keystoreLoadOperationResult = loadOperationResult;
            if (!isCancelled()) {
                KeyStoreContentsAdapter adapter = ((KeyStoreContentsAdapter) certificateList.getAdapter());
                adapter.addData(loadOperationResult.removeSuccessfullyLoadedData());
            }

            if(progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            showLoadErrors();
            keystoreLoadInProgress = false;
        }
    };

    private void showLoadErrors() {
        List<SecurityOperationException> unrecoverableErrors = keystoreLoadOperationResult.getUnrecoverableErrors();
        if(unrecoverableErrors.size() > 0) {
            String errorMessage = buildErrorMessage(unrecoverableErrors);
            buildAndShowAlertErrorLoadingFilesDialog(errorMessage);
        } else {
            processRecoverableErrors();
        }
    }

    private String buildErrorMessage(List<SecurityOperationException> unrecoverableErrors) {
        // establish what groups of errors there are
        List<CertificateLoadException> certExceptions = new ArrayList<>();
        List<KeyStoreOperationException> keystoreExceptions = new ArrayList<>();
        List<KeyStoreContentException> keystoreContentExceptions = new ArrayList<>();
        for(SecurityOperationException ex : unrecoverableErrors) {
            if(ex instanceof CertificateLoadException) {
                certExceptions.add((CertificateLoadException)ex);
            } else if(ex instanceof KeyStoreOperationException) {
                keystoreExceptions.add((KeyStoreOperationException)ex);
            } else if(ex instanceof KeyStoreContentException) {
                keystoreContentExceptions.add((KeyStoreContentException)ex);
            }
        }
        StringBuilder sb = new StringBuilder();
        if(certExceptions.size() > 0) {
            // add cert exceptions list
            sb.append(getContext().getString(R.string.error_heading_unloadable_certificate_files));
            sb.append('\n');
            for(CertificateLoadException ex : certExceptions) {
                sb.append(safeGetFilename(ex.getFile()));
                sb.append('\n');
            }
        }
        if(keystoreExceptions.size() > 0) {
            // add keystore exceptions list
            sb.append(getContext().getString(R.string.error_heading_unloadable_keystore_files));
            sb.append('\n');
            for(KeyStoreOperationException ex : keystoreExceptions) {
                sb.append(safeGetFilename(ex.getFile()));
                sb.append('\n');
            }
        }
        if(keystoreContentExceptions.size() > 0) {
            // add keystore content exceptions list
            sb.append(getContext().getString(R.string.error_heading_unloadable_keystore_aliases));
            sb.append('\n');
            Map<File, List<KeyStoreContentException>> aliasErrors = new HashMap<>();
            for(KeyStoreContentException ex : keystoreContentExceptions) {
                List<KeyStoreContentException> fileErrs = aliasErrors.get(ex.getFile());
                if(fileErrs == null) {
                    fileErrs = new ArrayList<>();
                    aliasErrors.put(ex.getFile(), fileErrs);
                }
                fileErrs.add(ex);
            }
            for(Map.Entry<File, List<KeyStoreContentException>> fileExs : aliasErrors.entrySet()) {
                sb.append(safeGetFilename(fileExs.getKey()));
                sb.append(": ");
                for (int i = 0; i < fileExs.getValue().size(); i++) {
                    sb.append(fileExs.getValue().get(i));
                    if(i < fileExs.getValue().size() - 1) {
                        sb.append(", ");
                    }
                }
            }
        }
        return sb.toString();
    }

    protected String safeGetFilename(File file) {
        try {
            return file.getCanonicalPath();
        } catch(IOException e) {
            return file.getName();
        } catch(SecurityException e) {
            return file.getName();
        }
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
        myState.keystoreLoadOperationProgress = keystoreLoadOperationResult;
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
        keystoreLoadOperationResult = myState.keystoreLoadOperationProgress;


        if(keystoreLoadOperationResult != null) {
            showLoadErrors();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FileListSelectionCompleteEvent event) {
        if(isTrackingRequest(event.getActionId())) {
            onCertificatesSelected(event.getSelectedFiles());
        }
    }

    public static class SavedState extends BaseSavedState {


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
        private LoadOperationResult keystoreLoadOperationProgress;

        public SavedState(Parcel source) {
            super(source);
            ksByteCount = source.readInt();
            ksType = source.readString();
            byte[] ksBytes = new byte[ksByteCount];
            source.readByteArray(ksBytes);
            value = X509Utils.deserialiseKeystore(ksBytes, ksPass, ksType);
            trackedRequest = source.readInt();
            keystoreLoadOperationProgress = (LoadOperationResult)source.readSerializable();
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
            dest.writeSerializable(keystoreLoadOperationProgress);
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
            certNameField = itemView.findViewById(R.id.x509_cert_identity);
            certVerifiedByField = itemView.findViewById(R.id.x509_cert_verified_by);
            certValidFromField = itemView.findViewById(R.id.x509_cert_valid_from);
            certValidToField = itemView.findViewById(R.id.x509_cert_valid_to);
            deleteButton = itemView.findViewById(R.id.list_item_delete_button);
        }
    }

    private class KeyStorePrivateKeyItemViewHolder extends KeyStoreCertificateItemViewHolder {

        protected final TextView keyTypeField;
        protected final TextView keyStrengthField;

        public KeyStorePrivateKeyItemViewHolder(View itemView) {
            super(itemView);
            keyTypeField = itemView.findViewById(R.id.x509_key_type);
            keyStrengthField = itemView.findViewById(R.id.x509_key_strength);
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
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);
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

        protected char[] getKeyPass(String alias) {
            return new char[0];
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
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
                return getItem(backingObjectStore, alias);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            } catch (UnrecoverableEntryException e) {
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        protected <T extends KeyStore.Entry> T getItem(KeyStore keystore, String alias) throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException {
            char[] pass = getKeyPass(alias);
            KeyStore.PasswordProtection protection = null;
            if(pass != null && pass.length > 0) {
                protection = new KeyStore.PasswordProtection(pass);
            }
            return (T)keystore.getEntry(alias, protection);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        private void populatePrivateKeyDetails(KeyStorePrivateKeyItemViewHolder viewHolder, final int position, KeyStore.PrivateKeyEntry item) {

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
            Pattern p = Pattern.compile(".*(CN=)(.*?)(?<![\\\\]),.*$");
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