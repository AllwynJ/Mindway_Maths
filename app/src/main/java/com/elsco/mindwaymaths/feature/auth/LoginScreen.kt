package com.elsco.mindwaymaths.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.dp
import com.elsco.mindwaymaths.R

@Composable fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(painterResource(R.drawable.ic_mindway), null, Modifier.size(88.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            CircularProgressIndicator(Modifier.size(26.dp))
        }
    }
}
@Composable fun LoginScreen(configured: Boolean, busy: Boolean, onSignIn: () -> Unit, onPrivacy: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_mindway), null, Modifier.size(56.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("x² + practice = progress", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("APTITUDE  /  REASONING", style = MaterialTheme.typography.labelLarge)
                Text("One concept. One question.\nOne step closer.", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text(stringResource(R.string.login_heading), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.login_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip({}, { Text("Exam-focused") }); SuggestionChip({}, { Text("Offline practice") })
        }
        Spacer(Modifier.weight(1f, fill = false))
        if (!configured) Text(stringResource(R.string.configuration_required), style = MaterialTheme.typography.bodyMedium)
        Button(onSignIn, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = configured && !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.google_continue))
        }
        TextButton(onPrivacy, Modifier.align(Alignment.CenterHorizontally)) { Text("Privacy · Terms · Data deletion") }
    }
}
