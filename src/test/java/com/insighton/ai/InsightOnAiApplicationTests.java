package com.insighton.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none"
})
class InsightOnAiApplicationTests {

    @Test
    void contextLoads() {
    }

}
