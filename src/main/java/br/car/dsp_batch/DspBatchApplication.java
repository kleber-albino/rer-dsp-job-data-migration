package br.car.dsp_batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DspBatchApplication {

	/**
	 * Exits the JVM after JobRunner — required so the core one-shot
	 * container (docker compose run) does not stay "stuck" and start.sh can
	 * continue to backend/frontend.
	 */
	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(DspBatchApplication.class, args)));
	}

}
