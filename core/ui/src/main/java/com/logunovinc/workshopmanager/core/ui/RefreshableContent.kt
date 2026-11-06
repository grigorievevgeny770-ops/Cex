package com.logunovinc.workshopmanager.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Same Material3 pull gesture/indicator as PullToRefreshBox, with an explicit enabled guard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(refreshing: Boolean, enabled: Boolean, onRefresh: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state=rememberPullToRefreshState()
    Box(modifier.fillMaxSize().pullToRefresh(isRefreshing=refreshing,state=state,enabled=enabled,onRefresh={ if(enabled) onRefresh() })) {
        content()
        PullToRefreshDefaults.Indicator(state=state,isRefreshing=refreshing,modifier=Modifier.align(Alignment.TopCenter))
    }
}
