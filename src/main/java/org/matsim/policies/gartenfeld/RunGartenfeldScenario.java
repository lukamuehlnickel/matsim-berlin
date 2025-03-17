package org.matsim.policies.gartenfeld;

import org.matsim.application.MATSimApplication;

/**
 * Run class for the Gartenfeld scenario.
 */
public final class RunGartenfeldScenario {

	private RunGartenfeldScenario() {
	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(GartenfeldScenario.class, args);
	}
}
