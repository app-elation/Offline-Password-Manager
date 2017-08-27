package nz.co.appelation.offlinepasswordmanager;

import android.content.SharedPreferences;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import nz.co.appelation.offlinepasswordmanager.model.AccountCategory;

public class CategoryHelper {

    @Inject
    SharedPreferences prefs;

    @Inject
    Resources resources;

    @Inject
    Lazy<DBManager> dbman; //Use lazy init here, since we have a circular dep between DBManager and CategoryHelper, however we need CategoryHelper only after DBManager inits...

    @Inject
    @Named("packageName")
    String packageName;

    /**
     * Translation table to map AccountCategory Ids to preference Ids.
     * Excludes "ALL" and "UNSPECIFIED".
     */
    private static final Map<String, String> CATEGORY_ID_TO_PREF_ID_TRANSLATION = new LinkedHashMap(9);

    private static final String CUSTOM_CATEGORY_DEFAULT_NAME_STRING_RESOURCE_ID_PREFIX = "custom_category_";

    private static final String CUSTOM_CATEGORY_ID_PREFIX = "CUSTOM_";

    static {
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_SHOPPING, "category_shopping");
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_SOCIAL, "category_social");
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_BANKING, "category_banking");
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_CUSTOM_0, "custom_category_0");
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_CUSTOM_1, "custom_category_1");
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_CUSTOM_2, "custom_category_2");
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_CUSTOM_3, "custom_category_3");
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_CUSTOM_4, "custom_category_4");
        CATEGORY_ID_TO_PREF_ID_TRANSLATION.put(AccountCategory.ID_CUSTOM_5, "custom_category_5");
    }

    public static final int NUMBER_OF_CUSTOM_CATEGORIES = 6;

    public List<AccountCategory> getAllActiveAccountCategoriesInclUnspecified(){
        List<AccountCategory> categories = new ArrayList<>();

        categories.add(new AccountCategory(AccountCategory.ID_UNSPECIFIED, resources.getString(R.string.category_unspecified)));

        categories.addAll(getActiveAccountCategoriesExclNoneAll());

        return categories;
    }

    public List<AccountCategory> getAllActiveAccountCategoriesInclAll(){
        List<AccountCategory> categories = new ArrayList<>();

        categories.add(new AccountCategory(AccountCategory.ALL, resources.getString(R.string.category_all)));

        categories.addAll(getActiveAccountCategoriesExclNoneAll());

        return categories;
    }

    private List<AccountCategory> getActiveAccountCategoriesExclNoneAll(){
        List<AccountCategory> categories = new ArrayList<>();

        for (String categoryId : CATEGORY_ID_TO_PREF_ID_TRANSLATION.keySet()){
            if (isCategoryEnabled(categoryId)){
                categories.add(getAccountCategory(categoryId));
            }
        }

        return categories;
    }

    public AccountCategory getAccountCategory(String categoryId) {
        String categoryName = null;

        if (categoryId != null) {
            if (!categoryId.toLowerCase().contains("custom")) {
                try {
                    categoryName = resources.getString(resources.getIdentifier(CATEGORY_ID_TO_PREF_ID_TRANSLATION.get(categoryId), "string", packageName));
                } catch (Throwable t) {
                    //consume
                }
            } else {
                categoryName = dbman.get().getCustomCategoryNameForIndex(getCustomCategoryIndexFromId(categoryId));
            }
        }

        if (categoryName == null){
            categoryName = resources.getString(R.string.unknown_category);
        }

        return new AccountCategory(categoryId, categoryName, isCategoryEnabled(categoryId));
    }

    public boolean isCategoryEnabled(String categoryId){
        if (categoryId == null){
            return false;
        }

        boolean defaultValue = false;
        if (!categoryId.toLowerCase().contains("custom")){
            /**
             * Shopping, Social, Bank base categories are default enabled. All custom categories are default disabled.
             */
            defaultValue = true;
        }

        return prefs.getBoolean(CATEGORY_ID_TO_PREF_ID_TRANSLATION.get(categoryId) + "_enabled", defaultValue);
    }

    public int getCustomCategoryStringResourceId(int customCategoryIndex){
        return resources.getIdentifier(CUSTOM_CATEGORY_DEFAULT_NAME_STRING_RESOURCE_ID_PREFIX + customCategoryIndex, "string", packageName);
    }

    public String getCustomCategoryEnabledPreferenceId(int customCategoryIndex){
        return CUSTOM_CATEGORY_DEFAULT_NAME_STRING_RESOURCE_ID_PREFIX + customCategoryIndex + "_enabled";
    }

    public String getCategoryEnabledPreferenceId(String categoryId){
        return CATEGORY_ID_TO_PREF_ID_TRANSLATION.get(categoryId) + "_enabled";
    }

    public String getCustomCategoryIdFromIndex(int customCategoryIndex){
        return CUSTOM_CATEGORY_ID_PREFIX + customCategoryIndex;
    }

    public List<String> getDefaultCustomCategoryNames(){
        List<String> defaultNames = new ArrayList<>();

        for (int i = 0; i < NUMBER_OF_CUSTOM_CATEGORIES; i++){
            defaultNames.add(resources.getString(getCustomCategoryStringResourceId(i)));
        }

        return defaultNames;
    }

    public int getCustomCategoryIndexFromId(String customCategoryId){
        if (customCategoryId == null){
            return 0;
        }

        String customCategoryIndex = customCategoryId.substring(customCategoryId.length() - 1, customCategoryId.length());
        try {
            return Integer.parseInt(customCategoryIndex);
        } catch (Throwable t){
            //consume
            return 0;
        }
    }

}
