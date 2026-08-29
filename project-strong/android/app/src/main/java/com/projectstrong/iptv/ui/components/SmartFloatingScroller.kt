package com.projectstrong.iptv.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.theme.AppPrimary
import kotlinx.coroutines.launch

/**
 * Context-aware intelligent floating scroller for LazyColumn lists.
 * Only displays 'Scroll to Top' when scrolled down, and 'Scroll to Bottom' when more items exist below.
 * Smoothly animates in/out so the UI stays uncluttered when not needed.
 */
@Composable
fun SmartLazyListScroller(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || itemCount <= 0) {
                false
            } else {
                val lastVisibleIndex = visibleItems.last().index
                lastVisibleIndex < itemCount - 1
            }
        }
    }

    Column(
        modifier = modifier
            .padding(end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.7f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.7f)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = AppPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to Top")
            }
        }

        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.7f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.7f)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        if (itemCount > 0) {
                            listState.animateScrollToItem(itemCount - 1)
                        }
                    }
                },
                containerColor = AppPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to Bottom")
            }
        }
    }
}

/**
 * Context-aware intelligent floating scroller for standard ScrollState vertical columns.
 * Appears dynamically based on scroll offset and max scrollable bounds.
 */
@Composable
fun SmartColumnScroller(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val showScrollToTop by remember {
        derivedStateOf {
            scrollState.value > 120
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            scrollState.maxValue > 250 && scrollState.value < (scrollState.maxValue - 120)
        }
    }

    Column(
        modifier = modifier
            .padding(end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.7f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.7f)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(0)
                    }
                },
                containerColor = AppPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to Top")
            }
        }

        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.7f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.7f)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                },
                containerColor = AppPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to Bottom")
            }
        }
    }
}
