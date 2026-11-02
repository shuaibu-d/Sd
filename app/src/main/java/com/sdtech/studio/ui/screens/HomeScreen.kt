package com.sdtech.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdtech.studio.R
import com.sdtech.studio.ui.components.EmptyState
import com.sdtech.studio.ui.components.ScreenScaffold
import com.sdtech.studio.ui.components.SectionHeader

@Composable
fun HomeScreen(
    onCreatePost: () -> Unit,
    onCreateVideo: () -> Unit
) {
    ScreenScaffold {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.brand_monogram), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(28.dp))

        ActionCard(
            title = stringResource(R.string.create_post_title),
            description = stringResource(R.string.create_post_description),
            symbol = "▧",
            onClick = onCreatePost
        )
        Spacer(Modifier.height(14.dp))
        ActionCard(
            title = stringResource(R.string.create_video_title),
            description = stringResource(R.string.create_video_description),
            symbol = "▶",
            onClick = onCreateVideo
        )

        Spacer(Modifier.height(30.dp))
        SectionHeader(title = stringResource(R.string.recent_projects))
        Spacer(Modifier.height(4.dp))
        EmptyState(
            title = stringResource(R.string.recent_projects_empty_title),
            description = stringResource(R.string.recent_projects_empty_description),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    symbol: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineLarge)
        }
    }
}
