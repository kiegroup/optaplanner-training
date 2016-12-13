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

package org.optaplanner.training.workerrostering.persistence;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.optaplanner.training.workerrostering.domain.Employee;
import org.optaplanner.training.workerrostering.domain.Roster;
import org.optaplanner.training.workerrostering.domain.RosterParametrization;
import org.optaplanner.training.workerrostering.domain.ShiftAssignment;
import org.optaplanner.training.workerrostering.domain.Skill;
import org.optaplanner.training.workerrostering.domain.Spot;
import org.optaplanner.training.workerrostering.domain.TimeSlot;
import org.optaplanner.training.workerrostering.domain.TimeSlotState;

public class WorkerRosteringGenerator {

    public static void main(String[] args) {
        new WorkerRosteringGenerator().generateAndWriteRoster(10, 7, false);
        new WorkerRosteringGenerator().generateAndWriteRoster(10, 28, false);
        new WorkerRosteringGenerator().generateAndWriteRoster(20, 28, false);
        new WorkerRosteringGenerator().generateAndWriteRoster(40, 28 * 2, false);
        new WorkerRosteringGenerator().generateAndWriteRoster(80, 28 * 4, false);
        new WorkerRosteringGenerator().generateAndWriteRoster(10, 28, true);
        new WorkerRosteringGenerator().generateAndWriteRoster(20, 28, true);
        new WorkerRosteringGenerator().generateAndWriteRoster(40, 28 * 2, true);
        new WorkerRosteringGenerator().generateAndWriteRoster(80, 28 * 4, true);
    }

    private final StringDataGenerator employeeNameGenerator = StringDataGenerator.build10kFullNames();
    private final StringDataGenerator spotNameGenerator = StringDataGenerator.build10kLocationNames();

    private final StringDataGenerator skillNameGenerator = new StringDataGenerator()
            .addPart(
                    "Mechanical",
                    "Electrical",
                    "Safety",
                    "Transportation",
                    "Operational",
                    "Physics",
                    "Monitoring",
                    "ICT")
            .addPart(
                    "bachelor",
                    "engineer",
                    "instructor",
                    "coordinator",
                    "manager",
                    "expert",
                    "inspector",
                    "analyst");

    protected Random random = new Random(37);
    protected WorkerRosteringSolutionFileIO solutionFileIO = new WorkerRosteringSolutionFileIO();

    public void generateAndWriteRoster(int spotListSize, int dayListSize, boolean continuousPlanning) {
        Roster roster = generateRoster(spotListSize, dayListSize * 3, continuousPlanning);
        solutionFileIO.write(roster, new File("data/workerrostering/import/roster-"
                + spotListSize + "spots-" + dayListSize + "days"
                + (continuousPlanning ? "-continuous" : "") + ".xlsx"));
    }

    public Roster generateRoster(int spotListSize, int timeSlotListSize, boolean continuousPlanning) {
        int employeeListSize = spotListSize * 4;
        int skillListSize = (spotListSize + 4) / 5;
        RosterParametrization rosterParametrization = new RosterParametrization();
        List<Skill> skillList = createSkillList(skillListSize);
        List<Spot> spotList = createSpotList(spotListSize, skillList);
        List<TimeSlot> timeSlotList = createTimeSlotList(timeSlotListSize, continuousPlanning);
        List<Employee> employeeList = createEmployeeList(employeeListSize, skillList, timeSlotList);
        List<ShiftAssignment> shiftAssignmentList = createShiftAssignmentList(spotList, timeSlotList, employeeList, continuousPlanning);
        return new Roster(rosterParametrization,
                skillList, spotList, timeSlotList, employeeList,
                shiftAssignmentList);
    }

    private List<Skill> createSkillList(int size) {
        List<Skill> skillList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = skillNameGenerator.generateNextValue();
            skillList.add(new Skill(name));
        }
        return skillList;
    }

    private List<Spot> createSpotList(int size, List<Skill> skillList) {
        List<Spot> spotList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = spotNameGenerator.generateNextValue();
            spotList.add(new Spot(name, skillList.get(random.nextInt(skillList.size()))));
        }
        return spotList;
    }

    private List<TimeSlot> createTimeSlotList(int size, boolean continuousPlanning) {
        List<TimeSlot> timeSlotList = new ArrayList<>(size);
        LocalDateTime previousEndDateTime = LocalDateTime.of(2017, 2, 1, 6, 0);
        for (int i = 0; i < size; i++) {
            LocalDateTime startDateTime = previousEndDateTime;
            LocalDateTime endDateTime = startDateTime.plusHours(8);
            TimeSlot timeSlot = new TimeSlot(startDateTime, endDateTime);
            if (continuousPlanning && i < size / 2) {
                if (i < size / 4) {
                    timeSlot.setTimeSlotState(TimeSlotState.HISTORY);
                } else {
                    timeSlot.setTimeSlotState(TimeSlotState.TENTATIVE);
                }
            } else {
                timeSlot.setTimeSlotState(TimeSlotState.DRAFT);
            }
            timeSlotList.add(timeSlot);
            previousEndDateTime = endDateTime;
        }
        return timeSlotList;
    }

    private List<Employee> createEmployeeList(int size, List<Skill> generalSkillList, List<TimeSlot> timeSlotList) {
        List<Employee> employeeList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = employeeNameGenerator.generateNextValue();
            LinkedHashSet<Skill> skillSet = new LinkedHashSet<>(extractRandomSubList(generalSkillList, 1.0));
            Employee employee = new Employee(name, skillSet);
            Set<TimeSlot> unavailableTimeSlotSet = new LinkedHashSet<>(extractRandomSubList(timeSlotList, 0.2));
            employee.setUnavailableTimeSlotSet(unavailableTimeSlotSet);
            employeeList.add(employee);
        }
        return employeeList;
    }

    private List<ShiftAssignment> createShiftAssignmentList(List<Spot> spotList, List<TimeSlot> timeSlotList,
            List<Employee> employeeList, boolean continuousPlanning) {
        List<ShiftAssignment> shiftAssignmentList = new ArrayList<>(spotList.size() * timeSlotList.size());
        for (Spot spot : spotList) {
            boolean weekendEnabled = random.nextInt(10) < 8;
            boolean nightEnabled = weekendEnabled && random.nextInt(10) < 8;
            int timeSlotIndex = 0;
            for (TimeSlot timeSlot : timeSlotList) {
                DayOfWeek dayOfWeek = timeSlot.getStartDateTime().getDayOfWeek();
                if (!weekendEnabled && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
                    timeSlotIndex++;
                    continue;
                }
                if (!nightEnabled && timeSlot.getStartDateTime().getHour() >= 20) {
                    timeSlotIndex++;
                    continue;
                }
                ShiftAssignment shiftAssignment = new ShiftAssignment(spot, timeSlot);
                if (continuousPlanning) {
                    if (timeSlotIndex < timeSlotList.size() / 2) {
                        List<Employee> availableEmployeeList = employeeList.stream()
                                .filter(employee -> !employee.getUnavailableTimeSlotSet().contains(timeSlot))
                                .collect(Collectors.toList());
                        Employee employee = availableEmployeeList.get(random.nextInt(availableEmployeeList.size()));
                        shiftAssignment.setEmployee(employee);
                        shiftAssignment.setLockedByUser(random.nextDouble() < 0.05);
                    }
                }
                shiftAssignmentList.add(shiftAssignment);
                timeSlotIndex++;
            }

        }
        return shiftAssignmentList;

    }

    private <E> List<E> extractRandomSubList(List<E> list, double maxRelativeSize) {
        List<E> subList = new ArrayList<>(list);
        Collections.shuffle(subList, random);
        // TODO List.subList() doesn't allow outer list to be garbage collected
        return subList.subList(0, random.nextInt((int) (list.size() * maxRelativeSize)) + 1);
    }

}
