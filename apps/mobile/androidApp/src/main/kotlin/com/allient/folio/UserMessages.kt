package com.allient.folio

import android.content.res.Resources
import androidx.annotation.StringRes
import java.io.IOException

/**
 * An error whose text for people is a string resource, shown in the app language by [userMessage].
 * The exception's own message stays in English for logs and crash reports.
 * The concrete classes keep the standard exception types ([IllegalArgumentException], [IllegalStateException],
 * [IOException]) so callers and tests that catch those still work.
 */
internal interface UserMessage {
    @get:StringRes val messageRes: Int
    val messageArgs: List<Any>
}

/** Rejected input (a file that can't be imported, a limit exceeded): what `require` throws. */
internal class UserInputException(@StringRes override val messageRes: Int, logMessage: String, override val messageArgs: List<Any> = emptyList(), cause: Throwable? = null) :
    IllegalArgumentException(logMessage, cause), UserMessage

/** An operation that can't run in the current state: what `check` throws. */
internal class UserStateException(@StringRes override val messageRes: Int, logMessage: String, override val messageArgs: List<Any> = emptyList(), cause: Throwable? = null) :
    IllegalStateException(logMessage, cause), UserMessage

/** A file or folder operation that failed. */
internal class UserIoException(@StringRes override val messageRes: Int, logMessage: String, override val messageArgs: List<Any> = emptyList(), cause: Throwable? = null) :
    IOException(logMessage, cause), UserMessage

/** `require` with a translatable message: throws [UserInputException]. [log] is the English text for logs. */
internal inline fun requireUser(value: Boolean, @StringRes messageRes: Int, vararg args: Any, log: () -> String) {
    if (!value) throw UserInputException(messageRes, log(), args.toList())
}

/** `check` with a translatable message: throws [UserStateException]. */
internal inline fun checkUser(value: Boolean, @StringRes messageRes: Int, vararg args: Any, log: () -> String) {
    if (!value) throw UserStateException(messageRes, log(), args.toList())
}

/** The first [UserMessage] in this error or its causes, if any. */
internal fun Throwable.findUserMessage(): UserMessage? = generateSequence(this) { it.cause }.take(16).filterIsInstance<UserMessage>().firstOrNull()

/**
 * The text to show people for this error, in the language of [resources]: the error's own string resource when it
 * (or one of its causes) is a [UserMessage], otherwise [fallback]. Raw exception messages from libraries or the
 * platform are never shown; they stay in logs.
 */
internal fun Throwable.userMessage(resources: Resources, @StringRes fallback: Int): String {
    val message = findUserMessage() ?: return resources.getString(fallback)
    return if (message.messageArgs.isEmpty()) resources.getString(message.messageRes)
    else resources.getString(message.messageRes, *message.messageArgs.toTypedArray())
}
