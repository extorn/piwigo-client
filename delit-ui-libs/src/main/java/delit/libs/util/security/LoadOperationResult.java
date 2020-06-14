package delit.libs.util.security;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;
import java.security.Key;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LoadOperationResult implements Parcelable {
    private final List<KeystoreLoadOperationResult> keystoreLoadResults;
    private final List<CertificateLoadOperationResult> certLoadResults;

    public LoadOperationResult() {
        keystoreLoadResults = new ArrayList<>();
        certLoadResults = new ArrayList<>();
    }

    protected LoadOperationResult(Parcel in) {
        keystoreLoadResults = in.createTypedArrayList(KeystoreLoadOperationResult.CREATOR);
        certLoadResults = in.createTypedArrayList(CertificateLoadOperationResult.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(keystoreLoadResults);
        dest.writeTypedList(certLoadResults);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<LoadOperationResult> CREATOR = new Creator<LoadOperationResult>() {
        @Override
        public LoadOperationResult createFromParcel(Parcel in) {
            return new LoadOperationResult(in);
        }

        @Override
        public LoadOperationResult[] newArray(int size) {
            return new LoadOperationResult[size];
        }
    };

    public List<CertificateLoadOperationResult> getCertLoadResults() {
        return certLoadResults;
    }

    public List<KeystoreLoadOperationResult> getKeystoreLoadResults() {
        return keystoreLoadResults;
    }


//        } catch(KeyStoreOperationException e) {
//Logging.recordException(e);
//            Throwable ex = e.getCause();
//            if(ex instanceof IOException) {
//                if(ex.getCause() instanceof UnrecoverableKeyException) {
//                    //Password required.
//                    e.getFile();
//                } else {
//                    //corrupt keystore (unrecoverable error)
//                }
//            } else if(ex instanceof CertificateException) {
//
//            } else if(ex instanceof NoSuchAlgorithmException) {
//                //unable to check keystore (unrecoverable error)
//            } else {
//                //keystore exception, or file not found exception (unrecoverable error).
//            }
//        } catch(KeyStoreContentException e) {
//Logging.recordException(e);
//            Throwable ex = e.getCause();
//            if(ex.getCause() instanceof UnrecoverableKeyException) {
//                //Password required (we don't want to support this at the moment I think).
//                e.getAlias();
//            } else if(ex instanceof KeyStoreException) {
//                //the keystore wasn't loaded properly. (unrecoverable error)
//            } else if(ex instanceof NoSuchAlgorithmException) {
//                //unable to check keystore (unrecoverable error)
//            } else {
//                //keystore exception, or file not found exception (unrecoverable error).
//            }
//        } catch(CertificateLoadException e) {
//Logging.recordException(e);
//            //handle this nicely.
//            throw e;
//        }

    public List<X509LoadOperation> getRemainingLoadOperations() {
        List<X509LoadOperation> loadOperations = new ArrayList<>();
        for (KeystoreLoadOperationResult result : keystoreLoadResults) {
            KeystoreLoadOperation loadOp = result.getLoadOperation();
            if (loadOp.getAliasesToLoad() == null || loadOp.getAliasesToLoad().size() > 0) {
                loadOperations.add(loadOp);
            }
        }
        for (CertificateLoadOperationResult certResult : certLoadResults) {
            loadOperations.add(certResult.getLoadOperation());
        }
        return loadOperations;
    }

    public void removeUnrecoverableErrors() {
        for (Iterator<KeystoreLoadOperationResult> keystoreResultIter = keystoreLoadResults.iterator(); keystoreResultIter.hasNext(); ) {
            KeystoreLoadOperationResult next = keystoreResultIter.next();
            for (SecurityOperationException e : next.getExceptionList()) {
                if (e instanceof KeyStoreOperationException) {
                    // handle keystore operation exceptions
                    if (!isRecoverable((KeyStoreOperationException) e)) {
                        keystoreResultIter.remove();
                        break;
                    }
                } else if (e instanceof KeyStoreContentException) {
                    next.getLoadOperation().removeAliasToLoad(((KeyStoreContentException) e).getAlias());
                }
            }

        }
        for (Iterator<CertificateLoadOperationResult> certResultIterator = certLoadResults.iterator(); certResultIterator.hasNext(); ) {
            CertificateLoadOperationResult next = certResultIterator.next();
            if (next.getException() != null) {
                certResultIterator.remove();
            }
        }
    }

    private boolean isRecoverable(KeyStoreContentException e) {
        return e.getCause() instanceof UnrecoverableKeyException;
    }

    private boolean isRecoverable(KeyStoreOperationException e) {
        boolean recoverable = e.getCause() instanceof IOException && e.getCause().getCause() instanceof UnrecoverableKeyException;
        if (!recoverable) {
            // this is in breach of the API but seems to be the case...
            recoverable = e.getCause().getCause() == null && e.getCause().getMessage().contains("wrong password");
        }
        return recoverable;
    }

    public List<SecurityOperationException> getUnrecoverableErrors() {
        List<SecurityOperationException> unrecoverableErrors = new ArrayList<>();
        for (KeystoreLoadOperationResult result : keystoreLoadResults) {
            if (result.getExceptionList().size() > 0) {
                for (SecurityOperationException e : result.getExceptionList()) {
                    if (e instanceof KeyStoreOperationException) {
                        // handle keystore operation exceptions
                        if (!isRecoverable((KeyStoreOperationException) e)) {
                            unrecoverableErrors.add(e);
                        }
                    } else if (e instanceof KeyStoreContentException) {
                        // errors loading content of keystore
                        if (!isRecoverable((KeyStoreContentException) e)) {
                            unrecoverableErrors.add(e);
                        }
                    }
                }
            }
        }
        for (CertificateLoadOperationResult certResult : certLoadResults) {
            CertificateLoadException e = certResult.getException();
            if (e != null) {
                unrecoverableErrors.add(e);
            }
        }
        return unrecoverableErrors;
    }

    public Map<Key, Certificate[]> removeSuccessfullyLoadedData() {
        Map<Key, Certificate[]> successfullyLoadedData = new HashMap<>(keystoreLoadResults.size() + certLoadResults.size());
        for (Iterator<KeystoreLoadOperationResult> iterator = keystoreLoadResults.iterator(); iterator.hasNext(); ) {
            KeystoreLoadOperationResult next = iterator.next();
            if (next.getKeystoreContent() != null) {
                successfullyLoadedData.putAll(next.getKeystoreContent());
                next.getKeystoreContent().clear();
            }
            if (next.getExceptionList().size() == 0) {
                iterator.remove();
            }
        }
        for (Iterator<CertificateLoadOperationResult> iterator = certLoadResults.iterator(); iterator.hasNext(); ) {
            CertificateLoadOperationResult next = iterator.next();
            for (Iterator<X509Certificate> certIterator = next.getCerts().iterator(); certIterator.hasNext(); ) {
                X509Certificate cert = certIterator.next();
                successfullyLoadedData.put(cert.getPublicKey(), new X509Certificate[]{cert});
                certIterator.remove();
            }
            if (next.getException() == null) {
                iterator.remove();
            }
        }
        return successfullyLoadedData;
    }

    /**
     * All recoverable errors basically are just needing a password at the moment
     *
     * @return
     */
    public SecurityOperationException getNextRecoverableError() {
        for (KeystoreLoadOperationResult result : keystoreLoadResults) {
            if (result.getExceptionList().size() > 0) {
                for (SecurityOperationException e : result.getExceptionList()) {
                    if (e instanceof KeyStoreOperationException) {
                        // handle keystore operation exceptions
                        if (isRecoverable((KeyStoreOperationException) e)) {
                            return e;
                        }
                    } else if (e instanceof KeyStoreContentException) {
                        // errors loading content of keystore
                        if (e.getCause() instanceof UnrecoverableKeyException) {
                            return e;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * All recoverable errors basically are just needing a password at the moment
     *
     * @return
     */
    public List<SecurityOperationException> getRecoverableErrors() {
        List<SecurityOperationException> recoverableErrors = new ArrayList<>();
        for (KeystoreLoadOperationResult result : keystoreLoadResults) {
            if (result.getExceptionList().size() > 0) {
                for (SecurityOperationException e : result.getExceptionList()) {
                    if (e instanceof KeyStoreOperationException) {
                        // handle keystore operation exceptions
                        if (isRecoverable((KeyStoreOperationException) e)) {
                            recoverableErrors.add(e);
                        }
                    } else if (e instanceof KeyStoreContentException) {
                        // errors loading content of keystore
                        if (isRecoverable((KeyStoreContentException) e)) {
                            recoverableErrors.add(e);
                        }
                    }
                }
            }
        }
        return recoverableErrors;
    }

    private KeystoreLoadOperationResult findKeystoreLoadOperationResult(String f) {
        for (KeystoreLoadOperationResult result : keystoreLoadResults) {
            if (result.getLoadOperation().getFileUri().getPath().equals(f)) {
                return result;
            }
        }
        throw new RuntimeException("Should never happen");
    }

    public void addPasswordForRerun(SecurityOperationException recoverableError, char[] pass) {
        if (recoverableError instanceof KeyStoreOperationException) {
            String f = recoverableError.getDataSource();
            KeystoreLoadOperationResult result = findKeystoreLoadOperationResult(f);
            result.getLoadOperation().setKeystorePass(pass);
            result.getExceptionList().remove(recoverableError);
        } else if (recoverableError instanceof KeyStoreContentException) {
            KeyStoreContentException err = (KeyStoreContentException) recoverableError;
            String f = err.getDataSource();
            KeystoreLoadOperationResult result = findKeystoreLoadOperationResult(f);
            result.getLoadOperation().getAliasPassMapp().put(err.getAlias(), pass);
            result.getLoadOperation().addAliasToLoad(err.getAlias());
            result.getExceptionList().remove(recoverableError);
        }
    }
}
