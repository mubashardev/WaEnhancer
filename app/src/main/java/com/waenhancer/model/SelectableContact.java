package com.waenhancer.model;

public class SelectableContact {
    private final String name;
    private final String phoneNumber;
    private boolean selected;

    public SelectableContact(String name, String phoneNumber, boolean selected) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.selected = selected;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
