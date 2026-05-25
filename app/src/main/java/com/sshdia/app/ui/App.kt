@file:OptIn(ExperimentalMaterial3Api::class)

package com.sshdia.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sshdia.app.data.HostProfile
import com.sshdia.app.data.HostStore
import com.sshdia.app.ssh.SshClient
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

    when (val current = screen) {
        Screen.HostList -> HostListScreen(
            hosts = hosts,
            onAdd = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it) },
            onDelete = { hosts = store.delete(it.id) },
            onConnect = { screen = Screen.Session(it) }
        )

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
    onAdd: () -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
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
                        onConnect = { onConnect(host) },
                        onEdit = { onEdit(host) },
                        onDelete = { onDelete(host) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HostCard(
    host: HostProfile,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                    text = host.target,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun run(cmd: String) {
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "1단계: 명령 실행 모드입니다. 대화형 터미널(vim 등)은 다음 단계에서 추가됩니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0B1020))
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                Text(
                    text = output.ifBlank { "여기에 명령 결과가 표시됩니다.\n아래 버튼이나 입력창을 사용하세요." },
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
                OutlinedButton(onClick = { run("uname -a") }, enabled = !running) {
                    Text("연결 테스트")
                }
                OutlinedButton(
                    onClick = { run("echo \"한글 출력 테스트 가나다라마바사\"") },
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
                        run(cmd)
                    },
                    enabled = !running && command.isNotBlank()
                ) { Text("실행") }
            }
        }
    }
}
