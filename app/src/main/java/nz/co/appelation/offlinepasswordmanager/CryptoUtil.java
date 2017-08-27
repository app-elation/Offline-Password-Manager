package nz.co.appelation.offlinepasswordmanager;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

import nz.co.appelation.offlinepasswordmanager.model.PasswordContainer;

/**
 * Util class, grouping all crypto related methods.
 *
 * This class contains a method adapted from Apache Commons Lang - org.apache.commons.lang3.RandomStringUtils#random()
 * See license: http://www.apache.org/licenses/LICENSE-2.0
 */
public class CryptoUtil {
    private static final String TAG = CryptoUtil.class.getSimpleName();

    public static final int HASH_SIZE = 512; //salt bits should be at least as big as the block size
    public static final int KEY_SIZE = 256; //Unfortunately Android limits the key size to 256
    public static final int IV_SIZE = 128; //AES 128 bit block size, so IV is 128 bits: http://security.stackexchange.com/questions/90848/encrypting-using-aes-256-can-i-use-256-bits-iv

    public static final int STREAM_READ_CHUNK_SIZE = 64;

    public static final int MIN_PASSWORD_LENGTH = 8; //64 bit

    private static final String KEY_NAME = "PWM_FINGERPRINT_KEY"; //The name of the key, as held in Android Keystore

    private static final String PSW_FILENAME = "psw";
    private static final String IV_FILENAME = "iv";
    private static final String SALT_FILENAME = "salt";

    private static final String AES = "AES";

    private static final int KEY_GENERATION_ITERATION_COUNT = 10000; //Takes about 750ms for 256bit keys.

    @Inject
    Context ctx;

    @Inject
    KeyStore keyStore;

    @Inject
    PasswordContainer passwordContainer;

    public byte[] makeSalt(){
        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[HASH_SIZE / 8];
        secureRandom.nextBytes(salt);

        return salt;
    }

    /**
     * Save the salt, used to salt the master password.
     *
     * @param salt
     */
    public void saveSalt(byte[] salt){
        FileOutputStream fos = null;

        File saltFile = new File(ctx.getNoBackupFilesDir() + File.separator + getCurrentSALTFilename());

        try {
            boolean success = saltFile.delete();
            fos = new FileOutputStream(saltFile);
            fos.write(salt);
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Unable to write salt file: " + saltFile.getAbsolutePath(), e);
            throw new RuntimeException(e);
        } finally {
            try {
                fos.close();
            } catch (Throwable t){
                //consume
            }
        }
    }

    /**
     * Save the IV, used to encrypt the master password.
     *
     * Saving of the IV - ref:
     * http://stackoverflow.com/questions/33214469
     * http://crypto.stackexchange.com/questions/31760
     * http://stackoverflow.com/questions/5796954
     * http://security.stackexchange.com/questions/17044
     *
     * @param iv
     */
    private void saveIV(byte[] iv){
        FileOutputStream fos = null;

        File ivFile = new File(ctx.getNoBackupFilesDir() + File.separator + getCurrentIVFilename());

        try {
            boolean success = ivFile.delete();
            fos = new FileOutputStream(ivFile);
            fos.write(iv);
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Unable to write iv file: " + ivFile.getAbsolutePath(), e);
            throw new RuntimeException(e);
        } finally {
            try {
                fos.close();
            } catch (Throwable t){
                //consume
            }
        }
    }

    public boolean hasSalt(){
        return new File(ctx.getNoBackupFilesDir(), getCurrentSALTFilename()).exists();
    }

    public byte[] loadCurrentSalt(){
        FileInputStream fis = null;

        File saltFile = new File(ctx.getNoBackupFilesDir() + File.separator + getCurrentSALTFilename());

        try {
            fis = new FileInputStream(saltFile);
            return readAll(fis);
        } catch (IOException e) {
            Log.e(TAG, "Unable to read salt file: " + saltFile.getAbsolutePath(), e);
            throw new RuntimeException(e);
        } finally {
            try {
                fis.close();
            } catch (Throwable t){
                //consume
            }
        }
    }

    public byte[] loadCurrentIV(){
        FileInputStream fis = null;

        File ivFile = new File(ctx.getNoBackupFilesDir() + File.separator + getCurrentIVFilename());

        try {
            fis = new FileInputStream(ivFile);
            return readAll(fis);
        } catch (IOException e) {
            Log.e(TAG, "Unable to read iv file: " + ivFile.getAbsolutePath(), e);
            throw new RuntimeException(e);
        } finally {
            try {
                fis.close();
            } catch (Throwable t){
                //consume
            }
        }
    }

    public boolean hasPasswordFile(){
        return new File(ctx.getNoBackupFilesDir(), getCurrentPSWFilename()).exists();
    }

    /**
     * Generates a new key with CBC for encryption and decryption.
     * This method does not return the key - it is persisted in the keystore under the current key name.
     */
    public void createNewKey() {
        try {
            //Create new key
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            keyGenerator.init(new KeyGenParameterSpec.Builder(getCurrentKeyName(), KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setUserAuthenticationRequired(true)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                            .setKeySize(KEY_SIZE)
                            .build(),
                    new SecureRandom());

            SecretKey key = keyGenerator.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create new key via KeyGenerator", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the current version key from the Android KeyStore. This key is authorised for use by fingerprint, and then used to encrypt / decrypts the master password.
     *
     * @return the secret key
     */
    public SecretKey getCurrentKey() {
        try {
            keyStore.load(null);
            return (SecretKey)keyStore.getKey(getCurrentKeyName(), null);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get key from keystore", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Clones a byte array.
     * Arrays are passed by reference... if you pass and hang onto a byte array, it may be modified from outside again... (or read)
     *
     * @param original the original byte array
     * @return the cloned array
     */
    public static final byte[] cloneByteArray(byte[] original){
        byte[] clone = new byte[original.length];
        System.arraycopy(original, 0, clone, 0, original.length);

        return clone;
    }

    /**
     * Clones a character array.
     * Arrays are passed by reference... if you pass and hang onto a char array, it may be modified from outside again... (or read)
     *
     * @param original the original char array
     * @return the cloned array
     */
    public static final char[] cloneCharArray(char[] original){
        char[] clone = new char[original.length];
        System.arraycopy(original, 0, clone, 0, original.length);

        return clone;
    }

    private Cipher createBaseCipher() throws NoSuchAlgorithmException, NoSuchPaddingException {
        return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
    }

    /**
     * Creates a new Cipher object for encryption of the Master Password.
     *
     * Base cipher is created as AES-CBC-PCKS7.
     *
     * In our usecase, we encrypt with a new key and kew IV (init vector) each time,
     * so we delete the old ones when we create the cipher for encryption.
     * Also, we get a new salt.
     *
     * The key is randomly generated by KeyGenerator and the stored in the KeyStore.
     *
     * @return the cipher to be used for encrypting the Master Password
     */
    public Cipher createCipherForMasterPasswordEncryption() {
        //Delete old key & password & salt & IV
        deleteKeyPasswordFileAndIV();

        //Create a new key in the keystore
        createNewKey();

        //Get new key
        SecretKey key = getCurrentKey();

        Cipher cipher = null;
        try {
            //Get base cipher
            cipher = createBaseCipher();

            //Init cipher for encryption
            cipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            Log.e(TAG, "Unable to create new cipher for Master Password encryption", e);
            throw new RuntimeException("Unable to create new cipher for Master Password encryption", e);
        }

        //Save new IV
        byte[] iv = getIVFromCipher(cipher);
        saveIV(iv);

        return cipher;
    }

    /**
     * Creates a new Cipher object for decryption of the Master Password.
     *
     * We fetch the key from the key store.
     *
     * Base cipher is created as AES-CBC-PCKS7.
     *
     * @return
     * @throws KeyLostException thrown if the key is no longer in the key store
     */
    public Cipher createCipherForMasterPasswordDecryption() throws KeyLostException {
        //Get our key from keystore
        SecretKey key = getCurrentKey();

        //Did we get it?
        if (key == null){
            //Throw KeyLostException so we can redo the password encryption
            throw new KeyLostException();
        }

        //Load the IV
        byte[] iv = loadCurrentIV();

        try {
            //Get base cipher
            Cipher cipher = createBaseCipher();

            //Init cypher for decryption, with loaded IV
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            return cipher;
        } catch (KeyPermanentlyInvalidatedException e) {
            //Device fingerprints or lockscreen settings changed, so old key is dead.
            //Throw KeyLostException so we can redo the password encryption
            Log.e(TAG, "Got KeyPermanentlyInvalidatedException while using key", e);
            throw new KeyLostException();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            Log.e(TAG, "Unable to create new cipher for Master Password decryption", e);
            throw new RuntimeException("Unable to create new cipher for Master Password decryption", e);
        }
    }

    /**
     * Creates a cipher for encryption.
     * We get the hashed password as the key.
     *
     * Base cipher is created as AES-CBC-PCKS7.
     *
     * The IV is generated by random for us, by the Cipher init.
     * This is recommended over supplying own random IV.
     * @see android.security.keystore.KeyGenParameterSpec.Builder#setRandomizedEncryptionRequired(boolean)
     *
     * The returned Cipher can be queried to retrieve the generated IV.
     *
     * @param key the hashed password
     * @return the cipher to be used for encrypting
     */
    public Cipher createCipherForEncryption(byte[] key) {
        SecretKeySpec keySpec = new SecretKeySpec(key, AES);

        try {
            //Get base cipher
            Cipher cipher = createBaseCipher();

            //Init cipher for encryption
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            Log.e(TAG, "Unable to create new cipher for encryption", e);
            throw new RuntimeException("Unable to create new cipher for encryption", e);
        }
    }

    /**
     * Creates a cipher for decryption
     * We get the hashed password as the key, and also the IV.
     *
     * Base cipher is created as AES-CBC-PCKS7.
     *
     * @param key the hashed password
     * @param iv the iv, as generated by the encryption cipher
     * @return the cipher to be used for decrypting
     */
    public Cipher createCipherForDecryption(byte[] key, byte[] iv) {
        if (iv.length != IV_SIZE / 8){
            Log.e(TAG, "DBDecryption: Invalid IV length detected: " + iv.length + ", was expecting: " + IV_SIZE / 8);
            throw new RuntimeException("DBDecryption: Invalid IV length detected: " + iv.length + ", was expecting: " + IV_SIZE / 8);
        }

        SecretKeySpec keySpec = new SecretKeySpec(key, AES);

        try {
            //Get base cipher
            Cipher cipher = createBaseCipher();

            //Init cipher for decryption
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));

            return cipher;
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchPaddingException |InvalidKeyException e) {
            Log.e(TAG, "Unable to create new cipher for decryption", e);
            throw new RuntimeException("Unable to create new cipher for decryption", e);
        }
    }

    /**
     * Generate a new account password, with length of pswLength, using SecureRandom.
     * Characters in ascii range 32-127.
     *
     * @param pswLength the password length
     * @return the random password
     */
    public char[] generateRandomAccountPassword(int pswLength){
        return randomChars(pswLength, 32, 127, false, false, null, new SecureRandom());
    }

    /**
     * Adapted from Apache Commons Lang - org.apache.commons.lang3.RandomStringUtils#random()
     * See license: http://www.apache.org/licenses/LICENSE-2.0
     *
     * This version will preserve the response as char array and not convert it back to String.
     *
     * We only accept the password as a character array! See: http://stackoverflow.com/questions/8881291/why-is-char-preferred-over-string-for-passwords-in-java/8889285#8889285
     *
     *
     * <p>Creates a random string based on a variety of options, using
     * supplied source of randomness.</p>
     *
     * <p>If start and end are both {@code 0}, start and end are set
     * to {@code ' '} and {@code 'z'}, the ASCII printable
     * characters, will be used, unless letters and numbers are both
     * {@code false}, in which case, start and end are set to
     * {@code 0} and {@code Integer.MAX_VALUE}.
     *
     * <p>If set is not {@code null}, characters between start and
     * end are chosen.</p>
     *
     * <p>This method accepts a user-supplied {@link Random}
     * instance to use as a source of randomness. By seeding a single
     * {@link Random} instance with a fixed seed and using it for each call,
     * the same random sequence of strings can be generated repeatedly
     * and predictably.</p>
     *
     * @param count  the length of random string to create
     * @param start  the position in set of chars to start at
     * @param end  the position in set of chars to end before
     * @param letters  only allow letters?
     * @param numbers  only allow numbers?
     * @param chars  the set of chars to choose randoms from, must not be empty.
     *  If {@code null}, then it will use the set of all chars.
     * @param random  a source of randomness.
     * @return the random string
     * @throws ArrayIndexOutOfBoundsException if there are not
     *  {@code (end - start) + 1} characters in the set array.
     * @throws IllegalArgumentException if {@code count} &lt; 0 or the provided chars array is empty.
     * @since 2.0
     */
    private char[] randomChars(int count, int start, int end, final boolean letters, final boolean numbers, final char[] chars, final Random random) {
        if (count == 0) {
            return new char[0];
        } else if (count < 0) {
            throw new IllegalArgumentException("Requested random string length " + count + " is less than 0.");
        }

        if (chars != null && chars.length == 0) {
            throw new IllegalArgumentException("The chars array must not be empty");
        }

        if (start == 0 && end == 0) {
            if (chars != null) {
                end = chars.length;
            } else {
                if (!letters && !numbers) {
                    end = Integer.MAX_VALUE;
                } else {
                    end = 'z' + 1;
                    start = ' ';
                }
            }
        } else {
            if (end <= start) {
                throw new IllegalArgumentException("Parameter end (" + end + ") must be greater than start (" + start + ")");
            }
        }

        final char[] buffer = new char[count];
        final int gap = end - start;

        while (count-- != 0) {
            char ch;
            if (chars == null) {
                ch = (char) (random.nextInt(gap) + start);
            } else {
                ch = chars[random.nextInt(gap) + start];
            }
            if (letters && Character.isLetter(ch)
                    || numbers && Character.isDigit(ch)
                    || !letters && !numbers) {
                if(ch >= 56320 && ch <= 57343) {
                    if(count == 0) {
                        count++;
                    } else {
                        // low surrogate, insert high surrogate after putting it in
                        buffer[count] = ch;
                        count--;
                        buffer[count] = (char) (55296 + random.nextInt(128));
                    }
                } else if(ch >= 55296 && ch <= 56191) {
                    if(count == 0) {
                        count++;
                    } else {
                        // high surrogate, insert low surrogate before putting it in
                        buffer[count] = (char) (56320 + random.nextInt(128));
                        count--;
                        buffer[count] = ch;
                    }
                } else if(ch >= 56192 && ch <= 56319) {
                    // private high surrogate, no effing clue, so skip it
                    count++;
                } else {
                    buffer[count] = ch;
                }
            } else {
                count++;
            }
        }

        return buffer;
    }

    public byte[] getIVFromCipher(Cipher cipher){
        try {
            AlgorithmParameters params = cipher.getParameters();
            IvParameterSpec ivSpec = params.getParameterSpec(IvParameterSpec.class);

            byte[] iv = ivSpec.getIV();
            Log.d(TAG, "IV size: " + iv.length);

            if (iv.length != IV_SIZE / 8){
                Log.e(TAG, "Invalid IV length detected: " + iv.length + ", was expecting: " + IV_SIZE / 8);
                throw new RuntimeException("Invalid IV length detected: " + iv.length + ", was expecting: " + IV_SIZE / 8);
            }

            return iv;
        } catch (InvalidParameterSpecException e) {
            Log.e(TAG, "Unable to get IV from cipher", e);
            throw new RuntimeException("Unable to get IV from cipher", e);
        }
    }

    /**
     * Take a password, and generate a key from it which can be used for encryption.
     *
     * PBKDF2 is used with 10000 hashes, to generate a 256bit key. Takes ~750ms on a modern device.
     *
     *
     * We only accept the password as a character array! See: http://stackoverflow.com/questions/8881291/why-is-char-preferred-over-string-for-passwords-in-java/8889285#8889285
     *
     * For some background: https://github.com/tozny/java-aes-crypto/blob/master/aes-crypto/src/main/java/com/tozny/crypto/android/AesCbcWithIntegrity.java
     *
     * @param password the original password as a char array
     * @return the key as a byte array
     */
    public byte[] generateKeyFromPassword(char[] password, byte[] salt){
        try {
            PBEKeySpec keySpec = new PBEKeySpec(password, salt, KEY_GENERATION_ITERATION_COUNT, KEY_SIZE);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1"); //also see https://crackstation.net/hashing-security.htm
            long t1 = System.currentTimeMillis();
            byte[] key = keyFactory.generateSecret(keySpec).getEncoded();
            long t2 = System.currentTimeMillis();
            keySpec.clearPassword();

            Log.d(TAG, "PBKDF2 key derivation took: " + (t2 - t1) + "ms");

            return key;
        } catch (InvalidKeySpecException e) {
            Log.e(TAG, "Unable to create key from password - Invalid key spec", e);
            throw new RuntimeException("Invalid key spec", e);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Unable to create key from password - No Such Algorithm", e);
            throw new RuntimeException("No Such Algorithm", e);
        }
    }

    /**
     * Safely reads a stream into a byte array.
     *
     * @param is the input stream to read from
     * @return byte array of content
     * @throws IOException thrown on any read error
     */
    private static final byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = null;

        try {
            baos = new ByteArrayOutputStream(STREAM_READ_CHUNK_SIZE);
            byte[] chunk = new byte[STREAM_READ_CHUNK_SIZE];
            int read;
            while ((read = is.read(chunk, 0, STREAM_READ_CHUNK_SIZE)) != -1){
                baos.write(chunk, 0, read);
            }

            baos.flush();

            return baos.toByteArray();
        } finally {
            try {
                baos.close();
            } catch (Throwable t){
                //consume
            }
        }
    }

    /**
     * WARNING: Deleting the salt file will make the password DB unusable, even with master password.
     *
     * Method to delete the fingerprint-authed key from the android keystore,
     * and the encrypted master password file,
     * and the salt,
     * and the IV.
     */
    public void deleteKeyPasswordFileSaltAndIV(){
        deleteKeyPasswordFileAndIV();

        //Delete salt
        boolean success = new File(ctx.getNoBackupFilesDir() + File.separator + getCurrentSALTFilename()).delete();
        Log.d(TAG, "Delete SALT file: " + success);
    }

    /**
     * Method to delete the fingerprint-authed key from the android keystore,
     * and the encrypted master password file,
     * and the IV.
     *
     * NOT the salt!
     */
    public void deleteKeyPasswordFileAndIV(){
        try {
            //Load the keystore
            keyStore.load(null);

            //Delete old key
            keyStore.deleteEntry(getCurrentKeyName());
        } catch (Exception e) {
            Log.e(TAG, "Error while deleting key", e);
            //consume
        }

        //Delete IV
        boolean success = new File(ctx.getNoBackupFilesDir() + File.separator + getCurrentIVFilename()).delete();
        Log.d(TAG, "Delete IV file: " + success);

        //Delete encrypted password
        success = new File(ctx.getNoBackupFilesDir() + File.separator + getCurrentPSWFilename()).delete();
        Log.d(TAG, "Delete PSW file: " + success);
    }

    public void encryptAndSaveMasterPasswordHash(Cipher fingerprintAuthedCipher){
        try {
            //Encrypt password
            byte[] encryptedPsw = fingerprintAuthedCipher.doFinal(passwordContainer.getHashedMasterPassword());

            saveEncryptedMasterPasswordHash(encryptedPsw);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            Log.e(TAG, "Unable to encrypt password", e);
            throw new RuntimeException("Unable to encrypt password", e);
        }
    }

    private void saveEncryptedMasterPasswordHash(byte[] encryptedPsw){
        FileOutputStream fos = null;

        File pswFile = new File(ctx.getNoBackupFilesDir() + File.separator + getCurrentPSWFilename());

        try {
            //Delete old encrypted password
            boolean success = pswFile.delete();
            Log.d(TAG, "Delete PSW file: " + success);

            //Save the new encrypted password
            fos = new FileOutputStream(pswFile);
            fos.write(encryptedPsw);
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Unable to save encrypted password", e);
            throw new RuntimeException("Unable to save encrypted password", e);
        } finally {
            try {
                fos.close();
            } catch (Throwable t) {
                //consume
            }
        }
    }

    public void loadAndDecryptMasterPasswordHash(Cipher fingerprintAuthedCipher){
        try {
            //Load the encrypted password
            byte[] encryptedPsw = loadEncryptedMasterPasswordHash(ctx);

            //Decrypt password
            byte[] decryptedPsw = fingerprintAuthedCipher.doFinal(encryptedPsw);

            passwordContainer.setHashedMasterPassword(decryptedPsw);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            Log.e(TAG, "Unable to decrypt password", e);
            throw new RuntimeException("Unable to decrypt password", e);
        }
    }

    private byte[] loadEncryptedMasterPasswordHash(Context ctx){
        FileInputStream fis = null;

        try {
            //Load the encrypted password
            fis = new FileInputStream(ctx.getNoBackupFilesDir() + File.separator + getCurrentPSWFilename());

            return readAll(fis);
        } catch (IOException e) {
            Log.e(TAG, "Unable to load encrypted password", e);
            throw new RuntimeException("Unable to load encrypted password", e);
        } finally {
            try {
                fis.close();
            } catch (Throwable t){
                //consume
            }
        }
    }

    /**
     * The IV file is versioned - to allow future versions of this app to migrate data from one version to the next.
     * This method return the current, versioned, IV filename.
     * The IV is the initialisation vector, used to encrypt the hashed master password (for fingerprint auth).
     * This IV is not used to encrypt the password DB - that file has it's own IV at the start of the file.
     *
     * @return the current IV filename
     */
    private String getCurrentIVFilename(){
        return IV_FILENAME + ".v" + OfflinePasswordManagerInjectedApplication.CURRENT_APP_VERSION;
    }

    /**
     * The PSW file is versioned - to allow future versions of this app to migrate data from one version to the next.
     * This method return the current, versioned, PSW filename.
     * The PSW file holds the encrypted, hashed master password, used to decrypt the main password DB.
     *
     * @return the current PSW filename
     */
    private String getCurrentPSWFilename(){
        return PSW_FILENAME + ".v" + OfflinePasswordManagerInjectedApplication.CURRENT_APP_VERSION;
    }

    /**
     * The SALT file is versioned - to allow future versions of this app to migrate data from one version to the next.
     * This method return the current, versioned, SALT filename.
     * The SALT file holds the salt bytes, used during hashing of the master password.
     *
     * @return the current SALT filename
     */
    private String getCurrentSALTFilename(){
        return SALT_FILENAME + ".v" + OfflinePasswordManagerInjectedApplication.CURRENT_APP_VERSION;
    }

    /**
     * The Key, as held in the Android Keystore, is versioned - to allow future versions of this app to migrate data from one version to the next.
     * This method return the current, versioned, Key name.
     * The Key is generated for use with fingerprint auth, and is used to encrypt/decrypt the master password file.
     *
     * @return the current Key name
     */
    private String getCurrentKeyName(){
        return KEY_NAME + ".v" + OfflinePasswordManagerInjectedApplication.CURRENT_APP_VERSION;
    }

}
