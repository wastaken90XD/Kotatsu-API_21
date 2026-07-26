package org.wastaken.kotatsu.api21.core.util.json

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Reads a JSON array from an [InputStream] one top-level element at a time.
 *
 * This exists to work around a platform bug that breaks
 * `Json.decodeFromStream` / `Json.decodeToSequence` on Android 5.x (API 21-23),
 * which is the minimum supported API level of this app.
 *
 * `kotlinx-serialization` reads streams through its internal `CharsetReader`,
 * which calls `CharBuffer.wrap(array, offset, length).slice()` when it has to
 * preload more data while keeping a partially consumed token. The resulting
 * buffer has a non-zero `arrayOffset()`, and the ICU based `CharsetDecoder` on
 * old Android releases subtracts that offset from the new position:
 *
 * ```
 * out.position(out.position() + data[OUTPUT_OFFSET] - out.arrayOffset())
 * ```
 *
 * which produces a negative position and crashes with
 * `java.lang.IllegalArgumentException: Bad position (limit 123): -16138`.
 * See https://github.com/Kotlin/kotlinx.serialization/issues/2457 - it is a
 * platform defect, so there is no library version to upgrade to.
 *
 * The crash only shows up once the payload exceeds the lexer buffer (16 KiB),
 * which is why small backups restore fine and real ones fail.
 *
 * This reader decodes UTF-8 itself and only ever hands the decoder buffers with
 * `arrayOffset() == 0`, so the buggy branch is never reached. Elements are
 * returned as raw JSON strings to be parsed with `Json.decodeFromString`, which
 * does not touch the stream reading code path at all. Only a single element is
 * held in memory at a time, so restoring stays streaming and bounded.
 */
class JsonArrayStreamReader(
	private val input: InputStream,
) {

	private val decoder = StandardCharsets.UTF_8.newDecoder()
		.onMalformedInput(CodingErrorAction.REPLACE)
		.onUnmappableCharacter(CodingErrorAction.REPLACE)

	// Both buffers are heap-allocated, so their arrayOffset() is always 0.
	private val byteBuffer: ByteBuffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE).also {
		(it as Buffer).flip() // start empty
	}
	private val charBuffer: CharBuffer = CharBuffer.allocate(CHAR_BUFFER_SIZE).also {
		(it as Buffer).flip() // start empty
	}

	private var isEndOfInput = false
	private var isDrained = false
	private var isStarted = false
	private var isFinished = false
	private var pendingChar = NO_CHAR

	/**
	 * Returns the next element of the array as a raw JSON string,
	 * or `null` when the array is exhausted.
	 */
	fun nextElement(): String? {
		if (isFinished) {
			return null
		}
		if (isStarted) {
			when (val separator = nextNonWhitespaceChar()) {
				NO_CHAR, ']'.code -> {
					isFinished = true
					return null
				}

				','.code -> Unit
				else -> throw IOException(
					"Malformed JSON: expected ',' or ']' but was '${separator.toChar()}'",
				)
			}
		} else {
			isStarted = true
			when (val start = nextNonWhitespaceChar()) {
				NO_CHAR -> {
					// An empty section is not an error, just nothing to restore
					isFinished = true
					return null
				}

				'['.code -> Unit
				else -> throw IOException(
					"Malformed JSON: expected '[' but was '${start.toChar()}'",
				)
			}
		}
		val first = nextNonWhitespaceChar()
		if (first == NO_CHAR || first == ']'.code) {
			isFinished = true
			return null
		}
		return readValue(first.toChar())
	}

	/**
	 * Reads a single JSON value, tracking nesting depth and string literals so
	 * that separators inside strings or nested objects are not mistaken for the
	 * end of the element.
	 */
	private fun readValue(first: Char): String {
		val sb = StringBuilder()
		var depth = 0
		var isInString = false
		var isEscaped = false
		var c = first
		while (true) {
			sb.append(c)
			if (isInString) {
				when {
					isEscaped -> isEscaped = false
					c == '\\' -> isEscaped = true
					c == '"' -> isInString = false
				}
			} else {
				when (c) {
					'"' -> isInString = true
					'{', '[' -> depth++
					'}', ']' -> {
						depth--
						if (depth <= 0) {
							return sb.toString()
						}
					}
				}
			}
			val next = readChar()
			if (next == NO_CHAR) {
				if (depth == 0 && !isInString) {
					return sb.toString().trim() // a scalar value closed by the end of input
				}
				throw EOFException("Malformed JSON: unexpected end of array")
			}
			c = next.toChar()
			if (depth == 0 && !isInString && (c == ',' || c == ']')) {
				// End of a scalar value - the separator belongs to the array
				pendingChar = c.code
				return sb.toString().trim()
			}
		}
	}

	private fun nextNonWhitespaceChar(): Int {
		while (true) {
			val c = readChar()
			if (c == NO_CHAR || !c.toChar().isWhitespace()) {
				return c
			}
		}
	}

	private fun readChar(): Int {
		val pending = pendingChar
		if (pending != NO_CHAR) {
			pendingChar = NO_CHAR
			return pending
		}
		return if (fillChars()) charBuffer.get().code else NO_CHAR
	}

	/**
	 * Ensures [charBuffer] has at least one character available,
	 * decoding more bytes if needed. Returns `false` on end of input.
	 */
	private fun fillChars(): Boolean {
		if (charBuffer.hasRemaining()) {
			return true
		}
		if (isDrained) {
			return false
		}
		(charBuffer as Buffer).clear()
		while (true) {
			val result = decoder.decode(byteBuffer, charBuffer, isEndOfInput)
			if (result.isError) {
				result.throwException()
			}
			if (charBuffer.position() > 0) {
				break
			}
			if (isEndOfInput) {
				// No more bytes and nothing decoded: flush any final state once.
				decoder.flush(charBuffer)
				isDrained = true
				break
			}
			// Underflow without any output: pull more bytes, compacting the
			// partially decoded multi-byte sequence to the front of the buffer.
			fillBytes()
		}
		(charBuffer as Buffer).flip()
		return charBuffer.hasRemaining()
	}

	private fun fillBytes() {
		byteBuffer.compact()
		try {
			if (!byteBuffer.hasRemaining()) {
				// Cannot happen for valid UTF-8 (no sequence is 8 KiB long), but
				// guard anyway so a corrupt stream cannot spin forever.
				throw IOException("Malformed JSON: undecodable input")
			}
			val bytesRead = input.read(
				byteBuffer.array(),
				byteBuffer.position(),
				byteBuffer.remaining(),
			)
			if (bytesRead <= 0) {
				isEndOfInput = true
			} else {
				(byteBuffer as Buffer).position(byteBuffer.position() + bytesRead)
			}
		} finally {
			(byteBuffer as Buffer).flip()
		}
	}

	private companion object {

		private const val NO_CHAR = -1
		private const val BYTE_BUFFER_SIZE = 8192
		private const val CHAR_BUFFER_SIZE = 8192
	}
}
