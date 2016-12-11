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

package org.optaplanner.training.election.optional.solver;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.impl.score.director.incremental.IncrementalScoreCalculator;
import org.optaplanner.training.election.domain.Election;
import org.optaplanner.training.election.domain.FederalState;

public class ElectionIncrementalScoreCalculator implements IncrementalScoreCalculator<Election> {

    private int gamerCandidateWins;
    private int gamerMinimumPopulation;

    @Override
    public void resetWorkingSolution(Election election) {
        gamerCandidateWins = 0;
        gamerMinimumPopulation = 0;
        for (FederalState federalState : election.getFederalStateList()) {
            insert(federalState);
        }
    }

    @Override
    public void beforeEntityAdded(Object entity) {
        // Do nothing
    }

    @Override
    public void afterEntityAdded(Object entity) {
        insert((FederalState) entity);
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {
        retract((FederalState) entity);
    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
        insert((FederalState) entity);
    }

    @Override
    public void beforeEntityRemoved(Object entity) {
        retract((FederalState) entity);
    }

    @Override
    public void afterEntityRemoved(Object entity) {
        // Do nothing
    }

    private void insert(FederalState federalState) {
        if (Election.GAMER_CANDIDATE.equals(federalState.getWinningCandidate())) {
            gamerCandidateWins += federalState.getElectoralVotes();
            gamerMinimumPopulation += federalState.getMinimumMajorityPopulation();
        }
    }

    private void retract(FederalState federalState) {
        if (Election.GAMER_CANDIDATE.equals(federalState.getWinningCandidate())) {
            gamerCandidateWins -= federalState.getElectoralVotes();
            gamerMinimumPopulation -= federalState.getMinimumMajorityPopulation();
        }
    }

    @Override
    public Score calculateScore(int initScore) {
        int hardScore = (gamerCandidateWins >= 270) ? 0 : (gamerCandidateWins - 270);
        return HardSoftScore.valueOf(initScore, hardScore, -gamerMinimumPopulation);
    }

}
