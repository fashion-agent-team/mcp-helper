package com.fashionagent.helper;

import java.time.Clock;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class McpHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpHelperApplication.class, args);
    }

    /** {@link HelperTools}의 @Tool 메서드를 MCP 도구로 내놓는다. */
    @Bean
    ToolCallbackProvider helperToolCallbacks(HelperTools helperTools) {
        return MethodToolCallbackProvider.builder().toolObjects(helperTools).build();
    }

    /** 만료 시간 계산용. 테스트에서 바꿔 끼운다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
