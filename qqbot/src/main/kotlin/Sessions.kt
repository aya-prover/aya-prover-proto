// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.qqbot

import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.subscribeMessages
import java.time.Duration
import java.util.*

abstract class Session {
  val createTime = Date().toInstant()
  abstract fun MessageEvent.handle()
  fun remove() =
    Sessions.sessions.remove(this)
}

object Sessions {
  val sessions = mutableListOf<Session>()
  fun update() {
    sessions.removeIf { it.createTime.isBefore(Date().toInstant().plus(Duration.ofDays(1))) }
  }
  fun add(session: Session) = sessions.add(session)
  init {
      AyaQQBot.bot.eventChannel.subscribeMessages {
        this.always {
          update()
          val msg = this
          sessions.forEach { with(it, { msg.handle() }) }
        }
      }
  }
}
