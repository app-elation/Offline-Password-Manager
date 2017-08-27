package nz.co.appelation.offlinepasswordmanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import nz.co.appelation.offlinepasswordmanager.model.AccountCategory;
import nz.co.appelation.offlinepasswordmanager.model.PasswordContainer;
import nz.co.appelation.offlinepasswordmanager.model.display.AccountEntry;

public class PasswordManagerActivity extends Activity implements NavigationDrawerFragment.NavigationDrawerCallbacks, NavigationDrawerFragment.SearchCallbacks {

    private static final String TAG = PasswordManagerActivity.class.getSimpleName();

    public final static String SETTINGS_ID = UUID.randomUUID().toString();

    public final static String EXIT_ID = UUID.randomUUID().toString();

    public final static String PARAM_ACCOUNT_ID = "ACCOUNT_ID";

    private NavigationDrawerFragment navigationDrawerFragment;

    private ArrayAdapter<AccountEntry> accountAdapter;

    private List<AccountEntry> accounts = new ArrayList<>();

    private String selectedDrawerItemId = AccountCategory.ALL;

    private boolean searchOpen = false;

    @Inject
    SharedPreferences prefs;

    @Inject
    DBManager dbman;

    @Inject
    Context ctx;

    @Inject
    AppStateHelper appStateHelper;

    @Inject
    DialogHelper dialogHelper;

    @Inject
    PasswordContainer passwordContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((OfflinePasswordManagerInjectedApplication) getApplication()).inject(this);

        if (!appStateHelper.isAppStateOKForPostAuthentication()){
            Log.i(TAG, "App state NOT OK for post authentication");

            /**
             * Invalid app state detected for post authentication activity: password container is not ready.
             * Relaunch activity here to prevent exceptions, and let user log in again.
             */
            Intent backToAuthIntent = new Intent(this, AuthenticationActivity.class);
            backToAuthIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(backToAuthIntent);
            beforeFinish();
            finish();
        }

        //Prevent screenshots or task manager thumbnails
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.password_manager_layout);

        navigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);

        // Set up the drawer.
        navigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout));

        initAccountList();

        findViewById(R.id.addAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ctx, AccountActivity.class);
                intent.putExtra(PARAM_ACCOUNT_ID, (String) null);
                startActivity(intent);
            }
        });
    }

    private void initAccountList() {
        ListView accountList = (ListView)findViewById(R.id.accountList);

        /**
         * Accounts list is initialised from the NavigationDrawerFragment#selectDrawerItem() method, which is also called from onResume().
         */

        accountAdapter = new ArrayAdapter<>(this, R.layout.account_list_item, accounts);
        accountList.setAdapter(accountAdapter);

        registerForContextMenu(accountList);

        accountList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                final AccountEntry account = (AccountEntry) parent.getItemAtPosition(position);

                navigationDrawerFragment.closeSearch();

                Intent intent = new Intent(ctx, AccountActivity.class);
                intent.putExtra(PARAM_ACCOUNT_ID, account.id);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.account_context_menu, menu);

        boolean clipboardEnabled = prefs.getBoolean("clipboard_enabled", false);
        if (!clipboardEnabled){
            menu.removeItem(R.id.action_copy_username);
            menu.removeItem(R.id.action_copy_password);
            menu.removeItem(R.id.action_copy_password_and_open_url);
            menu.removeItem(R.id.action_copy_password_show_username_and_open_url);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        AccountEntry account = accounts.get(info.position);

        boolean clipboardEnabled = prefs.getBoolean("clipboard_enabled", false);

        switch (item.getItemId()) {
            case R.id.action_copy_username: {
                if (clipboardEnabled) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    StringBuilder username = new StringBuilder(); //preserve password as char array, not string.
                    username.append(account.password);
                    ClipData clip = ClipData.newPlainText(getResources().getString(R.string.username), username);
                    clipboard.setPrimaryClip(clip);
                }
                return true;
            }
            case R.id.action_copy_password: {
                if (clipboardEnabled) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    StringBuilder password = new StringBuilder(); //preserve password as char array, not string.
                    password.append(account.password);
                    ClipData clip = ClipData.newPlainText(getResources().getString(R.string.password), password);
                    clipboard.setPrimaryClip(clip);
                }
                return true;
            }
            case R.id.action_copy_password_and_open_url: {
                if (clipboardEnabled) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    StringBuilder password = new StringBuilder(); //preserve password as char array, not string.
                    password.append(account.password);
                    ClipData clip = ClipData.newPlainText(getResources().getString(R.string.password), password);
                    clipboard.setPrimaryClip(clip);

                    openUrl(account.url);
                }
                return true;
            }
            case R.id.action_copy_password_show_username_and_open_url: {
                if (clipboardEnabled) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    StringBuilder password = new StringBuilder(); //preserve password as char array, not string.
                    password.append(account.password);
                    ClipData clip = ClipData.newPlainText(getResources().getString(R.string.password), password);
                    clipboard.setPrimaryClip(clip);

                    StringBuilder username = new StringBuilder(); //preserve username as char array, not string.
                    username.append(account.username);

                    dialogHelper.makeToast(username);

                    openUrl(account.url);
                }
                return true;
            }
            case R.id.action_open_url: {
                openUrl(account.url);
                return true;
            }
            default: {
                return super.onContextItemSelected(item);
            }
        }
    }

    private void openUrl(String url){
        if (url != null) {
            if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")){
                url = "http://" + url;
            }

            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(AccountCategory drawerItem) {
        if (drawerItem.id.equals(SETTINGS_ID)) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (drawerItem.id.equals(EXIT_ID)){
            /**
             * The previous activity, AuthenticationActivity, is set as no-history, so finishing this activity closes the app.
             */
            beforeFinish();
            finish();
        } else {
            selectedDrawerItemId = drawerItem.id;
            filterAccounts(selectedDrawerItemId);
        }
    }

    @Override
    public void onSearchOpen() {
        findViewById(R.id.addAccount).setVisibility(View.GONE);
        searchOpen = true;
        clearAccounts();
    }

    @Override
    public void onSearchQuerySubmit(String query){
        searchAccounts(query);
    }

    @Override
    public void onSearchQueryUpdate(String query){
        searchAccounts(query);
    }

    @Override
    public void onSearchClose() {
        findViewById(R.id.addAccount).setVisibility(View.VISIBLE);
        searchOpen = false;
        filterAccounts(selectedDrawerItemId);
    }

    @Override
    public void onResetSearch(){
        findViewById(R.id.addAccount).setVisibility(View.VISIBLE);
        searchOpen = false;
        filterAccounts(selectedDrawerItemId);
    }

    private void searchAccounts(String query) {
        if (!searchOpen) {
            //Damn zombi instance of OnQueryTextListener still spamming onQueryTextChange after search is closed!
            return;
        }

        accounts.clear();

        if (query != null && query.trim().length() != 0){
            for (AccountEntry accountEntry : dbman.getAllAccounts()) {
                String accountName = accountEntry.name != null ? accountEntry.name : "";
                if (accountName.toLowerCase().contains(query.toLowerCase())) {
                    accounts.add(accountEntry);
                }
            }
        }

        if (accountAdapter != null) {
            accountAdapter.notifyDataSetChanged();
        }
    }

    private void clearAccounts() {
        accounts.clear();

        if (accountAdapter != null) {
            accountAdapter.notifyDataSetChanged();
        }
    }

    private void filterAccounts(String categoryId){
        accounts.clear();

        for (AccountEntry accountEntry : dbman.getAllAccounts()){
            if (categoryId.equals(AccountCategory.ALL)){
                accounts.add(accountEntry);
            } else if (accountEntry.categoryId.equals(categoryId)) {
                accounts.add(accountEntry);
            }
        }

        if (accountAdapter != null) {
            accountAdapter.notifyDataSetChanged();
        }
    }

    private void beforeFinish(){
        if (passwordContainer != null) {
            passwordContainer.kill();
        }

        if (dbman != null) {
            dbman.killAccountModels();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (isFinishing()) {
            beforeFinish();
        }
    }

    @Override
    public void onBackPressed() {
        /**
         * If drawer is open, close drawer.
         * If search is open, close search.
         * If we're in a category view, go back to ALL view.
         * Else close activity.
         */
        if (navigationDrawerFragment.isDrawerOpen()){
            navigationDrawerFragment.closeDrawer();
        } else if (searchOpen) {
            navigationDrawerFragment.closeSearch(); //trigger close of search input
            searchOpen = false;
        } else if (!AccountCategory.ALL.equals(selectedDrawerItemId)){
            selectedDrawerItemId = AccountCategory.ALL;
            filterAccounts(selectedDrawerItemId);
        } else {
            beforeFinish();

            super.onBackPressed();
        }
    }

}
