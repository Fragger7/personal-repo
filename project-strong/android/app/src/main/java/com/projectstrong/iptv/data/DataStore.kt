package com.projectstrong.iptv.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.projectstrong.iptv.network.ParsedCredential

object DataStore {
    val scannedNodes = mutableStateListOf<ParsedCredential>()
    var scannerInput by mutableStateOf("")
    var isScanning by mutableStateOf(false)
    var scanProgress by mutableStateOf(0f)
    var scanCountText by mutableStateOf("")
    var ipInfo by mutableStateOf("")
    var showVpnWarning by mutableStateOf(false)
}