package es.kotlin.async.coroutine

import java.util.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

interface Consumer<T> {
	suspend fun consume(): T
	suspend fun consumeWithCancelHandler(cancel: CancelHandler): T
}

interface Producer<T> {
	suspend fun produce(v: T): Unit
}

class ProduceConsumer<T> : Consumer<T>, Producer<T> {
	val items = LinkedList<T>()
	val consumers = LinkedList<(T) -> Unit>()

	suspend override fun produce(v: T) {
		items.addLast(v)
		step()
	}

	private fun step() {
		while (items.isNotEmpty() && consumers.isNotEmpty()) {
			val consumer = consumers.removeFirst()
			val item = items.removeFirst()
			consumer(item)
		}
	}

	suspend override fun consume(): T = suspendCoroutine { c ->
		consumers += { c.resume(it) }
		step()
	}

	suspend override fun consumeWithCancelHandler(cancel: CancelHandler): T = suspendCoroutine { c ->
		val consumer: (T) -> Unit = { c.resume(it) }
		cancel {
			consumers -= consumer
			c.resumeWithException(CancellationException())
		}
		consumers += consumer
		step()
	}

}

fun <T> asyncProducer(callback: suspend Producer<T>.() -> Unit): Consumer<T> {
	val p = ProduceConsumer<T>()

	callback.startCoroutine(p, completion = object : Continuation<Unit> {
		override fun resumeWithException(exception: Throwable) {
			exception.printStackTrace()
		}

		override fun resume(value: Unit) {
		}
	})
	return p
}
