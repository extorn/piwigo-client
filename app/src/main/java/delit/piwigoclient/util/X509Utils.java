package delit.piwigoclient.util;

import android.content.Context;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.BuildConfig;

/**
 * Created by gareth on 15/07/17.
 */

public class X509Utils {
    private static final String TAG = "X509Utils";
    private static final char[] clientKeystorePass = new char[]{'!','P','1','r','4','t','3','5','!'};
    private static final char[] trustStorePass = new char[]{'!','P','1','r','4','t','3','5','!'};

    public static char[] getClientKeystorePass() {
        return clientKeystorePass;
    }

    public static KeyStore loadClientKeystore(Context context) {
        return loadKeystore(context, "clientKeystore." + KeyStore.getDefaultType(), clientKeystorePass);
    }

    public static void saveClientKeystore(Context context, KeyStore clientKeystore) {
        saveKeystore(context, clientKeystore, clientKeystorePass, "clientKeystore." + KeyStore.getDefaultType());
    }

    public static KeyStore loadTrustedCaKeystore(Context context) {
        return loadKeystore(context, "trustStore." + KeyStore.getDefaultType(), trustStorePass);
    }

    public static void saveTrustedCaKeystore(Context context, KeyStore clientKeystore) {
        saveKeystore(context, clientKeystore, trustStorePass, "trustStore." + KeyStore.getDefaultType());
    }

    public static void saveKeystore(Context context, KeyStore keystore, char[] keystorePassword, String keystoreFilename) {

        File appDataDir = context.getApplicationContext().getFilesDir();
        if(!appDataDir.exists()) {
            appDataDir.mkdir();
        }
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(new File(appDataDir, keystoreFilename)));
            keystore.store(bos, keystorePassword);
        } catch (FileNotFoundException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreFilename, e);
            }
        } catch (CertificateException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreFilename, e);
            }
        } catch (NoSuchAlgorithmException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreFilename, e);
            }
        } catch (KeyStoreException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreFilename, e);
            }
        } catch (IOException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error saving keystore : " + keystoreFilename, e);
            }
        } finally {
            if(bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    if(BuildConfig.DEBUG) {
                        Log.e(TAG, "Error closing keystore after save : " + keystoreFilename, e);
                    }
                }
            }
        }
    }

    public static KeyStore buildEmptyKeystore() throws KeyStoreException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try {
            ks.load(null, null);
        } catch (IOException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error creating blank keystore ", e);
            }
        } catch (NoSuchAlgorithmException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error creating blank keystore ", e);
            }
        } catch (CertificateException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error creating blank keystore ", e);
            }
        }
        return ks;
    }

    public static KeyStore buildPopulatedKeystore(Map<Key, X509Certificate[]> keystoreContent) throws KeyStoreException {
        KeyStore keystore = X509Utils.buildEmptyKeystore();
        if(keystoreContent != null) {
            int i = 0;
            for (Map.Entry<Key,X509Certificate[]> entry : keystoreContent.entrySet()) {
                if(entry.getKey() instanceof PublicKey) {
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

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static KeyStore buildPopulatedKeystore(Collection<X509Certificate> certs) throws KeyStoreException {
        KeyStore keystore = X509Utils.buildEmptyKeystore();
        if(certs != null) {
            int i = 0;
            for (X509Certificate cert : certs) {
                keystore.setCertificateEntry(String.valueOf(i++), cert);
            }
        }
        return keystore;
    }

    public static KeyStore loadKeystore(Context context, String keystoreFilename, char[] keystorePassword) {
        File appDataDir = context.getApplicationContext().getFilesDir();
        if(!appDataDir.exists()) {
            appDataDir.mkdir();
        }
        File keystoreFile = new File(appDataDir, keystoreFilename);
        KeyStore keystore = null;
        try {
            keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null, null);
        } catch (KeyStoreException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error generating blank keystore : " + keystoreFilename, e);
            }
        } catch (CertificateException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error generating blank keystore : " + keystoreFilename, e);
            }
        } catch (NoSuchAlgorithmException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error generating blank keystore : " + keystoreFilename, e);
            }
        } catch (IOException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error generating blank keystore : " + keystoreFilename, e);
            }
        }
        if(keystore != null && keystoreFile.exists()) {
            BufferedInputStream keystoreInput = null;
            try {
                keystoreInput = new BufferedInputStream(new FileInputStream(keystoreFile));
                keystore.load(keystoreInput, keystorePassword);
            } catch (CertificateException e) {
                if(BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading keystore : " + keystoreFilename, e);
                }
            } catch (NoSuchAlgorithmException e) {
                if(BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading keystore : " + keystoreFilename, e);
                }
            } catch (FileNotFoundException e) {
                if(BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading keystore : " + keystoreFilename, e);
                }
            } catch (IOException e) {
                if(BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading keystore : " + keystoreFilename, e);
                }
            } finally {
                if (keystoreInput != null) {
                    try {
                        keystoreInput.close();
                    } catch (IOException e) {
                        if(BuildConfig.DEBUG) {
                            Log.e(TAG, "Error closing keystore after load : " + keystoreFilename, e);
                        }
                    }
                }
            }
        }
        return keystore;
    }

    public static String getCertificateThumbprint(Certificate x509Certificate) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        byte[] publicKey = md.digest(x509Certificate.getPublicKey().getEncoded());

        StringBuffer hexString = new StringBuffer();
        for (int i=0;i<publicKey.length;i++) {
            String appendString = Integer.toHexString(0xFF & publicKey[i]);
            if(appendString.length()==1) {
                hexString.append("0");
            }
            hexString.append(appendString);
        }
        String thumbprint = hexString.toString();
        Log.d(TAG, "Cer: "+ thumbprint);
        return thumbprint;
    }

    public static HashSet<X509Certificate> loadCertificatesFromKeystore(KeyStore keystore) {
        HashSet<X509Certificate> certs = new HashSet<X509Certificate>();
        try {
            Enumeration<String> aliases = keystore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                certs.add((X509Certificate) keystore.getCertificate(alias));
            }
        } catch (KeyStoreException e) {
            Log.e(TAG, "Error extracting certificates from store", e);
        }
        return certs;
    }

    public static Set<String> listAliasesInStore(KeyStore keystore) {
        Set<String> aliases = new HashSet<String>();
        try {
            Enumeration<String> aliasesEnum = keystore.aliases();
            while (aliasesEnum.hasMoreElements()) {
                aliases.add(aliasesEnum.nextElement());
            }
        } catch (KeyStoreException e) {
            Log.e(TAG, "Error listing aliases", e);
        }
        return aliases;
    }

    public static X509Certificate loadCertificateFromFile(File f) {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(f));
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(bis);
            return cert;
        } catch (FileNotFoundException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading certificate file stream", e);
            }
            throw new RuntimeException("Error reading certificate file stream", e);
        } catch (CertificateException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading certificate file stream", e);
            }
            throw new RuntimeException("Error reading certificate file stream", e);
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Error closing certificate file stream", e);
                    }
                    throw new RuntimeException("Error closing certificate file stream", e);
                }
            }
        }
    }

    public static Map<Key, Certificate[]> loadCertificatesAndPrivateKeysFromDefaultFormatKeystoreFile(File f, char[] keystorePass) {
        return loadCertificatesAndPrivateKeysFromKeystoreFile(f, KeyStore.getDefaultType(), keystorePass);
    }

    public static Map<Key, Certificate[]> loadCertificatesAndPrivateKeysFromPkcs12KeystoreFile(File f, char[] keystorePass) {
        return loadCertificatesAndPrivateKeysFromKeystoreFile(f, "pkcs12", keystorePass);
    }

    public static Map<Key, Certificate[]> loadCertificatesAndPrivateKeysFromKeystoreFile(File f, String keystoreType, char[] keystorePass) {
        try {
            KeyStore keystore = KeyStore.getInstance(keystoreType);
            keystore.load(new FileInputStream(f), keystorePass);
            return loadCertificatesAndPrivateKeysFromKeystore(keystore);
        } catch (FileNotFoundException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            throw new RuntimeException("Error reading " + keystoreType + " file stream", e);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            throw new RuntimeException("Error reading " + keystoreType + " file stream", e);
        } catch (CertificateException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            throw new RuntimeException("Error reading " + keystoreType + " file stream", e);
        } catch (NoSuchAlgorithmException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            throw new RuntimeException("Error reading " + keystoreType + " file stream", e);
        } catch (KeyStoreException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystoreType + " file stream", e);
            }
            throw new RuntimeException("Error reading " + keystoreType + " file stream", e);
        }
    }

    public static HashMap<Key, Certificate[]> loadCertificatesAndPrivateKeysFromKeystore(KeyStore keystore) {
        try {

            Enumeration e = keystore.aliases();
            HashMap<Key, Certificate[]> certs = new HashMap<>();
            char[] blankKey = new char[0];
            while (e.hasMoreElements()) {
                String alias = (String) e.nextElement();
                Certificate[] certChain = (Certificate[]) keystore.getCertificateChain(alias);
                Key key = keystore.getKey(alias, blankKey);
                if(key == null) {
                    //TODO check this is the right cert
                    key = certChain[0].getPublicKey();
                }
                certs.put(key, certChain);
            }
            return certs;
        } catch (UnrecoverableKeyException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystore.getType() + " file stream", e);
            }
            throw new RuntimeException("Error reading " + keystore.getType() + " file stream", e);
        } catch (NoSuchAlgorithmException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystore.getType() + " file stream", e);
            }
            throw new RuntimeException("Error reading " + keystore.getType() + " file stream", e);
        } catch (KeyStoreException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading " + keystore.getType() + " file stream", e);
            }
            throw new RuntimeException("Error reading " + keystore.getType() + " file stream", e);
        }
    }

    public static KeyStore cloneKeystore(KeyStore mValue) {
        char[] blankPass = new char[0];
        byte[] ksBytes = serialiseKeystore(mValue, blankPass);
        KeyStore clone = deserialiseKeystore(ksBytes, blankPass, mValue.getType());
        return clone;
    }

    public static KeyStore deserialiseKeystore(byte[] ksBytes, char[] ksPass, String ksType) {
        try {
            KeyStore ks = KeyStore.getInstance(ksType);
            ks.load(new ByteArrayInputStream(ksBytes), ksPass);
            return ks;
        } catch (CertificateException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error deserialising keystore", e);
            }
            throw new RuntimeException("Error deserialising keystore", e);
        } catch (NoSuchAlgorithmException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error deserialising keystore", e);
            }
            throw new RuntimeException("Error deserialising keystore", e);
        } catch (KeyStoreException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error deserialising keystore", e);
            }
            throw new RuntimeException("Error deserialising keystore", e);
        } catch (IOException e) {
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
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error serialising keystore", e);
            }
            throw new RuntimeException("Error serialising keystore", e);
        } catch (NoSuchAlgorithmException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error serialising keystore", e);
            }
            throw new RuntimeException("Error serialising keystore", e);
        } catch (KeyStoreException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error serialising keystore", e);
            }
            throw new RuntimeException("Error serialising keystore", e);
        } catch (IOException e) {
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
            while(aliases.hasMoreElements()) {
                aliasesList.add(aliases.nextElement());
            }
            return aliasesList;
        } catch (KeyStoreException e) {
            throw new RuntimeException("Error compiling alias list", e);
        }
    }

    public static void addToKeystore(KeyStore keyStore, Map<Key, Certificate[]> keystoreContent) {
        char[] blankKey = new char[0];
        try {
            List<String> keystoreAliases = extractAliasesFromKeystore(keyStore);
            for(Map.Entry<Key, Certificate[]> newVal : keystoreContent.entrySet()) {
                String thumbprint = X509Utils.getCertificateThumbprint(newVal.getValue()[0]);

                if(newVal.getKey() instanceof PrivateKey) {
                    if(keystoreAliases.contains(thumbprint)) {
                        // remove the current entry because we're adding a key essentially.
                        keyStore.deleteEntry(thumbprint);
                    }
                    //Note: we may well end up with multiple entries with identical aliases - this is technically valid for a combined truststore and keystore, but not fully supported.
                    keyStore.setKeyEntry(thumbprint, newVal.getKey(), blankKey, newVal.getValue());
                } else {
                    try {
                        keyStore.setCertificateEntry(thumbprint, newVal.getValue()[0]);
                    } catch (KeyStoreException e) {
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "Error inserting data into keystore", e);
                        }
                        if(!keystoreAliases.contains(thumbprint)) {
                            throw new RuntimeException("Error inserting data into keystore", e);
                        } else {
                            // If the alias exists then because the alias is the thumbprint, the entry must be a private key entry so ignore the error
                        }
                    }
                }
                keystoreAliases.add(thumbprint);
            }
        } catch (KeyStoreException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error inserting data into keystore", e);
            }
            throw new RuntimeException("Error inserting data into keystore", e);
        } catch (NoSuchAlgorithmException e) {
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
        if(!SetUtils.equals(firstAliases, secondAliases)) {
            return false;
        }
        char[] storePass = new char[0];
        byte[] firstBytes = serialiseKeystore(first, storePass);
        byte[] secondBytes = serialiseKeystore(second, storePass);
        return Arrays.equals(firstBytes, secondBytes);
    }
}
