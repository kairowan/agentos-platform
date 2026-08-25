package com.agentos.shell;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setText(R.string.boot_message);
        status.setTextSize(22);
        setContentView(status);
    }
}

