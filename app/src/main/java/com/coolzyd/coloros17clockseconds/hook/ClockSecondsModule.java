package com.coolzyd.coloros17clockseconds.hook;

import android.content.ContentResolver;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public final class ClockSecondsModule extends XposedModule {
    private static final String TAG = "ColorOSClockSeconds";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String CLOCK_SECONDS_PREFERENCE_KEY =
            "oplus_status_bar_clock_seconds_mode";
    private static final String CONTROLLER_CLASS =
            "com.oplus.systemui.statusbar.clock.ClockSecondsController";
    private static final String SETTINGS_CONTROLLER_CLASS =
            "com.oplus.settings.feature.notification.controller.ClockSecondsModePreferenceController";
    private static final String MODE_SETTING = "oplus_status_bar_clock_seconds_mode";
    private static final String HOOK_DEADLINE_CHECK_ID = "clock-seconds-deadline-check";
    private static final String HOOK_EXPIRY_ID = "clock-seconds-expiry";
    private static final String HOOK_SETTINGS_STATE_ID = "clock-seconds-settings-state";
    private static final String HOOK_SETTINGS_HINT_ID = "clock-seconds-settings-hint";
    private static final String HOOK_SETTINGS_ASSIGNMENT_ID = "clock-seconds-settings-assignment";
    private static final String HOOK_SETTINGS_OPTION_TITLE_ID = "clock-seconds-settings-option-title";
    private static final int MODE_ENABLED = 1;
    private static final int SETTINGS_ENABLED_INDEX = 1;
    private static final String FIVE_MINUTES_ITEM = "CLOCK_SECONDS_FIVE_MINUTES";
    private static final Pattern FIVE_MINUTES_PATTERN = Pattern.compile(
            "5\\s*(?:分钟|分|minutes?|mins?\\.?)", Pattern.CASE_INSENSITIVE);
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
            Class<?> couiPreferenceClass =
                    Class.forName("com.coui.appcompat.preference.COUIPreference", false, classLoader);
            Method setAssignment =
                    couiPreferenceClass.getDeclaredMethod("setAssignment", CharSequence.class);
            Method getPreferenceKey = couiPreferenceClass.getMethod("getKey");
            Field contentResolver = settingsControllerClass.getDeclaredField("mContentResolver");
            Field preferenceField = settingsControllerClass.getDeclaredField("mPreference");
            contentResolver.setAccessible(true);
            preferenceField.setAccessible(true);

            Class<?> choiceAdapterClass = Class.forName(
                    "com.oplus.settings.feature.notification.controller.StatusIconBottomSheetChoiceListAdapter",
                    false,
                    classLoader);
            Class<?> choiceViewHolderClass = Class.forName(
                    "com.oplus.settings.feature.notification.controller.StatusIconBottomSheetChoiceListAdapter$ViewHolder",
                    false,
                    classLoader);
            Class<?> statusIconClass = Class.forName(
                    "com.oplus.settings.feature.notification.controller.StatusIconBottomSheetDialog$StatusIcon",
                    false,
                    classLoader);
            Class<?> statusIconDialogItemClass = Class.forName(
                    "com.oplus.settings.feature.notification.StatusIconDialogItem",
                    false,
                    classLoader);
            Field boundStatusIcon = choiceViewHolderClass.getDeclaredField("mStatusIcon");
            Field boundDialogItem = statusIconClass.getDeclaredField("mStatusIconDialogItem");
            Field optionTitle = choiceViewHolderClass.getDeclaredField("itemText");
            Field fiveMinutesItem = statusIconDialogItemClass.getDeclaredField(FIVE_MINUTES_ITEM);
            boundStatusIcon.setAccessible(true);
            boundDialogItem.setAccessible(true);
            optionTitle.setAccessible(true);
            fiveMinutesItem.setAccessible(true);
            Object fiveMinutes = fiveMinutesItem.get(null);
            Method bindChoice = choiceAdapterClass.getDeclaredMethod(
                    "onBindViewHolder", choiceViewHolderClass, int.class);

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
                        Object menuPreference = preferenceField.get(chain.getThisObject());
                        if (menuPreference != null) {
                            menuPreference.getClass()
                                    .getMethod("setSummary", CharSequence.class)
                                    .invoke(menuPreference, SETTINGS_HINT);
                        }
                        return result;
                    });

            hook(setAssignment)
                    .setId(HOOK_SETTINGS_ASSIGNMENT_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object preference = chain.getThisObject();
                        if (!CLOCK_SECONDS_PREFERENCE_KEY.equals(getPreferenceKey.invoke(preference))) {
                            return chain.proceed();
                        }

                        Object assignment = chain.getArg(0);
                        if (!(assignment instanceof CharSequence)) {
                            return chain.proceed();
                        }
                        CharSequence formatted = formatPermanentDuration((CharSequence) assignment);
                        return formatted == assignment
                                ? chain.proceed()
                                : chain.proceed(new Object[]{formatted});
                    });

            hook(bindChoice)
                    .setId(HOOK_SETTINGS_OPTION_TITLE_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object viewHolder = chain.getArg(0);
                        Object statusIcon = boundStatusIcon.get(viewHolder);
                        if (statusIcon != null && boundDialogItem.get(statusIcon) == fiveMinutes) {
                            TextView title = (TextView) optionTitle.get(viewHolder);
                            title.setText(formatPermanentDuration(title.getText()));
                        }
                        return result;
                    });

            log(Log.INFO, TAG, "Installed ColorOS clock-seconds Settings hook");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Failed to install clock-seconds Settings hook", error);
        }
    }

    private static CharSequence formatPermanentDuration(CharSequence assignment) {
        if (TextUtils.isEmpty(assignment)) {
            return assignment;
        }

        String text = assignment.toString();
        Matcher matcher = FIVE_MINUTES_PATTERN.matcher(text);
        if (!matcher.find()) {
            return assignment;
        }

        int start = matcher.start();
        int end = matcher.end();
        String formattedText = text.substring(0, end) + " 永久" + text.substring(end);
        SpannableString formatted = new SpannableString(formattedText);
        formatted.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return formatted;
    }
}
