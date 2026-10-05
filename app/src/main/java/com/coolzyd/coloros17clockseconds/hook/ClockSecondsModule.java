package com.coolzyd.coloros17clockseconds.hook;

import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public final class ClockSecondsModule extends XposedModule {
    private static final String TAG = "ColorOSClockSeconds";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String CONTROLLER_CLASS =
            "com.oplus.systemui.statusbar.clock.ClockSecondsController";
    private static final String SETTINGS_CONTROLLER_CLASS =
            "com.oplus.settings.feature.notification.controller.ClockSecondsModePreferenceController";
    private static final String MODE_SETTING = "oplus_status_bar_clock_seconds_mode";
    private static final String HOOK_DEADLINE_CHECK_ID = "clock-seconds-deadline-check";
    private static final String HOOK_EXPIRY_ID = "clock-seconds-expiry";
    private static final String HOOK_SETTINGS_STATE_ID = "clock-seconds-settings-state";
    private static final String HOOK_SETTINGS_HINT_ID = "clock-seconds-settings-hint";
    private static final int MODE_ENABLED = 1;
    private static final int SETTINGS_ENABLED_INDEX = 1;
    private static final String SETTINGS_HINT =
            "模块运行中：开启显秒后不会在 5 分钟后自动关闭；仍可通过本设置或长按时钟关闭。";

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (SYSTEM_UI_PACKAGE.equals(packageName)) {
            installSystemUiHooks(param.getDefaultClassLoader());
        } else if (SETTINGS_PACKAGE.equals(packageName)) {
            installSettingsStateHook(param.getDefaultClassLoader());
        }
    }

    private void installSystemUiHooks(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = Class.forName(CONTROLLER_CLASS, false, classLoader);

            Method isDeadlineActive =
                    controllerClass.getDeclaredMethod("isOplusFiveMinutesActive", long.class);
            Method scheduleExpiry =
                    controllerClass.getDeclaredMethod("scheduleExpiryLocked", int.class, long.class);
            Field lastAppliedMode = controllerClass.getField("lastAppliedMode");

            hook(isDeadlineActive)
                    .setId(HOOK_DEADLINE_CHECK_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    // ColorOS consults this helper only while the persisted mode is enabled.
                    .intercept(chain -> true);

            hook(scheduleExpiry)
                    .setId(HOOK_EXPIRY_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        // lastAppliedMode is updated before the controller schedules expiration.
                        if (lastAppliedMode.getInt(chain.getThisObject()) == MODE_ENABLED) {
                            return null;
                        }
                        return chain.proceed();
                    });

            log(Log.INFO, TAG, "Installed ColorOS 17 clock-seconds hooks");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Failed to install clock-seconds hooks", error);
        }
    }

    private void installSettingsStateHook(ClassLoader classLoader) {
        try {
            Class<?> settingsControllerClass =
                    Class.forName(SETTINGS_CONTROLLER_CLASS, false, classLoader);
            Method getCheckedItem = settingsControllerClass.getDeclaredMethod("getCheckedItem");
            Class<?> preferenceScreenClass =
                    Class.forName("androidx.preference.PreferenceScreen", false, classLoader);
            Method displayPreference = settingsControllerClass.getDeclaredMethod(
                    "displayPreference", preferenceScreenClass);
            Field contentResolver = settingsControllerClass.getDeclaredField("mContentResolver");
            Field preference = settingsControllerClass.getDeclaredField("mPreference");
            contentResolver.setAccessible(true);
            preference.setAccessible(true);

            hook(getCheckedItem)
                    .setId(HOOK_SETTINGS_STATE_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ContentResolver resolver =
                                (ContentResolver) contentResolver.get(chain.getThisObject());
                        int mode = Settings.Secure.getInt(resolver, MODE_SETTING, 0);
                        return mode == MODE_ENABLED
                                ? SETTINGS_ENABLED_INDEX
                                : chain.proceed();
                    });

            hook(displayPreference)
                    .setId(HOOK_SETTINGS_HINT_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object menuPreference = preference.get(chain.getThisObject());
                        if (menuPreference != null) {
                            menuPreference.getClass()
                                    .getMethod("setSummary", CharSequence.class)
                                    .invoke(menuPreference, SETTINGS_HINT);
                        }
                        return result;
                    });

            log(Log.INFO, TAG, "Installed ColorOS clock-seconds Settings hook");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Failed to install clock-seconds Settings hook", error);
        }
    }
}
