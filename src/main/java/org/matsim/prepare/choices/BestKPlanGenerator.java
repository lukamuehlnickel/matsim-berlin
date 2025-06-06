package org.matsim.prepare.choices;

import org.jetbrains.annotations.Nullable;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.modechoice.PlanCandidate;
import org.matsim.modechoice.PlanModel;
import org.matsim.modechoice.search.TopKChoicesGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Keeps selected plan as the first candidate and adds the rest with best options.
 */
public class BestKPlanGenerator implements ChoiceGenerator {
	private final int topK;
	private final TopKChoicesGenerator generator;

	public <T> BestKPlanGenerator(int topK, TopKChoicesGenerator generator) {
		this.topK = topK;
		this.generator = generator;
	}

	@Override
	public List<PlanCandidate> generate(Plan plan, PlanModel planModel, @Nullable Set<String> consideredModes) {

		List<String[]> chosen = new ArrayList<>();
		chosen.add(planModel.getCurrentModes());

		// Chosen candidate from data
		PlanCandidate existing = generator.generatePredefined(planModel, chosen).get(0);

		List<PlanCandidate> result = new ArrayList<>();
		result.add(existing);
		result.addAll(generator.generate(planModel, consideredModes, null));

		return result.stream().distinct().limit(topK).toList();
	}
}
