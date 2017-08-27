package nz.co.appelation.offlinepasswordmanager.model.persistence.v1;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * PasswordDB persistence model, v1.
 * Persistence model is separated from display model to allow version changes and upgrades on persisted DB.
 */
public class PasswordDB extends HashMap<String, List<AccountEntry>> implements Serializable {
    public static final long serialVersionUID = 1L; //v1

    public List<String> customCategories = new ArrayList<>();

}
