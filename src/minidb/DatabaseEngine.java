package minidb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ядро СУБД. Управляет таблицами, данными и индексами.
 * Реализует паттерн Repository.
 */
public class DatabaseEngine {
    // Хранилище схем: ИмяТаблицы -> Схема
    private Map<String, TableSchema> schemas = new HashMap<>();
    // Хранилище данных: ИмяТаблицы -> СписокСтрок
    private Map<String, List<Row>> tables = new HashMap<>();
    // Индексы: Таблица -> Колонка -> Значение -> НаборИндексовСтрок
    private Map<String, Map<String, Map<Object, Set<Integer>>>> indexes = new HashMap<>();

    private static final String DATA_DIR = "db_data";
    private final ObjectMapper mapper = new ObjectMapper();

    public DatabaseEngine() {
        loadData(); // Загружаем данные при старте
    }

    /** Создание новой таблицы */
    public void createTable(TableSchema schema) {
        if (schemas.containsKey(schema.getName())) {
            throw new IllegalArgumentException("Таблица '" + schema.getName() + "' уже существует");
        }
        schemas.put(schema.getName(), schema);
        tables.put(schema.getName(), new ArrayList<>());
        indexes.put(schema.getName(), new HashMap<>());
        saveData();
    }

    /** Вставка записи с проверкой уникальности PK */
    public void insert(String tableName, Row row) {
        TableSchema schema = schemas.get(tableName);
        if (schema == null) throw new IllegalArgumentException("Таблица не найдена: " + tableName);

        // Проверка уникальности Primary Key
        Object pkVal = row.getValue(schema.getPrimaryKeyIndex());
        List<Row> rows = tables.get(tableName);
        for (Row r : rows) {
            if (Objects.equals(r.getValue(schema.getPrimaryKeyIndex()), pkVal)) {
                throw new IllegalArgumentException("Нарушение уникальности PK: " + pkVal);
            }
        }

        rows.add(row);
        updateIndexesOnInsert(tableName, row, rows.size() - 1);
        saveData();
    }

    /** Выборка данных (SELECT) */
    public List<Row> select(String tableName, String whereCol, Object whereVal) {
        List<Row> rows = tables.get(tableName);
        if (whereCol == null) return new ArrayList<>(rows);

        // Попытка использовать индекс
        Map<String, Map<Object, Set<Integer>>> tableIdx = indexes.get(tableName);
        if (tableIdx != null && tableIdx.containsKey(whereCol)) {
            Set<Integer> idxSet = tableIdx.get(whereCol).get(whereVal);
            if (idxSet != null) {
                return idxSet.stream().map(rows::get).collect(Collectors.toList());
            }
        }

        // Fallback: линейный поиск
        return rows.stream()
                .filter(r -> Objects.equals(r.getValue(schemas.get(tableName).getColumnIndex(whereCol)), whereVal))
                .collect(Collectors.toList());
    }

    /** Обновление записей (UPDATE) */
    public int update(String tableName, String setCol, Object newVal, String whereCol, Object whereVal) {
        List<Row> rows = tables.get(tableName);
        TableSchema schema = schemas.get(tableName);
        int count = 0;
        for (Row r : rows) {
            if (Objects.equals(r.getValue(schema.getColumnIndex(whereCol)), whereVal)) {
                removeFromIndex(tableName, setCol, r.getValue(schema.getColumnIndex(setCol)), rows.indexOf(r));
                r.setValue(schema.getColumnIndex(setCol), newVal);
                addToIndex(tableName, setCol, newVal, rows.indexOf(r));
                count++;
            }
        }
        if (count > 0) saveData();
        return count;
    }

    /** Удаление записей (DELETE) */
    public int delete(String tableName, String whereCol, Object whereVal) {
        List<Row> rows = tables.get(tableName);
        TableSchema schema = schemas.get(tableName);
        Iterator<Row> it = rows.iterator();
        int count = 0;
        while (it.hasNext()) {
            Row r = it.next();
            if (Objects.equals(r.getValue(schema.getColumnIndex(whereCol)), whereVal)) {
                removeFromAllIndexes(tableName, r, rows.indexOf(r));
                it.remove();
                count++;
            }
        }
        if (count > 0) saveData();
        return count;
    }

    /** Создание индекса */
    public void createIndex(String tableName, String columnName) {
        TableSchema schema = schemas.get(tableName);
        List<Row> rows = tables.get(tableName);
        Map<Object, Set<Integer>> colIndex = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            Object val = rows.get(i).getValue(schema.getColumnIndex(columnName));
            colIndex.computeIfAbsent(val, v -> new HashSet<>()).add(i);
        }

        indexes.computeIfAbsent(tableName, t -> new HashMap<>()).put(columnName, colIndex);
        saveData();
    }

    // Вспомогательные методы для индексов

    private void updateIndexesOnInsert(String table, Row row, int idx) {
        Map<String, Map<Object, Set<Integer>>> tIdx = indexes.get(table);
        if (tIdx == null) return;
        TableSchema s = schemas.get(table);
        for (String col : tIdx.keySet()) {
            Object val = row.getValue(s.getColumnIndex(col));
            tIdx.get(col).computeIfAbsent(val, v -> new HashSet<>()).add(idx);
        }
    }

    private void addToIndex(String table, String col, Object val, int idx) {
        Map<Object, Set<Integer>> cIdx = indexes.get(table).get(col);
        if (cIdx != null) cIdx.computeIfAbsent(val, v -> new HashSet<>()).add(idx);
    }

    private void removeFromIndex(String table, String col, Object oldVal, int idx) {
        Map<Object, Set<Integer>> cIdx = indexes.get(table).get(col);
        if (cIdx != null) {
            Set<Integer> set = cIdx.get(oldVal);
            if (set != null) {
                set.remove(idx);
                if(set.isEmpty()) cIdx.remove(oldVal);
            }
        }
    }

    private void removeFromAllIndexes(String table, Row row, int idx) {
        Map<String, Map<Object, Set<Integer>>> tIdx = indexes.get(table);
        if (tIdx == null) return;
        TableSchema s = schemas.get(table);
        for (String col : tIdx.keySet()) {
            removeFromIndex(table, col, row.getValue(s.getColumnIndex(col)), idx);
        }
    }

    // Сохранение и загрузка

    private void saveData() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            mapper.writeValue(new File(DATA_DIR + "/schemas.json"), schemas);
            mapper.writeValue(new File(DATA_DIR + "/tables.json"), tables);
            mapper.writeValue(new File(DATA_DIR + "/indexes.json"), indexes);
        } catch (IOException e) {
            System.err.println("Ошибка сохранения: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        try {
            java.nio.file.Path dir = Paths.get(DATA_DIR);
            if (Files.exists(dir.resolve("schemas.json"))) {
                // Загрузка схем
                TypeReference<Map<String, TableSchema>> schemaTypeRef = new TypeReference<Map<String, TableSchema>>() {};
                schemas = mapper.readValue(dir.resolve("schemas.json").toFile(), schemaTypeRef);

                // Загрузка таблиц
                TypeReference<Map<String, List<Row>>> tableTypeRef = new TypeReference<Map<String, List<Row>>>() {};
                tables = mapper.readValue(dir.resolve("tables.json").toFile(), tableTypeRef);

                // Загрузка индексов
                TypeReference<Map<String, Map<String, Map<Object, Set<Integer>>>>> indexTypeRef =
                        new TypeReference<Map<String, Map<String, Map<Object, Set<Integer>>>>>() {};
                indexes = mapper.readValue(dir.resolve("indexes.json").toFile(), indexTypeRef);
            }
        } catch (Exception e) {
            System.out.println("База пуста или повреждена. Начинаем с чистого листа.");
            if (schemas == null) schemas = new HashMap<>();
            if (tables == null) tables = new HashMap<>();
            if (indexes == null) indexes = new HashMap<>();
        }
    }

    public TableSchema getSchema(String name) { return schemas.get(name); }
    public List<Row> getAllRows(String name) { return tables.getOrDefault(name, new ArrayList<>()); }
}