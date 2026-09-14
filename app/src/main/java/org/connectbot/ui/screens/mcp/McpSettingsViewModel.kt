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

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.mcp.McpService
import org.connectbot.mcp.McpServerManager
import timber.log.Timber
import javax.inject.Inject

/** MCP 服务器设置界面的 UI 状态 */
data class McpSettingsUiState(
    /** MCP 服务器是否已启用 */
    val mcpEnabled: Boolean = false,
    /** MCP 服务器监听端口 */
    val mcpPort: Int = McpServerManager.DEFAULT_PORT,
    /** 服务器是否正在运行 */
    val isRunning: Boolean = false,
    /** 连接状态摘要 */
    val connectionStatusSummary: String = "",
    /** 服务器地址 */
    val serverUrl: String = "http://localhost:${McpServerManager.DEFAULT_PORT}/mcp",
)

/**
 * MCP 服务器设置 ViewModel。
 *
 * 管理 MCP 服务器的启用/禁用、端口配置等设置。
 */
@HiltViewModel
class McpSettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    @ApplicationContext private val context: android.content.Context,
    private val dispatchers: CoroutineDispatchers,
    private val mcpServerManager: McpServerManager,
) : ViewModel() {

    /** 是否显示端口编辑对话框 */
    private val _showPortDialog = MutableStateFlow(false)
    val showPortDialog: StateFlow<Boolean> = _showPortDialog.asStateFlow()

    /** 关闭端口编辑对话框 */
    fun dismissPortDialog() {
        _showPortDialog.value = false
    }

    /** 打开端口编辑对话框 */
    fun openPortDialog() {
        _showPortDialog.value = true
    }

    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<McpSettingsUiState> = _uiState.asStateFlow()

    init {
        // 订阅服务器运行状态流：服务异步启动/停止后 UI 自动跟随，不再读到滞后快照
        viewModelScope.launch(dispatchers.default) {
            mcpServerManager.runningState.collect { running ->
                _uiState.update {
                    it.copy(
                        isRunning = running,
                        serverUrl = "http://localhost:${mcpServerManager.port}/mcp",
                    )
                }
            }
        }
    }

    private fun loadSettings(): McpSettingsUiState {
        val enabled = prefs.getBoolean(McpService.KEY_MCP_ENABLED, false)
        val port = prefs.getInt(KEY_MCP_PORT, McpServerManager.DEFAULT_PORT)
        return McpSettingsUiState(
            mcpEnabled = enabled,
            mcpPort = port,
            isRunning = mcpServerManager.isRunning,
            serverUrl = "http://localhost:$port/mcp",
        )
    }

    /** 更新 MCP 服务器启用状态 */
    fun updateMcpEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(McpService.KEY_MCP_ENABLED, enabled) }
        _uiState.update { it.copy(mcpEnabled = enabled) }

        if (enabled) {
            McpService.start(context)
        } else {
            McpService.stop(context)
        }
        refreshRunningState()
    }

    /** 更新 MCP 服务器端口 */
    fun updateMcpPort(port: Int) {
        _showPortDialog.value = false
        if (port in 1..65535) {
            prefs.edit { putInt(KEY_MCP_PORT, port) }
            _uiState.update { it.copy(mcpPort = port) }

            // 如果服务器正在运行，重启以应用新端口
            if (mcpServerManager.isRunning) {
                McpService.stop(context)
                if (_uiState.value.mcpEnabled) {
                    McpService.start(context)
                }
                refreshRunningState()
            }
        }
    }

    /** 立即同步一次运行状态（启动/停止后的首帧刷新） */
    private fun refreshRunningState() {
        _uiState.update {
            it.copy(
                isRunning = mcpServerManager.isRunning,
                serverUrl = "http://localhost:${mcpServerManager.port}/mcp",
            )
        }
    }

    companion object {
        private const val KEY_MCP_PORT = McpService.KEY_MCP_PORT
    }
}
