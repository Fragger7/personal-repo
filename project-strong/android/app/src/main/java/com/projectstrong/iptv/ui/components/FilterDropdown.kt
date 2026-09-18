package com.projectstrong.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.theme.AppPrimary
import com.projectstrong.iptv.ui.theme.AppSurfaceBorder
import com.projectstrong.iptv.ui.theme.AppSurfaceVariant
import com.projectstrong.iptv.ui.theme.AppTextMuted
import com.projectstrong.iptv.ui.theme.AppTextPrimary

@Composable
fun FilterDropdown(
    label: String,
    options: List<Pair<String, Int>>,
    noneCount: Int,
    selectedOptions: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val displayText = if (selectedOptions.isEmpty()) {
        "Any"
    } else if (selectedOptions.size == 1) {
        selectedOptions.first()
    } else {
        "${selectedOptions.size} Selected"
    }

    Box(modifier = Modifier.padding(end = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AppSurfaceVariant,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$label: $displayText",
                    color = if (selectedOptions.isNotEmpty()) AppPrimary else AppTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = AppTextMuted,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppSurfaceVariant).widthIn(min = 180.dp)
        ) {
            val allOptions = listOf(Pair("None", noneCount)) + options
            allOptions.forEach { (option, count) ->
                val isSelected = selectedOptions.contains(option)
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AppPrimary,
                                    uncheckedColor = AppTextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$option ($count)", color = AppTextPrimary) 
                        }
                    },
                    onClick = {
                        val newSet = if (isSelected) {
                            selectedOptions - option
                        } else {
                            selectedOptions + option
                        }
                        onSelectionChanged(newSet)
                    }
                )
            }
        }
    }
}
