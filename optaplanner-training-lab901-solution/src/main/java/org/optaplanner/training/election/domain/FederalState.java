/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.training.election.domain;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.training.election.optional.domain.FederalStateDifficultyComparator;

@PlanningEntity(difficultyComparatorClass = FederalStateDifficultyComparator.class)
public class FederalState {

    private String name;
    private int population;
    private int electoralVotes;

    @PlanningVariable(valueRangeProviderRefs = {"candidateRange"})
    private String winningCandidate;

    private FederalState() {
    }

    public FederalState(String name, int population, int electoralVotes) {
        this.name = name;
        this.population = population;
        this.electoralVotes = electoralVotes;
    }

    public String getName() {
        return name;
    }

    public int getPopulation() {
        return population;
    }

    public int getElectoralVotes() {
        return electoralVotes;
    }

    public String getWinningCandidate() {
        return winningCandidate;
    }

    public void setWinningCandidate(String winningCandidate) {
        this.winningCandidate = winningCandidate;
    }

    public int getMinimumMajorityPopulation() {
        return (population / 2) + 1;
    }

    @Override
    public String toString() {
        return name;
    }

}
