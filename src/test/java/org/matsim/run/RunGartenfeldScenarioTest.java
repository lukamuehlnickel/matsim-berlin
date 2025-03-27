package org.matsim.run;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.application.MATSimApplication;
import org.matsim.policies.gartenfeld.GartenfeldScenario;
import org.matsim.testcases.MatsimTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class RunGartenfeldScenarioTest {

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void pct1() {

		int code = MATSimApplication.execute(GartenfeldScenario.class,
			"--1pct",
			"--output", utils.getOutputDirectory(),
			"--parking-garages", "ONE_LINK",
			"--iterations", "2",
			"--config:qsim.numberOfThreads", "2",
			"--config:global.numberOfThreads", "2",
			"--config:simwrapper.defaultDashboards", "disabled"
		);

		assertThat(code).isEqualTo(0);

	}
}
