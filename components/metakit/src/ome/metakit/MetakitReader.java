//
// MetakitReader.java
//

/*
OME Metakit package for reading Metakit database files.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package ome.metakit;

import java.io.IOException;

import loci.common.DataTools;
import loci.common.RandomAccessInputStream;

/**
 * Top-level reader for Metakit database files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/metakit/src/ome/metakit/MetakitReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/metakit/src/ome/metakit/MetakitReader.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author Melissa Linkert melissa at glencoesoftware.com
 */
public class MetakitReader {

  // -- Fields --

  private RandomAccessInputStream stream;

  private String[] tableNames;
  private Column[][] columns;
  private int[] rowCount;

  private Object[][][] data;

  // -- Constructors --

  public MetakitReader(String file) throws IOException, MetakitException {
    this(new RandomAccessInputStream(file));
  }

  public MetakitReader(RandomAccessInputStream stream) throws MetakitException {
    this.stream = stream;
    try {
      initialize();
    }
    catch (IOException e) {
      throw new MetakitException(e);
    }
  }

  // -- MetakitReader API methods --

  /**
   * Retrieve the number of tables in this database file.
   */
  public int getTableCount() {
    return tableNames.length;
  }

  /**
   * Retrieve the name of every table in this database file.
   * The length of the returned array is equivalent to {@link getTableCount()}.
   */
  public String[] getTableNames() {
    return tableNames;
  }

  /**
   * Retrieve the name of every column in the table with the given index.
   * Tables are indexed from 0 to <code>{@link getTableCount()} - 1</code>.
   */
  public String[] getColumnNames(int tableIndex) {
    String[] columnNames = new String[columns[tableIndex].length];
    for (int i=0; i<columnNames.length; i++) {
      columnNames[i] = columns[tableIndex][i].getName();
    }
    return columnNames;
  }

  /**
   * Retrieve the name of every column in the named table.
   */
  public String[] getColumnNames(String tableName) {
    return getColumnNames(DataTools.indexOf(tableNames, tableName));
  }

  /**
   * Retrieve the type for every column in the table with the given index.
   * Tables are indexed from 0 to <code>{@link getTableCount()} - 1</code>.
   *
   * Every Object in the arrays returned by {@link getTableData(int)} and
   * {@link getTableData(String)} will be an instance of the corresponding
   * Class in the Class[] returned by this method.
   */
  public Class[] getColumnTypes(int tableIndex) {
    Class[] types = new Class[columns[tableIndex].length];
    for (int i=0; i<types.length; i++) {
      types[i] = columns[tableIndex][i].getType();
    }
    return types;
  }

  /**
   * Retrieve the type for every column in the named table.
   *
   * Every Object in the arrays returned by {@link getTableData(int)} and
   * {@link getTableData(String)} will be an instance of the corresponding
   * Class in the Class[] returned by this method.
   */
  public Class[] getColumnTypes(String tableName) {
    return getColumnTypes(DataTools.indexOf(tableNames, tableName));
  }

  /**
   * Retrieve the number of rows in the table with the given index.
   * Tables are indexed from 0 to <code>{@link getTableCount()} - 1</code>.
   */
  public int getRowCount(int tableIndex) {
    return rowCount[tableIndex];
  }

  /**
   * Retrieve the number of rows in the named table.
   */
  public int getRowCount(String tableName) {
    return getRowCount(DataTools.indexOf(tableNames, tableName));
  }

  /**
   * Retrieve all of the tabular data for the table with the given index.
   * Tables are indexed from 0 to <code>{@link getTableCount()} - 1</code>.
   *
   * @see getColumnTypes(int)
   */
  public Object[][] getTableData(int tableIndex) {
    Object[][] table = data[tableIndex];

    // table is stored in [column][row] order; reverse it for convenience
    Object[][] newTable = new Object[table[0].length][table.length];

    for (int row=0; row<newTable.length; row++) {
      for (int col=0; col<newTable[row].length; col++) {
        if (col < table.length && row < table[col].length) {
          newTable[row][col] = table[col][row];
        }
      }
    }

    return newTable;
  }

  /**
   * Retrieve all of the tabular data for the named table.
   *
   * @see getColumnTypes(String)
   */
  public Object[][] getTableData(String tableName) {
    return getTableData(DataTools.indexOf(tableNames, tableName));
  }

  /**
   * Retrieve the given row of data from the table with the given index.
   * Tables are indexed from 0 to <code>{@link getTableCount()} - 1</code>.
   *
   * @see getColumnTypes(int)
   */
  public Object[] getRowData(int rowIndex, int tableIndex) {
    Object[] row = new Object[data[tableIndex].length];
    for (int col=0; col<data[tableIndex].length; col++) {
      row[col] = data[tableIndex][col][rowIndex];
    }
    return row;
  }

  /**
   * Retrieve the given row of data from the named table.
   *
   * @see getColumnTypes(String)
   */
  public Object[] getRowData(int rowIndex, String tableName) {
    return getRowData(rowIndex, DataTools.indexOf(tableNames, tableName));
  }

  // -- Helper methods --

  private void initialize() throws IOException, MetakitException {
    String magic = stream.readString(2);

    if (magic.equals("LJ")) {
      stream.order(true);
    }
    else if (!magic.equals("JL")) {
      throw new MetakitException("Invalid magic string; got " + magic);
    }

    boolean valid = stream.read() == 26;
    if (!valid) {
      throw new MetakitException("'valid' flag was set to 'false'");
    }

    int headerType = stream.read();
    if (headerType != 0) {
      throw new MetakitException(
        "Header type " + headerType + " is not valid.");
    }

    long footerPointer = stream.readInt() - 16;

    stream.seek(footerPointer);
    readFooter();
  }

  private void readFooter() throws IOException, MetakitException {
    stream.skipBytes(4);

    long headerLocation = stream.readInt();
    stream.skipBytes(4);

    long tocLocation = stream.readInt();

    stream.seek(tocLocation);
    readTOC();
  }

  private void readTOC() throws IOException, MetakitException {
    int tocMarker = MetakitTools.readBpInt(stream);
    String structureDefinition = MetakitTools.readPString(stream);

    String[] tables = structureDefinition.split("],");
    tableNames = new String[tables.length];

    columns = new Column[tables.length][];

    for (int i=0; i<tables.length; i++) {
      String table = tables[i];
      int openBracket = table.indexOf("[");
      tableNames[i] = table.substring(0, openBracket);
      String columnList = table.substring(openBracket + 1);
      columnList = columnList.substring(columnList.indexOf("[") + 1);
      String[] cols = columnList.split(",");
      columns[i] = new Column[cols.length];

      for (int col=0; col<cols.length; col++) {
        columns[i][col] = new Column(cols[col]);
      }
    }

    rowCount = new int[tables.length];

    for (int i=0; i<rowCount.length; i++) {
      rowCount[i] = MetakitTools.readBpInt(stream);
      /* debug */ System.out.println("# rows for table " + i + " = " + rowCount[i]);
    }
    /* debug */ MetakitTools.readBpInt(stream);

    data = new Object[tables.length][][];

    int table = 0;
    //for (int table=0; table<tables.length; table++) {
      data[table] = new Object[columns[table].length][];
      if (rowCount[table] > 0) {
        for (int col=0; col<columns[table].length; col++) {
          ColumnMap map =
            new ColumnMap(columns[table][col], stream, rowCount[table]);
          data[table][col] = map.getValues();
        }
      }
    //}
  }

}
