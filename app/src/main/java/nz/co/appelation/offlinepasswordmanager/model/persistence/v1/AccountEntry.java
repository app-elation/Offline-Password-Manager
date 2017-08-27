package nz.co.appelation.offlinepasswordmanager.model.persistence.v1;

import java.io.Serializable;

import nz.co.appelation.offlinepasswordmanager.CryptoUtil;

/**
 * AccountEntry persistence model, v1.
 * Persistence model is separated from display model to allow version changes and upgrades on persisted DB.
 *
 * This class will copy any char[] passed into constructor.
 */
public class AccountEntry implements Serializable {
    public static final long serialVersionUID = 1L; //v1

    public volatile String id;
    public volatile String name;
    public volatile String categoryId;
    public volatile char[] username;
    public volatile char[] password;
    public volatile String url;
    public volatile String notes;

    public AccountEntry(String id, String name, String categoryId, char[] username, char[] password, String url, String notes){
        this.id = id;
        this.name = name;
        this.categoryId = categoryId;
        this.username = CryptoUtil.cloneCharArray(username);
        this.password = CryptoUtil.cloneCharArray(password);
        this.url = url;
        this.notes = notes;
    }

}



