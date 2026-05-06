package com.example.sensorycontrol.auth;

import android.content.Context;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

/**
 * Manages Firebase Authentication and user profile operations
 */
public class AuthManager {
    
    private static AuthManager instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;
    
    private AuthManager() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }
    
    public static synchronized AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }
    
    /**
     * Initialize Google Sign-In
     */
    public void initializeGoogleSignIn(Context context, String webClientId) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        
        googleSignInClient = GoogleSignIn.getClient(context, gso);
    }
    
    /**
     * Get Google Sign-In client
     */
    public GoogleSignInClient getGoogleSignInClient() {
        return googleSignInClient;
    }
    
    /**
     * Sign in with Google
     */
    public void signInWithGoogle(Task<GoogleSignInAccount> task, AuthCallback callback) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                firebaseAuthWithGoogle(account.getIdToken(), callback);
            } else {
                callback.onFailure("Google Sign-In failed");
            }
        } catch (ApiException e) {
            Timber.e(e, "Google sign in failed");
            callback.onFailure("Google Sign-In failed: " + e.getMessage());
        }
    }
    
    /**
     * Authenticate with Firebase using Google credentials
     */
    private void firebaseAuthWithGoogle(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        // Check if profile exists, create if not
                        checkAndCreateProfile(user, callback);
                    }
                } else {
                    Timber.e(task.getException(), "Firebase auth with Google failed");
                    callback.onFailure(task.getException() != null ? 
                        task.getException().getMessage() : "Authentication failed");
                }
            });
    }
    
    /**
     * Check if user profile exists, create if not
     */
    private void checkAndCreateProfile(FirebaseUser user, AuthCallback callback) {
        db.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (!documentSnapshot.exists()) {
                    // Create new profile for Google user
                    String name = user.getDisplayName() != null ? user.getDisplayName() : "User";
                    String email = user.getEmail() != null ? user.getEmail() : "";
                    createUserProfile(user.getUid(), name, email, callback);
                } else {
                    // Profile exists, just callback success
                    callback.onSuccess();
                }
            })
            .addOnFailureListener(e -> {
                Timber.e(e, "Failed to check user profile");
                callback.onFailure("Failed to verify profile: " + e.getMessage());
            });
    }
    
    /**
     * Check if user is currently logged in
     */
    public boolean isUserLoggedIn() {
        return auth.getCurrentUser() != null;
    }
    
    /**
     * Get current user
     */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }
    
    /**
     * Sign up new user with email and password
     */
    public void signUp(String email, String password, String name, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        createUserProfile(user.getUid(), name, email, callback);
                    }
                } else {
                    Timber.e(task.getException(), "Sign up failed");
                    callback.onFailure(task.getException() != null ? 
                        task.getException().getMessage() : "Sign up failed");
                }
            });
    }
    
    /**
     * Sign in existing user
     */
    public void signIn(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Timber.d("Sign in successful");
                    callback.onSuccess();
                } else {
                    Timber.e(task.getException(), "Sign in failed");
                    callback.onFailure(task.getException() != null ? 
                        task.getException().getMessage() : "Sign in failed");
                }
            });
    }
    
    /**
     * Sign out current user
     */
    public void signOut(Context context) {
        auth.signOut();
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
        }
        Timber.d("User signed out");
    }
    
    /**
     * Create user profile in Firestore
     */
    private void createUserProfile(String userId, String name, String email, AuthCallback callback) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("userId", userId);
        userProfile.put("name", name);
        userProfile.put("email", email);
        userProfile.put("childAge", 0);
        userProfile.put("createdAt", System.currentTimeMillis());
        
        db.collection("users").document(userId)
            .set(userProfile)
            .addOnSuccessListener(aVoid -> {
                Timber.d("User profile created");
                callback.onSuccess();
            })
            .addOnFailureListener(e -> {
                Timber.e(e, "Failed to create user profile");
                callback.onFailure("Failed to create profile: " + e.getMessage());
            });
    }
    
    /**
     * Get user profile from Firestore
     */
    public void getUserProfile(ProfileCallback callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onFailure("No user logged in");
            return;
        }
        
        db.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    callback.onSuccess(documentSnapshot.getData());
                } else {
                    callback.onFailure("Profile not found");
                }
            })
            .addOnFailureListener(e -> {
                Timber.e(e, "Failed to get user profile");
                callback.onFailure(e.getMessage());
            });
    }
    
    /**
     * Update user profile
     */
    public void updateUserProfile(Map<String, Object> updates, AuthCallback callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onFailure("No user logged in");
            return;
        }
        
        db.collection("users").document(user.getUid())
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                Timber.d("Profile updated");
                callback.onSuccess();
            })
            .addOnFailureListener(e -> {
                Timber.e(e, "Failed to update profile");
                callback.onFailure(e.getMessage());
            });
    }
    
    // Callbacks
    public interface AuthCallback {
        void onSuccess();
        void onFailure(String error);
    }
    
    public interface ProfileCallback {
        void onSuccess(Map<String, Object> profile);
        void onFailure(String error);
    }
}
