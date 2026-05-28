@file:OptIn(ExperimentalMaterial3Api::class)

package com.sshdia.app.ui

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sshdia.app.data.HostProfile
import com.sshdia.app.data.HostStore
import com.sshdia.app.ssh.SessionManager
import com.sshdia.app.ssh.SshClient
import com.sshdia.app.terminal.TerminalInputView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Screen {
    data object HostList : Screen
    data class Edit(val profile: HostProfile?) : Screen
    data class Session(val profile: HostProfile) : Screen
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { HostStore(context) }
    var hosts by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.HostList) }
    var refresh by remember { mutableStateOf(0) }

    when (val current = screen) {
        Screen.HostList -> {
            val activeIds = run { refresh; SessionManager.activeIds() }
            HostListScreen(
                hosts = hosts,
                activeIds = activeIds,
                onAdd = { screen = Screen.Edit(null) },
                onEdit = { screen = Screen.Edit(it) },
                onDelete = {
                    SessionManager.close(context, it.id)
                    hosts = store.delete(it.id)
                },
                onDuplicate = { hosts = store.duplicate(it.id) },
                onTerminate = {
                    SessionManager.close(context, it.id)
                    refresh++
                },
                onConnect = { screen = Screen.Session(it) }
            )
        }

        is Screen.Edit -> HostEditScreen(
            initial = current.profile,
            onCancel = { screen = Screen.HostList },
            onSave = {
                hosts = store.upsert(it)
                screen = Screen.HostList
            }
        )

        is Screen.Session -> SessionScreen(
            profile = current.profile,
            onBack = { screen = Screen.HostList }
        )
    }
}

@Composable
private fun HostListScreen(
    hosts: List<HostProfile>,
    activeIds: Set<String>,
    onAdd: () -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
    onDuplicate: (HostProfile) -> Unit,
    onTerminate: (HostProfile) -> Unit,
    onConnect: (HostProfile) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("sshdia") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "호스트 추가")
            }
        }
    ) { padding ->
        if (hosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "저장된 호스트가 없습니다.\n오른쪽 아래 + 버튼으로 추가하세요.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                hosts.forEach { host ->
                    HostCard(
                        host = host,
                        active = host.id in activeIds,
                        onConnect = { onConnect(host) },
                        onEdit = { onEdit(host) },
                        onDelete = { onDelete(host) },
                        onDuplicate = { onDuplicate(host) },
                        onTerminate = { onTerminate(host) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HostCard(
    host: HostProfile,
    active: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onTerminate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() }
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (active) "● 연결됨 · ${host.target}" else host.target,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (active) {
                IconButton(onClick = onTerminate) {
                    Icon(Icons.Default.Stop, contentDescription = "세션 종료", tint = Color(0xFFEF4444))
                }
            }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = "복사")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "편집")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제")
            }
        }
    }
}

@Composable
private fun HostEditScreen(
    initial: HostProfile?,
    onCancel: () -> Unit,
    onSave: (HostProfile) -> Unit
) {
    BackHandler { onCancel() }

    var label by remember { mutableStateOf(initial?.label ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var key by remember { mutableStateOf(initial?.privateKeyPem ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "호스트 추가" else "호스트 편집") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("이름 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("호스트 (IP 또는 도메인)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("포트") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("사용자 이름") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호 또는 키 암호 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("개인키 PEM (선택)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text("취소") }
                Button(
                    onClick = {
                        val profile = (initial ?: HostProfile()).copy(
                            label = label.trim(),
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 22,
                            username = username.trim(),
                            password = password,
                            privateKeyPem = key.trim()
                        )
                        onSave(profile)
                    },
                    enabled = host.isNotBlank() && username.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("저장") }
            }
        }
    }
}

@Composable
private fun SessionScreen(
    profile: HostProfile,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var terminalMode by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            profile.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            profile.target,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { terminalMode = !terminalMode }) {
                        if (terminalMode) {
                            Icon(Icons.Default.Code, contentDescription = "명령 실행 모드")
                        } else {
                            Icon(Icons.Default.Terminal, contentDescription = "터미널 모드")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (terminalMode) {
                TerminalPane(
                    profile = profile,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CommandPane(
                    profile = profile,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


@Composable
private fun TerminalPane(profile: HostProfile, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val fontSize = 13.sp
    val textStyle = remember {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            color = Color(0xFFE5E7EB)
        )
    }

    // mutableStateOf so reconnect() can swap in a fresh session
    var session by remember(profile.id) { mutableStateOf(SessionManager.getOrCreate(context, profile)) }
    var screenText by remember { mutableStateOf(session.screenText) }
    var cursorRow by remember { mutableStateOf(session.cursorRow) }
    var cursorIdx by remember { mutableStateOf(session.cursorCharIndex) }
    var status by remember { mutableStateOf(session.status) }
    var isClosed by remember { mutableStateOf(session.closed) }
    var composing by remember { mutableStateOf("") }

    DisposableEffect(session) {
        val l = {
            screenText = session.screenText
            cursorRow = session.cursorRow
            cursorIdx = session.cursorCharIndex
            status = session.status
            isClosed = session.closed
        }
        session.listener = l
        l()
        onDispose { if (session.listener === l) session.listener = null }
    }

    fun reconnect() {
        composing = ""
        SessionManager.close(context, profile.id)
        session = SessionManager.getOrCreate(context, profile)
        // DisposableEffect will re-run because session changed,
        // resetting screenText/status/isClosed via the new listener
        screenText = ""
        status = "연결 중..."
        isClosed = false
    }

    fun send(text: String) {
        session.write(text)
    }

    // Height reserved at the bottom for the floating function-key toolbar.
    val keyRowHeight = 48.dp

    Box(modifier = modifier.imePadding()) {
        // Terminal display — padded at the bottom so content is never hidden behind the key row.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = keyRowHeight)
                .background(Color(0xFF0B1020))
                .clipToBounds()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cellW = with(density) { fontSize.toPx() } * 0.6f
                val cellH = with(density) { fontSize.toPx() } * 1.45f
                val padPx = with(density) { 16.dp.toPx() }
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val cols = ((widthPx - padPx) / cellW).toInt().coerceIn(20, 400)
                val rows = (heightPx / cellH).toInt().coerceIn(6, 200)

                LaunchedEffect(cols, rows) {
                    session.resize(cols, rows)
                }

                // Show terminal output only when connected and has content.
                if (screenText.isNotBlank() && !isClosed) {
                    val rendered = remember(screenText, cursorRow, cursorIdx, composing) {
                        buildTerminalText(screenText, cursorRow, cursorIdx, composing)
                    }
                    Text(
                        text = rendered,
                        style = textStyle,
                        softWrap = false,
                        modifier = Modifier.padding(8.dp)
                    )
                    AndroidView(
                        factory = { ctx -> TerminalInputView(ctx) },
                        modifier = Modifier.matchParentSize(),
                        update = { view ->
                            view.onInput = { text -> send(text) }
                            view.onComposing = { text -> composing = text }
                        }
                    )
                }
            }

            // Connection status overlay — shown while connecting or after disconnect.
            if (screenText.isBlank() || isClosed) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        if (!isClosed) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8))
                        }
                        Text(
                            text = status,
                            color = if (isClosed) Color(0xFFFC8181) else Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        if (isClosed) {
                            Button(onClick = ::reconnect) {
                                Text("재연결")
                            }
                        }
                    }
                }
            }
        }

        // Function-key toolbar — floats at the bottom of the visible area.
        // When the soft keyboard is open this sits right above it (imePadding shrinks the Box).
        // All keys in a single horizontally-scrollable row to avoid stealing terminal height.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(keyRowHeight)
                .background(Color(0xFF1E293B))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton("Esc") { send("\u001b") }
            KeyButton("Tab") { send("\t") }
            KeyButton("Ctrl-C") { send("\u0003") }
            KeyButton("↑") { send("\u001b[A") }
            KeyButton("↓") { send("\u001b[B") }
            KeyButton("←") { send("\u001b[D") }
            KeyButton("→") { send("\u001b[C") }
            KeyButton("Ctrl-D") { send("\u0004") }
            KeyButton("Ctrl-Z") { send("\u001a") }
            KeyButton("Ctrl-L") { send("\u000c") }
            KeyButton("Home") { send("\u001b[H") }
            KeyButton("End") { send("\u001b[F") }
            KeyButton("PgUp") { send("\u001b[5~") }
            KeyButton("PgDn") { send("\u001b[6~") }
        }
    }
}

@Composable
private fun KeyButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(text, fontSize = 12.sp)
    }
}

/**
 * Render the terminal text, highlighting the cell at the cursor. [cursorRow] is the
 * row index within [text] (split on newlines); pass a negative value to disable the
 * cursor (e.g. while showing a status message).
 */
private fun buildTerminalText(
    text: String,
    cursorRow: Int,
    cursorIdx: Int,
    composing: String
): AnnotatedString {
    val lines = text.split('\n')
    if (cursorRow < 0 || cursorRow >= lines.size) return AnnotatedString(text)
    val idx = cursorIdx.coerceAtLeast(0)
    return buildAnnotatedString {
        lines.forEachIndexed { r, line ->
            if (r > 0) append("\n")
            if (r == cursorRow) {
                val needed = idx + 1
                val padded =
                    if (line.length < needed) line + " ".repeat(needed - line.length) else line
                append(padded.substring(0, idx))
                if (composing.isNotEmpty()) {
                    withStyle(SpanStyle(background = Color(0xFF334155), color = Color(0xFFFFFFFF))) {
                        append(composing)
                    }
                }
                withStyle(SpanStyle(background = Color(0xFF38BDF8), color = Color(0xFF0B1020))) {
                    append(padded.substring(idx, idx + 1))
                }
                append(padded.substring(idx + 1))
            } else {
                append(line)
            }
        }
    }
}

@Composable
private fun CommandPane(profile: HostProfile, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun runCmd(cmd: String) {
        if (cmd.isBlank() || running) return
        running = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SshClient.runCommand(profile, cmd) }
            }
            val body = result.fold(
                onSuccess = { r -> r.output.trimEnd('\n') + "\n[exit ${r.exitStatus}]" },
                onFailure = { e -> "[오류] ${e.message ?: e.toString()}" }
            )
            output = buildString {
                append(output)
                append("$ ").append(cmd).append('\n')
                append(body).append("\n\n")
            }
            running = false
        }
    }

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0B1020))
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            Text(
                text = output.ifBlank { "단발 명령을 실행하고 결과를 봅니다." },
                color = Color(0xFFE5E7EB),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { runCmd("uname -a") }, enabled = !running) {
                Text("연결 테스트")
            }
            OutlinedButton(
                onClick = { runCmd("echo \"한글 출력 테스트 가나다라마바사\"") },
                enabled = !running
            ) {
                Text("한글 테스트")
            }
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("명령어") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    val cmd = command
                    command = ""
                    runCmd(cmd)
                },
                enabled = !running && command.isNotBlank()
            ) { Text("실행") }
        }
    }
}
