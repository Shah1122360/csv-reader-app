package com.csvviewer.fileviewer.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.data.FileItem
import com.csvviewer.fileviewer.data.FileKind
import com.csvviewer.fileviewer.ui.screens.*

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val SEARCH = "search"
    const val VIEWER = "viewer"
    const val ANALYSIS = "analysis"
    const val CHARTS = "charts"
    const val EDITOR = "editor"
    const val CREATE = "create"
    const val CONVERT = "convert/{id}"
    const val BATCH = "batch"
    const val MERGE = "merge"
    const val COMPRESS = "compress"
    const val PDF = "pdf"
    const val TEXT = "text"
    const val IMAGE = "image"
    const val ZIP = "zip"
    const val SCANNER = "scanner"
    const val STORAGE = "storage"
    const val TRASH = "trash"
    const val FAVORITES = "favorites"
    const val APPEARANCE = "appearance"
    const val LANGUAGE = "language"
    const val ABOUT = "about"
    const val FEEDBACK = "feedback"
    const val FEEDBACK_SENT = "feedback_sent"
    const val PREMIUM = "premium"
    const val PREMIUM_SUCCESS = "premium_success"

    fun convert(id: String) = "convert/$id"
}

fun routeFor(kind: FileKind): String = when {
    kind.isTable -> Routes.VIEWER
    kind == FileKind.PDF -> Routes.PDF
    kind == FileKind.IMAGE -> Routes.IMAGE
    kind == FileKind.ZIP -> Routes.ZIP
    else -> Routes.TEXT
}

fun NavController.openFile(vm: AppViewModel, item: FileItem) {
    vm.open(item)
    navigate(routeFor(item.kind))
}

/** Opens a Pro feature, or the Premium screen for free users. */
fun NavController.pro(vm: AppViewModel, route: String) {
    navigate(if (vm.isPro) route else Routes.PREMIUM)
}

@Composable
fun AppNav(vm: AppViewModel, incoming: Uri?) {
    val nav = rememberNavController()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        NavHost(nav, startDestination = Routes.SPLASH) {
            composable(Routes.SPLASH) {
                SplashScreen {
                    nav.navigate(if (vm.onboarded) Routes.MAIN else Routes.ONBOARDING) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    if (incoming != null && vm.onboarded) nav.openFile(vm, vm.register(incoming))
                }
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(vm) {
                    vm.finishOnboarding()
                    nav.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                }
            }
            composable(Routes.MAIN) { MainScreen(vm, nav) }
            composable(Routes.SEARCH) { SearchScreen(vm, nav) }
            composable(Routes.VIEWER) { ViewerScreen(vm, nav) }
            composable(Routes.ANALYSIS) { AnalysisScreen(vm, nav) }
            composable(Routes.CHARTS) { ChartsScreen(vm, nav) }
            composable(Routes.EDITOR) { EditorScreen(vm, nav) }
            composable(Routes.CREATE) { CreateCsvScreen(vm, nav) }
            composable(Routes.CONVERT) { entry -> ConvertScreen(vm, nav, entry.arguments?.getString("id") ?: "csv-pdf") }
            composable(Routes.BATCH) { BatchScreen(vm, nav) }
            composable(Routes.MERGE) { MergeScreen(vm, nav) }
            composable(Routes.COMPRESS) { CompressScreen(vm, nav) }
            composable(Routes.PDF) { PdfViewerScreen(vm, nav) }
            composable(Routes.TEXT) { TextViewerScreen(vm, nav) }
            composable(Routes.IMAGE) { ImageViewerScreen(vm, nav) }
            composable(Routes.ZIP) { ZipViewerScreen(vm, nav) }
            composable(Routes.SCANNER) { ScannerScreen(vm, nav) }
            composable(Routes.STORAGE){ StorageScreen(vm, nav) }
            composable(Routes.TRASH) { TrashScreen(vm, nav) }
            composable(Routes.FAVORITES) { FavoritesScreen(vm, nav) }
            composable(Routes.APPEARANCE) { AppearanceScreen(vm, nav) }
            composable(Routes.LANGUAGE) { LanguageScreen(vm, nav) }
            composable(Routes.ABOUT) { AboutScreen(nav) }
            composable(Routes.FEEDBACK) { FeedbackScreen(nav) }
            composable(Routes.FEEDBACK_SENT) { FeedbackSentScreen(nav) }
            composable(Routes.PREMIUM) { PremiumScreen(vm, nav) }
            composable(Routes.PREMIUM_SUCCESS) { PremiumSuccessScreen(nav) }
        }
    }
}
