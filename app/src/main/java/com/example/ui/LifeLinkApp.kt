package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Contact
import com.example.data.EventLog
import com.example.data.LifeLinkRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LifeLinkApp(viewModel: LifeLinkViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    // Android 13+ Notification Permission, Fine Location, and SEND_SMS permission checks
    val postNotificationsPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
    } else null

    val locationPermission = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val smsPermission = rememberPermissionState(permission = Manifest.permission.SEND_SMS)

    LaunchedEffect(Unit) {
        postNotificationsPermission?.launchPermissionRequest()
        locationPermission.launchPermissionRequest()
        smsPermission.launchPermissionRequest()
    }

    // Collect variables from state
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val alertState by viewModel.alertState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()

    // 1단계 Pre-Alert 경보 다이얼로그 발생시키기
    if (alertState == 1) {
        PreAlertDialog(
            onDismiss = { viewModel.reportSurvival("1단계 화면 경고수용 및 해제") }
        )
    }

    Scaffold(
        bottomBar = {
            Column {
                if (!isPremium) {
                    SimulatedAdBanner()
                }
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Healing, contentDescription = "안심 서치") },
                        label = { Text("안심 대시보드", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("nav_dashboard_tab")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.PeopleAlt, contentDescription = "보호자 등록") },
                        label = { Text("보호자 설정", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("nav_contacts_tab")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.AutoMirrored.Default.ListAlt, contentDescription = "안심 로그") },
                        label = { Text("SMS 및 기록", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("nav_logs_tab")
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(viewModel)
                1 -> ContactsTab(viewModel)
                2 -> LogsTab(viewModel)
            }
        }
    }
}

@Composable
fun DashboardTab(viewModel: LifeLinkViewModel) {
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val lastSensingMsg by viewModel.lastSensingMsg.collectAsState()
    val alertState by viewModel.alertState.collectAsState()
    val sensorPulse by viewModel.sensorPulse.collectAsState()
    val isSimulationMode by viewModel.isSimulationMode.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()

    // Dynamic Pulsing scaling animator for pulse activity
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (alertState > 0) 1.25f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (alertState > 0) 450 else 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Title Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Logo",
                    tint = Color(0xFFE91E63),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "라이프링크 (LifeLink)",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = { viewModel.toggleMonitoring() },
                modifier = Modifier.testTag("toggle_monitoring_button")
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = "안심 중지",
                    tint = if (isMonitoring) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Monitoring State & Remaining Time Visual Circle Panel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Heart beat radar animation
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                        .background(
                            color = when (alertState) {
                                0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                1 -> Color(0xFFFF9800).copy(alpha = 0.25f)
                                else -> Color(0xFFF44336).copy(alpha = 0.35f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(145.dp)
                            .background(
                                color = when (alertState) {
                                    0 -> MaterialTheme.colorScheme.primary
                                    1 -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (alertState) {
                                    0 -> if (sensorPulse) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Default.SentimentVerySatisfied
                                    1 -> Icons.Default.NewReleases
                                    else -> Icons.Default.Dangerous
                                },
                                contentDescription = "상태아이콘",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(42.dp)
                                    .animateContentSize()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (alertState) {
                                    0 -> "안심 모니터링"
                                    1 -> "확인 대기"
                                    else -> "위급상황 발령"
                                },
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Time Indicator String Label
                val hours = remainingSeconds / 3600
                val minutes = (remainingSeconds % 3600) / 60
                val seconds = remainingSeconds % 60
                val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                Text(
                    text = "자동 안심 확인까지 남은 시간",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedTime,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (alertState > 0) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.testTag("remaining_time_text")
                )

                if (isSimulationMode) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚡ 가속 시뮬레이션 테스트가 가동 중입니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Central "I'm okay" button
        Button(
            onClick = { viewModel.reportSurvival("수동 버튼 터치") },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .testTag("survival_report_button"),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PanTool,
                    contentDescription = "Hand Sign",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "지금 잘 있어요 (수동 생존신고)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Activity Detection Status Sub Indicator Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (sensorPulse) MaterialTheme.colorScheme.primary else Color.Gray,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "스마트 움직임 감지 지시계",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lastSensingMsg,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Emergency Crash Test Button (High Visibility, Low Fatigue Accent)
        OutlinedButton(
            onClick = { viewModel.startQuickSimulation() },
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("quick_simulator_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = "Test", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "긴급 상황 10초 모의 체험해보기",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ContactsTab(viewModel: LifeLinkViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    val monitorHours by viewModel.monitorHours.collectAsState()
    val context = LocalContext.current

    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    // Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(contactUri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    var name = ""
                    var phone = ""
                    
                    val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        name = c.getString(nameIndex)
                    }

                    val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                    if (idIndex >= 0) {
                        val contactId = c.getString(idIndex)
                        val phoneCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(contactId),
                            null
                        )
                        phoneCursor?.use { pCursor ->
                            if (pCursor.moveToFirst()) {
                                val numberIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numberIndex >= 0) {
                                    phone = pCursor.getString(numberIndex)
                                }
                            }
                        }
                    }
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        viewModel.addContact(name, phone)
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Safe Hours setting logic section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "시간 가동 감지 간격 설정",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "설정한 시간 동안 기기 사용(충전, 화면 켜짐, 걸음)이 잡히지 않으면 알림 프로세스가 기동됩니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "현재 안심 주기: ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${monitorHours}시간",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = monitorHours.toFloat(),
                        onValueChange = { viewModel.updateMonitorHours(it.toInt()) },
                        valueRange = 6f..72f,
                        steps = 11, // Increment logic bounds
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("monitor_hours_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("최소 6시간", fontSize = 11.sp, color = Color.Gray)
                        Text("최대 72시간", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        // Contact register panel
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "긴급 보호자 연락처 등록",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Button(
                            onClick = { contactPickerLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.ContactPhone, contentDescription = "Import", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("주소록 추가", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        label = { Text("보호자 이름") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_contact_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newContactPhone,
                        onValueChange = { newContactPhone = it },
                        label = { Text("전화번호 (예: 010-1234-5678)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_contact_phone"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (newContactName.isNotBlank() && newContactPhone.isNotBlank()) {
                                viewModel.addContact(newContactName, newContactPhone)
                                newContactName = ""
                                newContactPhone = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_contact_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("직접 입력 추가", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Selected protectors list header
        item {
            Text(
                text = "현재 등록된 안심 보호자 (${contacts.size}/3명)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // Contact lists dynamic load
        if (contacts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = "Empty",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "등록된 보호자가 없습니다. 연락처를 등록해주세요.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(contacts) { contact ->
                ContactItem(contact = contact, onDelete = { viewModel.deleteContact(contact) })
            }
        }
    }
}

@Composable
fun ContactItem(contact: Contact, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Contact Logo",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(contact.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(contact.phoneNumber, fontSize = 14.sp, color = Color.Gray)
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_contact_button_${contact.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun LogsTab(viewModel: LifeLinkViewModel) {
    val eventLogs by viewModel.eventLogs.collectAsState()
    val smsMode by viewModel.smsMode.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()

    var showIosArchitectureDetail by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SaaS Angel Sponsorship & Goodwill Center (Monetization Hub)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isPremium) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.5.dp, if (isPremium) Color(0xFF81C784) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPremium) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Sponsor Icon",
                                tint = if (isPremium) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "라이프링크 엔젤 후원 (스폰서십)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isPremium) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPremium) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isPremium) "엔젤 스폰서 활성" else "광고 지원 일반형",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• 100% 기능 개방 정책: 독거어르신 수호 등 인명안전을 최우선으로 하므로 전송망 및 핵심 알림 기능에 기능 잠금이 일절 없습니다.\n" +
                                "• 지속가능 기여: 월 1,900원의 소액 엔젤 후원 시, 무안심 클라우드 서버망 확장 및 광고가 완전히 제거된 순정 안전망 서비스를 만나볼 수 있습니다.\n" +
                                "• 사회 환원 프로젝트: 모든 엔젤 후원 기금의 10%는 전국의 고독사 위기 독거 가구 안심 매칭 사회활동 연대에 전액 기부됩니다.",
                        fontSize = 12.sp,
                        color = if (isPremium) Color(0xFF2E7D32).copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = { viewModel.togglePremium() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPremium) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isPremium) "정기 후원 및 광고 제거 종료 (일반 버전 전환)" else "엔젤 정기 후원 가입 시뮬레이션하기 (월 1,900원)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // SMS Gateway Setup Header Option
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "안심 SOS 문자 발송 규격",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "구글 플레이 스토어 정책 준수를 위해 아래 전송 모드 중 하나를 선택해 기동하십시오.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 4-way SMS Engine Selector Grid Rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val firstRowModes = listOf(
                            Triple("PREMIUM", "프리미엄 클라우드", Icons.Default.Verified),
                            Triple("INTENT", "스토어 수동 연동", Icons.Default.Smartphone)
                        )
                        firstRowModes.forEach { (mode, label, icon) ->
                            val isSelected = smsMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { viewModel.updateSmsMode(mode) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val secondRowModes = listOf(
                            Triple("VIRTUAL", "가상 모의 전송", Icons.Default.PhonelinkSetup),
                            Triple("DIRECT", "로컬 백그라운드", Icons.AutoMirrored.Filled.Send)
                        )
                        secondRowModes.forEach { (mode, label, icon) ->
                            val isSelected = smsMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { viewModel.updateSmsMode(mode) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when (smsMode) {
                            "PREMIUM" -> "✅ [추천 - 구글 플레이 완벽 대응] 라이프링크 통합 클라우드를 거쳐 동작 정지 상황 감지 시 100% 무인 백그라운드 자동 알림을 전개합니다. 클라이언트에 SEND_SMS 권한 요구가 없어 플레이 스토어 무검수 즉각 통과 대상입니다."
                            "INTENT" -> "✅ [허용 - 구글 플레이 완벽 준수] 움직임 감지 부재 시 스마트폰 기본 문자 앱(Messages)에 수신처 보호자 번호와 조립된 SOS 위급 링크를 사전 주입한 채 열어드립니다. 원터치 발송으로 Play Store 규격에 100% 합치합니다."
                            "VIRTUAL" -> "✅ [추천 - 시뮬레이션 전용] 실제 통신 비용이나 SMS 권한 요청 없이, 타임라인 역사 기록에만 구동 긴급 전송 로그가 연계되어 기록되는 완전 안전 모의 훈련 규격입니다."
                            "DIRECT" -> "⚠️ [주의 - 구글 플레이 심사 반려] 단말의 직접 백그라운드 무인 SMS 전송(SmsManager)을 개시하여 발송합니다. 구글 플레이 스토어 출시 시 99% 즉각 승인 거절되므로 사설용/소형 자가 배포판으로 활용되는 규격입니다."
                            else -> "• 위기상황 발생 시 스마트폰 시스템 기본 메시지 앱에 연결해 드리는 연동 브릿지입니다."
                        },
                        fontSize = 12.sp,
                        color = when (smsMode) {
                            "DIRECT" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Cross-platform iOS Launch Engineering Architecture Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "iOS Icon",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "iOS 원클릭 출시 및 KMP 기술 이식도",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = { showIosArchitectureDetail = !showIosArchitectureDetail }) {
                            Text(if (showIosArchitectureDetail) "접기" else "자세히 보기", fontSize = 12.sp)
                        }
                    }

                    if (showIosArchitectureDetail) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 라이프링크 크로스플랫폼 (iOS/Android) 런칭 설계:\n\n" +
                                    "1. 비즈니스 로직 분리 (Kotlin Multiplatform):\n" +
                                    "본 앱은 Clean Architecture를 준수하여, UI와 단말 센서 감지기 외의 팩 카운트 타이머(Timer logic), 이벤트 역사 데이터베이스 구조(Room Database), 안심 공용 SaaS 클라우드 HTTP API 통신망은 'Kotlin Multiplatform Shared Module'로 묶어 iOS 상에서 100% 그대로 재사용할 수 있도록 선제 분할되었습니다.\n\n" +
                                    "2. iOS 전용 Background Activity 및 CoreMotion 통합:\n" +
                                    "iOS의 백그라운드 환경 특성상 Background Tasks를 스케줄링하고 CoreMotion 센서 락 인터페이스를 구현해, Android의 'SensorMonitor' 디스크립터에 해당하는 부분만 Swift Native API로 정밀 치환함으로써 앱 전체 코드의 80%를 공용 생산할 수 있습니다.\n\n" +
                                    "3. iOS 클라우드 알림 연동:\n" +
                                    "동작 미감지 긴급 구조 발생 시, 디바이스의 무인 통신망 제어가 엄격한 Apple iOS 생태계 하에서도 본 프리미엄 클라우드 API를 사용하기에 어떠한 로컬 문자 거절 사유 없이 순수 서버리스 API 트리거로 SMS 통보가 정상 송출됩니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Timeline log header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "안심 모니터 가동 역사",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                TextButton(onClick = { viewModel.clearAllLogs() }) {
                    Icon(Icons.Default.ClearAll, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("모두 소거", fontSize = 13.sp)
                }
            }
        }

        // Timeline Log Items
        if (eventLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Empty",
                            tint = Color.Gray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "안심 히스토리 활동 결과물이 비어 있습니다.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            items(eventLogs) { log ->
                LogItem(eventLog = log)
            }
        }
    }
}

@Composable
fun LogItem(eventLog: EventLog) {
    val date = Date(eventLog.timestamp)
    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    val dateString = formatter.format(date)

    val colorInfo = when (eventLog.type) {
        "SAFETY_INIT" -> Pair(Color(0xFF2196F3), Icons.Default.VerifiedUser) // Info blue
        "SENSOR_RESET" -> Pair(Color(0xFF4CAF50), Icons.Default.CheckCircle) // Active green
        "ALERT_WARNING" -> Pair(Color(0xFFFF9800), Icons.Default.Warning) // Warning orange
        "SMS_SENT" -> Pair(Color(0xFF9C27B0), Icons.AutoMirrored.Filled.SendToMobile) // Safe purple
        "SMS_FAILED" -> Pair(Color(0xFFF44336), Icons.Default.MobileOff) // Red Alert
        else -> Pair(Color.Gray, Icons.Default.History)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("log_item_${eventLog.type}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colorInfo.first.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = colorInfo.second,
                    contentDescription = eventLog.type,
                    tint = colorInfo.first,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (eventLog.type) {
                            "SAFETY_INIT" -> "안심가동구조"
                            "SENSOR_RESET" -> "체온보존생존"
                            "ALERT_WARNING" -> "비활동경고"
                            "SMS_SENT" -> "보호자SMS전송"
                            "SMS_FAILED" -> "SMS전송장애"
                            else -> "모드전환"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorInfo.first
                    )
                    Text(
                        text = dateString,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = eventLog.message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (eventLog.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = eventLog.detail,
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .background(Color.LightGray.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(6.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

// 1단계 Pre-Alert "잘 계신가요?" Fullscreen High Visibility Alert Dialog
@Composable
fun PreAlertDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = { /* Cannot dismiss without confirmation button to enforce state */ }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = "Alert",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "잘 계신가요?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "장시간 기기 활동 움직임이 없어 안전 확인 대기 중입니다.\n\n아래 '무사합니다'를 탭하거나 폰을 가볍게 흔들면 알림이 즉시 취소됩니다.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                        contentColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("dismiss_pre_alert_dialog_button")
                ) {
                    Text(
                        text = "네, 무사합니다!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun SimulatedAdBanner() {
    var adIndex by remember { mutableStateOf(0) }
    val ads = listOf(
        Pair("보건복지부", "노인맞춤돌봄서비스 상담 센터 연동 (국번없이 ☎129)"),
        Pair("라이프링크 공식기부처", "어르신 안전 수호 무인 긴급구조대 후원하기"),
        Pair("보건복지 공익광고", "고독사 예방 스마트 안심 체크인 앱 설치 지원사업"),
        Pair("라이프링크 스마트", "무자각 안전 감지 전용 스마트 홈 안심패키지 최대 40% 특가")
    )

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000)
            adIndex = (adIndex + 1) % ads.size
        }
    }

    val (sponsor, title) = ads[adIndex]

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(55.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AD badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "스폰서: $sponsor | 안전망 100% 무료 유지 기부 필터",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Ad Arrow",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

