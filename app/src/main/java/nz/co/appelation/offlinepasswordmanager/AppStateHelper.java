package nz.co.appelation.offlinepasswordmanager;

import javax.inject.Inject;

import dagger.Lazy;
import nz.co.appelation.offlinepasswordmanager.model.PasswordContainer;

/**
 * Helper class to check app state.
 */
public class AppStateHelper {

    private static final String TAG = AppStateHelper.class.getSimpleName();

    @Inject
    Lazy<PasswordContainer> passwordContainer;

    /**
     * Android may terminate an application and relaunch it back to a particular activity. This might happen when its running in the background and resources are low, or the user forces a permission change form the OS Settings menu.
     * Restoring to a previous activity may also happen if the app performs a controlled termination.
     *
     * In typical Android apps, an activity is supposed to persist it's state to a "Bundle" which can be used to restore the activity in these events.
     *
     * When the app is restored, a new OfflinePasswordManagerInjectedApplication is created, all required objects are created and injected into the activity which Android determines should be on top.
     *
     * This is a problem for this app - the decrypted master password hash in memory in the PasswordContainer. The PasswordContainer is only populated via the authentication process where the master password is either entered and hashed or decrypted from file via fingerprint-authed key.
     * There is no way to redo this except request input from the user, and we cannot safely persist the PasswordContainer in this state, not store it in the Bundle.
     *
     * The Bundle requires all values be "Parcelable"...
     * Once the PasswordContainer is Parcelable, it can be serialised or persisted by the OS outside our control.
     *
     * Currently this app has no special way of dealing with this app-restore, besides detecting it, and directing the use to re-authenticate.
     *
     * This method should be called in the onCreate() of every activity after authentication, and if the return is false, call finish() to close the activity before it attempts to load or perform any other action.
     *
     * @return true is passwordContainer is empty. If tested at any point post authentication, it indicates that the process has been restores by Android, and the password is not lost.
     */
    public boolean isAppStateOKForPostAuthentication(){
        return passwordContainer.get().isReady();
    }

}
