package io.github.coolzyd9107.coloros17clockseconds.hook;

import android.content.Context;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public final class ClockSecondsModule extends XposedModule {
    private static final String TAG = "ColorOSClockSeconds";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String LAUNCHER_PACKAGE = "com.android.launcher";
    private static final String CLOCK_SECONDS_PREFERENCE_KEY =
            "oplus_status_bar_clock_seconds_mode";
    private static final String CONTROLLER_CLASS =
            "com.oplus.systemui.statusbar.clock.ClockSecondsController";
    private static final String SETTINGS_CONTROLLER_CLASS =
            "com.oplus.settings.feature.notification.controller.ClockSecondsModePreferenceController";
    private static final String MODE_SETTING = "oplus_status_bar_clock_seconds_mode";
    private static final String MEMORY_INFO_SETTING =
            "display_memory_information_recent_task";
    private static final String MEMORY_INFO_COMPAT_SETTING = "allow_memory_info_display";
    private static final String LAUNCHER_HOOK_TIMESTAMP_SETTING =
            "coloros_clockseconds_launcher_hook_timestamp";
    private static final String MEMORY_INFO_CATEGORY_KEY =
            "category_display_information_recent_task";
    private static final String NETWORK_SPEED_INTERVAL_SETTING =
            "coloros_status_bar_network_speed_refresh_interval_ms";
    private static final int DEFAULT_NETWORK_SPEED_INTERVAL_MS = 4000;
    private static final String HOOK_DEADLINE_CHECK_ID = "clock-seconds-deadline-check";
    private static final String HOOK_EXPIRY_ID = "clock-seconds-expiry";
    private static final String HOOK_MODE_CHANGE_DEADLINE_ID = "clock-seconds-mode-change-deadline";
    private static final String HOOK_NETWORK_SPEED_INTERVAL_ID = "network-speed-refresh-interval";
    private static final String HOOK_LAUNCHER_MEMORY_INFO_ID = "launcher-memory-info-availability";
    private static final String HOOK_LAUNCHER_MEMORY_INFO_UI_ID = "launcher-memory-info-ui";
    private static final String HOOK_LAUNCHER_MEMORY_INFO_SWITCH_ID = "launcher-memory-info-switch";
    private static final String HOOK_LAUNCHER_MEMORY_INFO_CATEGORY_ID = "launcher-memory-info-category";
    private static final String HOOK_LAUNCHER_MEMORY_INFO_VISIBILITY_ID = "launcher-memory-info-visibility";
    private static final String HOOK_SETTINGS_STATE_ID = "clock-seconds-settings-state";
    private static final String HOOK_SETTINGS_HINT_ID = "clock-seconds-settings-hint";
    private static final String HOOK_SETTINGS_ASSIGNMENT_ID = "clock-seconds-settings-assignment";
    private static final String HOOK_SETTINGS_POPUP_LIST_ID = "clock-seconds-settings-popup-list";
    private static final String HOOK_SETTINGS_DEFAULT_MENU_TITLE_ID =
            "clock-seconds-settings-default-menu-title";
    private static final String HOOK_SETTINGS_MIXED_MENU_TITLE_ID =
            "clock-seconds-settings-mixed-menu-title";
    private static final String HOOK_SETTINGS_HORIZONTAL_MENU_TITLE_ID =
            "clock-seconds-settings-horizontal-menu-title";
    private static final int MODE_ENABLED = 1;
    private static final int SETTINGS_ENABLED_INDEX = 1;
    private static final Pattern FIVE_MINUTES_PATTERN = Pattern.compile(
            "5\\s*(?:分钟|分|minutes?|mins?\\.?)", Pattern.CASE_INSENSITIVE);
    private static final String SETTINGS_HINT =
            "模块运行中：开启显秒后不会在 5 分钟后自动关闭；仍可通过本设置或长按时钟关闭。";
    private final Set<Object> secondsMenuOptions =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (SYSTEM_UI_PACKAGE.equals(packageName)) {
            installSystemUiHooks(param.getDefaultClassLoader());
        } else if (SETTINGS_PACKAGE.equals(packageName)) {
            installSettingsStateHook(param.getDefaultClassLoader());
        } else if (LAUNCHER_PACKAGE.equals(packageName)) {
            installLauncherMemoryInfoHook(param.getDefaultClassLoader());
        }
    }

    private void installSystemUiHooks(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = Class.forName(CONTROLLER_CLASS, false, classLoader);

            Method isDeadlineActive =
                    controllerClass.getDeclaredMethod("isOplusFiveMinutesActive", long.class);
            Method scheduleExpiry =
                    controllerClass.getDeclaredMethod("scheduleExpiryLocked", int.class, long.class);
            Method onUserOrModeChanged = controllerClass.getDeclaredMethod(
                    "onUserOrModeChanged", int.class, int.class, long.class);
            Field lastAppliedMode = controllerClass.getField("lastAppliedMode");
            Class<?> networkSpeedControllerClass = Class.forName(
                    "com.oplus.systemui.statusbar.phone.netspeed.OplusNetworkSpeedControllerExImpl",
                    false,
                    classLoader);
            Method postNetworkSpeedUpdate = networkSpeedControllerClass.getDeclaredMethod(
                    "postUpdateNetworkSpeedDelay", long.class);
            Field networkSpeedContext = networkSpeedControllerClass.getDeclaredField("context");
            networkSpeedContext.setAccessible(true);

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

            hook(onUserOrModeChanged)
                    .setId(HOOK_MODE_CHANGE_DEADLINE_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int mode = ((Number) chain.getArg(1)).intValue();
                        if (mode != MODE_ENABLED) {
                            return chain.proceed();
                        }
                        return chain.proceed(new Object[]{
                                chain.getArg(0),
                                chain.getArg(1),
                                0L
                        });
                    });

            hook(postNetworkSpeedUpdate)
                    .setId(HOOK_NETWORK_SPEED_INTERVAL_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        long requestedDelay = ((Number) chain.getArg(0)).longValue();
                        if (requestedDelay <= 0L) {
                            return chain.proceed();
                        }
                        Context context = (Context) networkSpeedContext.get(chain.getThisObject());
                        int interval = Settings.Secure.getInt(
                                context.getContentResolver(),
                                NETWORK_SPEED_INTERVAL_SETTING,
                                DEFAULT_NETWORK_SPEED_INTERVAL_MS);
                        if (!isValidNetworkSpeedInterval(interval)) {
                            interval = DEFAULT_NETWORK_SPEED_INTERVAL_MS;
                        }
                        return chain.proceed(new Object[]{(long) interval});
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
            Class<?> couiMenuPreferenceClass =
                    Class.forName("com.coui.appcompat.preference.COUIMenuPreference", false, classLoader);
            Method setAssignment =
                    couiPreferenceClass.getDeclaredMethod("setAssignment", CharSequence.class);
            Method getPreferenceKey = couiPreferenceClass.getMethod("getKey");
            Field contentResolver = settingsControllerClass.getDeclaredField("mContentResolver");
            Field preferenceField = settingsControllerClass.getDeclaredField("mPreference");
            contentResolver.setAccessible(true);
            preferenceField.setAccessible(true);

            Class<?> popupListItemClass = Class.forName(
                    "com.coui.appcompat.poplist.PopupListItem", false, classLoader);
            Method getPopupTitle = popupListItemClass.getMethod("getTitle");
            Method setPopupList = couiMenuPreferenceClass.getDeclaredMethod(
                    "setPopupList", ArrayList.class);
            Class<?> mixedPopupListAdapterClass = Class.forName(
                    "com.coui.appcompat.poplist.MixedPopupListAdapter", false, classLoader);
            Method setMixedVerticalTitle = mixedPopupListAdapterClass.getDeclaredMethod(
                    "setTitleForVertical", TextView.class, popupListItemClass, int.class);
            Method setMixedHorizontalTitle = mixedPopupListAdapterClass.getDeclaredMethod(
                    "setTitleForHorizontal", TextView.class, popupListItemClass);
            Class<?> defaultAdapterClass = Class.forName(
                    "com.coui.appcompat.poplist.DefaultAdapter", false, classLoader);
            Method setDefaultTitle = defaultAdapterClass.getDeclaredMethod(
                    "setTitle", TextView.class, popupListItemClass, int.class);
            setMixedVerticalTitle.setAccessible(true);
            setMixedHorizontalTitle.setAccessible(true);
            setDefaultTitle.setAccessible(true);

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

            hook(setPopupList)
                    .setId(HOOK_SETTINGS_POPUP_LIST_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object menuPreference = chain.getThisObject();
                        if (CLOCK_SECONDS_PREFERENCE_KEY.equals(
                                getPreferenceKey.invoke(menuPreference))) {
                            Object popupItems = chain.getArg(0);
                            if (popupItems instanceof ArrayList) {
                                for (Object item : (ArrayList<?>) popupItems) {
                                    String title = (String) getPopupTitle.invoke(item);
                                    if (title != null && FIVE_MINUTES_PATTERN.matcher(title).find()) {
                                        secondsMenuOptions.add(item);
                                    }
                                }
                            }
                        }
                        return chain.proceed();
                    });

            hook(setMixedVerticalTitle)
                    .setId(HOOK_SETTINGS_MIXED_MENU_TITLE_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        applyPermanentDurationToMenuTitle(chain.getArg(0), chain.getArg(1));
                        return result;
                    });

            hook(setMixedHorizontalTitle)
                    .setId(HOOK_SETTINGS_HORIZONTAL_MENU_TITLE_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        applyPermanentDurationToMenuTitle(chain.getArg(0), chain.getArg(1));
                        return result;
                    });

            hook(setDefaultTitle)
                    .setId(HOOK_SETTINGS_DEFAULT_MENU_TITLE_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        applyPermanentDurationToMenuTitle(chain.getArg(0), chain.getArg(1));
                        return result;
                    });

            log(Log.INFO, TAG, "Installed ColorOS clock-seconds Settings hook");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Failed to install clock-seconds Settings hook", error);
        }
    }

    private void installLauncherMemoryInfoHook(ClassLoader classLoader) {
        try {
            Class<?> memoryInfoManagerClass = Class.forName("fp.e", false, classLoader);
            Method updateMemoryInfoState = memoryInfoManagerClass.getDeclaredMethod("i");
            Method isMemoryInfoAllowed = memoryInfoManagerClass.getDeclaredMethod("g");
            Method isMemoryInfoEnabled = memoryInfoManagerClass.getDeclaredMethod("h");
            Method getMemoryInfoManager = memoryInfoManagerClass.getDeclaredMethod("a", Context.class);
            Field memoryInfoAllowed = memoryInfoManagerClass.getField("f33271m");
            Field memoryInfoEnabled = memoryInfoManagerClass.getField("f33262d");
            Field contextField = memoryInfoManagerClass.getField("f33260b");
            memoryInfoAllowed.setAccessible(true);
            getMemoryInfoManager.setAccessible(true);

            Class<?> preferenceClass = Class.forName(
                    "androidx.preference.Preference", false, classLoader);
            Class<?> preferenceGroupClass = Class.forName(
                    "androidx.preference.PreferenceGroup", false, classLoader);
            Method getPreferenceKey = preferenceClass.getMethod("getKey");
            Method setPreferenceVisible = preferenceClass.getMethod("setVisible", boolean.class);
            Class<?> lockSettingFragmentClass = Class.forName(
                    "com.oplus.quickstep.locksetting.ui.LockSettingFragment", false, classLoader);
            Class<?> lockSettingActivityClass = Class.forName(
                    "com.oplus.quickstep.locksetting.ui.LockSettingActivity", false, classLoader);
            Class<?> clearAllPanelClass = Class.forName(
                    "com.oplus.quickstep.views.OplusClearAllPanelView", false, classLoader);
            Method updateMemoryPanel = clearAllPanelClass.getDeclaredMethod("B", boolean.class);
            Method removePreference = preferenceGroupClass.getDeclaredMethod("f", preferenceClass);
            Method addPreference = preferenceGroupClass.getDeclaredMethod("b", preferenceClass);
            Method getPreferenceParent = preferenceClass.getMethod("getParent");
            Method onCreateView = lockSettingFragmentClass.getDeclaredMethod(
                    "onCreateView",
                    Class.forName("android.view.LayoutInflater", false, classLoader),
                    Class.forName("android.view.ViewGroup", false, classLoader),
                    Class.forName("android.os.Bundle", false, classLoader));
            Method lockSettingActivityOnCreate = lockSettingActivityClass.getDeclaredMethod(
                    "onCreate", Class.forName("android.os.Bundle", false, classLoader));
            Method getPreferenceScreen = lockSettingFragmentClass.getMethod("getPreferenceScreen");
            Method fragmentUpdateMemoryInfoSwitch = lockSettingFragmentClass.getDeclaredMethod(
                    "updateMemoryInfoSwitch", boolean.class);
            Field memoryInfoSwitch = lockSettingFragmentClass.getDeclaredField("mDisplayMemoryInforSwitch");
            Field memoryInfoCategory = lockSettingFragmentClass.getDeclaredField("mDisplayInformationCategory");
            Field fragmentContext = lockSettingFragmentClass.getDeclaredField("mContext");
            memoryInfoSwitch.setAccessible(true);
            memoryInfoCategory.setAccessible(true);
            fragmentContext.setAccessible(true);
            addPreference.setAccessible(true);

            try {
                hook(setPreferenceVisible)
                        .setId(HOOK_LAUNCHER_MEMORY_INFO_VISIBILITY_ID)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object preference = chain.getThisObject();
                            Object key = getPreferenceKey.invoke(preference);
                            if (Boolean.FALSE.equals(chain.getArg(0))
                                    && (MEMORY_INFO_SETTING.equals(key)
                                    || MEMORY_INFO_CATEGORY_KEY.equals(key))) {
                                return chain.proceed(new Object[]{true});
                            }
                            return chain.proceed();
                        });
            } catch (Throwable error) {
                log(Log.ERROR, TAG, "Failed to preserve Launcher memory preference visibility", error);
            }

            hook(updateMemoryInfoState)
                    .setId(HOOK_LAUNCHER_MEMORY_INFO_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object manager = chain.getThisObject();
                        Context context = (Context) contextField.get(manager);
                        try {
                            memoryInfoAllowed.setBoolean(manager, true);
                        } catch (IllegalAccessException error) {
                            log(Log.WARN, TAG, "Could not update Launcher memory capability field", error);
                        }
                        Settings.Secure.putInt(
                                context.getContentResolver(), MEMORY_INFO_COMPAT_SETTING, 1);
                        boolean enabled = Settings.Secure.getInt(
                                context.getContentResolver(), MEMORY_INFO_SETTING, 0) == 1;
                        memoryInfoEnabled.setBoolean(manager, enabled);
                        return result;
                    });

            hook(isMemoryInfoAllowed)
                    .setId(HOOK_LAUNCHER_MEMORY_INFO_ID + "-capability")
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> true);

            hook(isMemoryInfoEnabled)
                    .setId(HOOK_LAUNCHER_MEMORY_INFO_ID + "-enabled")
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object manager = chain.getThisObject();
                        Context context = (Context) contextField.get(manager);
                        return Settings.Secure.getInt(
                                context.getContentResolver(), MEMORY_INFO_SETTING, 0) == 1;
                    });

            try {
                hook(updateMemoryPanel)
                        .setId(HOOK_LAUNCHER_MEMORY_INFO_ID + "-panel")
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Context context = ((android.view.View) chain.getThisObject()).getContext();
                            Object manager = getMemoryInfoManager.invoke(null, context);
                            boolean enabled = Settings.Secure.getInt(
                                    context.getContentResolver(), MEMORY_INFO_SETTING, 0) == 1;
                            memoryInfoEnabled.setBoolean(manager, enabled);
                            return chain.proceed();
                        });
            } catch (Throwable error) {
                log(Log.ERROR, TAG, "Failed to hook Launcher recent-task memory panel", error);
            }

            hook(fragmentUpdateMemoryInfoSwitch)
                    .setId(HOOK_LAUNCHER_MEMORY_INFO_SWITCH_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context context = (Context) fragmentContext.get(chain.getThisObject());
                        if (context == null) {
                            return chain.proceed();
                        }
                        boolean enabled = Settings.Secure.getInt(
                                context.getContentResolver(), MEMORY_INFO_SETTING, 0) == 1;
                        return chain.proceed(new Object[]{enabled});
                    });

            try {
                hook(removePreference)
                        .setId(HOOK_LAUNCHER_MEMORY_INFO_CATEGORY_ID)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object preference = chain.getArg(0);
                            if (preference != null && MEMORY_INFO_CATEGORY_KEY.equals(
                                    getPreferenceKey.invoke(preference))) {
                                return false;
                            }
                            return chain.proceed();
                        });
            } catch (Throwable error) {
                log(Log.ERROR, TAG, "Failed to keep Launcher memory settings category", error);
            }

            try {
                hook(onCreateView)
                        .setId(HOOK_LAUNCHER_MEMORY_INFO_UI_ID)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object fragment = chain.getThisObject();
                            Object switchPreference = memoryInfoSwitch.get(fragment);
                            Object category = memoryInfoCategory.get(fragment);
                            Context context = (Context) fragmentContext.get(fragment);
                            boolean enabled = context != null && Settings.Secure.getInt(
                                    context.getContentResolver(), MEMORY_INFO_SETTING, 0) == 1;
                            if (switchPreference != null) {
                                switchPreference.getClass().getMethod("setVisible", boolean.class)
                                        .invoke(switchPreference, true);
                                switchPreference.getClass().getMethod("setChecked", boolean.class)
                                        .invoke(switchPreference, enabled);
                            }
                            if (category != null) {
                                category.getClass().getMethod("setVisible", boolean.class)
                                        .invoke(category, true);
                                if (getPreferenceParent.invoke(category) == null) {
                                    addPreference.invoke(getPreferenceScreen.invoke(fragment), category);
                                }
                            }
                            if (context != null) {
                                Settings.Secure.putLong(context.getContentResolver(),
                                        LAUNCHER_HOOK_TIMESTAMP_SETTING, System.currentTimeMillis());
                            }
                            return result;
                        });
            } catch (Throwable error) {
                log(Log.ERROR, TAG, "Failed to restore Launcher memory setting UI", error);
            }

            hook(lockSettingActivityOnCreate)
                    .setId(HOOK_LAUNCHER_MEMORY_INFO_UI_ID + "-activity")
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Context context = (Context) chain.getThisObject();
                        Settings.Secure.putLong(context.getContentResolver(),
                                LAUNCHER_HOOK_TIMESTAMP_SETTING, System.currentTimeMillis());
                        return result;
                    });

            log(Log.INFO, TAG, "Restored Launcher recent-task memory information availability");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Failed to restore Launcher memory information", error);
        }
    }

    private void applyPermanentDurationToMenuTitle(Object view, Object item) {
        if (secondsMenuOptions.contains(item) && view instanceof TextView) {
            TextView title = (TextView) view;
            title.setText(formatPermanentDuration(title.getText()));
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

    private static boolean isValidNetworkSpeedInterval(int interval) {
        return interval == 500 || (interval >= 1000 && interval <= 5000 && interval % 1000 == 0);
    }
}
