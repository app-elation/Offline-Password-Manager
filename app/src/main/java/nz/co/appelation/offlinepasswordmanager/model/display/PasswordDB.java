package nz.co.appelation.offlinepasswordmanager.model.display;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The PasswordDB is the main collection model for account entries.
 * It extends HashMap for backing collection.
 * The key is the category-id, and the value is a list of account entries.
 * There is also a list of custom categories who's index corresponds to the custom category suffix (E.g.: CUSTOM_0).
 *
 * The display model has all fields as transient and is not serializable.
 */
public class PasswordDB extends HashMap<String, List<AccountEntry>> {

    public transient List<String> customCategories = new ArrayList<>();

}
