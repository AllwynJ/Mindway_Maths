package com.elsco.mindwaymaths.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elsco.mindwaymaths.R

@Composable fun Page(content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
}
@Composable fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium) }
@Composable fun ActionCard(title: String, subtitle: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Card(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            icon?.let { Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(it, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
            } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
        }
    }
}
@Composable fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = .78f))
    }
}
@Composable fun Notice(text: String, retry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            retry?.let { TextButton(it) { Text(stringResource(R.string.retry)) } }
        }
    }
}
@Composable fun ContentStatus(loading: Boolean, empty: Boolean, error: Boolean, retry: () -> Unit) {
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (error) Notice(stringResource(R.string.network_error), retry)
    else if (empty && !loading) Notice(stringResource(R.string.empty_content), retry)
}
fun String.label(): String = replace('_', ' ').replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
fun Double.percent(): String = "${kotlin.math.round(this).toInt()}%"
fun Long.clock(): String = "%02d:%02d".format(this / 60_000, this / 1_000 % 60)
