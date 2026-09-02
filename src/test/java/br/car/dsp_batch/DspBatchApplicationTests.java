package br.car.dsp_batch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.batch.url=jdbc:h2:mem:batch;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.batch.username=sa",
		"spring.datasource.batch.password=",
		"spring.datasource.batch.driver-class-name=org.h2.Driver",
		"spring.datasource.source.url=jdbc:h2:mem:source;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.source.username=sa",
		"spring.datasource.source.password=",
		"spring.datasource.source.driver-class-name=org.h2.Driver",
		"spring.datasource.target.url=jdbc:h2:mem:target;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.target.username=sa",
		"spring.datasource.target.password=",
		"spring.datasource.target.driver-class-name=org.h2.Driver",
		"spring.datasource.geo-target.url=jdbc:h2:mem:geotarget;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.geo-target.username=sa",
		"spring.datasource.geo-target.password=",
		"spring.datasource.geo-target.driver-class-name=org.h2.Driver",
		"spring.batch.jdbc.initialize-schema=always",
		"execution-jobs.admin-unit-level-1-geoserver-job=false",
		"execution-jobs.admin-unit-level-2-geoserver-job=false",
		"execution-jobs.admin-unit-level-3-geoserver-job=false",
		"execution-jobs.area-of-interest-geoserver-job=false"
})
class DspBatchApplicationTests {

	@Test
	void contextLoads() {
	}

}
