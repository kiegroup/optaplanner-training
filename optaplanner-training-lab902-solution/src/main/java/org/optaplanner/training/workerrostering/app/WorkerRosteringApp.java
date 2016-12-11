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

package org.optaplanner.training.workerrostering.app;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.training.workerrostering.domain.Roster;
import org.optaplanner.training.workerrostering.persistence.WorkerRosteringGenerator;
import org.optaplanner.training.workerrostering.persistence.WorkerRosteringSolutionFileIO;

public class WorkerRosteringApp {

    public static void main(String[] args) {
        String filename = "roster-10spots-28days";
        WorkerRosteringSolutionFileIO solutionFileIO = new WorkerRosteringSolutionFileIO();
        Roster roster = solutionFileIO.read(new File("data/workerrostering/import/" + filename + ".xlsx"));
        // WorkerRosteringGenerator generator = new WorkerRosteringGenerator();
        // Roster roster = generator.generateRoster(10, 28);

        // LAB-SOLUTION-START
        SolverFactory<Roster> solverFactory = SolverFactory.createFromXmlResource(
                "org/optaplanner/training/workerrostering/solver/workerRosteringSolverConfig.xml");
        roster = solverFactory.buildSolver().solve(roster);
        // LAB-SOLUTION-END

        File outputSolutionFile = new File("data/workerrostering/export/" + filename + "-solved"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH)) + ".xlsx");
        solutionFileIO.write(roster, outputSolutionFile);
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            try {
                desktop.open(outputSolutionFile);
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not open outputSolutionFile (" + outputSolutionFile
                        + ") on this operation system.", e);
            }
        }
    }

}
