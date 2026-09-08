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
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
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
                    text = "$label: $selectedOption",
                    color = if (selectedOption != "All") AppPrimary else AppTextPrimary,
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
            modifier = Modifier.background(AppSurfaceVariant)
        ) {
            val allOptions = listOf("All", "None") + options
            allOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = AppTextPrimary) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
