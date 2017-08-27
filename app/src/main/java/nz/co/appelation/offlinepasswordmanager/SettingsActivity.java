package nz.co.appelation.offlinepasswordmanager;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;

import java.lang.reflect.Array;
import java.util.Arrays;

import javax.inject.Inject;

import nz.co.appelation.offlinepasswordmanager.model.PasswordContainer;

public class SettingsActivity extends PreferenceActivity {

    private static final String TAG = SettingsActivity.class.getSimpleName();

    private static final int REQUEST_CODE_FOR_CSV_TEMPLATE = 0;

    private static final int REQUEST_CODE_FOR_CSV_EXPORT = 1;

    private static final int REQUEST_CODE_FOR_BACKUP = 2;

    private static final int REQUEST_CODE_FOR_RESTORE = 3;

    private static final int REQUEST_CODE_FOR_CSV_IMPORT = 4;

    private PrefChangeListener prefChangeListener = null;

    private PrefClickListener prefClickListener = null;

    private Dialog activeDialog = null;

    private char[] backupFilePassword = null;

    @Inject
    SharedPreferences prefs;

    @Inject
    CategoryHelper categoryHelper;

    @Inject
    PasswordContainer passwordContainer;

    @Inject
    DBManager dbman;

    @Inject
    ImportExportHelper importExportHelper;

    @Inject
    AppStateHelper appStateHelper;

    @Inject
    CryptoUtil cryptoUtil;

    @Inject
    DialogHelper dialogHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((OfflinePasswordManagerInjectedApplication) getApplication()).inject(this);

        if (!appStateHelper.isAppStateOKForPostAuthentication()){
            Log.i(TAG, "App state NOT OK for post authentication");

            /**
             * Invalid app state detected for post authentication activity: password container is not ready.
             * Finish activity here to prevent exceptions, and let user log in again.
             */
            beforeFinish();
            finish();
        }

        //Prevent screenshots or task manager thumbnails
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        addPreferencesFromResource(R.xml.settings);

        setupActionBar();
    }

    @Override
    protected void onResume() {
        super.onResume();

        prefChangeListener = new PrefChangeListener();
        bindPreferenceChangeListener(findPreference("custom_category_0"));
        bindPreferenceChangeListener(findPreference("custom_category_1"));
        bindPreferenceChangeListener(findPreference("custom_category_2"));
        bindPreferenceChangeListener(findPreference("custom_category_3"));
        bindPreferenceChangeListener(findPreference("custom_category_4"));
        bindPreferenceChangeListener(findPreference("custom_category_5"));
        bindPreferenceChangeListener(findPreference("use_fingeprint"));
        bindPreferenceChangeListener(findPreference("psw_length"));
        setNumberRange((EditTextPreference) findPreference("psw_length"), 1, 64);

        prefClickListener = new PrefClickListener();
        bindPreferenceClickListener(findPreference("action_pref_backup"));
        bindPreferenceClickListener(findPreference("action_pref_restore"));
        bindPreferenceClickListener(findPreference("action_pref_export"));
        bindPreferenceClickListener(findPreference("action_pref_import"));
        bindPreferenceClickListener(findPreference("action_pref_import_gen"));
        bindPreferenceClickListener(findPreference("action_pref_delete_everything"));
    }

    @Override
    protected void onPause() {
        super.onPause();

        unBindPreferenceChangeListener(findPreference("custom_category_0"));
        unBindPreferenceChangeListener(findPreference("custom_category_1"));
        unBindPreferenceChangeListener(findPreference("custom_category_2"));
        unBindPreferenceChangeListener(findPreference("custom_category_3"));
        unBindPreferenceChangeListener(findPreference("custom_category_4"));
        unBindPreferenceChangeListener(findPreference("custom_category_5"));
        unBindPreferenceChangeListener(findPreference("use_fingeprint"));
        unBindPreferenceChangeListener(findPreference("psw_length"));
        prefChangeListener = null;

        unBindPreferenceClickListener(findPreference("action_pref_backup"));
        unBindPreferenceClickListener(findPreference("action_pref_restore"));
        unBindPreferenceClickListener(findPreference("action_pref_export"));
        unBindPreferenceClickListener(findPreference("action_pref_import"));
        unBindPreferenceClickListener(findPreference("action_pref_import_gen"));
        unBindPreferenceClickListener(findPreference("action_pref_delete_everything"));
        prefClickListener = null;
    }

    private void bindPreferenceChangeListener(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(prefChangeListener);

        // Trigger the listener immediately with the preference's current value.
        if (preference.getKey().matches("custom_category_[0-9]")){
            int customCategoryIndex = categoryHelper.getCustomCategoryIndexFromId(preference.getKey());
            prefChangeListener.onPreferenceChange(preference, dbman.getCustomCategoryNameForIndex(customCategoryIndex), false);
        } else if (preference.getKey().matches("custom_category_[0-9]_enabled")) {
            prefChangeListener.onPreferenceChange(preference, prefs.getBoolean(preference.getKey(), false));
        } else if (preference.getKey().equals("psw_length")) {
            prefChangeListener.onPreferenceChange(preference, prefs.getString(preference.getKey(), "15"), false);
        }
    }

    private void bindPreferenceClickListener(Preference preference) {
        preference.setOnPreferenceClickListener(prefClickListener);
    }

    private void unBindPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(null);
    }

    private void unBindPreferenceClickListener(Preference preference) {
        preference.setOnPreferenceClickListener(null);
    }

    /**
     * Set up the {@link ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                beforeFinish();
                finish();
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void createCSVImportTemplate() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, ImportExportHelper.TEMPLATE_FILE);

        startActivityForResult(intent, REQUEST_CODE_FOR_CSV_TEMPLATE);
    }

    private void exportCSV() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, ImportExportHelper.CSV_EXPORT_FILE);

        startActivityForResult(intent, REQUEST_CODE_FOR_CSV_EXPORT);
    }

    private void importCSV() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(intent, REQUEST_CODE_FOR_CSV_IMPORT);
    }

    private void backupDB() {
        Command doWhenPasswordAcquired = new Command() {
            @Override
            public void execute(Object... input) {
                backupFilePassword = (char[])input[0];

                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE, importExportHelper.getBackupFileName());

                startActivityForResult(intent, REQUEST_CODE_FOR_BACKUP);
            }
        };

        promptPasswordEntry(doWhenPasswordAcquired, R.string.msg_backup_encrypt_psw);
    }

    private void restoreDB() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");

        startActivityForResult(intent, REQUEST_CODE_FOR_RESTORE);
    }

    private void promptPasswordEntry(final Command doWhenPasswordAcquired, final int messageId){
        activeDialog = dialogHelper.makePasswordInputDialog(SettingsActivity.this, R.string.title_backup_psw, messageId, android.R.drawable.ic_dialog_info, R.string.action_ok, R.string.action_cancel, new Command() {
            @Override
            public void execute(Object... input) {
                if (input == null || input[0] == null || Array.getLength(input[0]) < CryptoUtil.MIN_PASSWORD_LENGTH){
                    activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_invalid_password, R.string.msg_invalid_password, android.R.drawable.ic_dialog_alert, R.string.action_ok, null, new Command() {
                        @Override
                        public void execute(Object... input) {
                            promptPasswordEntry(doWhenPasswordAcquired, messageId);
                        }
                    }, null);
                } else {
                    doWhenPasswordAcquired.execute((char[]) input[0]);
                }
            }
        }, null, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == Activity.RESULT_OK){
            switch (requestCode) {
                case REQUEST_CODE_FOR_BACKUP: {
                    if (resultData != null) {
                        final Uri uri = resultData.getData();

                        try {
                            int count = importExportHelper.backupDB(uri, backupFilePassword);

                            activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_done, getText(R.string.msg_backup) + " " + count, android.R.drawable.ic_dialog_info, R.string.action_ok, null, null, null);
                        } catch (Exception e){
                            Log.e(TAG, "Unable to create backup DB file", e);

                            activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_generic_error, getText(R.string.msg_generic_error) + e.getMessage(), android.R.drawable.ic_dialog_alert, R.string.action_ok, null, null, null);
                        } finally {
                            beforeFinish();
                        }
                    }

                    break;
                }
                case REQUEST_CODE_FOR_RESTORE: {
                    if (resultData != null) {
                        final Uri uri = resultData.getData();

                        Command doWhenPasswordAcquired = new Command() {
                            @Override
                            public void execute(Object... input) {
                                char[] restoreFilePassword = (char[])input[0];

                                try {
                                    ImportExportHelper.ImportStats stats = importExportHelper.restoreDB(uri, restoreFilePassword);

                                    activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_done, getText(R.string.msg_restore) + " " + stats.successful + getText(R.string.msg_failed) + " " + stats.failed + getText(R.string.msg_restore_masterpassword), android.R.drawable.ic_dialog_info, R.string.action_ok, null, null, null);
                                } catch (WrongPasswordException wpe){
                                    Log.e(TAG, "Incorrect password entered for restore", wpe);

                                    activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_generic_error, R.string.err_incorrect_password, android.R.drawable.ic_dialog_alert, R.string.action_ok, null, null, null);
                                } catch (Exception e){
                                    Log.e(TAG, "Unable to restore DB from backup file", e);

                                    activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_generic_error, getText(R.string.msg_generic_error) + e.getMessage(), android.R.drawable.ic_dialog_alert, R.string.action_ok, null, null, null);
                                } finally {
                                    Arrays.fill(restoreFilePassword, (char) 0);
                                }
                            }
                        };

                        promptPasswordEntry(doWhenPasswordAcquired, R.string.msg_backup_decrypt_psw);
                    }

                    break;
                }
                case REQUEST_CODE_FOR_CSV_EXPORT: {
                    if (resultData != null) {
                        Uri uri = resultData.getData();

                        try {
                            int count = importExportHelper.exportCSV(uri);

                            activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_done, getText(R.string.msg_export_csv) + " " + count, android.R.drawable.ic_dialog_info, R.string.action_ok, null, null, null);
                        } catch (Exception e){
                            Log.e(TAG, "Unable to export to CSV file", e);

                            activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_generic_error, getText(R.string.msg_generic_error) + e.getMessage(), android.R.drawable.ic_dialog_alert, R.string.action_ok, null, null, null);
                        }
                    }

                    break;
                }
                case REQUEST_CODE_FOR_CSV_IMPORT: {
                    if (resultData != null) {
                        Uri uri = resultData.getData();

                        try {
                            ImportExportHelper.ImportStats stats = importExportHelper.importCSV(uri);

                            activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_done, getText(R.string.msg_import_csv) + " " + stats.successful + getText(R.string.msg_failed) + " " + stats.failed, android.R.drawable.ic_dialog_info, R.string.action_ok, null, null, null);
                        } catch (Exception e){
                            Log.e(TAG, "Unable to import from CSV file", e);

                            activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_generic_error, getText(R.string.msg_generic_error) + e.getMessage(), android.R.drawable.ic_dialog_alert, R.string.action_ok, null, null, null);
                        }
                    }

                    break;
                }
                case REQUEST_CODE_FOR_CSV_TEMPLATE: {
                    if (resultData != null) {
                        Uri uri = resultData.getData();

                        try {
                            importExportHelper.createCSVImportTemplate(uri);

                            activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_done, getText(R.string.msg_import_template), android.R.drawable.ic_dialog_info, R.string.action_ok, null, null, null);
                        } catch (Exception e){
                            Log.e(TAG, "Unable to create CSV import template", e);

                            activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_generic_error, getText(R.string.msg_generic_error) + e.getMessage(), android.R.drawable.ic_dialog_alert, R.string.action_ok, null, null, null);
                        }
                    }

                    break;
                }
            }
        }
    }

    private class PrefClickListener implements Preference.OnPreferenceClickListener {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            String key = preference.getKey();
            if (key.equals("action_pref_backup")){
                backupDB();
            } else if (key.equals("action_pref_restore")){
                activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_import_restore_confirm, R.string.msg_import_restore_confirm, android.R.drawable.ic_dialog_alert, R.string.action_import_restore_yes, R.string.action_cancel, new Command() {
                    @Override
                    public void execute(Object... input) {
                        restoreDB();
                    }
                }, null);
            } else if (key.equals("action_pref_export")){
                exportCSV();
            } else if (key.equals("action_pref_import")){
                activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_import_restore_confirm, R.string.msg_import_restore_confirm, android.R.drawable.ic_dialog_alert, R.string.action_import_restore_yes, R.string.action_cancel, new Command() {
                    @Override
                    public void execute(Object... input) {
                        importCSV();
                    }
                }, null);
            } else if (key.equals("action_pref_import_gen")){
                createCSVImportTemplate();
            } else if (key.equals("action_pref_delete_everything")) {
                activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_delete_everything, R.string.msg_delete_everything, android.R.drawable.ic_dialog_alert, R.string.action_delete_everything, R.string.action_cancel, new Command() {
                    @Override
                    public void execute(Object... input) {
                        Log.e(TAG, "Delete everything");

                        /**
                         * Delete everything and exit.
                         */
                        cryptoUtil.deleteKeyPasswordFileSaltAndIV();
                        dbman.deleteCurrentVersionDB();
                        dbman.deleteTempDB();
                        prefs.edit().clear().commit();

                        activeDialog = null;

                        passwordContainer.kill();
                        dbman.killAccountModels();

                        beforeFinish();

                        Process.killProcess(android.os.Process.myPid());
                    }
                }, null);
            }

            return true; //true means we handled the click
        }
    }

    private class PrefChangeListener implements Preference.OnPreferenceChangeListener {

        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            return onPreferenceChange(preference, value, true);
        }

        /**
         * We're using this method inside the OnPreferenceChangeListener for both initialising the preference summary fields, as well as handling additional tasks when a preference is changing.
         * The persist boolean param will be true when a preference is changing, and false if we're simply initialising the UI.
         *
         * @param preference the preference
         * @param value the new value
         * @param persist true if we are to persist the new value, or false if we're just updating the summary field in UI
         * @return true if we want Android to persist the new preference value, or false if we've handled it
         */
        public boolean onPreferenceChange(Preference preference, Object value, boolean persist) {
            String prefKey = preference.getKey();
            if (prefKey.matches("custom_category_[0-9]")){
                String customCategoryName = value.toString();
                preference.setSummary(customCategoryName);

                findPreference(prefKey + "_enabled").setSummary(customCategoryName);

                if (persist) {
                    int customCategoryIndex = categoryHelper.getCustomCategoryIndexFromId(prefKey);
                    dbman.updateCustomCategory(customCategoryIndex, customCategoryName);
                    dbman.saveDB();
                }

                return false; //return false here to NOT persist value in Android preferences... we've already saved it in the passwordDB
            } else if (prefKey.equals("use_fingeprint")) {
                if (persist && Boolean.FALSE.equals(value)) {
                    //Delete key from keystore, and persisted, encrypted password, and IV
                    cryptoUtil.deleteKeyPasswordFileAndIV();
                }
                if (persist && Boolean.TRUE.equals(value)) {
                    activeDialog = dialogHelper.makeDialog(SettingsActivity.this, R.string.title_fingerprint, R.string.msg_fingerprint_change, android.R.drawable.ic_dialog_info, R.string.action_ok, null, null, null);
                }
            } else if (prefKey.equals("psw_length")) {
                String passwordLength = value.toString();
                preference.setSummary(passwordLength);
            }

            return true;
        }
    }

    public static void setNumberRange(final EditTextPreference preference, final Number min, final Number max) {
        setNumberRange(preference, min, max, false);
    }

    public static void setNumberRange(final EditTextPreference preference, final Number min, final Number max, final boolean allowEmpty) {
        preference.getEditText().addTextChangedListener(new TextWatcher(){

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(final Editable editable) {
                Dialog dialog = preference.getDialog();
                if (dialog instanceof AlertDialog) {
                    Button button = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                    if (allowEmpty && editable.length() == 0) {
                        button.setEnabled(true);
                        return;
                    }
                    try {
                        if (min instanceof Integer || max instanceof Integer) {
                            int input = Integer.parseInt(editable.toString());
                            button.setEnabled((min == null || input >= min.intValue()) && (max == null || input <= max.intValue()));
                        } else if (min instanceof Float || max instanceof Float) {
                            float input = Float.parseFloat(editable.toString());
                            button.setEnabled((min == null || input >= min.floatValue()) && (max == null || input <= max.floatValue()));
                        } else if (min instanceof Double || max instanceof Double) {
                            double input = Double.parseDouble(editable.toString());
                            button.setEnabled((min == null || input >= min.doubleValue()) && (max == null || input <= max.doubleValue()));
                        }
                    } catch (Exception e) {
                        button.setEnabled(false);
                    }
                }
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (activeDialog != null){
            activeDialog.dismiss(); //prevent window leak
            activeDialog = null;
        }
    }

    private void beforeFinish(){
        if (backupFilePassword != null) {
            Arrays.fill(backupFilePassword, (char) 0);
            backupFilePassword = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        beforeFinish();
    }

}
