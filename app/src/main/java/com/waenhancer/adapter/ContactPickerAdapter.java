package com.waenhancer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.waenhancer.R;
import com.waenhancer.model.SelectableContact;

import java.util.ArrayList;
import java.util.List;

public class ContactPickerAdapter extends RecyclerView.Adapter<ContactPickerAdapter.ContactViewHolder> {

    public interface OnContactSelectionListener {
        boolean onContactSelected(SelectableContact contact, boolean toSelectedState);
    }

    private final List<SelectableContact> contacts = new ArrayList<>();
    private final OnContactSelectionListener listener;

    public ContactPickerAdapter() {
        this.listener = null;
    }

    public ContactPickerAdapter(OnContactSelectionListener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<SelectableContact> newContacts) {
        contacts.clear();
        contacts.addAll(newContacts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact_picker_row, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(contacts.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameView;
        private final TextView phoneView;
        private final MaterialCheckBox checkBox;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.contact_name);
            phoneView = itemView.findViewById(R.id.contact_phone);
            checkBox = itemView.findViewById(R.id.contact_checkbox);
        }

        void bind(@NonNull SelectableContact contact, OnContactSelectionListener listener) {
            nameView.setText(contact.getName());
            phoneView.setText(contact.getPhoneNumber());
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(contact.isSelected());
            View.OnClickListener toggleSelection = v -> {
                boolean newState = !contact.isSelected();
                if (listener != null) {
                    if (!listener.onContactSelected(contact, newState)) {
                        checkBox.setChecked(contact.isSelected());
                        return;
                    }
                }
                contact.setSelected(newState);
                checkBox.setChecked(newState);
            };
            itemView.setOnClickListener(toggleSelection);
            checkBox.setOnClickListener(toggleSelection);
        }
    }
}
