package nz.co.appelation.offlinepasswordmanager;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.inject.Inject;

import nz.co.appelation.offlinepasswordmanager.model.PasswordContainer;
import nz.co.appelation.offlinepasswordmanager.model.display.AccountEntry;
import nz.co.appelation.offlinepasswordmanager.model.display.PasswordDB;

/**
 * DB Manager - managed the encrypting and decrypting of the password DB object (based on a map).
 *
 * For some extra notes see:
 * https://tozny.com/blog/encrypting-strings-in-android-lets-make-better-mistakes/
 * https://github.com/tozny/java-aes-crypto
 */
public class DBManager {

    private static final String TAG = DBManager.class.getSimpleName();

    private static final String DB_FILENAME = "db";
    private static final String DB_TEMP_FILENAME = "db.tmp";

    /**
     * The magic byte header-
     * This header is encrypted along with the DB, by using the key derived from the master password.
     * If we attempt a decrypt, and the magic header decrypts correctly, then we know the supplied password key was correct.
     */
    private static final byte[] MAGIC = "714b7870d28191d8c68619bdd7e9d99a".getBytes(StandardCharsets.UTF_8); //Magic bytes used to identify a correctly decrypted DB stream.

    private PasswordDB passwordDB = null;

    @Inject
    PasswordContainer passwordContainer;

    @Inject
    CategoryHelper categoryHelper;

    @Inject
    Context ctx;

    @Inject
    ModelMapper modelMapper;

    @Inject
    CryptoUtil cryptoUtil;

    public DBManager(){
    }

    public void loadDB() throws WrongPasswordException {
        if (passwordContainer == null || passwordContainer.getHashedMasterPassword() == null){
            Log.e(TAG, "Password hash not present!");
            throw new RuntimeException("Password hash not present!");
        }

        if (!doesCurrentVersionDBExist()){
            //Any DB version conversion should have been handled outside of this method.
            Log.e(TAG, "DB file does not exist");
            throw new RuntimeException("DB file does not exist");
        }

        FileInputStream fis = null;
        ObjectInputStream ois = null;
        CipherInputStream dbInputStream = null;

        nz.co.appelation.offlinepasswordmanager.model.persistence.v1.PasswordDB pPasswordDB = null;

        try {
            fis = new FileInputStream(ctx.getNoBackupFilesDir() + File.separator + DB_FILENAME + ".v" + OfflinePasswordManagerInjectedApplication.CURRENT_APP_VERSION);

            /**
             * The first 128 bits is the iv, and is not encrypted. We read these bytes to set up the cipher.
             */
            byte[] iv = StreamUtil.readBytes(fis, CryptoUtil.IV_SIZE / 8);
            Cipher cipher = cryptoUtil.createCipherForDecryption(passwordContainer.getHashedMasterPassword(), iv);

            dbInputStream = new CipherInputStream(fis, cipher);

            /**
             * Next 32 bytes are for the MAGIC header. The header is encrypted, and if we read it back correctly,
             * then we are decrypting ok...
             */
            byte[] magic = StreamUtil.readBytes(dbInputStream, MAGIC.length);
            if (!Arrays.equals(MAGIC, magic)){
                throw new WrongPasswordException();
            }

            ois = new ObjectInputStream(dbInputStream);

            //At v1, we only have 1 version of the persistence model...
            pPasswordDB = (nz.co.appelation.offlinepasswordmanager.model.persistence.v1.PasswordDB) ois.readObject();
            passwordDB = modelMapper.mapPasswordDBFromPersistenceV1ToDisplay(pPasswordDB);
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "Failed to load DB", e);
            throw new RuntimeException(e);
        } finally {
            try {
                ois.close();
            } catch (Throwable t){
                //consume
            }

            try {
                dbInputStream.close();
            } catch (Throwable t){
                //consume
            }

            try {
                fis.close();
            } catch (Throwable t) {
                //consume
            }

            try {
                modelMapper.killModels(pPasswordDB);
            } catch (Throwable t) {
                //consume
            }
        }
    }

    /**
     * Saves the envrypted password DB but first writing to a temp file, and if successful, delete the previous DB file, and rename the temp one to be the new one.
     * This prevents any issues if we fail writing partially through.
     *
     * A new IV is generated for each save.
     *
     * Steps:
     * 1) Calculate a new IV
     * 2) Write IV to stream unencrypted
     * 3) Convert password DB (map) into object stream bytes
     * 4) Write encrypted magic bytes
     * 5) Write encrypted object stream bytes
     **/
    public void saveDB(){
        OutputStream fos = null;
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        ByteArrayInputStream bais = null;
        CipherOutputStream dbOutputStream = null;

        nz.co.appelation.offlinepasswordmanager.model.persistence.v1.PasswordDB pPasswordDB = null;

        try {
            //Clear any old temp DB
            deleteTempDB();

            //Get cipher
            Cipher cipher = cryptoUtil.createCipherForEncryption(passwordContainer.getHashedMasterPassword());

            /**
             * IV is generated by random for us, by the Cipher init.
             * This is recommended over supplying own random IV.
             * @see android.security.keystore.KeyGenParameterSpec.Builder#setRandomizedEncryptionRequired(boolean)
             */
            byte[] iv = cryptoUtil.getIVFromCipher(cipher);

            //Write the unencrypted IV
            fos = new FileOutputStream(ctx.getNoBackupFilesDir() + File.separator + DB_TEMP_FILENAME);
            fos.write(iv);

            //Convert password DB into object stream
            baos = new ByteArrayOutputStream(passwordDB.size() * 350 + 256); //assuming ~350 bytes per record, and a little overhead
            oos = new ObjectOutputStream(baos);

            pPasswordDB = modelMapper.mapPasswordDBFromDisplayToPersistenceV1(passwordDB);
            oos.writeObject(pPasswordDB);

            oos.flush();

            bais = new ByteArrayInputStream(baos.toByteArray());

            //Create encrypted stream
            dbOutputStream = new CipherOutputStream(fos, cipher);

            //Write encrypted magic bytes
            dbOutputStream.write(MAGIC);

            //Write encrypted object stream bytes
            byte[] chunk = new byte[CryptoUtil.STREAM_READ_CHUNK_SIZE];
            int read;
            while ((read = bais.read(chunk, 0, CryptoUtil.STREAM_READ_CHUNK_SIZE)) != -1){
                dbOutputStream.write(chunk, 0, read);
            }

            dbOutputStream.flush();
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to load DB", e);
            throw new RuntimeException(e);
        } finally {
            try {
                dbOutputStream.close();
            } catch (Throwable t){
                //consume
            }

            try {
                bais.close();
            } catch (Throwable t){
                //consume
            }

            try {
                oos.close();
            } catch(Throwable t){
                //consume
            }

            try {
                baos.close();
            } catch(Throwable t){
                //consume
            }

            try {
                fos.close();
            } catch(Throwable t){
                //consume
            }

            try {
                modelMapper.killModels(pPasswordDB);
            } catch(Throwable t){
                //consume
            }
        }

        moveTempToRealDB();
    }

    public void initEmptyDB() {
        passwordDB = new PasswordDB();

        passwordDB.customCategories = categoryHelper.getDefaultCustomCategoryNames();

        saveDB();
    }

    public void moveTempToRealDB(){
        deleteCurrentVersionDB();

        File from = new File(ctx.getNoBackupFilesDir(), DB_TEMP_FILENAME);
        File to = new File(ctx.getNoBackupFilesDir(), DB_FILENAME + ".v" + OfflinePasswordManagerInjectedApplication.CURRENT_APP_VERSION);
        boolean success = from.renameTo(to);

        if (!success){
            Log.e(TAG, "Unable to move temp DB to real DB");
            throw new RuntimeException("Unable to move temp DB to real DB");
        }
    }

    private void addAccount(AccountEntry account){
        if (!passwordDB.containsKey(account.categoryId)){
            passwordDB.put(account.categoryId, new ArrayList<AccountEntry>());
        }

        List<AccountEntry> accounts = passwordDB.get(account.categoryId);
        accounts.add(account.copy()); //add a copy of the account, so it cannot be modified from outside via reference.
    }

    public void updateAccount(AccountEntry account){
        //Remove old account
        AccountEntry oldAccount = getAccount(account.id);
        if (oldAccount != null) {
            List<AccountEntry> accountList = passwordDB.get(oldAccount.categoryId);
            accountList.remove(oldAccount);
            modelMapper.killModel(oldAccount);
        }

        //Add new account
        addAccount(account);
    }

    public String getCustomCategoryNameForIndex(int customCategoryIndex){
        return passwordDB.customCategories.get(customCategoryIndex);
    }

    public void updateCustomCategory(int customCategoryIndex, String customCategoryName){
        passwordDB.customCategories.set(customCategoryIndex, customCategoryName);
    }

    public void deleteAccount(String accountId){
        AccountEntry account = getAccount(accountId);
        List<AccountEntry> accountList = passwordDB.get(account.categoryId);
        accountList.remove(account);
        modelMapper.killModel(account);
    }

    public AccountEntry getAccount(String accountId){
        for (List<AccountEntry> accountList : passwordDB.values()){
            for (AccountEntry account : accountList){
                if (accountId.equals(account.id)){
                    return account;
                }
            }
        }

        return null;
    }

    private List<AccountEntry> getAccountListContainingAccount(String accountId){
        for (List<AccountEntry> accountList : passwordDB.values()){
            for (AccountEntry account : accountList){
                if (accountId.equals(account.id)){
                    return accountList;
                }
            }
        }

        return null;
    }

    public List<AccountEntry> getAllAccounts(){
        List<AccountEntry> allAccounts = new ArrayList<>();
        for (List<AccountEntry> accountList : passwordDB.values()){
            allAccounts.addAll(accountList);
        }

        return makeSortedCopy(allAccounts);
    }

    private List<AccountEntry> makeSortedCopy(List<AccountEntry> accounts){
        List<AccountEntry> copyList = new ArrayList<>();
        if (accounts != null) {
            copyList.addAll(accounts);

            Collections.sort(copyList, new Comparator<AccountEntry>() {
                @Override
                public int compare(AccountEntry lhs, AccountEntry rhs) {
                    int comp = (lhs.name != null ? lhs.name : "").compareToIgnoreCase(rhs.name != null ? rhs.name : "");
                    if (comp == 0){
                        return lhs.id.compareTo(rhs.id);
                    }

                    return comp;
                }
            });
        }

        return copyList;
    }

    public void deleteCurrentVersionDB(){
        boolean success = new File(ctx.getNoBackupFilesDir() + File.separator + DB_FILENAME + ".v" + OfflinePasswordManagerInjectedApplication.CURRENT_APP_VERSION).delete();
    }

    public void deleteTempDB(){
        boolean success = new File(ctx.getNoBackupFilesDir() + File.separator + DB_TEMP_FILENAME).delete();
    }

    public boolean doesCurrentVersionDBExist(){
        return new File(ctx.getNoBackupFilesDir(), DBManager.DB_FILENAME + ".v" + OfflinePasswordManagerInjectedApplication.CURRENT_APP_VERSION).exists();
    }

    public boolean doesPreviousVersionDBExist(){
        return false; //No other versions possible at app v1.
    }

    /**
     * Call to clean sensitive info in memory.
     * Will damage the PasswordDB model in memory. Only call after saving, and while existing.
     */
    public void killAccountModels(){
        try {
            if (passwordDB != null) {
                for (List<AccountEntry> accountList : passwordDB.values()) {
                    for (AccountEntry account : accountList) {
                        modelMapper.killModel(account);
                    }
                }

                passwordDB = null;
            }
        } catch (Throwable t){
            //consume
        }
    }

}
