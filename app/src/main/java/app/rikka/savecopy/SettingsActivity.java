package app.rikka.savecopy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;

import app.rikka.savecopy.databinding.SettingsActivityBinding;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsActivityBinding binding = SettingsActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SharedPreferences sharedPreferences = getSharedPreferences(Settings.FILE_NAME, MODE_PRIVATE);

        binding.preferAppFolder.setChecked(sharedPreferences.getBoolean(Settings.KEY_PREFER_APP_FOLDER, false));
        binding.preferAppFolder.setOnCheckedChangeListener((buttonView, isChecked) -> sharedPreferences.edit().putBoolean(Settings.KEY_PREFER_APP_FOLDER, isChecked).apply());
    }
}