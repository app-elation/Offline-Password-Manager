package nz.co.appelation.offlinepasswordmanager.model.display;

import java.util.UUID;

import nz.co.appelation.offlinepasswordmanager.CryptoUtil;
import nz.co.appelation.offlinepasswordmanager.model.AccountCategory;

/**
 * The AccountEntry is the main model for holding account entry fields.
 *
 * The display model has all fields as transient and is not serializable.
 *
 * This class will copy any char[] passed into constructor.
 */
public class AccountEntry implements Cloneable {
    public transient volatile String id;
    public transient volatile String name;
    public transient volatile String categoryId;
    public transient volatile char[] username;
    public transient volatile char[] password;
    public transient volatile String url;
    public transient volatile String notes;

    public AccountEntry(){
        this.id = makeId();
        this.categoryId = AccountCategory.ID_UNSPECIFIED;
        this.username = new char[0];
        this.password = new char[0];
    }

    public AccountEntry(String name, String categoryId, char[] username, char[] password, String url, String notes){
        this.id = makeId();
        this.name = name;
        this.categoryId = categoryId;
        this.username = CryptoUtil.cloneCharArray(username);
        this.password = CryptoUtil.cloneCharArray(password);
        this.url = url;
        this.notes = notes;
    }

    public AccountEntry(String id, String name, String categoryId, char[] username, char[] password, String url, String notes){
        this.id = id;
        this.name = name;
        this.categoryId = categoryId;
        this.username = CryptoUtil.cloneCharArray(username);
        this.password = CryptoUtil.cloneCharArray(password);
        this.url = url;
        this.notes = notes;
    }

    private static String makeId(){
        return UUID.randomUUID().toString();
    }

    public AccountEntry copy(){
        try {
            AccountEntry clone = (AccountEntry) this.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        AccountEntry clone = (AccountEntry) super.clone();

        clone.username = CryptoUtil.cloneCharArray(this.username);
        clone.password = CryptoUtil.cloneCharArray(this.password);

        return clone;
    }

    /**
     * This string becomes the label in the main account list view.
     *
     * @return the account name
     */
    @Override
    public String toString(){
        return name;
    }

}



