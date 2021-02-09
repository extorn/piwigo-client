package delit.libs.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.BuildConfig;
import delit.libs.core.util.Logging;
import delit.libs.util.security.CertificateLoadException;
import delit.libs.util.security.KeyStoreContentException;
import delit.libs.util.security.KeyStoreOperationException;
import delit.libs.util.security.KeystoreLoadOperation;
import delit.libs.util.security.KeystoreLoadOperationResult;
import delit.libs.util.security.SecurityOperationException;

/**
 * Created by gareth on 15/07/17.
 */

public class X509Utils {
    private static final String TAG = "X509Utils";
    private static final char[] clientKeystorePass = new char[]{'!', 'P', '1', 'r', '4', 't', '3', '5', '!'};
    private static final char[] trustStorePass = new char[]{'!', 'P', '1', 'r', '4', 't', '3', '5', '!'};
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static char[] getClientKeystorePass() {
        return clientKeystorePass;
    }

    public static KeyStore loadClientKeystore(Context context) {
        KeyStore keystore = loadKeystore(context, "clientKeystore." + KeyStore.getDefaultType(), clientKeystorePass);
        if(keystore == null) {
            try {
                keystore = buildEmptyKeystore();
            } catch (KeyStoreException e) {
                Logging.log(Log.ERROR, TAG, "Unable to build empty keystore");
            }
        }
        return keystore;
    }

    public static void saveClientKeystore(Context context, KeyStore clientKeystore) {
        saveKeystore(context, clientKeystore, clientKeystorePass, "clientKeystore." + KeyStore.getDefaultType());
    }

    public static KeyStore loadTrustedCaKeystore(Context context) {
        KeyStore keystore = loadKeystore(context, "trustStore." + KeyStore.getDefaultType(), trustStorePass);
        if(keystore == null) {
            try {
                keystore = buildEmptyKeystore();
            } catch (KeyStoreException e) {
                Logging.log(Log.ERROR, TAG, "Unable to build empty keystore");
            }
        }
        return keystore;
    }

    public static void saveTrustedCaKeystore(Context context, KeyStore clientKeystore) {
        saveKeystore(context, clientKeystore, trustStorePass, "trustStore." + KeyStore.getDefaultType());
    }

    public static byte[] saveKeystore(String keystoreDestId, KeyStore keystore, char[] keystorePass) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        saveKeystore(keystoreDestId, keystore, new BufferedOutputStream(baos), keystorePass);
        return baos.toByteArray();
    }

    public static void saveKeystore(String keystoreDestId, KeyStore keystore, BufferedOutputStream bos, char[] keystorePass) {
        try {
            keystore.store(bos, keystorePass);
        } catch (CertificateException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreDestId, e);
            }
        } catch (NoSuchAlgorithmException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreDestId, e);
            }
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreDestId, e);
            }
        } catch (IOException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreDestId, e);
            }
        }
    }

    public static void saveKeystore(Context context, KeyStore keystore, char[] keystorePassword, String keystoreFilename) {

        File appDataDir = context.getFilesDir();
        if (!appDataDir.exists()) {
            if(!appDataDir.mkdir()) {
                Logging.log(Log.ERROR, TAG, "Error saving keystore (folder couldn't be created) : " + keystoreFilename);
            }
        }
        try(BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(appDataDir, keystoreFilename)))) {

            saveKeystore(keystoreFilename, keystore, bos, keystorePassword);
        } catch (IOException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreFilename, e);
            }
        }
    }

    public static KeyStore buildEmptyKeystore() throws KeyStoreException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try {
            ks.load(null, null);
        } catch (IOException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error creating blank keystore ", e);
            }
        } catch (NoSuchAlgorithmException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error creating blank keystore ", e);
            }
        } catch (CertificateException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error creating blank keystore ", e);
            }
        }
        return ks;
    }

    public static KeyStore buildPopulatedKeystore(Map<Key, X509Certificate[]> keystoreContent) throws KeyStoreException {
        KeyStore keystore = buildEmptyKeystore();
        if (keystoreContent != null) {
            int i = 0;
            for (Map.Entry<Key, X509Certificate[]> entry : keystoreContent.entrySet()) {
                if (entry.getKey() instanceof PublicKey) {
                    keystore.setCertificateEntry(String.valueOf(i++), entry.getValue()[0]);
                } else {
                    keystore.setKeyEntry(String.valueOf(i++), entry.getKey(), new char[0], entry.getValue());
                }
            }
        }
        return keystore;
    }

    public static String getThumbprint(X509Certificate cert)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        String digestHex = bytesToHex(digest);
        return digestHex.toLowerCase();
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static KeyStore buildPopulatedKeystore(Collection<X509Certificate> certs) throws KeyStoreException {
        KeyStore keystore = buildEmptyKeystore();
        if (certs != null) {
            int i = 0;
            for (X509Certificate cert : certs) {
                keystore.setCertificateEntry(String.valueOf(i++), cert);
            }
        }
        return keystore;
    }

    public static KeyStore loadKeystore(String keystoreSrcId, byte[] bytes, char[] keystorePassword) {
        return loadKeystore(keystoreSrcId, new BufferedInputStream(new ByteArrayInputStream(bytes)), keystorePassword);
    }

    public static KeyStore loadKeystore(String keystoreSrcId, BufferedInputStream keystoreInput, char[] keystorePassword) {
        KeyStore keystore = null;
        try {
            keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null, null);
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error generating blank keystore : " + keystoreSrcId, e);
            }
        } catch (CertificateException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error generating blank keystore : " + keystoreSrcId, e);
            }
        } catch (NoSuchAlgorithmException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error generating blank keystore : " + keystoreSrcId, e);
            }
        } catch (IOException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error generating blank keystore : " + keystoreSrcId, e);
            }
        }
        if (keystore != null) {
            try {
                keystore.load(keystoreInput, keystorePassword);
            } catch (CertificateException e) {
                Logging.recordException(e);
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading keystore : " + keystoreSrcId, e);
                }
            } catch (NoSuchAlgorithmException e) {
                Logging.recordException(e);
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading keystore : " + keystoreSrcId, e);
                }
            } catch (IOException e) {
                Logging.recordException(e);
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading keystore : " + keystoreSrcId, e);
                }
            } finally {
                if (keystoreInput != null) {
                    try {
                        keystoreInput.close();
                    } catch (IOException e) {
                        Logging.recordException(e);
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "Error closing keystore after load : " + keystoreSrcId, e);
                        }
                    }
                }
            }
        }
        return keystore;
    }

    public static KeyStore loadKeystore(Context context, String keystoreFilename, char[] keystorePassword) {
        File appDataDir = context.getFilesDir();
        if (!appDataDir.exists()) {
            Logging.log(Log.ERROR, TAG, "Error loading keystore (source folder doesn't exist) : " + keystoreFilename);
            return null;
        }
        File keystoreFile = new File(appDataDir, keystoreFilename);
        if(keystoreFile.exists()) {
            try {
                BufferedInputStream keystoreInput = new BufferedInputStream(new FileInputStream(keystoreFile));
                return loadKeystore(keystoreFilename, keystoreInput, keystorePassword);
            } catch (FileNotFoundException e) {
                Logging.recordException(e);
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading keystore : " + keystoreFilename, e);
                }
            }
        }
        return null;
    }

    public static String getCertificateThumbprint(Certificate x509Certificate) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        byte[] publicKey = md.digest(x509Certificate.getPublicKey().getEncoded());

        StringBuilder hexString = new StringBuilder();
        for (byte aPublicKey : publicKey) {
            String appendString = Integer.toHexString(0xFF & aPublicKey);
            if (appendString.length() == 1) {
                hexString.append("0");
            }
            hexString.append(appendString);
        }
        String thumbprint = hexString.toString();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Cer: " + thumbprint);
        }
        return thumbprint;
    }

    public static HashSet<X509Certificate> loadCertificatesFromKeystore(KeyStore keystore) {
        HashSet<X509Certificate> certs = new HashSet<>();
        try {
            Enumeration<String> aliases = keystore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                certs.add((X509Certificate) keystore.getCertificate(alias));
            }
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error extracting certificates from store", e);
            }
        }
        return certs;
    }

    public static Set<String> listAliasesInStore(KeyStore keystore) {
        Set<String> aliases = new HashSet<>();
        try {
            Enumeration<String> aliasesEnum = keystore.aliases();
            while (aliasesEnum.hasMoreElements()) {
                aliases.add(aliasesEnum.nextElement());
            }
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error listing aliases", e);
            }
        }
        return aliases;
    }

    public static Collection<X509Certificate> loadCertificatesFromUri(Context context, Uri uri) {

        try(InputStream is = context.getContentResolver().openInputStream(uri)){
            if(is == null) {
                throw new IOException("unable to open input stream to uri " + uri);
            }
            try (BufferedInputStream bis = new BufferedInputStream(is)) {
                return loadCertificatesFromStream(bis, uri);
            }
        } catch (IOException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading certificate file stream", e);
            }
            throw new CertificateLoadException(uri.getPath(), "Error reading certificate file stream", e);
        }
    }


    public static Collection<X509Certificate> loadCertificatesFromStream(InputStream is, Uri certificateSource) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (Collection<X509Certificate>) cf.generateCertificates(is);
        } catch (CertificateException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading certificate file stream", e);
            }
            throw new CertificateLoadException(certificateSource.getPath(), "Error reading certificate file stream", e);
        }
    }

    public static KeystoreLoadOperationResult loadCertificatesAndPrivateKeysFromDefaultFormatKeystoreFile(Context  context, KeystoreLoadOperation loadOperation) {
        return loadCertificatesAndPrivateKeysFromKeystoreFile(loadOperation, KeyStore.getDefaultType(), context);
    }

    public static KeystoreLoadOperationResult loadCertificatesAndPrivateKeysFromPkcs12KeystoreFile(Context context, KeystoreLoadOperation loadOperation) {
        return loadCertificatesAndPrivateKeysFromKeystoreFile(loadOperation, "pkcs12", context);
    }

    public static KeystoreLoadOperationResult loadCertificatesAndPrivateKeysFromKeystoreFile(KeystoreLoadOperation loadOperation, String keystoreType, Context context) {
        KeystoreLoadOperationResult result = new KeystoreLoadOperationResult(loadOperation);

        try {
            ContentResolver contentResolver = context.getContentResolver();
            InputStream inputStream = new BufferedInputStream(Objects.requireNonNull(contentResolver.openInputStream(loadOperation.getFileUri())));
            KeyStore keystore = KeyStore.getInstance(keystoreType);
            keystore.load(inputStream, loadOperation.getKeystorePass());
            loadCertificatesAndPrivateKeysFromKeystore(loadOperation.getFileUri().getPath(), keystore, loadOperation.getAliasesToLoad(), loadOperation.getAliasPassMapp(), result);
            for (SecurityOperationException ex : result.getExceptionList()) {
                ex.setDataSource(loadOperation.getFileUri().getPath());
            }
        } catch (FileNotFoundException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            result.addException(new KeyStoreOperationException(loadOperation.getFileUri().getPath(), "Error reading " + keystoreType + " file stream", e));
        } catch (IOException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            result.addException(new KeyStoreOperationException(loadOperation.getFileUri().getPath(), "Error reading " + keystoreType + " file stream", e));
        } catch (CertificateException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            result.addException(new KeyStoreOperationException(loadOperation.getFileUri().getPath(), "Error reading " + keystoreType + " file stream", e));
        } catch (NoSuchAlgorithmException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            result.addException(new KeyStoreOperationException(loadOperation.getFileUri().getPath(), "Error reading " + keystoreType + " file stream", e));
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            result.addException(new KeyStoreOperationException(loadOperation.getFileUri().getPath(), "Error reading " + keystoreType + " file stream", e));
        }
        return result;
    }

    /**
     * @param keystore         to load from
     * @param aliasesToLoad    null to load all
     * @param aliasPasswordMap passwords for any keys protected
     * @param result
     * @return
     */
    public static KeystoreLoadOperationResult loadCertificatesAndPrivateKeysFromKeystore(String keystoreName, KeyStore keystore, List<String> aliasesToLoad, Map<String, char[]> aliasPasswordMap, KeystoreLoadOperationResult result) {

        String alias = null;
        try {
            Enumeration aliasesEnum = keystore.aliases();

            Map<Key, Certificate[]> certs = result.getKeystoreContent();
            char[] blankKey = new char[0];
            char[] keyPass;
            while (aliasesEnum.hasMoreElements()) {
                try {
                    alias = (String) aliasesEnum.nextElement();
                    if (aliasesToLoad != null && !aliasesToLoad.contains(alias)) {
                        //skip this alias
                        continue;
                    }
                    Certificate[] certChain = keystore.getCertificateChain(alias);
                    if (certChain != null) {
                        keyPass = aliasPasswordMap.get(alias);
                        if (keyPass == null) {
                            keyPass = blankKey;
                        }
                        Key key = keystore.getKey(alias, keyPass);
                        if (key == null) {
                            key = certChain[0].getPublicKey();
                        }
                        certs.put(key, certChain);
                    } else {
                        Logging.log(Log.WARN, TAG, "Unable to find certificate chain for alias in keystore");
                    }
                } catch (UnrecoverableKeyException e) {
                    Logging.recordException(e);
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Error reading " + keystore.getType() + " file stream", e);
                    }
                    result.addException(new KeyStoreContentException(keystoreName, alias, "Error reading " + keystore.getType() + " file stream", e));
                } catch (NoSuchAlgorithmException e) {
                    Logging.recordException(e);
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Error reading " + keystore.getType() + " file stream", e);
                    }
                    result.addException(new KeyStoreContentException(keystoreName, alias, "Error reading " + keystore.getType() + " file stream", e));
                } catch (KeyStoreException e) {
                    Logging.recordException(e);
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Error reading " + keystore.getType() + " file stream", e);
                    }
                    result.addException(new KeyStoreContentException(keystoreName, alias, "Error reading " + keystore.getType() + " file stream", e));
                }
            }
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystore.getType() + " file stream", e);
            }
            result.addException(new KeyStoreContentException(keystoreName, null, "Error reading " + keystore.getType() + " file stream", e));
        }
        return result;
    }

    public static KeyStore cloneKeystore(KeyStore mValue) {
        char[] blankPass = new char[0];
        byte[] ksBytes = serialiseKeystore(mValue, blankPass);
        return deserialiseKeystore(ksBytes, blankPass, mValue.getType());
    }

    public static KeyStore deserialiseKeystore(byte[] ksBytes, char[] ksPass, String ksType) {
        try {
            KeyStore ks = KeyStore.getInstance(ksType);
            ks.load(new ByteArrayInputStream(ksBytes), ksPass);
            return ks;
        } catch (CertificateException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error deserialising keystore", e);
            }
            throw new RuntimeException("Error deserialising keystore", e);
        } catch (NoSuchAlgorithmException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error deserialising keystore", e);
            }
            throw new RuntimeException("Error deserialising keystore", e);
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error deserialising keystore", e);
            }
            throw new RuntimeException("Error deserialising keystore", e);
        } catch (IOException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error deserialising keystore", e);
            }
            throw new RuntimeException("Error deserialising keystore", e);
        }
    }

    public static byte[] serialiseKeystore(KeyStore ks, char[] keystorePass) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ks.store(baos, keystorePass);
            return baos.toByteArray();
        } catch (CertificateException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error serialising keystore", e);
            }
            throw new RuntimeException("Error serialising keystore", e);
        } catch (NoSuchAlgorithmException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error serialising keystore", e);
            }
            throw new RuntimeException("Error serialising keystore", e);
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error serialising keystore", e);
            }
            throw new RuntimeException("Error serialising keystore", e);
        } catch (IOException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error serialising keystore", e);
            }
            throw new RuntimeException("Error serialising keystore", e);
        }
    }

    public static ArrayList<String> extractAliasesFromKeystore(KeyStore keyStore) {
        ArrayList<String> aliasesList;
        try {
            aliasesList = new ArrayList<>(keyStore.size());
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                aliasesList.add(aliases.nextElement());
            }
            return aliasesList;
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            throw new RuntimeException("Error compiling alias list", e);
        }
    }

    public static void addToKeystore(KeyStore keyStore, Map<Key, Certificate[]> keystoreContent) {
        char[] blankKey = new char[0];
        try {
            List<String> keystoreAliases = extractAliasesFromKeystore(keyStore);
            for (Map.Entry<Key, Certificate[]> newVal : keystoreContent.entrySet()) {
                String thumbprint = getCertificateThumbprint(newVal.getValue()[0]);

                if (newVal.getKey() instanceof PrivateKey) {
                    if (keystoreAliases.contains(thumbprint)) {
                        // remove the current entry because we're adding a key essentially.
                        keyStore.deleteEntry(thumbprint);
                    }
                    //Note: we may well end up with multiple entries with identical aliases - this is technically valid for a combined truststore and keystore, but not fully supported.
                    keyStore.setKeyEntry(thumbprint, newVal.getKey(), blankKey, newVal.getValue());
                } else {
                    try {
                        keyStore.setCertificateEntry(thumbprint, newVal.getValue()[0]);
                    } catch (KeyStoreException e) {
                        Logging.recordException(e);
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "Error inserting data into keystore", e);
                        }
                        if (!keystoreAliases.contains(thumbprint)) {
                            throw new RuntimeException("Error inserting data into keystore", e);
                        } else {
                            // If the alias exists then because the alias is the thumbprint, the entry must be a private key entry so ignore the error
                        }
                    }
                }
                keystoreAliases.add(thumbprint);
            }
        } catch (KeyStoreException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error inserting data into keystore", e);
            }
            throw new RuntimeException("Error inserting data into keystore", e);
        } catch (NoSuchAlgorithmException e) {
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error inserting data into keystore", e);
            }
            throw new RuntimeException("Error inserting data into keystore", e);
        }
    }

    /**
     * MAY return false even if the stores are equal depending on the implementation since
     * the contents are not recursively checked for equality. i.e. order of items in the keystore will affect equality.
     *
     * @param first
     * @param second
     * @return
     */
    public static boolean areEqual(KeyStore first, KeyStore second) {
        Set<String> firstAliases = listAliasesInStore(first);
        Set<String> secondAliases = listAliasesInStore(second);
        if (!CollectionUtils.equals(firstAliases, secondAliases)) {
            return false;
        }
        char[] storePass = new char[0];
        byte[] firstBytes = serialiseKeystore(first, storePass);
        byte[] secondBytes = serialiseKeystore(second, storePass);
        return Arrays.equals(firstBytes, secondBytes);
    }

    public static X509Certificate findFirstExpiredCert(List<? extends Certificate> certificates) {
        Date now = new Date();
        for (Certificate c : certificates) {
            X509Certificate cert = (X509Certificate) c;
            if (cert.getNotAfter().before(now)) {
                return cert;
            }
        }
        return null;
    }

    public static X509Certificate findFirstCertNotYetValid(List<? extends Certificate> certificates) {
        Date now = new Date();
        for (Certificate c : certificates) {
            X509Certificate cert = (X509Certificate) c;
            if (cert.getNotBefore().after(now)) {
                return cert;
            }
        }
        return null;
    }
}
