package io.github.coolzyd9107.coloros17clockseconds.settings;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.coolzyd9107.coloros17clockseconds.R;

public final class ModuleSettingsActivity extends AppCompatActivity {
    private static final String NETWORK_SPEED_INTERVAL_KEY =
            "coloros_status_bar_network_speed_refresh_interval_ms";
    private static final int DEFAULT_INTERVAL_MS = 4000;
    private static final int[] INTERVALS_MS = {500, 1000, 2000, 3000, 4000, 5000};
    private static final int[] INTERVAL_LABELS = {
            R.string.interval_half_second,
            R.string.interval_one_second,
            R.string.interval_two_seconds,
            R.string.interval_three_seconds,
            R.string.interval_four_seconds,
            R.string.interval_five_seconds
    };
    private static final String RESTART_COMMAND =
            "for pkg in com.android.systemui com.android.settings com.android.launcher; do "
                    + "for pid in $(pidof \"$pkg\" 2>/dev/null); do "
                    + "kill -TERM \"$pid\" 2>/dev/null || true; "
                    + "done; done";

    private final ExecutorService rootExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ChipGroup intervalGroup;
    private View rootView;
    private int selectedIntervalMs = DEFAULT_INTERVAL_MS;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int storedInterval = Settings.Secure.getInt(
                getContentResolver(), NETWORK_SPEED_INTERVAL_KEY, DEFAULT_INTERVAL_MS);
        selectedIntervalMs = isSupportedInterval(storedInterval)
                ? storedInterval
                : DEFAULT_INTERVAL_MS;
        setContentView(createContent());
    }

    @Override
    protected void onDestroy() {
        rootExecutor.shutdownNow();
        super.onDestroy();
    }

    private View createContent() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(MaterialColors.getColor(page, com.google.android.material.R.attr.colorSurface));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.settings_title);
        toolbar.setTitleTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        toolbar.setElevation(0f);
        page.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(28));
        scrollView.addView(content);
        page.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rootView = content;

        content.addView(createNetworkSpeedCard(), cardLayoutParams());
        content.addView(createRestartCard(), cardLayoutParams());
        return page;
    }

    private MaterialCardView createNetworkSpeedCard() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(20), dp(20), dp(18));

        TextView title = textView(R.string.network_interval_title,
                com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        body.addView(title);

        TextView summary = textView(R.string.network_interval_summary,
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        summary.setTextColor(MaterialColors.getColor(summary,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams summaryParams = wrapParams();
        summaryParams.topMargin = dp(6);
        body.addView(summary, summaryParams);

        intervalGroup = new ChipGroup(this);
        intervalGroup.setSingleSelection(true);
        intervalGroup.setSelectionRequired(true);
        intervalGroup.setChipSpacingHorizontal(dp(8));
        intervalGroup.setChipSpacingVertical(dp(8));
        intervalGroup.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        for (int i = 0; i < INTERVALS_MS.length; i++) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            chip.setText(INTERVAL_LABELS[i]);
            chip.setTag(INTERVALS_MS[i]);
            intervalGroup.addView(chip, new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (INTERVALS_MS[i] == selectedIntervalMs) {
                chip.setChecked(true);
            }
        }

        intervalGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            Chip chip = group.findViewById(checkedIds.get(0));
            if (chip == null || !(chip.getTag() instanceof Integer)) {
                return;
            }
            int interval = (Integer) chip.getTag();
            if (interval != selectedIntervalMs) {
                saveNetworkSpeedInterval(interval);
            }
        });

        LinearLayout.LayoutParams groupParams = wrapParams();
        groupParams.topMargin = dp(18);
        body.addView(intervalGroup, groupParams);

        TextView rootNote = textView(R.string.network_interval_root_note,
                com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        rootNote.setTextColor(MaterialColors.getColor(rootNote,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams noteParams = wrapParams();
        noteParams.topMargin = dp(12);
        body.addView(rootNote, noteParams);

        return cardWithContent(body);
    }

    private MaterialCardView createRestartCard() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(20), dp(20), dp(18));

        TextView title = textView(R.string.restart_section_title,
                com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        body.addView(title);

        TextView summary = textView(R.string.restart_section_summary,
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        summary.setTextColor(MaterialColors.getColor(summary,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams summaryParams = wrapParams();
        summaryParams.topMargin = dp(6);
        body.addView(summary, summaryParams);

        addScopeRow(body, R.string.scope_system_ui, "com.android.systemui");
        addDivider(body);
        addScopeRow(body, R.string.scope_settings, "com.android.settings");
        addDivider(body);
        addScopeRow(body, R.string.scope_launcher, "com.android.launcher");

        MaterialButton restartButton = new MaterialButton(this);
        restartButton.setText(R.string.restart_scoped_apps);
        restartButton.setIconResource(R.drawable.ic_restart_24);
        restartButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        restartButton.setOnClickListener(view -> confirmRestart());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(16);
        body.addView(restartButton, buttonParams);

        return cardWithContent(body);
    }

    private void addScopeRow(LinearLayout parent, int titleRes, String packageName) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView title = textView(titleRes,
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        row.addView(title);

        TextView packageLabel = new TextView(this);
        packageLabel.setText(packageName);
        packageLabel.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        packageLabel.setTextColor(MaterialColors.getColor(packageLabel,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams packageParams = wrapParams();
        packageParams.topMargin = dp(2);
        row.addView(packageLabel, packageParams);

        parent.addView(row, wrapParams());
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(MaterialColors.getColor(divider,
                com.google.android.material.R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        parent.addView(divider, params);
    }

    private MaterialCardView cardWithContent(View content) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(28));
        card.setCardElevation(0f);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
        card.addView(content, new MaterialCardView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void saveNetworkSpeedInterval(int interval) {
        int previous = selectedIntervalMs;
        selectedIntervalMs = interval;
        setIntervalControlsEnabled(false);
        runRootCommand("settings put secure " + NETWORK_SPEED_INTERVAL_KEY + " " + interval,
                success -> {
                    setIntervalControlsEnabled(true);
                    if (success) {
                        Snackbar.make(rootView, R.string.interval_saved, Snackbar.LENGTH_SHORT).show();
                    } else {
                        selectedIntervalMs = previous;
                        checkIntervalChip(previous);
                        Snackbar.make(rootView, R.string.root_action_failed, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmRestart() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restart_confirm_title)
                .setMessage(R.string.restart_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restart_confirm_action, (dialog, which) -> {
                    runRootCommand(RESTART_COMMAND, success -> Snackbar.make(rootView,
                            success ? R.string.root_restart_sent : R.string.root_action_failed,
                            Snackbar.LENGTH_LONG).show());
                })
                .show();
    }

    private void runRootCommand(String command, java.util.function.Consumer<Boolean> callback) {
        rootExecutor.execute(() -> {
            boolean success = false;
            try {
                Process process = new ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(true)
                        .start();
                try (InputStream output = process.getInputStream()) {
                    byte[] buffer = new byte[512];
                    while (output.read(buffer) != -1) {
                    }
                }
                success = process.waitFor() == 0;
            } catch (Exception ignored) {
            }
            boolean result = success;
            mainHandler.post(() -> callback.accept(result));
        });
    }

    private void setIntervalControlsEnabled(boolean enabled) {
        for (int i = 0; i < intervalGroup.getChildCount(); i++) {
            intervalGroup.getChildAt(i).setEnabled(enabled);
        }
    }

    private void checkIntervalChip(int interval) {
        for (int i = 0; i < intervalGroup.getChildCount(); i++) {
            Chip chip = (Chip) intervalGroup.getChildAt(i);
            if ((Integer) chip.getTag() == interval) {
                chip.setChecked(true);
                return;
            }
        }
    }

    private TextView textView(int textRes, int textAppearance) {
        TextView text = new TextView(this);
        text.setText(textRes);
        text.setTextAppearance(textAppearance);
        return text;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardLayoutParams() {
        LinearLayout.LayoutParams params = wrapParams();
        params.bottomMargin = dp(18);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static boolean isSupportedInterval(int interval) {
        return interval == 500 || (interval >= 1000 && interval <= 5000 && interval % 1000 == 0);
    }
}
