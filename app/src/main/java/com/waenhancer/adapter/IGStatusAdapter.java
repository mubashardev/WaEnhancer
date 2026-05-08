package com.waenhancer.adapter;

import static com.waenhancer.xposed.features.customization.IGStatus.itens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.views.dialog.TabDialogContent;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.WaContactWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class IGStatusAdapter extends ArrayAdapter {


    private final Class<?> clazzImageStatus;
    private final Class<?> statusInfoClazz;
    private final Method setCountStatus;
    private static Drawable cacheIcon;

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        var item = itens.get(position);
        IGStatusViewHolder holder;
        if (convertView == null) {
            holder = new IGStatusViewHolder();
            convertView = createLayoutStatus(holder);
            convertView.setTag(holder);
        } else {
            holder = (IGStatusViewHolder) convertView.getTag();
        }
        if (item == null) {
            holder.setInfo("my_status", position);
            if (hasOwnActiveStatus()) {
                holder.addButton.setVisibility(View.GONE);
            } else {
                holder.addButton.setVisibility(View.VISIBLE);
            }
        } else if (statusInfoClazz.isInstance(item)) {
            if (item instanceof View v) {
                v.setClickable(false);
            }
            holder.setInfo(item, position);
            holder.addButton.setVisibility(View.GONE);
        }
        convertView.setOnClickListener(v -> {
            if (holder.myStatus) {
                if (hasOwnActiveStatus()) {
                    try {
                        var clazz = Unobfuscator.getClassByName("StatusPlaybackActivity", getContext().getClassLoader());
                        var intent = new Intent(WppCore.getCurrentActivity(), clazz);
                        intent.putExtra("jid", "status@broadcast");
                        WppCore.getCurrentActivity().startActivity(intent);
                    } catch (Exception e) {
                        try {
                            var clazz = Unobfuscator.getClassByName("MyStatusesActivity", getContext().getClassLoader());
                            var intent = new Intent(WppCore.getCurrentActivity(), clazz);
                            WppCore.getCurrentActivity().startActivity(intent);
                        } catch (Exception ex) {
                            Utils.showToast(ex.getMessage(), 1);
                        }
                    }
                } else {
                    try {
                        var decorView = WppCore.getCurrentActivity().getWindow().getDecorView();
                        boolean clicked = clickViewByContentDescription(decorView, "updates");
                        if (!clicked) {
                            clicked = clickViewByContentDescription(decorView, "status");
                        }
                        if (!clicked) {
                            var bnv = findBottomNavigationMenuView(decorView);
                            if (bnv instanceof android.view.ViewGroup) {
                                android.view.ViewGroup vg = (android.view.ViewGroup) bnv;
                                if (vg.getChildCount() > 1) {
                                    vg.getChildAt(1).performClick();
                                    clicked = true;
                                }
                            }
                        }
                        if (!clicked) {
                            var viewPager = findViewByClassName(decorView, "ViewPager");
                            if (viewPager != null) {
                                XposedHelpers.callMethod(viewPager, "setCurrentItem", 1, true);
                                clicked = true;
                            }
                        }
                        if (!clicked) {
                            var clazz = Unobfuscator.getClassByName("MyStatusesActivity", getContext().getClassLoader());
                            var intent = new Intent(WppCore.getCurrentActivity(), clazz);
                            WppCore.getCurrentActivity().startActivity(intent);
                        }
                    } catch (Exception e) {
                        Utils.showToast(e.getMessage(), 1);
                    }
                }
                return;
            }
            try {
                var clazz = Unobfuscator.getClassByName("StatusPlaybackActivity", getContext().getClassLoader());
                var intent = new Intent(WppCore.getCurrentActivity(), clazz);
                intent.putExtra("jid", holder.userJid.getPhoneRawString());
                WppCore.getCurrentActivity().startActivity(intent);
            } catch (Exception e) {
                Utils.showToast(e.getMessage(), 1);
            }
        });

        return convertView;
    }

    public IGStatusAdapter(@NonNull Context context, @NonNull Class<?> statusInfoClazz) throws Exception {
        super(context, 0);
        this.clazzImageStatus = Unobfuscator.findFirstClassUsingName(this.getContext().getClassLoader(), StringMatchType.EndsWith, ".ContactStatusThumbnail");
        this.statusInfoClazz = statusInfoClazz;
        this.setCountStatus = ReflectionUtils.findMethodUsingFilter(this.clazzImageStatus, m -> m.getParameterCount() == 3 && Arrays.equals(new Class[]{int.class, int.class, int.class}, m.getParameterTypes()));
    }

    @Override
    public int getCount() {
        return itens.size();
    }

    class IGStatusViewHolder {
        public ImageView igStatusContactPhoto;
        public RelativeLayout addButton;
        public TextView igStatusContactName;
        public RelativeLayout internalContainer;
        public boolean myStatus;
        private FMessageWpp.UserJid userJid;

        private static Object findJidObject(Object obj, int depth) {
            if (obj == null || depth < 0) return null;
            Class<?> clazz = obj.getClass();
            String name = clazz.getName();
            if (name.endsWith(".Jid") || name.endsWith(".UserJid") || name.endsWith(".PhoneUserJid")) {
                return obj;
            }
            if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("androidx.")) {
                return null;
            }
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    Object found = findJidObject(val, depth - 1);
                    if (found != null) return found;
                } catch (Exception ignored) {}
            }
            return null;
        }

        public void setInfo(Object item, int position) {

            if (Objects.equals(item, "my_status")) {
                myStatus = true;
                igStatusContactName.setText(UnobfuscatorCache.getInstance().getString("mystatus"));
                Drawable profile = null;
                try {
                    var myUserJid = WppCore.getMyUserJid();
                    if (myUserJid != null) {
                        var waContact = WaContactWpp.getWaContactFromJid(myUserJid);
                        if (waContact != null && waContact.getProfilePhoto() != null) {
                            profile = BitmapDrawable.createFromPath(waContact.getProfilePhoto().getAbsolutePath());
                        }
                    }
                } catch (Exception ignored) {}
                if (profile == null) {
                    profile = WppCore.getMyPhoto();
                }
                if (profile == null) {
                    String contactName = null;
                    try {
                        var myUserJid = WppCore.getMyUserJid();
                        if (myUserJid != null) {
                            var waContact = WaContactWpp.getWaContactFromJid(myUserJid);
                            if (waContact != null) {
                                contactName = waContact.getDisplayName();
                            }
                        }
                    } catch (Exception ignored) {}
                    if (TextUtils.isEmpty(contactName)) {
                        contactName = "O";
                    }
                    profile = getLetterAvatar(contactName);
                }
                if (profile == null) {
                    profile = DesignUtils.getDrawableByName("avatar_contact");
                }
                if (profile == null) {
                    profile = Utils.getApplication().getDrawable(R.drawable.user_foreground);
                }
                igStatusContactPhoto.setImageDrawable(profile);
                if (hasOwnActiveStatus()) {
                    setCountStatus(1, 1);
                } else {
                    setCountStatus(0, 0);
                }
                return;
            }
            myStatus = false;
            try {
                Object jidObj = findJidObject(item, 3);
                if (jidObj == null) {
                    throw new RuntimeException("WAE: Jid object not found in status item");
                }
                this.userJid = new FMessageWpp.UserJid(jidObj);
                
                Object statusInfo = null;
                for (java.lang.reflect.Field f : item.getClass().getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(item);
                        if (val != null && findJidObject(val, 1) != null) {
                            statusInfo = val;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
                if (statusInfo == null) {
                    statusInfo = item;
                }
                
                var waContact = WaContactWpp.getWaContactFromJid(this.userJid);
                String contactName = null;
                if (waContact != null) {
                    contactName = waContact.getDisplayName();
                }
                if (TextUtils.isEmpty(contactName)) {
                    contactName = WppCore.getContactName(this.userJid);
                }
                if (TextUtils.isEmpty(contactName)) {
                    contactName = "WhatsApp Contact";
                }
                igStatusContactName.setText(contactName);
                
                Drawable profile = null;
                if (waContact != null && waContact.getProfilePhoto() != null) {
                    try {
                        profile = BitmapDrawable.createFromPath(waContact.getProfilePhoto().getAbsolutePath());
                    } catch (Exception ignored) {}
                }
                if (profile == null) {
                    profile = WppCore.getContactPhotoDrawable(this.userJid.getPhoneRawString());
                }
                if (profile == null) {
                    profile = Utils.getApplication().getDrawable(R.drawable.user_foreground);
                }
                igStatusContactPhoto.setImageDrawable(profile);
                
                int total = 1;
                int countUnseen = (position <= com.waenhancer.xposed.features.customization.IGStatus.unseenCount) ? 1 : 0;
                setCountStatus(countUnseen, total);
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }

        public void setCountStatus(int countUnseen, int total) {
            if (setCountStatus != null) {
                try {
                    setCountStatus.invoke(igStatusContactPhoto, total, countUnseen, total);
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
            if (internalContainer != null) {
                if (myStatus) {
                    if (hasOwnActiveStatus()) {
                        internalContainer.setBackground(createRingDrawable(DesignUtils.getUnSeenColor()));
                    } else {
                        internalContainer.setBackground(null);
                    }
                } else {
                    if (countUnseen > 0) {
                        internalContainer.setBackground(createRingDrawable(DesignUtils.getUnSeenColor()));
                    } else if (total > 0) {
                        internalContainer.setBackground(createRingDrawable(Color.GRAY));
                    } else {
                        internalContainer.setBackground(null);
                    }
                }
            }
        }

    }

    @NonNull
    private RelativeLayout createLayoutStatus(IGStatusViewHolder holder) {
        RelativeLayout relativeLayout = new RelativeLayout(this.getContext());
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(86), ViewGroup.LayoutParams.WRAP_CONTENT);
        relativeLayout.setLayoutParams(relativeParams);

        // Criando o FrameLayout
        FrameLayout frameLayout = new FrameLayout(this.getContext());
        frameLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Criando o LinearLayout
        LinearLayout linearLayout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams linearParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(linearParams);

        // Criando o RelativeLayout interno
        RelativeLayout internalRelativeLayout = new RelativeLayout(this.getContext());
        RelativeLayout.LayoutParams internalRelativeParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(56), Utils.dipToPixels(56));
        internalRelativeLayout.setLayoutParams(internalRelativeParams);

        // Adicionando os elementos ao RelativeLayout interno
        var contactPhoto = (ImageView) XposedHelpers.newInstance(this.clazzImageStatus, this.getContext());
        RelativeLayout.LayoutParams photoParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contactPhoto.setLayoutParams(photoParams);
        contactPhoto.setPadding(Utils.dipToPixels(3.5F), Utils.dipToPixels(3.5F), Utils.dipToPixels(3.5F), Utils.dipToPixels(3.5F));
        contactPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        contactPhoto.setImageDrawable(DesignUtils.getDrawableByName("avatar_contact"));
        holder.igStatusContactPhoto = contactPhoto;
        holder.internalContainer = internalRelativeLayout;
        contactPhoto.setClickable(false);
        XposedHelpers.callMethod(contactPhoto, "setBorderSize", (float) Utils.dipToPixels(2.5f));
        XposedHelpers.callMethod(contactPhoto, "setCornerRadius", (float) Utils.dipToPixels(80f));
        XposedHelpers.setObjectField(contactPhoto, "A02", Color.GRAY);
        XposedHelpers.setObjectField(contactPhoto, "A03", DesignUtils.getUnSeenColor());

        RelativeLayout addBtnRelativeLayout = new RelativeLayout(this.getContext());
        addBtnRelativeLayout.setBackgroundColor(Color.TRANSPARENT);
        RelativeLayout.LayoutParams addBtnParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        addBtnRelativeLayout.setLayoutParams(addBtnParams);
        addBtnRelativeLayout.setVisibility(View.GONE);

        ImageView iconImageView = new ImageView(this.getContext());
        RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(20), Utils.dipToPixels(20));
        iconImageView.setLayoutParams(iconParams);
        if (cacheIcon == null) {
            var icon = DesignUtils.getDrawableByName("my_status_add_button_new");
            if (icon != null) {
                cacheIcon = DesignUtils.generatePrimaryColorDrawable(icon);
            }
        }
        if (cacheIcon == null) {
            cacheIcon = getGreenPlusIcon();
        }
        iconImageView.setImageDrawable(cacheIcon);
        iconImageView.setBackgroundColor(Color.TRANSPARENT);
        addBtnRelativeLayout.addView(iconImageView);
        holder.addButton = addBtnRelativeLayout;


        internalRelativeLayout.addView(contactPhoto);
        internalRelativeLayout.addView(addBtnRelativeLayout);

        TextView contactName = new TextView(this.getContext());
        contactName.setEllipsize(TextUtils.TruncateAt.END);
        contactName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contactName.setLayoutParams(nameParams);
        contactName.setText("Name");
        contactName.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        contactName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        contactName.setTypeface(Typeface.DEFAULT_BOLD);
        contactName.setMaxLines(1);
        holder.igStatusContactName = contactName;
        linearLayout.addView(internalRelativeLayout);
        linearLayout.addView(contactName);
        frameLayout.addView(linearLayout);
        relativeLayout.addView(frameLayout);
        return relativeLayout;
    }

    private static android.view.View findViewByClassName(android.view.View root, String className) {
        if (root == null) return null;
        if (root.getClass().getName().contains(className)) {
            return root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.View child = vg.getChildAt(i);
                android.view.View found = findViewByClassName(child, className);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasOwnActiveStatus() {
        for (Object item : itens) {
            if (item == null) continue;
            try {
                Object jidObj = IGStatusViewHolder.findJidObject(item, 3);
                if (jidObj != null) {
                    var userJid = new FMessageWpp.UserJid(jidObj);
                    if (userJid.isStatus() || String.valueOf(jidObj).contains("status@broadcast")) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static Drawable getLetterAvatar(String name) {
        int size = Utils.dipToPixels(56);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        
        android.graphics.Paint circlePaint = new android.graphics.Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setColor(0xFF4A1525);
        canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f, circlePaint);
        
        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(android.graphics.Color.WHITE);
        textPaint.setTextSize(Utils.dipToPixels(24));
        textPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        
        String firstLetter = "O";
        if (name != null && !name.trim().isEmpty()) {
            firstLetter = name.trim().substring(0, 1).toUpperCase();
        }
        
        float x = size / 2.0f;
        float y = (size / 2.0f) - ((textPaint.descent() + textPaint.ascent()) / 2.0f);
        canvas.drawText(firstLetter, x, y, textPaint);
        
        return new android.graphics.drawable.BitmapDrawable(Utils.getApplication().getResources(), bitmap);
    }

    public static Drawable getGreenPlusIcon() {
        int size = Utils.dipToPixels(20);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        
        android.graphics.Paint circlePaint = new android.graphics.Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setColor(0xFF00E676);
        canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f, circlePaint);
        
        android.graphics.Paint plusPaint = new android.graphics.Paint();
        plusPaint.setAntiAlias(true);
        plusPaint.setColor(android.graphics.Color.WHITE);
        plusPaint.setStrokeWidth(Utils.dipToPixels(2.0f));
        plusPaint.setStyle(android.graphics.Paint.Style.STROKE);
        plusPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        
        float padding = Utils.dipToPixels(5);
        canvas.drawLine(padding, size / 2.0f, size - padding, size / 2.0f, plusPaint);
        canvas.drawLine(size / 2.0f, padding, size / 2.0f, size - padding, plusPaint);
        
        return new android.graphics.drawable.BitmapDrawable(Utils.getApplication().getResources(), bitmap);
    }

    private static boolean clickViewByContentDescription(android.view.View root, String descSnippet) {
        if (root == null) return false;
        CharSequence desc = root.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains(descSnippet.toLowerCase())) {
            root.performClick();
            return true;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (clickViewByContentDescription(vg.getChildAt(i), descSnippet)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static android.view.View findBottomNavigationMenuView(android.view.View root) {
        if (root == null) return null;
        String name = root.getClass().getName();
        if (name.contains("BottomNavigationMenuView") || name.contains("BottomNavigation") || name.contains("BottomBar")) {
            return root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.View found = findBottomNavigationMenuView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    public static Drawable createRingDrawable(int color) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gd.setColor(Color.TRANSPARENT);
        gd.setStroke(Utils.dipToPixels(2.5f), color);
        return gd;
    }
}
