# ConnectBot MCP 服务器

ConnectBot 现在支持作为 MCP（Model Context Protocol）服务器运行，允许 AI 助手（如 AiCode）通过 MCP 协议访问 SSH 连接管理功能。

## 功能特性

- **主机管理**：列出、创建、更新、删除 SSH/Telnet 主机配置
- **端口转发管理**：管理端口转发规则
- **终端命令执行**：在已连接的 SSH 主机上执行命令
- **连接状态监控**：查看当前连接状态

## 使用方法

1. 打开 ConnectBot 应用
2. 点击主屏幕右上角的菜单按钮（⋮）
3. 选择「MCP 服务器」
4. 启用 MCP 服务器并配置端口（默认 8765）

## 在 AiCode 中配置 MCP 服务器

在 AiCode 的 MCP 服务器设置中添加：

```json
{
  "name": "connectbot",
  "type": "http",
  "url": "http://localhost:8765/mcp"
}
```

## 可用工具

| 工具 | 描述 |
|------|------|
| `list_hosts` | 列出所有配置的主机 |
| `get_host` | 获取主机详情 |
| `create_host` | 创建新主机 |
| `update_host` | 更新主机配置 |
| `delete_host` | 删除主机 |
| `list_port_forwards` | 列出端口转发规则 |
| `create_port_forward` | 创建端口转发 |
| `delete_port_forward` | 删除端口转发 |
| `execute_command` | 在已连接主机上执行命令，等待输出稳定后返回命令输出 |
| `get_last_command_output` | 读取指定主机终端最近一次命令及其输出（历史上下文） |
| `get_connection_status` | 获取连接状态 |

## 安全注意事项

- MCP 服务器仅监听本地地址（localhost），外部无法访问
- MCP 服务器由前台服务（McpService）保活，避免应用退后台后被系统冻结导致连接失败
- 执行命令前请确保目标主机已连接
- 建议限制 AI 助手的操作权限，避免误操作

## 容器内构建

在 ARM64 proot 容器内请使用 `sh build.sh` 一键构建（自动处理 strace 包装与 KSP 重跑），预计 6～10 分钟。

## 协议说明

MCP 服务器以 Streamable HTTP 传输实现**标准 MCP 协议（JSON-RPC 2.0）**，支持 `initialize`、`tools/list`、`tools/call`、`ping`，兼容标准 MCP 客户端（AiCode、Claude Desktop 等）。服务器地址固定为 `http://<设备IP>:8765/mcp`；若客户端与 ConnectBot 运行在同一部设备上，使用 `http://127.0.0.1:8765/mcp`。
