package com.buslive.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buslive.app.BuildConfig
import com.buslive.app.R
import com.buslive.app.data.prefs.ThemeMode
import com.buslive.app.service.SyncState
import com.buslive.app.ui.common.SectionHeader
import com.buslive.app.ui.theme.LocalDarkTheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.state.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val offline by vm.offlineProgress.collectAsStateWithLifecycle()
    val otpTest by vm.otpTest.collectAsStateWithLifecycle()
    val tomtomTest by vm.tomtomTest.collectAsStateWithLifecycle()
    var tomtomKey by rememberSaveable { mutableStateOf("") }
    var tomtomLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(settings.tomtomKey) {
        if (!tomtomLoaded) {
            tomtomKey = settings.tomtomKey
            tomtomLoaded = true
        }
    }
    val dark = LocalDarkTheme.current
    var otpUrl by rememberSaveable { mutableStateOf("") }
    var otpLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(settings.otpBaseUrl) {
        if (!otpLoaded) {
            otpUrl = settings.otpBaseUrl
            otpLoaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // Language
            SectionHeader(stringResource(R.string.language))
            val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val langs = listOf(
                "" to stringResource(R.string.follow_system),
                "en" to "English",
                "he" to "עברית",
            )
            langs.forEach { (tag, label) ->
                val selected = if (tag.isEmpty()) currentTag.isEmpty() else currentTag.startsWith(tag) || (tag == "he" && currentTag.startsWith("iw"))
                OptionRow(label, selected) {
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                    )
                }
            }
            HorizontalDivider()

            // Theme
            SectionHeader(stringResource(R.string.theme))
            listOf(
                ThemeMode.SYSTEM to R.string.follow_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark,
            ).forEach { (mode, label) ->
                OptionRow(stringResource(label), settings.themeMode == mode) { vm.setTheme(mode) }
            }
            HorizontalDivider()

            // Map provider
            SectionHeader(stringResource(R.string.map_provider))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.tomtom_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tomtomKey,
                    onValueChange = { tomtomKey = it },
                    label = { Text(stringResource(R.string.tomtom_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.setTomtomKey(tomtomKey) }) { Text(stringResource(R.string.save)) }
                    OutlinedButton(onClick = { vm.testTomtom(tomtomKey) }, enabled = tomtomKey.isNotBlank()) { Text(stringResource(R.string.test)) }
                    TextButton(onClick = { tomtomKey = ""; vm.setTomtomKey("") }) { Text(stringResource(R.string.clear)) }
                }
                when (val t = tomtomTest) {
                    SettingsViewModel.OtpTest.Running -> Text(stringResource(R.string.otp_testing))
                    is SettingsViewModel.OtpTest.Ok -> Text(stringResource(R.string.tomtom_ok), color = MaterialTheme.colorScheme.primary)
                    is SettingsViewModel.OtpTest.Failed -> Text(stringResource(R.string.tomtom_failed, t.message), color = MaterialTheme.colorScheme.error)
                    SettingsViewModel.OtpTest.Idle -> Text(
                        stringResource(
                            if (settings.tomtomKey.isNotBlank() || com.buslive.app.BuildConfig.TOMTOM_API_KEY.isNotBlank()) R.string.map_using_tomtom
                            else R.string.map_using_free
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))

            // Offline data
            SectionHeader(stringResource(R.string.offline_data))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (settings.lastSyncDate != null) stringResource(
                        R.string.stops_synced,
                        settings.lastSyncDate!!,
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .format(Instant.ofEpochMilli(settings.lastSyncAt).atZone(com.buslive.app.domain.util.TimeUtil.ZONE)),
                    ) else stringResource(R.string.stops_not_synced),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::syncNow, enabled = sync != SyncState.RUNNING) {
                        Text(stringResource(R.string.refresh_stops_now))
                    }
                    if (sync == SyncState.RUNNING) Text(stringResource(R.string.syncing_short), style = MaterialTheme.typography.labelMedium)
                    if (sync == SyncState.FAILED) Text(stringResource(R.string.sync_failed_short), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.offline_map_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                if (offline.running) {
                    LinearProgressIndicator(progress = { offline.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.offline_progress, offline.percent, offline.bytes / (1024 * 1024)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (offline.done) Text(
                    stringResource(R.string.offline_done, offline.bytes / (1024 * 1024)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                offline.error?.let { Text(stringResource(R.string.offline_error, it), color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.downloadCity(dark) }, enabled = !offline.running) {
                        Text(stringResource(R.string.download_my_area))
                    }
                    TextButton(onClick = vm::deleteOffline, enabled = !offline.running) {
                        Text(stringResource(R.string.delete_offline_map))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))

            // Trip planner server
            SectionHeader(stringResource(R.string.otp_title))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.otp_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = otpUrl,
                    onValueChange = { otpUrl = it },
                    label = { Text(stringResource(R.string.otp_url)) },
                    placeholder = { Text("http://192.168.1.10:8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.setOtpUrl(otpUrl) }) { Text(stringResource(R.string.save)) }
                    OutlinedButton(onClick = { vm.testOtp(otpUrl) }, enabled = otpUrl.isNotBlank()) { Text(stringResource(R.string.test)) }
                    TextButton(onClick = { otpUrl = ""; vm.setOtpUrl("") }) { Text(stringResource(R.string.clear)) }
                }
                when (val t = otpTest) {
                    SettingsViewModel.OtpTest.Running -> Text(stringResource(R.string.otp_testing))
                    is SettingsViewModel.OtpTest.Ok -> Text(stringResource(R.string.otp_ok, t.options), color = MaterialTheme.colorScheme.primary)
                    is SettingsViewModel.OtpTest.Failed -> Text(stringResource(R.string.otp_failed, t.message), color = MaterialTheme.colorScheme.error)
                    SettingsViewModel.OtpTest.Idle -> Text(
                        if (settings.otpBaseUrl.isBlank()) stringResource(R.string.otp_using_builtin)
                        else stringResource(R.string.otp_using, settings.otpBaseUrl),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))

            // About
            SectionHeader(stringResource(R.string.about))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.credits_text), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("BusLive ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.padding(start = 12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
