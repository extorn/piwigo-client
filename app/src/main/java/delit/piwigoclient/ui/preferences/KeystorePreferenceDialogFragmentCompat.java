package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.PasswordInputToggle;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.IOUtils;
import delit.libs.util.Utils;
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
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.DialogFragmentUIHelper;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;

public class KeystorePreferenceDialogFragmentCompat<F extends KeystorePreferenceDialogFragmentCompat<F,FUIH>, FUIH extends DialogFragmentUIHelper<FUIH,F>> extends MyDialogPreferenceFragment<F,FUIH> implements DialogPreference.TargetFragment {

    private final String STATE_TRACKED_REQUEST = "KeystorePreference.TrackedRequest";
    private final String STATE_LOAD_OP_RESULT = "KeystorePreference.LoadOperationResult";
    private final String STATE_LOAD_PROGRESS = "KeystorePreference.LoadProgress";
    private final String STATE_KEYSTORE = "KeystorePreference.KeyStore";
    private static final char[] keystorePass = new char[]{'!', 'P', '1', 'r', '!', '4', 't', '3', '5', '!'};
    // State persistent items
    private int keystoreLoadProgress = -1;
    private LoadOperationResult keystoreLoadOperationResult;
    private int trackedRequest = -1;
    // non state persistent items
    private RecyclerView certificateList;
    private AlertDialog alertDialog;
    private KeyStore keystore;
    private ProgressIndicator progressIndicator;
    private AppSettingsViewModel appSettingsViewModel;

    @Override
    public <T extends Preference> T findPreference(@NonNull CharSequence key) {
        return (T)getPreference();
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
    protected void setupUiHelper(@NonNull View view) {
        //FIXME currently force using the main activity view instead. So popups happen on that view. It isn't great, but the dialog looks messy resizing.
        super.setupUiHelper(requireActivity().getWindow().getDecorView());
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {

        appSettingsViewModel = null;
        EventBus.getDefault().unregister(this);

        if (positiveResult) {
            KeyStore newValue = ((KeyStoreContentsAdapter) Objects.requireNonNull(certificateList.getAdapter())).getBackingObjectStore();
            if (getPreference().callChangeListener(newValue)) {
                getPreference().setKeystore(newValue);
            }
        }
    }

    @Override
    protected View onCreateDialogView(Context context) {
        appSettingsViewModel = obtainActivityViewModel(requireActivity(), AppSettingsViewModel.class);
        return buildCertificateListView(context, keystore);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    private View buildCertificateListView(Context context, KeyStore keystore) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_fullsize_recycler_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
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

        ExtendedFloatingActionButton addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.VISIBLE);
        addListItemButton.setOnClickListener(v -> {
            if (keystoreLoadProgress < 0 || keystoreLoadProgress == 100) {
                addNewCertificate();
            }
        });

        Button saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);

        return view;
    }

    private void buildAndShowAlertErrorLoadingFilesDialog(String errorMessage) {
        alertDialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(getContext(), R.style.Theme_App_EditPages))
                .setTitle(R.string.alert_error)
                .setMessage(errorMessage)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    alertDialog.dismiss();
                    keystoreLoadOperationResult.removeUnrecoverableErrors();
                    processRecoverableErrors(((AlertDialog)dialog).getContext());
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
        fileSelectionEvent.withInitialFolder(Uri.fromFile(requireContext().getExternalFilesDir(null)));
        fileSelectionEvent.withVisibleContent(allowedFileTypes, FileSelectionNeededEvent.ALPHABETICAL);
        fileSelectionEvent.withSelectedUriPermissionsForConsumerId(getUriPermissionsKey());
        fileSelectionEvent.setSelectedUriPermissionsForConsumerPurpose(getUriPermissionsPurpose());
        fileSelectionEvent.requestUriReadPermission();

        setTrackingRequest(fileSelectionEvent.getActionId());
        EventBus.getDefault().post(fileSelectionEvent);
    }

    /**
     * Use the preference key as the uri permissions key - if this isn't unique, you might want to change it.
     * @return The consumer key of any Uri Permissions granted for this preference
     */
    public @NonNull String getUriPermissionsKey() {
        return getPreference().getKey();
    }

    /**
     * Use the preference title as the uri permissions purpose
     * @return The purpose of any Uri Permissions granted for this preference
     */
    public @NonNull String getUriPermissionsPurpose() {
        return getPreference().getTitle().toString();
    }

    private void processRecoverableErrors(@NonNull Context context) {

        final SecurityOperationException recoverableError = keystoreLoadOperationResult.getNextRecoverableError();

        if (recoverableError == null) {
            // all passwords retrieved
            List<X509LoadOperation> loadOperations = keystoreLoadOperationResult.getRemainingLoadOperations();
            if (loadOperations.size() > 0) {
                keystoreLoadOperationResult = null;
                new AsyncX509LoaderTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, loadOperations.toArray(new X509LoadOperation[0]));
            }
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(context, R.style.Theme_App_EditPages));
                builder.setOnCancelListener(dialog -> {
                    keystoreLoadOperationResult.cancelAllLoadOperations();
                    getUIHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.import_cancelled));
                });
                builder.setTitle(R.string.alert_password_entry)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    @NonNull EditText passwordEditText = Objects.requireNonNull(alertDialog.findViewById(R.id.keystore_password_editText));
                    char[] pass = new char[passwordEditText.getText().length()];
                    passwordEditText.getText().getChars(0, passwordEditText.getText().length(), pass, 0);
                    alertDialog.dismiss();
                    keystoreLoadOperationResult.addPasswordForRerun(recoverableError, pass);
                    processRecoverableErrors(((AlertDialog)dialog).getContext());
                }
                )
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> {
                    // cancel the load of this password protected keystore or alias as appropriate
                    keystoreLoadOperationResult.cancelLoadOperation(recoverableError);
                    processRecoverableErrors(((AlertDialog)dialog).getContext());
                    String importObjectType;
                    String object;
                    if (recoverableError instanceof KeyStoreOperationException) {
                        importObjectType = getString(R.string.heading_keystore_filename);
                        object = new File(recoverableError.getDataSource()).getName();
                    } else {
                        importObjectType = getString(R.string.heading_keystore_alias);
                        object = ((KeyStoreContentException)recoverableError).getAlias();
                    }
                    getUIHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.import_x_of_type_y_cancelled_pattern, object, importObjectType));
                });

        View v = null;
        if (recoverableError instanceof KeyStoreOperationException) {
            // request keystore password
            v = LayoutInflater.from(builder.getContext()).inflate(R.layout.layout_keystore_password_entry, null);
        } else if (recoverableError instanceof KeyStoreContentException) {
            // request keystore alias key password
            v = LayoutInflater.from(builder.getContext()).inflate(R.layout.layout_keystore_key_password_entry, null);

            KeyStoreContentException e = (KeyStoreContentException) recoverableError;

            TextView keystoreAliasEditText = v.findViewById(R.id.keystore_alias_viewText);
            keystoreAliasEditText.setText(e.getAlias());
        }

        CheckBox viewUnencryptedToggle = Objects.requireNonNull(v).findViewById(R.id.toggle_visibility);
        if (viewUnencryptedToggle != null) {
            EditText passwordField = v.findViewById(R.id.keystore_password_editText);
            viewUnencryptedToggle.setOnCheckedChangeListener(new PasswordInputToggle(passwordField));
        }

        TextView filenameEditText = v.findViewById(R.id.keystore_filename_viewText);
        String filename = new File(recoverableError.getDataSource()).getName();
        filenameEditText.setText(filename);

        builder.setView(v);
        alertDialog = builder.show();
    }

    public static DialogFragment newInstance(String key) {
        final KeystorePreferenceDialogFragmentCompat<?,?> fragment =
                new KeystorePreferenceDialogFragmentCompat<>();
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

        private @NonNull Context getContext() {
            return Objects.requireNonNull(contextRef.get());
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_PRIVATE_KEY) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.layout_x509key_actionable_list_item, parent, false);
                return new KeyStorePrivateKeyItemViewHolder(view);
            } else if (viewType == VIEW_TYPE_CERTIFICATE) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.layout_x509cert_actionable_list_item, parent, false);
                return new KeyStoreCertificateItemViewHolder(view);
            } else {
                throw new RuntimeException("Unsupported view type : " + viewType);
            }

        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_PRIVATE_KEY:
                    populatePrivateKeyDetails((KeyStorePrivateKeyItemViewHolder) holder, position, getPrivateKeyEntry(position));
                    break;
                case VIEW_TYPE_CERTIFICATE:
                    populateCertificateDetails((KeyStoreCertificateItemViewHolder) holder, position, getTrustedCertificateEntry(position));
            }
        }

        private KeyStore.PrivateKeyEntry getPrivateKeyEntry(int position) {
            return (KeyStore.PrivateKeyEntry)getItem(position);
        }

        private KeyStore.TrustedCertificateEntry getTrustedCertificateEntry(int position) {
            KeyStore.Entry entry = getItem(position);
            return (KeyStore.TrustedCertificateEntry)entry;
        }

        @Override
        public int getItemViewType(int position) {
            KeyStore.Entry item = getItem(position);
            if (item instanceof KeyStore.PrivateKeyEntry) {
                return VIEW_TYPE_PRIVATE_KEY;
            } else if (item instanceof KeyStore.TrustedCertificateEntry) {
                return VIEW_TYPE_CERTIFICATE;
            } else {
                throw new RuntimeException("Unsupported keystore entry type : " + Utils.getId(item));
            }
        }

        public KeyStore.Entry getItem(int position) {
            try {
                if (aliasesList.size() <= position) {
                    throw new IllegalArgumentException("Keystore does not contain that many entries");
                }
                String alias = aliasesList.get(position);
                return getItem(backingObjectStore, alias);
            } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException e) {
                Logging.recordException(e);
                throw new RuntimeException(e);
            }
        }

        protected KeyStore.Entry getItem(KeyStore keystore, String alias) throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException {
            char[] pass = getKeyPass(alias);
            KeyStore.PasswordProtection protection = null;
            if (pass != null && pass.length > 0) {
                protection = new KeyStore.PasswordProtection(pass);
            }
            return keystore.getEntry(alias, protection);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        private void populatePrivateKeyDetails(KeyStorePrivateKeyItemViewHolder viewHolder, final int position, KeyStore.PrivateKeyEntry item) {

            PrivateKey privateKey = item.getPrivateKey();
            viewHolder.keyTypeField.setText(getContext().getString(R.string.x509_key_type_field_pattern, privateKey.getAlgorithm()));
            viewHolder.keyStrengthField.setText(getContext().getString(R.string.x509_key_strength_field_pattern, ((RSAPrivateKey) privateKey).getModulus().bitLength()));

            X509Certificate cert = (X509Certificate) item.getCertificate();
            fillCertificateFields(cert, viewHolder);
            viewHolder.deleteButton.setOnClickListener(v -> onDeleteItem(position));
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
            viewHolder.certNameField.setText(getContext().getString(R.string.x509_cert_name_field_pattern, certSubject));
            viewHolder.certVerifiedByField.setText(getContext().getString(R.string.x509_cert_verified_by_field_pattern, issuerSubject));
            viewHolder.certValidFromField.setText(getContext().getString(R.string.x509_cert_valid_from_field_pattern, sdf.format(cert.getNotBefore())));
            viewHolder.certValidToField.setText(getContext().getString(R.string.x509_cert_valid_to_field_pattern, sdf.format(cert.getNotAfter())));
        }

        private void populateCertificateDetails(KeyStoreCertificateItemViewHolder viewHolder, final int position, KeyStore.TrustedCertificateEntry item) {

            X509Certificate cert = (X509Certificate) item.getTrustedCertificate();
            fillCertificateFields(cert, viewHolder);
            viewHolder.deleteButton.setOnClickListener(v -> onDeleteItem(position));
        }

        private void onDeleteItem(int position) {
            try {
                backingObjectStore.deleteEntry(aliasesList.get(position));
                aliasesList.remove(position);
                notifyDataSetChanged();
            } catch (KeyStoreException e) {
                Logging.recordException(e);
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

    static class AsyncX509LoaderTask extends OwnedSafeAsyncTask<KeystorePreferenceDialogFragmentCompat<?,?>, X509LoadOperation, Integer, LoadOperationResult> {

        private AsyncX509LoaderTask(KeystorePreferenceDialogFragmentCompat<?,?> sourcePref) {
            super(sourcePref);
            withContext(sourcePref.requireContext());
        }

        @Override
        protected LoadOperationResult doInBackgroundSafely(X509LoadOperation... loadOps) {
            LoadOperationResult loadOperationResult = new LoadOperationResult();
            int currentFile = 0;
            for (X509LoadOperation loadOp : loadOps) {
                String fileExt = IOUtils.getFileExt(getContext(), loadOp.getFileUri()).toLowerCase();
                if (getOwner().getPreference().getAllowedCertificateFileTypes().contains(fileExt)) {
                    try {
                        Collection<X509Certificate> certs = X509Utils.loadCertificatesFromUri(getContext(), loadOp.getFileUri());
                        CertificateLoadOperationResult result = new CertificateLoadOperationResult(loadOp);
                        result.getCerts().addAll(certs);
                        loadOperationResult.getCertLoadResults().add(result);
                    } catch (CertificateLoadException e) {
                        Logging.recordException(e);
                        CertificateLoadOperationResult result = new CertificateLoadOperationResult(loadOp);
                        result.setException(e);
                        loadOperationResult.getCertLoadResults().add(result);
                    }
                } else if (getOwner().getPreference().getAllowedKeyFileTypes().contains(fileExt)) {
                    KeystoreLoadOperationResult keystoreLoadOperationResult;
                    if (KeyStorePreference.BKS_FILE_SUFFIX.equals(fileExt)) {
                        keystoreLoadOperationResult = X509Utils.loadCertificatesAndPrivateKeysFromKeystoreFile(KeystoreLoadOperation.from(loadOp), "bks", getContext());
                    } else {
                        keystoreLoadOperationResult = X509Utils.loadCertificatesAndPrivateKeysFromPkcs12KeystoreFile(getContext(), KeystoreLoadOperation.from(loadOp));
                    }
                    loadOperationResult.getKeystoreLoadResults().add(keystoreLoadOperationResult);
                }
                currentFile++;
                publishProgress((int) Math.rint(100 * ((double) currentFile / loadOps.length)));
                getOwner().appSettingsViewModel.removeAllUriPermissionsRecords(getContext(), loadOp.getFileUri(), getOwner().getUriPermissionsKey());
            }
            return loadOperationResult;
        }

        @Override
        protected void onProgressUpdateSafely(Integer... values) {
            for (Integer i : values) {
                getOwner().onProgressUpdate(i);
            }
        }

        @Override
        protected void onPostExecuteSafely(LoadOperationResult loadOperationResult) {
            getOwner().onKeystoreLoadFinished(loadOperationResult, isCancelled());
        }
    }

    public void onCertificatesSelected(ArrayList<DocumentFile> certificateFiles) {
        X509LoadOperation[] params = new X509LoadOperation[certificateFiles.size()];
        int i = 0;
        for (DocumentFile f : certificateFiles) {
            params[i++] = new X509LoadOperation(f.getUri());
        }
        keystoreLoadOperationResult = null;
        keystoreLoadProgress = 0;
        new AsyncX509LoaderTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }

    private void onKeystoreLoadFinished(LoadOperationResult loadOperationResult, boolean wasLoadCancelled) {
        keystoreLoadOperationResult = loadOperationResult;
        if (!wasLoadCancelled) {
            @NonNull KeyStoreContentsAdapter adapter = ((KeyStoreContentsAdapter) Objects.requireNonNull(certificateList.getAdapter()));
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
            processRecoverableErrors(requireContext());
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
            sb.append(requireContext().getString(R.string.error_heading_unloadable_certificate_files));
            sb.append('\n');
            for (CertificateLoadException ex : certExceptions) {
                sb.append(ex.getDataSource());
                sb.append('\n');
            }
        }
        if (keystoreExceptions.size() > 0) {
            // add keystore exceptions list
            sb.append(requireContext().getString(R.string.error_heading_unloadable_keystore_files));
            sb.append('\n');
            for (KeyStoreOperationException ex : keystoreExceptions) {
                sb.append(ex.getDataSource());
                sb.append('\n');
            }
        }
        if (keystoreContentExceptions.size() > 0) {
            // add keystore content exceptions list
            sb.append(requireContext().getString(R.string.error_heading_unloadable_keystore_aliases));
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
            onCertificatesSelected(event.getSelectedFolderItemsAsFiles());
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
        outState.putParcelable(STATE_LOAD_OP_RESULT, keystoreLoadOperationResult);
        outState.putInt(STATE_LOAD_PROGRESS, keystoreLoadProgress);
        outState.putByteArray(STATE_KEYSTORE, X509Utils.saveKeystore("byteArray", keystore, keystorePass));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            trackedRequest = savedInstanceState.getInt(STATE_TRACKED_REQUEST, -1);
            keystoreLoadOperationResult = savedInstanceState.getParcelable(STATE_LOAD_OP_RESULT);
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
