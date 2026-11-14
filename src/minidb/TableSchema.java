package minidb;

import java.util.List;

/**
 * Схема таблицы: хранит метаданные (имя, колонки, типы, первичный ключ).
 */
public class TableSchema {
    private String name;
    private List<String> columns;
    private List<DataType> types;
    private int primaryKeyIndex; // Индекс колонки, которая является PK


    public TableSchema() {}

    public TableSchema(String name, List<String> columns, List<DataType> types, int pkIndex) {
        this.name = name;
        this.columns = columns;
        this.types = types;
        this.primaryKeyIndex = pkIndex;
    }


    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<DataType> getTypes() { return types; }
    public void setTypes(List<DataType> types) { this.types = types; }

    public int getPrimaryKeyIndex() { return primaryKeyIndex; }
    public void setPrimaryKeyIndex(int primaryKeyIndex) { this.primaryKeyIndex = primaryKeyIndex; }
    public int getColumnIndex(String colName) {
        return columns.indexOf(colName);
    }
}