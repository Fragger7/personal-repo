package com.projectstrong.iptv.ui.state

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.projectstrong.iptv.data.PlayerScope

class StreamPreviewState {
    var isVisible by mutableStateOf(false)
        private set
        
    var streamUrl by mutableStateOf("")
        private set
        
    var streamName by mutableStateOf("")
        private set
        
    var streamId by mutableStateOf("")
        private set
        
    var categoryName by mutableStateOf("")
        private set

    var scope by mutableStateOf(PlayerScope.INSPECTOR)
        private set

    fun show(
        streamUrl: String,
        streamName: String,
        streamId: String = "",
        categoryName: String = "",
        scope: PlayerScope = PlayerScope.INSPECTOR
    ) {
        this.streamUrl = streamUrl
        this.streamName = streamName
        this.streamId = streamId
        this.categoryName = categoryName
        this.scope = scope
        this.isVisible = true
    }

    fun hide() {
        this.isVisible = false
    }
}

val LocalStreamPreviewState = compositionLocalOf<StreamPreviewState> { 
    error("No StreamPreviewState provided") 
}
