// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.qqbot

import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply

fun MessageChain.plainText() =
  filterIsInstance<PlainText>().joinToString { it.content }

suspend fun MessageEvent.reply(msg: Message) = sender.sendMessage(msg)
suspend fun MessageEvent.reply(msg: String) = reply(PlainText(msg))
suspend fun MessageEvent.quoteReply(msg: Message) = sender.sendMessage(QuoteReply(source) + msg)
suspend fun MessageEvent.quoteReply(msg: String) = quoteReply(PlainText(msg))
suspend fun GroupMessageEvent.reply(msg: Message) = source.group.sendMessage(msg)
suspend fun GroupMessageEvent.reply(msg: String) = reply(PlainText(msg))
suspend fun GroupMessageEvent.quoteReply(msg: Message) = source.group.sendMessage(QuoteReply(source) + msg)
suspend fun GroupMessageEvent.quoteReply(msg: String) = quoteReply(PlainText(msg))

suspend inline fun MessageEvent.error(f: () -> Unit) {
  try {
    f()
  } catch (e: Exception) {
    reply("ERROR: " + e.localizedMessage)
  }
}

suspend inline fun GroupMessageEvent.error(f: () -> Unit) {
  try {
    f()
  } catch (e: Exception) {
    reply("ERROR: " + e.localizedMessage)
  }
}
