package com.omamoripet.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omamoripet.mobile.ui.screens.WellnessNoticeCard
import com.omamoripet.mobile.ui.theme.OmamoriM3Theme

class PrivacyRationaleActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmamoriM3Theme {
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.privacy_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_rounded),
                                        contentDescription = stringResource(R.string.back)
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { padding ->
                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_favorite_border_rounded),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = stringResource(R.string.privacy_point_read_title),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = stringResource(R.string.privacy_point_read_body),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_shield_rounded),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = stringResource(R.string.privacy_point_local_title),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = stringResource(R.string.privacy_point_local_body),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            WellnessNoticeCard(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
