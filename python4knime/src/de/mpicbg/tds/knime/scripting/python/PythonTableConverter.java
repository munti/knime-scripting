package de.mpicbg.tds.knime.scripting.python;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import de.mpicbg.tds.knime.knutils.InputTableAttribute;
import org.knime.core.data.*;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.NodeLogger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 *
 */
public class PythonTableConverter {
    public static BufferedDataTable convertCSVToTable(ExecutionContext exec, File pyOutFile, NodeLogger logger) throws RuntimeException {
        try {
            CSVReader reader = new CSVReader(new BufferedReader(new FileReader(pyOutFile)), ',', '\"');

            int errCounter = 0;
            String[] columnNames = reader.readNext();
            String[] columnTypes = reader.readNext();

            // Create data rows
            DataColumnSpec[] colSpecs = new DataColumnSpec[columnTypes.length];

            // Loop over the column types and create the columns
            for (int i = 0; i < columnTypes.length; i++) {
                if ("INT".equals(columnTypes[i]))
                    colSpecs[i] = new DataColumnSpecCreator(columnNames[i], IntCell.TYPE).createSpec();
                else if ("FLOAT".equals(columnTypes[i]))
                    colSpecs[i] = new DataColumnSpecCreator(columnNames[i], DoubleCell.TYPE).createSpec();
                else colSpecs[i] = new DataColumnSpecCreator(columnNames[i], StringCell.TYPE).createSpec();
            }

            DataTableSpec tableSpec = new DataTableSpec(colSpecs);
            BufferedDataContainer container = exec.createDataContainer(tableSpec);

            String[] line;
            int rowNum = 0;
            while ((line = reader.readNext()) != null) {
                DataCell[] cells = new DataCell[columnNames.length];

                for (int i = 0; i < columnTypes.length; i++) {
                    DataColumnSpec colSpec = colSpecs[i];

                    // Create the cell based on the column type
                    try {
                        if (colSpec.getType().isCompatible(IntValue.class)) {
                            cells[i] = new IntCell(Integer.parseInt(line[i]));
                        } else if (colSpec.getType().isCompatible(DoubleValue.class))
                            cells[i] = new DoubleCell(Double.parseDouble(line[i]));
                        else cells[i] = new StringCell(line[i]);
                    } catch (NumberFormatException e) {
                        cells[i] = DataType.getMissingCell();
                    }
                }

                RowKey key = new RowKey("" + rowNum++);
                DataRow row = new DefaultRow(key, cells);
                container.addRowToTable(row);
            }

            container.close();

            return container.getTable();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> getColumnNames(DataTableSpec tableSpec) {
        List<String> colNames = new ArrayList<String>();
        for (DataColumnSpec colSpec : tableSpec) {
            colNames.add(colSpec.getName());
        }
        return colNames;
    }

    private static List<DataType> getColumnTypes(DataTableSpec tableSpec) {
        List<DataType> colTypes = new ArrayList<DataType>();
        for (DataColumnSpec colSpec : tableSpec) {
            colTypes.add(colSpec.getType());
        }

        return colTypes;
    }

    /**
     * Write a table to a CSV file.  The first line of the file is a CSV array of column names, the second is
     * a CSV array of column types, all subsequent lines are CSV rows of the table.
     *
     * @param exec
     * @param inputTable
     * @param kInFile
     * @throws RuntimeException
     */
    public static void convertTableToCSV(ExecutionContext exec, BufferedDataTable inputTable, File kInFile, NodeLogger logger) throws RuntimeException {
        try {
            CSVWriter writer = new CSVWriter(new BufferedWriter(new FileWriter(kInFile)), ',', '\"');
            DataTableSpec tableSpec = inputTable.getDataTableSpec();

            int numRows = inputTable.getRowCount();
            List<String> columnNames = getColumnNames(tableSpec);

            // The first line is the column names
            writer.writeNext(columnNames.toArray(new String[0]));

            // Build a list of column types
            List<DataType> colTypes = getColumnTypes(tableSpec);
            ArrayList<String> outputTypes = new ArrayList<String>();

            for (DataType type : colTypes) {
                if (type.equals(IntCell.TYPE)) outputTypes.add("INT");
                else if (type.equals(DoubleCell.TYPE)) outputTypes.add("FLOAT");
                else if (type.equals(StringCell.TYPE)) outputTypes.add("STRING");
            }

            // The second line is the column types
            writer.writeNext(outputTypes.toArray(new String[0]));

            // Add all the table values, row by row
            ArrayList<String> rowValues = new ArrayList<String>();

            for (DataRow dataRow : inputTable) {
                int colNum = 0;
                for (DataColumnSpec columnSpec : tableSpec) {
                    DataType colType = colTypes.get(colNum);
                    DataCell cell = dataRow.getCell(colNum++);

                    InputTableAttribute a = new InputTableAttribute(columnSpec.getName(), inputTable);
                    //Attribute a = new Attribute(columnSpec.getName(), columnSpec.getType());
                    if (cell.isMissing()) rowValues.add("");
                    else if (colType.equals(StringCell.TYPE)) {
                        rowValues.add(a.getNominalAttribute(dataRow));
                    } else if (colType.equals(DoubleCell.TYPE)) {
                        rowValues.add(a.getDoubleAttribute(dataRow).toString());
                    } else if (colType.equals(IntCell.TYPE)) {
                        rowValues.add(a.getIntAttribute(dataRow).toString());
                    }
                }

                // All the columns have been added so write it out
                writer.writeNext(rowValues.toArray(new String[0]));
                rowValues.clear();
            }

            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
