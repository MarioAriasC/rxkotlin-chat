package org.marioarias

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "chat")
data class ChatProperties(var name:String = "YourName")