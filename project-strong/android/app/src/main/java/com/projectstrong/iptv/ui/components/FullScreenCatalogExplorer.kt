package com.projectstrong.iptv.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.projectstrong.iptv.network.IPTVClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CategoryItem(val id: String, val name: String, var count: Int = 0)
data class ChannelItem(val streamId: String, val name: String, val categoryId: String, val iconUrl: String, val directUrl: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenCatalogExplorer(
    baseUrl: String,
    user: String,
    pass: String,
    title: String = "Catalog Explorer",
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<CategoryItem>>(emptyList()) }
    var allChannels by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var selectedCategoryId by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var showCopiedToast by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }

    // Fetch catalog data
    LaunchedEffect(baseUrl, user, pass) {
        isLoading = true
        errorMessage = null
        try {
            withContext(Dispatchers.IO) {
                val catJson = IPTVClient.getLiveCategories(baseUrl, user, pass)
                val streamsJson = IPTVClient.getAllLiveStreams(baseUrl, user, pass)

                if (streamsJson != null) {
                    val parsedChannels = mutableListOf<ChannelItem>()
                    val catCountMap = mutableMapOf<String, Int>()

                    val cleanBaseUrl = baseUrl.trimEnd('/')
                    for (i in 0 until streamsJson.length()) {
                        val stream = streamsJson.optJSONObject(i)
                        if (stream != null) {
                            val streamId = stream.optString("stream_id", "")
                            val name = stream.optString("name", "Unknown Stream")
                            val catId = stream.optString("category_id", "")
                            val icon = stream.optString("stream_icon", "")
                            val direct = "$cleanBaseUrl/live/$user/$pass/$streamId.ts"

                            parsedChannels.add(ChannelItem(streamId, name, catId, icon, direct))
                            catCountMap[catId] = (catCountMap[catId] ?: 0) + 1
                        }
                    }

                    val parsedCategories = mutableListOf<CategoryItem>()
                    parsedCategories.add(CategoryItem("all", "All Channels", parsedChannels.size))

                    if (catJson != null) {
                        for (i in 0 until catJson.length()) {
                            val cat = catJson.optJSONObject(i)
                            if (cat != null) {
                                val cid = cat.optString("category_id", "")
                                val cname = cat.optString("category_name", "Category $cid")
                                val count = catCountMap[cid] ?: 0
                                parsedCategories.add(CategoryItem(cid, cname, count))
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        categories = parsedCategories
                        allChannels = parsedChannels
                        isLoading = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Could not load stream catalog from server."
                        isLoading = false
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error loading catalog: ${e.localizedMessage}"
            isLoading = false
        }
    }

    // Filtered channel list
    val filteredChannels = remember(allChannels, selectedCategoryId, searchQuery) {
        allChannels.filter { channel ->
            val matchesCategory = (selectedCategoryId == "all" || channel.categoryId == selectedCategoryId)
            val matchesSearch = if (searchQuery.isBlank()) true else {
                channel.name.contains(searchQuery, ignoreCase = true) ||
                channel.streamId.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }
    }

    val selectedCategoryName = remember(categories, selectedCategoryId) {
        categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "All Channels"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0D1117)
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Channel Explorer",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = baseUrl.replace("http://", "").replace("https://", ""),
                            color = Color(0xFF60A5FA),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (allChannels.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF21262D))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${allChannels.size} Channels",
                                color = Color(0xFF34D399),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF3B82F6))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading Full Live Channel Catalog...", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Parsing categories and streams from provider", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color(0xFFEF4444), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(errorMessage!!, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            PrimaryButton(text = "Close", onClick = onDismiss)
                        }
                    }
                } else {
                    // Search Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter ${filteredChannels.size} channels by name or ID...", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedContainerColor = Color(0xFF0D1117),
                                unfocusedContainerColor = Color(0xFF0D1117)
                            ),
                            singleLine = true
                        )
                    }

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategoryId == cat.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCategoryId = cat.id
                                    coroutineScope.launch { listState.scrollToItem(0) }
                                },
                                label = {
                                    Text(
                                        text = "${cat.name} (${cat.count})",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF3B82F6),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF21262D),
                                    labelColor = Color(0xFF8B949E)
                                ),
                                border = null,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Subheader Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$selectedCategoryName • ${filteredChannels.size} results",
                            color = Color(0xFF8B949E),
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "Filtered by \"$searchQuery\"",
                                color = Color(0xFFFBBF24),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Divider(color = Color(0xFF21262D), thickness = 1.dp)

                    // Channels List
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        if (filteredChannels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.TvOff, contentDescription = "No channels", tint = Color.Gray, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No channels match your search or filter", color = Color.Gray)
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                items(filteredChannels, key = { it.streamId + it.name }) { channel ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF21262D)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.LiveTv,
                                                        contentDescription = "Live",
                                                        tint = Color(0xFF60A5FA),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = channel.name,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF30363D))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = "ID: ${channel.streamId}",
                                                                color = Color(0xFF8B949E),
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                // Copy Channel Name
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(channel.name))
                                                        toastText = "Copied Channel Name"
                                                        showCopiedToast = true
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = "Copy Name",
                                                        tint = Color(0xFF8B949E),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                // Copy Direct Stream URL
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(channel.directUrl))
                                                        toastText = "Copied Direct Stream URL (.ts)"
                                                        showCopiedToast = true
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Link,
                                                        contentDescription = "Copy Link",
                                                        tint = Color(0xFF34D399),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    Spacer(modifier = Modifier.height(72.dp))
                                }
                            }
                        }

                        // Floating scroll buttons
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                                containerColor = Color(0xFF3B82F6),
                                contentColor = Color.White,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Top")
                            }
                            FloatingActionButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (filteredChannels.isNotEmpty()) {
                                            listState.animateScrollToItem(filteredChannels.size - 1)
                                        }
                                    }
                                },
                                containerColor = Color(0xFF3B82F6),
                                contentColor = Color.White,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bottom")
                            }
                        }

                        // Copied Feedback Banner
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showCopiedToast,
                                enter = fadeIn() + slideInVertically { it },
                                exit = fadeOut() + slideOutVertically { it }
                            ) {
                                LaunchedEffect(showCopiedToast) {
                                    if (showCopiedToast) {
                                        kotlinx.coroutines.delay(1800)
                                        showCopiedToast = false
                                    }
                                }
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(toastText, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
