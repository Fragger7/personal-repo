package com.projectstrong.iptv.ui.state

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class CatalogExplorerState {
    var isVisible by mutableStateOf(false)
        private set
        
    var baseUrl by mutableStateOf("")
        private set
        
    var user by mutableStateOf("")
        private set
        
    var pass by mutableStateOf("")
        private set
        
    var title by mutableStateOf("")
        private set

    fun show(baseUrl: String, user: String, pass: String, title: String) {
        this.baseUrl = baseUrl
        this.user = user
        this.pass = pass
        this.title = title
        this.isVisible = true
    }

    fun hide() {
        this.isVisible = false
    }
}

val LocalCatalogExplorerState = compositionLocalOf<CatalogExplorerState> { 
    error("No CatalogExplorerState provided") 
}
