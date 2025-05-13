package workon.calldefenceapp

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import workon.calldefenceapp.ui.theme.CallDefenceAppTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import android.app.AlertDialog
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.VideoProfile

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_CODE_SET_DEFAULT_DIALER = 123
    }

    // Register the permissions launcher
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // All permissions granted, now try to set as default dialer
            requestDefaultDialerRole()
        } else {
            Toast.makeText(this, "All permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    // Register the default dialer launcher
    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Successfully set as default dialer", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to set as default dialer", Toast.LENGTH_LONG).show()
            // Show dialog explaining why default dialer access is needed
            showDefaultDialerExplanationDialog()
        }
    }

    private fun showDefaultDialerExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Default Dialer Permission Required")
            .setMessage("This app needs to be set as the default dialer to handle your calls. Please set it as the default dialer in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openDefaultDialerSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDefaultDialerSettings() {
        try {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            defaultDialerLauncher.launch(intent)
        } catch (e: Exception) {
            // If direct intent fails, open system settings
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (Q) and above
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    defaultDialerLauncher.launch(intent)
                }
            }
        } else {
            // For Android 9 (Pie) and below
            val telecomManager = getSystemService(TelecomManager::class.java)
            if (packageName != telecomManager.defaultDialerPackage) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                defaultDialerLauncher.launch(intent)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.MANAGE_OWN_CALLS,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else null
        ).filterNotNull()

        val notGrantedPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            permissionsLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            // If all permissions are granted, request default dialer role
            requestDefaultDialerRole()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isIncomingCall = intent.getBooleanExtra("incoming_call", false)
        val phoneNumber = intent.getStringExtra("phone_number") ?: "Unknown"
        
        enableEdgeToEdge()
        setContent {
            CallDefenceAppTheme {
                if (isIncomingCall) {
                    IncomingCallScreen(
                        callerName = getContactName(phoneNumber),
                        phoneNumber = phoneNumber,
                        onAcceptCall = {
                            CallManager.acceptCall()
                            finish()
                        },
                        onRejectCall = {
                            CallManager.rejectCall()
                            finish()
                        }
                    )
                } else {
                    CallApp()
                }
            }
        }
    }

    private fun getContactName(phoneNumber: String): String {
        var contactName = "Unknown"
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor = contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    contactName = it.getString(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return contactName
    }
}

@Composable
fun CallApp() {
    var selectedTab by remember { mutableStateOf(0) }
    var phoneNumber by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Dial Pad") },
                    icon = { Icon(painterResource(id = android.R.drawable.ic_menu_call), contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Call Logs") },
                    icon = { Icon(painterResource(id = android.R.drawable.ic_menu_recent_history), contentDescription = null) }
                )
            }

            // Content based on selected tab
            when (selectedTab) {
                0 -> DialPadScreen(
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = { phoneNumber = it },
                    onCallClick = { /* Handle call action */ }
                )
                1 -> CallLogsScreen()
            }
        }
    }
}

@Composable
fun DialPadScreen(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onCallClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Phone number display
        TextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Dial pad grid
        DialPadGrid(onNumberClick = { onPhoneNumberChange(phoneNumber + it) })

        Spacer(modifier = Modifier.height(16.dp))

        // Call button
        FloatingActionButton(
            onClick = onCallClick,
            containerColor = Color.Green,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_call),
                contentDescription = "Make Call",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun DialPadGrid(onNumberClick: (String) -> Unit) {
    val numbers = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "*", "0", "#"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in numbers.indices step 3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (j in 0..2) {
                    val index = i + j
                    if (index < numbers.size) {
                        DialPadButton(
                            number = numbers[index],
                            onClick = { onNumberClick(numbers[index]) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialPadButton(number: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = number,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CallLogsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Recent Calls",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Placeholder for call logs list
        // You would typically use LazyColumn here with actual call log data
        Text(
            text = "Call logs will appear here",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CallAppPreview() {
    CallDefenceAppTheme {
        CallApp()
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String,
    phoneNumber: String,
    onAcceptCall: () -> Unit,
    onRejectCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCallHandled by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Caller Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Caller Avatar
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerName.firstOrNull()?.toString() ?: "?",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Caller Name
                Text(
                    text = callerName,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Phone Number
                Text(
                    text = phoneNumber,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Call Status
                Text(
                    text = if (isCallHandled) "Call Handled" else "Incoming Call...",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Call Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject Call Button
                FloatingActionButton(
                    onClick = {
                        if (!isCallHandled) {
                            isCallHandled = true
                            onRejectCall()
                        }
                    },
                    containerColor = Color.Red,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_call),
                        contentDescription = "Reject Call",
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(135f)
                    )
                }

                // Accept Call Button
                FloatingActionButton(
                    onClick = {
                        if (!isCallHandled) {
                            isCallHandled = true
                            onAcceptCall()
                        }
                    },
                    containerColor = Color.Green,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_call),
                        contentDescription = "Accept Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IncomingCallScreenPreview() {
    CallDefenceAppTheme {
        IncomingCallScreen(
            callerName = "John Doe",
            phoneNumber = "+1 234 567 8900",
            onAcceptCall = {},
            onRejectCall = {},
        )
    }
}