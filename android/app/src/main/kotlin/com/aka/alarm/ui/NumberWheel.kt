package com.aka.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * A vertical wheel picker, Compose-native. Matches the iOS [NumberWheel] in row
 * height and selected-row treatment: glass-like translucent pill behind the
 * centered row, numbers above and below faded with distance.
 */
@Composable
fun NumberWheel(
    values: List<Int>,
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 80.dp,
    visibleRows: Int = 5,
    fontSize: Int = 44,
) {
    val initialIndex = values.indexOf(selection).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centeredIndex by remember(values) {
        derivedStateOf {
            val info = listState.layoutInfo
            // visibleItemsInfo[i].offset is in scroll coordinates, where
            // `viewportStartOffset = -beforeContentPadding`. The viewport's
            // visual centre in *those* coords is the midpoint of start/end —
            // NOT `viewportSize.height / 2`, which would shift the centre by
            // the top content padding and produce an N-row offset.
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2f - viewportCenter) }
                ?.index ?: initialIndex
        }
    }

    // Emit selection changes when scrolling settles.
    LaunchedEffect(listState.isScrollInProgress, centeredIndex) {
        if (!listState.isScrollInProgress && centeredIndex in values.indices) {
            val v = values[centeredIndex]
            if (v != selection) onSelectionChange(v)
        }
    }

    // External selection change → animate to that row.
    LaunchedEffect(selection) {
        val target = values.indexOf(selection)
        if (target in values.indices && target != centeredIndex && !listState.isScrollInProgress) {
            listState.animateScrollToItem(target)
        }
    }

    val totalHeight = rowHeight * visibleRows
    val centerPadding = rowHeight * ((visibleRows - 1) / 2)

    Box(modifier = modifier.height(totalHeight)) {
        // Glass selection pill behind the centered row.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(rowHeight)
                .background(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(percent = 50)
                )
        )

        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = centerPadding)
        ) {
            itemsIndexed(values) { index, value ->
                val distance = abs(index - centeredIndex)
                val alpha = (1f - 0.22f * distance).coerceAtLeast(0.18f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(rowHeight)
                ) {
                    Text(
                        text = "%02d".format(value),
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                }
            }
        }
    }
}
