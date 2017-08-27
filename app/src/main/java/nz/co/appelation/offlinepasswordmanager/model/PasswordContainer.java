package nz.co.appelation.offlinepasswordmanager.model;

import java.util.Arrays;

import javax.inject.Inject;

import dagger.Lazy;
import nz.co.appelation.offlinepasswordmanager.CryptoUtil;

/**
 * Container class to hold the DB password and hash in memory.
 * The master password is hashed with the salt, using PBKDF2. This becomes the hashedPassword (or key),
 * which is then saved to disk, encrypted with fingerprint the key.
 * The salt is randomly generated on first use.
 *
 * The salt and hashedPassword byte arrays are set as volatile to prevent thread-local caching / VM optimisation,
 * and are always written to returned as a copy to prevent external modification (as arrays will be passed by reference).
 */
public class PasswordContainer {
    private volatile byte[] salt = null;
    private volatile byte[] hashedPassword = null;

    @Inject
    Lazy<CryptoUtil> cryptoUtil;

    /**
     * Makes sure the salt byte array is initialised.
     * This means either reading the existing salt bytes from file, or creating and saving a new salt, if not used before.
     * This needs to be lazy-loaded, and cannot happen at instantiation time.
     */
    private void initSalt(){
        CryptoUtil cu = cryptoUtil.get();
        if (!cu.hasSalt()) {
            cu.saveSalt(cu.makeSalt());
        }

        salt = cu.loadCurrentSalt();
    }

    /**
     * Method to set the master password into the PasswordContainer.
     * This is used when setting a new master password, as entered by the user, usually on first use.
     *
     * The master password is hashed with the salt, and converted into a key, using PBKDF2.
     *
     * This method does not persist this generated key... This key must still be encrypted by the Android fingerprint key system, and then persisted in encrypted format.
     *
     * @param masterPassword    the master password
     */
    public void setAndHashMasterPassword(char[] masterPassword){
        initSalt();

        this.hashedPassword = cryptoUtil.get().generateKeyFromPassword(masterPassword, salt);
    }

    /**
     * Returns the decrypted hashed password key, as used to decrypt the DB.
     *
     * @return a copy of the persisted password hash byte array.
     */
    public byte[] getHashedMasterPassword(){
        return CryptoUtil.cloneByteArray(hashedPassword);
    }

    /**
     * Method used to set the hashed master password (which is the key derived from the master password), into the PasswordContainer.
     * This is typically used then the persisted password hash has been decrypted from file, by the Android fingerprint system.
     *
     * @param hashedPassword the decrypted, persisted password hash
     */
    public void setHashedMasterPassword(byte[] hashedPassword){
        this.hashedPassword = CryptoUtil.cloneByteArray(hashedPassword);
    }

    /**
     * The PasswordContainer is only usable once the hashed master password and salt are present.
     *
     * @return true if we have the hashed master password
     */
    public boolean isReady(){
        return hashedPassword != null;
    }

    /**
     * Clear any byte arrays held in memory.
     */
    public void kill(){
        if (hashedPassword != null) {
            Arrays.fill(hashedPassword, (byte) 0);
            hashedPassword = null;
        }

        if (salt != null) {
            Arrays.fill(salt, (byte) 0);
            salt = null;
        }
    }

}
