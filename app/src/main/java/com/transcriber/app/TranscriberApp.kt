package com.transcriber.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.transcriber.app.ui.screens.HomeScreen
import com.transcriber.app.ui.screens.SettingsScreen
import com.transcriber.app.ui.screens.TranscriptScreen
import com.transcriber.app.ui.theme.TranscriberTheme
import com.transcriber.app.viewmodel.HomeViewModel
import com.transcriber.app.viewmodel.SettingsViewModel
import com.transcriber.app.viewmodel.TranscriptViewModel

@Composable
fun TranscriberApp() {
    TranscriberTheme {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "home") {

            composable("home") {
                val viewModel: HomeViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                // Navigate to TranscriptScreen when an inbox item is promoted to a Meeting
                LaunchedEffect(Unit) {
                    viewModel.navigationEvent.collect { meetingId ->
                        navController.navigate("transcript/$meetingId")
                    }
                }

                HomeScreen(
                    uiState = uiState,
                    onStartRecording = { title, language -> viewModel.startRecording(title, language) },
                    onPauseRecording = { viewModel.pauseRecording() },
                    onResumeRecording = { viewModel.resumeRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onMeetingClick = { navController.navigate("transcript/$it") },
                    onDeleteMeeting = { viewModel.deleteMeeting(it) },
                    onSettingsClick = { navController.navigate("settings") },
                    onImportFile = { uri -> viewModel.importFromUri(uri) },
                    onProcessInboxItem = { item, title, lang -> viewModel.processInboxItem(item, title, lang) },
                    onDeleteInboxItem = { id -> viewModel.deleteInboxItem(id) }
                )
            }

            composable(
                "transcript/{meetingId}",
                arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
                val viewModel: TranscriptViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                androidx.compose.runtime.LaunchedEffect(meetingId) {
                    viewModel.loadMeeting(meetingId)
                }

                TranscriptScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onStartProcessing = { viewModel.startFullProcessing(meetingId) },
                    onRenameTitle = { viewModel.renameTitle(it) },
                    onSetEditingTitle = { viewModel.setEditingTitle(it) },
                    onRenameSpeaker = { original, newName -> viewModel.renameSpeaker(original, newName) },
                    onPlayPause = { viewModel.playPause() },
                    onSeekTo = { ms -> viewModel.seekTo(ms) },
                    onShareToCloud = { viewModel.shareMeeting() }
                )
            }

            composable("settings") {
                val viewModel: SettingsViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                SettingsScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onUpdateOpenRouterKey = { viewModel.updateOpenRouterApiKey(it) },
                    onUpdateDeepgramKey = { viewModel.updateDeepgramApiKey(it) },
                    onUpdateModel = { viewModel.updateSelectedModel(it) },
                    onUpdateLanguage = { viewModel.updateSelectedLanguage(it) },
                    onUpdateSupabaseUrl = { viewModel.updateSupabaseUrl(it) },
                    onUpdateSupabaseKey = { viewModel.updateSupabaseAnonKey(it) },
                    onUpdateSyncEnabled = { viewModel.updateSupabaseSyncEnabled(it) },
                    onSave = { viewModel.saveSettings() },
                    onSyncNow = { viewModel.syncNow() }
                )
            }
        }
    }
}
