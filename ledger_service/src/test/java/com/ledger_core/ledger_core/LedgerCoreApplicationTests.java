package com.ledger_core.ledger_core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "MONGODB_URI=mongodb://localhost:27017/ledger_test",
        "MONGODB_AUTO_INDEX_CREATION=false"
})
@ActiveProfiles("test")
class LedgerCoreApplicationTests {

	@Test
	void contextLoads() {
	}

}
