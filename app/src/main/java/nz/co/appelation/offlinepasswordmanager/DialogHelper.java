package nz.co.appelation.offlinepasswordmanager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import javax.inject.Inject;

/**
 * Util class for dialogs.
 */
public class DialogHelper {

    @Inject
    Context ctx;

    @Inject
    Resources resources;

    /**
     * Wrapper method for makeDialog(), which takes a message resource.
     *
     * @param activityContext parent activity context
     * @param title resource for title string
     * @param message resource for message string
     * @param icon resource for icon, or null if no icon
     * @param positiveButton resource for positive button string, or null if no positive button
     * @param negativeButton resource for negative button string, or null if no negative button
     * @param positiveCommand command for positive button click, or null if no positive button action
     * @param negativeCommand command for negative button click, or null if no negative button action
     * @return reference to the dialog
     */
    public AlertDialog makeDialog(Context activityContext, int title, int message, Integer icon, Integer positiveButton, Integer negativeButton, final Command positiveCommand, final Command negativeCommand){
        return makeDialog(activityContext, title, resources.getText(message), icon, positiveButton, negativeButton, positiveCommand, negativeCommand);
    }

    /**
     * Util method to show an alert dialog.
     * Configurable icon, positive and negative buttons.
     *
     * Dialog auto dismissed on either negative or positive click.
     *
     * @param activityContext parent activity context
     * @param title resource for title string
     * @param message message string
     * @param icon resource for icon, or null if no icon
     * @param positiveButton resource for positive button string, or null if no positive button
     * @param negativeButton resource for negative button string, or null if no negative button
     * @param positiveCommand command for positive button click, or null if no positive button action
     * @param negativeCommand command for negative button click, or null if no negative button action
     * @return reference to the dialog
     */
    public AlertDialog makeDialog(Context activityContext, int title, CharSequence message, Integer icon, Integer positiveButton, Integer negativeButton, final Command positiveCommand, final Command negativeCommand){
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activityContext)
                .setTitle(title)
                .setMessage(message);

        if (icon != null) {
            dialogBuilder.setIcon(icon);
        }

        if (positiveButton != null){
            dialogBuilder.setPositiveButton(positiveButton, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    if (positiveCommand != null) {
                        positiveCommand.execute();
                    }

                    dialog.dismiss();
                }
            });
        }

        if (negativeButton != null){
            dialogBuilder.setNegativeButton(negativeButton, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    if (negativeCommand != null) {
                        negativeCommand.execute();
                    }

                    dialog.dismiss();
                }
            });
        }

        return dialogBuilder.show();
    }

    public AlertDialog makePasswordInputDialog(Context activityContext, int title, int message, Integer icon, Integer positiveButton, Integer negativeButton, final Command positiveCommand, final Command negativeCommand, final Command dismissCommand){
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activityContext)
                .setTitle(title)
                .setMessage(message);

        final EditText pswInput = new EditText(ctx);
        pswInput.setTextColor(Color.BLACK);
        pswInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        dialogBuilder.setView(pswInput);

        if (icon != null) {
            dialogBuilder.setIcon(icon);
        }

        if (positiveButton != null){
            dialogBuilder.setPositiveButton(positiveButton, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    if (positiveCommand != null) {
                        char[] password = new char[pswInput.length()];
                        pswInput.getText().getChars(0, pswInput.length(), password, 0);

                        positiveCommand.execute(password);
                    }

                    pswInput.getText().clear();

                    dialog.dismiss();
                }
            });
        }

        if (negativeButton != null){
            dialogBuilder.setNegativeButton(negativeButton, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    if (negativeCommand != null) {
                        negativeCommand.execute();
                    }

                    pswInput.getText().clear();

                    dialog.dismiss();
                }
            });
        }

        dialogBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (dismissCommand != null) {
                    dismissCommand.execute();
                }
            }
        });

        return dialogBuilder.show();
    }

    public void makeToast(int message) {
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
    }

    public void makeToast(CharSequence message) {
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
    }

}
