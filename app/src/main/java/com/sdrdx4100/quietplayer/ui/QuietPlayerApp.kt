package com.sdrdx4100.quietplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.sdrdx4100.quietplayer.MainViewModel
import com.sdrdx4100.quietplayer.PlayerUiState
import com.sdrdx4100.quietplayer.data.Song
import com.sdrdx4100.quietplayer.data.DURATION_KEY
import com.sdrdx4100.quietplayer.external.ExternalSessionBridge
import com.sdrdx4100.quietplayer.external.ExternalSessionState
import kotlin.math.roundToLong

private enum class Destination { Library, NowPlaying, External }
private enum class LibraryTab { Songs, Albums, Artists }

@Composable
fun QuietPlayerApp(
    state: PlayerUiState,
    hasAudioPermission: Boolean,
    requestPermission: () -> Unit,
    externalState: ExternalSessionState,
    hasNotificationAccess: Boolean,
    requestNotificationAccess: () -> Unit,
    viewModel: MainViewModel,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Library) }
    QuietPlayerTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Background,
            bottomBar = {
                if (destination == Destination.Library && state.currentItem != null) {
                    MiniPlayer(state, { destination = Destination.NowPlaying }, viewModel::togglePlay)
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    !hasAudioPermission -> PermissionScreen(requestPermission)
                    destination == Destination.Library -> LibraryScreen(
                        state = state,
                        onExternal = { destination = Destination.External },
                        onSong = { song, source ->
                            viewModel.playSong(song, source)
                            destination = Destination.NowPlaying
                        },
                    )
                    destination == Destination.NowPlaying -> NowPlayingScreen(state, viewModel, { destination = Destination.Library })
                    else -> ExternalSessionScreen(
                        state = externalState,
                        hasAccess = hasNotificationAccess,
                        requestAccess = requestNotificationAccess,
                        onLibrary = { destination = Destination.Library },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.LibraryMusic, null, Modifier.size(42.dp), tint = Accent)
        Spacer(Modifier.height(20.dp))
        Text("Your music, kept local", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Allow access to audio files so Quiet Player can build your on-device library. Nothing is uploaded.",
            color = SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Allow music access") }
    }
}

@Composable
private fun LibraryScreen(state: PlayerUiState, onExternal: () -> Unit, onSong: (Song, List<Song>) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${state.songs.size} songs", color = SecondaryText)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onExternal) {
                Icon(Icons.Default.Cast, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("External")
            }
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = Background, divider = {}) {
            LibraryTab.entries.forEach { item ->
                Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.name) })
            }
        }
        when {
            state.scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> EmptyState("Couldn’t scan music", state.error)
            state.songs.isEmpty() -> EmptyState("No music found", "Add audio files to this device, then reopen the app.")
            else -> {
                val visibleSongs = when (tab) {
                    LibraryTab.Songs -> state.songs
                    LibraryTab.Albums -> state.songs.distinctBy { it.albumId }
                    LibraryTab.Artists -> state.songs.distinctBy { it.artist }
                }
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(visibleSongs, key = { _, song -> "${tab.name}-${song.id}" }) { _, song ->
                        LibraryRow(song, tab) {
                            val source = when (tab) {
                                LibraryTab.Songs -> state.songs
                                LibraryTab.Albums -> state.songs.filter { it.albumId == song.albumId }
                                LibraryTab.Artists -> state.songs.filter { it.artist == song.artist }
                            }
                            onSong(song, source)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(song: Song, tab: LibraryTab, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artworkUri.toString(), Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (tab) { LibraryTab.Albums -> song.album; LibraryTab.Artists -> song.artist; else -> song.title },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (tab) { LibraryTab.Albums -> song.artist; LibraryTab.Artists -> "Artist"; else -> "${song.artist} · ${song.album}" },
                color = SecondaryText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tab == LibraryTab.Songs) Text(formatDuration(song.durationMs), color = SecondaryText, fontSize = 12.sp)
    }
}

@Composable
private fun NowPlayingScreen(state: PlayerUiState, viewModel: MainViewModel, onLibrary: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("now_playing")
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                NowPlayingPane(state, viewModel, onLibrary, Modifier.weight(0.62f).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
                QueuePane(state, viewModel, Modifier.weight(0.38f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                NowPlayingPane(state, viewModel, onLibrary, Modifier.fillMaxWidth().weight(0.63f))
                Box(Modifier.height(1.dp).fillMaxWidth().background(Hairline))
                QueuePane(state, viewModel, Modifier.fillMaxWidth().weight(0.37f))
            }
        }
    }
}

@Composable
private fun NowPlayingPane(
    state: PlayerUiState,
    viewModel: MainViewModel,
    onLibrary: () -> Unit,
    modifier: Modifier,
) {
    val item = state.currentItem
    BoxWithConstraints(modifier.padding(end = 22.dp)) {
        val wide = maxWidth > 560.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Artwork(item?.mediaMetadata?.artworkUri?.toString(), Modifier.weight(0.48f).aspectRatio(1f))
                Spacer(Modifier.width(26.dp))
                PlayerDetails(state, viewModel, onLibrary, Modifier.weight(0.52f))
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLibrary) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Library") }
                    Spacer(Modifier.weight(1f))
                    Text("NOW PLAYING", fontSize = 11.sp, color = SecondaryText, letterSpacing = 1.6.sp)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }
                Artwork(item?.mediaMetadata?.artworkUri?.toString(), Modifier.weight(1f).aspectRatio(1f))
                Spacer(Modifier.height(14.dp))
                PlayerDetails(state, viewModel, onLibrary = null, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PlayerDetails(
    state: PlayerUiState,
    viewModel: MainViewModel,
    onLibrary: (() -> Unit)?,
    modifier: Modifier,
) {
    val metadata = state.currentItem?.mediaMetadata
    var pendingSeek by remember { mutableStateOf<Float?>(null) }
    val sliderValue = pendingSeek ?: state.positionMs.toFloat()
    Column(modifier, verticalArrangement = Arrangement.Center) {
        if (onLibrary != null) {
            IconButton(onClick = onLibrary, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Library", tint = SecondaryText)
            }
        }
        Text(
            metadata?.title?.toString() ?: "Choose a song",
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        Text(metadata?.artist?.toString() ?: "Quiet Player", color = SecondaryText, fontSize = 15.sp, maxLines = 1)
        Text(metadata?.albumTitle?.toString().orEmpty(), color = SecondaryText.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.height(20.dp))
        Slider(
            value = sliderValue.coerceIn(0f, state.durationMs.coerceAtLeast(1L).toFloat()),
            onValueChange = { pendingSeek = it },
            onValueChangeFinished = { pendingSeek?.roundToLong()?.let(viewModel::seekTo); pendingSeek = null },
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
            enabled = state.currentItem != null,
            modifier = Modifier.height(24.dp).testTag("seek_bar"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(sliderValue.toLong()), color = SecondaryText, fontSize = 11.sp)
            Text(formatDuration(state.durationMs), color = SecondaryText, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        PlaybackControls(state, viewModel)
    }
}

@Composable
private fun PlaybackControls(state: PlayerUiState, viewModel: MainViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::toggleShuffle, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffle) Accent else SecondaryText)
        }
        IconButton(onClick = viewModel::previous, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(30.dp))
        }
        FilledIconButton(
            onClick = viewModel::togglePlay,
            modifier = Modifier.size(58.dp).testTag("play_pause"),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryText, contentColor = Background),
        ) {
            Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else "Play", Modifier.size(32.dp))
        }
        IconButton(onClick = viewModel::next, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Default.SkipNext, "Next", Modifier.size(30.dp))
        }
        IconButton(onClick = viewModel::cycleRepeat, modifier = Modifier.size(48.dp)) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                "Repeat",
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) SecondaryText else Accent,
            )
        }
    }
}

@Composable
private fun QueuePane(state: PlayerUiState, viewModel: MainViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    Column(modifier.padding(start = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Up Next", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("${state.queue.size} tracks", fontSize = 12.sp, color = SecondaryText)
            }
            Icon(Icons.Default.DragHandle, "Long press and drag to reorder", tint = SecondaryText, modifier = Modifier.padding(10.dp))
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("queue")) {
            itemsIndexed(state.queue, key = { _, item -> item.mediaId }) { index, item ->
                QueueRow(
                    item = item,
                    playing = index == state.currentIndex,
                    onClick = { viewModel.playQueueIndex(index) },
                    onRemove = { viewModel.removeQueueItem(index) },
                    onMove = { direction -> viewModel.moveQueueItem(index, (index + direction).coerceIn(0, state.queue.lastIndex)) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: MediaItem,
    playing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var dragTotal by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (playing) SurfaceRaised else Color.Transparent, RoundedCornerShape(8.dp))
            .pointerInput(item.mediaId) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = { dragTotal = 0f },
                    onDragCancel = { dragTotal = 0f },
                ) { change, amount ->
                    change.consume()
                    dragTotal += amount.y
                    if (dragTotal > 52f) { onMove(1); dragTotal = 0f }
                    if (dragTotal < -52f) { onMove(-1); dragTotal = 0f }
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.mediaMetadata.artworkUri?.toString(), Modifier.size(42.dp))
        Spacer(Modifier.width(11.dp))
        if (playing) {
            Icon(Icons.Default.GraphicEq, null, tint = Accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(item.mediaMetadata.artist?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = SecondaryText)
        }
        Text(formatDuration(item.mediaMetadata.extras?.getLong(DURATION_KEY) ?: 0L), color = SecondaryText, fontSize = 11.sp)
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Close, "Remove from queue", Modifier.size(17.dp), tint = SecondaryText)
        }
    }
}

@Composable
private fun ExternalSessionScreen(
    state: ExternalSessionState,
    hasAccess: Boolean,
    requestAccess: () -> Unit,
    onLibrary: () -> Unit,
) {
    if (!hasAccess) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Cast, null, Modifier.size(42.dp), tint = Accent)
            Spacer(Modifier.height(18.dp))
            Text("Control another music app", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Notification access lets Quiet Player display and control the active Apple Music media session. Notification content is not stored.",
                color = SecondaryText,
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = requestAccess) { Text("Open notification access") }
            TextButton(onClick = onLibrary) { Text("Back to local library") }
        }
        return
    }

    var clock by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.isPlaying, state.positionMs) {
        while (state.isPlaying) {
            clock = android.os.SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(500)
        }
    }
    val position = state.estimatedPosition(clock)
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("external_session")) {
        val landscape = maxWidth > maxHeight
        val detail: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(state.artwork, Modifier.weight(0.47f).aspectRatio(1f))
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(0.53f), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onLibrary) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Library") }
                        Text(state.appName ?: "External session", color = Accent, fontSize = 13.sp)
                    }
                    Text(state.title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(7.dp))
                    Text(state.artist, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.album, color = SecondaryText.copy(alpha = .72f), fontSize = 13.sp, maxLines = 1)
                    Spacer(Modifier.height(18.dp))
                    Slider(
                        value = position.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1L).toFloat()),
                        onValueChange = { ExternalSessionBridge.seekTo(it.toLong()) },
                        valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                        enabled = state.connected && state.durationMs > 0,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(position), color = SecondaryText, fontSize = 11.sp)
                        Text(formatDuration(state.durationMs), color = SecondaryText, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ExternalSessionBridge::previous, modifier = Modifier.size(54.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(30.dp))
                        }
                        FilledIconButton(
                            onClick = ExternalSessionBridge::togglePlay,
                            modifier = Modifier.size(60.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryText, contentColor = Background),
                        ) {
                            Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", Modifier.size(32.dp))
                        }
                        IconButton(onClick = ExternalSessionBridge::next, modifier = Modifier.size(54.dp)) {
                            Icon(Icons.Default.SkipNext, "Next", Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
        val queue: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("External Queue", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (state.queue.isEmpty()) "${state.appName ?: "The source app"} is not sharing its queue" else "${state.queue.size} tracks from ${state.appName}",
                    color = SecondaryText,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn {
                    itemsIndexed(state.queue, key = { _, item -> item.id }) { _, item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { ExternalSessionBridge.playQueueItem(item.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(item.artwork, Modifier.size(44.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, color = SecondaryText, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                detail(Modifier.weight(.62f).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
                queue(Modifier.weight(.38f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                detail(Modifier.weight(.65f).fillMaxWidth())
                Box(Modifier.height(1.dp).fillMaxWidth().background(Hairline))
                queue(Modifier.weight(.35f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Artwork(uri: Any?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceRaised), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
                model = uri,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                success = { SubcomposeAsyncImageContent() },
                loading = { ArtworkPlaceholder() },
                error = { ArtworkPlaceholder() },
            )
    }
}

@Composable
private fun ArtworkPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Album, null, tint = SecondaryText.copy(alpha = 0.35f), modifier = Modifier.fillMaxSize(0.28f))
    }
}

@Composable
private fun MiniPlayer(state: PlayerUiState, onClick: () -> Unit, onToggle: () -> Unit) {
    val item = state.currentItem ?: return
    Row(
        Modifier.fillMaxWidth().background(Surface).clickable(onClick = onClick).navigationBarsPadding().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.mediaMetadata.artworkUri?.toString(), Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaMetadata.artist?.toString().orEmpty(), color = SecondaryText, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = onToggle) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(message, color = SecondaryText)
    }
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
