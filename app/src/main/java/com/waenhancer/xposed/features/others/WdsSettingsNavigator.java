package com.waenhancer.xposed.features.others;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayDeque;
import de.robv.android.xposed.XposedHelpers;

public class WdsSettingsNavigator {
    private final Activity activity;
    private final FrameLayout container;
    private final ArrayDeque<View> stack = new ArrayDeque<>();
    private final Runnable onEmptyStack;

    public WdsSettingsNavigator(Activity activity, FrameLayout container, Runnable onEmptyStack) {
        this.activity = activity;
        this.container = container;
        this.onEmptyStack = onEmptyStack;
    }

    public void push(View view, String title) {
        if (!stack.isEmpty()) {
            stack.peek().setVisibility(View.GONE);
        }
        stack.push(view);
        container.removeAllViews();
        container.addView(view);
        view.setVisibility(View.VISIBLE);
        updateToolbarTitle(title);
    }

    public boolean pop() {
        if (stack.size() <= 1) {
            stack.clear();
            if (onEmptyStack != null) {
                onEmptyStack.run();
            }
            return false;
        }

        View current = stack.pop();
        current.setVisibility(View.GONE);
        View next = stack.peek();
        container.removeAllViews();
        container.addView(next);
        next.setVisibility(View.VISIBLE);

        // Try to update title based on Tag or restore default
        String title = "WaEnhancerX Settings";
        if (next.getTag() instanceof String) {
            title = (String) next.getTag();
        }
        updateToolbarTitle(title);
        return true;
    }

    public boolean isAtRoot() {
        return stack.size() <= 1;
    }

    public void clear() {
        stack.clear();
    }

    private void updateToolbarTitle(String title) {
        try {
            if (activity.getActionBar() != null) {
                activity.getActionBar().setTitle(title);
            }
        } catch (Throwable ignored) {}
        try {
            // Support action bar reflection if needed
            Object actionBar = XposedHelpers.callMethod(activity, "getSupportActionBar");
            if (actionBar != null) {
                XposedHelpers.callMethod(actionBar, "setTitle", title);
            }
        } catch (Throwable ignored) {}
    }
}