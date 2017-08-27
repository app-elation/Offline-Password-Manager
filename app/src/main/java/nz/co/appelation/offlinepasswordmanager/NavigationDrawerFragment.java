package nz.co.appelation.offlinepasswordmanager;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import nz.co.appelation.offlinepasswordmanager.model.AccountCategory;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
public class NavigationDrawerFragment extends Fragment {

    /**
     * Remember the position of the selected item.
     */
    private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";

    /**
     * Per the design guidelines, you should show the drawer on launch until the user manually
     * expands it. This shared preference tracks this.
     */
    private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";

    /**
     * A pointer to the current callbacks instance (the Activity).
     */
    private NavigationDrawerCallbacks navDrawerCallbacks;
    private SearchCallbacks searchCallbacks;

    /**
     * Helper component that ties the action bar to the navigation drawer.
     */
    private ActionBarDrawerToggle drawerToggle;

    private DrawerLayout drawerLayout;
    private ListView drawerListView;
    private View fragmentContainerView;
    private Menu menu;

    private int currentSelectedPosition = 0;
    private boolean fromSavedInstanceState;
    private boolean userLearnedDrawer;

    @Inject
    CategoryHelper categoryHelper;

    @Inject
    SharedPreferences prefs;

    public NavigationDrawerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((OfflinePasswordManagerInjectedApplication) getActivity().getApplication()).inject(this);

        // Read in the flag indicating whether or not the user has demonstrated awareness of the
        // drawer. See PREF_USER_LEARNED_DRAWER for details.

        userLearnedDrawer = prefs.getBoolean(PREF_USER_LEARNED_DRAWER, false);

        if (savedInstanceState != null) {
            currentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
            fromSavedInstanceState = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        List<AccountCategory> menuOptions = getDrawerMenuOption();
        ((ArrayAdapter) drawerListView.getAdapter()).clear();
        ((ArrayAdapter) drawerListView.getAdapter()).addAll(menuOptions);
        ((ArrayAdapter) drawerListView.getAdapter()).notifyDataSetChanged();

        if (menuOptions.size() - 2 <= currentSelectedPosition){
            // If we removed category options, then don't select settings or exit, but jump back to all-category
            currentSelectedPosition = 0;
        }

        // Select either the default item (0) or the last selected item.
        selectDrawerItem(menuOptions.get(currentSelectedPosition), currentSelectedPosition);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Indicate that this fragment would like to influence the set of actions in the action bar.
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        drawerListView = (ListView) inflater.inflate(R.layout.drawer_password_manager, container, false);

        drawerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectDrawerItem((AccountCategory) parent.getItemAtPosition(position), position);
            }
        });

        drawerListView.setAdapter(new ArrayAdapter<>(
                getActionBar().getThemedContext(),
                android.R.layout.simple_list_item_activated_1,
                android.R.id.text1,
                new ArrayList<AccountCategory>()));

        return drawerListView;
    }

    public List<AccountCategory> getDrawerMenuOption(){
        List<AccountCategory> ret = new ArrayList<>();
        ret.addAll(categoryHelper.getAllActiveAccountCategoriesInclAll());

        ret.add(new AccountCategory(PasswordManagerActivity.SETTINGS_ID, getResources().getString(R.string.action_settings)));

        ret.add(new AccountCategory(PasswordManagerActivity.EXIT_ID, getResources().getString(R.string.action_exit)));

        return ret;
    }

    public boolean isDrawerOpen() {
        return drawerLayout != null && drawerLayout.isDrawerOpen(fragmentContainerView);
    }

    public void closeDrawer() {
        if (drawerLayout != null){
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * Users of this fragment must call this method to set up the navigation drawer interactions.
     *
     * @param fragmentId   The android:id of this fragment in its activity's layout.
     * @param drawerLayout The DrawerLayout containing this fragment's UI.
     */
    public void setUp(int fragmentId, DrawerLayout drawerLayout) {
        fragmentContainerView = getActivity().findViewById(fragmentId);

        this.drawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer opens
        this.drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.
        drawerToggle = new ActionBarDrawerToggle(
                getActivity(),                    /* host Activity */
                drawerLayout,                    /* DrawerLayout object */
                R.drawable.ic_drawer,             /* nav drawer image to replace 'Up' caret */
                R.string.navigation_drawer_open,  /* "open drawer" description for accessibility */
                R.string.navigation_drawer_close  /* "close drawer" description for accessibility */) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

                if (!isAdded()) {
                    return;
                }

                getActivity().invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                if (!isAdded()) {
                    return;
                }

                if (!userLearnedDrawer) {
                    // The user manually opened the drawer; store this flag to prevent auto-showing
                    // the navigation drawer automatically in the future.
                    userLearnedDrawer = true;
                    prefs.edit().putBoolean(PREF_USER_LEARNED_DRAWER, true).apply();
                }

                getActivity().invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                if (newState == DrawerLayout.STATE_DRAGGING || newState == DrawerLayout.STATE_SETTLING) {
                    hideKeyboard();
                    searchCallbacks.onResetSearch();
                }
            }
        };

        // If the user hasn't 'learned' about the drawer, open it to introduce them to the drawer,
        // per the navigation drawer design guidelines.
        if (!userLearnedDrawer && !fromSavedInstanceState) {
            Handler handler = new Handler();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    openDrawer();
                }
            });
        }

        // Defer code dependent on restoration of previous instance state.
        this.drawerLayout.post(new Runnable() {
            @Override
            public void run() {
                drawerToggle.syncState();
            }
        });

        this.drawerLayout.addDrawerListener(drawerToggle);
    }

    private void openDrawer(){
        drawerLayout.openDrawer(fragmentContainerView);
    }

    private void hideKeyboard(){
        try {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getActivity().getWindow().getDecorView().getWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);
        } catch (Throwable t){
            //consume
        }
    }

    private void selectDrawerItem(AccountCategory drawerItem, int position) {
        currentSelectedPosition = position;

        if (drawerListView != null) {
            drawerListView.setItemChecked(position, true);
        }

        if (drawerLayout != null) {
            drawerLayout.closeDrawer(fragmentContainerView);
        }

        if (navDrawerCallbacks != null) {
            navDrawerCallbacks.onNavigationDrawerItemSelected(drawerItem);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            navDrawerCallbacks = (NavigationDrawerCallbacks) context;
            searchCallbacks = (SearchCallbacks) context;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement NavigationDrawerCallbacks.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        navDrawerCallbacks = null;
        searchCallbacks = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (PasswordManagerActivity.SETTINGS_ID.equals(getDrawerMenuOption().get(currentSelectedPosition).id)){
            // If we're going to settings menu, then we'll come back to ALL category (since categories might change while in settings...)
            currentSelectedPosition = 0;
        }

        outState.putInt(STATE_SELECTED_POSITION, currentSelectedPosition);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Forward the new configuration the drawer toggle component.
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // If the drawer is open, show the global app actions in the action bar. See also
        // showGlobalContextActionBar, which controls the top-left area of the action bar.
        if (drawerLayout != null && !isDrawerOpen()) {
            inflater.inflate(R.menu.global, menu);
            this.menu = menu;

            ActionBar actionBar = getActionBar();
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            actionBar.setTitle(R.string.app_name);

            // Associate searchable configuration with the SearchView
            SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
            SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
            SearchableInfo info = searchManager.getSearchableInfo(getActivity().getComponentName());
            searchView.setSearchableInfo(info);

            searchView.setOnSearchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchCallbacks.onSearchOpen();
                }
            });

            searchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    searchCallbacks.onSearchClose();
                    return false;
                }
            });

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchCallbacks.onSearchQuerySubmit(query != null ? query : "");
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    searchCallbacks.onSearchQueryUpdate(newText != null ? newText : "");
                    return false;
                }
            });
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        if (item.getItemId() == R.id.action_search) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private ActionBar getActionBar() {
        return getActivity().getActionBar();
    }

    public void closeSearch() {
        SearchView searchView = (android.widget.SearchView) menu.findItem(R.id.action_search).getActionView();

        if (!searchView.isIconified()) {
            searchView.setOnQueryTextListener(null);
            searchView.setIconified(true);
            searchView.clearFocus();
            menu.findItem(R.id.action_search).collapseActionView();
            getActivity().invalidateOptionsMenu();
        }

        searchCallbacks.onSearchClose();
    }

    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public static interface NavigationDrawerCallbacks {
        /**
         * Called when an item in the navigation drawer is selected.
         */
        void onNavigationDrawerItemSelected(AccountCategory drawerItem);
    }

    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public static interface SearchCallbacks {
        void onSearchOpen();
        void onSearchClose();
        void onResetSearch();
        void onSearchQuerySubmit(String query);
        void onSearchQueryUpdate(String query);
    }

}
