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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.optaplanner.persistence.common.api.domain.solution.SolutionFileIO;
import org.optaplanner.training.workerrostering.domain.Employee;
import org.optaplanner.training.workerrostering.domain.Roster;
import org.optaplanner.training.workerrostering.domain.RosterParametrization;
import org.optaplanner.training.workerrostering.domain.ShiftAssignment;
import org.optaplanner.training.workerrostering.domain.Skill;
import org.optaplanner.training.workerrostering.domain.Spot;
import org.optaplanner.training.workerrostering.domain.TimeSlot;
import org.optaplanner.training.workerrostering.domain.TimeSlotState;

public class WorkerRosteringSolutionFileIO implements SolutionFileIO<Roster> {

    public static final DateTimeFormatter DATE_TIME_FORMATTER
            = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    public static final DateTimeFormatter DAY_FORMATTER
            = DateTimeFormatter.ofPattern("EEE dd-MMM", Locale.ENGLISH);

    public static final DateTimeFormatter TIME_FORMATTER
            = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private static final IndexedColors NON_EXISTING_COLOR = IndexedColors.GREY_80_PERCENT;
    private static final IndexedColors LOCKED_BY_USER_COLOR = IndexedColors.VIOLET;
    private static final IndexedColors UNAVAILABLE_COLOR = IndexedColors.BLUE_GREY;

    @Override
    public String getInputFileExtension() {
        return "xlsx";
    }

    @Override
    public String getOutputFileExtension() {
        return "xlsx";
    }

    @Override
    public Roster read(File inputSolutionFile) {
        Workbook workbook;
        try (InputStream in = new BufferedInputStream(new FileInputStream(inputSolutionFile))) {
            workbook = new XSSFWorkbook(in);
            return new RosterReader(workbook).readRoster();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Failed reading inputSolutionFile ("
                    + inputSolutionFile + ") to create a roster.", e);
        }
    }

    private static  class RosterReader {

        private final Workbook workbook;

        public RosterReader(Workbook workbook) {
            this.workbook = workbook;
        }

        public Roster readRoster() {
            RosterParametrization rosterParametrization = new RosterParametrization();
            List<Skill> skillList = readListSheet("Skills", new String[]{"Name"}, (Row row) -> {
                String name = row.getCell(0).getStringCellValue();
                return new Skill(name);
            });
            Map<String, Skill> skillMap = skillList.stream().collect(Collectors.toMap(
                    Skill::getName, skill -> skill));
            List<Spot> spotList = readListSheet("Spots", new String[]{"Name", "Required skill"}, (Row row) -> {
                String name = row.getCell(0).getStringCellValue();
                String requiredSkillName = row.getCell(1).getStringCellValue();
                Skill requiredSkill = skillMap.get(requiredSkillName);
                if (requiredSkill == null) {
                    throw new IllegalStateException("The requiredSkillName (" + requiredSkillName
                            + ") does not exist in the skillList (" + skillList + ").");
                }
                return new Spot(name, requiredSkill);
            });
            Map<String, Spot> spotMap = spotList.stream().collect(Collectors.toMap(
                    Spot::getName, spot -> spot));
            List<TimeSlot> timeSlotList = readListSheet("Timeslots", new String[]{"Start", "End", "State"}, (Row row) -> {
                LocalDateTime startDateTime = LocalDateTime.parse(row.getCell(0).getStringCellValue(), DATE_TIME_FORMATTER);
                LocalDateTime endDateTime = LocalDateTime.parse(row.getCell(1).getStringCellValue(), DATE_TIME_FORMATTER);
                TimeSlot timeSlot = new TimeSlot(startDateTime, endDateTime);
                timeSlot.setTimeSlotState(TimeSlotState.valueOf(row.getCell(2).getStringCellValue()));
                return timeSlot;
            });
            List<Employee> employeeList = readListSheet("Employees", new String[]{"Name", "Skills"}, (Row row) -> {
                String name = row.getCell(0).getStringCellValue();
                Set<Skill> skillSet = Arrays.stream(row.getCell(1).getStringCellValue().split(",")).map((skillName) -> {
                    Skill skill = skillMap.get(skillName);
                    if (skill == null) {
                        throw new IllegalStateException("The skillName (" + skillName
                                + ") does not exist in the skillList (" + skillList + ").");
                    }
                    return skill;
                }).collect(Collectors.toSet());
                Employee employee = new Employee(name, skillSet);
                employee.setUnavailableTimeSlotSet(new LinkedHashSet<>());
                return employee;
            });
            Map<String, Employee> employeeMap = employeeList.stream().collect(Collectors.toMap(
                    Employee::getName, employee -> employee));
            List<ShiftAssignment> shiftAssignmentList = readGridSheet("Spot roster", new String[]{"Name"}, (Row row) -> {
                String spotName = row.getCell(0).getStringCellValue();
                Spot spot = spotMap.get(spotName);
                if (spot == null) {
                    throw new IllegalStateException("The spotName (" + spotName
                            + ") does not exist in the spotList (" + spotList + ").");
                }
                return spot;
            }, timeSlotList, (Pair<Spot, TimeSlot> pair, Cell cell) ->  {
                if (hasStyle(cell, NON_EXISTING_COLOR)) {
                    return null;
                }
                ShiftAssignment shiftAssignment = new ShiftAssignment(pair.getKey(), pair.getValue());
                if (hasStyle(cell, LOCKED_BY_USER_COLOR)) {
                    shiftAssignment.setLockedByUser(true);
                }
                String employeeName = cell.getStringCellValue();
                if (employeeName.isEmpty()) {
                    throw new IllegalStateException("The sheet (Spot roster), has a cell ("
                            + cell.getRowIndex() + "," + cell.getColumnIndex() + ") which does not contain ? or an employeeName.");
                }
                if (employeeName.equals("?")) {
                    shiftAssignment.setEmployee(null);
                } else {
                    Employee employee = employeeMap.get(employeeName);
                    if (employee == null) {
                        throw new IllegalStateException("The sheet (Spot roster), has a cell ("
                                + cell.getRowIndex() + "," + cell.getColumnIndex()
                                + ") with an employeeName (" + employeeName
                                + ") that does not exist in the employeeList (" + employeeList + ").");
                    }
                    shiftAssignment.setEmployee(employee);
                }
                return shiftAssignment;
            });
            readGridSheet("Employee roster", new String[]{"Name"}, (Row row) -> {
                String employeeName = row.getCell(0).getStringCellValue();
                Employee employee = employeeMap.get(employeeName);
                if (employee == null) {
                    throw new IllegalStateException("The employeeName (" + employeeName
                            + ") does not exist in the employeeList (" + employeeList + ").");
                }
                return employee;
            }, timeSlotList, (Pair<Employee, TimeSlot> pair, Cell cell) ->  {
                if (hasStyle(cell, UNAVAILABLE_COLOR)) {
                    Employee employee = pair.getKey();
                    TimeSlot timeSlot = pair.getValue();
                    employee.getUnavailableTimeSlotSet().add(timeSlot);
                }
                return null;
            });
            return new Roster(rosterParametrization,
                    skillList, spotList, timeSlotList, employeeList,
                    shiftAssignmentList);
        }

        private <E> List<E> readListSheet(String sheetName, String[] headerTitles,
                Function<Row, E> rowMapper) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalStateException("The workbook does not contain a sheet with name ("
                        + sheetName + ").");
            }
            Row headerRow = sheet.getRow(1);
            if (headerRow == null) {
                throw new IllegalStateException("The sheet (" + sheetName + ") has no header data at row (1).");
            }
            int columnNumber = 0;
            for (String headerTitle : headerTitles) {
                Cell cell = headerRow.getCell(columnNumber);
                if (cell == null) {
                    throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
                            + cell.getRowIndex() + "," + cell.getColumnIndex()
                            + ") does not contain the headerTitle (" + headerTitle + ").");
                }
                if (!cell.getStringCellValue().equals(headerTitle)) {
                    throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
                            + cell.getRowIndex() + "," + cell.getColumnIndex()
                            + ") does not contain the headerTitle (" + headerTitle
                            + "), it contains cellValue (" + cell.getStringCellValue() + ") instead.");
                }
                columnNumber++;
            }
            List<E> elementList = new ArrayList<>(sheet.getLastRowNum() - 2);
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                for (int j = 0; j < headerTitles.length; j++) {
                    Cell cell = row.getCell(j);
                    if (cell == null) {
                        throw new IllegalStateException("The sheet (" + sheetName
                                + ") has no cell for " + headerTitles[j]
                                + " at row (" + i + ") at column (" + j + ").");
                    }
                }
                elementList.add(rowMapper.apply(row));
            }
            return elementList;
        }

        private <E, F> List<F> readGridSheet(String sheetName, String[] headerTitles,
                Function<Row, E> rowMapper, List<TimeSlot> timeSlotList,
                BiFunction<Pair<E, TimeSlot>, Cell, F> cellMapper) {
            readListSheet(sheetName, headerTitles, rowMapper);
            Sheet sheet = workbook.getSheet(sheetName);
            Row higherHeaderRow = sheet.getRow(0);
            Row lowerHeaderRow = sheet.getRow(1);
            int columnNumber = headerTitles.length;
            for (TimeSlot timeSlot : timeSlotList) {
                if (timeSlot.getStartDateTime().getHour() == 6) {
                    String expectedDayString = timeSlot.getStartDateTime().toLocalDate().format(DAY_FORMATTER);
                    Cell higherCell = higherHeaderRow.getCell(columnNumber);
                    if (higherCell == null) {
                        throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
                                + higherCell.getRowIndex() + "," + higherCell.getColumnIndex()
                                + ") does not contain the date (" + expectedDayString + ").");
                    }
                    if (!higherCell.getStringCellValue().equals(expectedDayString)) {
                        throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
                                + higherCell.getRowIndex() + "," + higherCell.getColumnIndex()
                                + ") does not contain the date (" + expectedDayString
                                + "), it contains cellValue (" + higherCell.getStringCellValue() + ") instead.");
                    }
                }
                String expectedStartDateTimeString = timeSlot.getStartDateTime().format(TIME_FORMATTER);
                Cell lowerCell = lowerHeaderRow.getCell(columnNumber);
                if (lowerCell == null) {
                    throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
                            + lowerCell.getRowIndex() + "," + lowerCell.getColumnIndex()
                            + ") does not contain the startDateTime (" + expectedStartDateTimeString + ").");
                }
                if (!lowerCell.getStringCellValue().equals(expectedStartDateTimeString)) {
                    throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
                            + lowerCell.getRowIndex() + "," + lowerCell.getColumnIndex()
                            + ") does not contain the startDateTime (" + expectedStartDateTimeString
                            + "), it contains cellValue (" + lowerCell.getStringCellValue() + ") instead.");
                }
                columnNumber++;
            }

            List<F> cellElementList = new ArrayList<>((sheet.getLastRowNum() - 2) * timeSlotList.size());
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                E rowElement = rowMapper.apply(row);
                for (int j = 0; j < timeSlotList.size(); j++) {
                    TimeSlot timeSlot = timeSlotList.get(j);
                    Cell cell = row.getCell(headerTitles.length + j);
                    if (cell == null) {
                        throw new IllegalStateException("The sheet (" + sheetName
                                + ") has no cell for " + headerTitles[j]
                                + " at row (" + i + ") at column (" + j + ").");
                    }
                    F cellElement = cellMapper.apply(Pair.of(rowElement, timeSlot), cell);
                    if (cellElement != null) {
                        cellElementList.add(cellElement);
                    }
                }
            }
            return cellElementList;
        }

        private boolean hasStyle(Cell cell, IndexedColors color) {
            return cell.getCellStyle().getFillForegroundColor() == color.getIndex()
                    && cell.getCellStyle().getFillPattern() == CellStyle.SOLID_FOREGROUND;
        }

    }

    @Override
    public void write(Roster roster, File outputSolutionFile) {
        Workbook workbook = new RosterWriter(roster).writeWorkbook();
        try (FileOutputStream out = new FileOutputStream(outputSolutionFile)) {
            workbook.write(out);
        } catch (IOException e) {
            throw new IllegalStateException("Failed writing outputSolutionFile ("
                    + outputSolutionFile + ") for roster (" + roster + ").", e);
        }
    }

    private static class RosterWriter {

        private final Roster roster;

        private final Workbook workbook;
        private final CellStyle headerStyle;

        private final CellStyle nonExistingStyle;
        private final CellStyle lockedByUserStyle;
        private final CellStyle unavailableStyle;

        public RosterWriter(Roster roster) {
            this.roster = roster;
            workbook = new XSSFWorkbook();
            headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            nonExistingStyle = createStyle(NON_EXISTING_COLOR);
            lockedByUserStyle = createStyle(LOCKED_BY_USER_COLOR);
            unavailableStyle = createStyle(UNAVAILABLE_COLOR);
        }

        public Workbook writeWorkbook() {
            Map<Pair<Spot, TimeSlot>, ShiftAssignment> spotMap = roster.getShiftAssignmentList().stream()
                    .collect(Collectors.toMap(
                    shiftAssignment -> Pair.of(shiftAssignment.getSpot(), shiftAssignment.getTimeSlot()),
                    shiftAssignment -> shiftAssignment));
            Map<Pair<Employee, TimeSlot>, List<ShiftAssignment>> employeeMap = roster.getShiftAssignmentList().stream()
                    .collect(Collectors.groupingBy(
                    shiftAssignment -> Pair.of(shiftAssignment.getEmployee(), shiftAssignment.getTimeSlot()),
                    Collectors.toList()));

            writeGridSheet("Spot roster", new String[]{"Name"}, roster.getSpotList(), (Row row, Spot spot) -> {
                row.createCell(0).setCellValue(spot.getName());
            }, (Cell cell, Pair<Spot, TimeSlot> pair) -> {
                ShiftAssignment shiftAssignment = spotMap.get(pair);
                if (shiftAssignment == null) {
                    cell.setCellStyle(nonExistingStyle);
                    cell.setCellValue(" "); // TODO HACK to get a clearer xlsx file
                    return;
                }
                if (shiftAssignment.isLockedByUser()) {
                    cell.setCellStyle(lockedByUserStyle);
                }
                Employee employee = shiftAssignment.getEmployee();
                if (employee == null) {
                    cell.setCellValue("?");
                    return;
                }
                cell.setCellValue(employee.getName());
            });
            writeGridSheet("Employee roster", new String[]{"Name"}, roster.getEmployeeList(), (Row row, Employee employee) -> {
                row.createCell(0).setCellValue(employee.getName());
            }, (Cell cell, Pair<Employee, TimeSlot> pair) -> {
                Employee employee = pair.getKey();
                TimeSlot timeSlot = pair.getValue();
                if (employee.getUnavailableTimeSlotSet().contains(timeSlot)) {
                    cell.setCellStyle(unavailableStyle);
                }
                List<ShiftAssignment> shiftAssignmentList = employeeMap.get(pair);
                if (shiftAssignmentList == null) {
                    cell.setCellValue(" "); // TODO HACK to get a clearer xlsx file
                    return;
                }
                cell.setCellValue(shiftAssignmentList.stream()
                        .map((shiftAssignment) -> shiftAssignment.getSpot().getName())
                        .collect(Collectors.joining(",")));
            });
            writeListSheet("Employees", new String[]{"Name", "Skills"}, roster.getEmployeeList(), (Row row, Employee employee) -> {
                row.createCell(0).setCellValue(employee.getName());
                row.createCell(1).setCellValue(employee.getSkillSet().stream()
                        .map(Skill::getName).collect(Collectors.joining(",")));
            });
            writeListSheet("Timeslots", new String[]{"Start", "End", "State"}, roster.getTimeSlotList(), (Row row, TimeSlot timeSlot) -> {
                row.createCell(0).setCellValue(timeSlot.getStartDateTime().format(DATE_TIME_FORMATTER));
                row.createCell(1).setCellValue(timeSlot.getEndDateTime().format(DATE_TIME_FORMATTER));
                row.createCell(2).setCellValue(timeSlot.getTimeSlotState().name());
            });
            writeListSheet("Spots", new String[]{"Name", "Required skill"}, roster.getSpotList(), (Row row, Spot spot) -> {
                row.createCell(0).setCellValue(spot.getName());
                row.createCell(1).setCellValue(spot.getRequiredSkill().getName());
            });
            writeListSheet("Skills", new String[]{"Name"}, roster.getSkillList(), (Row row, Skill skill) -> {
                row.createCell(0).setCellValue(skill.getName());
            });
            return workbook;
        }

        private <E> Sheet writeListSheet(String sheetName, String[] headerTitles, List<E> elementList,
                BiConsumer<Row, E> rowConsumer) {
            Sheet sheet = workbook.createSheet(sheetName);
            sheet.setDefaultColumnWidth(20);
            int rowNumber = 0;
            sheet.createRow(rowNumber++); // Leave empty
            Row headerRow = sheet.createRow(rowNumber++);
            int columnNumber = 0;
            for (String headerTitle : headerTitles) {
                Cell cell = headerRow.createCell(columnNumber);
                cell.setCellValue(headerTitle);
                cell.setCellStyle(headerStyle);
                columnNumber++;
            }
            sheet.createFreezePane(1, 2);
            for (E element : elementList) {
                Row row = sheet.createRow(rowNumber);
                rowConsumer.accept(row, element);
                rowNumber++;
            }
            return sheet;
        }

        private <E> void writeGridSheet(String sheetName, String[] headerTitles, List<E> rowElementList,
                BiConsumer<Row, E> rowConsumer,
                BiConsumer<Cell, Pair<E, TimeSlot>> cellConsumer) {
            Sheet sheet = writeListSheet(sheetName, headerTitles, rowElementList, rowConsumer);
            sheet.setDefaultColumnWidth(5);
            sheet.createFreezePane(headerTitles.length, 2);
            Row higherHeaderRow = sheet.getRow(0);
            Row lowerHeaderRow = sheet.getRow(1);
            int columnNumber = headerTitles.length;
            for (TimeSlot timeSlot : roster.getTimeSlotList()) {
                if (timeSlot.getStartDateTime().getHour() == 6) {
                    Cell cell = higherHeaderRow.createCell(columnNumber);
                    cell.setCellValue(timeSlot.getStartDateTime().toLocalDate().format(DAY_FORMATTER));
                    cell.setCellStyle(headerStyle);
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, columnNumber, columnNumber + 2));
                }
                Cell cell = lowerHeaderRow.createCell(columnNumber);
                cell.setCellValue(timeSlot.getStartDateTime().toLocalTime().format(TIME_FORMATTER));
                cell.setCellStyle(headerStyle);
                columnNumber++;
            }
            int rowNumber = 2;
            for (E rowElement : rowElementList) {
                Row row = sheet.getRow(rowNumber);
                columnNumber = headerTitles.length;
                for (TimeSlot timeSlot : roster.getTimeSlotList()) {
                    Cell cell = row.createCell(columnNumber);
                    cellConsumer.accept(cell, Pair.of(rowElement, timeSlot));
                    columnNumber++;
                }
                rowNumber++;
            }
        }

        private CellStyle createStyle(IndexedColors color) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(color.getIndex());
            style.setFillPattern(CellStyle.SOLID_FOREGROUND);
            return style;
        }

    }

}
