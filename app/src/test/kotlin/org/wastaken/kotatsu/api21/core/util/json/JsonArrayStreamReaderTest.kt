package org.wastaken.kotatsu.api21.core.util.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Tests for the streaming JSON array reader used by backup restore.
 *
 * These cover the cases that the previous `decodeToSequence` implementation
 * mishandled on Android 5.x: payloads larger than the 16 KiB lexer buffer, and
 * multi-byte characters straddling a buffer refill boundary.
 */
class JsonArrayStreamReaderTest {

	private fun readAll(json: String, chunkSize: Int = Int.MAX_VALUE): List<String> {
		val stream: InputStream = if (chunkSize == Int.MAX_VALUE) {
			ByteArrayInputStream(json.toByteArray())
		} else {
			DripFeedInputStream(json.toByteArray(), chunkSize)
		}
		val reader = JsonArrayStreamReader(stream)
		return generateSequence { reader.nextElement() }.toList()
	}

	@Test
	fun emptyArray() {
		assertEquals(emptyList<String>(), readAll("[]"))
		assertEquals(emptyList<String>(), readAll("  [   ]  "))
	}

	@Test
	fun emptyInputIsTreatedAsNoElements() {
		assertEquals(emptyList<String>(), readAll(""))
	}

	@Test
	fun scalarElements() {
		assertEquals(listOf("1", "2", "3"), readAll("[1, 2, 3]"))
		assertEquals(listOf("null", "true", "false"), readAll("[null,true,false]"))
	}

	@Test
	fun stringElementsContainingSeparators() {
		assertEquals(
			listOf("\"a\"", "\"b,c\"", "\"d]e\""),
			readAll("""["a","b,c","d]e"]"""),
		)
	}

	@Test
	fun escapedCharactersDoNotTerminateElements() {
		// A string ending in an escaped backslash must not swallow the closing quote
		assertEquals(
			listOf("""{"a":"x\\"}""", """{"b":"]"}"""),
			readAll("""[{"a":"x\\"},{"b":"]"}]"""),
		)
	}

	@Test
	fun nestedObjectsAndArrays() {
		assertEquals(
			listOf("""{"a":1}""", """{"b":[1,2,{"c":"]"}]}"""),
			readAll("""[{"a":1},{"b":[1,2,{"c":"]"}]}]"""),
		)
	}

	@Test
	fun whitespaceBetweenElementsIsIgnored() {
		assertEquals(
			listOf("""{"a":1}""", """{"b":2}"""),
			readAll("[\n\t{\"a\":1} ,\n\t{\"b\":2}\n]"),
		)
	}

	/**
	 * Multi-byte characters split across buffer refills used to corrupt the
	 * decoder state. Feeding the stream one byte at a time forces that path.
	 */
	@Test
	fun multiByteCharactersSplitAcrossBufferBoundaries() {
		val json = """[{"t":"日本語のテキスト"},{"t":"emoji 🎌 here"}]"""
		val expected = listOf("""{"t":"日本語のテキスト"}""", """{"t":"emoji 🎌 here"}""")
		for (chunkSize in intArrayOf(1, 2, 3, 5, 8, 64)) {
			assertEquals("chunkSize=$chunkSize", expected, readAll(json, chunkSize))
		}
	}

	/**
	 * Regression test for the reported failure:
	 * `IllegalArgumentException: Bad position (limit 123): -16138`.
	 * The crash only appeared once the payload exceeded the 16 KiB buffer.
	 */
	@Test
	fun largePayloadBeyondBufferSize() {
		val count = 2000
		val element = { i: Int -> """{"id":$i,"title":"Manga title number $i","tags":["a","b"]}""" }
		val json = (0 until count).joinToString(",", prefix = "[", postfix = "]") { element(it) }
		assert(json.toByteArray().size > 16 * 1024) { "payload must exceed the lexer buffer" }

		val result = readAll(json)
		assertEquals(count, result.size)
		assertEquals(element(0), result.first())
		assertEquals(element(count - 1), result.last())
	}

	@Test
	fun largePayloadReadInTinyChunks() {
		val count = 500
		val element = { i: Int -> """{"id":$i,"t":"ネコ $i"}""" }
		val json = (0 until count).joinToString(",", prefix = "[", postfix = "]") { element(it) }

		val result = readAll(json, chunkSize = 3)
		assertEquals(count, result.size)
		assertEquals(element(count - 1), result.last())
	}

	@Test
	fun exhaustedReaderKeepsReturningNull() {
		val reader = JsonArrayStreamReader(ByteArrayInputStream("[1]".toByteArray()))
		assertEquals("1", reader.nextElement())
		assertNull(reader.nextElement())
		assertNull(reader.nextElement())
	}

	/**
	 * Returns at most [chunkSize] bytes per read, emulating a slow or
	 * block-boundary-aligned source such as a [java.util.zip.ZipInputStream].
	 */
	private class DripFeedInputStream(
		private val data: ByteArray,
		private val chunkSize: Int,
	) : InputStream() {

		private var position = 0

		override fun read(): Int = if (position < data.size) data[position++].toInt() and 0xFF else -1

		override fun read(b: ByteArray, off: Int, len: Int): Int {
			if (position >= data.size) return -1
			val count = minOf(chunkSize, len, data.size - position)
			System.arraycopy(data, position, b, off, count)
			position += count
			return count
		}
	}
}
