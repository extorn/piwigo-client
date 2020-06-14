/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.vending.licensing;

import android.util.Log;

import com.google.android.vending.licensing.util.Base64;
import com.google.android.vending.licensing.util.Base64DecoderException;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import delit.libs.core.util.Logging;

/**
 * An Obfuscator that uses AES to encrypt data.
 */
public class AESObfuscator implements Obfuscator {
    private static final String UTF8 = "UTF-8";
    private static final String KEYGEN_ALGORITHM = "PBEWITHSHAAND256BITAES-CBC-BC";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String HEADER = "com.android.vending.licensing.AESObfuscator-1|";
    private static final String TAG = "AESObfuscator";

    private static final SecureRandom random = new SecureRandom();
    private final SecretKeySpec secret;
    private boolean recordErrors;

    /**
     * @param salt an array of random bytes to use for each (un)obfuscation
     * @param applicationId application identifier, e.g. the package name
     * @param deviceId device identifier. Use as many sources as possible to
     *    create this unique identifier.
     */
    public AESObfuscator(byte[] salt, String applicationId, String deviceId, boolean recordErrors) {
        this.recordErrors = recordErrors;
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEYGEN_ALGORITHM);
            KeySpec keySpec =
                    new PBEKeySpec((applicationId + deviceId).toCharArray(), salt, 1024, 256);
            SecretKey tmp = factory.generateSecret(keySpec);
            secret = new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (GeneralSecurityException e) {
Logging.recordException(e);
            // This can't happen on a compatible Android device.
            throw new RuntimeException("Invalid environment", e);
        }
    }

    private Cipher buildDecrypter(byte[] decryptionIV) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher mDecryptor = Cipher.getInstance(CIPHER_ALGORITHM);
        mDecryptor.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(decryptionIV));
        return mDecryptor;
    }

    private Cipher buildEncrypter() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher mEncryptor = Cipher.getInstance(CIPHER_ALGORITHM);
        mEncryptor.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(generateIV()));
        return mEncryptor;
    }

    public static byte[] generateIV() {
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        return iv;
    }

    private byte[] mergeArrays(byte[]... arrays) {
        int totLen = 0;
        for (byte[] arr : arrays) {
            totLen += arr.length;
        }
        byte[] output = new byte[totLen];
        totLen = 0;
        for (byte[] arr : arrays) {
            for (byte b : arr) {
                output[totLen++] = b;
            }
        }
        return output;
    }

    @Override
    public String obfuscateBytes(byte[] originalData, String key) {
        if (originalData == null) {
            return null;
        }
        try {
            // Header is appended as an integrity check
            Cipher cipher = buildEncrypter();
            byte[] inputData = mergeArrays(HEADER.getBytes(UTF8), key.getBytes(UTF8), originalData);
            byte[] encrypted = cipher.doFinal(inputData);
            byte[] iv = cipher.getIV();
            if(iv == null || iv.length != 16) {
                throw new IllegalStateException("IV has not been initialised");
            }
            byte[] finalOutput = mergeArrays(iv, encrypted);
            return Base64.encode(finalOutput);
        } catch (GeneralSecurityException e) {
            Logging.recordException(e);
            throw new RuntimeException("Invalid environment", e);
        } catch (UnsupportedEncodingException e) {
            Logging.recordException(e);
            throw new RuntimeException("Invalid environment", e);
        }
    }


    @Override
    public String obfuscateString(String original, String key) {
        if (original == null) {
            return null;
        }
        try {
            return obfuscateBytes(original.getBytes(UTF8), key);
        } catch (UnsupportedEncodingException e) {
            Logging.recordException(e);
            throw new RuntimeException("Invalid environment", e);
        }
    }

    @Override
    public String unobfuscateString(String obfuscated, String key) throws ValidationException {
        if (obfuscated == null) {
            return null;
        }
        byte[] bytes = unobfuscateBytes(obfuscated, key);
        try {
            return new String(bytes, UTF8);
        } catch (UnsupportedEncodingException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new RuntimeException("Invalid environment", e);
        }
    }

    @Override
    public byte[] unobfuscateBytes(String obfuscated, String key) throws ValidationException {
        if (obfuscated == null) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(obfuscated);
            byte[] ivBytes = Arrays.copyOf(bytes, 16);
            byte[] dataBytes = Arrays.copyOfRange(bytes, 16, bytes.length);
            Cipher cipher = buildDecrypter(ivBytes);

            byte[] headerBytes = HEADER.getBytes(UTF8);
            byte[] keyBytes = key.getBytes(UTF8);

            byte[] decrypted = cipher.doFinal(dataBytes);
            boolean sane = true;
            int totalHeaderLen = headerBytes.length;
            for (int i = 0; i < totalHeaderLen; i++) {
                sane &= decrypted[i] == headerBytes[i];
            }
            for (int i = 0; i < keyBytes.length; i++) {
                sane &= decrypted[totalHeaderLen + i] == keyBytes[i];
            }
            totalHeaderLen += keyBytes.length;

            // Check for presence of HEADER. This serves as a final integrity check, for cases
            // where the block size is correct during decryption.
            if (!sane) {
                if(BuildConfig.DEBUG) {
                    Log.w(TAG, "Unable to decrypt - Header not found (invalid data or key)" + ":" +
                            obfuscated + "\n" + HEADER + key + " not found in " + new String(decrypted, UTF8));
                }
                throw new ValidationException("Unable to decrypt - HEADER not found (invalid data or key)" + ":" + obfuscated);
            }

            return Arrays.copyOfRange(decrypted, totalHeaderLen, decrypted.length);
        } catch (Base64DecoderException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new ValidationException(e.getMessage() + ":" + obfuscated);
        } catch (IllegalBlockSizeException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new ValidationException(e.getMessage() + ":" + obfuscated);
        } catch(ArrayIndexOutOfBoundsException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new ValidationException(e.getMessage() + ":" + obfuscated);
        } catch(IllegalArgumentException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new ValidationException(e.getMessage() + ":" + obfuscated);
        } catch(BadPaddingException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new ValidationException(e.getMessage() + ":" + obfuscated);
        } catch (NoSuchPaddingException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new RuntimeException("Invalid environment", e);
        } catch (InvalidKeyException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new RuntimeException("Invalid environment", e);
        } catch (NoSuchAlgorithmException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new RuntimeException("Invalid environment", e);
        } catch (InvalidAlgorithmParameterException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new RuntimeException("Invalid environment", e);
        } catch (UnsupportedEncodingException e) {
            if (recordErrors) {
                Logging.recordException(e);
            }
            throw new RuntimeException("Invalid environment", e);
        }
    }
}
