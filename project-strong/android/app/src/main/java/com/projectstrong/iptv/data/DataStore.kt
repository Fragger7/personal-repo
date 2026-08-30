package com.projectstrong.iptv.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.projectstrong.iptv.network.ParsedCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object DataStore {
    val scanScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val scannedNodes = mutableStateListOf<ParsedCredential>()
    var scannerInput by mutableStateOf("")
    var scannerSourceLink by mutableStateOf("Direct Ingestion")
    var isScanning by mutableStateOf(false)
    var isScanPaused by mutableStateOf(false)
    var scanProgress by mutableStateOf(0f)
    var scanCountText by mutableStateOf("")
    var ipInfo by mutableStateOf("")
    var detectedIp by mutableStateOf("")
    var detectedIsp by mutableStateOf("")
    var detectedOrg by mutableStateOf("")
    var detectedCountry by mutableStateOf("")
    var isCloudHosting by mutableStateOf(false)
    var isVpnActive by mutableStateOf(false)
    var isCheckingNetwork by mutableStateOf(false)
    var showVpnWarning by mutableStateOf(false)
    var activeOnlyXtream by mutableStateOf(false)
    var activeOnlyStalker by mutableStateOf(false)
    var githubToken by mutableStateOf("")
    
    // Global Catalog Query State for Xtream
    var isQueryingCatalogs by mutableStateOf(false)
    var isCatalogQueryPaused by mutableStateOf(false)
    var catalogQueryProgress by mutableStateOf(0f)
    var catalogQueryStatusText by mutableStateOf("")
}
