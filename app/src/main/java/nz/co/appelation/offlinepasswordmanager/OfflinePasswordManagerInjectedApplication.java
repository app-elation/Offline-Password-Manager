package nz.co.appelation.offlinepasswordmanager;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import dagger.ObjectGraph;
import nz.co.appelation.offlinepasswordmanager.model.PasswordContainer;

public class OfflinePasswordManagerInjectedApplication extends Application {

    private static final String TAG = OfflinePasswordManagerInjectedApplication.class.getSimpleName();

    public static final String CURRENT_APP_VERSION = "1";

    public static final long AUTO_LOGOUT_WORKER_SLEEP = 60 * 1000; //1 minute
    public static final long AUTO_LOGOUT_TIME = 15 * 60 * 1000; //15 minutes

    private ActivityChecker autoLogoutWorker;

    private ObjectGraph objectGraph;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "OfflinePasswordManager instance created");

        initObjectGraph(new AppModule(this));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("auto_logout", true)) {
            autoLogoutWorker = new ActivityChecker(new Command(){
                @Override
                public void execute(Object... input) {
                    try {
                        PasswordContainer passwordContainer = objectGraph.get(PasswordContainer.class);
                        if (passwordContainer != null) {
                            Log.d(TAG, "Calling passwordContainer.kill()");
                            passwordContainer.kill();
                        }
                    } catch (Throwable t){
                        //consume
                    }

                    try {
                        DBManager dbMan = objectGraph.get(DBManager.class);
                        if (dbMan != null) {
                            Log.d(TAG, "Calling dbMan.killAccountModels()");
                            dbMan.killAccountModels();
                        }
                    } catch (Throwable t){
                        //consume
                    }
                }
            }, new Command(){
                @Override
                public void execute(Object... input) {
                    Log.d(TAG, "Process.killProcess");
                    Process.killProcess(android.os.Process.myPid());
                }
            });

            Log.i(TAG, "Starting autoLogoutWorker");
            autoLogoutWorker.start();

            //Register activity in the app
            registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    autoLogoutWorker.markActivity();
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    autoLogoutWorker.markActivity();
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    autoLogoutWorker.markActivity();
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    autoLogoutWorker.markActivity();
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    autoLogoutWorker.markActivity();
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                    autoLogoutWorker.markActivity();
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            });

            autoLogoutWorker.markActivity();
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.i(TAG, "OfflinePasswordManager in onTerminate");

        if (autoLogoutWorker != null) {
            autoLogoutWorker.killThread();
            autoLogoutWorker.interrupt();
        } else {
            PasswordContainer passwordContainer = objectGraph.get(PasswordContainer.class);
            if (passwordContainer != null) {
                Log.d(TAG, "Calling passwordContainer.kill()");
                passwordContainer.kill();
            }
        }

        autoLogoutWorker = null;
    }

    /**
     * Initialize the Dagger module.
     *
     * @param module for Dagger
     */
    public void initObjectGraph(Object module) {
        objectGraph = module != null ? ObjectGraph.create(module) : null;
    }

    public void inject(Object object) {
        if (objectGraph == null) {
            Log.i(TAG, "Object graph is not initialized.");
            return;
        }

        objectGraph.inject(object);
    }

    private static class ActivityChecker extends Thread {
        private volatile boolean run = true;
        private volatile long lastActivity = 0;

        private Command doLogout;
        private Command doTerminate;

        public ActivityChecker(Command doLogout, Command doTerminate){
            this.doLogout = doLogout;
            this.doTerminate = doTerminate;
        }

        @Override
        public void run() {
            markActivity();

            while (run && (System.currentTimeMillis() - lastActivity) < AUTO_LOGOUT_TIME) {
                try {
                    Thread.sleep(AUTO_LOGOUT_WORKER_SLEEP);
                } catch (InterruptedException e) {
                    //consume
                    Log.d(TAG, "Interrupted");
                }
            }

            doLogout.execute();
            if (run) {
                /**
                 * If run is true, it means we're logging out due to inactivity, so terminate instance.
                 * If run is false, it means worker thread was interrupted due to application terminating, so do not try to terminate again.
                 */
                doTerminate.execute();
            }
        }

        public void markActivity(){
            //Log.d(TAG, "Mark activity");
            lastActivity = System.currentTimeMillis();
        }

        public void killThread(){
            Log.i(TAG, "Terminating ActivityChecker");
            run = false;
        }
    }

}
