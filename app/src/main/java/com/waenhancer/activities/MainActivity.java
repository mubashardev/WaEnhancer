package com.waenhancer.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.navigation.NavigationBarView;
import com.waseemsabir.betterypermissionhelper.BatteryPermissionHelper;
import com.waenhancer.App;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.adapter.MainPagerAdapter;
import com.waenhancer.databinding.ActivityMainBinding;
import com.waenhancer.notices.NoticeCenter;
import com.waenhancer.ui.fragments.GeneralFragment;
import com.waenhancer.ui.fragments.HomeFragment;
import com.waenhancer.ui.fragments.base.BasePreferenceFragment;
import com.waenhancer.utils.FilePicker;

import java.io.File;

import android.view.View;
import android.view.ViewGroup;
import android.graphics.drawable.Drawable;
import eightbitlab.com.blurview.BlurAlgorithm;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.NestedScrollView;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.utils.KeyboxFetcher;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private BatteryPermissionHelper batteryPermissionHelper = BatteryPermissionHelper.Companion.getInstance();
    private String pendingScrollToPreference = null;
    private int pendingScrollToFragment = -1;
    private String pendingParentKey = null;

    private boolean isBottomBarHidden = false;
    private long backPressedTime = 0;

    private void animateBottomBar(boolean hide) {
        if (isBottomBarHidden == hide) return;
        isBottomBarHidden = hide;

        float translationY = 0f;
        if (hide) {
            int height = binding.navViewContainer.getHeight();
            float margin = 0f;
            if (binding.navViewContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                margin = ((ViewGroup.MarginLayoutParams) binding.navViewContainer.getLayoutParams()).bottomMargin;
            }
            translationY = height > 0 ? (height + margin + 100f) : 350f;
        }

        binding.navViewContainer.animate()
                .translationY(translationY)
                .setDuration(400)
                .setInterpolator(hide 
                        ? new AccelerateInterpolator(1.5f) 
                        : new OvershootInterpolator(1.1f))
                .start();
    }

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(@NonNull FragmentManager fm,
                                                  @NonNull Fragment f,
                                                  @NonNull View v,
                                                  @Nullable Bundle savedInstanceState) {
                    super.onFragmentViewCreated(fm, f, v, savedInstanceState);
                    setupScrollListenerForView(v);
                }
            };

    private void setupScrollListenerForView(View view) {
        if (view instanceof RecyclerView) {
            attachScrollListener((RecyclerView) view);
        } else if (view instanceof NestedScrollView) {
            attachNestedScrollListener((NestedScrollView) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setupScrollListenerForView(viewGroup.getChildAt(i));
            }
        }
    }

    private void attachScrollListener(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 15) {
                    animateBottomBar(true);
                } else if (dy < -15) {
                    animateBottomBar(false);
                }
            }
        });
    }

    private void attachNestedScrollListener(NestedScrollView nestedScrollView) {
        nestedScrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(@NonNull NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                int dy = scrollY - oldScrollY;
                if (dy > 15) {
                    animateBottomBar(true);
                } else if (dy < -15) {
                    animateBottomBar(false);
                }
            }
        });
    }

    private void setupBottomBarBlur() {
        float radius = 15f;
        View decorView = getWindow().getDecorView();
        ViewGroup rootView = decorView.findViewById(android.R.id.content);
        Drawable windowBackground = decorView.getBackground();

        BlurAlgorithm algorithm;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            algorithm = new RenderEffectBlur();
        } else {
            algorithm = new RenderScriptBlur(this);
        }

        binding.blurView.setupWith(rootView, algorithm)
                .setFrameClearDrawable(windowBackground)
                .setBlurRadius(radius);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.changeLanguage(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupBottomBarBlur();

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);

        setSupportActionBar(binding.toolbar);

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setPageTransformer(new DepthPageTransformer());

        binding.navView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                return switch (item.getItemId()) {
                    case R.id.navigation_chat -> {
                        binding.viewPager.setCurrentItem(0, true);
                        yield true;
                    }
                    case R.id.navigation_privacy -> {
                        binding.viewPager.setCurrentItem(1, true);
                        yield true;
                    }
                    case R.id.navigation_home -> {
                        binding.viewPager.setCurrentItem(2, true);
                        yield true;
                    }
                    case R.id.navigation_media -> {
                        binding.viewPager.setCurrentItem(3, true);
                        yield true;
                    }
                    case R.id.navigation_colors -> {
                        binding.viewPager.setCurrentItem(4, true);
                        yield true;
                    }
                    default -> false;
                };
            }
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                binding.navView.getMenu().getItem(position).setChecked(true);

                // Handle pending scroll after page change
                if (pendingScrollToFragment == position && pendingScrollToPreference != null) {
                    final String scrollKey = pendingScrollToPreference;
                    final String parentKey = pendingParentKey;
                    pendingScrollToPreference = null;
                    pendingScrollToFragment = -1;
                    pendingParentKey = null;

                    // Wait for fragment to be ready
                    binding.viewPager.postDelayed(() -> {
                        scrollToPreferenceInCurrentFragment(scrollKey, parentKey);
                    }, 300);
                }
            }
        });
        binding.viewPager.setCurrentItem(2, false);
        createMainDir();
        FilePicker.registerFilePicker(this);
        try {
            KeyboxFetcher.syncKeyboxAsync(this);
        } catch (Throwable ignored) {}

        // Wire up custom header action buttons
        binding.btnSearch.setOnClickListener(v -> {
            var options = ActivityOptionsCompat.makeCustomAnimation(
                    this, R.anim.slide_in_right, R.anim.slide_out_left);
            startActivity(new Intent(this, SearchActivity.class), options.toBundle());
        });

        binding.btnAbout.setOnClickListener(v -> {
            var options = ActivityOptionsCompat.makeCustomAnimation(
                    this, R.anim.slide_in_right, R.anim.slide_out_left);
            startActivity(new Intent(this, AboutActivity.class), options.toBundle());
        });

        binding.btnBattery.setOnClickListener(v -> {
            if (batteryPermissionHelper.isBatterySaverPermissionAvailable(this, true)) {
                batteryPermissionHelper.getPermission(this, true, true);
            } else {
                var intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
            }
        });

        // Hide battery button if already optimized
        var powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            binding.btnBattery.setVisibility(View.GONE);
        }

        // Handle incoming navigation from search
        handleIncomingIntent(getIntent());

        // Request notification permission if needed (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            String permission = "android.permission.POST_NOTIFICATIONS";
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{permission}, 101);
            }
        }
    }

    private void createMainDir() {
        var nomedia = new File(App.getWaEnhancerFolder(), ".nomedia");
        if (nomedia.exists()) {
            nomedia.delete();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null)
            return;

        /* Log removed */
        if (intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                Object val = intent.getExtras().get(key);
                /* Log removed */
            }
        }

        if ("android.service.quicksettings.action.QS_TILE_PREFERENCES".equals(intent.getAction())) {
            ComponentName component = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME);
            if (component != null) {
                String className = component.getClassName();
                int fragmentPos = -1;
                String prefKey = null;
                String parentKey = null;

                if (className.contains("GhostModeTileService")) {
                    fragmentPos = 1;
                    prefKey = "ghostmode";
                } else if (className.contains("DndModeTileService")) {
                    fragmentPos = 1;
                    prefKey = "show_dndmode";
                } else if (className.contains("FreezeLastSeenTileService")) {
                    fragmentPos = 1;
                    prefKey = "freezelastseen";
                } else if (className.contains("StealthReadTicksTileService")) {
                    fragmentPos = 1;
                    prefKey = "hideread";
                } else if (className.contains("StealthStatusViewingTileService")) {
                    fragmentPos = 1;
                    prefKey = "hidestatusview";
                } else if (className.contains("AlwaysOnlineTileService")) {
                    fragmentPos = 1;
                    prefKey = "always_online";
                } else if (className.contains("ProximitySensorSwitchTileService")) {
                    fragmentPos = 3;
                    prefKey = "disable_sensor_proximity";
                } else if (className.contains("BlockCallsTileService")) {
                    fragmentPos = 1;
                    prefKey = "call_privacy";
                } else if (className.contains("SmartTypingTileService")) {
                    fragmentPos = 1;
                    prefKey = "always_typing_global";
                } else if (className.contains("ContactOnlineNotificationsTileService")) {
                    fragmentPos = 0;
                    prefKey = "show_toast_on_contact_online";
                    parentKey = "conversation";
                } else if (className.contains("HideDeliveredTileService")) {
                    fragmentPos = 1;
                    prefKey = "hidereceipt";
                }

                if (fragmentPos >= 0) {
                    intent.putExtra("navigate_to_fragment", fragmentPos);
                    intent.putExtra("scroll_to_preference", prefKey);
                    if (parentKey != null) {
                        intent.putExtra("parent_preference", parentKey);
                    }
                }
            }
        }

        int fragmentPosition = intent.getIntExtra("navigate_to_fragment", -1);
        String preferenceKey = intent.getStringExtra("scroll_to_preference");
        String parentKey = intent.getStringExtra("parent_preference");

        if (fragmentPosition >= 0 && preferenceKey != null) {
            if (binding.viewPager.getCurrentItem() == fragmentPosition) {
                // Since target page is already selected, onPageSelected won't fire.
                // Clear any pending scroll variables and scroll immediately.
                pendingScrollToPreference = null;
                pendingScrollToFragment = -1;
                pendingParentKey = null;
                scrollToPreferenceInCurrentFragment(preferenceKey, parentKey);
            } else {
                // Store the scroll target
                pendingScrollToPreference = preferenceKey;
                pendingScrollToFragment = fragmentPosition;
                pendingParentKey = parentKey;

                // Navigate to the fragment (onPageSelected will handle the scroll)
                binding.viewPager.setCurrentItem(fragmentPosition, false);
            }

            // Clear intent extras
            intent.removeExtra("navigate_to_fragment");
            intent.removeExtra("scroll_to_preference");
            intent.removeExtra("parent_preference");
        } else if (fragmentPosition >= 0) {
            // Just navigate without scrolling
            binding.viewPager.setCurrentItem(fragmentPosition, true);
        }
    }

    private void scrollToPreferenceInCurrentFragment(String preferenceKey, String parentKey) {
        // Use post to ensure ViewPager is ready
        binding.viewPager.post(() -> {
            int currentItem = binding.viewPager.getCurrentItem();
            // In ViewPager2 with FragmentStateAdapter, fragments are tagged as "f" + position
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + currentItem);
            
            if (fragment == null) {
                // Try to find by ID if tag fails (depends on adapter implementation)
                fragment = getSupportFragmentManager().findFragmentById(binding.viewPager.getId());
            }

            if (fragment == null) return;

            if (fragment instanceof GeneralFragment || fragment instanceof HomeFragment) {
                // Nested fragments (General/Home use a child fragment container)
                if (parentKey != null && !parentKey.isEmpty()) {
                    navigateToSubFragmentAndScroll(fragment, parentKey, preferenceKey);
                } else {
                    // Direct scroll in current child fragment
                    scrollInChildFragment(fragment, preferenceKey);
                }
            } else if (fragment instanceof BasePreferenceFragment) {
                // Direct preference fragments (no nesting)
                ((BasePreferenceFragment) fragment).scrollToPreference(preferenceKey);
            }
        });
    }

    private void navigateToSubFragmentAndScroll(Fragment parentFragment, String parentKey, String childPreferenceKey) {
        if (parentFragment instanceof GeneralFragment) {
            GeneralFragment gf = (GeneralFragment) parentFragment;
            gf.showTab(parentKey);
            if (gf.getView() != null) {
                gf.getView().post(() -> {
                    if (gf.isAdded()) {
                        Fragment currentChild = gf.getChildFragmentManager().findFragmentById(R.id.general_frag_container);
                        if (currentChild instanceof BasePreferenceFragment) {
                            ((BasePreferenceFragment) currentChild).scrollToPreference(childPreferenceKey);
                        }
                    }
                });
            }
            return;
        }

        // Check if the target subfragment is already displayed
        Fragment currentChild = parentFragment.getChildFragmentManager().findFragmentById(R.id.frag_container);
        boolean isAlreadyDisplayed = false;
        
        if (currentChild != null) {
            if ("general_home".equals(parentKey) && currentChild instanceof GeneralFragment.GeneralPreferenceFragment) {
                isAlreadyDisplayed = true;
            } else if ("homescreen".equals(parentKey) && currentChild instanceof GeneralFragment.HomeScreenGeneralPreference) {
                isAlreadyDisplayed = true;
            } else if ("conversation".equals(parentKey) && currentChild instanceof GeneralFragment.ConversationGeneralPreference) {
                isAlreadyDisplayed = true;
            }
        }

        if (isAlreadyDisplayed) {
            if (currentChild instanceof BasePreferenceFragment) {
                ((BasePreferenceFragment) currentChild).scrollToPreference(childPreferenceKey);
            }
            return;
        }

        // Directly instantiate the sub-fragment
        Fragment subFragment = null;

        switch (parentKey) {
            case "general_home":
                subFragment = new GeneralFragment.GeneralPreferenceFragment();
                break;
            case "homescreen":
                subFragment = new GeneralFragment.HomeScreenGeneralPreference();
                break;
            case "conversation":
                subFragment = new GeneralFragment.ConversationGeneralPreference();
                break;
        }

        if (subFragment != null && parentFragment.isAdded() && parentFragment.getView() != null) {
            if (parentFragment.getView().findViewById(R.id.frag_container) == null) {
                parentFragment.getView().post(() -> {
                    if (parentFragment.isAdded() && parentFragment.getView() != null) {
                        navigateToSubFragmentAndScroll(parentFragment, parentKey, childPreferenceKey);
                    }
                });
                return;
            }
            final Fragment finalSubFragment = subFragment;
            // Replace the current child fragment with back stack so back button works
            parentFragment.getChildFragmentManager().beginTransaction()
                    .replace(R.id.frag_container, subFragment)
                    .addToBackStack(null)
                    .commit();
            parentFragment.getChildFragmentManager().executePendingTransactions();

            // Wait for fragment to be ready, then scroll
            parentFragment.getView().postDelayed(() -> {
                if (finalSubFragment.isAdded() && finalSubFragment instanceof BasePreferenceFragment) {
                    ((BasePreferenceFragment) finalSubFragment).scrollToPreference(childPreferenceKey);
                }
            }, 600);
        }
    }

    private void scrollInChildFragment(Fragment parentFragment, String preferenceKey) {
        Fragment childFragment = parentFragment.getChildFragmentManager().findFragmentById(R.id.frag_container);
        if (childFragment instanceof BasePreferenceFragment) {
            ((BasePreferenceFragment) childFragment).scrollToPreference(preferenceKey);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        invalidateOptionsMenu();
        // Re-check battery optimization each time the user returns to the app
        // (e.g. after granting exemption from system settings)
        var powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            binding.btnBattery.setVisibility(View.GONE);
        }

        // Check if device was unlinked due to stable reversion
        SharedPreferences rawPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (rawPrefs.getBoolean("unlinked_reverted_to_stable", false)) {
            rawPrefs.edit().putBoolean("unlinked_reverted_to_stable", false).apply();
            showReversionBottomSheet();
        }

        // Check if there is a pending downgrade reason to show
        String downgradeMsg = rawPrefs.getString("pending_downgrade_reason_msg", null);
        if (downgradeMsg != null) {
            Toast.makeText(this, downgradeMsg, Toast.LENGTH_LONG).show();
            showDowngradeBottomSheet(downgradeMsg);
        }

        // Remote notices (cached + rate-limited)
        binding.getRoot().post(() -> NoticeCenter.checkAndShow(this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Menu items are handled by custom action buttons in the layout
        return true;
    }

    @SuppressLint("BatteryLife")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_search) {
            var options = ActivityOptionsCompat.makeCustomAnimation(
                    this, R.anim.slide_in_right, R.anim.slide_out_left);
            startActivity(new Intent(this, SearchActivity.class), options.toBundle());
            return true;
        } else if (item.getItemId() == R.id.menu_about) {
            var options = ActivityOptionsCompat.makeCustomAnimation(
                    this, R.anim.slide_in_right, R.anim.slide_out_left);
            startActivity(new Intent(this, AboutActivity.class), options.toBundle());
            return true;
        } else if (item.getItemId() == R.id.batteryoptimization) {
            if (batteryPermissionHelper.isBatterySaverPermissionAvailable(this, true)) {
                batteryPermissionHelper.getPermission(this, true, true);
            } else {
                var intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public static boolean isXposedFrameworkPresent(Context context) {
        final String TAG = "WaeX_FwDetect";

        // 1. System property written by our own initZygote — most reliable signal when in-scope or system allows.
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            String val = (String) get.invoke(null, "debug.waenhancer.lsposed", "0");
            if ("1".equals(val)) {
                /* Log removed */
                return true;
            }
        } catch (Throwable ignored) {}

        // 2. Shell Command Check: Check directory visibility of LSPosed or other modules directories.
        // Bypasses Java API sandboxing since we catch the "Permission denied" error on existing folders.
        try {
            String[] commands = {
                "ls /data/adb/lspd",
                "ls /data/adb/modules",
                "ls /data/adb/ksu"
            };
            for (String cmd : commands) {
                Process process = Runtime.getRuntime().exec(cmd);
                int exitCode = process.waitFor();
                BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line = stdErr.readLine();
                if (exitCode == 0 || (line != null && line.contains("Permission denied"))) {
                    /* Log removed */
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Package manager — check for active manager packages (LSPosed Manager/Zygisk)
        if (context != null) {
            PackageManager pm = context.getPackageManager();
            for (String pkg : new String[]{
                    "org.lsposed.manager", "io.github.lsposed.manager",
                    "org.meowcat.edxposed.manager", "com.solohsu.android.edxp.manager",
                    "de.robv.android.xposed.installer", "me.weishu.exp"}) {
                try {
                    pm.getPackageInfo(pkg, 0);
                    /* Log removed */
                    return true;
                } catch (Throwable ignored) {}
            }
        }

        /* Log removed */
        return false;
    }

    @Override
    public void onBackPressed() {
        int currentItem = binding.viewPager.getCurrentItem();
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + currentItem);
        if (fragment != null && fragment.isAdded()) {
            FragmentManager childFm = fragment.getChildFragmentManager();
            if (childFm.getBackStackEntryCount() > 0) {
                childFm.popBackStack();
                return;
            }
        }

        if (currentItem != 2) {
            binding.viewPager.setCurrentItem(2, true);
            return;
        }

        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
            backPressedTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }

    private static class DepthPageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.85f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            int pageWidth = page.getWidth();

            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                page.setAlpha(1f);
                page.setTranslationX(0f);
                page.setTranslationZ(0f);
                page.setScaleX(1f);
                page.setScaleY(1f);
            } else if (position <= 1) {
                page.setAlpha(1 - position);
                page.setTranslationX(pageWidth * -position);
                page.setTranslationZ(-1f);
                float scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            } else {
                page.setAlpha(0f);
            }
        }
    }

    private void showReversionBottomSheet() {
        try {
            BottomSheetDialog dialog = new BottomSheetDialog(this);
            View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_action, null);
            dialog.setContentView(view);
            dialog.setCancelable(true);

            ((MaterialTextView) view.findViewById(R.id.bs_title)).setText("Reverted to Stable");
            ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(
                    "Your device has been unlinked and Pro configurations cleared because you are running the Stable version of the module. Active Pro trial features are only whitelisted on the Beta update channel for now.");

            MaterialButton joinBtn = view.findViewById(R.id.bs_confirm_btn);
            joinBtn.setText("Join Beta Channel");
            joinBtn.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(this, ChangelogActivity.class);
                intent.putExtra("target_channel", "beta");
                startActivity(intent);
            });

            MaterialButton dismissBtn = view.findViewById(R.id.bs_cancel_btn);
            dismissBtn.setText("Dismiss");
            dismissBtn.setOnClickListener(v -> dialog.dismiss());

            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
            dialog.show();
        } catch (Exception ignored) {}
    }

    private void showDowngradeBottomSheet(String message) {
        try {
            BottomSheetDialog dialog = new BottomSheetDialog(this);
            View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_action, null);
            dialog.setContentView(view);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            ((MaterialTextView) view.findViewById(R.id.bs_title)).setText("Downgraded to Free");
            ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(message);

            MaterialButton actionBtn = view.findViewById(R.id.bs_confirm_btn);
            actionBtn.setText("Upgrade to Pro");
            actionBtn.setOnClickListener(v -> {
                SharedPreferences rawPrefs = PreferenceManager.getDefaultSharedPreferences(this);
                rawPrefs.edit().remove("pending_downgrade_reason_msg").apply();
                
                dialog.dismiss();
                try {
                    Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                    startActivity(new Intent(this, clazz));
                } catch (Exception ignored) {}
            });

            MaterialButton dismissBtn = view.findViewById(R.id.bs_cancel_btn);
            dismissBtn.setText("Dismiss");
            dismissBtn.setOnClickListener(v -> {
                SharedPreferences rawPrefs = PreferenceManager.getDefaultSharedPreferences(this);
                rawPrefs.edit().remove("pending_downgrade_reason_msg").apply();
                
                dialog.dismiss();
            });

            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
            dialog.show();
        } catch (Exception ignored) {}
    }
}