package nz.co.appelation.offlinepasswordmanager;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import nz.co.appelation.offlinepasswordmanager.model.PasswordContainer;

@Module(
        library = true,
        injects = {AuthenticationActivity.class, PasswordManagerActivity.class, DBManager.class, AccountActivity.class, CategoryHelper.class, SettingsActivity.class, NavigationDrawerFragment.class, CategoryHelper.class, PasswordContainer.class, ImportExportHelper.class, AppStateHelper.class, ModelMapper.class, CryptoUtil.class, DialogHelper.class}
)
public class AppModule {

    private static final String TAG = AppModule.class.getSimpleName();

    private final Context context;

    public AppModule(Context context) {
        this.context = context;
    }

    @Provides
    public Context providesContext() {
        return context;
    }

    @Provides
    public FingerprintManager providesFingerprintManager(Context context) {
        return context.getSystemService(FingerprintManager.class);
    }

    @Provides
    public KeyguardManager providesKeyguardManager(Context context) {
        return context.getSystemService(KeyguardManager.class);
    }

    @Provides
    @Singleton
    public PasswordContainer providesPasswordContainer() {
        PasswordContainer passwordContainer = new PasswordContainer();
        ((OfflinePasswordManagerInjectedApplication)context).inject(passwordContainer);

        return passwordContainer;
    }

    @Provides
    public KeyStore providesKeystore() {
        try {
            return KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            Log.e(TAG, "Failed to get an instance of KeyStore", e);
            throw new RuntimeException("Failed to get an instance of KeyStore", e);
        }
    }

    @Provides
    public InputMethodManager providesInputMethodManager(Context context) {
        return (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Provides
    public SharedPreferences providesSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context); //default is MODE_PRIVATE
    }

    @Provides
    @Singleton
    public DBManager providesDBManager(){
        DBManager dbman = new DBManager();
        ((OfflinePasswordManagerInjectedApplication)context).inject(dbman);

        return dbman;
    }

    @Provides
    @Singleton
    public CategoryHelper providesCategoryHelper(){
        CategoryHelper categoryHelper = new CategoryHelper();
        ((OfflinePasswordManagerInjectedApplication)context).inject(categoryHelper);

        return categoryHelper;
    }

    @Provides
    public ImportExportHelper provideImportExportHelper(){
        ImportExportHelper importExportHelper = new ImportExportHelper();
        ((OfflinePasswordManagerInjectedApplication)context).inject(importExportHelper);

        return importExportHelper;
    }

    @Provides
    public AppStateHelper provideAppStateHelper(){
        AppStateHelper appStateHelper = new AppStateHelper();
        ((OfflinePasswordManagerInjectedApplication)context).inject(appStateHelper);

        return appStateHelper;
    }

    @Provides
    @Singleton
    public CryptoUtil provideCryptoUtil(){
        CryptoUtil cryptoUtil = new CryptoUtil();
        ((OfflinePasswordManagerInjectedApplication)context).inject(cryptoUtil);

        return cryptoUtil;
    }

    @Provides
    public ModelMapper provideModelMapper(){
        return new ModelMapper();
    }

    @Provides
    public Resources getResources() {
        return context.getResources();
    }

    @Provides
    @Named("packageName")
    public String getPackageName() {
        return context.getPackageName();
    }

    @Provides
    public DialogHelper provideDialogHelper() {
        DialogHelper dialogHelper = new DialogHelper();
        ((OfflinePasswordManagerInjectedApplication)context).inject(dialogHelper);

        return dialogHelper;
    }

}
