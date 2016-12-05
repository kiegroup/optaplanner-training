/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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

package org.optaplanner.examples.flp.persistence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.examples.common.persistence.AbstractTxtSolutionImporter;
import org.optaplanner.examples.flp.domain.FlpLocation;
import org.optaplanner.examples.flp.domain.FlpSolution;
import org.optaplanner.examples.flp.domain.Store;
import org.optaplanner.examples.flp.domain.Warehouse;

public class FlpImporter extends AbstractTxtSolutionImporter {

    private static final String INPUT_FILE_SUFFIX = "txt";

    public FlpImporter() {
        super(true);
    }

    @Override
    public String getInputFileSuffix() {
        return INPUT_FILE_SUFFIX;
    }

    public TxtInputBuilder createTxtInputBuilder() {
        return new FlpInputBuilder();
    }

    public static class FlpInputBuilder extends TxtInputBuilder {

        private FlpSolution solution;

        private int warehousesListSize;
        private int storesListSize;

        public FlpSolution readSolution() throws IOException {
            solution = new FlpSolution();
            solution.setId(0L);
            readHeaders();
            readWarehouseList();
            readStoreList();

            // TODO Calculate the search space size
            logger.info("FacultyPlacerSolution {} has {} warehouses and {} stores with a search space of TODO.",
                    getInputId(),
                    solution.getWarehouseList().size(),
                    solution.getStoreList().size());
            return solution;
        }

        private void readHeaders() throws IOException {
            readConstantLine("\\#.*");
            readConstantLine("\\#.*");
            String[] headerTokens = splitBySpace(bufferedReader.readLine(), 2);
            warehousesListSize = Integer.valueOf(headerTokens[0]);
            storesListSize = Integer.valueOf(headerTokens[1]);
        }

        private void readWarehouseList() throws IOException {
            readConstantLine("\\#.*");
            List<Warehouse> warehouseList = new ArrayList<Warehouse>(warehousesListSize);
            long id = 0L;
            for (int i = 0; i < warehousesListSize; i++) {
                String[] lineTokens = splitBySpace(bufferedReader.readLine(), 4);
                Warehouse warehouse = new Warehouse();
                warehouse.setId(id);
                id++;
                warehouse.setLocation(new FlpLocation(Double.valueOf(lineTokens[3]), Double.valueOf(lineTokens[2])));
                // Avoid doubles by multiplying all numbers by 10000
                warehouse.setSetupCost((long) (Double.valueOf(lineTokens[0]) * 10000.0));
                warehouse.setCapacity(Integer.valueOf(lineTokens[1]));
                warehouseList.add(warehouse);
            }
            solution.setWarehouseList(warehouseList);
        }

        private void readStoreList() throws IOException {
            readConstantLine("\\#.*");
            List<Store> storeList = new ArrayList<Store>(storesListSize);
            long id = 0L;
            for (int i = 0; i < storesListSize; i++) {
                String[] lineTokens = splitBySpace(bufferedReader.readLine(), 3);
                Store store = new Store();
                store.setId(id);
                id++;
                store.setLocation(new FlpLocation(Double.valueOf(lineTokens[2]), Double.valueOf(lineTokens[1])));
                store.setDemand(Integer.valueOf(lineTokens[0]));
                storeList.add(store);
            }
            solution.setStoreList(storeList);
        }

    }

}
