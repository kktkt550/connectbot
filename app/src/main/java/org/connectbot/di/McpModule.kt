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

package org.connectbot.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.connectbot.data.HostRepository
import org.connectbot.mcp.McpServerManager
import org.connectbot.mcp.McpTools
import org.connectbot.mcp.TerminalManagerProvider
import org.connectbot.mcp.TerminalManagerProviderImpl
import javax.inject.Singleton

/**
 * Hilt 模块：提供 MCP 相关的依赖绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class McpModule {

    /** 绑定 TerminalManagerProvider 接口到具体实现 */
    @Binds
    @Singleton
    abstract fun bindTerminalManagerProvider(
        impl: TerminalManagerProviderImpl,
    ): TerminalManagerProvider
}
