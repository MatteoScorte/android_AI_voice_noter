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
import com.transcriber.app.ui.screens.CanvaSkillEditorScreen
import com.transcriber.app.ui.screens.CanvaSkillManagerScreen
import com.transcriber.app.ui.screens.CategoryEditorScreen
import com.transcriber.app.ui.screens.CategoryManagerScreen
import com.transcriber.app.ui.screens.HomeScreen
import com.transcriber.app.ui.screens.SettingsScreen
import com.transcriber.app.ui.screens.TranscriptScreen
import com.transcriber.app.ui.theme.TranscriberTheme
import com.transcriber.app.viewmodel.CanvaSkillViewModel
import com.transcriber.app.viewmodel.CategoryViewModel
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

                LaunchedEffect(Unit) {
                    viewModel.navigationEvent.collect { meetingId ->
                        navController.navigate("transcript/$meetingId")
                    }
                }

                HomeScreen(
                    uiState = uiState,
                    onStartRecording = { title, language, folderId -> viewModel.startRecording(title, language, folderId) },
                    onPauseRecording = { viewModel.pauseRecording() },
                    onResumeRecording = { viewModel.resumeRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onMeetingClick = { navController.navigate("transcript/$it") },
                    onDeleteMeeting = { viewModel.deleteMeeting(it) },
                    onSettingsClick = { navController.navigate("settings") },
                    onCategoriesClick = { navController.navigate("category_manager") },
                    onImportFile = { uri -> viewModel.importFromUri(uri) },
                    onProcessInboxItem = { item, title, lang -> viewModel.processInboxItem(item, title, lang) },
                    onDeleteInboxItem = { id -> viewModel.deleteInboxItem(id) },
                    onSelectFolder = { viewModel.setSelectedFolder(it) },
                    onCreateFolder = { name, color -> viewModel.createFolder(name, color) },
                    onUpdateFolder = { id, name, color -> viewModel.updateFolder(id, name, color) },
                    onDeleteFolder = { viewModel.deleteFolder(it) },
                    onAssignFolder = { meetingId, folderId -> viewModel.assignFolderToMeeting(meetingId, folderId) },
                    onCanvaSkillsClick = { navController.navigate("canva_skill_manager") }
                )
            }

            composable(
                "transcript/{meetingId}",
                arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
                val viewModel: TranscriptViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(meetingId) {
                    viewModel.loadMeeting(meetingId)
                }

                TranscriptScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onStartProcessing = { category -> viewModel.startFullProcessing(meetingId, category) },
                    onRenameTitle = { viewModel.renameTitle(it) },
                    onSetEditingTitle = { viewModel.setEditingTitle(it) },
                    onRenameSpeaker = { original, newName -> viewModel.renameSpeaker(original, newName) },
                    onPlayPause = { viewModel.playPause() },
                    onSeekTo = { ms -> viewModel.seekTo(ms) },
                    onShareToCloud = { viewModel.shareMeeting() },
                    onExportToCanva = { skill -> viewModel.exportToWebhook(skill) },
                    onResetCanvaExport = { viewModel.resetCanvaExport() }
                )
            }

            composable("settings") {
                val viewModel: SettingsViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current
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
                    onSyncNow = { viewModel.syncNow() },
                    onUpdateN8nWebhookUrl = { viewModel.updateN8nWebhookUrl(it) }
                )
            }

            // ── Category Manager ───────────────────────────────────────────────
            composable("category_manager") {
                val viewModel: CategoryViewModel = viewModel()
                CategoryManagerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNewCategory = { navController.navigate("category_editor") },
                    onEditCategory = { id -> navController.navigate("category_editor?categoryId=$id") }
                )
            }

            // ── Canva Skill Manager ────────────────────────────────────────────
            composable("canva_skill_manager") {
                val viewModel: CanvaSkillViewModel = viewModel()
                CanvaSkillManagerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNewSkill = { navController.navigate("canva_skill_editor") },
                    onEditSkill = { id -> navController.navigate("canva_skill_editor?skillId=$id") }
                )
            }

            // ── Canva Skill Editor ─────────────────────────────────────────────
            composable(
                "canva_skill_editor?skillId={skillId}",
                arguments = listOf(
                    navArgument("skillId") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val skillId = backStackEntry.arguments?.getInt("skillId") ?: 0
                val viewModel: CanvaSkillViewModel = viewModel()
                CanvaSkillEditorScreen(
                    skillId = skillId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Category Editor ────────────────────────────────────────────────
            // categoryId = 0 means "new category" (default value)
            composable(
                "category_editor?categoryId={categoryId}",
                arguments = listOf(
                    navArgument("categoryId") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: 0
                val viewModel: CategoryViewModel = viewModel()
                CategoryEditorScreen(
                    categoryId = categoryId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
