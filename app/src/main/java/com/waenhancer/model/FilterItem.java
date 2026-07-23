package com.waenhancer.model;

public class FilterItem {
    public static final String BEHAVIOR_GONE = "gone";
    public static final String BEHAVIOR_COLOR = "color";
    public static final String BEHAVIOR_OPACITY = "opacity";
    public static final String BEHAVIOR_RESIZE = "resize";

    public String id;
    public String behavior = BEHAVIOR_GONE;
    public int color = 0xFFFF0000; // Default to Red
    public int opacity = 100;
    public float scale = 1.0f;

    public FilterItem(String id) {
        this.id = id;
    }

    public FilterItem(String id, String behavior, int color, int opacity, float scale) {
        this.id = id;
        this.behavior = behavior;
        this.color = color;
        this.opacity = opacity;
        this.scale = scale;
    }
}
