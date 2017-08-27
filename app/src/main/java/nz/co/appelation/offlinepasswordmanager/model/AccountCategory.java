package nz.co.appelation.offlinepasswordmanager.model;

import java.io.Serializable;

public class AccountCategory implements Serializable {
    public static final long serialVersionUID = 1L;

    public static final String ALL = "ALL"; //ALL is technically not an ID since its not used as a key in the map...

    public static final String ID_UNSPECIFIED = "UNSPECIFIED"; //An account which is not assigned a category, get this value for categoryId
    public static final String ID_SHOPPING = "SHOPPING";
    public static final String ID_SOCIAL = "SOCIAL";
    public static final String ID_BANKING = "BANKING";

    public static final String ID_CUSTOM_0 = "CUSTOM_0";
    public static final String ID_CUSTOM_1 = "CUSTOM_1";
    public static final String ID_CUSTOM_2 = "CUSTOM_2";
    public static final String ID_CUSTOM_3 = "CUSTOM_3";
    public static final String ID_CUSTOM_4 = "CUSTOM_4";
    public static final String ID_CUSTOM_5 = "CUSTOM_5";

    public String id;
    public String name;
    public boolean enabled = false;

    public AccountCategory(String id, String name){
        this.id = id;
        this.name = name;
        this.enabled = true;
    }

    public AccountCategory(String id, String name, boolean enabled){
        this.id = id;
        this.name = name;
        this.enabled = enabled;
    }

    @Override
    public String toString(){
        return name;
    }

}
