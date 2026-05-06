package com.example.sensorycontrol.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.sensorycontrol.R;
import com.example.sensorycontrol.activities.LoginActivity;
import com.example.sensorycontrol.auth.AuthManager;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {
    
    private TextView userNameText;
    private TextView userEmailText;
    private Button logoutButton;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        userNameText = view.findViewById(R.id.user_name_text);
        userEmailText = view.findViewById(R.id.user_email_text);
        logoutButton = view.findViewById(R.id.logout_button);
        
        // Load user info
        loadUserInfo();
        
        // Logout button
        logoutButton.setOnClickListener(v -> showLogoutDialog());
    }
    
    private void loadUserInfo() {
        AuthManager authManager = AuthManager.getInstance();
        FirebaseUser user = authManager.getCurrentUser();
        
        if (user != null) {
            userEmailText.setText(user.getEmail());
            
            authManager.getUserProfile(new AuthManager.ProfileCallback() {
                @Override
                public void onSuccess(java.util.Map<String, Object> profile) {
                    String name = (String) profile.get("name");
                    if (name != null) {
                        userNameText.setText(name);
                    }
                }
                
                @Override
                public void onFailure(String error) {
                    userNameText.setText("User");
                }
            });
        }
    }
    
    private void showLogoutDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout", (dialog, which) -> performLogout())
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void performLogout() {
        AuthManager.getInstance().signOut(requireContext());
        
        // Navigate to login screen
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
