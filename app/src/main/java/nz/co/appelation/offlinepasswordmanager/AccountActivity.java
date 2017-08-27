package nz.co.appelation.offlinepasswordmanager;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

import javax.inject.Inject;

import nz.co.appelation.offlinepasswordmanager.model.AccountCategory;
import nz.co.appelation.offlinepasswordmanager.model.display.AccountEntry;

public class AccountActivity extends Activity {

    private static final String TAG = AccountActivity.class.getSimpleName();

    private static final int BUTTONS_OFF = 0;
    private static final int BUTTONS_SAVE = 1;

    private AccountEntry account = null;
    private boolean isNewAccount = false;
    private boolean hideMenu = false;

    private boolean editMode = false;

    private Dialog activeDialog = null;

    @Inject
    DBManager dbman;

    @Inject
    SharedPreferences prefs;

    @Inject
    CategoryHelper categoryHelper;

    @Inject
    ModelMapper modelMapper;

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

        setContentView(R.layout.account_layout);
        fixMonoFont();

        setupActionBar();

        if (savedInstanceState != null) {
            editMode = savedInstanceState.getBoolean("editMode", false);
        }

        loadAccount(getIntent().getStringExtra(PasswordManagerActivity.PARAM_ACCOUNT_ID));

        findViewById(R.id.randomPassword).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText accountPassword = ((EditText) findViewById(R.id.accountPassword));
                char[] randomPassword = makeRandomPassword();
                accountPassword.setText(randomPassword, 0, randomPassword.length);
                accountPassword.requestFocus();
            }
        });
    }

    private void setupActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Show the Up/Back button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * Generate a new random password, in convenient ascii range.
     *
     * The password length is configurable via the settings, with default of 15 characters.
     *
     * Ref:
     * http://www.cryptofails.com/post/70059604166/password-generators-mathrandom
     *
     * @return the random password.
     */
    private char[] makeRandomPassword() {
        int pswLength = 15;
        try {
            pswLength = Integer.parseInt(prefs.getString("psw_length", "15"));
        } catch (Throwable t){
            //consume
        }

        return cryptoUtil.generateRandomAccountPassword(pswLength);
    }

    private void loadAccount(String accountId){
        modelMapper.killModel(account);

        if (accountId != null){
            account = dbman.getAccount(accountId).copy(); //use a copy so original copy in map stays pristine until update
        } else {
            account = new AccountEntry();
            isNewAccount = true;
            hideMenu = true;
            editMode = true;

            /**
             * Set default username, if configured.
             */
            account.username = prefs.getString("default_username", "").toCharArray();
        }

        if (!editMode){
            makeReadonly();
            initButtons(BUTTONS_OFF);
        } else {
            makeEditable();
            initButtons(BUTTONS_SAVE);
        }

        resetAccountFormFields();
    }

    private void resetAccountFormFields(){
        ((EditText) findViewById(R.id.accountName)).setText(account.name);
        ((EditText) findViewById(R.id.accountUsername)).setText(account.username, 0, account.username.length);
        ((EditText) findViewById(R.id.accountPassword)).setText(account.password, 0, account.password.length);
        ((EditText) findViewById(R.id.accountURL)).setText(account.url);
        ((EditText) findViewById(R.id.accountNotes)).setText(account.notes);
        ((TextView) findViewById(R.id.accountNotesDisplay)).setText(account.notes);

        initCategorySpinner();
    }

    private void initCategorySpinner(){
        List<AccountCategory> accountCategoryList = categoryHelper.getAllActiveAccountCategoriesInclUnspecified();

        /**
         * If account's current category is not enabled, add it to the bottom of the list,
         * otherwise saving the account will override the category assignment to AccountCategory.ID_UNSPECIFIED.
         */
        if (!categoryHelper.isCategoryEnabled(account.categoryId)){
            AccountCategory inactiveCategory = categoryHelper.getAccountCategory(account.categoryId);
            inactiveCategory.name += " " + getString(R.string.not_enabled);
            accountCategoryList.add(inactiveCategory);
        }

        ArrayAdapter<AccountCategory> accountCategoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, android.R.id.text1, accountCategoryList);
        final Spinner accountCategories = ((Spinner) findViewById(R.id.accountCategory));
        accountCategories.setAdapter(accountCategoryAdapter);
        accountCategories.setSelection(getAccountCategoryPositionById(account.categoryId, accountCategoryList));

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                accountCategories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        /**
                         * Double-nested-runnable!? aaaah!!
                         * Just trying to wrestle the dumb Spinner events into submission, and prevent the double-tap required to open spinner...
                         */
                        parent.post(new Runnable() {
                            @Override
                            public void run() {
                                accountCategories.requestFocusFromTouch();
                            }
                        });
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

                accountCategories.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean hasFocus) {
                        if (hasFocus) {
                            accountCategories.performClick();
                        }
                    }
                });
            }
        }, 250);
    }

    private int getAccountCategoryPositionById(String categoryId, List<AccountCategory> accountCategories){
        for (int i = 0; i < accountCategories.size(); i++){
            if (accountCategories.get(i).id.equals(categoryId)){
                return i;
            }
        }

        return 0; //0 will always be AccountCategory.ID_UNSPECIFIED (at least in context of this activity)
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!hideMenu)
        {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.account_menu, menu);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_edit_account: {
                makeEditable();
                initButtons(BUTTONS_SAVE);

                hideMenu = true;
                invalidateOptionsMenu();

                EditText accountName = (EditText) findViewById(R.id.accountName);
                accountName.requestFocus();
                showKeyboard();

                return true;
            }
            case R.id.action_delete_account: {
                activeDialog = dialogHelper.makeDialog(AccountActivity.this, R.string.title_delete, R.string.message_delete, android.R.drawable.ic_delete, R.string.action_delete, R.string.action_cancel, new Command() {
                    @Override
                    public void execute(Object... input) {
                        dbman.deleteAccount(account.id);
                        dbman.saveDB();

                        dialogHelper.makeToast(getResources().getText(R.string.message_delete_done) + " " + account.name);

                        beforeFinish();
                        finish();
                    }
                }, null);

                return true;
            }
            case android.R.id.home: {
                // app icon in action bar clicked; go home
                beforeFinish();
                finish();
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private boolean saveAccount(){
        String accountName = ((EditText) findViewById(R.id.accountName)).getText().toString();
        if (accountName == null || accountName.trim().length() == 0){
            dialogHelper.makeToast(R.string.validation_account_name);
            return false;
        } else {
            account.name = ((EditText) findViewById(R.id.accountName)).getText().toString();

            EditText accountUsername = (EditText) findViewById(R.id.accountUsername);
            account.username = new char[accountUsername.length()];
            accountUsername.getText().getChars(0, accountUsername.length(), account.username, 0);

            EditText accountPassword = (EditText) findViewById(R.id.accountPassword);
            account.password = new char[accountPassword.length()];
            accountPassword.getText().getChars(0, accountPassword.length(), account.password, 0);

            account.url = ((EditText) findViewById(R.id.accountURL)).getText().toString();
            account.categoryId = ((AccountCategory)((Spinner) findViewById(R.id.accountCategory)).getSelectedItem()).id;
            account.notes = ((EditText) findViewById(R.id.accountNotes)).getText().toString();

            dbman.updateAccount(account); //this will add a COPY of account obj to the password DB.
            dbman.saveDB();

            String message = getResources().getString(R.string.message_save_done) + " " + account.name;
            dialogHelper.makeToast(message);

            hideMenu = false;
            editMode = false;
            invalidateOptionsMenu();

            return true;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        MenuInflater inflater = getMenuInflater();

        boolean clipboardEnabled = prefs.getBoolean("clipboard_enabled", false);

        switch (view.getId()) {
            case R.id.accountUsername: {
                if (clipboardEnabled) {
                    view.requestFocus();
                    inflater.inflate(R.menu.username_context_menu, menu);
                }
                break;
            }
            case R.id.accountPassword: {
                if (clipboardEnabled) {
                    view.requestFocus();
                    inflater.inflate(R.menu.password_context_menu, menu);
                }
                break;
            }
            case R.id.accountURL: {
                view.requestFocus();
                inflater.inflate(R.menu.url_context_menu, menu);
                break;
            }
            case R.id.accountNotesDisplay: {
                view.requestFocus();
                inflater.inflate(R.menu.notes_context_menu, menu);
                break;
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        boolean clipboardEnabled = prefs.getBoolean("clipboard_enabled", false);

        switch (item.getItemId()) {
            case R.id.action_copy_username: {
                if (clipboardEnabled) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    StringBuilder username = new StringBuilder(); //preserve username as char array, not string.
                    username.append(account.username);
                    clipboard.setPrimaryClip(ClipData.newPlainText(getResources().getString(R.string.username), username));
                }
                return true;
            }
            case R.id.action_copy_password: {
                if (clipboardEnabled) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    StringBuilder password = new StringBuilder(); //preserve password as char array, not string.
                    password.append(account.password);
                    clipboard.setPrimaryClip(ClipData.newPlainText(getResources().getString(R.string.password), password));
                }
                return true;
            }
            case R.id.action_copy_url: {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(getResources().getString(R.string.url), account.url));
                return true;
            }
            case R.id.action_open_url: {
                String url = account.url;
                if (url != null) {
                    if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")){
                        url = "http://" + url;
                    }

                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                }
                return true;
            }
            case R.id.action_copy_notes: {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(getResources().getString(R.string.notes), account.notes));
                return true;
            }
            default: {
                return super.onContextItemSelected(item);
            }
        }
    }

    private void hideKeyboard(){
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);
        } catch (Throwable t){
            //consume
        }
    }

    private void showKeyboard(){
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(getCurrentFocus(), InputMethodManager.SHOW_IMPLICIT);
                } catch (Throwable t){
                    //consume
                }
            }
        }, 200);
    }

    private void initButtons(int mode){
        if (mode == BUTTONS_OFF){
            findViewById(R.id.buttonArea).setVisibility(View.GONE);
        } else if (mode == BUTTONS_SAVE){
            findViewById(R.id.buttonArea).setVisibility(View.VISIBLE);
            findViewById(R.id.saveButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (saveAccount()){
                        beforeFinish();
                        finish();
                    }
                }
            });

            findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    makeReadonly();
                    initButtons(BUTTONS_OFF);

                    hideKeyboard();

                    hideMenu = false;
                    editMode = false;
                    invalidateOptionsMenu();

                    resetAccountFormFields();

                    if (isNewAccount){
                        beforeFinish();
                        finish();
                    }
                }
            });
        }
    }

    private void makeEditable(){
        editMode = true;

        ((EditText) findViewById(R.id.accountName)).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        EditText accountUsername = ((EditText) findViewById(R.id.accountUsername));
        accountUsername.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        unregisterForContextMenu(accountUsername);

        EditText accountPassword = ((EditText) findViewById(R.id.accountPassword));
        accountPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        unregisterForContextMenu(accountPassword);

        findViewById(R.id.randomPassword).setVisibility(View.VISIBLE);

        EditText accountURL = ((EditText) findViewById(R.id.accountURL));
        accountURL.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        unregisterForContextMenu(accountURL);

        Spinner accountCategories = ((Spinner) findViewById(R.id.accountCategory));
        accountCategories.setEnabled(true);
        accountCategories.setFocusable(true);
        accountCategories.setFocusableInTouchMode(true);

        EditText accountNotes = ((EditText) findViewById(R.id.accountNotes));
        accountNotes.setVisibility(View.VISIBLE);
        accountNotes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        accountNotes.setEnabled(true);
        accountNotes.setMinLines(6);
        accountNotes.setLines(6);
        accountNotes.setMovementMethod(new ScrollingMovementMethod());
        accountNotes.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent event) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
                switch (event.getAction() & MotionEvent.ACTION_MASK){
                    case MotionEvent.ACTION_UP: {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                    }
                }
                return false;
            }
        });

        TextView accountNotesDisplay = ((TextView) findViewById(R.id.accountNotesDisplay));
        accountNotesDisplay.setVisibility(View.GONE);
        unregisterForContextMenu(accountNotesDisplay);
        accountNotesDisplay.setMinLines(6);
        accountNotesDisplay.setLines(6);
    }

    private void makeReadonly() {
        editMode = false;

        ((EditText) findViewById(R.id.accountName)).setInputType(InputType.TYPE_NULL);

        EditText accountUsername = ((EditText) findViewById(R.id.accountUsername));
        accountUsername.setInputType(InputType.TYPE_NULL);
        registerForContextMenu(accountUsername);

        EditText accountPassword = ((EditText) findViewById(R.id.accountPassword));
        accountPassword.setInputType(InputType.TYPE_NULL);
        registerForContextMenu(accountPassword);

        findViewById(R.id.randomPassword).setVisibility(View.GONE);

        EditText accountURL = ((EditText) findViewById(R.id.accountURL));
        accountURL.setInputType(InputType.TYPE_NULL);
        registerForContextMenu(accountURL);

        Spinner accountCategories = ((Spinner) findViewById(R.id.accountCategory));
        accountCategories.setEnabled(false);
        accountCategories.setFocusable(false);
        accountCategories.setFocusableInTouchMode(false);

        EditText accountNotes = ((EditText) findViewById(R.id.accountNotes));
        accountNotes.setVisibility(View.GONE);
        accountNotes.setEnabled(false);
        accountNotes.setMinLines(6);
        accountNotes.setLines(6);

        TextView accountNotesDisplay = ((TextView) findViewById(R.id.accountNotesDisplay));
        accountNotesDisplay.setVisibility(View.VISIBLE);
        registerForContextMenu(accountNotesDisplay);
        accountNotesDisplay.setMinLines(6);
        accountNotesDisplay.setLines(6);
        accountNotesDisplay.setMovementMethod(new ScrollingMovementMethod());
        accountNotesDisplay.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent event) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
                switch (event.getAction() & MotionEvent.ACTION_MASK){
                    case MotionEvent.ACTION_UP: {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                    }
                }
                return false;
            }
        });
    }

    private void fixMonoFont(){
        Typeface fnt = Typeface.createFromAsset(getAssets(), "fonts/VeraMono.ttf");
        ((EditText) findViewById(R.id.accountUsername)).setTypeface(fnt);
        ((EditText) findViewById(R.id.accountPassword)).setTypeface(fnt);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (activeDialog != null){
            activeDialog.dismiss(); //prevent window leak
            activeDialog = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (!isFinishing()){
            outState.putBoolean("editMode", editMode);
        }
    }

    private void beforeFinish(){
        View accountNotes = findViewById(R.id.accountNotes);
        if (accountNotes != null) {
            accountNotes.setOnTouchListener(null);
        }

        View accountNotesDisplay = findViewById(R.id.accountNotesDisplay);
        if (accountNotesDisplay != null) {
            accountNotesDisplay.setOnTouchListener(null);
        }

        if (modelMapper != null) {
            modelMapper.killModel(account);
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        beforeFinish();
    }

}
