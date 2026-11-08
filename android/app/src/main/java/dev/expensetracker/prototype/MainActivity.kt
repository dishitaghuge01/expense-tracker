package dev.expensetracker.prototype

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.expensetracker.model.ProbePermissionState
import dev.expensetracker.model.SyntheticDemo
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val permissions = listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    private var permissionStates by mutableStateOf<List<ProbePermissionState>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { MaterialTheme { PrototypeScreen() } }
    }

    override fun onResume() {
        super.onResume()
        // Refresh grant state only; never query the SMS content provider.
        if (permissionStates.isNotEmpty()) refreshPermissionStates()
    }

    private fun refreshPermissionStates() {
        permissionStates = permissions.map { permission ->
            val flags = packageManager.getPermissionFlags(permission, packageName, android.os.Process.myUserHandle())
            when {
                checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED -> ProbePermissionState.GRANTED
                flags and (PackageManager.FLAG_PERMISSION_USER_FIXED or
                    PackageManager.FLAG_PERMISSION_POLICY_FIXED or PackageManager.FLAG_PERMISSION_SYSTEM_FIXED) != 0 ->
                    ProbePermissionState.BLOCKED
                else -> ProbePermissionState.NOT_GRANTED
            }
        }
    }

    @Composable
    private fun PrototypeScreen() {
        var showDemo by rememberSaveable { mutableStateOf(false) }
        var consent by rememberSaveable { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { refreshPermissionStates() }

        Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
            Column(
                modifier = Modifier.padding(insets).padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Family Expense Tracker", style = MaterialTheme.typography.headlineMedium)
                Text("Prototype — not production · 0.0.1")
                Text("Locked. No sign-in, licence, capture or sharing is implemented. No messages are read, stored or uploaded.")
                Button(onClick = { showDemo = !showDemo }) { Text(if (showDemo) "Hide synthetic demo" else "Show synthetic demo") }
                if (showDemo) {
                    Text("Fictional example — not your payments", style = MaterialTheme.typography.titleMedium)
                    Text("Groceries: ${inr(SyntheticDemo.expensesPaise[0])}")
                    Text("Transport: ${inr(SyntheticDemo.expensesPaise[1])}")
                    Text("Gross: ${inr(SyntheticDemo.grossPaise)}")
                    Text("Linked refund: ${inr(SyntheticDemo.refundPaise)}")
                    Text("Net: ${inr(SyntheticDemo.netPaise)}")
                    Text("Demo data is static. It does not establish bank or device compatibility.")
                }
                Text("Optional SMS permission check", style = MaterialTheme.typography.titleLarge)
                Text("Android may grant, deny or restrict SMS permissions depending on the installer and device. This checks grants only, never message contents. A grant does not enable capture or prove live-alert support.")
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Text("I consent to requesting and checking SMS permissions only. No alert reading or capture.")
                Button(
                    enabled = consent,
                    onClick = {
                        refreshPermissionStates()
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                ) { Text("Run permission-only check") }
                permissions.forEachIndexed { index, permission ->
                    Text("${permission.substringAfterLast('.')}: ${permissionStates.getOrNull(index)?.label ?: ProbePermissionState.NOT_CHECKED.label}")
                }
                if (permissionStates.isNotEmpty()) {
                    OutlinedButton(onClick = { refreshPermissionStates() }) { Text("Refresh permission status") }
                    OutlinedButton(onClick = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }) { Text("Open Android app settings") }
                }
                Text("There is no capture switch in this APK. Unchecking consent stops further requests; revoke existing grants in Android settings. Synthetic demo and permission check are independent.")
                Text("Test build only. It cannot qualify a public launch. Website versus Play Store is undecided.")
            }
        }
    }

    private fun inr(paise: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
        currency = java.util.Currency.getInstance("INR")
    }.format(java.math.BigDecimal.valueOf(paise, 2))
}
