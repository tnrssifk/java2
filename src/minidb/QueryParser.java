package minidb;

import java.time.LocalDate;
import java.util.*;

public class QueryParser {
    private final DatabaseEngine engine;

    public QueryParser(DatabaseEngine engine) {
        this.engine = engine;
    }

    public String execute(String query) {
        query = query.trim();
        if (query.isEmpty()) return "";

        String upper = query.toUpperCase();
        try {
            if (upper.startsWith("CREATE TABLE")) return handleCreateTable(query);
            if (upper.startsWith("CREATE INDEX")) return handleCreateIndex(query);
            if (upper.startsWith("INSERT")) return handleInsert(query);
            if (upper.startsWith("SELECT")) return handleSelect(query);
            if (upper.startsWith("UPDATE")) return handleUpdate(query);
            if (upper.startsWith("DELETE")) return handleDelete(query);
            return "Неизвестная команда.";
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    private String handleCreateTable(String q) {
        // CREATE TABLE cars (id INT PK, brand STRING, price DOUBLE)
        String body = q.substring("CREATE TABLE".length()).trim();
        String[] parts = body.split("\\(", 2);
        String name = parts[0].trim();
        String colsStr = parts[1].replace(")", "").trim();

        List<String> cols = new ArrayList<>();
        List<DataType> types = new ArrayList<>();
        int pkIdx = 0;

        String[] colDefs = colsStr.split(",");
        for (int i = 0; i < colDefs.length; i++) {
            String[] def = colDefs[i].trim().split("\\s+");
            cols.add(def[0]);
            String typeStr = def[1].toUpperCase().replaceAll("[^A-Z]", "");

            switch(typeStr) {
                case "INT": types.add(DataType.INTEGER); break;
                case "DOUBLE": types.add(DataType.DOUBLE); break;
                case "STRING": types.add(DataType.STRING); break;
                case "DATE": types.add(DataType.DATE); break;
                case "BOOL": types.add(DataType.BOOLEAN); break;
                default: throw new IllegalArgumentException("Неизвестный тип: " + typeStr);
            }
            if (def.length > 2 && def[2].equalsIgnoreCase("PK")) pkIdx = i;
        }

        engine.createTable(new TableSchema(name, cols, types, pkIdx));
        return "Таблица '" + name + "' создана.";
    }

    private String handleInsert(String q) {

        String intoPart = q.substring("INSERT INTO".length(), q.indexOf("VALUES")).trim();
        String tableName = intoPart.split("\\s+")[0];

        List<String> specifiedCols = null;
        if (intoPart.contains("(")) {
            String colsStr = intoPart.substring(intoPart.indexOf("(")+1, intoPart.indexOf(")"));
            specifiedCols = Arrays.asList(colsStr.split(","));
            specifiedCols.replaceAll(String::trim);
        }

        String valsStr = q.substring(q.indexOf("VALUES")+6).trim().replace(";", "");
        valsStr = valsStr.substring(valsStr.indexOf("(")+1, valsStr.lastIndexOf(")"));
        String[] rawVals = splitValues(valsStr);

        TableSchema schema = engine.getSchema(tableName);
        List<Object> fullRow = new ArrayList<>(Collections.nCopies(schema.getColumns().size(), null));

        List<String> targetCols = specifiedCols != null ? specifiedCols : schema.getColumns();
        for (int i = 0; i < rawVals.length; i++) {
            int colIdx = schema.getColumnIndex(targetCols.get(i));
            Object val = parseValue(rawVals[i].trim(), schema.getTypes().get(colIdx));
            fullRow.set(colIdx, val);
        }

        if (fullRow.get(schema.getPrimaryKeyIndex()) == null)
            throw new IllegalArgumentException("Первичный ключ не может быть NULL");

        engine.insert(tableName, new Row(fullRow));
        return "Запись добавлена.";
    }

    private String handleSelect(String q) {
        // SELECT brand, price FROM cars WHERE price = 20000;
        String fromPart = q.substring("SELECT".length(), q.indexOf("FROM")).trim();
        String rest = q.substring(q.indexOf("FROM")+4).trim().replace(";", "");

        String[] fromWhere = rest.split("WHERE", 2);
        String tableName = fromWhere[0].trim();
        if (engine.getSchema(tableName) == null) {
            return "Ошибка: Таблица '" + tableName + "' не существует!";
        }
        String whereClause = fromWhere.length > 1 ? fromWhere[1].trim() : null;

        String whereCol = null; Object whereVal = null;
        if (whereClause != null) {
            String[] cond = whereClause.split("[=><!]+", 2);
            whereCol = cond[0].trim();
            if (whereClause.contains("=")) {
                TableSchema s = engine.getSchema(tableName);
                whereVal = parseValue(cond[1].trim(), s.getTypes().get(s.getColumnIndex(whereCol)));
            }
        }

        List<Row> result = engine.select(tableName, whereCol, whereVal);

        List<String> showCols = fromPart.equals("*") ?
                engine.getSchema(tableName).getColumns() :
                Arrays.asList(fromPart.split(","));
        showCols.replaceAll(String::trim);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s", String.join(" | ", showCols))).append("\n");
        sb.append("-".repeat(showCols.size() * 22)).append("\n");

        for (Row r : result) {
            List<String> line = new ArrayList<>();
            for (String c : showCols) {
                int idx = engine.getSchema(tableName).getColumnIndex(c);
                line.add(String.valueOf(r.getValue(idx)));
            }
            sb.append(String.format("%-20s", String.join(" | ", line))).append("\n");
        }
        return sb.toString();
    }

    private String handleUpdate(String q) {
        // UPDATE cars SET price = 30000 WHERE id = 2;
        String setPart = q.substring(q.indexOf("SET")+3, q.indexOf("WHERE")).trim();
        String wherePart = q.substring(q.indexOf("WHERE")+5).trim().replace(";", "");
        String tableName = q.substring("UPDATE".length(), q.indexOf("SET")).trim();

        String[] setKV = setPart.split("=");
        String[] whereKV = wherePart.split("=");

        TableSchema s = engine.getSchema(tableName);
        Object setVal = parseValue(setKV[1].trim(), s.getTypes().get(s.getColumnIndex(setKV[0].trim())));
        Object whereVal = parseValue(whereKV[1].trim(), s.getTypes().get(s.getColumnIndex(whereKV[0].trim())));

        int cnt = engine.update(tableName, setKV[0].trim(), setVal, whereKV[0].trim(), whereVal);
        return "Обновлено записей: " + cnt;
    }

    private String handleDelete(String q) {
        String wherePart = q.substring(q.indexOf("WHERE")+5).trim().replace(";", "");
        String tableName = q.substring("DELETE FROM".length(), q.indexOf("WHERE")).trim();

        String[] kv = wherePart.split("=");
        TableSchema s = engine.getSchema(tableName);
        Object val = parseValue(kv[1].trim(), s.getTypes().get(s.getColumnIndex(kv[0].trim())));

        int cnt = engine.delete(tableName, kv[0].trim(), val);
        return "Удалено записей: " + cnt;
    }

    private String handleCreateIndex(String q) {

        String onPart = q.substring(q.indexOf("ON")+2, q.indexOf("(")).trim();
        String col = q.substring(q.indexOf("(")+1, q.indexOf(")")).trim();
        String[] parts = onPart.split("\\s+");
        String tableName = parts[parts.length-1];

        engine.createIndex(tableName, col);
        return "Индекс создан.";
    }



    private String[] splitValues(String s) {
        List<String> res = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\'') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) { res.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        res.add(cur.toString());
        return res.toArray(new String[0]);
    }

    private Object parseValue(String v, DataType type) {
        if (v.equalsIgnoreCase("NULL")) return null;
        try {
            switch (type) {
                case INTEGER: return Integer.parseInt(v);
                case DOUBLE: return Double.parseDouble(v);
                case BOOLEAN: return Boolean.parseBoolean(v);
                case DATE: return LocalDate.parse(v);
                case STRING: return v.replace("'", "");
                default: return v;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ошибка преобразования значения '" + v + "' в тип " + type);
        }
    }
}