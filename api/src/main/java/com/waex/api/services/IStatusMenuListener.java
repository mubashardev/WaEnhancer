package com.waex.api.services;

import android.view.Menu;
import android.view.MenuItem;
import java.util.List;

public interface IStatusMenuListener {
    MenuItem onAddMenu(Menu menu, List<?> fMessageList, int currentIndex);
    void onMenuClick(MenuItem item, Object fragmentInstance, List<?> fMessageList, int currentIndex);
}
