/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ui.screens.mcp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.ui.PreviewScreen
import org.connectbot.ui.theme.ConnectBotTheme

/**
 * MCP 服务器设置界面。
 *
 * 提供 MCP 服务器的开关、端口配置、连接状态等信息。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val showPortDialog by viewModel.showPortDialog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_mcp_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item {
                // 主开关
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.pref_mcp_enabled_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.pref_mcp_enabled_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.mcpEnabled,
                            onCheckedChange = viewModel::updateMcpEnabled,
                        )
                    },
                )
            }

            item { HorizontalDivider() }

            item {
                // 端口配置
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.pref_mcp_port_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.pref_mcp_port_summary, uiState.mcpPort))
                    },
                    modifier = Modifier
                        .clickable {
                            viewModel.openPortDialog()
                        },
                )
            }

            item { HorizontalDivider() }

            item {
                // 服务器状态
                val statusText = when {
                    !uiState.mcpEnabled -> stringResource(R.string.mcp_status_off)
                    uiState.isRunning -> stringResource(R.string.mcp_status_running, uiState.serverUrl)
                    else -> stringResource(R.string.mcp_status_stopped)
                }
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.pref_mcp_status_title))
                    },
                    supportingContent = {
                        Text(statusText)
                    },
                )
            }

            item { HorizontalDivider() }

            // 工具列表说明
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.mcp_tools_title))
                    },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.mcp_tool_host_management))
                            Text(stringResource(R.string.mcp_tool_port_forward))
                            Text(stringResource(R.string.mcp_tool_terminal_command))
                        }
                    },
                )
            }
        }
    }

    // 端口编辑对话框
    // 端口编辑对话框
    if (showPortDialog) {
        McpPortDialog(
            currentPort = uiState.mcpPort,
            onDismiss = viewModel::dismissPortDialog,
            onConfirm = viewModel::updateMcpPort,
        )
    }
}

/** 端口编辑对话框 */
@Composable
private fun McpPortDialog(
    currentPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var portText by remember { mutableStateOf(currentPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_mcp_port_title)) },
        text = {
            Column {
                Text(stringResource(R.string.mcp_port_hint))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(portText.toIntOrNull() ?: currentPort)
                },
            ) {
                Text(stringResource(R.string.button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}

/** 预览 */
@PreviewScreen
@Composable
private fun McpSettingsPreview() {
    ConnectBotTheme {
        McpSettingsScreen(onNavigateBack = {})
    }
}
