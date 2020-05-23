package delit.libs.ui.view.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.lang.ref.WeakReference;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.PasswordInputToggle;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.X509Utils;
import delit.libs.util.security.CertificateLoadException;
import delit.libs.util.security.CertificateLoadOperationResult;
import delit.libs.util.security.KeyStoreContentException;
import delit.libs.util.security.KeyStoreOperationException;
import delit.libs.util.security.KeystoreLoadOperation;
import delit.libs.util.security.KeystoreLoadOperationResult;
import delit.libs.util.security.LoadOperationResult;
import delit.libs.util.security.SecurityOperationException;
import delit.libs.util.security.X509LoadOperation;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;

public class KeystorePreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private String STATE_TRACKED_REQUEST = "KeystorePreference.TrackedRequest";
    private String STATE_LOAD_OP_RESULT = "KeystorePreference.LoadOperationResult";
    private String STATE_LOAD_PROGRESS = "KeystorePreference.LoadProgress";
    private String STATE_KEYSTORE = "KeystorePreference.KeyStore";
    private static final char[] keystorePass = new char[]{'!', 'P', '1', 'r', '!', '4', 't', '3', '5', '!'};
    // State persistent items
    private int keystoreLoadProgress = -1;
    private LoadOperationResult keystoreLoadOperationResult;
    private int trackedRequest = -1;
    // non state persistent items
    private RecyclerView certificateList;
    private AlertDialog alertDialog;
    private ExtendedFloatingActionButton addListItemButton;
    private KeyStore keystore;
    private ProgressIndicator progressIndicator;

    @Override
    public Preference findPreference(CharSequence key) {
        return getPreference();
    }

    @Override
    public KeyStorePreference getPreference() {
        return (KeyStorePreference) super.getPreference();
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        EventBus.getDefault().register(this);
        if (keystoreLoadOperationResult != null) {
            showLoadErrors();
        }
        view.requestFocus();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {

        EventBus.getDefault().unregister(this);

        if (positiveResult) {
            KeyStore newValue = ((KeyStoreContentsAdapter) certificateList.getAdapter()).getBackingObjectStore();
            if (getPreference().callChangeListener(newValue)) {
                getPreference().setKeystore(newValue);
            }
        }
    }

    @Override
    protected View onCreateDialogView(Context context) {
        return buildCertificateListView(context, keystore);
    }

    private View buildCertificateListView(Context context, KeyStore keystore) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_fullsize_recycler_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        progressIndicator = view.findViewById(R.id.list_action_progress_indicator);
        progressIndicator.setVisibility(View.GONE);
        if(keystoreLoadProgress >= 0 && keystoreLoadProgress < 100) {
            progressIndicator.showProgressIndicator(R.string.alert_loading_certificates_from_selected_files, keystoreLoadProgress);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.certificates_heading);
        heading.setVisibility(View.VISIBLE);

        certificateList = view.findViewById(R.id.list);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(context);
        certificateList.setLayoutManager(mLayoutManager);
        KeyStoreContentsAdapter adapter = new KeyStoreContentsAdapter(context, X509Utils.cloneKeystore(keystore));
        certificateList.setAdapter(adapter);

        addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.VISIBLE);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (keystoreLoadProgress < 0 || keystoreLoadProgress == 100) {
                    addNewCertificate();
                }
            }
        });

        Button saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);

        return view;
    }

    private void buildAndShowAlertErrorLoadingFilesDialog(String errorMessage) {
        alertDialog = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.alert_error)
                .setMessage(errorMessage)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        alertDialog.dismiss();
                        keystoreLoadOperationResult.removeUnrecoverableErrors();
                        processRecoverableErrors(((AlertDialog)dialog).getContext());
                    }
                })
                .show();
    }

    private void addNewCertificate() {
        FileSelectionNeededEvent fileSelectionEvent = new FileSelectionNeededEvent(true, false, true);
        Set<String> allowedFileTypes = new HashSet<>();
        if (!getPreference().isJustKeysWanted()) {
            allowedFileTypes.addAll(getPreference().getAllowedCertificateFileTypes());
        }
        allowedFileTypes.addAll(getPreference().getAllowedKeyFileTypes());
        fileSelectionEvent.withInitialFolder(Uri.fromFile(Environment.getExternalStorageDirectory()));
        fileSelectionEvent.withVisibleContent(allowedFileTypes, FileSelectionNeededEvent.ALPHABETICAL);
//        fileSelectionEvent.withSelectedUriPermissionsForConsumerId(getUriPermissionsKey()); Not currently needed as permissions are transient.

        setTrackingRequest(fileSelectionEvent.getActionId());
        EventBus.getDefault().post(fileSelectionEvent);
    }

    private void processRecoverableErrors(Context context) {

        final SecurityOperationException recoverableError = keystoreLoadOperationResult.getNextRecoverableError();

        if (recoverableError == null) {
            // all passwords retrieved
            List<X509LoadOperation> loadOperations = keystoreLoadOperationResult.getRemainingLoadOperations();
            if (loadOperations.size() > 0) {
                keystoreLoadOperationResult = null;
                new AsyncX509LoaderTask(this).execute(loadOperations.toArray(new X509LoadOperation[0]));
            }
            return;
        }

        View v = null;
        if (recoverableError instanceof KeyStoreOperationException) {
            // request keystore password
            v = LayoutInflater.from(context).inflate(R.layout.layout_keystore_password_entry, null);
        } else if (recoverableError instanceof KeyStoreContentException) {
            // request keystore alias key password
            v = LayoutInflater.from(context).inflate(R.layout.layout_keystore_key_password_entry, null);

            KeyStoreContentException e = (KeyStoreContentException) recoverableError;

            EditText keystoreAliasEditText = v.findViewById(R.id.keystore_alias_editText);
            keystoreAliasEditText.setText(e.getAlias());
        }

        CheckBox viewUnencryptedToggle = v.findViewById(R.id.toggle_visibility);
        if (viewUnencryptedToggle != null) {
            EditText passwordField = v.findViewById(R.id.keystore_password_editText);
            viewUnencryptedToggle.setOnCheckedChangeListener(new PasswordInputToggle(passwordField));
        }

        EditText filenameEditText = v.findViewById(R.id.keystore_filename_editText);
        String filename = new File(recoverableError.getDataSource()).getName();
        filenameEditText.setText(filename);

        alertDialog = new MaterialAlertDialogBuilder(context)
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
                        processRecoverableErrors(((AlertDialog)dialog).getContext());
                    }
                })
                .show();
    }

    public static DialogFragment newInstance(String key) {
        final KeystorePreferenceDialogFragmentCompat fragment =
                new KeystorePreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    private static class KeyStoreContentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int VIEW_TYPE_PRIVATE_KEY = 1;
        private static final int VIEW_TYPE_CERTIFICATE = 2;
        private final SimpleDateFormat sdf;
        private final WeakReference<Context> contextRef;
        private KeyStore backingObjectStore;
        private ArrayList<String> aliasesList;

        public KeyStoreContentsAdapter(@NonNull Context context, @NonNull KeyStore ks) {
            setData(ks);
            contextRef = new WeakReference<>(context);
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
            if (viewType == VIEW_TYPE_PRIVATE_KEY) {
                view = LayoutInflater.from(contextRef.get()).inflate(R.layout.layout_x509key_actionable_list_item, parent, false);
                return new KeyStorePrivateKeyItemViewHolder(view);
            } else if (viewType == VIEW_TYPE_CERTIFICATE) {
                view = LayoutInflater.from(contextRef.get()).inflate(R.layout.layout_x509cert_actionable_list_item, parent, false);
                return new KeyStoreCertificateItemViewHolder(view);
            } else {
                throw new RuntimeException("Unsupported view type : " + viewType);
            }

        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_PRIVATE_KEY:
                    populatePrivateKeyDetails((KeyStorePrivateKeyItemViewHolder) holder, position, getItem(position));
                    break;
                case VIEW_TYPE_CERTIFICATE:
                    populateCertificateDetails((KeyStoreCertificateItemViewHolder) holder, position, getItem(position));
            }
        }

        @Override
        public int getItemViewType(int position) {
            KeyStore.Entry item = getItem(position);
            if (item instanceof KeyStore.PrivateKeyEntry) {
                return VIEW_TYPE_PRIVATE_KEY;
            } else if (item instanceof KeyStore.TrustedCertificateEntry) {
                return VIEW_TYPE_CERTIFICATE;
            } else {
                throw new RuntimeException("Unsupported keystore entry type : " + item.getClass().getName());
            }
        }

        public <T extends KeyStore.Entry> T getItem(int position) {
            try {
                if (aliasesList.size() <= position) {
                    throw new IllegalArgumentException("Keystore does not contain that many entries");
                }
                String alias = aliasesList.get(position);
                return getItem(backingObjectStore, alias);
            } catch (KeyStoreException e) {
                Crashlytics.logException(e);
                throw new RuntimeException(e);
            } catch (UnrecoverableEntryException e) {
                Crashlytics.logException(e);
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                Crashlytics.logException(e);
                throw new RuntimeException(e);
            }
        }

        protected <T extends KeyStore.Entry> T getItem(KeyStore keystore, String alias) throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException {
            char[] pass = getKeyPass(alias);
            KeyStore.PasswordProtection protection = null;
            if (pass != null && pass.length > 0) {
                protection = new KeyStore.PasswordProtection(pass);
            }
            return (T) keystore.getEntry(alias, protection);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        private void populatePrivateKeyDetails(KeyStorePrivateKeyItemViewHolder viewHolder, final int position, KeyStore.PrivateKeyEntry item) {

            PrivateKey privateKey = item.getPrivateKey();
            viewHolder.keyTypeField.setText(contextRef.get().getString(R.string.x509_key_type_field_pattern, privateKey.getAlgorithm()));
            viewHolder.keyStrengthField.setText(contextRef.get().getString(R.string.x509_key_strength_field_pattern, ((RSAPrivateKey) privateKey).getModulus().bitLength()));

            X509Certificate cert = (X509Certificate) item.getCertificate();
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
            if (m.matches()) {
                return m.group(2);
            }
            return val;
        }

        private void fillCertificateFields(X509Certificate cert, KeyStoreCertificateItemViewHolder viewHolder) {
            String certSubject = getIsolatedCnFieldIfPossible(cert.getSubjectX500Principal());
            String issuerSubject = getIsolatedCnFieldIfPossible(cert.getIssuerX500Principal());
            viewHolder.certNameField.setText(contextRef.get().getString(R.string.x509_cert_name_field_pattern, certSubject));
            viewHolder.certVerifiedByField.setText(contextRef.get().getString(R.string.x509_cert_verified_by_field_pattern, issuerSubject));
            viewHolder.certValidFromField.setText(contextRef.get().getString(R.string.x509_cert_valid_from_field_pattern, sdf.format(cert.getNotBefore())));
            viewHolder.certValidToField.setText(contextRef.get().getString(R.string.x509_cert_valid_to_field_pattern, sdf.format(cert.getNotAfter())));
        }

        private void populateCertificateDetails(KeyStoreCertificateItemViewHolder viewHolder, final int position, KeyStore.TrustedCertificateEntry item) {

            X509Certificate cert = (X509Certificate) item.getTrustedCertificate();
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
                Crashlytics.logException(e);
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

    private static class KeyStoreCertificateItemViewHolder extends RecyclerView.ViewHolder {

        protected final TextView certNameField;
        protected final TextView certVerifiedByField;
        protected final TextView certValidFromField;
        protected final TextView certValidToField;
        protected final MaterialButton deleteButton;

        public KeyStoreCertificateItemViewHolder(View itemView) {
            super(itemView);
            certNameField = itemView.findViewById(R.id.x509_cert_identity);
            certVerifiedByField = itemView.findViewById(R.id.x509_cert_verified_by);
            certValidFromField = itemView.findViewById(R.id.x509_cert_valid_from);
            certValidToField = itemView.findViewById(R.id.x509_cert_valid_to);
            deleteButton = itemView.findViewById(R.id.list_item_delete_button);
        }
    }

    private static class KeyStorePrivateKeyItemViewHolder extends KeyStoreCertificateItemViewHolder {

        protected final TextView keyTypeField;
        protected final TextView keyStrengthField;

        public KeyStorePrivateKeyItemViewHolder(View itemView) {
            super(itemView);
            keyTypeField = itemView.findViewById(R.id.x509_key_type);
            keyStrengthField = itemView.findViewById(R.id.x509_key_strength);
        }
    }

    static class AsyncX509LoaderTask extends AsyncTask<X509LoadOperation, Integer, LoadOperationResult> {

        private final KeystorePreferenceDialogFragmentCompat sourcePref;

        private AsyncX509LoaderTask(KeystorePreferenceDialogFragmentCompat sourcePref) {
            this.sourcePref = sourcePref;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoadOperationResult doInBackground(X509LoadOperation... loadOps) {
            LoadOperationResult loadOperationResult = new LoadOperationResult();
            int currentFile = 0;
            for (X509LoadOperation loadOp : loadOps) {
                String fileSuffix = loadOp.getFile().getName().replaceFirst(".*(\\.[^.]*)", "$1").toLowerCase();
                if (sourcePref.getPreference().getAllowedCertificateFileTypes().contains(fileSuffix)) {
                    try {
                        Collection<X509Certificate> certs = X509Utils.loadCertificatesFromUri(loadOp.getFile().getUri());
                        CertificateLoadOperationResult result = new CertificateLoadOperationResult(loadOp);
                        result.getCerts().addAll(certs);
                        loadOperationResult.getCertLoadResults().add(result);
                    } catch (CertificateLoadException e) {
                        Crashlytics.logException(e);
                        CertificateLoadOperationResult result = new CertificateLoadOperationResult(loadOp);
                        result.setException(e);
                        loadOperationResult.getCertLoadResults().add(result);
                    }
                } else if (sourcePref.getPreference().getAllowedKeyFileTypes().contains(fileSuffix)) {
                    KeystoreLoadOperationResult keystoreLoadOperationResult;
                    if (KeyStorePreference.BKS_FILE_SUFFIX.equals(fileSuffix)) {
                        keystoreLoadOperationResult = X509Utils.loadCertificatesAndPrivateKeysFromKeystoreFile(KeystoreLoadOperation.from(loadOp), "bks");
                    } else {
                        keystoreLoadOperationResult = X509Utils.loadCertificatesAndPrivateKeysFromPkcs12KeystoreFile(KeystoreLoadOperation.from(loadOp));
                    }
                    loadOperationResult.getKeystoreLoadResults().add(keystoreLoadOperationResult);
                }
                currentFile++;
                publishProgress((int) Math.rint(100 * ((double) currentFile / loadOps.length)));
            }
            return loadOperationResult;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            for (Integer i : values) {
                sourcePref.onProgressUpdate(i);
            }
        }

        @Override
        protected void onPostExecute(LoadOperationResult loadOperationResult) {
            sourcePref.onKeystoreLoadFinished(loadOperationResult, isCancelled());
        }
    }

    public void onCertificatesSelected(ArrayList<DocumentFile> certificateFiles) {
        X509LoadOperation[] params = new X509LoadOperation[certificateFiles.size()];
        int i = 0;
        for (DocumentFile f : certificateFiles) {
            params[i++] = new X509LoadOperation(f);
        }
        keystoreLoadOperationResult = null;
        keystoreLoadProgress = 0;
        new AsyncX509LoaderTask(this).execute(params);
    }

    private void onKeystoreLoadFinished(LoadOperationResult loadOperationResult, boolean wasLoadCancelled) {
        keystoreLoadOperationResult = loadOperationResult;
        if (!wasLoadCancelled) {
            KeyStoreContentsAdapter adapter = ((KeyStoreContentsAdapter) certificateList.getAdapter());
            adapter.addData(loadOperationResult.removeSuccessfullyLoadedData());
        }
        progressIndicator.setVisibility(View.GONE);
        showLoadErrors();
        keystoreLoadProgress = 100;
    }

    private void onProgressUpdate(Integer i) {
        progressIndicator.showProgressIndicator(R.string.alert_loading_certificates_from_selected_files, i);
    }

    private void showLoadErrors() {
        List<SecurityOperationException> unrecoverableErrors = keystoreLoadOperationResult.getUnrecoverableErrors();
        if (unrecoverableErrors.size() > 0) {
            String errorMessage = buildErrorMessage(unrecoverableErrors);
            buildAndShowAlertErrorLoadingFilesDialog(errorMessage);
        } else {
            processRecoverableErrors(getContext());
        }
    }

    private String buildErrorMessage(List<SecurityOperationException> unrecoverableErrors) {
        // establish what groups of errors there are
        List<CertificateLoadException> certExceptions = new ArrayList<>();
        List<KeyStoreOperationException> keystoreExceptions = new ArrayList<>();
        List<KeyStoreContentException> keystoreContentExceptions = new ArrayList<>();
        for (SecurityOperationException ex : unrecoverableErrors) {
            if (ex instanceof CertificateLoadException) {
                certExceptions.add((CertificateLoadException) ex);
            } else if (ex instanceof KeyStoreOperationException) {
                keystoreExceptions.add((KeyStoreOperationException) ex);
            } else if (ex instanceof KeyStoreContentException) {
                keystoreContentExceptions.add((KeyStoreContentException) ex);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (certExceptions.size() > 0) {
            // add cert exceptions list
            sb.append(getContext().getString(R.string.error_heading_unloadable_certificate_files));
            sb.append('\n');
            for (CertificateLoadException ex : certExceptions) {
                sb.append(ex.getDataSource());
                sb.append('\n');
            }
        }
        if (keystoreExceptions.size() > 0) {
            // add keystore exceptions list
            sb.append(getContext().getString(R.string.error_heading_unloadable_keystore_files));
            sb.append('\n');
            for (KeyStoreOperationException ex : keystoreExceptions) {
                sb.append(ex.getDataSource());
                sb.append('\n');
            }
        }
        if (keystoreContentExceptions.size() > 0) {
            // add keystore content exceptions list
            sb.append(getContext().getString(R.string.error_heading_unloadable_keystore_aliases));
            sb.append('\n');
            Map<String, List<KeyStoreContentException>> aliasErrors = new HashMap<>();
            for (KeyStoreContentException ex : keystoreContentExceptions) {
                List<KeyStoreContentException> fileErrs = aliasErrors.get(ex.getDataSource());
                if (fileErrs == null) {
                    fileErrs = new ArrayList<>();
                    aliasErrors.put(ex.getDataSource(), fileErrs);
                }
                fileErrs.add(ex);
            }
            for (Map.Entry<String, List<KeyStoreContentException>> fileExs : aliasErrors.entrySet()) {
                sb.append(fileExs.getKey());
                sb.append(": ");
                for (int i = 0; i < fileExs.getValue().size(); i++) {
                    sb.append(fileExs.getValue().get(i));
                    if (i < fileExs.getValue().size() - 1) {
                        sb.append(", ");
                    }
                }
            }
        }
        return sb.toString();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED, sticky = true)
    public void onEvent(FileSelectionCompleteEvent event) {
        if (isTrackingRequest(event.getActionId())) {
            EventBus.getDefault().removeStickyEvent(event);
            onCertificatesSelected(event.getSelectedFolderItemsAsFiles(getContext()));
        }
    }

    protected void setTrackingRequest(int requestId) {
        trackedRequest = requestId;
    }

    protected boolean isTrackingRequest(int requestId) {
        if (trackedRequest == requestId) {
            trackedRequest = -1;
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TRACKED_REQUEST, trackedRequest);
        outState.putSerializable(STATE_LOAD_OP_RESULT, keystoreLoadOperationResult);
        outState.putInt(STATE_LOAD_PROGRESS, keystoreLoadProgress);
        outState.putByteArray(STATE_KEYSTORE, X509Utils.saveKeystore("byteArray", keystore, keystorePass));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            trackedRequest = savedInstanceState.getInt(STATE_TRACKED_REQUEST, -1);
            keystoreLoadOperationResult = BundleUtils.getSerializable(savedInstanceState, STATE_LOAD_OP_RESULT, LoadOperationResult.class);
            keystoreLoadProgress = savedInstanceState.getInt(STATE_LOAD_PROGRESS);
            keystore = X509Utils.loadKeystore("byteArray", savedInstanceState.getByteArray(STATE_KEYSTORE), keystorePass) ;
        } else {
            KeyStorePreference pref = getPreference();
            keystore = pref.getKeystore();
            keystoreLoadOperationResult = null;
            trackedRequest = -1;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }
}
