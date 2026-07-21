package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.Contact
import com.example.data.EventLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LifeLinkApp(viewModel: LifeLinkViewModel) {
    val context = LocalContext.current
    val setupCompleted by viewModel.setupCompleted.collectAsState()
    val alertState by viewModel.alertState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var permissionsReady by remember { mutableStateOf(hasCorePermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsReady = hasCorePermissions(context)
        if (permissionsReady) viewModel.ensureMonitoringStarted()
    }
    val requestPermissions = {
        permissionLauncher.launch(requiredPermissions())
    }

    LaunchedEffect(setupCompleted) {
        if (setupCompleted) {
            permissionsReady = hasCorePermissions(context)
            if (permissionsReady) viewModel.ensureMonitoringStarted() else requestPermissions()
        }
    }

    if (!setupCompleted) {
        StartupSetupDialog(onComplete = viewModel::completeSetup)
    }
    if (alertState == 1) {
        PreAlertDialog(onDismiss = { viewModel.reportSurvival("사전 알림에서 무사 확인") })
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    label = { Text("안심") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("연락처") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
                    label = { Text("기록") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> DashboardTab(viewModel, permissionsReady, requestPermissions)
                1 -> ContactsTab(viewModel)
                else -> LogsTab(viewModel)
            }
        }
    }
}

@Composable
private fun DashboardTab(
    viewModel: LifeLinkViewModel,
    permissionsReady: Boolean,
    requestPermissions: () -> Unit
) {
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val lastActivity by viewModel.lastSensingMsg.collectAsState()
    val alertState by viewModel.alertState.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val monitorHours by viewModel.monitorHours.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFD63C62), modifier = Modifier.size(30.dp))
                    Text("  라이프링크", fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                IconButton(
                    onClick = {
                        if (permissionsReady) viewModel.toggleMonitoring() else requestPermissions()
                    },
                    modifier = Modifier.testTag("toggle_monitoring_button")
                ) {
                    Icon(
                        if (isMonitoring) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = if (isMonitoring) "모니터링 중지" else "모니터링 시작",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        if (!permissionsReady) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("필수 권한 확인이 필요합니다", fontWeight = FontWeight.Bold)
                        Text("활동 감지, 알림, 긴급 문자 권한이 없으면 안전 모니터링을 시작할 수 없습니다.")
                        TextButton(onClick = requestPermissions) { Text("권한 확인") }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (alertState) {
                        1 -> Color(0xFFFFF3CD)
                        2 -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            !isMonitoring -> "모니터링 일시 중지"
                            alertState == 1 -> "안전 확인 대기 중"
                            alertState == 2 -> "긴급 문자 결과 확인 필요"
                            else -> "정상 모니터링 중"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("다음 안전 확인까지", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatRemaining(remainingSeconds),
                        fontSize = 42.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.testTag("remaining_time_text")
                    )
                    Text("마지막 활동: $lastActivity", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.reportSurvival("수동 무사 확인") },
                enabled = isMonitoring,
                modifier = Modifier.fillMaxWidth().height(64.dp).testTag("survival_report_button")
            ) {
                Text("무사합니다", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null)
                        Text("  안전 확인 시간", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(6, 12, 24, 48, 72)) { hours ->
                            FilterChip(
                                selected = monitorHours == hours,
                                onClick = { viewModel.updateMonitorHours(hours) },
                                label = { Text("${hours}시간") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null)
                    Text(
                        "  이 앱은 119나 의료기기를 대신하지 않습니다. 휴대전화 전원이 꺼지거나 앱이 강제 종료된 경우에는 감지와 문자 발송이 작동하지 않을 수 있습니다.",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactsTab(viewModel: LifeLinkViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("긴급 연락처", fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("최대 3명에게 기기의 SIM으로 긴급 문자를 보냅니다.")
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("보호자 이름") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit).take(15) },
                label = { Text("전화번호") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.addContact(name, phone)
                    if (name.isNotBlank() && phone.length >= 8 && contacts.size < 3) {
                        name = ""
                        phone = ""
                    }
                },
                enabled = contacts.size < 3,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Text(" 연락처 등록")
            }
        }
        items(contacts, key = { it.id }) { contact ->
            ContactItem(contact, onDelete = { viewModel.deleteContact(contact) })
        }
    }
}

@Composable
private fun ContactItem(contact: Contact, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(contact.name, fontWeight = FontWeight.Bold)
                Text(contact.phoneNumber)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LogsTab(viewModel: LifeLinkViewModel) {
    val eventLogs by viewModel.eventLogs.collectAsState()
    var showPrivacy by remember { mutableStateOf(false) }

    if (showPrivacy) PrivacyDialog(onDismiss = { showPrivacy = false })
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("발송 및 동작 기록", fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("문자 요청이 아니라 실제 발송·전달 콜백 결과를 기록합니다.")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("현재 전송 방식", fontWeight = FontWeight.Bold)
                    Text("기기에 설치된 SIM을 통한 자동 SMS")
                    Text("위치를 얻지 못하면 '위치 확인 불가'로 표시합니다.", fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showPrivacy = true }) { Text("개인정보 처리 안내") }
                TextButton(onClick = viewModel::clearAllLogs) { Text("기록 삭제") }
            }
            HorizontalDivider()
        }
        if (eventLogs.isEmpty()) {
            item { Text("아직 기록이 없습니다.") }
        } else {
            items(eventLogs, key = { it.id }) { LogItem(it) }
        }
    }
}

@Composable
private fun LogItem(eventLog: EventLog) {
    val time = remember(eventLog.timestamp) {
        SimpleDateFormat("MM월 dd일 HH:mm:ss", Locale.KOREA).format(Date(eventLog.timestamp))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(eventLog.message, fontWeight = FontWeight.Bold)
            Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (eventLog.detail.isNotBlank()) Text(eventLog.detail, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PreAlertDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Warning, null) },
        title = { Text("잘 계신가요?") },
        text = { Text("30분 안에 무사함을 확인하지 않으면 등록된 보호자에게 긴급 문자를 보냅니다.") },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("무사합니다", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun StartupSetupDialog(onComplete: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Favorite, null, tint = Color(0xFFD63C62)) },
        title = { Text("라이프링크 시작하기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("한 번 설정하면 휴대전화 활동을 백그라운드에서 확인합니다.")
                Text("설정 시간 동안 활동이 없으면 먼저 알림을 표시하고, 응답이 없을 때 최대 3명의 보호자에게 SIM 문자를 보냅니다.")
                Text("문자·활동 감지·알림 권한이 필요합니다. 위치 권한은 긴급 문자에 현재 위치를 포함할 때만 사용합니다.", fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = onComplete) { Text("설정 시작") }
        }
    )
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("개인정보 처리 안내") },
        text = {
            Text(
                "보호자 이름과 전화번호, 설정, 동작 기록은 이 기기에만 저장됩니다. " +
                    "긴급 시 현재 위치와 배터리 상태를 확인해 사용자가 등록한 보호자에게 SMS로 전달합니다. " +
                    "현재 앱은 Firebase, Twilio, 광고, 결제 또는 외부 클라우드로 데이터를 보내지 않습니다. " +
                    "앱 데이터는 기기 백업에서 제외됩니다. 연락처와 기록은 앱에서 삭제할 수 있고, 앱 삭제 시 모두 제거됩니다."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } }
    )
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun hasCorePermissions(context: Context): Boolean {
    val smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
        PackageManager.PERMISSION_GRANTED
    val activityGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED
    val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return smsGranted && activityGranted && notificationGranted
}

private fun formatRemaining(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remaining)
}
