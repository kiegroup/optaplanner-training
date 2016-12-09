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

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.optaplanner.persistence.common.api.domain.solution.SolutionFileIO;
import org.optaplanner.training.workerrostering.domain.Employee;
import org.optaplanner.training.workerrostering.domain.Roster;
import org.optaplanner.training.workerrostering.domain.ShiftAssignment;
import org.optaplanner.training.workerrostering.domain.Skill;
import org.optaplanner.training.workerrostering.domain.Spot;
import org.optaplanner.training.workerrostering.domain.TimeSlot;

public class WorkerRosteringSolutionFileIO implements SolutionFileIO<Roster> {

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
        return null; // TODO
    }

    @Override
    public void write(Roster roster, File outputSolutionFile) {
        Workbook workbook = new RosterWriter(roster).fillWorkbook();
        try (FileOutputStream out = new FileOutputStream(outputSolutionFile)) {
            workbook.write(out);
        } catch (IOException e) {
            throw new IllegalStateException("Failed creating  outputSolutionFile ("
                    + outputSolutionFile + ") for roster (" + roster + ").", e);
        }
    }

    private static class RosterWriter {

        private final Roster roster;

        private final Workbook workbook;
        private final CellStyle headerStyle;
        private final CellStyle unexistingStyle;

        public RosterWriter(Roster roster) {
            this.roster = roster;
            workbook = new XSSFWorkbook();
            headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            unexistingStyle = workbook.createCellStyle();
            unexistingStyle.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            unexistingStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);
        }

        public Workbook fillWorkbook() {
            Map<Pair<Spot, TimeSlot>, ShiftAssignment> spotMap = roster.getShiftAssignmentList().stream()
                    .collect(Collectors.toMap(
                    shiftAssignment -> Pair.of(shiftAssignment.getSpot(), shiftAssignment.getTimeSlot()),
                    shiftAssignment -> shiftAssignment));
            Map<Pair<Employee, TimeSlot>, List<ShiftAssignment>> employeeMap = roster.getShiftAssignmentList().stream()
                    .collect(Collectors.groupingBy(
                    shiftAssignment -> Pair.of(shiftAssignment.getEmployee(), shiftAssignment.getTimeSlot()),
                    Collectors.toList()));

            writeGridSheet("Spots", new String[]{"Name", "Required skill"}, roster.getSpotList(), (Row row, Spot spot) -> {
                row.createCell(0).setCellValue(spot.getName());
                row.createCell(1).setCellValue(spot.getRequiredSkill().getName());
            }, (Cell cell, Pair<Spot, TimeSlot> pair) -> {
                ShiftAssignment shiftAssignment = spotMap.get(pair);
                if (shiftAssignment == null) {
                    cell.setCellStyle(unexistingStyle);
                    return;
                }
                Employee employee = shiftAssignment.getEmployee();
                if (employee == null) {
                    cell.setCellValue("?");
                    return;
                }
                cell.setCellValue(employee.getName());
            });
            writeGridSheet("Employees", new String[]{"Name", "Skills"}, roster.getEmployeeList(), (Row row, Employee employee) -> {
                row.createCell(0).setCellValue(employee.getName());
                row.createCell(1).setCellValue(employee.getSkillSet().stream()
                        .map(Skill::getName).collect(Collectors.joining(",")));
            }, (Cell cell, Pair<Employee, TimeSlot> pair) -> {
                List<ShiftAssignment> shiftAssignmentList = employeeMap.get(pair);
                if (shiftAssignmentList == null) {
                    return;
                }
                cell.setCellValue(shiftAssignmentList.stream()
                        .map((shiftAssignment) -> shiftAssignment.getSpot().getName())
                        .collect(Collectors.joining(",")));
            });
            writeListSheet("Timeslots", new String[]{"Start", "End"}, roster.getTimeSlotList(), (Row row, TimeSlot timeSlot) -> {
                row.createCell(0).setCellValue(timeSlot.getStartDateTime().toString());
                row.createCell(1).setCellValue(timeSlot.getEndDateTime().toString());
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
            Row higherHeaderRow = sheet.getRow(0);
            Row lowerHeaderRow = sheet.getRow(1);
            int columnNumber = headerTitles.length;
            for (TimeSlot timeSlot : roster.getTimeSlotList()) {
                if (timeSlot.getStartDateTime().getHour() == 6) {
                    Cell cell = higherHeaderRow.createCell(columnNumber);
                    // TODO use formatter
                    cell.setCellValue(timeSlot.getStartDateTime().getDayOfWeek().toString().substring(0, 3)
                            + " " + timeSlot.getStartDateTime().toLocalDate().toString());
                    cell.setCellStyle(headerStyle);
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, columnNumber, columnNumber + 2));
                }
                Cell cell = lowerHeaderRow.createCell(columnNumber);
                cell.setCellValue(timeSlot.getStartDateTime().toLocalTime().toString());
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

    }

}
