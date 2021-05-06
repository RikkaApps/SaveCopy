package app.rikka.savecopy;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

public class SaveActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            finish();
            return;
        }

        getPackageManager().clearPackagePreferredActivities(getPackageName());

        checkConfirmation();
    }

    private void checkConfirmation() {
        if (shouldShowConfirmation()) {
            new AlertDialog.Builder(this, R.style.AppTheme_Dialog_Alert)
                    .setMessage(R.string.dialog_confirmation_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> checkPermission())
                    .setOnDismissListener(dialog -> finish())
                    .show();
            return;
        }
        checkPermission();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || (Build.VERSION.SDK_INT == 29 && Build.VERSION.PREVIEW_SDK_INT == 0))) {
            String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            if (checkSelfPermission(permissions[0]) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(permissions[1]) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, PERMISSION_REQUEST_CODE);
                return;
            }
        }

        startSave();
    }

    private void startSave() {
        String callingPackage = null;
        Uri referrer = getReferrer();
        if (referrer != null) callingPackage = referrer.getAuthority();

        Intent intent = new Intent(getIntent());
        intent.setClassName(this, SaveService.class.getName());
        intent.putExtra(SaveService.CALLING_PACKAGE, callingPackage);
        startService(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length == 0) {
                return;
            }

            boolean granted = true;
            for (int grantResult : grantResults) {
                granted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }
            if (granted) {
                startSave();
            } else {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_YES) > 0;
                int theme = isNight ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
                new AlertDialog.Builder(this, theme)
                        .setTitle(R.string.dialog_no_permission_title)
                        .setMessage(R.string.dialog_no_permission_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.dialog_no_permission_button_app_info, (dialog, which) -> {
                            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i.addCategory(Intent.CATEGORY_DEFAULT);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        })
                        .setOnDismissListener((dialog -> finish()))
                        .show();
            }
        }
    }

    private boolean shouldShowConfirmation() {
        try {
            Intent intentForTest = new Intent(getIntent());
            intentForTest.setComponent(null);
            intentForTest.setPackage(null);
            return getPackageManager().queryIntentActivities(intentForTest, PackageManager.MATCH_DEFAULT_ONLY).size() <= 1;
        } catch (Throwable e) {
            return true;
        }
    }
}
