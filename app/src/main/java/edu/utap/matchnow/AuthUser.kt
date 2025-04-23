package edu.utap.matchnow

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.ktx.Firebase

// This is our abstract concept of a User, which is visible
// outside AuthUser. That way, client code will not change
// if we use something other than Firebase for authentication
data class User(
    private val nullableName: String?,
    private val nullableEmail: String?,
    val uid: String
) {
    val name: String = nullableName ?: "User logged out"
    val email: String = nullableEmail ?: "User logged out"
}

const val invalidUserUid = "-1"

// Extension function to determine if user is valid
fun User.isInvalid(): Boolean {
    return uid == invalidUserUid
}

val invalidUser = User(null, null, invalidUserUid)

class AuthUser(private val registry: ActivityResultRegistry) :
    FirebaseAuth.AuthStateListener,
    DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AuthUser"
    }

    private var pendingLogin = false
    // ActivityResultLauncher that will be used to launch the Firebase AuthUI
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    // LiveData that holds the current User (invalidUser by default)
    private val liveUser = MutableLiveData<User>().apply {
        postValue(invalidUser)
    }

    init {
        // Listen to FirebaseAuth state
        Firebase.auth.addAuthStateListener(this)
    }

    fun observeUser(): LiveData<User> {
        // XXX Write me, not too difficult
        return liveUser
    }

    // Update our live data upon a change of state for our FirebaseUser
    private fun postUserUpdate(firebaseUser: FirebaseUser?) {
        // XXX Write me
        if (firebaseUser == null) {
            liveUser.postValue(invalidUser)
        } else {
            // Convert FirebaseUser to our generic User class
            val newUser = User(firebaseUser.displayName, firebaseUser.email, firebaseUser.uid)
            liveUser.postValue(newUser)
        }
    }

    // Called whenever the FirebaseAuth state changes
    override fun onAuthStateChanged(auth: FirebaseAuth) {
        postUserUpdate(auth.currentUser)
    }

    // We register our launcher here, once the Activity is actually created
    override fun onCreate(owner: LifecycleOwner) {
        signInLauncher = registry.register(
            "key",
            owner,
            FirebaseAuthUIActivityResultContract()
        ) { result ->
            Log.d(TAG, "sign in result ${result.resultCode}")
            // XXX Write me, pendingLogin
            pendingLogin = false
        }
    }

    private fun user(): FirebaseUser? {
        return Firebase.auth.currentUser
    }

    fun setDisplayName(displayName: String) {
        Log.d(TAG, "XXX profile change request")
        // If no user is logged in, return
        val user = user() ?: return

        // https://firebase.google.com/docs/auth/android/manage-users#update_a_users_profile
        val profileUpdates = userProfileChangeRequest {
            this.displayName = displayName
        }
        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Refresh our LiveData so observers see the updated display name
                    postUserUpdate(user())
                } else {
                    Log.d(TAG, "Failed to update display name", task.exception)
                }
            }
    }

    fun login() {
        // Only log in if the user is null and we are not already in a pending login
        if (user() == null && !pendingLogin) {
            Log.d(TAG, "XXX user null, log in")
            pendingLogin = true
            val providers = arrayListOf(
                AuthUI.IdpConfig.EmailBuilder().build()
            )
            // Create and launch sign-in intent
            val signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .setTheme(R.style.Theme_FirebaseAuth_NoActionBar)
                .build()
            signInLauncher.launch(signInIntent)
        }
    }

    fun logout() {
        if (user() == null) return
        Firebase.auth.signOut()
    }
}