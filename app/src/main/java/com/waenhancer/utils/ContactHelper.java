package com.waenhancer.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.Manifest;
import android.content.pm.PackageManager;
import com.waenhancer.xposed.core.db.DelMessageStore;

public class ContactHelper {

    public static String normalizeJid(String jid) {
        if (jid == null) return null;
        String clean = jid.trim();
        String server = "s.whatsapp.net";
        if (clean.contains("@")) {
            String[] parts = clean.split("@");
            clean = parts[0];
            server = parts[1];
        }
        if (clean.contains(":")) {
            clean = clean.split(":")[0];
        }
        if (clean.contains(".")) {
            clean = clean.split("\\.")[0];
        }
        return clean + "@" + server;
    }

    public static String getContactName(Context context, String jid) {
        if (jid == null) return null;

        String cleanJid = normalizeJid(jid);

        boolean hasContactsPermission = context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;

        // 1. If permission is granted, query system contacts
        if (hasContactsPermission) {
            String systemName = getSystemContactName(context, cleanJid);
            if (systemName != null && !systemName.trim().isEmpty()) {
                return systemName.trim();
            }
        }

        // 2. Fallback to locally synced WhatsApp contacts database
        try {
            String waName = DelMessageStore.getInstance(context)
                    .getWhatsAppContactName(cleanJid);
            if (waName != null && !waName.trim().isEmpty()) {
                return waName.trim();
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static String getSystemContactName(Context context, String jid) {
        String phoneNumber = jid.replace("@s.whatsapp.net", "").replace("@g.us", "");
        if (phoneNumber.contains("@")) phoneNumber = phoneNumber.split("@")[0];

        // 1. Try standard PhoneLookup
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String name = cursor.getString(0);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Try with a leading '+' if it doesn't have one
        if (!phoneNumber.startsWith("+")) {
            try {
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode("+" + phoneNumber));
                String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

                try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        String name = cursor.getString(0);
                        if (name != null && !name.trim().isEmpty()) {
                            return name;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 3. Fallback: Query CommonDataKinds.Phone directly with trailing digits match (last 7 to 9 digits)
        if (phoneNumber.length() >= 7) {
            String suffix = phoneNumber.substring(phoneNumber.length() - 7);
            try {
                Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
                String selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
                String[] selectionArgs = new String[]{"%" + suffix};

                try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        String name = cursor.getString(0);
                        if (name != null && !name.trim().isEmpty()) {
                            return name;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }
}