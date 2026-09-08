package com.projectstrong.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.theme.AppPrimary
import com.projectstrong.iptv.ui.theme.AppSurfaceBorder
import com.projectstrong.iptv.ui.theme.AppSurfaceVariant
import com.projectstrong.iptv.ui.theme.AppTextPrimary
import com.projectstrong.iptv.ui.theme.AppTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectToggles(
    label: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onOptionToggled: (Set<String>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = label,
            color = AppTextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (option in options) {
                val isSelected = selectedOptions.contains(option)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) AppPrimary else AppSurfaceVariant,
                    border = BorderStroke(1.dp, if (isSelected) AppPrimary else AppSurfaceBorder),
                    modifier = Modifier.clickable {
                        val newSet = if (isSelected) selectedOptions - option else selectedOptions + option
                        onOptionToggled(newSet)
                    }
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else AppTextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
