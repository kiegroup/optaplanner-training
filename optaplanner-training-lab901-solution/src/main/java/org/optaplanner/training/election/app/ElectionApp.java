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

package org.optaplanner.training.election.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.training.election.domain.ElectionSolution;
import org.optaplanner.training.election.domain.FederalState;

public class ElectionApp {

    public static void main(String[] args) {
        ElectionSolution electionSolution = readElection();
        SolverFactory<ElectionSolution> solverFactory = SolverFactory.createFromXmlResource(
                "org/optaplanner/training/election/solver/electionSolverConfig.xml");
        electionSolution = solverFactory.buildSolver().solve(electionSolution);
        printElection(electionSolution);
    }

    private static void printElection(ElectionSolution electionSolution) {
        System.out.println("Election");
        System.out.println("========");

        List<FederalState> federalStateList = electionSolution.getFederalStateList();
        int populationTotal = federalStateList.stream().mapToInt(FederalState::getPopulation).sum();
        federalStateList.stream()
                .filter((federalState) -> ElectionSolution.BAD_CANDIDATE.equals(federalState.getWinningCandidate()))
                .sorted(Comparator.comparing(FederalState::getElectoralVotes)
                        .thenComparing(FederalState::getPopulation).reversed())
                .forEach((federalState) -> System.out.printf(
                        "Win %2d electoral votes in %-20s. Bribe %,10d citizens there.\n",
                        federalState.getElectoralVotes(),
                        federalState.getName(),
                        federalState.getMinimumMajorityPopulation()));

        System.out.println("");
        int bribeMinimumPopulation = federalStateList.stream()
                .filter((federalState) -> ElectionSolution.BAD_CANDIDATE.equals(federalState.getWinningCandidate()))
                .mapToInt(FederalState::getMinimumMajorityPopulation).sum();
        double percentage = bribeMinimumPopulation * 100.0 / populationTotal;
        System.out.printf("Bribe %.2f%% of the US population to become president (even if all citizens votes).\n", percentage);
    }

    private static ElectionSolution readElection() {
        Path inputFile = Paths.get("data/election/import/president2016.txt");
        try (Stream<String> stream = Files.lines(inputFile)) {
            List<FederalState> federalStateList = stream
                    .filter((line) -> !line.isEmpty() && !line.startsWith("#"))
                    .map((line) -> {
                        String[] tokens = line.split(",");
                        if (tokens.length != 3) {
                            throw new IllegalStateException("The line (" + line + ") does not have 3 tokens.");
                        }
                        return new FederalState(tokens[0], Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                    }).collect(Collectors.toList());
            return new ElectionSolution(federalStateList);
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("Reading inputFile (" + inputFile + ") failed.", e);
        }

    }

}
