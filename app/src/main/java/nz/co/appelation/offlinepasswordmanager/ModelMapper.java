package nz.co.appelation.offlinepasswordmanager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nz.co.appelation.offlinepasswordmanager.model.display.AccountEntry;
import nz.co.appelation.offlinepasswordmanager.model.display.PasswordDB;

/**
 * Mapper to convert v1 persistence models and display models.
 * (If new versions of persistence models are required, we'll add more mapper methods)
 *
 * Persistence model is separated from display model to allow version changes and upgrades on persisted DB.
 */
public class ModelMapper {

    public PasswordDB mapPasswordDBFromPersistenceV1ToDisplay(nz.co.appelation.offlinepasswordmanager.model.persistence.v1.PasswordDB pPasswordDB){
        if (pPasswordDB == null){
            return null;
        }

        PasswordDB dPasswordDB = new PasswordDB();

        for (String key : pPasswordDB.keySet()){
            List<nz.co.appelation.offlinepasswordmanager.model.persistence.v1.AccountEntry> pAccountEntries = pPasswordDB.get(key);

            List<AccountEntry> dAccountEntries = null;
            if (pAccountEntries != null) {
                dAccountEntries = new ArrayList<>(pAccountEntries.size());
                for (nz.co.appelation.offlinepasswordmanager.model.persistence.v1.AccountEntry pAccountEntry : pAccountEntries) {
                    dAccountEntries.add(mapAccountEntryFromPersistenceV1ToDisplay(pAccountEntry));
                }
            }

            dPasswordDB.put(key, dAccountEntries);
        }

        dPasswordDB.customCategories.addAll(pPasswordDB.customCategories);

        return dPasswordDB;
    }

    public AccountEntry mapAccountEntryFromPersistenceV1ToDisplay(nz.co.appelation.offlinepasswordmanager.model.persistence.v1.AccountEntry pAccountEntry){
        if (pAccountEntry == null){
            return null;
        }

        return new AccountEntry(pAccountEntry.id, pAccountEntry.name, pAccountEntry.categoryId, pAccountEntry.username, pAccountEntry.password, pAccountEntry.url, pAccountEntry.notes);
    }

    public nz.co.appelation.offlinepasswordmanager.model.persistence.v1.PasswordDB mapPasswordDBFromDisplayToPersistenceV1(PasswordDB dPasswordDB){
        if (dPasswordDB == null){
            return null;
        }

        nz.co.appelation.offlinepasswordmanager.model.persistence.v1.PasswordDB pPasswordDB = new nz.co.appelation.offlinepasswordmanager.model.persistence.v1.PasswordDB();

        for (String key : dPasswordDB.keySet()){
            List<AccountEntry> dAccountEntries = dPasswordDB.get(key);

            List<nz.co.appelation.offlinepasswordmanager.model.persistence.v1.AccountEntry> pAccountEntries = null;
            if (dAccountEntries != null) {
                pAccountEntries = new ArrayList<>(dAccountEntries.size());
                for (AccountEntry dAccountEntry : dAccountEntries) {
                    pAccountEntries.add(mapAccountEntryFromDisplayToPersistenceV1(dAccountEntry));
                }
            }

            pPasswordDB.put(key, pAccountEntries);
        }

        pPasswordDB.customCategories.addAll(dPasswordDB.customCategories);

        return pPasswordDB;
    }

    public nz.co.appelation.offlinepasswordmanager.model.persistence.v1.AccountEntry mapAccountEntryFromDisplayToPersistenceV1(AccountEntry dAccountEntry){
        if (dAccountEntry == null){
            return null;
        }

        return new nz.co.appelation.offlinepasswordmanager.model.persistence.v1.AccountEntry(dAccountEntry.id, dAccountEntry.name, dAccountEntry.categoryId, dAccountEntry.username, dAccountEntry.password, dAccountEntry.url, dAccountEntry.notes);
    }

    /**
     * We keep usernames and passwords in char arrays: http://stackoverflow.com/questions/8881291/why-is-char-preferred-over-string-for-passwords-in-java/8889285#8889285
     *
     * This method kills the usernames and passwords in each model.
     *
     * @param pPasswordDB the persistence model
     */
    public void killModels(nz.co.appelation.offlinepasswordmanager.model.persistence.v1.PasswordDB pPasswordDB){
        if (pPasswordDB == null){
            return;
        }

        for (String key : pPasswordDB.keySet()){
            List<nz.co.appelation.offlinepasswordmanager.model.persistence.v1.AccountEntry> pAccountEntries = pPasswordDB.get(key);

            for (nz.co.appelation.offlinepasswordmanager.model.persistence.v1.AccountEntry pAccountEntry : pAccountEntries) {
                if (pAccountEntry.username != null) {
                    Arrays.fill(pAccountEntry.username, (char) 0);
                    pAccountEntry.username = null;
                }
                if (pAccountEntry.password != null) {
                    Arrays.fill(pAccountEntry.password, (char) 0);
                    pAccountEntry.password = null;
                }
            }
        }
    }

    /**
     * We keep username and password in char arrays: http://stackoverflow.com/questions/8881291/why-is-char-preferred-over-string-for-passwords-in-java/8889285#8889285
     *
     * This method kills the username and password in a model.
     *
     * @param accountEntry the account entry display model
     */
    public void killModel(AccountEntry accountEntry){
        if (accountEntry == null){
            return;
        }

        if (accountEntry.username != null) {
            Arrays.fill(accountEntry.username, (char) 0);
            accountEntry.username = null;
        }
        if (accountEntry.password != null) {
            Arrays.fill(accountEntry.password, (char) 0);
            accountEntry.password = null;
        }
    }

}
