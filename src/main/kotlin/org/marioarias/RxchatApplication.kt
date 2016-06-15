package org.marioarias

import org.kotlinprimavera.beans.factory.get
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Import
import rx.Observable
import rx.Observer
import rx.lang.kotlin.merge
import rx.lang.kotlin.observable
import rx.observables.ConnectableObservable
import rx.schedulers.Schedulers
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.regex.Matcher
import java.util.regex.Pattern


@SpringBootApplication
@Import(AmqpConfiguration::class)
open class RxChatApplication

val chatGeneral = "chat.general"

interface ChatMessage

data class GeneralMessage(val body: String) : ChatMessage
class PrivateMessage(name: String, original: String) : ChatMessage {
    val routingKey: String
    val body: String

    init {
        val parts = PATTERN.split(original, 2)
        if (parts.size < 2) {
            throw IllegalArgumentException("Wrong format")
        }
        if (parts[0] == AT) {
            throw IllegalArgumentException("Undefined user")
        }

        body = "$AT$name says:${parts[1]}"
        routingKey = "chat.private.${COMPILE.matcher(parts[0]).replaceAll(Matcher.quoteReplacement(""))}"
    }

    companion object {
        private val PATTERN = Pattern.compile(" ")
        val AT = "@"
        private val COMPILE = Pattern.compile(AT, Pattern.LITERAL)
    }

    override fun toString(): String {
        return "PrivateMessage(routingKey='$routingKey', body='$body')"
    }
}

fun RabbitTemplate.general(message: GeneralMessage): Unit {
    convertAndSend(chatGeneral, "", message.body)
}

fun RabbitTemplate.privatelly(message: PrivateMessage): Unit {
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

fun generalObservable(name: String, input: Observable<String>): Observable<ChatMessage> {
    return input.filter { s -> !s.startsWith(PrivateMessage.AT) }
            .map { s -> "${PrivateMessage.AT}$name says:$s" }
            .map { s -> GeneralMessage(s) }
}

fun privateObservable(name: String, input: Observable<String>): Observable<ChatMessage> {
    return input.filter { s -> s.startsWith(PrivateMessage.AT) }
            .map { s -> PrivateMessage(name, s) }
}

class Chat(val context: ConfigurableApplicationContext,
           val name: String,
           vararg obs: Observable<ChatMessage>) : Observer<ChatMessage> {

    val subs = obs.asIterable().merge().subscribeOn(Schedulers.io()).subscribe(this)
    val latch = CountDownLatch(1);
    val template = context[RabbitTemplate::class.java]

    init {
        template.general(GeneralMessage("$name CONNECTED"))
    }

    override fun onNext(message: ChatMessage) {
        when(message){
            is GeneralMessage -> template.general(message)
            is PrivateMessage -> template.privatelly(message)
        }
    }

    override fun onError(e: Throwable) {
        println(e.message)
    }

    override fun onCompleted() {
        template.general(GeneralMessage("$name DISCONNECTED"))
        println("Bye!!")
        latch.countDown()
        subs.unsubscribe()
        context.close()
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


