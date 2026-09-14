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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.connectbot.data.HostRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import io.ktor.server.netty.Netty
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP 服务器管理器。
 *
 * 以 Streamable HTTP 传输实现标准 MCP 协议（JSON-RPC 2.0）：
 * initialize → tools/list → tools/call，让 AI 助手（如 AiCode）能够
 * 通过 MCP 协议访问 ConnectBot 的 SSH 功能（主机管理、端口转发、终端命令执行）。
 */
@Singleton
class McpServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hostRepository: HostRepository,
    private val mcpTools: McpTools,
) {
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _runningState = MutableStateFlow(false)

    /** 服务器运行状态流，UI 订阅此流获取真实状态 */
    val runningState: StateFlow<Boolean> = _runningState.asStateFlow()

    /** 服务器是否正在运行 */
    var isRunning: Boolean
        get() = _runningState.value
        private set(value) {
            _runningState.value = value
        }

    /** 当前监听端口 */
    var port: Int = DEFAULT_PORT
        private set

    /** 服务器地址（用于显示） */
    val serverUrl: String
        get() = "http://localhost:$port/mcp"

    /**
     * 启动 MCP 服务器。
     *
     * @param port 监听端口，默认 8765
     */
    fun start(port: Int = DEFAULT_PORT) {
        if (isRunning) {
            Timber.w("MCP 服务器已在运行中，跳过启动")
            return
        }

        this.port = port
        scope.launch {
            try {
                val engine = embeddedServer(Netty, port = port) { mcpRouting() }
                engine.start(wait = false)
                server = engine
                isRunning = true
                Timber.i("MCP 服务器已启动，端口: $port，地址: $serverUrl")
            } catch (e: Exception) {
                Timber.e(e, "MCP 服务器启动失败")
                isRunning = false
            }
        }
    }

    /**
     * 停止 MCP 服务器。
     */
    fun stop() {
        server?.stop(500, 1000)
        server = null
        isRunning = false
        Timber.i("MCP 服务器已停止")
    }

    /** 注册 MCP Streamable HTTP 路由。 */
    private fun Application.mcpRouting() {
        routing {
            post("/mcp") {
                val body = call.receiveText()
                Timber.d("MCP 请求: $body")
                val json = try {
                    JSONObject(body)
                } catch (e: Exception) {
                    call.respondText(
                        rpcError(null, -32700, "Parse error: ${e.message}").toString(),
                        ContentType.Application.Json,
                    )
                    return@post
                }

                val method = json.optString("method", "")
                val hasId = json.has("id") && !json.isNull("id")

                if (!hasId) {
                    // notification：无需响应内容
                    call.respond(HttpStatusCode.Accepted)
                    return@post
                }

                val id = json.opt("id")
                val response = when (method) {
                    "initialize" -> rpcResult(id, buildInitializeResult(json))
                    "ping" -> rpcResult(id, JSONObject())
                    "tools/list" -> rpcResult(id, buildToolsList())
                    "tools/call" -> rpcResult(id, handleToolCall(json))
                    else -> rpcError(id, -32601, "Method not found: $method")
                }
                call.respondText(response.toString(), ContentType.Application.Json)
            }
            get("/mcp") {
                call.respondText(
                    """{"error":"SSE streaming not supported"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.MethodNotAllowed,
                )
            }
        }
    }

    private fun buildInitializeResult(request: JSONObject): JSONObject {
        val params = request.optJSONObject("params") ?: JSONObject()
        val requested = params.optString("protocolVersion", SUPPORTED_PROTOCOL_VERSION)
        return JSONObject()
            .put("protocolVersion", requested.ifEmpty { SUPPORTED_PROTOCOL_VERSION })
            .put(
                "capabilities",
                JSONObject().put("tools", JSONObject().put("listChanged", false)),
            )
            .put(
                "serverInfo",
                JSONObject()
                    .put("name", "connectbot")
                    .put("version", "1.0.0"),
            )
    }

    private fun buildToolsList(): JSONObject {
        val schemaObject = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject())
        val schemaHostId = prop("hostId", "string", "主机 ID 或昵称")
        val tools = JSONArray()
            .put(tool("list_hosts", "列出所有配置的 SSH/Telnet 主机", schemaObject))
            .put(
                tool(
                    "get_host",
                    "获取主机详情",
                    schema(schemaHostId),
                ),
            )
            .put(
                tool(
                    "create_host",
                    "创建新的 SSH/Telnet 主机",
                    schema(
                        prop("nickname", "string", "主机昵称"),
                        prop("hostname", "string", "主机名或 IP"),
                        prop("port", "integer", "端口，默认 22"),
                        prop("username", "string", "登录用户名"),
                        prop("protocol", "string", "协议：ssh 或 telnet"),
                    ),
                ),
            )
            .put(
                tool(
                    "update_host",
                    "更新主机配置",
                    schema(
                        schemaHostId,
                        prop("nickname", "string", "新昵称（可选）"),
                        prop("hostname", "string", "新主机名（可选）"),
                        prop("port", "integer", "新端口（可选）"),
                        prop("username", "string", "新用户名（可选）"),
                    ),
                ),
            )
            .put(tool("delete_host", "删除主机", schema(schemaHostId)))
            .put(
                tool(
                    "list_port_forwards",
                    "列出主机的端口转发规则",
                    schema(schemaHostId),
                ),
            )
            .put(
                tool(
                    "create_port_forward",
                    "创建端口转发规则",
                    schema(
                        schemaHostId,
                        prop("nickname", "string", "规则昵称"),
                        prop("type", "string", "类型：local / remote / dynamic"),
                        prop("sourcePort", "integer", "本地监听端口"),
                        prop("destAddr", "string", "目标地址"),
                        prop("destPort", "integer", "目标端口"),
                    ),
                ),
            )
            .put(
                tool(
                    "delete_port_forward",
                    "删除端口转发规则",
                    schema(prop("forwardId", "integer", "转发规则 ID")),
                ),
            )
            .put(
                tool(
                    "execute_command",
                    "在已连接的 SSH 主机上执行命令，并等待终端输出稳定后一并返回",
                    schema(
                        schemaHostId,
                        prop("command", "string", "要执行的命令"),
                        prop("waitForOutputMs", "integer", "等待输出的时长毫秒，默认 3000，最长 15000"),
                    ),
                ),
            )
            .put(
                tool(
                    "get_last_command_output",
                    "读取指定主机终端最近一次命令及其输出（历史上下文）",
                    schema(schemaHostId),
                ),
            )
            .put(
                tool(
                    "get_terminal_screen",
                    "读取指定主机终端当前屏幕及最近滚动缓冲的完整文本（适合查看终端当前状态与历史上下文）",
                    schema(schemaHostId),
                ),
            )
            .put(
                tool(
                    "get_connection_status",
                    "获取当前连接状态摘要",
                    schemaObject,
                ),
            )
        return JSONObject().put("tools", tools)
    }

    private fun handleToolCall(request: JSONObject): JSONObject {
        val params = request.optJSONObject("params") ?: JSONObject()
        val name = params.optString("name", "")
        val args = params.optJSONObject("arguments") ?: JSONObject()

        return try {
            val text = when (name) {
                "list_hosts" -> mcpTools.listHosts()
                "get_host" -> mcpTools.getHost(args.optString("hostId", ""))
                "create_host" -> mcpTools.createHost(
                    args.optString("nickname", ""),
                    args.optString("hostname", ""),
                    args.optInt("port", 22),
                    args.optString("username", ""),
                    args.optString("protocol", "ssh"),
                )
                "update_host" -> mcpTools.updateHost(
                    args.optString("hostId", ""),
                    args.optStringOrNull("nickname"),
                    args.optStringOrNull("hostname"),
                    args.optIntOrNull("port"),
                    args.optStringOrNull("username"),
                )
                "delete_host" -> mcpTools.deleteHost(args.optString("hostId", ""))
                "list_port_forwards" -> mcpTools.listPortForwards(args.optString("hostId", ""))
                "create_port_forward" -> mcpTools.createPortForward(
                    args.optString("hostId", ""),
                    args.optString("nickname", ""),
                    args.optString("type", "local"),
                    args.optInt("sourcePort", 0),
                    args.optString("destAddr", "localhost"),
                    args.optInt("destPort", 0),
                )
                "delete_port_forward" -> mcpTools.deletePortForward(args.optLong("forwardId", 0L))
                "execute_command" -> mcpTools.executeCommand(
                    args.optString("hostId", ""),
                    args.optString("command", ""),
                    args.optInt("waitForOutputMs", 3000),
                )
                "get_last_command_output" -> mcpTools.getLastCommandOutput(args.optString("hostId", ""))
                "get_terminal_screen" -> mcpTools.getTerminalScreen(args.optString("hostId", ""))
                "get_connection_status" -> mcpTools.getConnectionStatus()
                else -> {
                    return JSONObject()
                        .put(
                            "content",
                            JSONArray().put(textContent("未知工具: $name")),
                        )
                        .put("isError", true)
                }
            }
            JSONObject()
                .put("content", JSONArray().put(textContent(text)))
                .put("isError", false)
        } catch (e: Exception) {
            Timber.e(e, "MCP 工具调用失败: $name")
            JSONObject()
                .put("content", JSONArray().put(textContent("执行失败: ${e.message ?: "未知错误"}")))
                .put("isError", true)
        }
    }

    private fun textContent(text: String): JSONObject =
        JSONObject().put("type", "text").put("text", text)

    private fun rpcResult(id: Any?, result: JSONObject): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id ?: JSONObject.NULL)
            .put("result", result)

    private fun rpcError(id: Any?, code: Int, message: String): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id ?: JSONObject.NULL)
            .put("error", JSONObject().put("code", code).put("message", message))

    private fun prop(name: String, type: String, description: String): Pair<String, JSONObject> =
        name to JSONObject()
            .put("type", type)
            .put("description", description)

    private fun schema(vararg props: Pair<String, JSONObject>): JSONObject {
        val properties = JSONObject()
        for ((name, def) in props) {
            properties.put(name, def)
        }
        return JSONObject().put("type", "object").put("properties", properties)
    }

    private fun tool(name: String, description: String, inputSchema: JSONObject): JSONObject =
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put("inputSchema", inputSchema)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    /** 获取当前所有主机的连接状态摘要 */
    fun getConnectionStatusSummary(): String = mcpTools.getConnectionStatus()

    /** 完全关闭，取消作用域 */
    fun shutdown() {
        stop()
        scope.cancel()
    }

    companion object {
        const val DEFAULT_PORT = 8765
        const val SUPPORTED_PROTOCOL_VERSION = "2025-06-18"
    }
}
