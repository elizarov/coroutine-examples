package es.kotlin.net.async

import es.kotlin.async.coroutine.asyncFun
import es.kotlin.async.coroutine.asyncGenerate
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.Charset
import java.util.*
import kotlin.coroutines.suspendCoroutine

class AsyncClient(
	private val sc: AsynchronousSocketChannel = AsynchronousSocketChannel.open()
) {
	private var _connected = false

	val millisecondsTimeout = 60 * 1000L

	companion object {
		suspend operator fun invoke(host: String, port: Int, bufferSize: Int = 1024) = createAndConnect(host, port, bufferSize)

		suspend fun createAndConnect(host: String, port: Int, bufferSize: Int = 1024) = asyncFun {
			val socket = AsyncClient()
			socket.connect(host, port)
			socket
		}
	}

	suspend fun connect(host: String, port: Int) = connect(InetSocketAddress(host, port))

	suspend fun connect(remote: SocketAddress): Unit = suspendCoroutine { c ->
		sc.connect(remote, this, object : CompletionHandler<Void, AsyncClient> {
			override fun completed(result: Void?, attachment: AsyncClient): Unit = run { _connected = true; c.resume(Unit) }
			override fun failed(exc: Throwable, attachment: AsyncClient): Unit = run { _connected = false; c.resumeWithException(exc) }
		})
	}

	val connected: Boolean get() = this._connected
	val bytesStream = readStream().iterator()

	//fun getAsyncStream(): Stream<ByteArray> {
	//Stream.
	//}

	fun readStream() = asyncGenerate {
		while (true) {
			val ba: Any = ___read(1)
			println("Read: $ba")
			val c: Any = (ba as ByteArray)[0]
			yield(c)
		}
	}

	suspend fun read(size: Int): ByteArray = asyncFun {
		val out = ByteArray(size)
		for (n in 0 until size) out[n] = bytesStream.next() as Byte
		out
	}

	suspend private fun ___read(size: Int): ByteArray = suspendCoroutine { c ->
		val out = ByteArray(size)
		val buffer = ByteBuffer.wrap(out)

		sc.read(buffer, this, object : CompletionHandler<Int, AsyncClient> {
			override fun completed(result: Int, attachment: AsyncClient): Unit = run {
				if (result < 0) {
					println("___read.completed.Resume: EOF")
					c.resumeWithException(RuntimeException("EOF"))
				} else {
					println("___read.completed..Resume: $out")
					c.resume(Arrays.copyOf(out, result))
				}
			}

			override fun failed(exc: Throwable, attachment: AsyncClient): Unit = run {
				println("___read.failed.Resume: $exc")
				c.resumeWithException(exc)
			}
		})
	}

	//suspend private fun _read(size: Int): ByteArray = asyncFun {
	//	val onCancel: Signal<Unit> = Signal()
	//	try {
	//		__read(size, onCancel)
	//	} finally {
	//		onCancel.invoke(Unit)
	//	}
	//}

	//suspend private fun __read(size: Int, onCancel: Signal<Unit>): ByteArray = suspendCoroutine { c ->
	//	val out = ByteArray(size)
	//	val buffer = ByteBuffer.wrap(out)
//
	//	sc.read(buffer, this, object : CompletionHandler<Int, AsyncClient> {
	//		override fun completed(result: Int, attachment: AsyncClient): Unit = run {
	//			if (result < 0) {
	//				c.resumeWithException(RuntimeException("EOF"))
	//			} else {
	//				c.resume(Arrays.copyOf(out, result))
	//			}
	//		}
//
	//		override fun failed(exc: Throwable, attachment: AsyncClient): Unit = run {
	//			c.resumeWithException(exc)
	//		}
	//	})
//
	//	onCancel.add {
	//		// Do cancellation
	//	}
	//}

	suspend fun write(data: ByteArray) = suspendCoroutine<Unit> { c ->
		val buffer = ByteBuffer.wrap(data)
		sc.write(buffer, this, object : CompletionHandler<Int, AsyncClient> {
			override fun completed(result: Int, attachment: AsyncClient): Unit = run { c.resume(Unit) }
			override fun failed(exc: Throwable, attachment: AsyncClient): Unit = run { c.resumeWithException(exc) }
		})
	}

	suspend fun close(): Unit = asyncFun {
		sc.close()
	}
}

suspend fun AsyncClient.readLine(charset: Charset = Charsets.UTF_8) = asyncFun {
	try {
		val os = ByteArrayOutputStream()
		// @TODO: optimize this!
		while (true) {
			val ba = read(1)
			os.write(ba[0].toInt())
			if (ba[0].toChar() == '\n') break
		}
		val out = os.toByteArray().toString(charset)
		val res = if (out.endsWith("\r\n")) {
			out.substring(0, out.length - 2)
		} else if (out.endsWith("\n")) {
			out.substring(0, out.length - 1)
		} else {
			out
		}
		res
	} catch (e: Throwable) {
		println("readLine.ERROR: ${e.message}")
		throw e
	} finally {
		println("readLine completed!")
	}
}
