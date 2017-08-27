package nz.co.appelation.offlinepasswordmanager;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import javax.crypto.Cipher;
import javax.inject.Inject;

import nz.co.appelation.offlinepasswordmanager.model.PasswordContainer;

public class AuthenticationActivity extends Activity {

    private static final String TAG = AuthenticationActivity.class.getSimpleName();

    private static final int FINGERPRINT_PERMISSION_REQUEST_CODE_FOR_ENCRYPT = 0;

    private static final int FINGERPRINT_PERMISSION_REQUEST_CODE_FOR_DECRYPT = 1;

    private static final String PREF_USE_FINGERPRINT = "use_fingeprint";

    private static final String PREF_SHOW_HELP = "show_help";

    @Inject
    FingerprintManager fingerprintManager;

    @Inject
    KeyguardManager keyguardManager;

    @Inject
    PasswordContainer passwordContainer;

    @Inject
    SharedPreferences prefs;

    @Inject
    DBManager dbman;

    @Inject
    CryptoUtil cryptoUtil;

    private CancellationSignal cancellationSignal;

    private int defaultTextColor = Color.BLACK;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((OfflinePasswordManagerInjectedApplication) getApplication()).inject(this);

        //Prevent screenshots or task manager thumbnails
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.authentication_layout);

        backupDefaultTextColor();
    }

    @Override
    protected void onResume() {
        super.onResume();

        stopFingerprintListener();
        clearMasterPasswordField();
        initHelpView();

        //Do we have an existing DB?
        if (dbman.doesCurrentVersionDBExist()) {
            //Does user prefer to use fingerprint?
            if (prefs.getBoolean(PREF_USE_FINGERPRINT, true)){
                //Do we have an encrypted password?
                if (cryptoUtil.hasPasswordFile()) {
                    doFingerprintAuthCheck(Mode.DECRYPT);
                } else {
                    //Revert back to password entry
                    setMessage(R.string.msg_enter_master_password);
                    showMasterPasswordInput(new Command(){
                        @Override
                        public void execute(Object... input){
                            passwordContainer.setAndHashMasterPassword(getMasterPasswordAsChars());
                            clearMasterPasswordField();

                            /**
                             * User is reverting back to using fingerprint auth, after having established an encrypted DB already.
                             * First load DB to make sure entered password is correct.
                             * If correct, then do fingerprint auth again, and save encrypted password.
                             */

                            try {
                                dbman.loadDB();
                            } catch (WrongPasswordException wpe){
                                setError(R.string.err_incorrect_password);
                                setFocusOnMasterPassword();

                                return;
                            }

                            doFingerprintAuthCheck(Mode.ENCRYPT);
                        }
                    });
                }
            } else {
                //Access DB with master password (no fingerprint)
                setMessage(null);
                showMasterPasswordInput(new Command(){
                    @Override
                    public void execute(Object... input){
                        passwordContainer.setAndHashMasterPassword(getMasterPasswordAsChars());
                        clearMasterPasswordField();

                        try {
                            dbman.loadDB();
                            goToPasswordManagerActivity();
                        } catch (WrongPasswordException wpe){
                            setError(R.string.err_incorrect_password);
                            setFocusOnMasterPassword();
                        }
                    }
                });
            }
        } else if (dbman.doesPreviousVersionDBExist()){
            //Convert prior version of DB file to current?
            //At app v1, there are no prior versions possible...
            //If we have to version the DB file in the future - handle conversion prompt here.
        } else {
            //Prompt to create new
            setMessage(R.string.msg_create_new);
            showMasterPasswordInput(new Command(){
                @Override
                public void execute(Object... input){
                    passwordContainer.setAndHashMasterPassword(getMasterPasswordAsChars());
                    clearMasterPasswordField();

                    doFingerprintAuthCheck(Mode.ENCRYPT);
                }
            });
        }
    }

    /**
     * Avoid holding onto the master password in String form:
     * https://www.ibm.com/support/knowledgecenter/SSYKE2_7.1.0/com.ibm.java.security.component.71.doc/security-component/JceDocs/api_ex_pbe.html
     * http://stackoverflow.com/questions/8881291/why-is-char-preferred-over-string-for-passwords-in-java
     *
     * @return the master password char array
     */
    private char[] getMasterPasswordAsChars(){
        EditText masterPassword = (EditText) findViewById(R.id.masterPassword);
        char[] masterPasswordChars = new char[masterPassword.length()];
        masterPassword.getText().getChars(0, masterPassword.length(), masterPasswordChars, 0);

        return masterPasswordChars;
    }

    private void initHelpView(){
        if (prefs.getBoolean(PREF_SHOW_HELP, true)) {
            hideKeyboard();

            findViewById(R.id.helpTextScollView).setVisibility(View.VISIBLE);

            ((TextView) findViewById(R.id.helpText)).setMovementMethod(LinkMovementMethod.getInstance());

            findViewById(R.id.dontShowAgain).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putBoolean(PREF_SHOW_HELP, false).apply();
                    findViewById(R.id.helpTextScollView).setVisibility(View.GONE);
                }
            });
        } else {
            findViewById(R.id.helpTextScollView).setVisibility(View.GONE);
        }
    }

    private void goToPasswordManagerActivity(){
        startActivity(new Intent(this, PasswordManagerActivity.class));
    }

    private void doFingerprintAuthCheck(Mode fingerprintAuthMode){
        setMessage(null);

        showFingerprintInput();

        showSkipFingerprintLink(fingerprintAuthMode);

        if (checkSelfPermission(Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            int requestCode = (Mode.ENCRYPT == fingerprintAuthMode ? FINGERPRINT_PERMISSION_REQUEST_CODE_FOR_ENCRYPT : FINGERPRINT_PERMISSION_REQUEST_CODE_FOR_DECRYPT);
            requestPermissions(new String[]{Manifest.permission.USE_FINGERPRINT}, requestCode);
        } else {
            doFingerprintAuth(fingerprintAuthMode);
        }
    }

    private void doFingerprintAuth(Mode fingerprintAuthMode) throws SecurityException {
        if (!keyguardManager.isKeyguardSecure()) {
            setError(R.string.err_lock_scr_security);
        } else if (!fingerprintManager.isHardwareDetected()) {
            setError(R.string.err_no_hardware);
        } else if (!fingerprintManager.hasEnrolledFingerprints()) {
            setError(R.string.err_no_fingerprints);
        } else {
            //Create cipher for encryption
            Cipher cipher = null;
            try {
                cipher = createCipher(fingerprintAuthMode);
            } catch (KeyLostException e) {
                /**
                 * This is a recoverable exception-
                 * The key was lost due to fingerprint or lockscreen settings changed on the device.
                 * We have to re-encrypt the password.
                 */

                Log.i(TAG, "Got KeyLostException");

                setError(R.string.err_key_lost);
                setFocusOnMasterPassword();

                //Delete password file, key, and IV - force use to re-enter master password on next try.
                cryptoUtil.deleteKeyPasswordFileAndIV();

                passwordContainer.kill();

                /**
                 * Do not throw exception - this is recoverable.
                 */
            } catch (Throwable t) {
                /**
                 *All other unrecoverable errors...
                 */

                /**
                 * Note:
                 * There is a bug in the Android emulator: If you enroll a fingerprint, and then reboot the AVD, next time you try and do fingerprint auth,
                 * fingerprintManager.hasEnrolledFingerprints() will be true, however when you try and init the KeyGenerator with
                 * .setUserAuthenticationRequired(true), you get this exception:
                 * "java.lang.RuntimeException: java.security.InvalidAlgorithmParameterException: java.lang.IllegalStateException: At least one fingerprint
                 * must be enrolled to create keys requiring user authentication for every use"
                 * @See CryptoUtil.createCipher()
                 *
                 * Ref: https://github.com/googlesamples/android-FingerprintDialog/issues/18
                 *
                 * That exception will come through to this catch.
                 *
                 * To avoid, after booting AVD, delete all enrolled finterprints in Android Security settings, and re-enroll, then test app.
                 */

                Log.e(TAG, "Fatal error while creating cipher in " + fingerprintAuthMode + " mode.", t);

                //Delete password file, key, and IV - force use to re-enter master password on next try.
                cryptoUtil.deleteKeyPasswordFileAndIV();

                //Turn off fingerprint auth
                prefs.edit().putBoolean(PREF_USE_FINGERPRINT, false).commit();

                passwordContainer.kill();

                throw new RuntimeException("Fatal error while creating cipher in " + fingerprintAuthMode + " mode.", t);
            }

            //Get fingerprint crypto object, wrapping the cypher
            FingerprintManager.CryptoObject fingerprintCryptoObject = new FingerprintManager.CryptoObject(cipher);

            //Listen for fingerprint auth
            setMessage(R.string.msg_auth_with_with_fingerprint);
            startFingerprintListen(fingerprintCryptoObject, fingerprintAuthMode);
        }
    }

    private void showSkipFingerprintLink(final Mode fingerprintAuthMode){
        final TextView skipLink = (TextView) findViewById(R.id.skipFingerprint);
        skipLink.setVisibility(View.VISIBLE);
        skipLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /**
                 * Handle "disable fingerprint auth" action.
                 */

                skipLink.setVisibility(View.GONE);

                stopFingerprintListener();

                prefs.edit().putBoolean(PREF_USE_FINGERPRINT, false).apply();

                setIcon(Icon.LOCK);

                //Delete old psw & key & iv
                cryptoUtil.deleteKeyPasswordFileAndIV();

                if (fingerprintAuthMode == Mode.ENCRYPT){
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setMessage(null);
                        }
                    }, 100);

                    /**
                     * In encrypt mode, we need to encrypt the master password with the fingerprint key, however, here the user selected NOT to use fingerprint auth,
                     * so there is no master password file saved or requires encryption.
                     * This could happen on first use, or if user already has a passwordDB, disabled fingerprint auth, and then re-enabled it, only to cancel (skip) during login, and end up here.
                     * So, we may or may not have a current DB at this point.
                     * User will enter master password on each use, so we just launch the PasswordManagerActivity, after initing new DB.
                     * Any old encrypted password file, salt and IV was deleted above.
                     */

                    //If we already have a DB, then do not init DB again.
                    if (dbman.doesCurrentVersionDBExist()){
                        //Do not init DB!
                    } else {
                        // Init new DB
                        dbman.initEmptyDB();
                    }

                    /**
                     * passwordContainer already contains the newly hashed master password at this point.
                     */
                    clearMasterPasswordField();

                    //Go to PasswordManagerActivity
                    goToPasswordManagerActivity();
                } else if (fingerprintAuthMode == Mode.DECRYPT){
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setMessage(R.string.msg_master_password_auth);
                        }
                    }, 100);

                    /**
                     * In decrypt mode, we need to decrypt the master password with the fingerprint key, however, here the user selected NOT to use fingerprint auth.
                     * User will enter master password on each use.
                     * Old encrypted password file, salt and IV was deleted above.
                     * So we set and hash the entered master password into the passwordContainer, load the DB, and launch the PasswordManagerActivity.
                     * DB loading will fail with WrongPasswordException if master password was incorrectly entered.
                     */

                    //Access DB with master password (no fingerprint)
                    showMasterPasswordInput(new Command(){
                        @Override
                        public void execute(Object... input){
                            passwordContainer.setAndHashMasterPassword(getMasterPasswordAsChars());

                            clearMasterPasswordField();

                            try {
                                //Load DB (will fail if entered password was incorrect)
                                dbman.loadDB();

                                //Go to PasswordManagerActivity
                                goToPasswordManagerActivity();
                            } catch (WrongPasswordException wpe) {
                                Log.e(TAG, "Incorrect master password entered");
                                setError(R.string.err_incorrect_password);
                                setFocusOnMasterPassword();
                            }
                        }
                    });
                }
            }
        });
    }

    private void startFingerprintListen(FingerprintManager.CryptoObject fingerprintCryptoObject, final Mode mode) throws SecurityException {
        cancellationSignal = new CancellationSignal();

        fingerprintManager.authenticate(fingerprintCryptoObject, cancellationSignal, 0, new FingerprintManager.AuthenticationCallback(){

            /**
             * Called when an unrecoverable error has been encountered and the operation is complete.
             * No further callbacks will be made on this object.
             * @param errorCode An integer identifying the error message
             * @param errString A human-readable error string that can be shown in UI
             */
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                Log.d(TAG, "Auth fingerprint auth error. Code: " + errorCode);

                if (errorCode == 7){
                    //Too many attempts
                    setError(getResources().getString(R.string.err_finterprint_auth_failed) + ": " + errString.toString() + getResources().getString(R.string.err_finterprint_auth_failed_retry_wait));
                } else {
                    setError(getResources().getString(R.string.err_finterprint_auth_failed) + ": " + errString.toString() + getResources().getString(R.string.err_finterprint_auth_failed_retry));
                }

                TextView messageTextView = ((TextView)findViewById(R.id.message));
                if (messageTextView != null){
                    messageTextView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = getIntent();
                            beforeFinish();
                            finish();
                            startActivity(intent);
                        }
                    });
                }
            }

            /**
             * Called when a recoverable error has been encountered during authentication. The help
             * string is provided to give the user guidance for what went wrong, such as
             * "Sensor dirty, please clean it."
             * @param helpCode An integer identifying the error message
             * @param helpString A human-readable string that can be shown in UI
             */
            public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                setError(helpString.toString());
            }

            /**
             * Called when a fingerprint is recognized.
             * @param result An object containing authentication-related data
             */
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                setMessage(R.string.msg_auth_passed);

                final FingerprintManager.CryptoObject resultFingerprintCryptoObject = result.getCryptoObject();

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mode == Mode.DECRYPT) {
                            /**
                             * In decrypt mode, we need to decrypt the master password with the fingerprint key.
                             * So load and decrypt it, and set into the passwordContainer, load the DB, and launch the PasswordManagerActivity.
                             * DB loading will fail with WrongPasswordException if supplied master password hash is incorrect - however since we loaded it
                             * from the encrypted master password file - this should not happen...
                             * (WrongPasswordException is more applicable to the usecase where fingerprint auth is not used, and user enters master password each time)
                             */

                            cryptoUtil.loadAndDecryptMasterPasswordHash(resultFingerprintCryptoObject.getCipher());

                            try {
                                //Load DB
                                dbman.loadDB();

                                //Go to PasswordManagerActivity
                                goToPasswordManagerActivity();
                            } catch (WrongPasswordException wpe){
                                //Unrecoverable error - this is not possible
                                Log.e(TAG, "Incorrect master password loaded from file (decrypt mode)");
                                throw new RuntimeException("Incorrect master password loaded from file (decrypt mode)");
                            }
                        } else if (mode == Mode.ENCRYPT){
                            /**
                             * In encrypt mode, we need to encrypt the master password with the fingerprint key.
                             *
                             * Before we do that, we check if we already have a DB. If so - then we've already loaded and successfully decrypted it in the onResume() method.
                             * If we don't have a DB yet, then create one.
                             *
                             * Then encrypt and save password.
                             */

                            if (!dbman.doesCurrentVersionDBExist()){
                                dbman.initEmptyDB();
                            }

                            //Encrypt and save password
                            cryptoUtil.encryptAndSaveMasterPasswordHash(resultFingerprintCryptoObject.getCipher());

                            //Go to PasswordManagerActivity
                            goToPasswordManagerActivity();
                        }
                    }
                }, 750);
            }

            /**
             * Called when a fingerprint is valid but not recognized.
             */
            public void onAuthenticationFailed() {
                setError(R.string.err_fingerprint_auth_failed);
            }
        }, null);
    }

    private void stopFingerprintListener(){
        if (cancellationSignal != null){
            cancellationSignal.cancel();
            cancellationSignal = null;
        }
    }

    private void showFingerprintInput(){
        setIcon(Icon.FINGERPRINT);

        hideKeyboard();

        findViewById(R.id.masterPasswordSet).setVisibility(View.GONE);
        clearMasterPasswordField();
    }

    private void showMasterPasswordInput(final Command command){
        setIcon(Icon.LOCK);

        findViewById(R.id.masterPasswordSet).setVisibility(View.VISIBLE);
        clearMasterPasswordField();
        findViewById(R.id.masterPassword).requestFocus();

        if (!prefs.getBoolean(PREF_SHOW_HELP, true)) {
            showKeyboard();
        }

        registerKeyListenerForMasterPasswordField(command);

        findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndActionMasterPasswordEntry(command);
            }
        });
    }

    private void validateAndActionMasterPasswordEntry(final Command command){
        if (((EditText) findViewById(R.id.masterPassword)).getText().length() < CryptoUtil.MIN_PASSWORD_LENGTH){
            setError(R.string.msg_8_chars_psw);
            setFocusOnMasterPassword();
        } else {
            command.execute();
        }
    }

    private Cipher createCipher(Mode mode) throws KeyLostException {
        if (mode == Mode.ENCRYPT){
            return cryptoUtil.createCipherForMasterPasswordEncryption();
        } else if (mode == Mode.DECRYPT) {
            return cryptoUtil.createCipherForMasterPasswordDecryption();
        }

        throw new IllegalArgumentException("Unknown Mode");
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case FINGERPRINT_PERMISSION_REQUEST_CODE_FOR_DECRYPT: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    doFingerprintAuth(Mode.DECRYPT);
                } else {
                    // Permission Denied
                    setError(R.string.err_no_permission_fingerprint);
                }
                break;
            }
            case FINGERPRINT_PERMISSION_REQUEST_CODE_FOR_ENCRYPT: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    doFingerprintAuth(Mode.ENCRYPT);
                } else {
                    // Permission Denied
                    setError(R.string.err_no_permission_fingerprint);
                }
                break;
            }
            default: {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    /**
     * Setting focus straight back to the EditText fails sometimes if you are doing some other layout changes (showing/hiding other focusable views)/
     * Set a small delay on this to ensure focus gets set correctly.
     */
    private void setFocusOnMasterPassword(){
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.masterPassword).requestFocus();
            }
        }, 250);
    }

    private void setIcon(Icon icon){
        if (Icon.FINGERPRINT == icon){
            findViewById(R.id.fingerprintIcon).setVisibility(View.VISIBLE);
            findViewById(R.id.lockIcon).setVisibility(View.GONE);
        } else if (Icon.LOCK == icon){
            findViewById(R.id.fingerprintIcon).setVisibility(View.GONE);
            findViewById(R.id.lockIcon).setVisibility(View.VISIBLE);
        }
    }

    private void clearMasterPasswordField(){
        if (findViewById(R.id.masterPassword) != null){
            ((EditText) findViewById(R.id.masterPassword)).getText().clear();
        }
    }

    private void registerKeyListenerForMasterPasswordField(final Command command){
        if (findViewById(R.id.masterPassword) != null){
            findViewById(R.id.masterPassword).setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                        validateAndActionMasterPasswordEntry(command);
                        return true;
                    }

                    return false;
                }
            });

            ((EditText) findViewById(R.id.masterPassword)).setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        validateAndActionMasterPasswordEntry(command);
                        return true;
                    }

                    return false;
                }
            });
        }
    }

    private void deregisterKeyListenerForMasterPasswordField(){
        View masterPassword = findViewById(R.id.masterPassword);
        if (masterPassword != null){
            masterPassword.setOnKeyListener(null);
        }
    }

    private void backupDefaultTextColor(){
        TextView messageTextView = ((TextView)findViewById(R.id.message));
        defaultTextColor = messageTextView.getCurrentTextColor();
    }

    private void setMessage(Integer messageId){
        TextView messageTextView = ((TextView)findViewById(R.id.message));
        messageTextView.setTextColor(defaultTextColor);
        messageTextView.setOnClickListener(null);
        if (messageId == null) {
            messageTextView.setText("");
        } else {
            messageTextView.setText(messageId);
        }

        messageTextView.setOnClickListener(null);
    }

    private void setError(Integer messageId){
        if (messageId == null) {
            setError("");
        } else {
            setError(getResources().getString(messageId));
        }
    }

    private void setError(String message){
        TextView messageTextView = ((TextView)findViewById(R.id.message));
        messageTextView.setTextColor(Color.RED);
        messageTextView.setOnClickListener(null);
        if (message == null) {
            messageTextView.setText("");
        } else {
            messageTextView.setText(message);
        }

        messageTextView.setOnClickListener(null);
    }

    private void hideKeyboard(){
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(findViewById(R.id.masterPassword).getWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);
                } catch (Throwable t){
                    //consume
                }
            }
        }, 200);
    }

    private void showKeyboard(){
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(findViewById(R.id.masterPassword), InputMethodManager.SHOW_IMPLICIT);
                } catch (Throwable t){
                    //consume
                }
            }
        }, 200);
    }

    private void beforeFinish(){
        stopFingerprintListener();
        deregisterKeyListenerForMasterPasswordField();
    }

    @Override
    protected void onStop() {
        super.onStop();

        beforeFinish();
    }

    @Override
    protected void onPause() {
        super.onPause();

        beforeFinish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        beforeFinish();
    }

    private enum Mode {
        ENCRYPT, DECRYPT;
    }

    private enum Icon {
        FINGERPRINT, LOCK;
    }

}
