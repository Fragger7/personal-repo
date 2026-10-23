package com.projectstrong.iptv.ui.state

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    fun show(streamUrl: String, streamName: String, streamId: String = "", categoryName: String = "") {
        this.streamUrl = streamUrl
        this.streamName = streamName
        this.streamId = streamId
        this.categoryName = categoryName
        this.isVisible = true
    }

    fun hide() {
        this.isVisible = false
    }
}

val LocalStreamPreviewState = compositionLocalOf<StreamPreviewState> { 
    error("No StreamPreviewState provided") 
}
