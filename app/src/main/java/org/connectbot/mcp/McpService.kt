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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import org.connectbot.ui.MainActivity
import org.connectbot.R
import timber.log.Timber
import javax.inject.Inject

/**
 * MCP 服务器前台服务。
 *
 * 以前台服务保活 MCP 服务器，避免 ConnectBot 退到后台后被系统冻结
 * （Android 12+ cached app freezer），导致 AI 客户端无法连接。
 */
@AndroidEntryPoint
class McpService : Service() {

    @Inject
    lateinit var mcpServerManager: McpServerManager

    @Inject
    lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            mcpServerManager.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        val port = prefs.getInt(KEY_MCP_PORT, McpServerManager.DEFAULT_PORT)
        mcpServerManager.start(port)
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
        )
        Timber.i("MCP 前台服务已启动，端口: %s", port)
        return START_STICKY
    }

    override fun onDestroy() {
        mcpServerManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_server)
            .setContentTitle(getString(R.string.mcp_notification_title))
            .setContentText(mcpServerManager.serverUrl)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.mcp_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val KEY_MCP_ENABLED = "mcpEnabled"
        const val KEY_MCP_PORT = "mcpPort"
        private const val CHANNEL_ID = "mcp_server"
        private const val NOTIFICATION_ID = 8765
        const val ACTION_STOP = "org.connectbot.action.MCP_STOP"

        fun start(context: Context) {
            val intent = Intent(context, McpService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, McpService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
