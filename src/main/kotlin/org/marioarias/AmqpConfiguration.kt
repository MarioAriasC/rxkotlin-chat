package org.marioarias


import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar
import org.springframework.boot.autoconfigure.amqp.RabbitProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableRabbit
open class AmqpConfiguration : RabbitListenerConfigurer {


    override fun configureRabbitListeners(registrar: RabbitListenerEndpointRegistrar) {
        val generalEndpoint = SimpleRabbitListenerEndpoint().apply {
            id = "generalEndpoint"
            setQueues(generalQueue())
            setMessageListener(listener())
        }
        val privateEndpoint = SimpleRabbitListenerEndpoint().apply {
            id = "privateEndpoint"
            setQueues(privateQueue())
            setMessageListener(listener("[PRIVATE ]"))
        }
        registrar.registerEndpoint(generalEndpoint)
        registrar.registerEndpoint(privateEndpoint)
    }

    private fun listener(prefix: String = ""): (Message) -> Unit {
        return { message ->
            println(prefix + String(message.body))
        }
    }

    //private val log = LoggerFactory.getLogger(AmqpConfiguration::class.java)

    @Bean
    open fun connectionFactory(props: RabbitProperties) = CachingConnectionFactory().apply {
        setAddresses(props.addresses)
        setUsername(props.username)
        setPassword(props.password)
    }

    @Bean
    open fun template(connectionFactory: ConnectionFactory) = RabbitTemplate(connectionFactory)

    @Bean
    open fun admin(connectionFactory: ConnectionFactory) = RabbitAdmin(connectionFactory).apply {
        afterPropertiesSet()
    }

    @Bean
    open fun privateQueue() = Queue("chat.private.${chatProperties().name}")

    @Bean
    open fun generalQueue() = Queue("chat.general.${chatProperties().name}")

    @Bean
    open fun chatProperties() = ChatProperties()

    @Bean
    open fun generalFanout() = FanoutExchange("chat.general")

    @Bean
    open fun binding(generalFanout: FanoutExchange,
                     generalQueue: Queue): Binding = BindingBuilder.bind(generalQueue).to(generalFanout)

}