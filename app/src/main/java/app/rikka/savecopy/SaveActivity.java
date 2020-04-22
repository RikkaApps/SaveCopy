package app.rikka.savecopy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class SaveActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Intent intent = new Intent(getIntent());
            intent.setClassName(this, SaveService.class.getName());
            startService(intent);
        }
        finish();
    }
}