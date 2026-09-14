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

import org.connectbot.service.TerminalManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TerminalManagerProvider 的默认实现。
 *
 * 由 MainActivity 在绑定服务后调用 [setManager] 设置实例。
 * 在 Service 未绑定时返回 null。
 */
@Singleton
class TerminalManagerProviderImpl @Inject constructor() : TerminalManagerProvider {

    @Volatile
    private var manager: TerminalManager? = null

    /** 由 MainActivity 在服务绑定后调用 */
    fun setManager(manager: TerminalManager?) {
        this.manager = manager
    }

    override fun get(): TerminalManager? = manager
}
