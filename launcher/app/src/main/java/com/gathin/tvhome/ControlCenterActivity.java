package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;

/**
 * Control Center over another app (this TV): a see-through screen opened by a remote button (app_keys target
 * "control_center"), so the panel appears on top of SmartTube or YouTube without going Home. It closes with the
 * panel, and the app underneath carries on.
 */
public class ControlCenterActivity extends Activity {
    private boolean shown;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shown) return;
        shown = true;
        ControlCenter.showOver(this, this::finish);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) finish();   // something else came in front: no panel left behind
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
