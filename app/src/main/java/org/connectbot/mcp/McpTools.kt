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

package org.connectbot.mcp

import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * 终端管理器提供者。
 *
 * TerminalManager 是 Android Service，不能通过 Hilt 直接注入。
 * 通过此接口解耦依赖，实现由 MainActivity 绑定后设置。
 */
interface TerminalManagerProvider {
    /** 获取当前 TerminalManager 实例，未绑定时返回 null */
    fun get(): TerminalManager?
}

/**
 * MCP 工具集。
 *
 * 实现 ConnectBot 的 MCP 工具，供 AI 助手调用：
 * - 主机管理：列出、创建、更新、删除主机
 * - 端口转发：管理端口转发规则
 * - 终端命令：在已连接主机上执行命令
 */
@Singleton
class McpTools @Inject constructor(
    private val hostRepository: HostRepository,
    private val terminalManagerProvider: TerminalManagerProvider,
) {

    /** 列出所有主机 */
    fun listHosts(): String {
        return runBlocking {
            val hosts = hostRepository.getHosts()
            if (hosts.isEmpty()) {
                "没有配置任何主机。"
            } else {
                hosts.joinToString("\n") { host ->
                    "ID: ${host.id}, 昵称: ${host.nickname}, 协议: ${host.protocol}, " +
                        "主机: ${host.hostname}:${host.port}, 用户: ${if (host.username.isEmpty()) "(未设置)" else host.username}"
                }
            }
        }
    }

    /** 获取单个主机详情 */
    fun getHost(hostId: String): String {
        val host = findHostByIdOrName(hostId)
        return if (host != null) {
            formatHostDetail(host)
        } else {
            "未找到主机: $hostId"
        }
    }

    /** 创建新主机 */
    fun createHost(
        nickname: String,
        hostname: String,
        port: Int = 22,
        username: String = "",
        protocol: String = "ssh",
    ): String {
        return runBlocking {
            val newHost = Host(
                nickname = nickname,
                hostname = hostname,
                port = port,
                username = username,
                protocol = protocol,
            )
            val savedHost = hostRepository.saveHost(newHost)
            "已创建主机: ${savedHost.nickname} (ID: ${savedHost.id})"
        }
    }

    /** 更新现有主机 */
    fun updateHost(
        hostId: String,
        nickname: String? = null,
        hostname: String? = null,
        port: Int? = null,
        username: String? = null,
    ): String {
        val host = findHostByIdOrName(hostId)
        return if (host != null) {
            runBlocking {
                val updatedHost = host.copy(
                    nickname = nickname ?: host.nickname,
                    hostname = hostname ?: host.hostname,
                    port = port ?: host.port,
                    username = username ?: host.username,
                )
                hostRepository.saveHost(updatedHost)
                "已更新主机: ${updatedHost.nickname}"
            }
        } else {
            "未找到主机: $hostId"
        }
    }

    /** 删除主机 */
    fun deleteHost(hostId: String): String {
        val host = findHostByIdOrName(hostId)
        return if (host != null) {
            runBlocking {
                hostRepository.deleteHost(host)
            }
            "已删除主机: ${host.nickname}"
        } else {
            "未找到主机: $hostId"
        }
    }

    /** 列出指定主机的端口转发 */
    fun listPortForwards(hostId: String): String {
        val host = findHostByIdOrName(hostId)
        return if (host != null) {
            val forwards = getPortForwardsForHost(host.id)
            if (forwards.isEmpty()) {
                "主机 ${host.nickname} 没有配置端口转发。"
            } else {
                forwards.joinToString("\n") { pf ->
                    "ID: ${pf.id}, 名称: ${pf.nickname}, 类型: ${pf.type}, " +
                        "源: ${pf.sourceAddr}:${pf.sourcePort} -> 目标: ${pf.destAddr ?: "?"}:${pf.destPort}"
                }
            }
        } else {
            "未找到主机: $hostId"
        }
    }

    /** 创建端口转发 */
    fun createPortForward(
        hostId: String,
        nickname: String,
        type: String,
        sourcePort: Int,
        destAddr: String = "localhost",
        destPort: Int = 0,
    ): String {
        val host = findHostByIdOrName(hostId)
        return if (host != null) {
            runBlocking {
                val portForward = PortForward(
                    hostId = host.id,
                    nickname = nickname,
                    type = type,
                    sourcePort = sourcePort,
                    destAddr = destAddr,
                    destPort = destPort,
                )
                val saved = hostRepository.savePortForward(portForward)
                "已创建端口转发: ${saved.nickname} (ID: ${saved.id})"
            }
        } else {
            "未找到主机: $hostId"
        }
    }

    /** 删除端口转发 */
    fun deletePortForward(forwardId: Long): String {
        return runBlocking {
            val hosts = hostRepository.getHosts()
            val forward = hosts.flatMap { host ->
                hostRepository.getPortForwardsForHost(host.id)
            }.find { it.id == forwardId }

            if (forward != null) {
                hostRepository.deletePortForward(forward)
                "已删除端口转发: ${forward.nickname}"
            } else {
                "未找到端口转发 ID: $forwardId"
            }
        }
    }

    /** 获取所有连接状态 */
    fun getConnectionStatus(): String {
        val manager = terminalManagerProvider.get()
        if (manager == null) {
            return "终端管理器未就绪，请确保应用已启动。"
        }
        val bridges = manager.bridgesFlow.value
        if (bridges.isEmpty()) {
            return "当前没有活跃的终端连接。"
        }
        return bridges.joinToString("\n") { bridge ->
            val state = when {
                bridge.isConnecting -> "连接中"
                bridge.isDisconnected -> "已断开"
                else -> "已连接"
            }
            "${bridge.host.nickname} (${bridge.host.hostname}:${bridge.host.port}) - $state"
        }
    }

    /** 在已连接主机上执行命令，并等待终端输出稳定后一并返回 */
    fun executeCommand(hostId: String, command: String, waitForOutputMs: Int = 3000): String {
        val manager = terminalManagerProvider.get()
            ?: return "终端管理器未就绪，请确保应用已启动。"

        val host = findHostByIdOrName(hostId)
            ?: return "未找到主机: $hostId"

        val bridge = manager.bridgesFlow.value.find { it.host.id == host.id }
        if (bridge == null || bridge.isDisconnected) {
            return "主机 ${host.nickname} 当前没有活跃连接，请先连接该主机。"
        }

        val data = (command + "\n").toByteArray(Charsets.UTF_8)
        bridge.sendBytes(data)

        val output = readStableOutput(bridge, waitForOutputMs.coerceIn(0, 15000))
        return "命令已发送到 ${host.nickname}: $command\n\n--- 终端输出 ---\n$output"
    }

    /** 读取指定主机终端当前屏幕及滚动缓冲的完整文本 */
    fun getTerminalScreen(hostId: String): String {
        val manager = terminalManagerProvider.get()
            ?: return "终端管理器未就绪，请确保应用已启动。"

        val host = findHostByIdOrName(hostId)
            ?: return "未找到主机: $hostId"

        val bridge = manager.bridgesFlow.value.find { it.host.id == host.id }
        if (bridge == null || bridge.isDisconnected) {
            return "主机 ${host.nickname} 当前没有活跃连接。"
        }

        val screen = readTerminalText(bridge, maxScrollbackLines = 100)
        return if (screen.isNullOrBlank()) {
            val diag = diagnostics(bridge, maxScrollbackLines = 100)
            "终端 ${host.nickname} 屏幕内容不可用。$diag"
        } else {
            "--- ${host.nickname} 终端屏幕（含最近滚动缓冲）---\n$screen"
        }
    }

    /**
     * 通过反射读取终端快照的屏幕与滚动缓冲文本。
     * 快照类（TerminalEmulatorImpl/TerminalSnapshot）均为 internal，只能反射访问；
     * 返回 null 表示快照不可用，非 null 时为拼接文本（诊断信息已内嵌在不可用文本中）。 */
    private fun readTerminalText(bridge: TerminalBridge, maxScrollbackLines: Int): String? {
        val result = runCatching {
            refreshSnapshot(bridge)
            val emulator = bridge.terminalEmulator
            val snapshotMethod = emulator.javaClass.methods
                .firstOrNull { it.name.startsWith("getSnapshot") }
            if (snapshotMethod == null) {
                return "[诊断] 未找到 getSnapshot 方法, class=${emulator.javaClass.name}"
            }
            val snapshotFlow = snapshotMethod.invoke(emulator)
                ?: return "[诊断] getSnapshot 方法返回 null"
            // getSnapshot$lib 返回 StateFlow，再取 getValue() 得到当前快照
            val getValue = snapshotFlow.javaClass.methods
                .firstOrNull { it.name == "getValue" && it.parameterCount == 0 }
                ?: return "[诊断] StateFlow.getValue 不可用"
            val snapshot = getValue.invoke(snapshotFlow)
                ?: return "[诊断] 快照对象为空"

            val scrollback = snapshot.javaClass.getMethod("getScrollback")
                .invoke(snapshot) as? List<*> ?: return "[诊断] getScrollback 调用失败"
            val lines = snapshot.javaClass.getMethod("getLines")
                .invoke(snapshot) as? List<*> ?: return "[诊断] getLines 调用失败"
            val firstLine = lines.firstOrNull()
                ?: return "[诊断] lines 为空 (scrollback=${scrollback.size})"
            val getText = firstLine.javaClass.getMethod("getText")

            val screenLines = lines.mapNotNull { getText.invoke(it) as? String }
            val scrollbackLines = scrollback.mapNotNull { getText.invoke(it) as? String }
                .takeLast(maxScrollbackLines)

            val text = (scrollbackLines + screenLines)
                .joinToString("\n")
                .trimEnd()
                .takeLast(8000)
            if (text.isBlank()) {
                "[诊断] 快照有 ${scrollback.size + lines.size} 行但全为空白"
            } else {
                text
            }
        }.getOrElse { "[诊断] 反射异常: ${it::class.java.simpleName}: ${it.message}" }

        return if (result.startsWith("[诊断]")) null else result
    }

    /** 读取指定主机终端最近一次命令及其输出（历史上下文） */
    fun getLastCommandOutput(hostId: String): String {
        val manager = terminalManagerProvider.get()
            ?: return "终端管理器未就绪，请确保应用已启动。"

        val host = findHostByIdOrName(hostId)
            ?: return "未找到主机: $hostId"

        val bridge = manager.bridgesFlow.value.find { it.host.id == host.id }
        if (bridge == null || bridge.isDisconnected) {
            return "主机 ${host.nickname} 当前没有活跃连接。"
        }

        refreshSnapshot(bridge)
        val output = bridge.terminalEmulator.getLastCommandOutput()
        if (!output.isNullOrBlank()) {
            return "--- ${host.nickname} 最近命令输出 ---\n$output"
        }

        // 远端 shell 未启用 OSC 133 shell integration 时无 COMMAND_FINISHED 标记，
        // 退化为直接读取终端屏幕与滚动缓冲文本
        val screen = readTerminalText(bridge, maxScrollbackLines = 60)
        val diag = diagnostics(bridge, maxScrollbackLines = 60)
        return if (screen.isNullOrBlank()) {
            "终端 ${host.nickname} 目前没有可读取的命令输出。$diag"
        } else {
            "--- ${host.nickname} 终端屏幕（含最近滚动缓冲）---\n$screen"
        }
    }

    /**
     * 轮询读取终端最近命令输出，直到内容稳定（连续两次采样相同）或超时。
     * 每次采样前主动刷新终端快照：快照仅在终端界面渲染时自动生成，
     * 应用退到后台时无人调用渲染管线，必须显式触发。 */
    private fun readStableOutput(bridge: TerminalBridge, waitMs: Int): String {
        val deadline = System.currentTimeMillis() + waitMs
        var last: String? = null
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(300)
            refreshSnapshot(bridge)
            val current = bridge.terminalEmulator.getLastCommandOutput() ?: ""
            if (current == last && current.isNotBlank()) break
            last = current
        }
        val output = last?.takeIf { it.isNotBlank() }
            ?: readTerminalText(bridge, maxScrollbackLines = 30)?.lines()
                ?.takeLast(30)?.joinToString("\n")
            ?: "(暂无输出)"
        if (output == "(暂无输出)") {
            return "(暂无输出) 诊断: ${diagnostics(bridge, maxScrollbackLines = 30)}"
        }
        return output
    }

    /** 生成终端快照不可用时的诊断信息（用于排查反射链路失败点） */
    private fun diagnostics(bridge: TerminalBridge, maxScrollbackLines: Int): String {
        val emulator = bridge.terminalEmulator
        val snapshotMethod = emulator.javaClass.methods
            .firstOrNull { it.name.startsWith("getSnapshot") } ?: return "未找到 getSnapshot 方法"
        val snapshotFlow = runCatching { snapshotMethod.invoke(emulator) }.getOrNull()
            ?: return "getSnapshot 调用失败"
        val getValue = snapshotFlow.javaClass.methods
            .firstOrNull { it.name == "getValue" && it.parameterCount == 0 } ?: return "getValue 不可用"
        val snapshot = runCatching { getValue.invoke(snapshotFlow) }.getOrNull()
            ?: return "快照值为空"
        val lines = runCatching { snapshot.javaClass.getMethod("getLines").invoke(snapshot) }.
            getOrNull() as? List<*> ?: return "getLines 失败"
        val scrollback = runCatching {
            snapshot.javaClass.getMethod("getScrollback").invoke(snapshot)
        }.getOrNull() as? List<*> ?: return "getScrollback 失败"
        val screenText = lines.joinToString("\n") { runCatching { it.toString() }.getOrDefault("") }
        return "[快照 lines=${lines.size}, scrollback=${scrollback.size}, 屏幕=${screenText.length}字符]"
    }

    /** 主动让终端模拟器处理待渲染的更新并生成快照（与界面渲染无关）。
     *  TerminalEmulatorImpl 对外 internal，只能反射调用其 public 方法 processPendingUpdates。 */
    private fun refreshSnapshot(bridge: TerminalBridge): Boolean {
        return runCatching {
            bridge.terminalEmulator.javaClass
                .getMethod("processPendingUpdates")
                .invoke(bridge.terminalEmulator)
            true
        }.getOrDefault(false)
    }

    // ==================== 内部方法 ====================

    private fun findHostByIdOrName(query: String): Host? {
        return runBlocking {
            val hosts = hostRepository.getHosts()
            hosts.find { it.nickname == query || it.id.toString() == query }
        }
    }

    private fun getPortForwardsForHost(hostId: Long): List<PortForward> {
        return runBlocking {
            hostRepository.getPortForwardsForHost(hostId)
        }
    }

    private fun formatHostDetail(host: Host): String {
        return "ID: ${host.id}\n" +
            "昵称: ${host.nickname}\n" +
            "协议: ${host.protocol}\n" +
            "主机: ${host.hostname}\n" +
            "端口: ${host.port}\n" +
            "用户: ${if (host.username.isEmpty()) "(未设置)" else host.username}\n" +
            "颜色: ${host.color ?: "(默认)"}"
    }
}
