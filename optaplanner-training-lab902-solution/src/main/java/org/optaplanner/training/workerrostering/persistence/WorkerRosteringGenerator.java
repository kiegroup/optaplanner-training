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

import org.optaplanner.training.workerrostering.domain.Employee;
import org.optaplanner.training.workerrostering.domain.Roster;
import org.optaplanner.training.workerrostering.domain.RosterParametrization;
import org.optaplanner.training.workerrostering.domain.ShiftAssignment;
import org.optaplanner.training.workerrostering.domain.Skill;
import org.optaplanner.training.workerrostering.domain.Spot;
import org.optaplanner.training.workerrostering.domain.TimeSlot;

public class WorkerRosteringGenerator {

    private static final StringDataGenerator employeeNameGenerator = StringDataGenerator.build10kFullNames();
    private static final StringDataGenerator spotNameGenerator = StringDataGenerator.build10kLocationNames();

    private static final StringDataGenerator skillNameGenerator = new StringDataGenerator()
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

    public static void main(String[] args) {
        new WorkerRosteringGenerator().generateAndWriteRoster(10, 28);
    }

    protected Random random = new Random(37);
    protected WorkerRosteringSolutionFileIO solutionFileIO = new WorkerRosteringSolutionFileIO();

    public void generateAndWriteRoster(int spotListSize, int timeSlotListSize) {
        Roster roster = generateRoster(spotListSize, timeSlotListSize);
        solutionFileIO.write(roster, new File("data/workerrostering/import/roster-"
                + spotListSize + "spots-" + timeSlotListSize + "timeslots.xlsx"));
    }

    public Roster generateRoster(int spotListSize, int timeSlotListSize) {
        int employeeListSize = spotListSize * 4;
        int skillListSize = (spotListSize + 4) / 5;
        RosterParametrization rosterParametrization = new RosterParametrization();
        List<Skill> skillList = createSkillList(skillListSize);
        List<Spot> spotList = createSpotList(spotListSize, skillList);
        List<TimeSlot> timeSlotList = createTimeSlotList(timeSlotListSize);
        List<Employee> employeeList = createEmployeeList(employeeListSize, skillList);
        List<ShiftAssignment> shiftAssignmentList = createShiftAssignmentList(spotList, timeSlotList, employeeList);
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

    private List<TimeSlot> createTimeSlotList(int size) {
        List<TimeSlot> timeSlotList = new ArrayList<>(size);
        LocalDateTime previousEndDateTime = LocalDateTime.of(2017, 2, 1, 6, 0);
        for (int i = 0; i < size; i++) {
            LocalDateTime startDateTime = previousEndDateTime;
            LocalDateTime endDateTime = startDateTime.plusHours(8);
            timeSlotList.add(new TimeSlot(startDateTime, endDateTime));
            previousEndDateTime = endDateTime;
        }
        return timeSlotList;
    }

    private List<Employee> createEmployeeList(int size, List<Skill> generalSkillList) {
        List<Employee> employeeList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = employeeNameGenerator.generateNextValue();
            LinkedHashSet<Skill> skillSet = new LinkedHashSet<>(extractRandomSubList(generalSkillList));
            employeeList.add(new Employee(name, skillSet));
        }
        return employeeList;
    }

    private List<ShiftAssignment> createShiftAssignmentList(List<Spot> spotList, List<TimeSlot> timeSlotList,
            List<Employee> employeeList ) {
        List<ShiftAssignment> shiftAssignmentList = new ArrayList<>(spotList.size() * timeSlotList.size());
        for (Spot spot : spotList) {
            boolean weekendEnabled = random.nextInt(10) < 8;
            boolean nightEnabled = weekendEnabled && random.nextInt(10) < 8;
            for (TimeSlot timeSlot : timeSlotList) {
                DayOfWeek dayOfWeek = timeSlot.getStartDateTime().getDayOfWeek();
                if (!weekendEnabled && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
                    continue;
                }
                if (!nightEnabled && timeSlot.getStartDateTime().getHour() >= 20) {
                    continue;
                }
                shiftAssignmentList.add(new ShiftAssignment(spot, timeSlot));
            }

        }
        return shiftAssignmentList;

    }

    private <E> List<E> extractRandomSubList(List<E> list) {
        List<E> subList = new ArrayList<>(list);
        Collections.shuffle(subList, random);
        // TODO List.subList() doesn't allow outer list to be garbage collected
        return subList.subList(0, random.nextInt(list.size()) + 1);
    }

}
