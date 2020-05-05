package app.rikka.savecopy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import app.rikka.savecopy.databinding.InfoActivityBinding;

public class InfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InfoActivityBinding binding = InfoActivityBinding.inflate(getLayoutInflater());
        binding.text1.setText(getString(R.string.introduction, getString(R.string.save_a_copy)));
        binding.settings.setOnClickListener(v -> startActivity(new Intent("android.intent.action.APPLICATION_PREFERENCES")
                .setComponent(ComponentName.createRelative(getPackageName(), SettingsActivity.class.getName()))));
        setContentView(binding.getRoot());
    }
}