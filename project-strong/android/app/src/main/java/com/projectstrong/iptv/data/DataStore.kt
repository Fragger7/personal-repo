package com.projectstrong.iptv.data

import androidx.compose.runtime.mutableStateListOf
import com.projectstrong.iptv.network.ParsedCredential

object DataStore {
    val scannedNodes = mutableStateListOf<ParsedCredential>()
}
