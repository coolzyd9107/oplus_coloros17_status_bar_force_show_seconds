package io.github.coolzyd9107.coloros17clockseconds;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public final class ColorOSModuleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
