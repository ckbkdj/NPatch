package org.lsposed.lspatch.ui.page

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.NavResult
import com.ramcosta.composedestinations.result.ResultRecipient
import kotlinx.coroutines.launch
import org.lsposed.lspatch.R
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.ui.component.AnywhereDropdown
import org.lsposed.lspatch.ui.component.SelectionColumn
import org.lsposed.lspatch.ui.component.ShimmerAnimation
import org.lsposed.lspatch.ui.component.settings.SettingsCheckBox
import org.lsposed.lspatch.ui.component.settings.SettingsEditor
import org.lsposed.lspatch.ui.component.settings.SettingsItem
import org.lsposed.lspatch.ui.page.destinations.SelectAppsScreenDestination
import org.lsposed.lspatch.ui.util.InstallResultReceiver
import org.lsposed.lspatch.ui.util.LocalSnackbarHost
import org.lsposed.lspatch.ui.util.checkIsApkFixedByLSP
import org.lsposed.lspatch.ui.util.installApk
import org.lsposed.lspatch.ui.util.installApks
import org.lsposed.lspatch.ui.util.isScrolledToEnd
import org.lsposed.lspatch.ui.util.lastItemIndex
import org.lsposed.lspatch.ui.util.uninstallApkByPackageName
import org.lsposed.lspatch.ui.viewmodel.NewPatchViewModel
import org.lsposed.lspatch.ui.viewmodel.NewPatchViewModel.PatchState
import org.lsposed.lspatch.ui.viewmodel.NewPatchViewModel.ViewAction
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo
import org.lsposed.lspatch.util.ShizukuApi

private const val TAG = "NewPatchPage"

const val ACTION_STORAGE = 0
const val ACTION_APPLIST = 1
const val ACTION_INTENT_INSTALL = 2

@OptIn(ExperimentalMaterial3Api::class)
@Destination
@Composable
fun NewPatchScreen(
    navigator: DestinationsNavigator,
    resultRecipient: ResultRecipient<SelectAppsScreenDestination, SelectAppsResult>,
    id: Int,
    data: Uri? = null
) {
    val viewModel = viewModel<NewPatchViewModel>()
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val errorUnknown = stringResource(R.string.error_unknown)
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { apks ->
        if (apks.isEmpty()) {
            navigator.navigateUp()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            LSPPackageManager.getAppInfoFromApks(apks)
                .onSuccess {
                    viewModel.dispatch(ViewAction.ConfigurePatch(it.first()))
                }
                .onFailure {
                    snackbarHost.showSnackbar(it.message ?: errorUnknown)
                    navigator.navigateUp()
                }
        }
    }

    var showSelectModuleDialog by remember { mutableStateOf(false) }
    val noXposedModules = stringResource(R.string.patch_no_xposed_module)
    val storageModuleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { apks ->
            if (apks.isEmpty()) {
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                LSPPackageManager.getAppInfoFromApks(apks).onSuccess { appInfos ->
                    val modules = appInfos.filter { it.isXposedModule }
                    if (modules.isEmpty()) {
                        snackbarHost.showSnackbar(noXposedModules)
                    } else {
                        viewModel.embeddedModules = modules
                    }
                }.onFailure {
                    snackbarHost.showSnackbar(it.message ?: errorUnknown)
                }
            }
        }

    Log.d(TAG, "PatchState: ${viewModel.patchState}")
    when (viewModel.patchState) {
        PatchState.INIT -> {
            LaunchedEffect(Unit) {
                LSPPackageManager.cleanTmpApkDir()
                when (id) {
                    ACTION_STORAGE -> {
                        storageLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                        viewModel.dispatch(ViewAction.DoneInit)
                    }

                    ACTION_APPLIST -> {
                        navigator.navigate(SelectAppsScreenDestination(false, null))
                        viewModel.dispatch(ViewAction.DoneInit)
                    }

                    ACTION_INTENT_INSTALL -> {
                        data?.let { uri ->
                            scope.launch {
                                LSPPackageManager.getAppInfoFromApks(listOf(uri)).onSuccess {
                                    viewModel.dispatch(ViewAction.ConfigurePatch(it.first()))
                                }.onFailure {
                                    snackbarHost.showSnackbar(it.message ?: errorUnknown)
                                    navigator.navigateUp()
                                }
                            }
                        }
                    }
                }
            }
        }
        PatchState.SELECTING -> {
            resultRecipient.onNavResult {
                Log.d(TAG, "onNavResult: $it")
                when (it) {
                    is NavResult.Canceled -> navigator.navigateUp()
                    is NavResult.Value -> {
                        val result = it.value as SelectAppsResult.SingleApp
                        viewModel.dispatch(ViewAction.ConfigurePatch(result.selected))
                    }
                }
            }
        }
        else -> {
            Scaffold(
                topBar = {
                    when (viewModel.patchState) {
                        PatchState.CONFIGURING -> ConfiguringTopBar { navigator.navigateUp() }
                        PatchState.PATCHING,
                        PatchState.FINISHED,
                        PatchState.ERROR -> CenterAlignedTopAppBar(title = { Text(viewModel.patchApp.app.packageName) })
                        else -> Unit
                    }
                },
                floatingActionButton = {
                    if (viewModel.patchState == PatchState.CONFIGURING) {
                        ConfiguringFab()
                    }
                }
            ) { innerPadding ->
                if (viewModel.patchState == PatchState.CONFIGURING) {
                    PatchOptionsBody(Modifier.padding(innerPadding)) {
                        showSelectModuleDialog = true
                    }
                    resultRecipient.onNavResult {
                        if (it is NavResult.Value) {
                            val result = it.value as SelectAppsResult.MultipleApps
                            viewModel.embeddedModules = result.selected
                        }
                    }
                } else {
                    DoPatchBody(Modifier.padding(innerPadding), navigator)
                }
            }

            if (showSelectModuleDialog) {
                AlertDialog(onDismissRequest = { showSelectModuleDialog = false },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(content = { Text(stringResource(android.R.string.cancel)) },
                            onClick = { showSelectModuleDialog = false })
                    },
                    title = {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.patch_embed_modules),
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                                onClick = {
                                    storageModuleLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                                    showSelectModuleDialog = false
                                }) {
                                Text(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    text = stringResource(R.string.patch_from_storage),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            TextButton(modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                                onClick = {
                                    navigator.navigate(
                                        SelectAppsScreenDestination(true,
                                            viewModel.embeddedModules.mapTo(ArrayList()) { it.app.packageName })
                                    )
                                    showSelectModuleDialog = false
                                }) {
                                Text(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    text = stringResource(R.string.patch_from_applist),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfiguringTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.screen_new_patch)) },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                content = { Icon(Icons.Outlined.ArrowBack, null) }
            )
        }
    )
}

@Composable
private fun ConfiguringFab() {
    val viewModel = viewModel<NewPatchViewModel>()
    ExtendedFloatingActionButton(
        text = { Text(stringResource(R.string.patch_start)) },
        icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
        onClick = { viewModel.dispatch(ViewAction.SubmitPatch) }
    )
}

@Composable
private fun sigBypassLvStr(level: Int) = when (level) {
    0 -> stringResource(R.string.patch_sigbypasslv0)
    1 -> stringResource(R.string.patch_sigbypasslv1)
    2 -> stringResource(R.string.patch_sigbypasslv2)
    else -> throw IllegalArgumentException("Invalid sigBypassLv: $level")
}

@Composable
private fun PatchOptionsBody(modifier: Modifier, onAddEmbed: () -> Unit) {
    val viewModel = viewModel<NewPatchViewModel>()

    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = viewModel.patchApp.label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = viewModel.patchApp.app.packageName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = stringResource(R.string.patch_mode),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 24.dp, bottom = 12.dp)
        )
        SelectionColumn(Modifier.padding(horizontal = 24.dp)) {
            SelectionItem(
                selected = viewModel.useManager,
                onClick = { viewModel.useManager = true },
                icon = Icons.Outlined.Api,
                title = stringResource(R.string.patch_local),
                desc = stringResource(R.string.patch_local_desc)
            )
            SelectionItem(
                selected = !viewModel.useManager,
                onClick = { viewModel.useManager = false },
                icon = Icons.Outlined.WorkOutline,
                title = stringResource(R.string.patch_integrated),
                desc = stringResource(R.string.patch_integrated_desc),
                extraContent = {
                    TextButton(
                        onClick = onAddEmbed,
                        content = { Text(text = stringResource(R.string.patch_embed_modules), style = MaterialTheme.typography.bodyLarge) }
                    )
                }
            )
        }
        SettingsEditor(Modifier.padding(top = 6.dp),
            stringResource(R.string.patch_new_package),
            viewModel.newPackageName,
            onValueChange = {
                viewModel.newPackageName = it
            },
        )
        SettingsCheckBox(
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable { viewModel.debuggable = !viewModel.debuggable },
            checked = viewModel.debuggable,
            icon = Icons.Outlined.BugReport,
            title = stringResource(R.string.patch_debuggable)
        )
        SettingsCheckBox(
            modifier = Modifier.clickable { viewModel.overrideVersionCode = !viewModel.overrideVersionCode },
            checked = viewModel.overrideVersionCode,
            icon = Icons.Outlined.Layers,
            title = stringResource(R.string.patch_override_version_code),
            desc = stringResource(R.string.patch_override_version_code_desc)
        )

        SettingsCheckBox(
            modifier = Modifier.clickable { viewModel.injectDex = !viewModel.injectDex },
            checked = viewModel.injectDex,
            icon = Icons.Outlined.Code,
            title = stringResource(R.string.patch_inject_dex),
            desc = stringResource(R.string.patch_inject_dex_desc)
        )

        SettingsCheckBox(
            modifier = Modifier.clickable { viewModel.outputLog = !viewModel.outputLog },
            checked = viewModel.outputLog,
            icon = Icons.Outlined.AddCard,
            title = stringResource(R.string.patch_output_log_to_media),
            desc = stringResource(R.string.patch_output_log_to_media_desc)
        )

        var bypassExpanded by remember { mutableStateOf(false) }
        AnywhereDropdown(
            expanded = bypassExpanded,
            onDismissRequest = { bypassExpanded = false },
            onClick = { bypassExpanded = true },
            surface = {
                SettingsItem(
                    icon = Icons.Outlined.RemoveModerator,
                    title = stringResource(R.string.patch_sigbypass),
                    desc = sigBypassLvStr(viewModel.sigBypassLevel)
                )
            }
        ) {
            repeat(3) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = viewModel.sigBypassLevel == it, onClick = { viewModel.sigBypassLevel = it })
                            Text(sigBypassLvStr(it))
                        }
                    },
                    onClick = {
                        viewModel.sigBypassLevel = it
                        bypassExpanded = false
                    }
                )
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun DoPatchBody(modifier: Modifier, navigator: DestinationsNavigator) {
    val viewModel = viewModel<NewPatchViewModel>()
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (viewModel.logs.isEmpty()) {
            viewModel.dispatch(ViewAction.LaunchPatch)
        }
    }

    BoxWithConstraints(modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        val shellBoxMaxHeight =
            if (viewModel.patchState == PatchState.PATCHING) maxHeight
            else maxHeight - ButtonDefaults.MinHeight - 12.dp
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentHeight()
                .animateContentSize(spring(stiffness = Spring.StiffnessLow))
        ) {
            ShimmerAnimation(enabled = viewModel.patchState == PatchState.PATCHING) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) {
                    val scrollState = rememberLazyListState()
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = shellBoxMaxHeight)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant) // Replaced 'brush' with a theme color
                            .padding(horizontal = 24.dp, vertical = 18.dp)
                    ) {
                        items(viewModel.logs) {
                            when (it.first) {
                                Log.DEBUG, Log.INFO -> Text(text = it.second)
                                Log.ERROR -> Text(text = it.second, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    LaunchedEffect(scrollState.lastItemIndex) {
                        if (scrollState.lastItemIndex != null && !scrollState.isScrolledToEnd) {
                            scrollState.animateScrollToItem(scrollState.lastItemIndex!!)
                        }
                    }
                }
            }

            when (viewModel.patchState) {
                PatchState.PATCHING -> BackHandler {}
                PatchState.FINISHED -> {
                    val installSuccessfully = stringResource(R.string.patch_install_successfully)
                    val installFailed = stringResource(R.string.patch_install_failed)
                    val copyError = stringResource(R.string.copy_error)
                    var installation by remember { mutableStateOf<NewPatchViewModel.InstallMethod?>(null) }
                    val onFinish: (Int, String?) -> Unit = { status, message ->
                        scope.launch {
                            if (status == PackageInstaller.STATUS_SUCCESS) {
                                snackbarHost.showSnackbar(installSuccessfully)
                                navigator.navigateUp()
                            } else if (status != LSPPackageManager.STATUS_USER_CANCELLED) {
                                val result = snackbarHost.showSnackbar(installFailed, copyError)
                                if (result == SnackbarResult.ActionPerformed) {
                                    val cm = lspApp.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("LSPatch", message))
                                }
                            }
                            installation = null // Reset installation state
                        }
                    }
                    when (installation) {
                        NewPatchViewModel.InstallMethod.SYSTEM -> InstallDialog2(viewModel.patchApp, onFinish)
                        NewPatchViewModel.InstallMethod.SHIZUKU -> InstallDialog(viewModel.patchApp, onFinish)
                        null -> {}
                    }
                    Row(Modifier.padding(top = 12.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { navigator.navigateUp() },
                            content = { Text(stringResource(R.string.patch_return)) }
                        )
                        Spacer(Modifier.weight(0.2f))
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                installation = if (!ShizukuApi.isPermissionGranted) NewPatchViewModel.InstallMethod.SYSTEM else NewPatchViewModel.InstallMethod.SHIZUKU
                                Log.d(TAG, "Installation method: $installation")
                            },
                            content = { Text(stringResource(R.string.install)) }
                        )
                    }
                }
                PatchState.ERROR -> {
                    Row(Modifier.padding(top = 12.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { navigator.navigateUp() },
                            content = { Text(stringResource(R.string.patch_return)) }
                        )
                        Spacer(Modifier.weight(0.2f))
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val cm = lspApp.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("LSPatch", viewModel.logs.joinToString(separator = "\n") { it.second }))
                            },
                            content = { Text(stringResource(R.string.copy_error)) }
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun UninstallConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                content = { Text(stringResource(android.R.string.ok)) }
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                content = { Text(stringResource(android.R.string.cancel)) }
            )
        },
        title = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.uninstall),
                textAlign = TextAlign.Center
            )
        },
        text = { Text(stringResource(R.string.patch_uninstall_text)) }
    )
}

@Composable
private fun InstallDialog(patchApp: AppInfo, onFinish: (Int, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var uninstallFirst by remember { mutableStateOf(ShizukuApi.isPackageInstalledWithoutPatch(patchApp.app.packageName)) }
    var installing by remember { mutableStateOf(0) } // 0: idle, 1: installing, 2: uninstalling

    suspend fun doInstall() {
        Log.i(TAG, "Installing app ${patchApp.app.packageName}")
        installing = 1
        val (status, message) = LSPPackageManager.install()
        installing = 0
        Log.i(TAG, "Installation end: $status, $message")
        onFinish(status, message)
    }

    LaunchedEffect(uninstallFirst) {
        if (!uninstallFirst && installing == 0) {
            doInstall()
        }
    }

    if (uninstallFirst) {
        UninstallConfirmationDialog(
            onDismiss = { onFinish(LSPPackageManager.STATUS_USER_CANCELLED, "User cancelled") },
            onConfirm = {
                scope.launch {
                    Log.i(TAG, "Uninstalling app ${patchApp.app.packageName}")
                    installing = 2
                    val (status, message) = LSPPackageManager.uninstall(patchApp.app.packageName)
                    installing = 0
                    Log.i(TAG, "Uninstallation end: $status, $message")
                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        uninstallFirst = false // This will trigger the LaunchedEffect to install
                    } else {
                        onFinish(status, message)
                    }
                }
            }
        )
    }

    if (installing != 0) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(if (installing == 1) R.string.installing else R.string.uninstalling),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }
        )
    }
}

@Composable
private fun InstallDialog2(patchApp: AppInfo, onFinish: (Int, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var uninstallFirst by remember { mutableStateOf(checkIsApkFixedByLSP(lspApp, patchApp.app.packageName)) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val splitInstallReceiver = remember { InstallResultReceiver() }

    fun doInstall() {
        Log.i(TAG, "Installing app with system installer: ${patchApp.app.packageName}")
        val apkFiles = lspApp.targetApkFiles
        if (apkFiles.isNullOrEmpty()){
            onFinish(PackageInstaller.STATUS_FAILURE, "No target APK files found for installation")
            return
        }
        if (apkFiles.size > 1) {
            scope.launch {
                val success = installApks(lspApp, apkFiles)
                onFinish(
                    if (success) PackageInstaller.STATUS_SUCCESS else PackageInstaller.STATUS_FAILURE,
                    if (success) "Split APKs installed successfully" else "Failed to install split APKs"
                )
            }
        } else  {
            installApk(lspApp, apkFiles.first())
            // For single APK install, the result is typically handled by onActivityResult,
            // but since we are using a receiver for splits, we can unify later if needed.
            // For now, system prompt is the feedback. We might need a better way to track this.
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val intentFilter = IntentFilter(InstallResultReceiver.ACTION_INSTALL_STATUS)
        // Correctly handle receiver registration for different Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(splitInstallReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(splitInstallReceiver, intentFilter)
        }

        onDispose {
            context.unregisterReceiver(splitInstallReceiver)
        }
    }

    LaunchedEffect(uninstallFirst) {
        if (!uninstallFirst) {
            Log.d(TAG, "State changed to install, starting installation via system.")
            doInstall()
            // Since system installer is an Intent, it's fire-and-forget. We can dismiss our UI.
            onFinish(LSPPackageManager.STATUS_USER_CANCELLED, "Handed over to system installer")
        }
    }

    if (uninstallFirst) {
        UninstallConfirmationDialog(
            onDismiss = { onFinish(LSPPackageManager.STATUS_USER_CANCELLED, "User cancelled") },
            onConfirm = {
                scope.launch {
                    Log.i(TAG, "Uninstalling app ${patchApp.app.packageName}")
                    uninstallApkByPackageName(lspApp, patchApp.app.packageName)
                    // After uninstall intent is sent, we can assume it will proceed.
                    uninstallFirst = false
                }
            }
        )
    }
}