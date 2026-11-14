package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PosSeeds
import com.example.data.model.StaffUser
import com.example.ui.components.NonImeNumericKeypad
import com.example.ui.components.applyKeypadInput
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan950
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Navy950
import com.example.ui.theme.OnDark
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose950
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun StaffLockScreen(
    users: List<StaffUser>,
    initialSelectedUser: StaffUser?,
    pinError: String?,
    onClearError: () -> Unit,
    onUnlock: (StaffUser, String) -> Unit
) {
    val activeUsers = remember(users) {
        users.filter { it.active }.ifEmpty { PosSeeds.DEFAULT_USERS }
    }
    val sellerUser = remember(activeUsers) {
        activeUsers.find { it.role != "admin" } ?: PosSeeds.DEFAULT_USERS.first { it.role != "admin" }
    }
    val adminUser = remember(activeUsers) {
        activeUsers.find { it.role == "admin" } ?: PosSeeds.DEFAULT_USERS.first { it.role == "admin" }
    }

    var selectedUser by remember(activeUsers, initialSelectedUser) {
        mutableStateOf(
            if (initialSelectedUser?.role == "admin") adminUser else adminUser
        )
    }
    var pinBuffer by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate950
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Navy950,
                    border = BorderStroke(2.dp, Amber500),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Choix Profil",
                            tint = Amber400,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CERAMICA POS",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Slate100,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Vendeur sans PIN • Administrateur par Code PIN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cyan400,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Direct 1-tap Seller Entry (No PIN)
                Button(
                    onClick = { onUnlock(sellerUser, "") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("lock_user_${sellerUser.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Accès Vendeur (Vente & Stock) — Sans PIN",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black
                    )
                }

                // Profile Selector Row (2 Users)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(sellerUser, adminUser).forEach { user ->
                        val isSel = selectedUser.id == user.id
                        val isAdm = user.role == "admin"
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (!isAdm) {
                                        onUnlock(user, "")
                                    } else {
                                        selectedUser = user
                                        pinBuffer = ""
                                        onClearError()
                                    }
                                }
                                .testTag("lock_card_${user.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSel) Navy950 else Slate900,
                            border = BorderStroke(
                                if (isSel) 2.dp else 1.dp,
                                if (isSel) Amber500 else Slate700
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (isAdm) Icons.Default.AdminPanelSettings else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (isAdm) Amber400 else Emerald400,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Slate100,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = if (isAdm) "Code PIN requis" else "Accès direct",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isAdm) Amber400 else Cyan400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Admin PIN entry section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Slate900,
                    border = BorderStroke(1.5.dp, if (pinError != null) Rose400 else Slate700)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Code PIN Administrateur",
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate100,
                            fontWeight = FontWeight.Black
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) { idx ->
                                val filled = idx < pinBuffer.length
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) Amber400 else Slate850)
                                )
                            }
                        }
                        if (pinError != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Rose950
                            ) {
                                Text(
                                    text = pinError,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Rose400,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                NonImeNumericKeypad(
                    onKeyPress = { key ->
                        onClearError()
                        val (next, _) = applyKeypadInput(
                            current = pinBuffer,
                            key = key,
                            allowDecimal = false,
                            isPristine = false
                        )
                        val trimmed = next.take(4)
                        pinBuffer = trimmed
                        if (trimmed.length == 4) {
                            onUnlock(adminUser, trimmed)
                            if (trimmed != adminUser.pin) {
                                pinBuffer = ""
                            }
                        }
                    },
                    allowDecimal = false,
                    onValidate = {
                        onUnlock(adminUser, pinBuffer)
                        if (pinBuffer != adminUser.pin) pinBuffer = ""
                    },
                    validateLabel = "Accès Administrateur",
                    accentColor = Amber500
                )
            }
        }
    }
}
