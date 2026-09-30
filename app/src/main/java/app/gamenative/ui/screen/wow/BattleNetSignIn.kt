package app.gamenative.ui.screen.wow

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.gamenative.Crypto
import app.gamenative.PluviaApp
import app.gamenative.externaldisplay.IMEInputReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

object BattleNetSignIn {
    val requested = MutableStateFlow(false)

    data class Login(val email: String, val password: String)

    fun load(context: Context): Login? {
        val prefs = prefs(context)
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val sealed = prefs.getString(KEY_PASSWORD, null) ?: return null
        return try {
            Login(email, String(Crypto.decrypt(Base64.decode(sealed, Base64.NO_WRAP))))
        } catch (e: Exception) {
            Timber.w(e, "Saved Battle.net login unreadable, clearing it")
            forget(context)
            null
        }
    }

    fun save(context: Context, login: Login) {
        val sealed = Base64.encodeToString(Crypto.encrypt(login.password.toByteArray()), Base64.NO_WRAP)
        prefs(context).edit().putString(KEY_EMAIL, login.email).putString(KEY_PASSWORD, sealed).apply()
    }

    fun forget(context: Context) {
        prefs(context).edit().clear().apply()
    }

    suspend fun typeInto(receiver: IMEInputReceiver, login: Login) {
        receiver.typeText(login.email + "\t")
        delay(KEY_SETTLE_MS)
        receiver.typeText(login.password + "\n")
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "battle_net_login"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val KEY_SETTLE_MS = 300L
}

@Composable
fun BattleNetCredentialDialog(
    onDismiss: () -> Unit,
    onConfirm: (BattleNetSignIn.Login) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(DIALOG_SETTLE_MS)
        emailFocus.requestFocus()
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battle.net login") },
        text = {
            Column {
                Text("Saved encrypted on this device only. Select WoW's email field before signing in.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(emailFocus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = email.isNotBlank() && password.isNotEmpty(),
                onClick = { onConfirm(BattleNetSignIn.Login(email.trim(), password)) },
            ) { Text("Save & sign in") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BattleNetSignInHost(onBeforeTyping: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requested by BattleNetSignIn.requested.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    fun signIn(login: BattleNetSignIn.Login) {
        val receiver = PluviaApp.imeInputReceiver ?: return
        onBeforeTyping()
        scope.launch {
            delay(MENU_CLOSE_MS)
            BattleNetSignIn.typeInto(receiver, login)
        }
    }

    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        BattleNetSignIn.requested.value = false
        val saved = BattleNetSignIn.load(context)
        if (saved != null) {
            signIn(saved)
        } else {
            showDialog = true
        }
    }

    if (showDialog) {
        BattleNetCredentialDialog(
            onDismiss = { showDialog = false },
            onConfirm = { login ->
                showDialog = false
                BattleNetSignIn.save(context, login)
                signIn(login)
            },
        )
    }
}

private const val MENU_CLOSE_MS = 400L
private const val DIALOG_SETTLE_MS = 300L
