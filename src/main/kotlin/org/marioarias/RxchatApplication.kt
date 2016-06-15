package org.marioarias

import org.kotlinprimavera.beans.factory.get
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Import
import rx.Observable
import rx.lang.kotlin.merge
import rx.lang.kotlin.observable
import rx.lang.kotlin.subscribeWith
import rx.observables.ConnectableObservable
import rx.schedulers.Schedulers
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.regex.Pattern


@SpringBootApplication
@Import(AmqpConfiguration::class)
open class RxChatApplication

interface ChatMessage

data class GeneralMessage(val body: String) : ChatMessage
data class PrivateMessage(val routingKey: String, val body: String) : ChatMessage {
    companion object {
        val SPACE_PATTERN = Pattern.compile(" ")
    }
}

fun RabbitTemplate.general(message: GeneralMessage): Unit {
    convertAndSend("chat.general", "", message.body)
}

fun RabbitTemplate.privately(message: PrivateMessage): Unit {
    convertAndSend(message.routingKey, message.body)
}

fun main(args: Array<String>) {


    //val log = LoggerFactory.getLogger(RxChatApplication::class.java)
    val context = SpringApplication.run(RxChatApplication::class.java, *args)
    val props = context[ChatProperties::class.java]
    val name = props.name
    println("""
    $name, Welcome to the RxKotlin Chat
    -Every message that you send will published in the general chat
    -To send private messages, use '@' before your friend's alias (e.g.: @JohnDoe Hello)
    -To exit use the command ':q!'
    """)

    val scannerObservable = scanner()
    val general = generalObservable(name, scannerObservable)
    val priv = privateObservable(name, scannerObservable)

    val chat = Chat(context, name, general, priv)
    scannerObservable.connect()
    chat.latch.await()
}

private fun String.says(message: String): String {
    return "@$this says:$message"
}

fun generalObservable(name: String, input: Observable<String>): Observable<ChatMessage> {
    return input.filter { s -> !s.startsWith("@") }
            .map { s -> name.says(s) }
            .map { s -> GeneralMessage(s) }
}

fun privateObservable(name: String, input: Observable<String>): Observable<ChatMessage> {
    return input.filter { s -> s.startsWith("@") }
            .map { s -> s.split(PrivateMessage.SPACE_PATTERN, 2) }
            .filter { parts ->
                val condition = parts.size == 2
                if (!condition) {
                    println("Wrong format")
                }
                condition
            }
            .filter { parts ->
                val condition = !parts[0].equals("@")
                if (!condition) {
                    println("Invalid user")
                }
                condition
            }
            .map { parts ->
                PrivateMessage("chat.private.${parts[0].replace("@", "")}",
                        name.says(parts[1]))
            }

}

class Chat(val context: ConfigurableApplicationContext,
           val name: String,
           vararg obs: Observable<ChatMessage>) {

    val latch = CountDownLatch(1);
    val template = context[RabbitTemplate::class.java]

    init {
        template.general(GeneralMessage("$name CONNECTED"))
        obs.asIterable().merge().subscribeOn(Schedulers.io()).subscribeWith {
            onNext { message ->
                when (message) {
                    is GeneralMessage -> template.general(message)
                    is PrivateMessage -> template.privately(message)
                }
            }

            onError { e ->
                println(e.message)
            }

            onCompleted {
                template.general(GeneralMessage("$name DISCONNECTED"))
                println("Bye!!")
                latch.countDown()
                context.close()
            }
        }
    }
}

fun scanner(): ConnectableObservable<String> = observable<String> { subscriber ->
    try {
        if (subscriber.isUnsubscribed) {
            return@observable
        }

        val scanner = Scanner(System.`in`)
        while (true) {
            val line = scanner.nextLine()!!
            if (line.equals(":q!")) {
                break
            }
            subscriber.onNext(line)
        }
    } catch (e: IOException) {
        subscriber.onError(e)
    }

    if (!subscriber.isUnsubscribed) {
        subscriber.onCompleted()
    }
}.publish()


